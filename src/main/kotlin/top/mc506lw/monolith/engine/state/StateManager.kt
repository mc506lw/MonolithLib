package top.mc506lw.monolith.engine.state

import org.bukkit.Location
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.CoordinateTransform

class StateManager(
    val structureId: String,
    val controllerLocation: Location,
    val structure: MonolithStructure,
    val transform: CoordinateTransform,
    var state: StructureState = StructureState.UNFORMED
) {
    val positionCache = PositionCache()
    var lastValidationTime: Long = 0L
        private set
    
    fun updateState(newState: StructureState) {
        this.state = newState
        this.lastValidationTime = System.currentTimeMillis()
    }
    
    fun isFormed(): Boolean = state == StructureState.FORMED
    
    fun isUnformed(): Boolean = state == StructureState.UNFORMED || state == StructureState.FORMING
    
    fun markChunkUnloaded() {
        updateState(StructureState.CHUNK_UNLOADED)
    }
    
    fun resetToUnformed() {
        positionCache.clear()
        updateState(StructureState.UNFORMED)
    }
}
