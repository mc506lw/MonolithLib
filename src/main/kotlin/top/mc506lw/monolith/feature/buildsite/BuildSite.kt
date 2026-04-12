package top.mc506lw.monolith.feature.buildsite

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.validation.AutoFixer
import top.mc506lw.monolith.validation.BlockFixEntry
import top.mc506lw.monolith.validation.DetailedValidationResult
import top.mc506lw.monolith.validation.RebarTrigger
import top.mc506lw.monolith.validation.ValidationEngine
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.Predicates
import top.mc506lw.monolith.validation.predicate.RotatedPredicate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object BlueprintItem {
    
    private val BLUEPRINT_KEY = NamespacedKey("monolithlib", "blueprint_id")
    private val BLUEPRINT_FACING_KEY = NamespacedKey("monolithlib", "blueprint_facing")
    
    fun create(blueprintId: String): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text("\u00a7b蓝图: \u00a7f$blueprintId"))
        meta.lore(listOf(
            Component.text("\u00a77右键放置以创建工地"),
            Component.text("\u00a777蓝图ID: \u00a7e$blueprintId")
        ))
        
        meta.persistentDataContainer.set(BLUEPRINT_KEY, PersistentDataType.STRING, blueprintId)
        meta.isUnbreakable = true
        
        try {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true)
        } catch (_: Exception) {}
        
        item.itemMeta = meta
        return item
    }
    
    fun getBlueprintId(item: ItemStack): String? {
        return item.itemMeta?.persistentDataContainer?.get(BLUEPRINT_KEY, PersistentDataType.STRING)
    }
    
    fun isBlueprintItem(item: ItemStack): Boolean {
        return getBlueprintId(item) != null
    }
    
    fun setFacing(item: ItemStack, facing: Facing) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(BLUEPRINT_FACING_KEY, PersistentDataType.STRING, facing.name)
        item.itemMeta = meta
    }
    
    fun getFacing(item: ItemStack): Facing? {
        val name = item.itemMeta?.persistentDataContainer?.get(BLUEPRINT_FACING_KEY, PersistentDataType.STRING)
        return name?.let { try { Facing.valueOf(it) } catch (e: Exception) { null } }
    }
}

data class SiteGhostBlock(
    val worldPos: Vector3i,
    val relativePos: Vector3i,
    val isCore: Boolean,
    val predicate: Predicate,
    val previewBlockData: BlockData
)

