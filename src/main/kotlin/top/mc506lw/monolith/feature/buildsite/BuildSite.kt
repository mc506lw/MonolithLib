package top.mc506lw.monolith.feature.buildsite

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
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
import top.mc506lw.monolith.validation.ValidationEngine
import top.mc506lw.monolith.validation.predicate.MaterialPredicate
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.Predicates
import top.mc506lw.monolith.validation.predicate.RotatedPredicate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class BuildSiteState {
    BUILDING_LAYERS,
    AWAITING_CORE,
    COMPLETED
}

object BlueprintItem {

    private val BLUEPRINT_KEY = NamespacedKey("monolithlib", "blueprint_id")
    private val BLUEPRINT_FACING_KEY = NamespacedKey("monolithlib", "blueprint_facing")

    fun create(blueprintId: String): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName(Component.text("\u00a7b蓝图: \u00a7f$blueprintId"))
        meta.lore(listOf(
            Component.text("\u00a77右键放置以创建工地"),
            Component.text("\u00a77蓝图ID: \u00a7e$blueprintId")
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

    var state: BuildSiteState = BuildSiteState.BUILDING_LAYERS
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

    var isCompleted: Boolean = false
        private set

    private val displayEntities = ConcurrentHashMap<Vector3i, BlockDisplay>()
    private val playerRenderCache = ConcurrentHashMap<UUID, Set<Vector3i>>()
    private var coreGlassPlaced: Boolean = false
    private var coreControllerDisplay: BlockDisplay? = null
    private var coreItemDisplay: ItemDisplay? = null

    private val validationEngine: ValidationEngine by lazy {
        ValidationEngine(blueprint, transform)
    }

    init {
        placedBlocks.addAll(initialPlacedBlocks)

        val shape: Shape = blueprint.scaffoldShape
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
                Predicates.material(blockEntry.blockData.material)
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

    fun getCoreBlock(): SiteGhostBlock? = allGhostBlocks.find { it.isCore }

    fun renderForPlayer(player: Player) {
        if (isCompleted) return
        if (state == BuildSiteState.COMPLETED) return

        val world = anchorLocation.world
        if (world == null || player.world != world) return

        if (state == BuildSiteState.AWAITING_CORE) {
            renderCoreMarker(world)
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
        if (state == BuildSiteState.AWAITING_CORE || isCompleted) return false

        val currentLayerY = getCurrentLayerY() ?: return false
        val layerBlocks = getCurrentLayerBlocks()

        if (layerBlocks.isEmpty()) return false

        val world = anchorLocation.world ?: return false

        for (ghost in layerBlocks) {
            if (ghost.isCore) continue

            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            if (!block.chunk.isLoaded) return false
            if (block.type.isAir) return false

            val context = Predicate.PredicateContext(position = ghost.relativePos, block = block)
            if (!ghost.predicate.testMaterialOnly(block.blockData, context)) return false
        }

        return true
    }

    fun advanceToNextLayer(): Boolean {
        if (currentLayer < totalLayers - 1) {
            currentLayer++
            clearCurrentLayerRenderings()
            return true
        }
        return false
    }

    fun advanceToNextIncompleteLayer(): Int {
        if (isLastLayer()) return 0

        var advancedCount = 0

        while (currentLayer < totalLayers - 1) {
            currentLayer++
            advancedCount++
            clearCurrentLayerRenderings()

            val layerInfo = "推进到第${currentLayer}层 (共推进${advancedCount}层)"
            Bukkit.getLogger().info("[BuildSite] advanceToNextIncompleteLayer: $layerInfo")

            if (!isLayerFullyPlaced(currentLayer)) {
                Bukkit.getLogger().info("[BuildSite] advanceToNextIncompleteLayer: 第${currentLayer}层未完成，停止")
                break
            }

            Bukkit.getLogger().info("[BuildSite] advanceToNextIncompleteLayer: 第${currentLayer}层已完成，继续检查下一层")
        }

        if (isLastLayer() && isLayerFullyPlaced(currentLayer)) {
            val lastLayerInfo = "已到达最后层(${currentLayer})且该层已完成!"
            Bukkit.getLogger().info("[BuildSite] advanceToNextIncompleteLayer: $lastLayerInfo")
        }

        return advancedCount
    }

    private fun isLastLayer(): Boolean = currentLayer >= totalLayers - 1

    private fun isLayerFullyPlaced(layerIndex: Int): Boolean {
        val layerY = layerYLevels.getOrNull(layerIndex) ?: return true

        val layerBlocks = allGhostBlocks.filter { it.worldPos.y == layerY && !it.isCore }
        if (layerBlocks.isEmpty()) return true

        val world = anchorLocation.world ?: return false

        for (ghost in layerBlocks) {
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)

            if (!block.chunk.isLoaded) return false
            if (block.type.isAir) return false

            val context = Predicate.PredicateContext(position = ghost.relativePos, block = block)
            if (!ghost.predicate.testMaterialOnly(block.blockData, context)) return false
        }

        return true
    }

    fun triggerAssembled(): Boolean {
        if (state != BuildSiteState.AWAITING_CORE) return false

        val world = anchorLocation.world ?: return false

        Bukkit.getLogger().info("[BuildSite] triggerAssembled: 开始成型流程")

        val coreBlock = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
        val coreBlockType = coreBlock.type
        val coreBlockData = coreBlock.blockData.clone()

        AutoFixer.fixAssembledState(blueprint.assembledShape, anchorLocation, facing, blueprint.meta.controllerOffset)

        Bukkit.getLogger().info("[BuildSite] triggerAssembled: 已应用 assembledShape")

        if (blueprint.displayEntities.isNotEmpty()) {
            top.mc506lw.monolith.feature.display.DisplayEntityManager.spawnDisplayEntities(
                siteId = id,
                displayEntities = blueprint.displayEntities,
                anchorLocation = anchorLocation,
                facing = facing,
                centerOffset = blueprint.meta.controllerOffset
            )
        }

        state = BuildSiteState.COMPLETED
        removeAllRenderings()
        removeCoreGlass()

        return true
    }

    fun revertToBuilding() {
        if (state != BuildSiteState.COMPLETED) {
            Bukkit.getLogger().warning("[BuildSite] revertToBuilding(): 状态不是COMPLETED, 当前状态=$state, 跳过")
            return
        }

        Bukkit.getLogger().info("[BuildSite] revertToBuilding(): 开始恢复到建筑阶段")
        Bukkit.getLogger().info("[BuildSite] revertToBuilding(): siteId=$id, blueprintId=$blueprintId")

        val world = anchorLocation.world ?: return

        top.mc506lw.monolith.feature.display.DisplayEntityManager.removeAllForSite(id)
        Bukkit.getLogger().info("[BuildSite] revertToBuilding(): 已移除展示实体")

        val scaffoldShape = blueprint.scaffoldShape
        val transform = CoordinateTransform(facing)
        val anchorPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )
        val controllerOffset = blueprint.meta.controllerOffset

        var restoredCount = 0
        var errorCount = 0

        for (blockEntry in scaffoldShape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = anchorPos,
                relativePos = blockEntry.position,
                centerOffset = controllerOffset
            )

            if (!world.isChunkLoaded(worldPos.x shr 4, worldPos.z shr 4)) continue

            try {
                val block = world.getBlockAt(worldPos.x, worldPos.y, worldPos.z)

                if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(block)) {
                    Bukkit.getLogger().info("[BuildSite] revertToBuilding(): 跳过Rebar方块 $worldPos (${block.type})")
                    continue
                }

                block.blockData = blockEntry.blockData.clone()
                restoredCount++
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[BuildSite] revertToBuilding(): 恢复方块失败 $worldPos: ${e.message}")
                errorCount++
            }
        }

        placedBlocks.clear()
        currentLayer = 0
        isCompleted = false
        state = BuildSiteState.BUILDING_LAYERS

        Bukkit.getLogger().info("[BuildSite] revertToBuilding(): 完成! 恢复了" + restoredCount + "个方块, " + errorCount + "个错误")
    }

