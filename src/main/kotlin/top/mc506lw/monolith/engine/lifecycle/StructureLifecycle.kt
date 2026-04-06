package top.mc506lw.monolith.engine.lifecycle

import org.bukkit.Bukkit
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.engine.state.PositionCache
import top.mc506lw.monolith.engine.state.StateManager
import top.mc506lw.monolith.engine.state.StructureState
import top.mc506lw.monolith.engine.validation.AsyncValidator
import top.mc506lw.monolith.engine.validation.ValidationEngine
import top.mc506lw.monolith.engine.validation.ValidationResult

class StructureLifecycle(
    val stateManager: StateManager,
    private val structure: MonolithStructure,
    private val transform: CoordinateTransform,
    private val validationEngine: ValidationEngine,
    private val asyncValidator: AsyncValidator,
    private val onFormed: (StateManager) -> Unit = {},
    private val onBroken: (StateManager) -> Unit = {},
    private val onIncomplete: (StateManager) -> Unit = {}
) {
    fun startMonitoring() {
        if (stateManager.isUnformed()) {
            asyncValidator.startValidation(stateManager.controllerLocation.toString()) {
                performAsyncValidation()
            }
        }
        
        stateManager.updateState(StructureState.FORMING)
    }
    
    fun stopMonitoring() {
        asyncValidator.stopValidation(stateManager.controllerLocation.toString())
    }
    
    fun handleChunkUnload() {
        stateManager.markChunkUnloaded()
        stopMonitoring()
    }
    
    fun handleChunkLoad() {
        if (stateManager.state == StructureState.CHUNK_UNLOADED) {
            stateManager.resetToUnformed()
            startMonitoring()
        }
    }
    
    private fun performAsyncValidation() {
        val result = validationEngine.validateAsync(stateManager.controllerLocation)
        
        when (result) {
            is ValidationResult.Success -> {
                submitMainThread {
                    stateManager.updateState(StructureState.FORMED)
                    buildPositionCache()
                    onFormed(stateManager)
                }
                stopMonitoring()
            }
            is ValidationResult.Failure -> {
                if (stateManager.state == StructureState.FORMED) {
                    submitMainThread {
                        stateManager.updateState(StructureState.INCOMPLETE)
                        stateManager.positionCache.clear()
                        onBroken(stateManager)
                    }
                }
            }
            ValidationResult.Pending -> {}
        }
    }
    
    private fun buildPositionCache() {
        for (entry in structure.flattenedBlocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = Vector3i(
                    stateManager.controllerLocation.blockX,
                    stateManager.controllerLocation.blockY,
                    stateManager.controllerLocation.blockZ
                ),
                relativePos = entry.relativePosition,
                centerOffset = structure.centerOffset
            )
            
            val chunkKey = PositionCache.getChunkKey(worldPos.x, worldPos.z)
            stateManager.positionCache.addPosition(chunkKey, entry.relativePosition)
        }
    }
    
    private fun submitMainThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(top.mc506lw.rebar.MonolithLib.instance, action)
    }
}
