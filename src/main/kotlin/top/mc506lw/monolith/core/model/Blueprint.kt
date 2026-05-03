package top.mc506lw.monolith.core.model

import org.bukkit.NamespacedKey
import top.mc506lw.monolith.core.math.Vector3i

data class BlueprintMeta(
    val displayName: String = "",
    val description: String = "",
    val controllerOffset: Vector3i = Vector3i.ZERO,
    val displayOffset: Vector3i = Vector3i.ZERO
)

class Blueprint(
    val id: String,
    val stages: Map<BuildStage, Shape>,
    val meta: BlueprintMeta = BlueprintMeta(),
    val displayEntities: List<DisplayEntityData> = emptyList(),
    val slots: Map<String, Vector3i> = emptyMap(),
    val customData: Map<String, Any> = emptyMap(),
    val controllerRebarKey: NamespacedKey? = null
) {
    val scaffoldShape: Shape get() = stages[BuildStage.SCAFFOLD] ?: stages.values.firstOrNull() ?: Shape.EMPTY
    val assembledShape: Shape get() = stages[BuildStage.ASSEMBLED] ?: stages.values.firstOrNull() ?: Shape.EMPTY

    val sizeX: Int get() = assembledShape.boundingBox.width
    val sizeY: Int get() = assembledShape.boundingBox.height
    val sizeZ: Int get() = assembledShape.boundingBox.depth
    val blockCount: Int get() = assembledShape.blocks.size

    @Deprecated("Use scaffoldShape or assembledShape", ReplaceWith("assembledShape"))
    val shape: Shape get() = assembledShape

    fun getSlotPosition(slotId: String): Vector3i? = slots[slotId]
    fun getCustomData(key: String): Any? = customData[key]
    fun getCustomString(key: String): String? = customData[key] as? String
    fun getCustomInt(key: String): Int? = (customData[key] as? Number)?.toInt()
    fun getCustomDouble(key: String): Double? = (customData[key] as? Number)?.toDouble()
    fun getCustomBoolean(key: String): Boolean? = customData[key] as? Boolean

    companion object {
        fun builder(id: String): BlueprintBuilder = BlueprintBuilder(id)

        fun fromSingleShape(id: String, shape: Shape, meta: BlueprintMeta = BlueprintMeta()): Blueprint {
            return Blueprint(
                id = id,
                stages = mapOf(BuildStage.SCAFFOLD to shape, BuildStage.ASSEMBLED to shape),
                meta = meta
            )
        }

        fun fromSingleShape(id: String, shape: Shape, meta: BlueprintMeta, displayEntities: List<DisplayEntityData>?): Blueprint {
            return Blueprint(
                id = id,
                stages = mapOf(BuildStage.SCAFFOLD to shape, BuildStage.ASSEMBLED to shape),
                meta = meta,
                displayEntities = displayEntities ?: emptyList()
            )
        }
    }
}

class BlueprintBuilder(private val id: String) {
    private var scaffoldShape: Shape? = null
    private var assembledShape: Shape? = null
    private var singleShape: Shape? = null
    private var meta: BlueprintMeta = BlueprintMeta()
    private val displayEntities = mutableListOf<DisplayEntityData>()
    private val slots = mutableMapOf<String, Vector3i>()
    private val customData = mutableMapOf<String, Any>()
    private var controllerRebarKey: NamespacedKey? = null

    fun shape(shape: Shape): BlueprintBuilder {
        this.singleShape = shape
        return this
    }

    fun shape(blocks: List<BlockEntry>): BlueprintBuilder {
        this.singleShape = Shape(blocks)
        return this
    }

    fun scaffoldShape(shape: Shape): BlueprintBuilder {
        this.scaffoldShape = shape
        return this
    }

    fun assembledShape(shape: Shape): BlueprintBuilder {
        this.assembledShape = shape
        return this
    }

    fun displayName(name: String): BlueprintBuilder {
        this.meta = meta.copy(displayName = name)
        return this
    }

    fun description(desc: String): BlueprintBuilder {
        this.meta = meta.copy(description = desc)
        return this
    }

    fun controllerOffset(x: Int, y: Int, z: Int): BlueprintBuilder {
        this.meta = meta.copy(controllerOffset = Vector3i(x, y, z))
        return this
    }

    fun controllerOffset(offset: Vector3i): BlueprintBuilder {
        this.meta = meta.copy(controllerOffset = offset)
        return this
    }

    fun displayOffset(x: Int, y: Int, z: Int): BlueprintBuilder {
        this.meta = meta.copy(displayOffset = Vector3i(x, y, z))
        return this
    }

    fun displayOffset(offset: Vector3i): BlueprintBuilder {
        this.meta = meta.copy(displayOffset = offset)
        return this
    }

    fun displayEntity(data: DisplayEntityData): BlueprintBuilder {
        displayEntities.add(data)
        return this
    }

    fun displayEntities(entities: List<DisplayEntityData>): BlueprintBuilder {
        displayEntities.addAll(entities)
        return this
    }

    fun slot(slotId: String, x: Int, y: Int, z: Int): BlueprintBuilder {
        slots[slotId] = Vector3i(x, y, z)
        return this
    }

    fun slot(slotId: String, position: Vector3i): BlueprintBuilder {
        slots[slotId] = position
        return this
    }

    fun customData(key: String, value: Any): BlueprintBuilder {
        customData[key] = value
        return this
    }

    fun controllerRebar(key: NamespacedKey?): BlueprintBuilder {
        this.controllerRebarKey = key
        return this
    }

    fun build(): Blueprint {
        val stages = if (scaffoldShape != null || assembledShape != null) {
            val scaffold = scaffoldShape ?: assembledShape
                ?: singleShape
                ?: throw IllegalStateException("Blueprint must have a shape")
            val assembled = assembledShape ?: scaffoldShape
                ?: singleShape
                ?: throw IllegalStateException("Blueprint must have a shape")
            mapOf(BuildStage.SCAFFOLD to scaffold, BuildStage.ASSEMBLED to assembled)
        } else {
            val shape = singleShape ?: throw IllegalStateException("Blueprint must have a shape")
            mapOf(BuildStage.SCAFFOLD to shape, BuildStage.ASSEMBLED to shape)
        }

        return Blueprint(
            id = id,
            stages = stages,
            meta = meta,
            displayEntities = displayEntities.toList(),
            slots = slots.toMap(),
            customData = customData.toMap(),
            controllerRebarKey = controllerRebarKey
        )
    }
}
