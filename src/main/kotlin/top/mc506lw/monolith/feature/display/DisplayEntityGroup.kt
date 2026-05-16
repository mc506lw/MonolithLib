package top.mc506lw.monolith.feature.display

import io.github.pylonmc.rebar.block.base.RebarEntityHolderBlock
import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import org.bukkit.Bukkit
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
    initialYaw: Float = 0f
) {
    private val transformSystem = DisplayTransformSystem()
    private var rootEntity: Entity? = null
    private val childEntities = mutableListOf<Entity>()
    private var centerLoc: Location = centerLocation

    init {
        transformSystem.setPlacementYaw(initialYaw)
        createRootEntity(centerLocation)
        Bukkit.getLogger().info("[DisplayEntityGroup] 初始化完成, 初始yaw=${transformSystem.placementYaw}°")
    }

    val location: Location get() = rootEntity?.location ?: centerLoc

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

        updateRootEntityTransformation()

        anchor.addEntity(VirtualDisplayAnchor.DISPLAY_GROUP_KEY, rootEntity!!)
    }

    fun addBlockDisplay(
        name: String,
        blockData: BlockData,
        translation: Vector3f,
        scale: Vector3f,
        rotation: Quaternionf? = null
    ): Entity {
        val root = rootEntity ?: throw IllegalStateException("Root entity not initialized")

        val blueprintTransform = DisplayEntityTransform(
            translation = Vector3f(translation),
            rotation = rotation ?: Quaternionf(),
            scale = Vector3f(scale)
        )

        val entityIndex = transformSystem.addEntity(blueprintTransform)

        val display = BlockDisplayBuilder()
            .material(blockData.material)
            .build(root.location)

        (display as? BlockDisplay)?.let { entity ->
            try {
                entity.block = blockData
            } catch (_: Exception) {}
        }

        childEntities.add(display)
        anchor.addEntity("${VirtualDisplayAnchor.ENTITY_PREFIX}$name", display)

        applyTransformationToEntity(entityIndex, display)

        if (entityIndex < 5) {
            logEntityInfo(name, blueprintTransform, entityIndex)
        }

        return display
    }

    fun setYaw(newYaw: Float) {
        val oldYaw = transformSystem.placementYaw
        transformSystem.setPlacementYaw(newYaw)

        Bukkit.getLogger().info("[DisplayEntityGroup] setYaw: $oldYaw° → $newYaw°")

        updateRootEntityTransformation()
        applyAllTransformations()
    }

    fun rotate(deltaYaw: Float) {
        setYaw(transformSystem.placementYaw + deltaYaw)
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
        transformSystem.clear()
        rootEntity?.remove()
        rootEntity = null
    }

    fun getRootEntity(): Entity? = rootEntity

    fun getChildEntities(): List<Entity> = childEntities.toList()

    fun getSize(): Int = childEntities.size

    fun getCurrentYaw(): Float = transformSystem.placementYaw

    private fun updateRootEntityTransformation() {
        val groupRotation = DisplayTransformSystem.yawToQuaternion(transformSystem.placementYaw)

        (rootEntity as? BlockDisplay)?.let { entity ->
            val currentTransform = entity.transformation
            val blockData = entity.block
            entity.transformation = Transformation(
                currentTransform.translation,
                groupRotation,
                currentTransform.scale,
                currentTransform.rightRotation
            )
            try {
                entity.block = blockData
            } catch (_: Exception) {}
        }
    }

    private fun applyAllTransformations() {
        childEntities.forEachIndexed { index, entity ->
            if (index < transformSystem.size()) {
                applyTransformationToEntity(index, entity)
            }
        }
    }

    private fun applyTransformationToEntity(entityIndex: Int, entity: Entity) {
        try {
            val finalTransform = transformSystem.getFinalTransform(entityIndex)

            (entity as? BlockDisplay)?.let { display ->
                val blockData = display.block
                display.transformation = finalTransform.toTransformation()
                try {
                    display.block = blockData
                } catch (_: Exception) {}
            }

            if (entityIndex < 5) {
                logAppliedTransform(entityIndex, finalTransform)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[DisplayEntityGroup] 应用变换到实体[$entityIndex]失败: ${e.message}")
        }
    }

    private fun logEntityInfo(name: String, blueprint: DisplayEntityTransform, index: Int) {
        Bukkit.getLogger().info("[DisplayEntityGroup] 添加实体[$name](#$index):")
        Bukkit.getLogger().info("  蓝图translation: (${blueprint.translation.x}, ${blueprint.translation.y}, ${blueprint.translation.z})")
        Bukkit.getLogger().info("  蓝图rotation: (${blueprint.rotation.x}, ${blueprint.rotation.y}, ${blueprint.rotation.z}, ${blueprint.rotation.w})")
        Bukkit.getLogger().info("  当前组yaw: ${transformSystem.placementYaw}°")
    }

    private fun logAppliedTransform(index: Int, final: DisplayEntityTransform) {
        Bukkit.getLogger().info("[DisplayEntityGroup] 实体[$index] 最终变换:")
        Bukkit.getLogger().info("  最终translation: (${final.translation.x}, ${final.translation.y}, ${final.translation.z})")
        Bukkit.getLogger().info("  最终rotation: (${final.rotation.x}, ${final.rotation.y}, ${final.rotation.z}, ${final.rotation.w})")
    }

    companion object {
        const val DISPLAY_GROUP_KEY = "display_group"
        const val ENTITY_PREFIX = "vde_"
    }
}
