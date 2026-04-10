package top.mc506lw.monolith.lifecycle

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.bukkit.Location
import top.mc506lw.monolith.core.math.Vector3i
import java.util.concurrent.ConcurrentHashMap

class PositionCache {
    private val chunkCache = ConcurrentHashMap<Long, LongOpenHashSet>()
    
    fun addPosition(chunkKey: Long, position: Vector3i) {
        synchronized(this) {
            val set = chunkCache.computeIfAbsent(chunkKey) { LongOpenHashSet() }
            set.add(position.toLong())
        }
    }
    
    fun removePosition(chunkKey: Long, position: Vector3i) {
        synchronized(this) {
            val set = chunkCache[chunkKey] ?: return
            set.remove(position.toLong())
            
            if (set.isEmpty()) {
                chunkCache.remove(chunkKey)
            }
        }
    }
    
    fun containsPosition(chunkKey: Long, position: Vector3i): Boolean {
        val set = chunkCache[chunkKey] ?: return false
        return synchronized(set) { set.contains(position.toLong()) }
    }
    
    fun hasActiveStructuresInChunk(chunkKey: Long): Boolean = chunkCache.containsKey(chunkKey)
    
    fun clearChunk(chunkKey: Long): LongOpenHashSet? = chunkCache.remove(chunkKey)
    
    fun clear() = chunkCache.clear()
    
    companion object {
        fun getChunkKey(location: Location): Long {
            return ((location.blockX shr 4).toLong() and 0xFFFFFFFFL) or 
                   (((location.blockZ shr 4).toLong() and 0xFFFFFFFFL) shl 32)
        }
        
        fun getChunkKey(x: Int, z: Int): Long {
            return (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
        }
    }
}
