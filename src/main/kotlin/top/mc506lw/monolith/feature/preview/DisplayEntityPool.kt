package top.mc506lw.monolith.feature.preview

import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import java.util.concurrent.ConcurrentHashMap

class DisplayEntityPool(private val poolSize: Int = 100) {
    private val availableEntities = ConcurrentHashMap.newKeySet<Display>()
    private val inUseEntities = ConcurrentHashMap.newKeySet<Display>()
    
    fun acquire(location: Location): ItemDisplay? {
        val entity = if (availableEntities.isNotEmpty()) {
            availableEntities.firstOrNull()?.also { availableEntities.remove(it) }
        } else {
            null
        } ?: return createNewEntity(location)
        
        inUseEntities.add(entity)
        updateEntityLocation(entity, location)
        return entity as? ItemDisplay
    }
    
    fun release(entity: Display) {
        inUseEntities.remove(entity)
        entity.isVisibleByDefault = false
        availableEntities.add(entity)
    }
    
    fun releaseAll() {
        inUseEntities.toList().forEach { release(it) }
    }
    
    fun cleanup() {
        (availableEntities + inUseEntities).forEach { it.remove() }
        availableEntities.clear()
        inUseEntities.clear()
    }
    
    private fun createNewEntity(location: Location): ItemDisplay? {
        return try {
            location.world?.spawn(location, ItemDisplay::class.java)?.apply {
                isVisibleByDefault = false
                isPersistent = false
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateEntityLocation(entity: Display, location: Location) {
        entity.teleport(location)
        entity.isVisibleByDefault = true
    }
}
