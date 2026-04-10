package top.mc506lw.monolith.feature.preview

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StructurePreviewManager {
    
    private val previewSessions = ConcurrentHashMap<UUID, PreviewSession>()
    
    fun startPreview(
        player: Player,
        blueprint: Blueprint,
        controllerLocation: Location,
        facing: Facing
    ): PreviewSession? {
        cancelPreview(player)
        
        val session = PreviewSession(
            sessionId = "${player.uniqueId}-${System.currentTimeMillis()}",
            playerId = player.uniqueId,
            blueprintId = blueprint.id,
            blueprint = blueprint,
            controllerLocation = controllerLocation,
            facing = facing,
            renderRadius = 64
        )
        
        previewSessions[player.uniqueId] = session
        session.start()
        
        return session
    }
    
    fun cancelPreview(player: Player) {
        val session = previewSessions.remove(player.uniqueId)
        session?.stop()
    }
    
    fun getPreview(player: Player): PreviewSession? {
        return previewSessions[player.uniqueId]
    }
    
    fun hasActivePreview(player: Player): Boolean {
        return previewSessions.containsKey(player.uniqueId)
    }
    
    fun cleanup() {
        previewSessions.values.forEach { it.stop() }
        previewSessions.clear()
    }
}