    fun disassembleToBuilding() {
        if (state != BuildSiteState.COMPLETED) {
            Bukkit.getLogger().warning("[BuildSite] disassembleToBuilding(): 状态不是COMPLETED, 当前状态=$state, 跳过")
            return
        }

        Bukkit.getLogger().info("[BuildSite] disassembleToBuilding(): 重置工地状态 siteId=$id")

        top.mc506lw.monolith.feature.display.DisplayEntityManager.removeAllForSite(id)

        placedBlocks.clear()
        currentLayer = 0
        isCompleted = false
        state = BuildSiteState.BUILDING_LAYERS

        Bukkit.getLogger().info("[BuildSite] disassembleToBuilding(): 完成! 已重置为BUILDING_LAYERS状态")
    }

    private fun spawnCoreItemDisplay(world: org.bukkit.World) {
        if (coreItemDisplay != null && coreItemDisplay!!.isValid) return

        coreItemDisplay?.remove()

        Bukkit.getLogger().info("[BuildSite] spawnCoreItemDisplay: anchorLocation=$anchorLocation, coreWorldPos=$coreWorldPos, centerOffset=${blueprint.meta.controllerOffset}, facing=$facing")

        val location = Location(world,
            coreWorldPos.x.toDouble() + 0.5,
            coreWorldPos.y.toDouble() + 0.5,
            coreWorldPos.z.toDouble() + 0.5)

        try {
            val display = world.spawn(location, ItemDisplay::class.java) { d ->
                val coreGhost = getCoreBlock()
                val coreItem = if (coreGhost != null && coreRebarKey != null) {
                    ItemStack(coreGhost.previewBlockData.material)
                } else if (coreRebarKey != null) {
                    ItemStack(Material.FURNACE)
                } else {
                    ItemStack(Material.STRUCTURE_BLOCK)
                }
                d.setItemStack(coreItem)
                d.isGlowing = true
                d.glowColorOverride = Color.fromRGB(255, 215, 0)
                d.isPersistent = false
                d.brightness = Display.Brightness(15, 15)
                
                val rotationRad = Math.toRadians((facing.rotationSteps * 90).toDouble()).toFloat()
                d.transformation = Transformation(
                    Vector3f(0f, -0.25f, 0f),
                    AxisAngle4f(0f, 1f, 0f, rotationRad),
                    Vector3f(0.6f, 0.6f, 0.6f),
                    AxisAngle4f()
                )
            }
            Bukkit.getLogger().info("[BuildSite] spawnCoreItemDisplay: 已生成 at ${display.location}")
            coreItemDisplay = display
        } catch (_: Exception) {}
    }

