package top.mc506lw.monolith.feature.builder

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.Facing
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StructureBuildManager {
    
    private val buildSessions = ConcurrentHashMap<UUID, StructureBuilder>()
    
    fun startBuild(
        player: Player,
        blueprint: Blueprint,
        controllerLocation: Location,
        facing: Facing
    ): StructureBuilder? {
        cancelBuild(player)
        
        val isSurvival = player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR
        
        val builder = StructureBuilder(
            player = player,
            blueprint = blueprint,
            controllerLocation = controllerLocation,
            facing = facing,
            isSurvival = isSurvival
        )
        
        buildSessions[player.uniqueId] = builder
        
        if (builder.startBuild()) {
            return builder
        } else {
            buildSessions.remove(player.uniqueId)
            return null
        }
    }
    
    fun cancelBuild(player: Player) {
        val session = buildSessions.remove(player.uniqueId)
        session?.cancelBuild()
    }
    
    fun getBuild(player: Player): StructureBuilder? {
        return buildSessions[player.uniqueId]
    }
    
    fun isBuilding(player: Player): Boolean {
        return buildSessions.containsKey(player.uniqueId)
    }
    
    fun cleanup() {
        buildSessions.values.forEach { it.cancelBuild() }
        buildSessions.clear()
    }
}
