package top.mc506lw.monolith.feature.material

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

data class MaterialRequirement(
    val material: Material,
    val required: Int,
    var current: Int = 0
) {
    val progress: Float get() = if (required > 0) current.toFloat() / required else 1f
    
    val isComplete: Boolean get() = current >= required
    
    fun toDisplayString(): String = "$material: $current/$required"
}

data class MaterialStats(
    val requirements: Map<Material, MaterialRequirement>,
    val totalRequired: Int,
    val totalCurrent: Int
) {
    val overallProgress: Float get() = if (totalRequired > 0) totalCurrent.toFloat() / totalRequired else 1f
    
    val isComplete: Boolean get() = requirements.values.all { it.isComplete }
}
