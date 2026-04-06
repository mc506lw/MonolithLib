package top.mc506lw.monolith.core.math

data class Vector3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: Vector3i) = Vector3i(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3i) = Vector3i(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus() = Vector3i(-x, -y, -z)
    
    fun toLong(): Long {
        return (x.toLong() and 0x7FFFFFFF) or ((y.toLong() and 0x7FFFFFFF) shl 31) or ((z.toLong() and 0x7FFFFFFF) shl 62)
    }
    
    companion object {
        val ZERO = Vector3i(0, 0, 0)
        val UP = Vector3i(0, 1, 0)
        val DOWN = Vector3i(0, -1, 0)
        val NORTH = Vector3i(0, 0, -1)
        val SOUTH = Vector3i(0, 0, 1)
        val WEST = Vector3i(-1, 0, 0)
        val EAST = Vector3i(1, 0, 0)
        
        fun fromLong(value: Long): Vector3i {
            val x = (value and 0x7FFFFFFF).toInt()
            val y = ((value shr 31) and 0x7FFFFFFF).toInt()
            val z = ((value shr 62) and 0x7FFFFFFF).toInt()
            return Vector3i(x, y, z)
        }
    }
}
