package top.mc506lw.monolith.api

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.structure.StructureRegistry
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.engine.state.StateManager
import top.mc506lw.monolith.engine.state.StructureState
import top.mc506lw.monolith.engine.validation.AsyncValidator
import top.mc506lw.monolith.engine.validation.ValidationEngine
import top.mc506lw.monolith.engine.lifecycle.StructureLifecycle
import top.mc506lw.monolith.feature.io.IOModule
import top.mc506lw.monolith.feature.material.MaterialModule
import top.mc506lw.monolith.feature.preview.PreviewModule
import top.mc506lw.monolith.feature.preview.PreviewSession
import top.mc506lw.monolith.feature.material.MaterialStats
import top.mc506lw.rebar.MonolithLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StructureAPI : MonolithAPI {
    
    private val activeValidations = ConcurrentHashMap<UUID, StructureLifecycle>()
    private val previewModule = PreviewModule(MonolithLib.instance)
    private val materialModule = MaterialModule(MonolithLib.instance)
    
    override fun registerStructure(structure: MonolithStructure) {
        StructureRegistry.getInstance().register(structure)
    }
    
    override fun getStructure(id: String): MonolithStructure? = StructureRegistry.getInstance().get(id)
    
    override fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing) {
        val structure = getStructure(structureId) ?: return
        
        val transform = CoordinateTransform(facing)
        
        stopValidation(player)
        
        val stateManager = StateManager(
            structureId = structureId,
            controllerLocation = controllerLocation,
            structure = structure,
            transform = transform,
            state = StructureState.FORMING
        )
        
        val validationEngine = ValidationEngine(structure, transform)
        val asyncValidator = AsyncValidator(1500L)
        
        val lifecycle = StructureLifecycle(
            stateManager = stateManager,
            structure = structure,
            transform = transform,
            validationEngine = validationEngine,
            asyncValidator = asyncValidator,
            onFormed = { state ->
                player.sendMessage("§a[MonolithLib] 结构 $structureId 已形成!")
            },
            onBroken = { state ->
                player.sendMessage("§c[MonolithLib] 结构 $structureId 已损坏!")
            }
        )
        
        lifecycle.startMonitoring()
        activeValidations[player.uniqueId] = lifecycle
    }
    
    override fun stopValidation(player: Player) {
        activeValidations[player.uniqueId]?.stopMonitoring()
        activeValidations.remove(player.uniqueId)
    }
    
    override fun hasActiveValidation(player: Player): Boolean = activeValidations.containsKey(player.uniqueId)
    
    override fun startPreview(player: Player, controllerLocation: Location, structureId: String, facing: Facing): PreviewSession? {
        val structure = getStructure(structureId) ?: return null
        
        return previewModule.getGhostRenderer().startPreview(
            player = player,
            controllerLocation = controllerLocation,
            structure = structure,
            facing = facing
        )
    }
    
    override fun stopPreview(player: Player) {
        previewModule.getGhostRenderer().stopPreview(player)
    }
    
    override fun stopPreviewAtLocation(location: Location) {
        previewModule.getGhostRenderer().stopPreviewAtLocation(location)
    }
    
    override fun hasActivePreview(player: Player): Boolean = previewModule.getGhostRenderer().hasActivePreview(player)
    
    override fun getPreviewSession(location: Location): PreviewSession? = previewModule.getGhostRenderer().getSession(location)
    
    override fun getPlayerPreviewSessions(player: Player): List<PreviewSession> = previewModule.getGhostRenderer().getPlayerSessions(player)
    
    override fun setPreviewLayer(player: Player, layer: Int): Boolean {
        return previewModule.getGhostRenderer().setLayer(player, layer)
    }
    
    override fun nextPreviewLayer(player: Player): Boolean {
        return previewModule.getGhostRenderer().nextLayer(player)
    }
    
    override fun prevPreviewLayer(player: Player): Boolean {
        return previewModule.getGhostRenderer().prevLayer(player)
    }
    
    override fun getMaterialStats(player: Player, structureId: String): MaterialStats? {
        val structure = getStructure(structureId) ?: return null
        
        val requirements = materialModule.calculateForStructure(structureId, structure.flattenedBlocks)
        materialModule.updatePlayerInventory(player, structureId, requirements)
        
        return materialModule.getPlayerStats(player, structureId)
    }
    
    override fun reloadStructures() {
        StructureRegistry.getInstance().clear()
        
        val ioModule = IOModule(MonolithLib.instance.dataFolder)
        val structures = ioModule.loadAllStructures()
        
        structures.forEach { StructureRegistry.getInstance().register(it) }
        
        println("[MonolithLib] 重新加载了 ${structures.size} 个结构")
    }
}
