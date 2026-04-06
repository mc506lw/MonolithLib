package top.mc506lw.monolith.feature.preview

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.Predicate
import top.mc506lw.monolith.core.predicate.RotatedPredicate
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class GhostColor(val color: Color) {
    CORRECT(Color.LIME),
    MISSING(Color.RED),
    WRONG(Color.ORANGE)
}

data class GhostBlock(
    val worldPos: Vector3i,
    val relativePos: Vector3i,
    val predicate: Predicate,
    val previewBlockData: BlockData,
    var currentState: GhostColor = GhostColor.MISSING,
    var displayEntity: BlockDisplay? = null
)

class PreviewSession(
    val sessionId: String,
    val playerId: UUID,
    val structureId: String,
    val structure: MonolithStructure,
    val controllerLocation: Location,
    val transform: CoordinateTransform,
    private val renderRadius: Int = 7
) {
    private val ghostBlocks = mutableListOf<GhostBlock>()
    private val displayEntities = ConcurrentHashMap<Vector3i, BlockDisplay>()
    
    var currentLayer: Int = 0
        private set
    var totalLayers: Int = 0
        private set
    var isActive: Boolean = false
        private set
    var isComplete: Boolean = false
        private set
    var isCancelled: Boolean = false
        private set
    
    val minLayer: Int
    val maxLayer: Int
    
    init {
        initializeGhostBlocks()
        
        val worldYCoords = ghostBlocks.map { it.worldPos.y }.distinct().sorted()
        
        minLayer = worldYCoords.minOrNull() ?: controllerLocation.blockY
        maxLayer = worldYCoords.maxOrNull() ?: controllerLocation.blockY
        totalLayers = worldYCoords.size
        currentLayer = minLayer
    }
    
    private fun initializeGhostBlocks() {
        val rotationSteps = transform.facing.rotationSteps
        
        for (entry in structure.flattenedBlocks) {
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
            
            val rotatedPredicate = RotatedPredicate(entry.predicate, rotationSteps)
            
            ghostBlocks.add(GhostBlock(
                worldPos = worldPos,
                relativePos = entry.relativePosition,
                predicate = rotatedPredicate,
                previewBlockData = rotatedPreview
            ))
        }
    }
    
    fun start() {
        isActive = true
    }
    
    fun stop() {
        isActive = false
        cleanup()
    }
    
    fun cancel() {
        isCancelled = true
        isActive = false
        cleanup()
    }
    
    fun cleanup() {
        displayEntities.values.forEach { it.remove() }
        displayEntities.clear()
    }
    
    fun update(player: Player): UpdateResult {
        if (!isActive || isComplete || isCancelled) {
            return UpdateResult.STOPPED
        }
        
        val world = controllerLocation.world
        if (world == null || player.world != world) {
            return UpdateResult.CONTINUE
        }
        
        val controllerBlock = world.getBlockAt(
            controllerLocation.blockX,
            controllerLocation.blockY,
            controllerLocation.blockZ
        )
        if (controllerBlock.type.isAir) {
            return UpdateResult.CONTROLLER_BROKEN
        }
        
        val playerLoc = player.location
        val playerBlockX = playerLoc.blockX
        val playerBlockY = playerLoc.blockY
        val playerBlockZ = playerLoc.blockZ
        
        val radiusSq = renderRadius * renderRadius
        
        val visiblePositions = mutableSetOf<Vector3i>()
        
        for (ghost in ghostBlocks) {
            if (ghost.worldPos.y != currentLayer) continue
            
            val dx = ghost.worldPos.x - playerBlockX
            val dy = ghost.worldPos.y - playerBlockY
            val dz = ghost.worldPos.z - playerBlockZ
            val distSq = dx * dx + dy * dy + dz * dz
            
            if (distSq <= radiusSq) {
                visiblePositions.add(ghost.worldPos)
                val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
                updateGhostBlock(ghost, block)
            }
        }
        
        val toRemove = displayEntities.keys.filter { it !in visiblePositions }
        toRemove.forEach { pos ->
            displayEntities[pos]?.remove()
            displayEntities.remove(pos)
        }
        
        val completed = checkLayerCompletion()
        return if (completed) UpdateResult.COMPLETED else UpdateResult.CONTINUE
    }
    
    private fun updateGhostBlock(ghost: GhostBlock, block: Block) {
        val isCorrect = ghost.predicate.testMaterialOnly(
            block.blockData,
            Predicate.PredicateContext(
                position = ghost.relativePos,
                block = block
            )
        )
        
        val isAir = block.type.isAir
        
        val newState = when {
            isAir -> GhostColor.MISSING
            isCorrect -> GhostColor.CORRECT
            else -> GhostColor.WRONG
        }
        
        ghost.currentState = newState
        
        val display = getOrCreateDisplayEntity(ghost)
        
        display?.let { d ->
            d.glowColorOverride = newState.color
            d.isGlowing = true
        }
    }
    
    private fun getOrCreateDisplayEntity(ghost: GhostBlock): BlockDisplay? {
        val existing = displayEntities[ghost.worldPos]
        if (existing != null && existing.isValid) {
            return existing
        }
        
        val world = controllerLocation.world ?: return null
        val location = Location(world, ghost.worldPos.x.toDouble(), ghost.worldPos.y.toDouble(), ghost.worldPos.z.toDouble())
        
        return try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = ghost.previewBlockData
                d.isGlowing = true
                d.glowColorOverride = ghost.currentState.color
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                val scale = 0.5f
                d.transformation = Transformation(
                    Vector3f(0.25f, 0.25f, 0.25f),
                    AxisAngle4f(),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f()
                )
            }
            
            displayEntities[ghost.worldPos] = display
            display
        } catch (e: Exception) {
            null
        }
    }
    
    private fun checkLayerCompletion(): Boolean {
        val layerBlocks = ghostBlocks.filter { it.worldPos.y == currentLayer }
        val allCorrect = layerBlocks.isNotEmpty() && layerBlocks.all { it.currentState == GhostColor.CORRECT }
        
        if (allCorrect) {
            if (currentLayer < maxLayer) {
                currentLayer++
                displayEntities.values.forEach { it.remove() }
                displayEntities.clear()
            } else {
                isComplete = true
                cleanup()
                return true
            }
        }
        return false
    }
    
    fun getLayerProgress(): Pair<Int, Int> {
        val layerBlocks = ghostBlocks.filter { it.worldPos.y == currentLayer }
        val correct = layerBlocks.count { it.currentState == GhostColor.CORRECT }
        return Pair(correct, layerBlocks.size)
    }
    
    fun setLayer(layer: Int): Boolean {
        if (layer in minLayer..maxLayer) {
            currentLayer = layer
            displayEntities.values.forEach { it.remove() }
            displayEntities.clear()
            return true
        }
        return false
    }
    
    fun nextLayer(): Boolean {
        return if (currentLayer < maxLayer) {
            currentLayer++
            displayEntities.values.forEach { it.remove() }
            displayEntities.clear()
            true
        } else false
    }
    
    fun prevLayer(): Boolean {
        return if (currentLayer > minLayer) {
            currentLayer--
            displayEntities.values.forEach { it.remove() }
            displayEntities.clear()
            true
        } else false
    }
    
    enum class UpdateResult {
        CONTINUE,
        COMPLETED,
        CONTROLLER_BROKEN,
        STOPPED
    }
}
