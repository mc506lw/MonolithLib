package top.mc506lw.monolith.feature.buildsite

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import top.mc506lw.rebar.MonolithLib
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BuildSitePreviewManager {
    
    private val legacy = LegacyComponentSerializer.legacySection()
    private val PREVIEW_TAG = "monolithlib:buildsite_preview"
    private const val PREVIEW_DURATION_TICKS = 200L
    
    private val activePreviews = ConcurrentHashMap<UUID, BuildSitePreview>()
    private val playerPreviews = ConcurrentHashMap<UUID, UUID>()
    private val previewTasks = ConcurrentHashMap<UUID, BukkitTask>()
    
    private val redConcreteData: BlockData by lazy {
        Bukkit.createBlockData(Material.RED_CONCRETE)
    }
    
    private val yellowConcreteData: BlockData by lazy {
        Bukkit.createBlockData(Material.YELLOW_CONCRETE)
    }
    
    fun startPreview(
        player: Player,
        blueprint: Blueprint,
        anchorLocation: Location,
        facing: Facing,
        validationResult: ValidationResult
    ): BuildSitePreview? {
        stopPreview(player)
        
        val world = anchorLocation.world ?: return null
        
        val preview = BuildSitePreview(
            id = UUID.randomUUID(),
            playerId = player.uniqueId,
            blueprintId = blueprint.id,
            world = world,
            boundingBox = validationResult.boundingBox,
            validationResult = validationResult,
            facing = facing,
            anchorLocation = anchorLocation.clone()
        )
        
        activePreviews[preview.id] = preview
        playerPreviews[player.uniqueId] = preview.id
        
        preview.show()
        
        scheduleAutoCancel(player)
        
        return preview
    }
    
    private fun scheduleAutoCancel(player: Player) {
        val existingTask = previewTasks.remove(player.uniqueId)
        existingTask?.cancel()
        
        var countdown = 10
        val task = Bukkit.getScheduler().runTaskTimer(MonolithLib.instance, Runnable {
            countdown--
            if (countdown <= 0) {
                if (hasActivePreview(player)) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.previewExpired))
                    stopPreview(player)
                }
                previewTasks.remove(player.uniqueId)?.cancel()
            } else if (countdown <= 3 && hasActivePreview(player)) {
                player.sendActionBar(legacy.serialize(I18n.Message.BuildSite.previewCountdown(countdown)))
            }
        }, 20L, 20L)
        
        previewTasks[player.uniqueId] = task
    }
    
    fun stopPreview(player: Player) {
        val task = previewTasks.remove(player.uniqueId)
        task?.cancel()
        
        val previewId = playerPreviews.remove(player.uniqueId) ?: return
        val preview = activePreviews.remove(previewId) ?: return
        preview.hide()
    }
    
    fun stopAllPreviews() {
        previewTasks.values.forEach { it.cancel() }
        previewTasks.clear()
        
        activePreviews.values.forEach { it.hide() }
        activePreviews.clear()
        playerPreviews.clear()
    }
    
    fun getPreview(player: Player): BuildSitePreview? {
        val previewId = playerPreviews[player.uniqueId] ?: return null
        return activePreviews[previewId]
    }
    
    fun hasActivePreview(player: Player): Boolean {
        return playerPreviews.containsKey(player.uniqueId)
    }
    
    fun confirmPreview(player: Player): BuildSitePreview? {
        val preview = getPreview(player) ?: return null
        return preview
    }
    
    fun cancelPreview(player: Player) {
        stopPreview(player)
    }
}

