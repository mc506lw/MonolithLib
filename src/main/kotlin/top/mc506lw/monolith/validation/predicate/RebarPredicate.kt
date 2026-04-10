package top.mc506lw.monolith.validation.predicate

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.feature.rebar.RebarAdapter

class RebarPredicate(
    private val rebarKey: NamespacedKey,
    override val previewBlockData: BlockData
) : Predicate {
    
    override val hint: String? = "Rebar: ${rebarKey.namespace}:${rebarKey.key}"
    
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        val block: Block? = context.block
        
        return block != null && RebarAdapter.isRebarBlock(block, rebarKey)
    }
    
    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return test(blockData, context)
    }
    
    val key: NamespacedKey get() = rebarKey
    
    override fun toString(): String = "RebarPredicate(key=$rebarKey)"
}
