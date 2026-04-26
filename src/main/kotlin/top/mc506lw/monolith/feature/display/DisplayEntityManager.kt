package top.mc506lw.monolith.feature.display

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.transform.BlockStateRotator
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DisplayEntityManager {

    private val siteDisplays = ConcurrentHashMap<UUID, MutableMap<Vector3i, BlockDisplay>>()

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

        val displays = mutableMapOf<Vector3i, BlockDisplay>()
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
                displays[worldPos] = display
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
        val displays = siteDisplays.remove(siteId) ?: return 0

        var removed = 0
        for ((_, display) in displays) {
            try {
                if (display.isValid) {
                    display.remove()
                    removed++
                }
            } catch (_: Exception) {}
        }

        return removed
    }

    fun hasDisplaysForSite(siteId: UUID): Boolean = siteDisplays.containsKey(siteId)

    fun cleanup() {
        for ((_, displays) in siteDisplays) {
            for ((_, display) in displays) {
                try {
                    display.remove()
                } catch (_: Exception) {}
            }
        }
        siteDisplays.clear()
    }
}
