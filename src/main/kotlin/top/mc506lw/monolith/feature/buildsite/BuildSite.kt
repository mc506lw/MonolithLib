package top.mc506lw.monolith.feature.buildsite

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Quaternionf
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
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.Predicates
import top.mc506lw.monolith.validation.predicate.RotatedPredicate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class BuildSiteState {
    BUILDING,
    AWAITING_CORE,
    VIRTUAL
}

object BlueprintItem {

    private val BLUEPRINT_KEY = NamespacedKey("monolithlib", "blueprint_id")
    private val BLUEPRINT_FACING_KEY = NamespacedKey("monolithlib", "blueprint_facing")

    fun create(blueprintId: String): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName(net.kyori.adventure.text.Component.text("\u00a7b蓝图: \u00a7f$blueprintId"))
        meta.lore(listOf(
            net.kyori.adventure.text.Component.text("\u00a77右键放置以创建工地"),
            net.kyori.adventure.text.Component.text("\u00a77蓝图ID: \u00a7e$blueprintId")
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
    var blueprint: Blueprint,
    val anchorLocation: Location,
    val facing: Facing,
    initialLayer: Int = 0,
    initialPlacedBlocks: Set<Vector3i> = emptySet(),
    initialState: BuildSiteState = BuildSiteState.BUILDING
) {
    val blueprintId: String get() = blueprint.id

    val transform: CoordinateTransform = CoordinateTransform(facing)

    var currentLayer: Int = initialLayer
        private set

    var state: BuildSiteState = initialState
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

    private val ghostDisplayEntities = ConcurrentHashMap<Vector3i, BlockDisplay>()
    private val playerRenderCache = ConcurrentHashMap<UUID, Set<Vector3i>>()
    private var coreGlassPlaced: Boolean = false
    private var coreItemDisplay: ItemDisplay? = null

    val backupData: MutableMap<Vector3i, BlockData> = ConcurrentHashMap()

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
        if (state == BuildSiteState.VIRTUAL) return

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

        val existing = ghostDisplayEntities[ghost.worldPos]
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
            ghostDisplayEntities[ghost.worldPos] = display
        } catch (_: Exception) {}
    }

    private fun cleanupStaleEntities(visiblePositions: Set<Vector3i>) {
        val toRemove = ghostDisplayEntities.keys.filter { it !in visiblePositions }
        for (pos in toRemove) {
            ghostDisplayEntities[pos]?.remove()
            ghostDisplayEntities.remove(pos)
        }
    }

    fun checkLayerCompletion(): Boolean {
        if (state == BuildSiteState.AWAITING_CORE || state == BuildSiteState.VIRTUAL || isCompleted) return false

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

    fun advanceToNextIncompleteLayer(): Int {
        if (isLastLayer()) return 0

        var advancedCount = 0

        while (currentLayer < totalLayers - 1) {
            currentLayer++
            advancedCount++
            clearCurrentLayerRenderings()

            if (!isLayerFullyPlaced(currentLayer)) {
                break
            }
        }

        return advancedCount
    }

    fun isLastLayer(): Boolean = currentLayer >= totalLayers - 1

    fun isLayerFullyPlaced(layerIndex: Int): Boolean {
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

    fun checkIfAllNonCoreLayersComplete(): Boolean {
        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) return false
        }
        return true
    }

