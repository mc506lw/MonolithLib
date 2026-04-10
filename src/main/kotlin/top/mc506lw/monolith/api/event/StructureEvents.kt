package top.mc506lw.monolith.api.event

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import top.mc506lw.monolith.core.model.Blueprint

class StructureFormEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val componentBlocks: Map<String, Block>
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class StructureBreakEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val brokenBlock: Block,
    val isController: Boolean
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class BlueprintBlockPlaceEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val block: Block,
    val slotId: String?,
    val isCorrect: Boolean
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class BlueprintBlockBreakEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val block: Block,
    val slotId: String?
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class BlueprintLoadEvent(
    val blueprint: Blueprint,
    val source: String
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class BlueprintUnloadEvent(
    val structureId: String
) : Event() {
    
    override fun getHandlers(): HandlerList = HANDLER_LIST
    
    companion object {
        private val HANDLER_LIST = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
