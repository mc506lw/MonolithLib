package top.mc506lw.monolith.core.predicate

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i

interface Predicate {
    fun test(blockData: BlockData, context: PredicateContext): Boolean
    
    fun testMaterialOnly(blockData: BlockData, context: PredicateContext): Boolean {
        return test(blockData, context)
    }
    
    val previewBlockData: BlockData?
    val hint: String?
    
    data class PredicateContext(
        val position: Vector3i,
        val facing: BlockFace? = null,
        val properties: Map<String, Any?> = emptyMap(),
        val block: Block? = null
    )
}
