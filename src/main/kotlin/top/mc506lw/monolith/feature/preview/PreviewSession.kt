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
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.Predicates
import top.mc506lw.monolith.validation.predicate.RotatedPredicate
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
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
    val blueprintId: String,
    val blueprint: Blueprint,
    val controllerLocation: Location,
    private val _transform: CoordinateTransform,
    private val renderRadius: Int = 7
) {
    constructor(
        sessionId: String,
        playerId: UUID,
        blueprintId: String,
        blueprint: Blueprint,
        controllerLocation: Location,
        facing: Facing = Facing.NORTH,
        renderRadius: Int = 7
    ) : this(
        sessionId = sessionId,
        playerId = playerId,
        blueprintId = blueprintId,
        blueprint = blueprint,
        controllerLocation = controllerLocation,
        _transform = CoordinateTransform(facing),
        renderRadius = renderRadius
    )
    
    val structureId: String get() = blueprintId
    val transform: CoordinateTransform get() = _transform
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
    var showAllLayers: Boolean = false
    
    val ghostBlockCount: Int get() = ghostBlocks.size
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
        val shape: Shape = blueprint.scaffoldShape
        val centerOffset = blueprint.meta.controllerOffset
        val rotationSteps = transform.facing.rotationSteps
        
        for (blockEntry in shape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = Vector3i(
                    controllerLocation.blockX,
                    controllerLocation.blockY,
                    controllerLocation.blockZ
                ),
                relativePos = blockEntry.position,
                centerOffset = centerOffset
            )
            
            val originalPreview = blockEntry.blockData.clone()
            val rotatedPreview = BlockStateRotator.rotate(originalPreview, rotationSteps)
            
            val predicate = Predicates.strict(blockEntry.blockData)
            val rotatedPredicate = RotatedPredicate(predicate, rotationSteps)
            
            ghostBlocks.add(GhostBlock(
                worldPos = worldPos,
                relativePos = blockEntry.position,
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
    
    private var updateTick = 0
    
    fun update(player: Player): UpdateResult {
        if (!isActive || isComplete || isCancelled) {
            if (displayEntities.isEmpty() && ghostBlocks.isNotEmpty()) {
                org.bukkit.Bukkit.getLogger().warning("[Preview] update退出(STOPPED): isActive=$isActive, isComplete=$isComplete, isCancelled=$isCancelled, ghosts=${ghostBlocks.size}")
            }
            return UpdateResult.STOPPED
        }
        
        val world = controllerLocation.world
        if (world == null || player.world != world) {
            if (displayEntities.isEmpty()) {
                org.bukkit.Bukkit.getLogger().warning("[Preview] world不匹配: controllerWorld=${controllerLocation?.world?.name}, playerWorld=${player.world.name}, controllerLoc=$controllerLocation")
            }
            return UpdateResult.CONTINUE
        }
        
        val playerLoc = player.location
        val playerBlockX = playerLoc.blockX
        val playerBlockY = playerLoc.blockY
        val playerBlockZ = playerLoc.blockZ
        
        val radiusSq = renderRadius * renderRadius
        
        val visiblePositions = mutableSetOf<Vector3i>()
        
        for (ghost in ghostBlocks) {
            if (!showAllLayers && ghost.worldPos.y != currentLayer) continue
            
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
        
        updateTick++
        if (updateTick <= 3) {
            val sampleGhost = ghostBlocks.firstOrNull()
            val sampleDist = sampleGhost?.let {
                val ddx = (it.worldPos.x - playerBlockX).toDouble()
                val ddy = (it.worldPos.y - playerBlockY).toDouble()
                val ddz = (it.worldPos.z - playerBlockZ).toDouble()
                kotlin.math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz)
            }
            org.bukkit.Bukkit.getLogger().info("[Preview] tick#$updateTick: player=($playerBlockX,$playerBlockY,$playerBlockZ), controller=${controllerLocation.blockX},${controllerLocation.blockY},${controllerLocation.blockZ}, visible=${visiblePositions.size}/${ghostBlocks.size}, entities=${displayEntities.size}, nearestGhostDist=$sampleDist")
            
            if (visiblePositions.isNotEmpty() && displayEntities.size < visiblePositions.size) {
                org.bukkit.Bukkit.getLogger().warning("[Preview] tick#$updateTick: 有${visiblePositions.size}个可见位置但只有${displayEntities.size}个实体! 部分spawn可能失败")
            }
        }
        
        val toRemove = displayEntities.keys.filter { it !in visiblePositions }
        toRemove.forEach { pos ->
            displayEntities[pos]?.remove()
            displayEntities.remove(pos)
        }
        
        val completed = if (showAllLayers) {
            ghostBlocks.isNotEmpty() && ghostBlocks.all { it.currentState == GhostColor.CORRECT }
        } else {
            checkLayerCompletion()
        }
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
        }
    }
    
    private var spawnCount = 0
    
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
                d.glowColorOverride = ghost.currentState.color
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                val scale = 1.0f
                d.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f()
                )
            }
            
            spawnCount++
            if (spawnCount <= 3) {
                org.bukkit.Bukkit.getLogger().info("[Preview] spawn#$spawnCount: pos=${ghost.worldPos}, block=${ghost.previewBlockData.material}, valid=${display.isValid}, entityID=${display.entityId}")
            }
            
            displayEntities[ghost.worldPos] = display
            display
        } catch (e: Exception) {
            org.bukkit.Bukkit.getLogger().warning("[Preview] 生成BlockDisplay失败: pos=${ghost.worldPos}, error=${e.javaClass.simpleName}: ${e.message}")
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
