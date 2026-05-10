package top.mc506lw.monolith.feature.display

import io.github.pylonmc.rebar.block.base.RebarEntityHolderBlock
import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchor

class DisplayEntityGroup(
    private val anchor: RebarEntityHolderBlock,
    centerLocation: Location,
    private val displayOffset: Vector3f = Vector3f(),
    private val groupRotation: Quaternionf = Quaternionf()
) {
    private var rootEntity: Entity? = null
    private val childEntities = mutableListOf<Entity>()
    private var centerLoc: Location = centerLocation

    val location: Location get() = rootEntity?.location ?: centerLoc

    init {
        createRootEntity(centerLocation)
    }

    private fun createRootEntity(centerLocation: Location) {
        val world = centerLocation.world!!
        val rootLoc = Location(
            world,
            centerLocation.x + displayOffset.x(),
            centerLocation.y + displayOffset.y(),
            centerLocation.z + displayOffset.z()
        )

        rootEntity = BlockDisplayBuilder()
            .material(Material.BARRIER)
            .transformation(TransformBuilder()
                .scale(0.001, 0.001, 0.001)
                .build()
            )
            .build(rootLoc)

        (rootEntity as? BlockDisplay)?.let { entity ->
            val currentTransform = entity.transformation
            entity.transformation = Transformation(
                currentTransform.translation,
                groupRotation,
                currentTransform.scale,
                currentTransform.rightRotation
            )
        }

        anchor.addEntity(VirtualDisplayAnchor.DISPLAY_GROUP_KEY, rootEntity!!)
    }

    private fun rotateVector(vector: Vector3f, rotation: Quaternionf): Vector3f {
        return Quaternionf(rotation).transform(Vector3f(vector))
    }

    fun addBlockDisplay(
        name: String,
        blockData: BlockData,
        translation: Vector3f,
        scale: Vector3f,
        rotation: Quaternionf? = null
    ): Entity {
        val root = rootEntity ?: throw IllegalStateException("Root entity not initialized")
        val world = root.world!!

        val rotatedTranslation = rotateVector(translation, groupRotation)

        val finalRotation = if (rotation != null) {
            Quaternionf(groupRotation).mul(rotation)
        } else {
            Quaternionf(groupRotation)
        }

        val display = BlockDisplayBuilder()
            .material(blockData.material)
            .build(root.location)

        (display as? BlockDisplay)?.let { entity ->
            entity.transformation = Transformation(
                rotatedTranslation,
                finalRotation,
                scale,
                Quaternionf()
            )
            try {
                entity.block = blockData
            } catch (_: Exception) {}
        }

        childEntities.add(display)
        anchor.addEntity("${VirtualDisplayAnchor.ENTITY_PREFIX}$name", display)

        return display
    }

    fun rotate(rotationY: Float) {
        val newGroupRotation = Quaternionf().rotateY(rotationY)

        (rootEntity as? BlockDisplay)?.let { entity ->
            val currentTransform = entity.transformation
            entity.transformation = Transformation(
                currentTransform.translation,
                newGroupRotation,
                currentTransform.scale,
                currentTransform.rightRotation
            )
        }

        childEntities.forEach { child ->
            (child as? BlockDisplay)?.let { entity ->
                val currentTransform = entity.transformation
                val newTranslation = rotateVector(currentTransform.translation, newGroupRotation)
                entity.transformation = Transformation(
                    newTranslation,
                    newGroupRotation,
                    currentTransform.scale,
                    currentTransform.rightRotation
                )
            }
        }
    }

    fun moveTo(newLocation: Location) {
        centerLoc = newLocation
        rootEntity?.teleport(Location(
            newLocation.world,
            newLocation.x + displayOffset.x(),
            newLocation.y + displayOffset.y(),
            newLocation.z + displayOffset.z()
        ))
    }

    fun remove() {
        childEntities.forEach { it.remove() }
        childEntities.clear()
        rootEntity?.remove()
        rootEntity = null
    }

    fun getRootEntity(): Entity? = rootEntity

    fun getChildEntities(): List<Entity> = childEntities.toList()

    fun getSize(): Int = childEntities.size

    companion object {
        const val DISPLAY_GROUP_KEY = "display_group"
        const val ENTITY_PREFIX = "vde_"
    }
}