    fun removeCoreItemDisplay() {
        coreItemDisplay?.remove()
        coreItemDisplay = null
    }

    private fun renderCoreMarker(world: org.bukkit.World) {
        spawnCoreItemDisplay(world)
    }

    fun canPlaceCore(pos: Vector3i): Boolean {
        return state == BuildSiteState.AWAITING_CORE && pos == coreWorldPos
    }

    fun enterAwaitingCore() {
        Bukkit.getLogger().info("[BuildSite] enterAwaitingCore: siteId=$id, 之前状态=$state")
        state = BuildSiteState.AWAITING_CORE
        clearCurrentLayerRenderings()
        val world = anchorLocation.world
        if (world != null) {
            removeCoreGlass()
            spawnCoreItemDisplay(world)
        }
        Bukkit.getLogger().info("[BuildSite] enterAwaitingCore: 完成, 新状态=$state")
    }

    fun isCorePosition(pos: Vector3i): Boolean = pos == coreWorldPos

    fun isCoreGlassPosition(pos: Vector3i): Boolean = pos == coreWorldPos && coreGlassPlaced

    fun containsPosition(pos: Vector3i): Boolean = allGhostBlocks.any { it.worldPos == pos }

    fun isAssembledPosition(pos: Vector3i): Boolean {
        if (pos.x < boundingMinX || pos.x > boundingMaxX ||
            pos.y < boundingMinY || pos.y > boundingMaxY ||
            pos.z < boundingMinZ || pos.z > boundingMaxZ) return false

        val transform = CoordinateTransform(facing)
        val centerOffset = blueprint.meta.controllerOffset
        val controllerPos = Vector3i(anchorLocation.blockX, anchorLocation.blockY, anchorLocation.blockZ)

        for (blockEntry in blueprint.assembledShape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = blockEntry.position,
                centerOffset = centerOffset
            )
            if (worldPos == pos) return true
        }

