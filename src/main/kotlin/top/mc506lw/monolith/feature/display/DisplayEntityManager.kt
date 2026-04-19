package top.mc506lw.monolith.feature.display

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.DisplayEntityData
import top.mc506lw.monolith.core.model.DisplayType
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.Math.toRadians
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DisplayEntityManager {

    private val siteEntities = ConcurrentHashMap<UUID, MutableList<UUID>>()

    private fun rotateVectorY(vec: Vector3f, rotationSteps: Int): Vector3f {
        if (rotationSteps == 0) return vec

        val angle = toRadians((rotationSteps * 90).toDouble()).toFloat()
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)

        return Vector3f(
            vec.x * cos - vec.z * sin,
            vec.y,
            vec.x * sin + vec.z * cos
        )
    }

    private fun rotateQuaternionY(rotationSteps: Int): Quaternionf {
        if (rotationSteps == 0) return Quaternionf()

        val angle = toRadians((rotationSteps * 90).toDouble()).toFloat()
        return Quaternionf().rotateY(angle)
    }

    fun spawnDisplayEntities(
        siteId: UUID,
        displayEntities: List<DisplayEntityData>,
        anchorLocation: Location,
        facing: Facing,
        centerOffset: Vector3i = Vector3i.ZERO
    ): Int {
        val world = anchorLocation.world ?: return 0
        val transform = CoordinateTransform(facing)
        val controllerPos = Vector3i(
            anchorLocation.blockX,
            anchorLocation.blockY,
            anchorLocation.blockZ
        )
        val rotationSteps = facing.rotationSteps

        Bukkit.getLogger().info("[DisplayEntityManager] spawnDisplayEntities: anchor=$anchorLocation, facing=$facing, centerOffset=$centerOffset, count=${displayEntities.size}")

        val spawnedIds = mutableListOf<UUID>()
        var spawnedCount = 0

        for (data in displayEntities) {
                val worldPos = transform.toWorldPosition(
                    controllerPos = controllerPos,
                    relativePos = data.position,
                    centerOffset = centerOffset
                )

                if (!world.isChunkLoaded(worldPos.x shr 4, worldPos.z shr 4)) continue

                val location = Location(
                world,
                worldPos.x.toDouble(),
                worldPos.y.toDouble(),
                worldPos.z.toDouble()
            )

            val rotatedTranslation = rotateVectorY(data.translation, rotationSteps)
            val rotationQuat = rotateQuaternionY(rotationSteps)
            val rotatedRotation = Quaternionf(data.rotation).mul(rotationQuat)

            Bukkit.getLogger().info("[DisplayEntityManager] 实体#${spawnedCount + 1}: savedPos=${data.position}, originalTrans=${data.translation}, rotatedTrans=$rotatedTranslation, worldPos=$worldPos")

            try {
                val entity = when (data.entityType) {
                    DisplayType.BLOCK -> {
                        if (data.blockData != null) {
                            world.spawn(location, BlockDisplay::class.java) { d ->
                                d.block = data.blockData
                                d.transformation = Transformation(
                                    rotatedTranslation,
                                    rotatedRotation,
                                    data.scale,
                                    org.joml.Quaternionf()
                                )
                                d.isPersistent = false
                            }
                        } else null
                    }

                    DisplayType.ITEM -> {
                        if (data.itemStack != null) {
                            world.spawn(location, ItemDisplay::class.java) { d ->
                                d.setItemStack(data.itemStack)
                                d.transformation = Transformation(
                                    rotatedTranslation,
                                    rotatedRotation,
                                    data.scale,
                                    org.joml.Quaternionf()
                                )
                                d.isPersistent = false
                            }
                        } else null
                    }
                }

                if (entity != null) {
                    spawnedIds.add(entity.uniqueId)
                    spawnedCount++
                }
            } catch (_: Exception) {}
        }

        if (spawnedIds.isNotEmpty()) {
            siteEntities[siteId] = spawnedIds
        }

        return spawnedCount
    }

    fun removeAllForSite(siteId: UUID): Int {
        val ids = siteEntities.remove(siteId) ?: return 0
        var removed = 0

        for (uuid in ids) {
            try {
                for (world in Bukkit.getWorlds()) {
                    val entity = world.getEntity(uuid)
                    if (entity != null) {
                        entity.remove()
                        removed++
                        break
                    }
                }
            } catch (_: Exception) {}
        }

        return removed
    }

    fun cleanup() {
        for ((_, ids) in siteEntities) {
            for (uuid in ids) {
                try {
                    for (world in Bukkit.getWorlds()) {
                        val entity = world.getEntity(uuid)
                        entity?.remove()
                    }
                } catch (_: Exception) {}
            }
        }
        siteEntities.clear()
    }
}