class BuildSitePreview(
    val id: UUID,
    val playerId: UUID,
    val blueprintId: String,
    val world: World,
    val boundingBox: BoundingBox,
    val validationResult: ValidationResult,
    val facing: Facing,
    val anchorLocation: Location
) {
    companion object {
        private val legacy = LegacyComponentSerializer.legacySection()
    }
    
    private val edgeDisplays = mutableListOf<BlockDisplay>()
    private val cornerDisplays = mutableListOf<BlockDisplay>()
    private val errorMarkerDisplays = mutableListOf<BlockDisplay>()
    private var isActive = false
    
    fun show() {
        if (isActive) return
        isActive = true
        
        createBoundingBoxEdges()
        createErrorMarkers()
    }
    
    fun hide() {
        if (!isActive) return
        isActive = false
        
        edgeDisplays.forEach { 
            if (it.isValid) it.remove() 
        }
        cornerDisplays.forEach { 
            if (it.isValid) it.remove() 
        }
        errorMarkerDisplays.forEach { 
            if (it.isValid) it.remove() 
        }
        
        edgeDisplays.clear()
        cornerDisplays.clear()
        errorMarkerDisplays.clear()
    }
    
    private fun createBoundingBoxEdges() {
        val box = boundingBox
        val color = if (validationResult.isValid) Color.RED else Color.fromRGB(255, 85, 85)
        val thickness = 0.1f
        val maxSegmentLength = 80
        
        val edges = listOf(
            Pair(Vector3i(box.minX, box.minY, box.minZ), Vector3i(box.maxX + 1, box.minY, box.minZ)),
            Pair(Vector3i(box.minX, box.maxY + 1, box.minZ), Vector3i(box.maxX + 1, box.maxY + 1, box.minZ)),
            Pair(Vector3i(box.minX, box.minY, box.maxZ + 1), Vector3i(box.maxX + 1, box.minY, box.maxZ + 1)),
            Pair(Vector3i(box.minX, box.maxY + 1, box.maxZ + 1), Vector3i(box.maxX + 1, box.maxY + 1, box.maxZ + 1)),
            
            Pair(Vector3i(box.minX, box.minY, box.minZ), Vector3i(box.minX, box.maxY + 1, box.minZ)),
            Pair(Vector3i(box.maxX + 1, box.minY, box.minZ), Vector3i(box.maxX + 1, box.maxY + 1, box.minZ)),
            Pair(Vector3i(box.minX, box.minY, box.maxZ + 1), Vector3i(box.minX, box.maxY + 1, box.maxZ + 1)),
            Pair(Vector3i(box.maxX + 1, box.minY, box.maxZ + 1), Vector3i(box.maxX + 1, box.maxY + 1, box.maxZ + 1)),
            
            Pair(Vector3i(box.minX, box.minY, box.minZ), Vector3i(box.minX, box.minY, box.maxZ + 1)),
            Pair(Vector3i(box.maxX + 1, box.minY, box.minZ), Vector3i(box.maxX + 1, box.minY, box.maxZ + 1)),
            Pair(Vector3i(box.minX, box.maxY + 1, box.minZ), Vector3i(box.minX, box.maxY + 1, box.maxZ + 1)),
            Pair(Vector3i(box.maxX + 1, box.maxY + 1, box.minZ), Vector3i(box.maxX + 1, box.maxY + 1, box.maxZ + 1))
        )
        
        for ((start, end) in edges) {
            val dx = (end.x - start.x).toFloat()
            val dy = (end.y - start.y).toFloat()
            val dz = (end.z - start.z).toFloat()
            val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            
            if (length <= maxSegmentLength) {
                createEdgeDisplay(start, end, color, thickness)
            } else {
                val segments = kotlin.math.ceil(length / maxSegmentLength).toInt()
                for (i in 0 until segments) {
                    val t0 = i.toFloat() / segments
                    val t1 = (i + 1).toFloat() / segments
                    
                    val segStart = Vector3i(
                        (start.x + (end.x - start.x) * t0).toInt(),
                        (start.y + (end.y - start.y) * t0).toInt(),
                        (start.z + (end.z - start.z) * t0).toInt()
                    )
                    val segEnd = Vector3i(
                        (start.x + (end.x - start.x) * t1).toInt(),
                        (start.y + (end.y - start.y) * t1).toInt(),
                        (start.z + (end.z - start.z) * t1).toInt()
                    )
                    
                    createEdgeDisplay(segStart, segEnd, color, thickness)
                }
            }
        }
        
        val corners = listOf(
            Vector3i(box.minX, box.minY, box.minZ),
            Vector3i(box.maxX + 1, box.minY, box.minZ),
            Vector3i(box.minX, box.maxY + 1, box.minZ),
            Vector3i(box.maxX + 1, box.maxY + 1, box.minZ),
            Vector3i(box.minX, box.minY, box.maxZ + 1),
            Vector3i(box.maxX + 1, box.minY, box.maxZ + 1),
            Vector3i(box.minX, box.maxY + 1, box.maxZ + 1),
            Vector3i(box.maxX + 1, box.maxY + 1, box.maxZ + 1)
        )
        
        for (corner in corners) {
            createCornerDisplay(corner, color)
        }
    }
    
    private fun createEdgeDisplay(start: Vector3i, end: Vector3i, color: Color, thickness: Float) {
        val dx = (end.x - start.x).toFloat()
        val dy = (end.y - start.y).toFloat()
        val dz = (end.z - start.z).toFloat()
        
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        
        if (length < 0.1f) return
        
        val location = Location(world, start.x.toDouble(), start.y.toDouble(), start.z.toDouble())
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = Bukkit.createBlockData(Material.RED_CONCRETE)
                d.isGlowing = true
                d.glowColorOverride = color
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                val scaleX = if (dx != 0f) kotlin.math.abs(dx) else thickness
                val scaleY = if (dy != 0f) kotlin.math.abs(dy) else thickness
                val scaleZ = if (dz != 0f) kotlin.math.abs(dz) else thickness
                
                d.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(),
                    Vector3f(scaleX, scaleY, scaleZ),
                    AxisAngle4f()
                )
            }
            
            edgeDisplays.add(display)
        } catch (_: Exception) {}
    }
    
    private fun createCornerDisplay(pos: Vector3i, color: Color) {
        val location = Location(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = Bukkit.createBlockData(Material.RED_CONCRETE)
                d.isGlowing = true
                d.glowColorOverride = color
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                d.transformation = Transformation(
                    Vector3f(-0.15f, -0.15f, -0.15f),
                    AxisAngle4f(),
                    Vector3f(0.3f, 0.3f, 0.3f),
                    AxisAngle4f()
                )
            }
            
            cornerDisplays.add(display)
        } catch (_: Exception) {}
    }
    
    private fun createErrorMarkers() {
        val errorColor = Color.fromRGB(255, 0, 0)
        
        for (error in validationResult.errors) {
            for (pos in error.positions) {
                createErrorMarker(pos, errorColor)
            }
        }
        
        val warningColor = Color.fromRGB(255, 255, 0)
        for (warning in validationResult.warnings) {
            for (pos in warning.positions) {
                createErrorMarker(pos, warningColor)
            }
        }
    }
    
    private fun createErrorMarker(pos: Vector3i, color: Color) {
        val location = Location(world, pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5)
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = Bukkit.createBlockData(if (color == Color.fromRGB(255, 0, 0)) Material.REDSTONE_BLOCK else Material.GOLD_BLOCK)
                d.isGlowing = true
                d.glowColorOverride = color
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                d.transformation = Transformation(
                    Vector3f(-0.3f, -0.3f, -0.3f),
                    AxisAngle4f(),
                    Vector3f(0.6f, 0.6f, 0.6f),
                    AxisAngle4f()
                )
            }
            
            errorMarkerDisplays.add(display)
        } catch (_: Exception) {}
    }
    
    fun getSummaryMessage(): List<String> {
        val messages = mutableListOf<String>()
        
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewHeader))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewBlueprint(blueprintId)))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewFacing(facing.name)))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewSize(boundingBox.width, boundingBox.height, boundingBox.depth)))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewPosition(
            boundingBox.minX, boundingBox.minY, boundingBox.minZ,
            boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ
        )))
        
        if (validationResult.isValid) {
            messages.add(legacy.serialize(I18n.Message.BuildSite.previewValid))
        } else {
            messages.add(legacy.serialize(I18n.Message.BuildSite.previewErrors(validationResult.errors.size)))
            for (error in validationResult.errors) {
                messages.add("  §c- ${error.message}")
            }
        }
        
        if (validationResult.warnings.isNotEmpty()) {
            messages.add(legacy.serialize(I18n.Message.BuildSite.previewWarnings))
            for (warning in validationResult.warnings) {
                messages.add("  §e- ${warning.message}")
            }
        }
        
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewInstructions))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewAutoCancel(10)))
        messages.add(legacy.serialize(I18n.Message.BuildSite.previewFooter))
        
        return messages
    }
}
