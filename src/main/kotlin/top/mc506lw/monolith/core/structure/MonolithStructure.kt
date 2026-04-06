package top.mc506lw.monolith.core.structure

import org.bukkit.NamespacedKey
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.AirPredicate
import top.mc506lw.monolith.core.predicate.Predicate

data class FlattenedBlock(
    val relativePosition: Vector3i,
    val predicate: Predicate,
    val previewBlockData: org.bukkit.block.data.BlockData?,
    val hint: String? = null
)

data class StructureMeta(
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "1.0"
)

class MonolithStructure private constructor(
    val id: String,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val centerOffset: Vector3i,
    internal val storage: Array<Array<Array<Predicate?>>>,
    val flattenedBlocks: List<FlattenedBlock>,
    val controllerRebarKey: NamespacedKey?,
    val slots: Map<String, Vector3i>,
    val customData: Map<String, Any>,
    val meta: StructureMeta,
    val configHash: String = ""
) {
    val totalSize: Int = sizeX * sizeY * sizeZ
    
    fun getPredicate(x: Int, y: Int, z: Int): Predicate? {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            return null
        }
        return storage[z][y][x]
    }
    
    fun isWithinBounds(x: Int, y: Int, z: Int): Boolean {
        return x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ
    }
    
    fun getSlotPosition(slotId: String): Vector3i? = slots[slotId]
    
    fun getCustomData(key: String): Any? = customData[key]
    
    fun getCustomString(key: String): String? = customData[key] as? String
    
    fun getCustomInt(key: String): Int? = (customData[key] as? Number)?.toInt()
    
    fun getCustomDouble(key: String): Double? = (customData[key] as? Number)?.toDouble()
    
    fun getCustomBoolean(key: String): Boolean? = customData[key] as? Boolean
    
    @Suppress("UNCHECKED_CAST")
    fun getCustomList(key: String): List<Any>? = customData[key] as? List<Any>
    
    @Suppress("UNCHECKED_CAST")
    fun getCustomMap(key: String): Map<String, Any>? = customData[key] as? Map<String, Any>
    
    class Builder(private val id: String) {
        private var sizeX: Int = 0
        private var sizeY: Int = 0
        private var sizeZ: Int = 0
        private var centerOffset: Vector3i = Vector3i.ZERO
        
        private lateinit var storage: Array<Array<Array<Predicate?>>>
        
        private var controllerRebarKey: NamespacedKey? = null
        private val slots = mutableMapOf<String, Vector3i>()
        private val customData = mutableMapOf<String, Any>()
        private var meta: StructureMeta = StructureMeta()
        
        init {
            resize(1, 1, 1)
        }
        
        fun size(x: Int, y: Int, z: Int): Builder {
            require(x > 0 && y > 0 && z > 0) { "尺寸必须大于0" }
            this.sizeX = x
            this.sizeY = y
            this.sizeZ = z
            resize(x, y, z)
            return this
        }
        
        fun center(x: Int, y: Int, z: Int): Builder {
            this.centerOffset = Vector3i(x, y, z)
            return this
        }
        
        fun center(offset: Vector3i): Builder {
            this.centerOffset = offset
            return this
        }
        
        fun set(x: Int, y: Int, z: Int, predicate: Predicate?): Builder {
            if (isWithinBounds(x, y, z)) {
                storage[z][y][x] = predicate
            }
            return this
        }
        
        fun controllerRebar(key: String): Builder {
            val parts = key.split(":")
            this.controllerRebarKey = when {
                parts.size == 2 -> NamespacedKey(parts[0], parts[1])
                parts.size == 1 -> NamespacedKey("minecraft", parts[0])
                else -> null
            }
            return this
        }
        
        fun controllerRebar(key: NamespacedKey): Builder {
            this.controllerRebarKey = key
            return this
        }
        
        fun slot(slotId: String, x: Int, y: Int, z: Int): Builder {
            slots[slotId] = Vector3i(x, y, z)
            return this
        }
        
        fun slot(slotId: String, position: Vector3i): Builder {
            slots[slotId] = position
            return this
        }
        
        fun customData(key: String, value: Any): Builder {
            customData[key] = value
            return this
        }
        
        fun meta(name: String, description: String = "", author: String = "", version: String = "1.0"): Builder {
            this.meta = StructureMeta(name, description, author, version)
            return this
        }
        
        fun fill(predicate: Predicate?): Builder {
            for (z in 0 until sizeZ) {
                for (y in 0 until sizeY) {
                    for (x in 0 until sizeX) {
                        storage[z][y][x] = predicate
                    }
                }
            }
            return this
        }
        
        fun build(): MonolithStructure {
            val flattened = buildFlattenedList()
            return MonolithStructure(
                id = id,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ,
                centerOffset = centerOffset,
                storage = storage,
                flattenedBlocks = flattened,
                controllerRebarKey = controllerRebarKey,
                slots = slots.toMap(),
                customData = customData.toMap(),
                meta = meta
            )
        }
        
        private fun resize(x: Int, y: Int, z: Int) {
            storage = Array(z) { Array(y) { arrayOfNulls(x) } }
        }
        
        private fun isWithinBounds(x: Int, y: Int, z: Int): Boolean {
            return x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ
        }
        
        private fun buildFlattenedList(): List<FlattenedBlock> {
            val result = mutableListOf<FlattenedBlock>()
            
            for (z in 0 until sizeZ) {
                for (y in 0 until sizeY) {
                    for (x in 0 until sizeX) {
                        val predicate = storage[z][y][x] ?: continue
                        
                        if (predicate is AirPredicate) continue
                        
                        result.add(FlattenedBlock(
                            relativePosition = Vector3i(x, y, z),
                            predicate = predicate,
                            previewBlockData = predicate.previewBlockData,
                            hint = predicate.hint
                        ))
                    }
                }
            }
            
            return result
        }
    }
}
