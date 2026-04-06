package top.mc506lw.monolith.feature.preview

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.structure.FlattenedBlock
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.rebar.MonolithLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StructurePreviewSession(
    val sessionId: String,
    val playerId: UUID,
    val structureId: String,
    val structure: MonolithStructure,
    val controllerLocation: Location,
    val transform: CoordinateTransform
) {
    private val displayEntities = ConcurrentHashMap<Vector3i, BlockDisplay>()
    private var expireTaskId: Int = -1
    
    var isActive: Boolean = false
        private set
    
    fun start() {
        if (isActive) return
        isActive = true
        createPreview()
        scheduleExpire()
    }
    
    fun stop() {
        if (!isActive) return
        isActive = false
        cancelExpireTask()
        cleanup()
    }
    
    private fun scheduleExpire() {
        expireTaskId = Bukkit.getScheduler().runTaskLater(MonolithLib.instance, Runnable {
            if (isActive) {
                val player = Bukkit.getPlayer(playerId)
                player?.sendMessage("§e[MonolithLib] 预览已过期")
                stop()
            }
        }, 200L).taskId
    }
    
    private fun cancelExpireTask() {
        if (expireTaskId != -1) {
            Bukkit.getScheduler().cancelTask(expireTaskId)
            expireTaskId = -1
        }
    }
    
    private fun createPreview() {
        val world = controllerLocation.world ?: return
        val rotationSteps = transform.facing.rotationSteps
        
        val surfaceBlocks = findSurfaceBlocks()
        
        for (entry in surfaceBlocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = Vector3i(
                    controllerLocation.blockX,
                    controllerLocation.blockY,
                    controllerLocation.blockZ
                ),
                relativePos = entry.relativePosition,
                centerOffset = structure.centerOffset
            )
            
            val originalPreview = entry.previewBlockData ?: Material.STONE.createBlockData()
            val rotatedPreview = BlockStateRotator.rotate(originalPreview, rotationSteps)
            
            createDisplayEntity(worldPos, rotatedPreview)
        }
    }
    
    private fun findSurfaceBlocks(): List<FlattenedBlock> {
        val surfaceBlocks = mutableListOf<FlattenedBlock>()
        val allPositions = structure.flattenedBlocks.map { it.relativePosition }.toSet()
        
        for (block in structure.flattenedBlocks) {
            if (isSurfaceBlock(block.relativePosition, allPositions)) {
                surfaceBlocks.add(block)
            }
        }
        
        return surfaceBlocks
    }
    
    private fun isSurfaceBlock(pos: Vector3i, allPositions: Set<Vector3i>): Boolean {
        val neighbors = listOf(
            Vector3i(pos.x + 1, pos.y, pos.z),
            Vector3i(pos.x - 1, pos.y, pos.z),
            Vector3i(pos.x, pos.y + 1, pos.z),
            Vector3i(pos.x, pos.y - 1, pos.z),
            Vector3i(pos.x, pos.y, pos.z + 1),
            Vector3i(pos.x, pos.y, pos.z - 1)
        )
        
        for (neighbor in neighbors) {
            if (neighbor !in allPositions) {
                return true
            }
        }
        
        return false
    }
    
    private fun createDisplayEntity(worldPos: Vector3i, blockData: BlockData) {
        val world = controllerLocation.world ?: return
        val location = Location(world, worldPos.x.toDouble(), worldPos.y.toDouble(), worldPos.z.toDouble())
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = blockData
                d.isGlowing = true
                d.glowColorOverride = Color.AQUA
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                d.setViewRange(128f)
                d.setDisplayHeight(512f)
                d.setDisplayWidth(512f)
                
                val scale = 0.5f
                d.transformation = Transformation(
                    Vector3f(0.25f, 0.25f, 0.25f),
                    AxisAngle4f(),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f()
                )
            }
            
            displayEntities[worldPos] = display
        } catch (e: Exception) {
            // Ignore spawn failures
        }
    }
    
    fun cleanup() {
        displayEntities.values.forEach { it.remove() }
        displayEntities.clear()
    }
}
