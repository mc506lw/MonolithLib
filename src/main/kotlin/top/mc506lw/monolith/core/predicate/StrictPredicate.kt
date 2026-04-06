package top.mc506lw.monolith.core.predicate

import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Stairs

class StrictPredicate(
    override val previewBlockData: BlockData,
    private val ignoredStates: Set<String> = emptySet()
) : Predicate {
    
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        if (blockData.material != previewBlockData.material) return false
        
        if (previewBlockData is Stairs && blockData is Stairs) {
            return StairsNormalizer.areEquivalent(previewBlockData, blockData)
        }
        
        return compareIgnoringStates(previewBlockData.asString, blockData.asString)
    }
    
    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == previewBlockData.material
    }
    
    override val hint: String? = "严格匹配: ${previewBlockData.material}"
    
    private fun compareIgnoringStates(target: String, actual: String): Boolean {
        if (ignoredStates.isEmpty()) {
            return target == actual
        }
        
        val targetParts = target.split("[", "]").filter { it.isNotBlank() }
        val actualParts = actual.split("[", "]").filter { it.isNotBlank() }
        
        if (targetParts.isEmpty() && actualParts.isEmpty()) return true
        if (actualParts.isEmpty()) return false
        
        val targetStates = parseStates(targetParts.getOrNull(1) ?: "")
        val actualStates = parseStates(actualParts.getOrNull(1) ?: "")
        
        for ((key, value) in targetStates) {
            if (key in ignoredStates) continue
            if (actualStates[key] != value) return false
        }
        
        return true
    }
    
    private fun parseStates(stateStr: String): Map<String, String> {
        if (stateStr.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<String, String>()
        for (part in stateStr.split(",")) {
            val keyValue = part.split("=")
            if (keyValue.size == 2) {
                result[keyValue[0]] = keyValue[1]
            }
        }
        return result
    }
}
