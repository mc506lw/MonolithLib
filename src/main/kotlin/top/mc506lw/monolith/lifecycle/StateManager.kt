package top.mc506lw.monolith.lifecycle

import org.bukkit.Location
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.transform.CoordinateTransform

class StateManager(
    val structureId: String,
    val controllerLocation: Location,
    val blueprint: Blueprint,
    val transform: CoordinateTransform,
    var state: BuildState = BuildState.UNFORMED
) {
    val positionCache = PositionCache()
    var lastValidationTime: Long = 0L
        private set
    
    fun updateState(newState: BuildState) {
        this.state = newState
        this.lastValidationTime = System.currentTimeMillis()
    }
    
    fun isFormed(): Boolean = state == BuildState.FORMED
    
    fun isUnformed(): Boolean = state == BuildState.UNFORMED || state == BuildState.FORMING
    
    fun markChunkUnloaded() {
        updateState(BuildState.CHUNK_UNLOADED)
    }
    
    fun resetToUnformed() {
        positionCache.clear()
        updateState(BuildState.UNFORMED)
    }
}
