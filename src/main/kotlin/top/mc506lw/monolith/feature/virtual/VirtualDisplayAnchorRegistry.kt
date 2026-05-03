package top.mc506lw.monolith.feature.virtual

import org.bukkit.Material
import org.bukkit.NamespacedKey
import top.mc506lw.rebar.MonolithLib
import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder

object VirtualDisplayAnchorRegistry {

    fun register() {
        RebarBlock.register(VirtualDisplayAnchor.KEY, VirtualDisplayAnchor.MATERIAL, VirtualDisplayAnchor::class.java)

        val itemStack = ItemStackBuilder.rebar(VirtualDisplayAnchor.MATERIAL, VirtualDisplayAnchor.KEY).build()
        RebarItem.register(RebarItem::class.java, itemStack, VirtualDisplayAnchor.KEY)

        println("[MonolithLib] 已注册 VirtualDisplayAnchor (key=${VirtualDisplayAnchor.KEY}, material=${VirtualDisplayAnchor.MATERIAL})")
    }
}
