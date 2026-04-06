package top.mc506lw.monolith.api

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.feature.material.MaterialStats
import top.mc506lw.monolith.feature.preview.PreviewSession

interface MonolithAPI {
    
    fun registerStructure(structure: MonolithStructure)
    
    fun getStructure(id: String): MonolithStructure?
    
    fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing = Facing.NORTH)
    
    fun stopValidation(player: Player)
    
    fun hasActiveValidation(player: Player): Boolean
    
    fun startPreview(player: Player, controllerLocation: Location, structureId: String, facing: Facing = Facing.NORTH): PreviewSession?
    
    fun stopPreview(player: Player)
    
    fun stopPreviewAtLocation(location: Location)
    
    fun hasActivePreview(player: Player): Boolean
    
    fun getPreviewSession(location: Location): PreviewSession?
    
    fun getPlayerPreviewSessions(player: Player): List<PreviewSession>
    
    fun setPreviewLayer(player: Player, layer: Int): Boolean
    
    fun nextPreviewLayer(player: Player): Boolean
    
    fun prevPreviewLayer(player: Player): Boolean
    
    fun getMaterialStats(player: Player, structureId: String): MaterialStats?
    
    fun reloadStructures()
    
    companion object {
        @Volatile
        private var instance: MonolithAPI? = null
        
        fun getInstance(): MonolithAPI {
            return instance ?: throw IllegalStateException("MonolithAPI 尚未初始化")
        }
        
        internal fun setInstance(api: MonolithAPI) {
            instance = api
        }
    }
}
