package top.mc506lw.monolith.feature.buildsite

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.rebar.MonolithLib
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BuildSiteManager {
    
    private val sites = ConcurrentHashMap<UUID, BuildSite>()
    private val locationIndex = ConcurrentHashMap<String, UUID>()
    private val chunkSites = ConcurrentHashMap<String, MutableSet<UUID>>()
    private var trackingTask: BukkitTask? = null
    
    private lateinit var dataFolder: File
    private lateinit var saveFile: File
    
    private const val TRACKING_INTERVAL = 5L
    private const val UNLOAD_DISTANCE_SQ = BuildSite.UNLOAD_DISTANCE.toLong() * BuildSite.UNLOAD_DISTANCE
    
    fun init(plugin: MonolithLib) {
        dataFolder = File(plugin.dataFolder, "buildsites")
        dataFolder.mkdirs()
        saveFile = File(dataFolder, "build_sites.yml")
        
        loadAll()
        startTracking(plugin)
    }
    
    fun createSite(
        blueprint: Blueprint,
        anchorLocation: Location,
        facing: Facing
    ): BuildSite? {
        val existing = getSiteAt(anchorLocation)
        if (existing != null) return null
        
        val site = BuildSite(
            id = UUID.randomUUID(),
            blueprint = blueprint,
            anchorLocation = anchorLocation.clone(),
            facing = facing
        )
        
        registerSite(site)
        saveAll()
        
        return site
    }
    
    private fun registerSite(site: BuildSite) {
        sites[site.id] = site
        
        val locationKey = locationToKey(site.anchorLocation)
        locationIndex[locationKey] = site.id
        
        val chunkKey = chunkKeyFromLocation(site.anchorLocation)
        chunkSites.getOrPut(chunkKey) { ConcurrentHashMap.newKeySet() }.add(site.id)
    }
    
    fun getSite(id: UUID): BuildSite? = sites[id]
    
    fun getSiteAt(location: Location): BuildSite? {
        val key = locationToKey(location)
        val siteId = locationIndex[key] ?: return null
        return sites[siteId]
    }
    
    fun getSiteAtBlock(worldName: String, x: Int, y: Int, z: Int): BuildSite? {
        val key = "$worldName|$x|$y|$z"
        val siteId = locationIndex[key] ?: return null
        return sites[siteId]
    }
    
    fun getSitesInChunk(worldName: String, chunkX: Int, chunkZ: Int): List<BuildSite> {
        val chunkKey = "$worldName|$chunkX|$chunkZ"
        val siteIds = chunkSites[chunkKey] ?: return emptyList()
        return siteIds.mapNotNull { sites[it] }.filter { it.isActive && !it.isCompleted }
    }
    
    fun getAllActiveSites(): List<BuildSite> = sites.values.filter { it.isActive && !it.isCompleted }
    
    fun removeSite(siteId: UUID) {
        val site = sites.remove(siteId) ?: return
        
        val locationKey = locationToKey(site.anchorLocation)
        locationIndex.remove(locationKey)
        
        site.removeAllRenderings()
        
        val chunkKey = chunkKeyFromLocation(site.anchorLocation)
        chunkSites[chunkKey]?.remove(siteId)
        
        saveAll()
    }
    
    fun handleChunkLoad(worldName: String, chunkX: Int, chunkZ: Int) {
        val chunkKey = "$worldName|$chunkX|$chunkZ"
        val siteIdsInChunk = chunkSites[chunkKey] ?: return
        
        for (siteId in siteIdsInChunk) {
            val site = sites[siteId]
            if (site != null && !site.isCompleted) {
                for (player in Bukkit.getOnlinePlayers()) {
                    site.renderForPlayer(player)
                }
            }
        }
    }
    
    fun handleChunkUnload(worldName: String, chunkX: Int, chunkZ: Int) {
        val chunkKey = "$worldName|$chunkX|$chunkZ"
        val siteIdsInChunk = chunkSites[chunkKey]?.toList() ?: return
        
        for (siteId in siteIdsInChunk) {
            val site = sites[siteId]
            if (site != null) {
                site.removeAllRenderings()
            }
        }
    }
    
    fun onPlayerMove(player: Player) {
        val activeSites = getAllActiveSites()
        
        if (activeSites.isEmpty()) return
        
        for (site in activeSites) {
            if (site.anchorLocation.world?.name != player.world.name) continue
            
            val distSq = player.location.distanceSquared(site.anchorLocation)
            
            if (distSq <= UNLOAD_DISTANCE_SQ) {
                site.renderForPlayer(player)
            } else {
                site.removeRenderingForPlayer(player.uniqueId)
            }
        }
    }
    
    fun onPlayerQuit(player: Player) {
        for (site in getAllActiveSites()) {
            site.removeRenderingForPlayer(player.uniqueId)
        }
    }
    
    private fun startTracking(plugin: MonolithLib) {
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tickTracking()
        }, TRACKING_INTERVAL, TRACKING_INTERVAL)
    }
    
    private fun tickTracking() {
        for (player in Bukkit.getOnlinePlayers()) {
            onPlayerMove(player)
        }
    }
    
    fun stopTracking() {
        trackingTask?.cancel()
        trackingTask = null
    }
    
    fun cleanup() {
        stopTracking()
        for (site in sites.values) {
            site.removeAllRenderings()
        }
        sites.clear()
        locationIndex.clear()
        chunkSites.clear()
    }
    
    private fun locationToKey(location: Location): String {
        return "${location.world?.name}|${location.blockX}|${location.blockY}|${location.blockZ}"
    }
    
    private fun chunkKeyFromLocation(location: Location): String {
        val cx = location.blockX shr 4
        val cz = location.blockZ shr 4
        return "${location.world?.name}|$cx|$cz"
    }

    private fun loadAll() {
        if (!saveFile.exists()) return
        
        try {
            val lines = saveFile.readLines()
            var currentSite: MutableMap<String, Any>? = null
            val loadedSites = mutableListOf<MutableMap<String, Any>>()
            
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("site:") -> {
                        currentSite = mutableMapOf("id" to trimmed.substringAfter("site:").trim())
                        loadedSites.add(currentSite!!)
                    }
                    trimmed.contains(":") && currentSite != null -> {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2) {
                            currentSite!![parts[0].trim()] = parts[1].trim()
                        }
                    }
                    trimmed.isEmpty() -> {
                        currentSite = null
                    }
                }
            }
            
            for (siteData in loadedSites) {
                try {
                    restoreSiteFromData(siteData)
                } catch (e: Exception) {
                    println("[MonolithLib] 加载工地失败: ${e.message}")
                }
            }
            
            println("[MonolithLib] 已加载 ${loadedSites.size} 个存档工地")
        } catch (e: Exception) {
            println("[MonolithLib] 读取工地存档失败: ${e.message}")
        }
    }
    
    private fun restoreSiteFromData(data: Map<String, Any>) {
        val idStr = data["id"] as? String ?: return
        val blueprintId = data["blueprint_id"] as? String ?: return
        val worldName = data["world"] as? String ?: return
        val x = (data["x"] as? String)?.toIntOrNull() ?: return
        val y = (data["y"] as? String)?.toIntOrNull() ?: return
        val z = (data["z"] as? String)?.toIntOrNull() ?: return
        val facingStr = data["facing"] as? String ?: "NORTH"
        val layerStr = data["current_layer"] as? String ?: "0"
        val placedStr = data["placed_blocks"] as? String ?: ""
        
        val api = try { MonolithAPI.getInstance() } catch (_: Exception) { return }
        val blueprint = api.registry.getBlueprint(blueprintId) ?: return
        
        val world = Bukkit.getWorld(worldName) ?: return
        val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        val facing = try { Facing.valueOf(facingStr) } catch (_: Exception) { Facing.NORTH }
        val currentLayer = layerStr.toIntOrNull() ?: 0
        
        val placedBlocks = if (placedStr.isNotEmpty()) {
            placedStr.split(";").mapNotNull { pos ->
                val coords = pos.split(",")
                if (coords.size == 3) {
                    Vector3i(coords[0].toIntOrNull() ?: return@mapNotNull null,
                             coords[1].toIntOrNull() ?: return@mapNotNull null,
                             coords[2].toIntOrNull() ?: return@mapNotNull null)
                } else null
            }.toSet()
        } else emptySet()
        
        val site = BuildSite(
            id = UUID.fromString(idStr),
            blueprint = blueprint,
            anchorLocation = location,
            facing = facing,
            initialLayer = currentLayer,
            initialPlacedBlocks = placedBlocks
        )
        
        registerSite(site)
    }
    
    fun saveAll() {
        try {
            val lines = mutableListOf<String>()
            
            for ((_, site) in sites) {
                if (!site.isActive || site.isCompleted) continue
                
                lines.add("site: ${site.id}")
                lines.add("  blueprint_id: ${site.blueprintId}")
                lines.add("  world: ${site.anchorLocation.world?.name}")
                lines.add("  x: ${site.anchorLocation.blockX}")
                lines.add("  y: ${site.anchorLocation.blockY}")
                lines.add("  z: ${site.anchorLocation.blockZ}")
                lines.add("  facing: ${site.facing.name}")
                lines.add("  current_layer: ${site.currentLayer}")
                
                val placedStr = site.placedBlocks.joinToString(";") { "${it.x},${it.y},${it.z}" }
                lines.add("  placed_blocks: $placedStr")
                lines.add("")
            }
            
            saveFile.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            println("[MonolithLib] 保存工地数据失败: ${e.message}")
        }
    }
}
