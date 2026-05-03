package top.mc506lw.monolith.feature.buildsite

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class BuildSiteCancelEvent(
    val site: BuildSite,
    val player: Player,
    returnBlueprint: Boolean,
    cleanUp: Boolean,
    droppedItem: ItemStack?
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

    var returnBlueprint: Boolean = returnBlueprint
        private set

    var cleanUp: Boolean = cleanUp
        private set

    var droppedItem: ItemStack? = droppedItem
        private set

    private var cancelled = false

    override fun getHandlers(): HandlerList = handlerList

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    fun setReturnBlueprint(returnBlueprint: Boolean) {
        this.returnBlueprint = returnBlueprint
    }

    fun setCleanUp(cleanUp: Boolean) {
        this.cleanUp = cleanUp
    }

    fun setDroppedItem(item: ItemStack?) {
        this.droppedItem = item
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
