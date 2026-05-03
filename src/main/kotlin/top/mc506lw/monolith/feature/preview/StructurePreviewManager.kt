package top.mc506lw.monolith.feature.preview

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.rebar.MonolithLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StructurePreviewManager {
    
    private val previewSessions = ConcurrentHashMap<UUID, PreviewSession>()
    private val sessionCreatedAt = ConcurrentHashMap<UUID, Long>()
    private val updateTasks = ConcurrentHashMap<UUID, BukkitTask>()
    
    val TIMEOUT_TICKS = 200L
    
    fun startPreview(
        player: Player,
        blueprint: Blueprint,
        controllerLocation: Location,
        facing: Facing
    ): PreviewSession? {
        cancelPreview(player)

        val assembledShape = blueprint.assembledShape
        if (assembledShape.blocks.isEmpty()) {
            player.sendMessage("§c[MonolithLib] 蓝图 ${blueprint.id} 的成型阶段为空，无法预览")
            Bukkit.getLogger().warning("[Preview] 蓝图 ${blueprint.id} assembledShape 为空，blockCount=${blueprint.blockCount}")
            return null
        }
        
        val session = PreviewSession(
            sessionId = "${player.uniqueId}-${System.currentTimeMillis()}",
            playerId = player.uniqueId,
            blueprintId = blueprint.id,
            blueprint = blueprint,
            controllerLocation = controllerLocation,
            facing = facing,
            renderRadius = 64
        )
        
        Bukkit.getLogger().info("[Preview] 启动预览: blueprint=${blueprint.id}, scaffoldBlocks=${blueprint.scaffoldShape.blocks.size}, assembledBlocks=${blueprint.assembledShape.blocks.size}, ghosts=${session.ghostBlockCount}, radius=64, allLayers=true")
        
        previewSessions[player.uniqueId] = session
        sessionCreatedAt[player.uniqueId] = System.currentTimeMillis()
        session.showAllLayers = true
        session.start()
        
        startUpdateLoop(player, session)
        
        return session
    }
    
    private fun startUpdateLoop(player: Player, session: PreviewSession) {
        Bukkit.getLogger().info("[Preview] 启动update循环: player=${player.name}, session=${session.sessionId}, isActive=${session.isActive}")
        
        val task = Bukkit.getScheduler().runTaskTimer(MonolithLib.instance, Runnable {
            if (!session.isActive) {
                Bukkit.getLogger().info("[Preview] update循环退出: session不再active")
                updateTasks.remove(player.uniqueId)?.cancel()
                return@Runnable
            }
            
            checkTimeouts()
            
            val p = Bukkit.getPlayer(player.uniqueId) ?: run {
                Bukkit.getLogger().info("[Preview] update循环退出: 玩家离线")
                cancelPreview(player)
                return@Runnable
            }
            
            val result = session.update(p)
            
            when (result) {
                PreviewSession.UpdateResult.COMPLETED -> {
                    p.sendMessage("§a[MonolithLib] §f蓝图构建完成！")
                    cancelPreview(p)
                }
                PreviewSession.UpdateResult.CONTROLLER_BROKEN -> {
                    p.sendMessage("§c[MonolithLib] §f控制器已被破坏，预览取消")
                    cancelPreview(p)
                }
                PreviewSession.UpdateResult.STOPPED -> {
                    Bukkit.getLogger().info("[Preview] update返回STOPPED, 取消预览")
                    cancelPreview(p)
                }
                PreviewSession.UpdateResult.CONTINUE -> {
                }
            }
        }, 5L, 5L)
        
        updateTasks[player.uniqueId] = task
    }
    
    fun cancelPreview(player: Player) {
        val task = updateTasks.remove(player.uniqueId)
        task?.cancel()
        
        val session = previewSessions.remove(player.uniqueId)
        sessionCreatedAt.remove(player.uniqueId)
        session?.stop()
    }
    
    fun getPreview(player: Player): PreviewSession? {
        return previewSessions[player.uniqueId]
    }
    
    fun hasActivePreview(player: Player): Boolean {
        return previewSessions.containsKey(player.uniqueId)
    }
    
    fun checkTimeouts(): Int {
        val now = System.currentTimeMillis()
        val timeoutMs = TIMEOUT_TICKS * 50L
        var cancelled = 0
        
        val toCancel = mutableListOf<UUID>()
        
        for ((playerId, createdAt) in sessionCreatedAt) {
            if (now - createdAt > timeoutMs) {
                toCancel.add(playerId)
            }
        }
        
        for (playerId in toCancel) {
            val session = previewSessions.remove(playerId)
            sessionCreatedAt.remove(playerId)
            session?.stop()
            val player = Bukkit.getPlayer(playerId)
            player?.sendMessage("§e[MonolithLib] 预览已超时自动取消 (10秒)")
            cancelled++
        }
        
        return cancelled
    }
    
    fun cleanup() {
        updateTasks.values.forEach { it.cancel() }
        updateTasks.clear()
        
        previewSessions.values.forEach { it.stop() }
        previewSessions.clear()
        sessionCreatedAt.clear()
    }
}
