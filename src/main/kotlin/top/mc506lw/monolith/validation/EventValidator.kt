package top.mc506lw.monolith.validation

import org.bukkit.Location
import java.util.concurrent.CopyOnWriteArrayList

class EventValidator(
    private val onBlockChange: (Location) -> Unit = {}
) {
    
    fun validateBlockChange(location: Location) {
        onBlockChange(location)
    }
}

object EventValidatorRegistry {
    private val validators = CopyOnWriteArrayList<EventValidator>()
    
    fun register(validator: EventValidator) {
        validators.add(validator)
    }
    
    fun unregister(validator: EventValidator) {
        validators.remove(validator)
    }
    
    fun notifyBlockChange(location: Location) {
        validators.forEach { it.validateBlockChange(location) }
    }
    
    fun clear() = validators.clear()
}
