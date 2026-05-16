package top.mc506lw.monolith.feature.display

import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

data class DisplayEntityTransform(
    val translation: Vector3f,
    val rotation: Quaternionf,
    val scale: Vector3f
) {
    companion object {
        val IDENTITY = DisplayEntityTransform(
            translation = Vector3f(),
            rotation = Quaternionf(),
            scale = Vector3f(1f, 1f, 1f)
        )
    }

    fun toTransformation(): Transformation {
        return Transformation(translation, rotation, scale, Quaternionf())
    }
}

class DisplayTransformSystem {

    data class EntityState(
        val blueprintTransform: DisplayEntityTransform,
        var cachedFinalTransform: DisplayEntityTransform? = null
    )

    private val entities = mutableListOf<EntityState>()

    var placementYaw: Float = 0f
        private set

    fun addEntity(blueprintTransform: DisplayEntityTransform): Int {
        entities.add(EntityState(blueprintTransform))
        return entities.size - 1
    }

    fun setPlacementYaw(yawDegrees: Float) {
        placementYaw = yawDegrees
        invalidateCache()
    }

    fun rotate(deltaYaw: Float) {
        setPlacementYaw(placementYaw + deltaYaw)
    }

    fun getFinalTransform(entityIndex: Int): DisplayEntityTransform {
        if (entityIndex >= entities.size) {
            throw IndexOutOfBoundsException("Entity index $entityIndex out of bounds (size=${entities.size})")
        }

        val state = entities[entityIndex]

        state.cachedFinalTransform?.let { return it }

        val finalTransform = computeFinalTransform(state.blueprintTransform)
        state.cachedFinalTransform = finalTransform

        return finalTransform
    }

    fun getAllFinalTransforms(): List<DisplayEntityTransform> {
        return entities.mapIndexed { index, _ -> getFinalTransform(index) }
    }

    fun invalidateCache() {
        entities.forEach { it.cachedFinalTransform = null }
    }

    fun clear() {
        entities.clear()
        placementYaw = 0f
    }

    fun size(): Int = entities.size

    private fun computeFinalTransform(blueprint: DisplayEntityTransform): DisplayEntityTransform {
        val placementRotation = yawToQuaternion(placementYaw)

        val rotatedTranslation = rotateVector(blueprint.translation, placementRotation)

        val finalRotation = if (!isIdentityQuaternion(blueprint.rotation)) {
            Quaternionf(placementRotation).mul(Quaternionf(blueprint.rotation).normalize())
        } else {
            Quaternionf(placementRotation)
        }.normalize()

        return DisplayEntityTransform(
            translation = rotatedTranslation,
            rotation = finalRotation,
            scale = blueprint.scale
        )
    }

    companion object {
        fun yawToQuaternion(yawDegrees: Float): Quaternionf {
            return Quaternionf().rotateY(Math.toRadians(yawDegrees.toDouble()).toFloat())
        }

        fun rotateVector(vector: Vector3f, rotation: Quaternionf): Vector3f {
            return Quaternionf(rotation).normalize().transform(Vector3f(vector))
        }

        fun isIdentityQuaternion(q: Quaternionf): Boolean {
            return kotlin.math.abs(q.w()) > 0.999f
        }

        fun facingToYaw(facing: top.mc506lw.monolith.core.transform.Facing): Float {
            return when (facing) {
                top.mc506lw.monolith.core.transform.Facing.SOUTH -> 0f
                top.mc506lw.monolith.core.transform.Facing.WEST -> 90f
                top.mc506lw.monolith.core.transform.Facing.NORTH -> 180f
                top.mc506lw.monolith.core.transform.Facing.EAST -> -90f
            }
        }
    }
}
