package top.mc506lw.monolith.feature.display

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import top.mc506lw.monolith.core.math.Matrix3x3
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.DisplayEntityData
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DisplayEntityManager {

    private val siteDisplays = ConcurrentHashMap<UUID, MutableList<Display>>()

    fun spawnVirtualViewFromEntities(
        siteId: UUID,
        entities: List<DisplayEntityData>,
        anchorLocation: Location,
        facing: Facing,
        controllerOffset: Vector3i,
        displayOffset: Vector3i = Vector3i.ZERO,
        scaffoldWorldPositions: List<Vector3i> = emptyList()
    ): Int {
        val world = anchorLocation.world ?: return 0
        val rotationSteps = facing.rotationSteps
        val rotationMatrix = when (rotationSteps) {
            0 -> Matrix3x3.IDENTITY
            1 -> Matrix3x3.rotationY90()
            2 -> Matrix3x3.rotationY180()
            3 -> Matrix3x3.rotationY270()
            else -> Matrix3x3.IDENTITY
        }
        val controllerPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )

        val effectiveOffset = if (scaffoldWorldPositions.isNotEmpty() && entities.isNotEmpty()) {
            val scMinX = scaffoldWorldPositions.minOf { it.x }.toDouble()
            val scMinY = scaffoldWorldPositions.minOf { it.y }.toDouble()
            val scMinZ = scaffoldWorldPositions.minOf { it.z }.toDouble()

            var deMinX = Double.MAX_VALUE
            var deMinY = Double.MAX_VALUE
            var deMinZ = Double.MAX_VALUE
            for (e in entities) {
                val ex = controllerPos.x + rotationMatrix.m00 * e.translation.x + rotationMatrix.m02 * e.translation.z
                val ey = controllerPos.y + e.translation.y.toDouble()
                val ez = controllerPos.z + rotationMatrix.m20 * e.translation.x + rotationMatrix.m22 * e.translation.z
                val hx = kotlin.math.abs(rotationMatrix.m00 * e.scale.x / 2 + rotationMatrix.m02 * e.scale.z / 2)
                val hy = e.scale.y / 2
                val hz = kotlin.math.abs(rotationMatrix.m20 * e.scale.x / 2 + rotationMatrix.m22 * e.scale.z / 2)
                deMinX = minOf(deMinX, ex - hx)
                deMinY = minOf(deMinY, ey - hy)
                deMinZ = minOf(deMinZ, ez - hz)
            }

            val autoX = kotlin.math.round(scMinX - deMinX).toInt()
            val autoY = kotlin.math.round(scMinY - deMinY).toInt()
            val autoZ = kotlin.math.round(scMinZ - deMinZ).toInt()

            Bukkit.getLogger().info("[DisplayEntityManager] 自动对齐(角点): scaffold_min=($scMinX,$scMinY,$scMinZ), DE_min=($deMinX,$deMinY,$deMinZ), auto=($autoX,$autoY,$autoZ), 用户配置=$displayOffset")

            Vector3i(autoX + displayOffset.x, autoY + displayOffset.y, autoZ + displayOffset.z)
        } else {
            displayOffset
        }

        Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualViewFromEntities: 开始, siteId=$siteId, entities=${entities.size}, controllerPos=$controllerPos, facing=$facing, 最终offset=$effectiveOffset")

        val displays = mutableListOf<Display>()
        var successCount = 0
        var failCount = 0

        for ((index, entity) in entities.withIndex()) {
            val rotTransX = rotationMatrix.m00 * entity.translation.x + rotationMatrix.m02 * entity.translation.z
            val rotTransY = entity.translation.y.toDouble()
            val rotTransZ = rotationMatrix.m20 * entity.translation.x + rotationMatrix.m22 * entity.translation.z

            val worldX = controllerPos.x + rotTransX + effectiveOffset.x
            val worldY = controllerPos.y + rotTransY + effectiveOffset.y.toDouble()
            val worldZ = controllerPos.z + rotTransZ + effectiveOffset.z

            val location = Location(world, worldX, worldY, worldZ)

            if (index < 3) {
                Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualViewFromEntities: entity[$index] pos=${entity.position}, translation=(${entity.translation.x}, ${entity.translation.y}, ${entity.translation.z}), scale=(${entity.scale.x}, ${entity.scale.y}, ${entity.scale.z}), rotTrans=($rotTransX, $rotTransY, $rotTransZ), spawnLoc=($worldX, $worldY, $worldZ)")
            }

            try {
                val display = when (entity.entityType) {
                    top.mc506lw.monolith.core.model.DisplayType.BLOCK -> {
                        if (entity.blockData == null) continue
                        val rotatedBlockData = BlockStateRotator.rotate(entity.blockData.clone(), rotationSteps)
                        world.spawn(location, BlockDisplay::class.java) { d ->
                            d.block = rotatedBlockData
                            d.transformation = buildTransformation(entity, rotationSteps)
                            d.isPersistent = false
                            d.brightness = Display.Brightness(15, 15)
                        }
                    }
                    top.mc506lw.monolith.core.model.DisplayType.ITEM -> {
                        val itemStackCopy = entity.itemStack?.clone()
                        world.spawn(location, ItemDisplay::class.java) { d ->
                            if (itemStackCopy != null) d.setItemStack(itemStackCopy)
                            d.transformation = buildTransformation(entity, rotationSteps)
                            d.isPersistent = false
                            d.brightness = Display.Brightness(15, 15)
                        }
                    }
                }
                displays.add(display)
                successCount++
            } catch (e: Exception) {
                failCount++
                if (failCount <= 3) {
                    Bukkit.getLogger().warning("[DisplayEntityManager] spawnVirtualViewFromEntities: 生成失败 at ($worldX, $worldY, $worldZ): ${e.message}")
                }
            }
        }

        if (displays.isNotEmpty()) {
            siteDisplays[siteId] = displays
        }

        Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualViewFromEntities: 完成! 成功=$successCount, 失败=$failCount, 总计=${entities.size}")

        return successCount
    }

    private fun buildTransformation(entity: DisplayEntityData, rotationSteps: Int): Transformation {
        val leftRotation = applyFacingRotation(entity.rotation, rotationSteps)

        return Transformation(
            Vector3f(0f, 0f, 0f),
            leftRotation,
            entity.scale,
            Quaternionf()
        )
    }

    private fun applyFacingRotation(originalRotation: Quaternionf, rotationSteps: Int): Quaternionf {
        if (rotationSteps == 0) return Quaternionf(originalRotation)

        val facingRotation = when (rotationSteps) {
            1 -> Quaternionf().rotateY(kotlin.math.PI.toFloat() / 2f)
            2 -> Quaternionf().rotateY(kotlin.math.PI.toFloat())
            3 -> Quaternionf().rotateY(-kotlin.math.PI.toFloat() / 2f)
            else -> Quaternionf()
        }

        return Quaternionf(facingRotation).mul(originalRotation)
    }

    fun spawnVirtualView(
        siteId: UUID,
        assembledShape: Shape,
        anchorLocation: Location,
        facing: Facing,
        controllerOffset: Vector3i
    ): Int {
        val world = anchorLocation.world ?: return 0
        val transform = CoordinateTransform(facing)
        val rotationSteps = facing.rotationSteps
        val controllerPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )

        Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualView: 开始, siteId=$siteId")
        Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualView: assembledShape.blocks.size=${assembledShape.blocks.size}, controllerPos=$controllerPos, facing=$facing, controllerOffset=$controllerOffset")

        val displays = mutableListOf<Display>()
        var successCount = 0
        var failCount = 0

        for ((index, entry) in assembledShape.blocks.withIndex()) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = entry.position,
                centerOffset = controllerOffset
            )

            if (!world.isChunkLoaded(worldPos.x shr 4, worldPos.z shr 4)) continue

            val rotatedBlockData = BlockStateRotator.rotate(entry.blockData.clone(), rotationSteps)

            if (index < 5) {
                Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualView: entry[$index] pos=${entry.position}, blockData=${entry.blockData.material}, rotated=$rotatedBlockData, worldPos=$worldPos")
            }

            val location = Location(
                world,
                worldPos.x.toDouble(),
                worldPos.y.toDouble(),
                worldPos.z.toDouble()
            )

            try {
                val display = world.spawn(location, BlockDisplay::class.java) { d ->
                    d.block = rotatedBlockData
                    d.isPersistent = false
                    d.brightness = Display.Brightness(15, 15)
                }
                displays.add(display)
                successCount++
            } catch (e: Exception) {
                failCount++
                if (failCount <= 3) {
                    Bukkit.getLogger().warning("[DisplayEntityManager] spawnVirtualView: 生成展示实体失败 at $worldPos: ${e.message}")
                }
            }
        }

        if (displays.isNotEmpty()) {
            siteDisplays[siteId] = displays
        }

        Bukkit.getLogger().info("[DisplayEntityManager] spawnVirtualView: 完成! 成功=$successCount, 失败=$failCount, 总计=${assembledShape.blocks.size}")

        return successCount
    }

    fun removeAllForSite(siteId: UUID): Int {
        Bukkit.getLogger().info("[DisplayEntityManager] removeAllForSite: 开始, siteId=$siteId")
        val displays = siteDisplays.remove(siteId)

        if (displays == null) {
            Bukkit.getLogger().warning("[DisplayEntityManager] removeAllForSite: ⚠️ 未找到siteId=$siteId 的展示实体数据! 当前已注册的siteIds=${siteDisplays.keys}")
            return 0
        }

        Bukkit.getLogger().info("[DisplayEntityManager] removeAllForSite: 找到 ${displays.size} 个展示实体")

        var removed = 0
        for (display in displays) {
            try {
                if (display.isValid) {
                    display.remove()
                    removed++
                } else {
                    Bukkit.getLogger().info("[DisplayEntityManager] removeAllForSite: 实体已失效, 跳过")
                }
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[DisplayEntityManager] removeAllForSite: 删除实体失败: ${e.message}")
            }
        }

        Bukkit.getLogger().info("[DisplayEntityManager] removeAllForSite: 完成! 成功删除=$removed, 总数=${displays.size}")

        return removed
    }

    fun hasDisplaysForSite(siteId: UUID): Boolean = siteDisplays.containsKey(siteId)

    fun cleanup() {
        for ((_, displays) in siteDisplays) {
            for (display in displays) {
                try {
                    display.remove()
                } catch (_: Exception) {}
            }
        }
        siteDisplays.clear()
    }
}