    fun findFirstIncompleteLayer(): Int {
        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) return i
        }
        return -1
    }

    fun enterAwaitingCore() {
        if (state == BuildSiteState.AWAITING_CORE) return

        state = BuildSiteState.AWAITING_CORE
        clearCurrentLayerRenderings()
        removeCoreGlass()

        val world = anchorLocation.world
        if (world != null) {
            spawnCoreItemDisplay(world)
        }
    }

    fun forceEnterAwaitingCore() {
        if (state == BuildSiteState.AWAITING_CORE) return

        val lastLayerIndex = (totalLayers - 1).coerceAtLeast(0)
        currentLayer = lastLayerIndex

        clearCurrentLayerRenderings()
        removeCoreGlass()

        state = BuildSiteState.AWAITING_CORE

        spawnCoreItemDisplay(anchorLocation.world)
    }

    fun advanceToLayer(targetLayer: Int) {
        if (targetLayer < 0 || targetLayer >= totalLayers) return
        if (targetLayer <= currentLayer) return

        currentLayer = targetLayer
        clearCurrentLayerRenderings()
    }

    fun transitionToVirtual() {
        if (state == BuildSiteState.VIRTUAL) return

        val world = anchorLocation.world ?: return

        Bukkit.getLogger().info("[BuildSite] transitionToVirtual: 开始, siteId=$id, blueprintId=$blueprintId")

        backupData.clear()
        for (ghost in allGhostBlocks) {
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            backupData[ghost.worldPos] = block.blockData.clone()
        }

        for (ghost in allGhostBlocks) {
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            block.setType(Material.STRUCTURE_VOID, false)
        }

        removeCoreItemDisplay()
        clearCurrentLayerRenderings()
        removeCoreGlass()

        placeAnchorAndSpawnEntities(world)

        state = BuildSiteState.VIRTUAL
    }

    private fun placeAnchorAndSpawnEntities(world: org.bukkit.World) {
        val coreBlock = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)

        io.github.pylonmc.rebar.block.BlockStorage.placeBlock(
            coreBlock,
            top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor.KEY
        )

        Bukkit.getLogger().info("[BuildSite] placeAnchorAndSpawnEntities: 已放置Anchor方块, 等待Rebar初始化... pos=(${coreWorldPos.x},${coreWorldPos.y},${coreWorldPos.z})")

        org.bukkit.Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
            val anchor = io.github.pylonmc.rebar.block.BlockStorage.get(coreBlock)
            if (anchor == null) {
                Bukkit.getLogger().severe("[BuildSite] placeAnchorAndSpawnEntities: ❌ 无法获取Anchor! pos=(${coreWorldPos.x},${coreWorldPos.y},${coreWorldPos.z})")
                return@Runnable
            }
            if (anchor !is top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor) {
                Bukkit.getLogger().severe("[BuildSite] placeAnchorAndSpawnEntities: ❌ Anchor类型错误! actual=${anchor::class.simpleName}")
                return@Runnable
            }

            Bukkit.getLogger().info("[BuildSite] placeAnchorAndSpawnEntities: ✅ Anchor已就位, 开始注册展示实体...")

            spawnDisplayEntitiesViaAnchor(anchor)
        }, 1L)
    }

    private fun spawnDisplayEntitiesViaAnchor(
        anchor: top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor
    ) {
        val displayEntityBlocks = blueprint.displayEntities.filter {
            it.entityType == top.mc506lw.monolith.core.model.DisplayType.BLOCK && it.blockData != null &&
            it.blockData!!.material != Material.STRUCTURE_VOID && it.blockData!!.material != Material.AIR
        }

        if (displayEntityBlocks.isEmpty()) {
            Bukkit.getLogger().warning("[BuildSite] spawnDisplayEntitiesViaAnchor: 无有效展示实体数据!")
            return
        }

        val latestBlueprint = top.mc506lw.monolith.api.MonolithAPI.getInstance().registry.get(blueprintId)
        val effectiveDisplayOffset = latestBlueprint?.meta?.displayOffset ?: blueprint.meta.displayOffset

        val controllerPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )

        val rotationSteps = facing.rotationSteps

        Bukkit.getLogger().info("[BuildSite] spawnDisplayEntitiesViaAnchor: 通过Rebar Anchor管理${displayEntityBlocks.size}个实体, 旋转步数=$rotationSteps")

        val groupCenter = calculateGroupCenter(displayEntityBlocks)
        Bukkit.getLogger().info("[BuildSite] 组质心=(${String.format("%.4f", groupCenter.x)}, ${String.format("%.4f", groupCenter.y)}, ${String.format("%.4f", groupCenter.z)})")

        var successCount = 0
        var failCount = 0

        for ((index, entity) in displayEntityBlocks.withIndex()) {
            try {
                val entityName = "${top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor.ENTITY_PREFIX}$index"

                if (anchor.isHeldEntityPresent(entityName)) {
                    Bukkit.getLogger().info("[BuildSite]   [$index] 实体已存在, 跳过: $entityName")
                    successCount++
                    continue
                }

                val relOffset = org.joml.Vector3f(
                    entity.translation.x - groupCenter.x,
                    entity.translation.y - groupCenter.y,
                    entity.translation.z - groupCenter.z
                )

                val rotatedOffset = rotateRelativeOffsetForGroup(relOffset, rotationSteps)

                val finalTrans = org.joml.Vector3f(
                    groupCenter.x + rotatedOffset.x + effectiveDisplayOffset.x,
                    groupCenter.y + rotatedOffset.y + effectiveDisplayOffset.y,
                    groupCenter.z + rotatedOffset.z + effectiveDisplayOffset.z
                )

                val baseX = anchorLocation.x.toDouble()
                val baseY = anchorLocation.y.toDouble()
                val baseZ = anchorLocation.z.toDouble()

                val worldX = baseX + finalTrans.x
                val worldY = baseY + finalTrans.y
                val worldZ = baseZ + finalTrans.z

                val spawnLoc = Location(anchorLocation.world, worldX, worldY, worldZ)

                val leftRotation = applyFacingRotationForAnchor(entity.rotation, rotationSteps)

                val scaleX = when (rotationSteps) { 1, 3 -> entity.scale.z else -> entity.scale.x }
                val scaleZ = when (rotationSteps) { 1, 3 -> entity.scale.x else -> entity.scale.z }

                val transformMatrix = org.joml.Matrix4f()
                    .translation(0f, 0f, 0f)
                    .rotate(leftRotation)
                    .scale(scaleX, entity.scale.y, scaleZ)

                val blockDisplay = io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder()
                    .material(entity.blockData!!.material)
                    .transformation(transformMatrix)
                    .build(spawnLoc)

                anchor.addEntity(entityName, blockDisplay)
                successCount++

                if (index < 3 || index == displayEntityBlocks.size - 1) {
                    Bukkit.getLogger().info("[BuildSite]   [$index/${displayEntityBlocks.size}] 注册到Anchor: $entityName | 世界坐标=($worldX, $worldY, $worldZ)")
                }
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[BuildSite] spawnDisplayEntitiesViaAnchor: 实体[$index]生成失败: ${e.message}")
                e.printStackTrace()
                failCount++
            }
        }

        Bukkit.getLogger().info("[BuildSite] spawnDisplayEntitiesViaAnchor: ✅ 完成! 成功=$successCount, 失败=$failCount (全部由Rebar Anchor管理)")
    }

    private fun calculateGroupCenter(entities: List<top.mc506lw.monolith.core.model.DisplayEntityData>): org.joml.Vector3f {
        if (entities.isEmpty()) return org.joml.Vector3f()
        
        var cx = 0f
        var cy = 0f
        var cz = 0f
        
        for (e in entities) {
            cx += e.translation.x
            cy += e.translation.y
            cz += e.translation.z
        }
        
        return org.joml.Vector3f(cx / entities.size, cy / entities.size, cz / entities.size)
    }

    private fun rotateRelativeOffsetForGroup(offset: org.joml.Vector3f, steps: Int): org.joml.Vector3f {
        if (steps == 0) return offset

        return when (steps % 4) {
            1 -> org.joml.Vector3f(offset.z, offset.y, -offset.x)
            2 -> org.joml.Vector3f(-offset.x, offset.y, -offset.z)
            3 -> org.joml.Vector3f(-offset.z, offset.y, offset.x)
            else -> org.joml.Vector3f(offset)
        }
    }

    private fun applyFacingRotationForAnchor(originalRotation: org.joml.Quaternionf, rotationSteps: Int): org.joml.Quaternionf {
        if (rotationSteps == 0) return org.joml.Quaternionf(originalRotation)

        val facingRotation = when (rotationSteps) {
            1 -> org.joml.Quaternionf().rotateY(kotlin.math.PI.toFloat() / 2f)
            2 -> org.joml.Quaternionf().rotateY(kotlin.math.PI.toFloat())
            3 -> org.joml.Quaternionf().rotateY(-kotlin.math.PI.toFloat() / 2f)
            else -> org.joml.Quaternionf()
        }

        return org.joml.Quaternionf(facingRotation).mul(originalRotation)
    }

    private fun buildDisplayShapeFromBackup(): Shape {
        val entries = mutableListOf<top.mc506lw.monolith.core.model.BlockEntry>()

        for ((worldPos, blockData) in backupData) {
            if (blockData.material == Material.STRUCTURE_VOID ||
                blockData.material == Material.AIR ||
                blockData.material == Material.RED_STAINED_GLASS) continue

            val relativePos = transform.toRelativePosition(
                worldPos = worldPos,
                controllerPos = Vector3i(anchorLocation.blockX, anchorLocation.blockY, anchorLocation.blockZ),
                centerOffset = blueprint.meta.controllerOffset
            )
            entries.add(top.mc506lw.monolith.core.model.BlockEntry(relativePos, blockData))
        }

        return Shape(entries)
    }

    fun disassembleFromVirtual(): Boolean {
        if (state != BuildSiteState.VIRTUAL) {
            Bukkit.getLogger().warning("[BuildSite] disassembleFromVirtual: 状态不是VIRTUAL! 当前状态=$state, siteId=$id")
            return false
        }

        val world = anchorLocation.world ?: return false

        Bukkit.getLogger().info("[BuildSite] disassembleFromVirtual: 开始解体, siteId=$id")

        val coreBlock = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
        val anchor = io.github.pylonmc.rebar.block.BlockStorage.get(coreBlock)

        if (anchor is top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor) {
            anchor.tryRemoveAllEntities()
            Bukkit.getLogger().info("[BuildSite] disassembleFromVirtual: Anchor已清理所有展示实体")
        } else {
            Bukkit.getLogger().warning("[BuildSite] disassembleFromVirtual: 未找到Anchor或类型不匹配, actual=${anchor?.let { it::class.simpleName }}")
        }

        if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(coreBlock)) {
            io.github.pylonmc.rebar.block.BlockStorage.breakBlock(coreBlock)
            Bukkit.getLogger().info("[BuildSite] disassembleFromVirtual: 已通过breakBlock移除Anchor")
        } else {
            coreBlock.setType(Material.AIR, false)
        }

        for ((pos, originalData) in backupData) {
            if (!world.isChunkLoaded(pos.x shr 4, pos.z shr 4)) continue
            val block = world.getBlockAt(pos.x, pos.y, pos.z)
            if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(block)) continue
            block.setBlockData(originalData.clone(), false)
        }

        backupData.clear()

        state = BuildSiteState.AWAITING_CORE

        val lastLayerIndex = (totalLayers - 1).coerceAtLeast(0)
        currentLayer = lastLayerIndex

        spawnCoreItemDisplay(world)

        return true
    }

    private fun spawnCoreItemDisplay(world: org.bukkit.World) {
        if (coreItemDisplay != null && coreItemDisplay!!.isValid) return

        coreItemDisplay?.remove()

        val location = Location(world,
            coreWorldPos.x.toDouble() + 0.5,
            coreWorldPos.y.toDouble() + 0.5,
            coreWorldPos.z.toDouble() + 0.5)

        try {
            val display = world.spawn(location, ItemDisplay::class.java) { d ->
                val coreItem = when {
                    coreRebarKey != null -> {
                        try {
                            io.github.pylonmc.rebar.item.builder.ItemStackBuilder
                                .rebar(Material.LODESTONE, coreRebarKey)
                                .build()
                        } catch (e: Exception) {
                            ItemStack(Material.LODESTONE)
                        }
                    }
                    else -> ItemStack(Material.STRUCTURE_BLOCK)
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

    fun isCorePosition(pos: Vector3i): Boolean = pos == coreWorldPos

    fun isNearCorePosition(pos: Vector3i): Boolean {
        val dx = kotlin.math.abs(pos.x - coreWorldPos.x)
        val dy = kotlin.math.abs(pos.y - coreWorldPos.y)
        val dz = kotlin.math.abs(pos.z - coreWorldPos.z)
        return (dx + dy + dz) <= 1 && pos != coreWorldPos
    }

    fun isCoreGlassPosition(pos: Vector3i): Boolean = pos == coreWorldPos && coreGlassPlaced

    fun containsPosition(pos: Vector3i): Boolean = allGhostBlocks.any { it.worldPos == pos }

    fun isVirtualPosition(pos: Vector3i): Boolean {
        if (state != BuildSiteState.VIRTUAL) return false
        return allGhostBlocks.any { it.worldPos == pos }
    }

    fun getGhostAt(pos: Vector3i): SiteGhostBlock? = allGhostBlocks.find { it.worldPos == pos }

    fun recordPlacement(worldPos: Vector3i) {
        placedBlocks.add(worldPos)
    }

    fun hasPlacedBlock(worldPos: Vector3i): Boolean {
        return placedBlocks.contains(worldPos)
    }

    fun removePlacement(worldPos: Vector3i) {
        placedBlocks.remove(worldPos)
    }

    fun revertToBuilding(): Int {
        if (state != BuildSiteState.AWAITING_CORE) return -1

        removeCoreItemDisplay()

        state = BuildSiteState.BUILDING

        val world = anchorLocation.world
        if (world != null) {
            val coreBlock = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
            if (coreBlock.type != Material.RED_STAINED_GLASS && coreBlock.type != Material.AIR) {
                val isRebar = io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(coreBlock)
                if (isRebar) {
                    try {
                        val rebarBlock = io.github.pylonmc.rebar.block.BlockStorage.get(coreBlock)
                        if (rebarBlock != null) {
                            val rebarItem = io.github.pylonmc.rebar.item.builder.ItemStackBuilder
                                .rebar(coreBlock.type, (rebarBlock as io.github.pylonmc.rebar.block.RebarBlock).schema.key)
                                .build()
                            coreBlock.world.dropItemNaturally(coreBlock.location, rebarItem)
                        }
                    } catch (e: Exception) {
                        val fallbackItem = ItemStack(coreBlock.type)
                        coreBlock.world.dropItemNaturally(coreBlock.location, fallbackItem)
                    }
                } else {
                    val dropItem = ItemStack(coreBlock.type)
                    coreBlock.world.dropItemNaturally(coreBlock.location, dropItem)
                }
                coreBlock.setType(Material.AIR, false)
            }
        }

        if (world != null) {
            placeCoreGlassIfAbsent(world)
        }

        var targetLayer = -1
        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) {
                targetLayer = i
                break
            }
        }

        if (targetLayer == -1) {
            targetLayer = 0
        }

        currentLayer = targetLayer

        return targetLayer
    }

    fun revertFromCompleted(player: Player? = null): Boolean {
        if (!isCompleted && state != BuildSiteState.VIRTUAL) return false

        val world = anchorLocation.world ?: return false

        isCompleted = false

        for ((pos, originalData) in backupData) {
            if (!world.isChunkLoaded(pos.x shr 4, pos.z shr 4)) continue
            val block = world.getBlockAt(pos.x, pos.y, pos.z)
            if (block.type == Material.STRUCTURE_VOID) {
                block.blockData = originalData.clone()
            }
        }

        val coreBlock = world.getBlockAt(coreWorldPos.x, coreWorldPos.y, coreWorldPos.z)
        if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(coreBlock)) {
            val anchor = io.github.pylonmc.rebar.block.BlockStorage.get(coreBlock)
            if (anchor is top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor) {
                anchor.tryRemoveAllEntities()
            }
            io.github.pylonmc.rebar.block.BlockStorage.breakBlock(coreBlock)
        } else {
            coreBlock.setType(Material.AIR, false)
        }

        removeAllRenderings()
        removeCoreItemDisplay()
        removeCoreGlass()

        state = BuildSiteState.BUILDING

        var targetLayer = -1
        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) {
                targetLayer = i
                break
            }
        }
        currentLayer = if (targetLayer >= 0) targetLayer else 0

        placedBlocks.clear()

        for (ghost in allGhostBlocks) {
            if (ghost.isCore) continue
            val block = anchorLocation.world?.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z) ?: continue
            if (ghost.predicate.testMaterialOnly(block.blockData, Predicate.PredicateContext(position = ghost.relativePos, block = block))) {
                placedBlocks.add(ghost.worldPos)
            }
        }

        var allLayersDone = true
        for (i in layerYLevels.indices) {
            if (!isLayerFullyPlaced(i)) {
                allLayersDone = false
                break
            }
        }

        if (allLayersDone) {
            enterAwaitingCore()
            player?.sendMessage("§e[MonolithLib] §f所有层已完整，请放置核心控制器")
        } else {
            val advanced = advanceToNextIncompleteLayer()
            if (advanced > 0) {
                player?.sendMessage("§e[MonolithLib] §f结构已解体，重新进入建造模式 (当前层: ${currentLayer + 1})")
            } else {
                player?.sendMessage("§e[MonolithLib] §f结构已解体，重新进入建造模式")
            }
        }

        return true
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
        ghostDisplayEntities.values.forEach { it.remove() }
        ghostDisplayEntities.clear()
        playerRenderCache.clear()
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

    internal fun restoreCompleted() {
        isCompleted = true
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
                missingBlocks.add(ghostBlock.worldPos)
            }
        }

        val totalCount = allGhostBlocks.size
        val completionRate = if (totalCount > 0) matchedCount.toDouble() / totalCount else 0.0

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
        ghostDisplayEntities.values.forEach { it.remove() }
        ghostDisplayEntities.clear()
        playerRenderCache.clear()
    }

    companion object {
        const val RENDER_RADIUS = 7
        const val UNLOAD_DISTANCE = 50
    }
}