class BuildSite(
    val id: UUID,
    val blueprint: Blueprint,
    val anchorLocation: Location,
    val facing: Facing,
    initialLayer: Int = 0,
    initialPlacedBlocks: Set<Vector3i> = emptySet()
) {
    val blueprintId: String get() = blueprint.id
    
    val transform: CoordinateTransform = CoordinateTransform(facing)
    
    var currentLayer: Int = initialLayer
        private set
    
    var placedBlocks: MutableSet<Vector3i> = ConcurrentHashMap.newKeySet<Vector3i>()
        private set
    
    val allGhostBlocks: List<SiteGhostBlock>
    val coreWorldPos: Vector3i
    val coreRebarKey: NamespacedKey? = blueprint.controllerRebarKey
    
    val boundingMinX: Int
    val boundingMinY: Int
    val boundingMinZ: Int
    val boundingMaxX: Int
    val boundingMaxY: Int
    val boundingMaxZ: Int
    
    val layerYLevels: List<Int>
    var totalLayers: Int = 0
        private set
    
    val isActive: Boolean = true
    var isCompleted: Boolean = false
        private set
    var isWaitingForCore: Boolean = false
        private set
    
    private val displayEntities = ConcurrentHashMap<Vector3i, BlockDisplay>()
    private val playerRenderCache = ConcurrentHashMap<UUID, Set<Vector3i>>()
    private var coreGlassPlaced: Boolean = false
    private var coreControllerDisplay: BlockDisplay? = null
    
    init {
        placedBlocks.addAll(initialPlacedBlocks)
        
        val shape: Shape = blueprint.shape
        val centerOffset = blueprint.meta.controllerOffset
        val rotationSteps = facing.rotationSteps
        val controllerPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )
        
        coreWorldPos = transform.toWorldPosition(
            controllerPos = controllerPos,
            relativePos = centerOffset,
            centerOffset = centerOffset
        )
        
        val blocks = mutableListOf<SiteGhostBlock>()
        
        for (blockEntry in shape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = blockEntry.position,
                centerOffset = centerOffset
            )
            
            val originalPreview = blockEntry.blockData.clone()
            val rotatedPreview = BlockStateRotator.rotate(originalPreview, rotationSteps)
            
            val isCore = (blockEntry.position == centerOffset)
            
            val predicate = if (isCore && coreRebarKey != null) {
                Predicates.rebar(coreRebarKey, rotatedPreview)
            } else {
                Predicates.strict(blockEntry.blockData)
            }
            
            val rotatedPredicate = RotatedPredicate(predicate, rotationSteps)
            
            blocks.add(SiteGhostBlock(
                worldPos = worldPos,
                relativePos = blockEntry.position,
                isCore = isCore,
                predicate = rotatedPredicate,
                previewBlockData = if (isCore && coreRebarKey != null) {
                    Bukkit.createBlockData(Material.FURNACE)
                } else {
                    rotatedPreview
                }
            ))
        }
        
        allGhostBlocks = blocks.toList()
        
        if (blocks.isEmpty()) {
            boundingMinX = anchorLocation.blockX
            boundingMinY = anchorLocation.blockY
            boundingMinZ = anchorLocation.blockZ
            boundingMaxX = anchorLocation.blockX
            boundingMaxY = anchorLocation.blockY
            boundingMaxZ = anchorLocation.blockZ
        } else {
            boundingMinX = blocks.minOf { it.worldPos.x }
            boundingMinY = blocks.minOf { it.worldPos.y }
            boundingMinZ = blocks.minOf { it.worldPos.z }
            boundingMaxX = blocks.maxOf { it.worldPos.x }
            boundingMaxY = blocks.maxOf { it.worldPos.y }
            boundingMaxZ = blocks.maxOf { it.worldPos.z }
        }
        
        layerYLevels = allGhostBlocks.map { it.worldPos.y }.distinct().sorted()
        totalLayers = layerYLevels.size
        
        currentLayer = when {
            layerYLevels.isEmpty() -> 0
            initialLayer >= layerYLevels.size -> 0
            else -> initialLayer
        }
    }
    
    fun getCurrentLayerY(): Int? = layerYLevels.getOrNull(currentLayer)
    
    fun getCurrentLayerBlocks(): List<SiteGhostBlock> {
        val layerY = getCurrentLayerY() ?: return emptyList()
        return allGhostBlocks.filter { it.worldPos.y == layerY && !it.isCore }
    }
    
    fun getCoreBlock(): SiteGhostBlock? {
        return allGhostBlocks.find { it.isCore }
    }
    
    fun renderForPlayer(player: Player) {
        if (isCompleted) return
        
        val world = anchorLocation.world
        if (world == null || player.world != world) return
        
        if (isWaitingForCore) {
            renderCoreController(world)
            return
        }
        
        val playerLoc = player.location
        val radiusSq = RENDER_RADIUS * RENDER_RADIUS
        val currentLayerY = getCurrentLayerY() ?: return
        
        placeCoreGlassIfAbsent(world)
        
        val visiblePositions = mutableSetOf<Vector3i>()
        
        for (ghost in allGhostBlocks) {
            if (ghost.worldPos.y != currentLayerY) continue
            if (ghost.isCore) continue
            
            val dx = ghost.worldPos.x - playerLoc.blockX
            val dy = ghost.worldPos.y - playerLoc.blockY
            val dz = ghost.worldPos.z - playerLoc.blockZ
            val distSq = dx.toLong() * dx + dy.toLong() * dy + dz.toLong() * dz
            
            if (distSq <= radiusSq.toLong()) {
                visiblePositions.add(ghost.worldPos)
                updateGhostDisplay(world, ghost)
            }
        }
        
        cleanupStaleEntities(visiblePositions)
        playerRenderCache[player.uniqueId] = visiblePositions
    }
    
    private fun placeCoreGlassIfAbsent(world: org.bukkit.World) {
        if (coreGlassPlaced) return
        
        val block = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
        if (!block.type.isAir && !block.isReplaceable) return
        
        block.type = Material.RED_STAINED_GLASS
        coreGlassPlaced = true
    }
    
    private fun updateGhostDisplay(world: org.bukkit.World, ghost: SiteGhostBlock) {
        val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
        
        val context = Predicate.PredicateContext(position = ghost.relativePos, block = block)
        
        val isFullyCorrect = ghost.predicate.test(block.blockData, context)
        val isMaterialCorrect = ghost.predicate.testMaterialOnly(block.blockData, context)
        val isAir = block.type.isAir
        
        val glowColor = when {
            isFullyCorrect -> Color.LIME
            isAir -> Color.RED
            isMaterialCorrect -> Color.YELLOW
            else -> Color.RED
        }
        
        val existing = displayEntities[ghost.worldPos]
        if (existing != null && existing.isValid) {
            existing.glowColorOverride = glowColor
            return
        }
        
        existing?.remove()
        
        val location = Location(world, 
            ghost.worldPos.x.toDouble(), 
            ghost.worldPos.y.toDouble(), 
            ghost.worldPos.z.toDouble())
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = ghost.previewBlockData
                d.isGlowing = true
                d.glowColorOverride = glowColor
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
        } catch (_: Exception) {}
    }
    
    private fun cleanupStaleEntities(visiblePositions: Set<Vector3i>) {
        val toRemove = displayEntities.keys.filter { it !in visiblePositions }
        for (pos in toRemove) {
            displayEntities[pos]?.remove()
            displayEntities.remove(pos)
        }
    }
    
    fun checkLayerCompletion(): Boolean {
        if (isWaitingForCore || isCompleted) return false
        
        val currentLayerY = getCurrentLayerY() ?: return false
        val layerBlocks = getCurrentLayerBlocks()
        
        if (layerBlocks.isEmpty()) return false
        
        val world = anchorLocation.world ?: return false
        
        val allMatchMaterial = layerBlocks.all { ghost ->
            if (ghost.isCore) return@all true
            
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            
            if (block.type.isAir) return@all false
            
            val expectedMaterial = ghost.previewBlockData.material
            block.type == expectedMaterial
        }
        
        return allMatchMaterial
    }
    
    fun advanceToNextLayer(): Boolean {
        if (currentLayer < totalLayers - 1) {
            currentLayer++
            clearCurrentLayerRenderings()
            return true
        } else {
            return false
        }
    }
    
    fun startFinalPhase(): Boolean {
        if (currentLayer >= totalLayers - 1) {
            isWaitingForCore = true
            removeAllRenderings()
            removeCoreGlass()
            return true
        }
        return false
    }
    
    fun autoCorrectAllBlocks(): Int {
        val world = anchorLocation.world ?: return 0
        var corrected = 0
        
        for (ghost in allGhostBlocks) {
            if (ghost.isCore) continue
            
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            
            if (block.type.isAir) continue
            
            val context = Predicate.PredicateContext(position = ghost.relativePos, block = block)
            val isFullyCorrect = ghost.predicate.test(block.blockData, context)
            
            if (!isFullyCorrect) {
                try {
                    block.setBlockData(ghost.previewBlockData.clone())
                    corrected++
                } catch (_: Exception) {}
            }
        }
        
        return corrected
    }
    
    private fun renderCoreController(world: org.bukkit.World) {
        if (coreControllerDisplay != null && coreControllerDisplay!!.isValid) return
        
        coreControllerDisplay?.remove()
        
        val coreGhost = getCoreBlock() ?: return
        val location = Location(world, 
            coreWorldPos.x.toDouble() + 0.5, 
            coreWorldPos.y.toDouble(), 
            coreWorldPos.z.toDouble() + 0.5)
        
        try {
            val display = world.spawn(location, BlockDisplay::class.java) { d ->
                d.block = coreGhost.previewBlockData
                d.isGlowing = true
                d.glowColorOverride = Color.fromRGB(255, 215, 0)
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f),
                    AxisAngle4f(),
                    Vector3f(1f, 1f, 1f),
                    AxisAngle4f()
                )
            }
            coreControllerDisplay = display
        } catch (_: Exception) {}
    }
    
    fun removeRenderingForPlayer(playerId: UUID) {
        playerRenderCache.remove(playerId)
    }
    
    fun removeAllRenderings() {
        displayEntities.values.forEach { it.remove() }
        displayEntities.clear()
        playerRenderCache.clear()
        coreControllerDisplay?.remove()
        coreControllerDisplay = null
    }
    
    fun removeCoreGlass() {
        if (!coreGlassPlaced) return
        
        val world = anchorLocation.world ?: return
        val block = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
        if (block.type == Material.RED_STAINED_GLASS) {
            block.type = Material.AIR
        }
        coreGlassPlaced = false
    }
    
    fun containsPosition(pos: Vector3i): Boolean {
        return allGhostBlocks.any { it.worldPos == pos }
    }
    
    fun isCorePosition(pos: Vector3i): Boolean {
        return pos == coreWorldPos
    }
    
    fun isCoreGlassPosition(pos: Vector3i): Boolean {
        return pos == coreWorldPos && coreGlassPlaced
    }
    
    fun canPlaceCore(pos: Vector3i): Boolean {
        return isWaitingForCore && pos == coreWorldPos
    }
    
    fun getGhostAt(pos: Vector3i): SiteGhostBlock? {
        return allGhostBlocks.find { it.worldPos == pos }
    }
    
    fun recordPlacement(worldPos: Vector3i) {
        placedBlocks.add(worldPos)
    }
    
    fun removePlacement(worldPos: Vector3i) {
        placedBlocks.remove(worldPos)
    }
    
    fun revertFromWaitingForCore(): Int {
        if (!isWaitingForCore) return -1
        
        coreControllerDisplay?.remove()
        coreControllerDisplay = null
        
        isWaitingForCore = false
        
        var revertedLayer = -1
        for (i in layerYLevels.indices.reversed()) {
            val layerY = layerYLevels[i]
            val layerBlocks = allGhostBlocks.filter { it.worldPos.y == layerY && !it.isCore }
            if (layerBlocks.isNotEmpty()) {
                currentLayer = i
                revertedLayer = i
                break
            }
        }
        
        if (revertedLayer == -1) {
            currentLayer = 0
        }
        
        return revertedLayer
    }
    
    fun getProgress(): Pair<Int, Int> {
        val nonCoreBlocks = allGhostBlocks.filter { !it.isCore }
        val placed = nonCoreBlocks.count { it.worldPos in placedBlocks }
        return Pair(placed, nonCoreBlocks.size)
    }
    
    fun getCompletionRate(): Double {
        val (placed, total) = getProgress()
        return if (total > 0) placed.toDouble() / total.toDouble() else 1.0
    }
    
    private fun clearCurrentLayerRenderings() {
        displayEntities.values.forEach { it.remove() }
        displayEntities.clear()
        playerRenderCache.clear()
    }
    
    fun markCompleted() {
        isCompleted = true
        removeAllRenderings()
        removeCoreGlass()
    }
    
    private val validationEngine: ValidationEngine by lazy {
        ValidationEngine(blueprint, transform)
    }
    
    fun checkCompletionAndFix(): CompletableFuture<Boolean> {
        val world = anchorLocation.world
        if (world == null) {
            return CompletableFuture.completedFuture(false)
        }
        
        val result = validationEngine.validateDetailed(anchorLocation)
        
        if (!result.isComplete) {
            return CompletableFuture.completedFuture(false)
        }
        
        if (result.needsFix) {
            return AutoFixer.fixBlocks(result.blocksToFix, world)
                .thenApply { fixedCount ->
                    if (fixedCount > 0) {
                        RebarTrigger.triggerFormationSync(anchorLocation)
                    } else {
                        false
                    }
                }
        } else {
            return CompletableFuture.completedFuture(
                RebarTrigger.triggerFormationSync(anchorLocation)
            )
        }
    }
    
    fun validateDetailed(): DetailedValidationResult {
        return validationEngine.validateDetailed(anchorLocation)
    }
    
    fun getBlocksToFix(): List<BlockFixEntry> {
        val result = validationEngine.validateDetailed(anchorLocation)
        return result.blocksToFix
    }
    
    companion object {
        const val RENDER_RADIUS = 7
        const val UNLOAD_DISTANCE = 50
    }
}