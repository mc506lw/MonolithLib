package top.mc506lw.monolith.common

import org.bukkit.Location
import top.mc506lw.monolith.core.math.Vector3i

object Extensions {

    fun Location.toVector3i(): Vector3i {
        return Vector3i(this.blockX, this.blockY, this.blockZ)
    }

    fun Vector3i.toLocation(world: org.bukkit.World?): Location {
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun <T> Collection<T>.takeIfNotEmpty(): Collection<T>? = if (this.isEmpty()) null else this

    fun Long.chunkX(): Int = (this and 0xFFFFFFFFL).toInt()

    fun Long.chunkZ(): Int = ((this ushr 32) and 0xFFFFFFFFL).toInt()
}
