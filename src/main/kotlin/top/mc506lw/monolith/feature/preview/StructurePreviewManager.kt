package top.mc506lw.monolith.feature.preview

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StructurePreviewManager {
    
    private val previewSessions = ConcurrentHashMap<UUID, StructurePreviewSession>()
    
    fun startPreview(
        player: Player,
        structure: MonolithStructure,
        controllerLocation: Location,
        facing: Facing
    ): StructurePreviewSession? {
        cancelPreview(player)
        
        val transform = CoordinateTransform(facing)
        
        val session = StructurePreviewSession(
            sessionId = "${player.uniqueId}-${System.currentTimeMillis()}",
            playerId = player.uniqueId,
            structureId = structure.id,
            structure = structure,
            controllerLocation = controllerLocation,
            transform = transform
        )
        
        previewSessions[player.uniqueId] = session
        session.start()
        
        return session
    }
    
    fun cancelPreview(player: Player) {
        val session = previewSessions.remove(player.uniqueId)
        session?.stop()
    }
    
    fun getPreview(player: Player): StructurePreviewSession? {
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
