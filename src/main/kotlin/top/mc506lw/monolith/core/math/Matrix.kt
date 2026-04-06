package top.mc506lw.monolith.core.math

import kotlin.math.cos
import kotlin.math.sin

data class Matrix3x3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double
) {
    operator fun times(other: Matrix3x3): Matrix3x3 {
        return Matrix3x3(
            m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
            m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
            m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
            m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
            m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
            m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
            m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
            m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
            m20 * other.m02 + m21 * other.m12 + m22 * other.m22
        )
    }
    
    fun transform(point: Vector3i): Vector3i {
        return Vector3i(
            (m00 * point.x + m01 * point.y + m02 * point.z).toInt(),
            (m10 * point.x + m11 * point.y + m12 * point.z).toInt(),
            (m20 * point.x + m21 * point.y + m22 * point.z).toInt()
        )
    }
    
    companion object {
        val IDENTITY = Matrix3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        
        fun rotationY90(): Matrix3x3 = Matrix3x3(0.0, 0.0, 1.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0)
        
        fun rotationY180(): Matrix3x3 = Matrix3x3(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.0)
        
        fun rotationY270(): Matrix3x3 = Matrix3x3(0.0, 0.0, -1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        
        fun mirrorX(): Matrix3x3 = Matrix3x3(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        
        fun mirrorZ(): Matrix3x3 = Matrix3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.0)
    }
}
