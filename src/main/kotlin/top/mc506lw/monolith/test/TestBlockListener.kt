package top.mc506lw.monolith.test

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.feature.buildsite.BuildSiteManager
import top.mc506lw.monolith.test.blocks.TestControllerBlock
import top.mc506lw.monolith.test.blocks.FurnaceCoreBlock
import top.mc506lw.monolith.test.blocks.MachineCoreBlock
import top.mc506lw.monolith.test.blocks.CustomStructureController

object TestBlockListener : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        val player = event.player
        val placedPos = Vector3i(block.x, block.y, block.z)
        
        for (site in BuildSiteManager.getAllActiveSites()) {
            if (site.anchorLocation.world?.name != block.world.name) continue
            if (site.isCorePosition(placedPos)) {
                return
            }
        }
        
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
        
        val session = MonolithAPI.getInstance().preview.start(player, structureId, block.location, facing)
        if (session != null) {
            player.sendMessage(I18n.Message.Test.previewStarted(structureId))
            player.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
            
            if (session.blueprint.shape.blocks.isNotEmpty()) {
                player.sendMessage(I18n.Message.Structure.blockCount(session.blueprint.shape.blocks.size))
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.clickedBlock == null) return
        
        val block = event.clickedBlock ?: return
        val player = event.player
        
        val rebarBlock = BlockStorage.get(block)
        if (rebarBlock == null) return
        
        if (rebarBlock.key == FurnaceCoreBlock.KEY) {
            event.isCancelled = true
            
            val structureId = "test_loose_furnace"
            val blueprint = MonolithAPI.getInstance().registry.getBlueprint(structureId)
            
            if (blueprint == null) {
                player.sendMessage("§c[MonolithLib] 蓝图不存在: $structureId")
                return
            }
            
            val transform = top.mc506lw.monolith.core.transform.CoordinateTransform(
                facing = top.mc506lw.monolith.core.transform.Facing.NORTH
            )
            val engine = top.mc506lw.monolith.validation.ValidationEngine(blueprint, transform)
            val result = engine.validateSync(block.location)
            
            when (result) {
                is top.mc506lw.monolith.validation.ValidationResult.Success -> {
                    player.sendMessage("§a[MonolithLib] 结构检测成功!")
                    player.sendMessage("§7匹配方块: §e${result.matchedBlocks}/${result.totalBlocks}")
                    player.sendMessage("§a结构已完成，可以激活机器!")
                }
                is top.mc506lw.monolith.validation.ValidationResult.Failure -> {
                    player.sendMessage("§c[MonolithLib] 结构检测失败!")
                    player.sendMessage("§7错误数量: §c${result.errors.size}")
                    
                    val missing = result.errors.take(5)
                    missing.forEach { error ->
                        val pos = error.position
                        player.sendMessage("§7- §c(${pos.blockX}, ${pos.blockY}, ${pos.blockZ})")
                    }
                    
                    if (result.errors.size > 5) {
                        player.sendMessage("§7... 还有 §c${result.errors.size - 5} §7个错误")
                    }
                }
                is top.mc506lw.monolith.validation.ValidationResult.Pending -> {
                    player.sendMessage("§e[MonolithLib] 正在检测中，请稍后再试...")
                }
            }
        }
    }
}
