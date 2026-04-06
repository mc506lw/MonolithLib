package top.mc506lw.monolith.test

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.test.blocks.TestControllerBlock
import top.mc506lw.monolith.test.blocks.FurnaceCoreBlock
import top.mc506lw.monolith.test.blocks.MachineCoreBlock
import top.mc506lw.monolith.test.blocks.CustomStructureController

object TestBlockListener : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        val player = event.player
        
        val rebarBlock = BlockStorage.get(block)
        if (rebarBlock == null) return
        
        val (structureId, facing) = when (rebarBlock.key) {
            TestControllerBlock.KEY -> {
                player.sendMessage(I18n.Message.Test.controllerDetected)
                "test_simple_3x3" to Facing.NORTH
            }
            FurnaceCoreBlock.KEY -> {
                player.sendMessage(I18n.Message.Test.furnaceDetected)
                "test_loose_furnace" to Facing.NORTH
            }
            MachineCoreBlock.KEY -> {
                player.sendMessage(I18n.Message.Test.machineDetected)
                player.sendMessage(I18n.Message.Test.rebarRequired)
                "test_rebar_machine" to Facing.NORTH
            }
            CustomStructureController.KEY -> {
                player.sendMessage(I18n.Message.Test.customDetected)
                player.sendMessage(I18n.Message.Test.customHint)
                return
            }
            else -> return
        }
        
        val session = MonolithAPI.getInstance().startPreview(player, block.location, structureId, facing)
        if (session != null) {
            player.sendMessage(I18n.Message.Test.previewStarted(structureId))
            player.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
            
            val structure = session.structure
            if (structure.slots.isNotEmpty()) {
                player.sendMessage(I18n.Message.Structure.slots(structure.slots.keys.joinToString(", ")))
            }
            if (structure.customData.isNotEmpty()) {
                player.sendMessage(I18n.Message.Structure.customData(structure.customData.size))
            }
        }
    }
}
