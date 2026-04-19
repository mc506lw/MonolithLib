package top.mc506lw.monolith.feature.buildsite

import io.github.pylonmc.rebar.block.BlockStorage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
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
                val blueprint = api?.registry?.get(blueprintId) ?: return

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
        val blueprint = api?.registry?.get(blueprintId)
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
            player, blueprint, targetLocation, facing, validationResult
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
        val blueprint = api?.registry?.get(blueprintId)
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

            if (site.checkLayerCompletion()) {
                val advancedCount = site.advanceToNextIncompleteLayer()
                if (advancedCount > 0) {
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.layerCompleted(site.currentLayer)))
                } else {
                    onAllLayersComplete(site, player)
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

        when (site.state) {
            BuildSiteState.AWAITING_CORE -> {
                if (site.canPlaceCore(placedPos)) {
                    val rebarKey = site.coreRebarKey
                    if (rebarKey != null) {
                        val savedItem = event.itemInHand.clone()
                        val blockLocation = placedBlock.location.clone()

                        Bukkit.getLogger().info("[BuildSiteListener] 检测到 Rebar 控制器放置, 期望 key=$rebarKey, 位置=$placedPos")
                        
                        Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                            val currentBlock = blockLocation.block
                            if (currentBlock.type == Material.AIR) {
                                Bukkit.getLogger().info("[BuildSiteListener] 延迟检测时核心位置已为空，跳过")
                                return@Runnable
                            }

                            val placedRebarBlock = BlockStorage.get(currentBlock)
                            
                            if (placedRebarBlock == null || placedRebarBlock.schema.key != rebarKey) {
                                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.placeCorrectController))
                                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.controllerRequired(rebarKey.key)))

                                currentBlock.type = Material.AIR

                                val returnItem = savedItem.clone()
                                returnItem.amount = 1
                                player.inventory.addItem(returnItem)
                                return@Runnable
                            }

                            onCorePlaced(site, player, currentBlock)
                        }, 1L)
                        return
                    }

                    onCorePlaced(site, player, placedBlock)
                } else {
                    event.isCancelled = true
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.placeControllerFirst))
                }
            }

            BuildSiteState.BUILDING_LAYERS -> {
                if (site.isCorePosition(placedPos)) {
                    event.isCancelled = true
                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.coreCannotPlace))
                    return
                }

                site.recordPlacement(placedPos)
                EasyBuildManager.onSiteUpdated(site)

                checkLayerAdvancement(site, player)
            }

            BuildSiteState.COMPLETED -> {
                event.isCancelled = true
            }
        }
    }

    private fun onCorePlaced(site: BuildSite, player: Player, coreBlock: Block) {
        Bukkit.getLogger().info("[BuildSiteListener] === onCorePlaced 开始 ===")
        Bukkit.getLogger().info("[BuildSiteListener] coreBlock: location=${coreBlock.location}, type=${coreBlock.type}")
        
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.corePlacedValidating))

        Bukkit.getLogger().info("[BuildSiteListener] 开始 validateDetailed()...")
        val result = site.validateDetailed()
        Bukkit.getLogger().info("[BuildSiteListener] validateDetailed() 完成: isComplete=${result.isComplete}, needsFix=${result.needsFix}")

        if (!result.isComplete) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureIncomplete(result.totalCount - result.matchedCount)))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.completionRate((result.completionRate * 100).toInt())))

            val rebarBlock = BlockStorage.get(coreBlock)
            if (rebarBlock != null) {
                Bukkit.getLogger().info("[BuildSiteListener] 结构不完整，使用 breakNaturally 移除 Rebar 方块")
                coreBlock.breakNaturally()
            } else {
                coreBlock.type = Material.AIR
            }
            
            val returnItem = org.bukkit.inventory.ItemStack(coreBlock.type)
            returnItem.amount = 1
            player.inventory.addItem(returnItem)
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

        Bukkit.getLogger().info("[BuildSiteListener] onCorePlaced: 准备调用 triggerAssembled, coreRebarKey=${site.coreRebarKey}")
        Bukkit.getLogger().info("[BuildSiteListener] onCorePlaced: coreBlock 位置=${coreBlock.location}, type=${coreBlock.type}")

        site.triggerAssembled()

        Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
            Bukkit.getLogger().info("[BuildSiteListener] onCorePlaced: 延迟任务开始, 准备触发 Rebar")
            Bukkit.getLogger().info("[BuildSiteListener] onCorePlaced: anchorLocation=${site.anchorLocation}, 核心位置方块类型=${site.anchorLocation.block.type}")
            val triggered = top.mc506lw.monolith.validation.RebarTrigger.triggerFormationSync(site.anchorLocation)
            if (triggered) {
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureActivated))
            } else {
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureCompleteWaiting))
            }
        }, 2L)

        site.markCompleted()
        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()

        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCompletedBroadcast(site.blueprintId, player.name)))
        }
    }

    private fun checkLayerAdvancement(site: BuildSite, player: Player) {
        Bukkit.getLogger().info("[BuildSiteListener] checkLayerAdvancement: 开始检查, siteId=${site.id}, currentLayer=${site.currentLayer}, totalLayers=${site.totalLayers}, state=${site.state}")

        if (!site.checkLayerCompletion()) {
            Bukkit.getLogger().info("[BuildSiteListener] checkLayerAdvancement: 当前层未完成")
            return
        }

        Bukkit.getLogger().info("[BuildSiteListener] checkLayerAdvancement: 当前层已完成! 准备推进")

        val advancedCount = site.advanceToNextIncompleteLayer()

        Bukkit.getLogger().info("[BuildSiteListener] checkLayerAdvancement: 推进了 $advancedCount 层, 新的currentLayer=${site.currentLayer}")

        if (advancedCount > 0) {
            for (step in 1..advancedCount) {
                val completedLayer = site.currentLayer - advancedCount + step
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.layerCompleted(completedLayer + 1)))
            }

            Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                    site.renderForPlayer(onlinePlayer)
                }
            }, 5L)

            BuildSiteManager.saveAll()
        } else {
            Bukkit.getLogger().info("[BuildSiteListener] checkLayerAdvancement: 所有层完成，调用 onAllLayersComplete")
            onAllLayersComplete(site, player)
        }
    }

    private fun onAllLayersComplete(site: BuildSite, player: Player) {
        Bukkit.getLogger().info("[BuildSiteListener] === onAllLayersComplete 开始 ===")

        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersComplete))

        val result = site.validateDetailed()

        Bukkit.getLogger().info("[BuildSiteListener] validateDetailed: isComplete=${result.isComplete}, completionRate=${result.completionRate}")

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

        site.enterAwaitingCore()

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val brokenBlock = event.block
        val brokenPos = Vector3i(brokenBlock.x, brokenBlock.y, brokenBlock.z)

        Bukkit.getLogger().info("[BuildSiteListener] onBlockBreak: 玩家=${player.name}, 位置=$brokenPos, 方块类型=${brokenBlock.type}")

        for (site in BuildSiteManager.getAllSites()) {
            if (site.anchorLocation.world?.name != brokenBlock.world.name) continue
            
            Bukkit.getLogger().info("[BuildSiteListener] 检查工地: siteId=${site.id}, state=${site.state}, anchor=${site.anchorLocation.blockX},${site.anchorLocation.blockY},${site.anchorLocation.blockZ}")

            if (brokenBlock.type == Material.RED_STAINED_GLASS && site.isCoreGlassPosition(brokenPos)) {
                if (site.state == BuildSiteState.BUILDING_LAYERS || site.state == BuildSiteState.AWAITING_CORE) {
                    Bukkit.getLogger().info("[BuildSiteListener] 检测到核心玻璃破坏, 取消工地 siteId=${site.id}, state=${site.state}")

                    event.isDropItems = false

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
                } else {
                    Bukkit.getLogger().info("[BuildSiteListener] 核心玻璃破坏但状态为${site.state}, 跳过取消逻辑")
                }
            }

            if (site.state == BuildSiteState.BUILDING_LAYERS && site.containsPosition(brokenPos) && !site.isCorePosition(brokenPos)) {
                val ghost = site.getGhostAt(brokenPos)

                if (ghost != null) {
                    val isPlaced = site.hasPlacedBlock(brokenPos)
                    val isActualBlock = brokenBlock.type != Material.AIR &&
                                        brokenBlock.type != ghost.previewBlockData?.material &&
                                        !io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(brokenBlock)

                    Bukkit.getLogger().info("[BuildSiteListener] BUILDING_LAYERS: 方块破坏, 位置=$brokenPos, 当前方块=${brokenBlock.type}, isPlaced=$isPlaced, isActualBlock=$isActualBlock, scaffold=${ghost.previewBlockData?.material}")

                    if (isPlaced || isActualBlock) {
                        event.isDropItems = false

                        if (isActualBlock) {
                            val dropItem = org.bukkit.inventory.ItemStack(brokenBlock.type)
                            brokenBlock.world.dropItemNaturally(brokenBlock.location, dropItem)
                            Bukkit.getLogger().info("[BuildSiteListener] 掉落了实际放置的方块: ${brokenBlock.type}")
                        }

                        val scaffoldData = ghost.previewBlockData
                        if (scaffoldData != null) {
                            brokenBlock.blockData = scaffoldData.clone()
                            Bukkit.getLogger().info("[BuildSiteListener] 已恢复为scaffold: ${scaffoldData.material}")
                        }

                        site.removePlacement(brokenPos)

                        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.buildBlockDestroyed(site.currentLayer + 1)))

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

            if (site.state == BuildSiteState.COMPLETED) {
                val isCoreController = site.isCorePosition(brokenPos)
                val isAssembledBlock = site.isAssembledPosition(brokenPos)

                Bukkit.getLogger().info("[BuildSiteListener] COMPLETED状态检查: isCoreController=$isCoreController, isAssembledBlock=$isAssembledBlock")

                if (isCoreController || isAssembledBlock) {
                    Bukkit.getLogger().info("[BuildSiteListener] 检测到结构方块破坏！恢复到建造前状态")

                    event.isDropItems = false

                    val world = brokenBlock.world

                    if (brokenBlock.type != Material.AIR) {
                        if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(brokenBlock)) {
                            val rebarBlock = io.github.pylonmc.rebar.block.BlockStorage.get(brokenBlock)
                            if (rebarBlock != null) {
                                val itemStack = io.github.pylonmc.rebar.item.builder.ItemStackBuilder
                                    .rebar(rebarBlock.schema.material, rebarBlock.schema.key)
                                    .build()
                                world.dropItemNaturally(brokenBlock.location, itemStack)
                                Bukkit.getLogger().info("[BuildSiteListener] 掉落了Rebar控制器: ${rebarBlock.schema.key}")
                            }
                        } else {
                            val itemStack = org.bukkit.inventory.ItemStack(brokenBlock.type)
                            world.dropItemNaturally(brokenBlock.location, itemStack)
                            Bukkit.getLogger().info("[BuildSiteListener] 掉落了被破坏的方块: ${brokenBlock.type}")
                        }
                    }

                    val scaffoldShape = site.blueprint.scaffoldShape
                    val transform = top.mc506lw.monolith.core.transform.CoordinateTransform(site.facing)
                    val controllerPos = top.mc506lw.monolith.core.math.Vector3i(
                        site.anchorLocation.blockX,
                        site.anchorLocation.blockY,
                        site.anchorLocation.blockZ
                    )
                    val controllerOffset = site.blueprint.meta.controllerOffset

                    var restoredCount = 0

                    for (blockEntry in scaffoldShape.blocks) {
                        val worldPos = transform.toWorldPosition(
                            controllerPos = controllerPos,
                            relativePos = blockEntry.position,
                            centerOffset = controllerOffset
                        )

                        if (worldPos == brokenPos) continue

                        if (!world.isChunkLoaded(worldPos.x shr 4, worldPos.z shr 4)) continue

                        val partBlock = world.getBlockAt(worldPos.x, worldPos.y, worldPos.z)

                        if (partBlock.type == Material.AIR) continue
                        if (io.github.pylonmc.rebar.block.BlockStorage.isRebarBlock(partBlock)) continue

                        partBlock.blockData = blockEntry.blockData.clone()
                        restoredCount++
                    }

                    Bukkit.getLogger().info("[BuildSiteListener] 结构解体: 恢复了 $restoredCount 个方块到scaffold状态")

                    top.mc506lw.monolith.feature.display.DisplayEntityManager.removeAllForSite(site.id)

                    site.disassembleToBuilding()
                    EasyBuildManager.onSiteUpdated(site)

                    player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureDisassembled))

                    Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
                        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                            site.renderForPlayer(onlinePlayer)
                        }
                    }, 2L)

                    BuildSiteManager.saveAll()
                    return
                }
            }

            if (site.state == BuildSiteState.AWAITING_CORE && site.containsPosition(brokenPos) && !site.isCorePosition(brokenPos)) {
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
        BuildSiteManager.handleChunkLoad(event.world.name, event.chunk.x, event.chunk.z)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        BuildSiteManager.handleChunkUnload(event.world.name, event.chunk.x, event.chunk.z)
    }
}
