package top.mc506lw.monolith.core.io

data class NbtCompound(val value: Map<String, Any>)
data class NbtList(val value: List<Any>)
data class NbtByteArray(val value: ByteArray)
data class NbtIntArray(val value: IntArray)
data class NbtLongArray(val value: LongArray)

object NbtTypes {
    const val TAG_END: Byte = 0
    const val TAG_BYTE: Byte = 1
    const val TAG_SHORT: Byte = 2
    const val TAG_INT: Byte = 3
    const val TAG_LONG: Byte = 4
    const val TAG_FLOAT: Byte = 5
    const val TAG_DOUBLE: Byte = 6
    const val TAG_BYTE_ARRAY: Byte = 7
    const val TAG_STRING: Byte = 8
    const val TAG_LIST: Byte = 9
    const val TAG_COMPOUND: Byte = 10
    const val TAG_INT_ARRAY: Byte = 11
    const val TAG_LONG_ARRAY: Byte = 12
}

class NbtReader(private val input: java.io.DataInput) {
    
    fun readTag(): Pair<String?, Any> {
        val type = input.readByte()
        if (type == NbtTypes.TAG_END) {
            return Pair(null, Unit)
        }
        val name = input.readUTF()
        val value = readValue(type)
        return Pair(name, value)
    }
    
    fun readValue(type: Byte): Any = when (type) {
        NbtTypes.TAG_BYTE -> input.readByte()
        NbtTypes.TAG_SHORT -> input.readShort()
        NbtTypes.TAG_INT -> input.readInt()
        NbtTypes.TAG_LONG -> input.readLong()
        NbtTypes.TAG_FLOAT -> input.readFloat()
        NbtTypes.TAG_DOUBLE -> input.readDouble()
        NbtTypes.TAG_BYTE_ARRAY -> {
            val length = input.readInt()
            val bytes = ByteArray(length)
            input.readFully(bytes)
            NbtByteArray(bytes)
        }
        NbtTypes.TAG_STRING -> input.readUTF()
        NbtTypes.TAG_LIST -> {
            val listType = input.readByte()
            val length = input.readInt()
            val list = mutableListOf<Any>()
            repeat(length) {
                list.add(readValue(listType))
            }
            NbtList(list)
        }
        NbtTypes.TAG_COMPOUND -> {
            val compound = mutableMapOf<String, Any>()
            while (true) {
                val childType = input.readByte()
                if (childType == NbtTypes.TAG_END) break
                val childName = input.readUTF()
                compound[childName] = readValue(childType)
            }
            NbtCompound(compound)
        }
        NbtTypes.TAG_INT_ARRAY -> {
            val length = input.readInt()
            val ints = IntArray(length)
            repeat(length) { ints[it] = input.readInt() }
            NbtIntArray(ints)
        }
        NbtTypes.TAG_LONG_ARRAY -> {
            val length = input.readInt()
            val longs = LongArray(length)
            repeat(length) { longs[it] = input.readLong() }
            NbtLongArray(longs)
        }
        else -> throw IllegalArgumentException("Unknown NBT type: $type")
    }
    
    fun readRoot(): Map<String, Any> {
        val type = input.readByte()
        if (type != NbtTypes.TAG_COMPOUND) {
            throw IllegalArgumentException("Root tag must be compound")
        }
        val name = input.readUTF()
        val value = readValue(NbtTypes.TAG_COMPOUND) as NbtCompound
        return value.value
    }
}

fun Map<String, Any>.getNbtCompound(key: String): Map<String, Any>? {
    return (this[key] as? NbtCompound)?.value
}

fun Map<String, Any>.getNbtList(key: String): List<Any>? {
    return (this[key] as? NbtList)?.value
}

fun Map<String, Any>.getNbtString(key: String): String? {
    return this[key] as? String
}

fun Map<String, Any>.getNbtInt(key: String): Int? {
    return when (val v = this[key]) {
        is Int -> v
        is Byte -> v.toInt()
        is Short -> v.toInt()
        else -> null
    }
}

fun Map<String, Any>.getNbtLong(key: String): Long? {
    return when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        else -> null
    }
}

fun Map<String, Any>.getNbtShort(key: String): Short? {
    return when (val v = this[key]) {
        is Short -> v
        is Byte -> v.toShort()
        else -> null
    }
}

fun Map<String, Any>.getNbtByteArray(key: String): ByteArray? {
    return (this[key] as? NbtByteArray)?.value
}

fun Map<String, Any>.getNbtIntArray(key: String): IntArray? {
    return (this[key] as? NbtIntArray)?.value
}

fun Map<String, Any>.getNbtLongArray(key: String): LongArray? {
    return (this[key] as? NbtLongArray)?.value
}
