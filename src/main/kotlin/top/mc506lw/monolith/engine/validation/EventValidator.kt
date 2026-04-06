package top.mc506lw.monolith.engine.validation

import org.bukkit.Bukkit
import org.bukkit.Location
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.engine.state.PositionCache

class EventValidator(
    private val positionCache: PositionCache
) {
    fun onBlockChange(location: Location): Boolean? {
        val chunkKey = PositionCache.getChunkKey(location)
        
        if (!positionCache.hasActiveStructuresInChunk(chunkKey)) {
            return null
        }
        
        val pos = Vector3i(location.blockX, location.blockY, location.blockZ)
        
        return positionCache.containsPosition(chunkKey, pos)
    }
    
    fun submitMainThreadValidation(location: Location, validator: () -> Unit) {
        Bukkit.getScheduler().runTask(top.mc506lw.rebar.MonolithLib.instance, validator)
    }
}
