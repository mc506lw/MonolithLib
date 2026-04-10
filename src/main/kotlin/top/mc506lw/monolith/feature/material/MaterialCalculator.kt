package top.mc506lw.monolith.feature.material

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import top.mc506lw.monolith.core.model.BlockEntry
import java.util.concurrent.ConcurrentHashMap

class MaterialCalculator {
    
    fun calculateRequirements(entries: List<BlockEntry>): Map<Material, MaterialRequirement> {
        val materialCounts = ConcurrentHashMap<Material, Int>()
        
        for (entry in entries) {
            val material = entry.blockData?.material ?: continue
            
            materialCounts.merge(material, 1) { old, _ -> old + 1 }
        }
        
        return materialCounts.map { (material, count) ->
            material to MaterialRequirement(material = material, required = count)
        }.toMap()
    }
    
    fun calculateFromInventory(player: Player, requirements: Map<Material, MaterialRequirement>): MaterialStats {
        val inventoryMaterials = countInventoryMaterials(player)
        
        val updatedRequirements = requirements.mapValues { (_, req) ->
            val available = inventoryMaterials[req.material] ?: 0
            req.copy(current = minOf(available, req.required))
        }
        
        return MaterialStats(
            requirements = updatedRequirements,
            totalRequired = updatedRequirements.values.sumOf { it.required },
            totalCurrent = updatedRequirements.values.sumOf { it.current }
        )
    }
    
    private fun countInventoryMaterials(player: Player): Map<Material, Int> {
        val counts = mutableMapOf<Material, Int>()
        
        for (item in player.inventory.contents) {
            if (item == null || item.type.isAir) continue
            
            counts.merge(item.type, item.amount) { old, amount -> old + amount }
        }
        
        return counts
    }
}
