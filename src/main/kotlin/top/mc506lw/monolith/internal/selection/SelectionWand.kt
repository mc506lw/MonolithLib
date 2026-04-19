package top.mc506lw.monolith.internal.selection

import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.base.RebarBlockInteractor
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import top.mc506lw.rebar.MonolithLib

class SelectionWand(stack: org.bukkit.inventory.ItemStack) : RebarItem(stack), RebarBlockInteractor {

    override fun onUsedToClickBlock(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.action == Action.PHYSICAL) return
        
        val player = event.player
        val block = event.clickedBlock ?: return
        
        event.isCancelled = true
        
        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                SelectionManager.setPos1(player, block)
            }
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                SelectionManager.setPos2(player, block)
            }
            else -> {}
        }
    }
    
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "selection_wand")
        
        val STACK: org.bukkit.inventory.ItemStack by lazy {
            ItemStackBuilder.rebar(Material.BLAZE_ROD, KEY)
                .name(Component.text("\u00a7eMonolith 选区魔杖"))
                .lore(listOf(
                    Component.text("  \u00a77左键方块设置 Pos1", NamedTextColor.GRAY),
                    Component.text("  \u00a77右键方块设置 Pos2", NamedTextColor.GRAY),
                    Component.text("  \u00a77然后使用 /ml save <名称> 保存", NamedTextColor.GRAY)
                ))
                .build()
        }
    }
}