        for (blockEntry in blueprint.scaffoldShape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = blockEntry.position,
                centerOffset = centerOffset
            )
            if (worldPos == pos) return true
        }

        return false
    }

    fun getGhostAt(pos: Vector3i): SiteGhostBlock? = allGhostBlocks.find { it.worldPos == pos }

    fun getScaffoldBlockDataAt(worldPos: Vector3i): BlockData? {
        val transform = CoordinateTransform(facing)
        val anchorPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )
        val controllerOffset = blueprint.meta.controllerOffset
        for (blockEntry in blueprint.scaffoldShape.blocks) {
            val pos = transform.toWorldPosition(
                controllerPos = anchorPos,
                relativePos = blockEntry.position,
                centerOffset = controllerOffset
            )
            if (pos == worldPos) return blockEntry.blockData
        }
        for (blockEntry in blueprint.assembledShape.blocks) {
            val pos = transform.toWorldPosition(
                controllerPos = anchorPos,
                relativePos = blockEntry.position,
                centerOffset = controllerOffset
            )
            if (pos == worldPos) return blockEntry.blockData
        }
        return null
    }

    fun recordPlacement(worldPos: Vector3i) {
        if (!placedBlocks.contains(worldPos)) {
            placedBlocks.add(worldPos)
            Bukkit.getLogger().info("[Placement] + 记录放置: $worldPos (总计=${placedBlocks.size})")
        } else {
            Bukkit.getLogger().info("[Placement] = 重复记录: $worldPos (已存在)")
        }
    }

    fun hasPlacedBlock(worldPos: Vector3i): Boolean {
        val inSet = placedBlocks.contains(worldPos)

        if (inSet) {
            val world = anchorLocation.world
            if (world != null) {
                val block = world.getBlockAt(worldPos.x, worldPos.y, worldPos.z)
                if (block.type.isAir || !containsPosition(worldPos)) {
                    val warnMsg = "不同步: placedBlocks包含$worldPos 但世界方块是${block.type}, 自动移除"
                    Bukkit.getLogger().warning("[Placement] ⚠️ $warnMsg")
                    placedBlocks.remove(worldPos)
                    return false
                }
            }
        }

        return inSet
    }

    fun removePlacement(worldPos: Vector3i) {
        val removed = placedBlocks.remove(worldPos)
        Bukkit.getLogger().info("[Placement] - 移除放置: $worldPos (成功=$removed, 剩余=${placedBlocks.size})")
    }

    fun getPlacedBlockType(worldPos: Vector3i): org.bukkit.Material? {
        val world = anchorLocation.world ?: return null
        val block = world.getBlockAt(worldPos.x, worldPos.y, worldPos.z)

        return if (!block.type.isAir && !io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(block)) {
            block.type
        } else {
            null
        }
    }

    fun revertFromWaitingForCore(): Int {
        if (state != BuildSiteState.AWAITING_CORE) return -1

        Bukkit.getLogger().info("[BuildSite] revertFromWaitingForCore: 开始回退, siteId=$id, 当前currentLayer=$currentLayer")

        removeCoreItemDisplay()

        state = BuildSiteState.BUILDING_LAYERS

        var targetLayer = -1

        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) {
                targetLayer = i
                Bukkit.getLogger().info("[BuildSite] revertFromWaitingForCore: 找到未完成层 $i (Y=${layerYLevels[i]})")
                break
            }
        }

        if (targetLayer == -1) {
            targetLayer = 0
            Bukkit.getLogger().info("[BuildSite] revertFromWaitingForCore: 所有层都已完成，回退到第0层")
        }

        currentLayer = targetLayer

        Bukkit.getLogger().info("[BuildSite] revertFromWaitingForCore: 完成! 新的currentLayer=$currentLayer")

        return targetLayer
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

    fun markCompleted() {
        isCompleted = true
        removeCoreItemDisplay()
        removeAllRenderings()
        removeCoreGlass()
    }

    fun validateDetailed(): DetailedValidationResult {
        val world = anchorLocation.world ?: return DetailedValidationResult(
            isComplete = false, matchedCount = 0, totalCount = allGhostBlocks.size,
            completionRate = 0.0, blocksToFix = emptyList(), missingBlocks = allGhostBlocks.map { it.worldPos }
        )

        var matchedCount = 0
        val blocksToFix = mutableListOf<BlockFixEntry>()
        val missingBlocks = mutableListOf<Vector3i>()

        for (ghostBlock in allGhostBlocks) {
            val blockLocation = Location(world, ghostBlock.worldPos.x.toDouble(), ghostBlock.worldPos.y.toDouble(), ghostBlock.worldPos.z.toDouble())
            
            if (!blockLocation.chunk.isLoaded) {
                missingBlocks.add(ghostBlock.worldPos)
                continue
            }

            val actualBlock = blockLocation.block
            val actualBlockData = actualBlock.blockData
            val context = Predicate.PredicateContext(position = ghostBlock.relativePos, block = actualBlock)

            if (ghostBlock.predicate.testMaterialOnly(actualBlockData, context)) {
                matchedCount++
                if (!ghostBlock.predicate.test(actualBlockData, context)) {
                    blocksToFix.add(BlockFixEntry(
                        worldPos = ghostBlock.worldPos,
                        relativePos = ghostBlock.relativePos,
                        targetBlockData = ghostBlock.previewBlockData,
                        currentBlockData = actualBlockData
                    ))
                }
            } else {
                Bukkit.getLogger().info("[BuildSite.validateDetailed] 不匹配: relPos=${ghostBlock.relativePos}, worldPos=${ghostBlock.worldPos}, " +
                    "期望=${ghostBlock.previewBlockData.asString}, 实际=${actualBlockData.asString}, isCore=${ghostBlock.isCore}, predicateType=${ghostBlock.predicate::class.simpleName}")
                missingBlocks.add(ghostBlock.worldPos)
            }
        }

        val totalCount = allGhostBlocks.size
        val completionRate = if (totalCount > 0) matchedCount.toDouble() / totalCount else 0.0
        
        Bukkit.getLogger().info("[BuildSite.validateDetailed] 结果: matched=$matchedCount, total=$totalCount, missing=${missingBlocks.size}, toFix=${blocksToFix.size}")

        return DetailedValidationResult(
            isComplete = matchedCount == totalCount,
            matchedCount = matchedCount,
            totalCount = totalCount,
            completionRate = completionRate,
            blocksToFix = blocksToFix,
            missingBlocks = missingBlocks
        )
    }

    private fun clearCurrentLayerRenderings() {
        displayEntities.values.forEach { it.remove() }
        displayEntities.clear()
        playerRenderCache.clear()
    }

    companion object {
        const val RENDER_RADIUS = 7
        const val UNLOAD_DISTANCE = 50
    }
}
