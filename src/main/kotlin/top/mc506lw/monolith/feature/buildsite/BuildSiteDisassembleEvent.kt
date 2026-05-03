package top.mc506lw.monolith.feature.buildsite

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class BuildSiteDisassembleEvent(
    val site: BuildSite,
    val player: Player,
    dropRebarItem: Boolean,
    customDrop: ItemStack?
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

    var dropRebarItem: Boolean = dropRebarItem
        private set

    var customDrop: ItemStack? = customDrop
        private set

    private var cancelled = false

    override fun getHandlers(): HandlerList = handlerList

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    fun setDropRebarItem(drop: Boolean) {
        this.dropRebarItem = drop
    }

    fun setCustomDrop(item: ItemStack?) {
        this.customDrop = item
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
