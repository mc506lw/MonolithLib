package top.mc506lw.monolith.validation.predicate

import org.bukkit.Material
import org.bukkit.block.data.BlockData

class MaterialPredicate(
    private val targetMaterial: Material
) : Predicate {

    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == targetMaterial
    }

    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == targetMaterial
    }

    override val previewBlockData: BlockData? = targetMaterial.createBlockData()
    override val hint: String? = "Material: ${targetMaterial.name}"
}
