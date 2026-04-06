package top.mc506lw.monolith.test.blocks

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer
import top.mc506lw.rebar.MonolithLib

object TestBlocks {
    
    fun registerAll() {
        registerBlockWithItem(TestControllerBlock.KEY, TestControllerBlock.MATERIAL, TestControllerBlock::class.java)
        registerBlockWithItem(FurnaceCoreBlock.KEY, FurnaceCoreBlock.MATERIAL, FurnaceCoreBlock::class.java)
        registerBlockWithItem(MachineCoreBlock.KEY, MachineCoreBlock.MATERIAL, MachineCoreBlock::class.java)
        registerBlockWithItem(CustomStructureController.KEY, CustomStructureController.MATERIAL, CustomStructureController::class.java)
        
        println("[MonolithLib Test] 已注册 4 个测试 Rebar 方块")
    }
    
    private fun registerBlockWithItem(key: NamespacedKey, material: Material, blockClass: Class<out RebarBlock>) {
        RebarBlock.register(key, material, blockClass)
        
        val itemStack = ItemStackBuilder.rebar(material, key).build()
        
        RebarItem.register(RebarItem::class.java, itemStack, key)
    }
}

class TestControllerBlock(
    block: Block,
    context: BlockCreateContext
) : RebarBlock(block, context) {
    
    constructor(block: Block, pdc: PersistentDataContainer) : this(block, BlockCreateContext.Default(block))
    
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "test_controller")
        val MATERIAL = Material.CRAFTING_TABLE
    }
}

class FurnaceCoreBlock(
    block: Block,
    context: BlockCreateContext
) : RebarBlock(block, context) {
    
    constructor(block: Block, pdc: PersistentDataContainer) : this(block, BlockCreateContext.Default(block))
    
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "furnace_core")
        val MATERIAL = Material.FURNACE
    }
}

class MachineCoreBlock(
    block: Block,
    context: BlockCreateContext
) : RebarBlock(block, context) {
    
    constructor(block: Block, pdc: PersistentDataContainer) : this(block, BlockCreateContext.Default(block))
    
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "machine_core")
        val MATERIAL = Material.BLAST_FURNACE
    }
}

class CustomStructureController(
    block: Block,
    context: BlockCreateContext
) : RebarBlock(block, context) {
    
    constructor(block: Block, pdc: PersistentDataContainer) : this(block, BlockCreateContext.Default(block))
    
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "custom_controller")
        val MATERIAL = Material.LODESTONE
    }
}
