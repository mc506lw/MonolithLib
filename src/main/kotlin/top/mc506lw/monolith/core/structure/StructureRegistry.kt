package top.mc506lw.monolith.core.structure

import org.bukkit.NamespacedKey
import java.util.concurrent.ConcurrentHashMap

class StructureRegistry {
    private val structures = ConcurrentHashMap<String, MonolithStructure>()
    private val controllerToStructures = ConcurrentHashMap<NamespacedKey, MutableList<String>>()
    
    fun register(structure: MonolithStructure) {
        structures[structure.id] = structure
        
        structure.controllerRebarKey?.let { key ->
            controllerToStructures.getOrPut(key) { mutableListOf() }.add(structure.id)
        }
    }
    
    fun get(id: String): MonolithStructure? = structures[id]
    
    fun contains(id: String): Boolean = structures.containsKey(id)
    
    fun getAll(): Map<String, MonolithStructure> = structures.toMap()
    
    fun getByControllerKey(key: NamespacedKey): List<MonolithStructure> {
        val ids = controllerToStructures[key] ?: return emptyList()
        return ids.mapNotNull { structures[it] }
    }
    
    fun remove(id: String): MonolithStructure? {
        val structure = structures.remove(id) ?: return null
        
        structure.controllerRebarKey?.let { key ->
            controllerToStructures[key]?.remove(id)
        }
        
        return structure
    }
    
    fun clear() {
        structures.clear()
        controllerToStructures.clear()
    }
    
    val size: Int get() = structures.size
    
    companion object {
        @Volatile
        private var instance: StructureRegistry? = null
        
        fun getInstance(): StructureRegistry {
            return instance ?: synchronized(this) {
                instance ?: StructureRegistry().also { instance = it }
            }
        }
    }
}
