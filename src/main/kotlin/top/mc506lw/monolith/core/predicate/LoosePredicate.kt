package top.mc506lw.monolith.core.predicate

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.data.BlockData

class LoosePredicate(
    val material: Material? = null,
    val tag: Tag<Material>? = null,
    val statePatterns: Map<String, String?> = emptyMap(),
    override val previewBlockData: BlockData? = null,
    override val hint: String? = null,
    val ignoredStates: Set<String> = emptySet()
) : Predicate {
    
    constructor(blockData: BlockData, ignoredStates: Set<String> = emptySet()) : this(
        material = blockData.material,
        statePatterns = emptyMap(),
        previewBlockData = blockData,
        hint = "宽松匹配: ${blockData.material.name}",
        ignoredStates = ignoredStates
    )
    
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        if (material != null && blockData.material != material) return false
        if (tag != null && !tag.isTagged(blockData.material)) return false
        
        if (ignoredStates.isNotEmpty() && material != null) {
            val currentStates = extractAllStates(blockData.asString)
            for ((key, value) in currentStates) {
                if (key !in ignoredStates) {
                    val expectedValue = statePatterns[key]
                    if (expectedValue != null && expectedValue != "*" && expectedValue != value) {
                        return false
                    }
                }
            }
            return true
        }
        
        for ((key, pattern) in statePatterns) {
            if (pattern == null) continue
            if (pattern == "*") continue
            
            val blockState = blockData.asString
            val stateValue = extractStateValue(blockState, key)
                ?: return false
            
            if (stateValue != pattern) return false
        }
        
        return true
    }
    
    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        if (material != null && blockData.material != material) return false
        if (tag != null && !tag.isTagged(blockData.material)) return false
        return true
    }
    
    private fun extractStateValue(blockStateString: String, key: String): String? {
        val statePart = blockStateString.substringAfter("[", "").substringBefore("]", "")
        if (statePart.isEmpty()) return null
        
        for (state in statePart.split(",")) {
            val parts = state.split("=")
            if (parts.size == 2 && parts[0] == key) {
                return parts[1]
            }
        }
        return null
    }
    
    private fun extractAllStates(blockStateString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val statePart = blockStateString.substringAfter("[", "").substringBefore("]", "")
        if (statePart.isEmpty()) return result
        
        for (state in statePart.split(",")) {
            val parts = state.split("=")
            if (parts.size == 2) {
                result[parts[0]] = parts[1]
            }
        }
        return result
    }
    
    companion object {
        fun anyMaterial(materials: Set<Material>, preview: BlockData?): LoosePredicate {
            return LoosePredicate(
                material = materials.firstOrNull(),
                statePatterns = mapOf("material" to "*"),
                previewBlockData = preview,
                hint = "任意材质"
            )
        }
        
        fun fromTag(tag: Tag<Material>, preview: BlockData?): LoosePredicate {
            return LoosePredicate(tag = tag, previewBlockData = preview, hint = "标签匹配")
        }
    }
}
