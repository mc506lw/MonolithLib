package top.mc506lw.monolith.core.model

import org.bukkit.NamespacedKey
import top.mc506lw.monolith.core.math.Vector3i

data class BlueprintMeta(
    val displayName: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "1.0",
    val controllerOffset: Vector3i = Vector3i.ZERO
)

class Blueprint(
    val id: String,
    val shape: Shape,
    val meta: BlueprintMeta = BlueprintMeta(),
    val slots: Map<String, Vector3i> = emptyMap(),
    val customData: Map<String, Any> = emptyMap(),
    val controllerRebarKey: NamespacedKey? = null
) {
    val sizeX: Int get() = shape.boundingBox.width
    val sizeY: Int get() = shape.boundingBox.height
    val sizeZ: Int get() = shape.boundingBox.depth
    val blockCount: Int get() = shape.blocks.size

    fun getSlotPosition(slotId: String): Vector3i? = slots[slotId]
    fun getCustomData(key: String): Any? = customData[key]
    fun getCustomString(key: String): String? = customData[key] as? String
    fun getCustomInt(key: String): Int? = (customData[key] as? Number)?.toInt()
    fun getCustomDouble(key: String): Double? = (customData[key] as? Number)?.toDouble()
    fun getCustomBoolean(key: String): Boolean? = customData[key] as? Boolean

    companion object {
        fun builder(id: String): BlueprintBuilder = BlueprintBuilder(id)
    }
}

class BlueprintBuilder(private val id: String) {
    private var shape: Shape? = null
    private var meta: BlueprintMeta = BlueprintMeta()
    private val slots = mutableMapOf<String, Vector3i>()
    private val customData = mutableMapOf<String, Any>()
    private var controllerRebarKey: NamespacedKey? = null

    fun shape(shape: Shape): BlueprintBuilder {
        this.shape = shape
        return this
    }

    fun shape(blocks: List<BlockEntry>): BlueprintBuilder {
        this.shape = Shape(blocks)
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

    fun author(author: String): BlueprintBuilder {
        this.meta = meta.copy(author = author)
        return this
    }

    fun version(version: String): BlueprintBuilder {
        this.meta = meta.copy(version = version)
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
        val finalShape = shape ?: throw IllegalStateException("Blueprint must have a shape")
        return Blueprint(id, finalShape, meta, slots.toMap(), customData.toMap(), controllerRebarKey)
    }
}
