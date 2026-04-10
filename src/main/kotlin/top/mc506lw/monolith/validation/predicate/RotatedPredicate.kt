package top.mc506lw.monolith.validation.predicate

import org.bukkit.Material
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.transform.BlockStateRotator

class RotatedPredicate(
    private val originalPredicate: Predicate,
    private val rotationSteps: Int
) : Predicate {
    
    override val previewBlockData: BlockData = BlockStateRotator.rotate(
        originalPredicate.previewBlockData ?: Material.STONE.createBlockData(), 
        rotationSteps
    )
    
    override val hint: String? = originalPredicate.hint
    
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        val inverseRotation = (4 - rotationSteps) % 4
        val rotatedBlockData = BlockStateRotator.rotate(blockData, inverseRotation)
        
        return originalPredicate.test(rotatedBlockData, context)
    }
    
    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return originalPredicate.testMaterialOnly(blockData, context)
    }
}
