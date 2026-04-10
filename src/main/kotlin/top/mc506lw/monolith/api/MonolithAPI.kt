package top.mc506lw.monolith.api

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.feature.preview.PreviewSession
import top.mc506lw.monolith.core.transform.Facing
import org.bukkit.NamespacedKey
import top.mc506lw.monolith.feature.material.MaterialStats
import java.io.File

interface ShapeRegistry {
    fun registerBlueprint(blueprint: Blueprint)
    fun getBlueprint(id: String): Blueprint?
    fun getShape(id: String): Shape?
    fun getAllBlueprints(): Map<String, Blueprint>
    fun getBlueprintsByControllerKey(key: NamespacedKey): List<Blueprint>
    fun contains(id: String): Boolean
    fun remove(id: String): Blueprint?
    fun clear()
    val size: Int
}

interface IOFacade {
    fun loadShape(file: File, format: String? = null): Shape?
    fun loadShapeRotated(file: File, facingRotationSteps: Int): Shape?
    fun getSupportedFormats(): List<String>
    fun getSupportedExtensions(): Set<String>
}

interface PreviewFacade {
    fun start(
        player: Player,
        blueprint: Blueprint,
        anchorLocation: Location,
        facing: Facing = Facing.NORTH
    ): PreviewSession?

    fun start(
        player: Player,
        blueprintId: String,
        anchorLocation: Location,
        facing: Facing = Facing.NORTH
    ): PreviewSession?

    fun stop(player: Player)
    fun stopAtLocation(location: Location)
    fun hasActive(player: Player): Boolean
    fun getSession(location: Location): PreviewSession?
    fun getPlayerSessions(player: Player): List<PreviewSession>
    fun setLayer(player: Player, layer: Int): Boolean
    fun nextLayer(player: Player): Boolean
    fun prevLayer(player: Player): Boolean
}

interface MonolithAPI {

    val registry: ShapeRegistry

    val io: IOFacade

    val preview: PreviewFacade

    fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing = Facing.NORTH)

    fun stopValidation(player: Player)

    fun hasActiveValidation(player: Player): Boolean

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
