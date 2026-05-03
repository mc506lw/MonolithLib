package top.mc506lw.monolith.feature.virtual

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.base.RebarBreakHandler
import io.github.pylonmc.rebar.block.base.RebarEntityHolderBlock
import io.github.pylonmc.rebar.block.context.BlockBreakContext
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import top.mc506lw.rebar.MonolithLib

class VirtualDisplayAnchor(
    block: Block,
    context: BlockCreateContext
) : RebarBlock(block, context), RebarEntityHolderBlock, RebarBreakHandler {

    constructor(block: Block, pdc: PersistentDataContainer) : this(block, BlockCreateContext.Default(block))

    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "virtual_display_anchor")
        val MATERIAL = Material.BARRIER

        const val ENTITY_PREFIX = "vde_"
    }

    override var disableBlockTextureEntity = true

    override fun onBreak(drops: MutableList<ItemStack>, context: BlockBreakContext) {
        drops.clear()
        tryRemoveAllEntities()
    }

    override fun postLoad() {
        org.bukkit.Bukkit.getLogger().info("[VirtualDisplayAnchor] postLoad: 检查展示实体恢复, ${blockLocationStr}")
    }

    private val blockLocationStr get() = "pos=(${block.x},${block.y},${block.z})"
}
