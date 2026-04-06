package top.mc506lw.monolith.feature.material

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.monolith.core.structure.FlattenedBlock

class MaterialModule(plugin: JavaPlugin) {
    private val calculator = MaterialCalculator()
    private val cache = mutableMapOf<String, MaterialStats>()
    
    fun calculateForStructure(structureId: String, entries: List<FlattenedBlock>): Map<Material, MaterialRequirement> {
        return calculator.calculateRequirements(entries)
    }
    
    fun getPlayerStats(player: Player, structureId: String): MaterialStats? {
        return cache[structureId]
    }
    
    fun updatePlayerInventory(player: Player, structureId: String, requirements: Map<Material, MaterialRequirement>) {
        cache[structureId] = calculator.calculateFromInventory(player, requirements)
    }
    
    fun clearCache(structureId: String? = null) {
        if (structureId != null) {
            cache.remove(structureId)
        } else {
            cache.clear()
        }
    }
}
