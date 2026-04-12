package top.mc506lw.monolith.feature.buildsite

import io.github.pylonmc.rebar.block.BlockStorage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing

class BuildSiteListener : Listener {
    
    private val legacy = LegacyComponentSerializer.legacySection()
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        if (!BlueprintItem.isBlueprintItem(item)) return
        
        val clickedBlock = event.clickedBlock ?: return
        val targetFace = event.blockFace
        val targetLocation = clickedBlock.getRelative(targetFace).location
        
        if (player.isSneaking) {
            if (BuildSitePreviewManager.hasActivePreview(player)) {
                BuildSitePreviewManager.cancelPreview(player)
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.previewCancelled))
                event.isCancelled = true
            }
            return
        }
        
        val existingPreview = BuildSitePreviewManager.getPreview(player)
        if (existingPreview != null) {
            val distance = targetLocation.distance(existingPreview.anchorLocation)
            
            if (distance <= 1.0) {
                confirmPlacement(player, existingPreview, item)
                event.isCancelled = true
            } else {
                BuildSitePreviewManager.cancelPreview(player)
                
                val targetBlock = targetLocation.block
                if (!targetBlock.type.isAir && !targetBlock.isReplaceable) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.targetNotAir))
                    return
                }
                
                val existingSite = BuildSiteManager.getSiteAt(targetLocation)
                if (existingSite != null) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteExists(existingSite.blueprintId)))
                    return
                }
                
                val blueprintId = BlueprintItem.getBlueprintId(item) ?: return
                val api = try { top.mc506lw.monolith.api.MonolithAPI.getInstance() } catch (_: IllegalStateException) { null }
                val blueprint = api?.registry?.getBlueprint(blueprintId) ?: return
                
                val facing = Facing.fromYaw(player.location.yaw)
                val validationResult = BuildSiteValidator.validate(blueprint, targetLocation, facing)
                
                val newPreview = BuildSitePreviewManager.startPreview(
                    player, blueprint, targetLocation, facing, validationResult
                )
                
                if (newPreview != null) {
                    for (msg in newPreview.getSummaryMessage()) {
                        player.sendMessage(msg)
                    }
                }
                
                event.isCancelled = true
            }
            return
        }
        
        val targetBlock = targetLocation.block
        if (!targetBlock.type.isAir && !targetBlock.isReplaceable) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.targetNotAir))
            return
        }
        
        val blueprintId = BlueprintItem.getBlueprintId(item) ?: run {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blueprintCorrupted))
            return
        }
        
        val api = try { top.mc506lw.monolith.api.MonolithAPI.getInstance() } catch (_: IllegalStateException) { null }
        val blueprint = api?.registry?.getBlueprint(blueprintId)
        if (blueprint == null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blueprintNotRegistered(blueprintId)))
            return
        }
        
        val existingSite = BuildSiteManager.getSiteAt(targetLocation)
        if (existingSite != null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteExists(existingSite.blueprintId)))
            return
        }
        
        val facing = Facing.fromYaw(player.location.yaw)
        
        val validationResult = BuildSiteValidator.validate(blueprint, targetLocation, facing)
        
        val preview = BuildSitePreviewManager.startPreview(
            player,
            blueprint,
            targetLocation,
            facing,
            validationResult
        )
        
        if (preview == null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.previewFailed))
            return
        }
        
        for (msg in preview.getSummaryMessage()) {
            player.sendMessage(msg)
        }
        
        event.isCancelled = true
    }
    
    private fun confirmPlacement(player: Player, preview: BuildSitePreview, item: org.bukkit.inventory.ItemStack) {
        if (!preview.validationResult.isValid) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.validationFailed))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.fixIssues))
            return
        }
        
        val blueprintId = preview.blueprintId
        
        val api = try { top.mc506lw.monolith.api.MonolithAPI.getInstance() } catch (_: IllegalStateException) { null }
        val blueprint = api?.registry?.getBlueprint(blueprintId)
        if (blueprint == null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blueprintNotRegistered(blueprintId)))
            BuildSitePreviewManager.cancelPreview(player)
            return
        }
        
        val anchorLocation = preview.anchorLocation
        
        val facing = preview.facing
        
        val site = BuildSiteManager.createSite(blueprint, anchorLocation, facing)
        
        if (site == null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCreateFailed))
            return
        }
        
        BuildSitePreviewManager.stopPreview(player)
        
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
            player.inventory.setItemInMainHand(itemInHand)
        } else {
            player.inventory.setItemInMainHand(null)
        }
        
        scanExistingBlocks(site, player)
        
        site.renderForPlayer(player)
        EasyBuildManager.onSiteUpdated(site)
        
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCreated))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteInfo(blueprint.id, facing.name)))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteLayers(site.totalLayers, site.currentLayer + 1)))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCoreHint))
    }
    
    private fun scanExistingBlocks(site: BuildSite, player: Player) {
        val world = site.anchorLocation.world ?: return
        var matchedCount = 0
        
        for (ghost in site.allGhostBlocks) {
            if (ghost.isCore) continue
            
            val block = world.getBlockAt(ghost.worldPos.x, ghost.worldPos.y, ghost.worldPos.z)
            if (block.type.isAir) continue
            
            val expectedMaterial = ghost.previewBlockData.material
            if (block.type == expectedMaterial) {
                site.recordPlacement(ghost.worldPos)
                matchedCount++
            }
        }
        
        if (matchedCount > 0) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.existingBlocks(matchedCount)))
            
            while (site.checkLayerCompletion()) {
                if (site.advanceToNextLayer()) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.layerCompleted(site.currentLayer)))
                } else {
                    startFinalPhase(site, player)
                    return
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val placedBlock = event.block
        val placedPos = Vector3i(placedBlock.x, placedBlock.y, placedBlock.z)
        
        var affectedSite: BuildSite? = null
        
        for (site in BuildSiteManager.getAllActiveSites()) {
            if (site.anchorLocation.world?.name != placedBlock.world.name) continue
            
            if (site.containsPosition(placedPos)) {
                affectedSite = site
                break
            }
        }
        
        val site = affectedSite ?: return
        
        if (site.isWaitingForCore) {
            if (site.canPlaceCore(placedPos)) {
                val rebarKey = site.coreRebarKey
                if (rebarKey != null) {
                    Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                        val placedRebarBlock = BlockStorage.get(placedBlock)
                        top.mc506lw.rebar.MonolithLib.instance.logger.info("[CoreCheck] 放置位置: $placedPos")
                        top.mc506lw.rebar.MonolithLib.instance.logger.info("[CoreCheck] 获取的RebarBlock: $placedRebarBlock")
                        if (placedRebarBlock != null) {
                            top.mc506lw.rebar.MonolithLib.instance.logger.info("[CoreCheck] RebarBlock.schema.key: ${placedRebarBlock.schema.key}")
                        }
                        top.mc506lw.rebar.MonolithLib.instance.logger.info("[CoreCheck] 需要的rebarKey: $rebarKey")
                        
                        if (placedRebarBlock == null || placedRebarBlock.schema.key != rebarKey) {
                            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.placeCorrectController))
                            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.controllerRequired(rebarKey.key)))
                            
                            placedBlock.type = Material.AIR
                            val itemStack = event.itemInHand.clone()
                            itemStack.amount = 1
                            player.inventory.addItem(itemStack)
                            return@Runnable
                        }
                        
                        handleCorePlacement(site, player, placedBlock)
                    }, 1L)
                    return
                }
                
                event.isCancelled = true
                handleCorePlacement(site, player, placedBlock)
            } else {
                event.isCancelled = true
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.placeControllerFirst))
            }
            return
        }
        
        if (site.isCorePosition(placedPos)) {
            event.isCancelled = true
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.coreCannotPlace))
            return
        }
        
        site.recordPlacement(placedPos)
        EasyBuildManager.onSiteUpdated(site)
        
        checkLayerAdvancement(site, player)
    }
    
    private fun handleCorePlacement(site: BuildSite, player: Player, coreBlock: Block) {
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.corePlacedValidating))
        
        val result = site.validateDetailed()
        
        if (!result.isComplete) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureIncomplete(result.totalCount - result.matchedCount)))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.completionRate((result.completionRate * 100).toInt())))
            
            coreBlock.type = Material.AIR
            val itemStack = org.bukkit.inventory.ItemStack(coreBlock.type)
            player.inventory.addItem(itemStack)
            return
        }
        
        if (result.needsFix) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blocksNeedFix(result.blocksToFix.size)))
            
            val world = coreBlock.world
            val fixedCount = top.mc506lw.monolith.validation.AutoFixer.fixBlocksSync(result.blocksToFix, world)
            
            if (fixedCount > 0) {
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blocksFixed(fixedCount)))
            }
        }
        
        Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
            val triggered = top.mc506lw.monolith.validation.RebarTrigger.triggerFormationSync(site.anchorLocation)
            
            if (triggered) {
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureActivated))
            } else {
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureCompleteWaiting))
            }
        }, 2L)
        
        site.markCompleted()
        BuildSiteManager.removeSite(site.id)
        
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCompletedBroadcast(site.blueprintId, player.name)))
        }
    }
    
    private fun checkLayerAdvancement(site: BuildSite, player: Player) {
        if (!site.checkLayerCompletion()) return
        
        if (site.advanceToNextLayer()) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.layerCompleted(site.currentLayer)))
            
            Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                    site.renderForPlayer(onlinePlayer)
                }
            }, 5L)
            
            BuildSiteManager.saveAll()
        } else {
            startFinalPhase(site, player)
        }
    }
    
    private fun startFinalPhase(site: BuildSite, player: Player) {
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersComplete))
        
        val result = site.validateDetailed()
        
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersProgress(
            (result.completionRate * 100).toInt(),
            result.matchedCount,
            result.totalCount
        )))
        
        if (result.needsFix) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersFix(result.blocksToFix.size)))
            
            val world = site.anchorLocation.world
            if (world != null) {
                val fixedCount = top.mc506lw.monolith.validation.AutoFixer.fixBlocksSync(result.blocksToFix, world)
                if (fixedCount > 0) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.blocksFixed(fixedCount)))
                }
            }
        }
        
        site.startFinalPhase()
        
        val rebarKey = site.coreRebarKey
        if (rebarKey != null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.shellCompleteController))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.shellControllerKey(rebarKey.key)))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.shellCoreMarker))
        } else {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.shellCompleteNoController))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.shellCoreMarker))
        }
        
        Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                site.renderForPlayer(onlinePlayer)
            }
        }, 10L)
        
        BuildSiteManager.saveAll()
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val brokenBlock = event.block
        val brokenPos = Vector3i(brokenBlock.x, brokenBlock.y, brokenBlock.z)
        
        for (site in BuildSiteManager.getAllActiveSites()) {
            if (site.anchorLocation.world?.name != brokenBlock.world.name) continue
            
            if (brokenBlock.type == Material.RED_STAINED_GLASS && site.isCoreGlassPosition(brokenPos)) {
                site.removeCoreGlass()
                
                val blueprintItem = BlueprintItem.create(site.blueprintId)
                BlueprintItem.setFacing(blueprintItem, site.facing)
                
                val leftover = player.inventory.addItem(blueprintItem)
                if (leftover.isNotEmpty()) {
                    player.world.dropItemNaturally(player.location, blueprintItem)
                }
                
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCancelled))
                
                BuildSiteManager.removeSite(site.id)
                
                for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCancelledBroadcast(site.blueprintId, player.name)))
                }
                
                return
            }
            
            if (site.isWaitingForCore && site.containsPosition(brokenPos) && !site.isCorePosition(brokenPos)) {
                val ghost = site.getGhostAt(brokenPos) ?: continue
                
                if (ghost.previewBlockData.material == brokenBlock.type) {
                    site.removePlacement(brokenPos)
                    
                    val revertedLayer = site.revertFromWaitingForCore()
                    
                    if (revertedLayer >= 0) {
                        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.buildBlockDestroyed(revertedLayer + 1)))
                    }
                    
                    EasyBuildManager.onSiteUpdated(site)
                    
                    Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                            site.renderForPlayer(onlinePlayer)
                        }
                    }, 2L)
                    
                    BuildSiteManager.saveAll()
                    return
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return
        
        BuildSiteManager.onPlayerMove(event.player)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        BuildSiteManager.onPlayerQuit(event.player)
        BuildSitePreviewManager.stopPreview(event.player)
        PrinterManager.onPlayerQuit(event.player.uniqueId)
        LitematicaModeManager.onPlayerQuit(event.player.uniqueId)
        EasyBuildManager.onPlayerQuit(event.player.uniqueId)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        BuildSiteManager.handleChunkLoad(
            event.world.name,
            event.chunk.x,
            event.chunk.z
        )
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        BuildSiteManager.handleChunkUnload(
            event.world.name,
            event.chunk.x,
            event.chunk.z
        )
    }
}