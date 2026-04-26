package top.mc506lw.monolith.feature.buildsite

import io.github.pylonmc.rebar.block.BlockStorage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
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
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.transform.Facing

class BuildSiteListener : Listener {

    private val legacy = LegacyComponentSerializer.legacySection()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val placedBlock = event.block
        val placedPos = Vector3i(placedBlock.x, placedBlock.y, placedBlock.z)

        val site = BuildSiteManager.getSiteAt(placedPos) ?: return

        when (site.state) {
            BuildSiteState.BUILDING -> handlePlaceInBuilding(site, placedPos, placedBlock, player, event)
            BuildSiteState.AWAITING_CORE -> handlePlaceInAwaitingCore(site, placedPos, placedBlock, player, event)
            BuildSiteState.VIRTUAL -> {
                event.isCancelled = true
                player.sendMessage("\u00a7c[MonolithLib] \u865a\u62df\u673a\u5668\u533a\u57df\u7981\u6b62\u653e\u7f6e\u65b9\u5757")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val brokenBlock = event.block
        val brokenPos = Vector3i(brokenBlock.x, brokenBlock.y, brokenBlock.z)

        val site = BuildSiteManager.getSiteAt(brokenPos) ?: return

        when (site.state) {
            BuildSiteState.BUILDING -> handleBreakInBuilding(site, brokenPos, brokenBlock, player, event)
            BuildSiteState.AWAITING_CORE -> handleBreakInAwaitingCore(site, brokenPos, brokenBlock, player, event)
            BuildSiteState.VIRTUAL -> handleBreakInVirtual(site, brokenPos, brokenBlock, player, event)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val item = player.inventory.itemInMainHand

        if (BlueprintItem.isBlueprintItem(item)) {
            handleBlueprintPlace(event, player, item)
            return
        }

        handleControllerActivation(event, player, item)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to ?: return

        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        for (site in BuildSiteManager.getAllActiveSites()) {
            if (site.state == BuildSiteState.VIRTUAL) continue
            if (site.isCompleted) continue

            val siteWorld = site.anchorLocation.world
            if (siteWorld != player.world) continue

            val dx = to.x - site.anchorLocation.x
            val dy = to.y - site.anchorLocation.y
            val dz = to.z - site.anchorLocation.z
            val distSq = dx * dx + dy * dy + dz * dz

            if (distSq < (BuildSite.UNLOAD_DISTANCE * BuildSite.UNLOAD_DISTANCE).toDouble()) {
                site.renderForPlayer(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        for (site in BuildSiteManager.getAllActiveSites()) {
            site.removeRenderingForPlayer(event.player.uniqueId)
        }
    }

    private fun handleBlueprintPlace(event: PlayerInteractEvent, player: Player, item: ItemStack) {
        val clickedBlock = event.clickedBlock ?: return
        val blockFace = event.blockFace ?: return

        val blueprintId = BlueprintItem.getBlueprintId(item) ?: return
        val blueprint = top.mc506lw.monolith.api.MonolithAPI.getInstance().registry.get(blueprintId) ?: return

        val targetBlock = clickedBlock.getRelative(blockFace)
        val targetLocation = targetBlock.location

        val facing = BlueprintItem.getFacing(item) ?: Facing.fromYaw(player.location.yaw)

        val existing = BuildSiteManager.getSiteAt(Vector3i(targetLocation.blockX, targetLocation.blockY, targetLocation.blockZ))
        if (existing != null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteExists(blueprintId)))
            return
        }

        val site = BuildSiteManager.createSite(blueprint, targetLocation, facing)
        if (site == null) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCreateFailed))
            return
        }

        event.isCancelled = true

        item.amount -= 1
        if (item.amount <= 0) {
            player.inventory.setItemInMainHand(null)
        }

        val existingCount = site.placedBlocks.size
        if (existingCount > 0) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.existingBlocks(existingCount)))
        }

        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCreated))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteInfo(blueprintId, facing.name)))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteLayers(site.totalLayers, site.currentLayer + 1)))
        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCoreHint))

        if (site.checkIfAllNonCoreLayersComplete()) {
            site.enterAwaitingCore()
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersComplete))
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.coreCannotPlace))
        }

        site.renderForPlayer(player)
        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun handleControllerActivation(event: PlayerInteractEvent, player: Player, item: ItemStack) {
        val clickedBlock = event.clickedBlock ?: return
        val clickedPos = Vector3i(clickedBlock.x, clickedBlock.y, clickedBlock.z)

        val rebarItem = try {
            io.github.pylonmc.rebar.item.RebarItem.fromStack(item)
        } catch (e: Exception) {
            Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: RebarItem.fromStack 异常: ${e.message}")
            null
        }

        if (rebarItem == null) {
            Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: 物品不是Rebar物品, type=${item.type}, name=${item.itemMeta?.displayName}")
            return
        }

        Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: 检测到Rebar物品, key=${rebarItem.schema.key}, clickedPos=$clickedPos")

        for (site in BuildSiteManager.getAllActiveSites()) {
            if (site.state != BuildSiteState.AWAITING_CORE) continue

            val isCore = site.isCorePosition(clickedPos) || site.isNearCorePosition(clickedPos)
            Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: 检查工地 ${site.id}, state=${site.state}, isCore=$isCore, clickedPos=$clickedPos, coreWorldPos=${site.coreWorldPos}, coreRebarKey=${site.coreRebarKey}")

            if (!isCore) continue

            if (site.coreRebarKey != null && rebarItem.schema.key != site.coreRebarKey) {
                Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: 控制器key不匹配! 需要=${site.coreRebarKey}, 实际=${rebarItem.schema.key}")
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.placeCorrectController))
                return
            }

            Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: ✅ 匹配成功! 触发虚拟化")

            event.isCancelled = true

            item.amount -= 1
            if (item.amount <= 0) {
                player.inventory.setItemInMainHand(null)
            }

            activateVirtualMachine(site, player)
            return
        }

        Bukkit.getLogger().info("[MonolithLib] handleControllerActivation: 未找到匹配的AWAITING_CORE工地")
    }

    private fun activateVirtualMachine(site: BuildSite, player: Player) {
        site.transitionToVirtual()

        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureActivated))
        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)

        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun handlePlaceInBuilding(
        site: BuildSite, pos: Vector3i, block: Block, player: Player, event: BlockPlaceEvent
    ) {
        if (site.isCorePosition(pos)) {
            event.isCancelled = true
            return
        }

        if (!site.containsPosition(pos)) return

        site.recordPlacement(pos)

        if (site.checkLayerCompletion()) {
            checkLayerAdvancement(site, player)
        }

        site.renderForPlayer(player)
        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun handlePlaceInAwaitingCore(
        site: BuildSite, pos: Vector3i, block: Block, player: Player, event: BlockPlaceEvent
    ) {
        if (site.isCorePosition(pos)) {
            event.isCancelled = true
            return
        }

        if (!site.containsPosition(pos)) return

        site.recordPlacement(pos)
        BuildSiteManager.saveAll()
    }

    private fun handleBreakInBuilding(
        site: BuildSite, pos: Vector3i, block: Block, player: Player, event: BlockBreakEvent
    ) {
        if (site.isCorePosition(pos)) {
            if (block.type == Material.RED_STAINED_GLASS) {
                event.isDropItems = false
                cancelBuildSite(site, player, event)
            }
            return
        }

        if (!site.containsPosition(pos)) return

        event.isDropItems = false

        val dropItem = ItemStack(block.type)
        block.world.dropItemNaturally(block.location, dropItem)

        val ghost = site.getGhostAt(pos)
        if (ghost != null) {
            block.blockData = ghost.previewBlockData.clone()
        }

        site.removePlacement(pos)

        val incompleteLayer = site.findFirstIncompleteLayer()
        if (incompleteLayer >= 0 && incompleteLayer < site.currentLayer) {
            site.advanceToLayer(incompleteLayer)
        }

        site.renderForPlayer(player)
        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun handleBreakInAwaitingCore(
        site: BuildSite, pos: Vector3i, block: Block, player: Player, event: BlockBreakEvent
    ) {
        if (site.isCorePosition(pos)) {
            event.isCancelled = true
            return
        }

        if (!site.containsPosition(pos)) return

        event.isDropItems = false

        val dropItem = ItemStack(block.type)
        block.world.dropItemNaturally(block.location, dropItem)

        val ghost = site.getGhostAt(pos)
        if (ghost != null) {
            block.blockData = ghost.previewBlockData.clone()
        }

        site.removePlacement(pos)

        val revertedLayer = site.revertToBuilding()
        if (revertedLayer >= 0) {
            player.sendMessage(legacy.serialize(I18n.Message.BuildSite.buildBlockDestroyed(revertedLayer + 1)))
        }

        site.renderForPlayer(player)
        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun handleBreakInVirtual(
        site: BuildSite, pos: Vector3i, block: Block, player: Player, event: BlockBreakEvent
    ) {
        if (!site.isVirtualPosition(pos)) return

        event.isCancelled = true

        disassembleVirtualMachine(site, player)
    }

    private fun disassembleVirtualMachine(site: BuildSite, player: Player) {
        val success = site.disassembleFromVirtual()
        if (!success) return

        if (site.coreRebarKey != null) {
            try {
                val rebarItem = io.github.pylonmc.rebar.item.builder.ItemStackBuilder
                    .rebar(Material.LODESTONE, site.coreRebarKey)
                    .build()
                player.world.dropItemNaturally(player.location, rebarItem)
            } catch (e: Exception) {
                val fallbackItem = ItemStack(Material.LODESTONE)
                player.world.dropItemNaturally(player.location, fallbackItem)
            }
        }

        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.structureDisassembled))
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f)

        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun cancelBuildSite(
        site: BuildSite, player: Player, event: BlockBreakEvent
    ) {
        event.isDropItems = false

        Bukkit.getLogger().info("[MonolithLib] cancelBuildSite: 取消工地 ${site.id}, 保留玩家放置的方块")

        site.removeCoreGlass()
        site.removeAllRenderings()
        site.removeCoreItemDisplay()

        val blueprintItem = BlueprintItem.create(site.blueprintId)
        BlueprintItem.setFacing(blueprintItem, site.facing)
        player.inventory.addItem(blueprintItem)

        player.sendMessage(legacy.serialize(I18n.Message.BuildSite.siteCancelled))

        BuildSiteManager.removeSite(site.id)
        BuildSiteManager.saveAll()
    }

    private fun checkLayerAdvancement(site: BuildSite, player: Player) {
        if (!site.checkLayerCompletion()) return

        val advancedCount = site.advanceToNextIncompleteLayer()

        if (advancedCount > 0) {
            for (step in 1..advancedCount) {
                val completedLayer = site.currentLayer - advancedCount + step
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.layerCompleted(completedLayer + 1)))
            }

            if (site.isLastLayer() && site.isLayerFullyPlaced(site.currentLayer)) {
                site.enterAwaitingCore()
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersComplete))
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.coreCannotPlace))

                scheduleRenderingUpdate(site)
                EasyBuildManager.onSiteUpdated(site)
                BuildSiteManager.saveAll()
                return
            }

            scheduleRenderingUpdate(site)
        } else {
            if (site.checkIfAllNonCoreLayersComplete()) {
                site.enterAwaitingCore()
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.allLayersComplete))
                player.sendMessage(legacy.serialize(I18n.Message.BuildSite.coreCannotPlace))

                scheduleRenderingUpdate(site)
                EasyBuildManager.onSiteUpdated(site)
                BuildSiteManager.saveAll()
                return
            }
        }

        EasyBuildManager.onSiteUpdated(site)
        BuildSiteManager.saveAll()
    }

    private fun scheduleRenderingUpdate(site: BuildSite) {
        Bukkit.getScheduler().runTaskLater(top.mc506lw.rebar.MonolithLib.instance, Runnable {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                site.renderForPlayer(onlinePlayer)
            }
        }, 5L)
    }
}
