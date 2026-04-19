package top.mc506lw.monolith.internal.listener

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing

object RebarControllerListener : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        val player = event.player
        
        val rebarBlock = BlockStorage.get(block) ?: return
        
        val api = MonolithAPI.getInstance()
        val blueprints = api.registry.getByControllerKey(rebarBlock.key)
        if (blueprints.isEmpty()) return
        
        val blueprint = blueprints.first()
        
        player.sendMessage(I18n.Message.Structure.controllerDetected(rebarBlock.key.toString()))
        player.sendMessage(I18n.Message.Structure.associatedStructure(blueprint.id))
        
        if (blueprint.assembledShape.blocks.isEmpty()) {
            player.sendMessage(I18n.Message.Structure.noBlocks)
            player.sendMessage(I18n.Message.Structure.addBlocksHint)
            return
        }
        
        val facing = detectFacingFromPlayer(player)
        
        val session = api.preview.start(player, blueprint.id, block.location, facing)
        if (session != null) {
            player.sendMessage(I18n.Message.Preview.started(blueprint.id, facing.name))
            player.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
            player.sendMessage(I18n.Message.Structure.blockCount(blueprint.assembledShape.blocks.size))
        }
    }
    
    private fun detectFacingFromPlayer(player: org.bukkit.entity.Player): Facing {
        val yaw = player.location.yaw
        return when {
            yaw >= -45 && yaw < 45 -> Facing.SOUTH
            yaw >= 45 && yaw < 135 -> Facing.WEST
            yaw >= 135 || yaw < -135 -> Facing.NORTH
            yaw >= -135 && yaw < -45 -> Facing.EAST
            else -> Facing.NORTH
        }
    }
}
