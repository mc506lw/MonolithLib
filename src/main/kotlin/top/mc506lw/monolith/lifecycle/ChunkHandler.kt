package top.mc506lw.monolith.lifecycle

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.concurrent.ConcurrentHashMap

class ChunkHandler : Listener {
    private val lifecycleMap = ConcurrentHashMap<String, BlueprintLifecycle>()

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunkKey = "${event.world.name}_${event.chunk.x}_${event.chunk.z}"
        lifecycleMap[chunkKey]?.handleChunkUnload()
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunkKey = "${event.world.name}_${event.chunk.x}_${event.chunk.z}"
        lifecycleMap[chunkKey]?.handleChunkLoad()
    }

    fun registerLifecycle(key: String, lifecycle: BlueprintLifecycle) {
        lifecycleMap[key] = lifecycle
    }

    fun unregisterLifecycle(key: String) {
        lifecycleMap.remove(key)
    }

    fun clear() = lifecycleMap.clear()
}
