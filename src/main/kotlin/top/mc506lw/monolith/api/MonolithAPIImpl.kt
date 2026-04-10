package top.mc506lw.monolith.api

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.io.ShapeIO
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.feature.preview.PreviewSession
import top.mc506lw.monolith.feature.preview.StructurePreviewManager
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.feature.material.MaterialStats
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BlueprintRegistry : ShapeRegistry {

    private val blueprints = ConcurrentHashMap<String, Blueprint>()
    private val shapeCache = ConcurrentHashMap<String, Shape>()

    override fun registerBlueprint(blueprint: Blueprint) {
        blueprints[blueprint.id] = blueprint
        shapeCache[blueprint.id] = blueprint.shape
    }

    override fun getBlueprint(id: String): Blueprint? = blueprints[id]

    override fun getShape(id: String): Shape? = shapeCache[id]

    override fun getAllBlueprints(): Map<String, Blueprint> = blueprints.toMap()

    override fun getBlueprintsByControllerKey(key: NamespacedKey): List<Blueprint> {
        return blueprints.values.filter { it.controllerRebarKey == key }
    }

    override fun contains(id: String): Boolean = blueprints.containsKey(id)

    override fun remove(id: String): Blueprint? {
        val blueprint = blueprints.remove(id)
        if (blueprint != null) {
            shapeCache.remove(id)
        }
        return blueprint
    }

    override fun clear() {
        blueprints.clear()
        shapeCache.clear()
    }

    override val size: Int get() = blueprints.size
}

class ShapeIOFacade(private val dataFolder: File) : IOFacade {

    private val io: ShapeIO = ShapeIO.create(dataFolder)

    override fun loadShape(file: File, format: String?): Shape? {
        return io.loadShape(file, format)
    }

    override fun loadShapeRotated(file: File, facingRotationSteps: Int): Shape? {
        return io.loadShapeRotated(file, facingRotationSteps)
    }

    override fun getSupportedFormats(): List<String> {
        return io.getSupportedFormats()
    }

    override fun getSupportedExtensions(): Set<String> {
        return io.getSupportedExtensions()
    }
}

class ShapePreviewFacade : PreviewFacade {

    private val previewManager = StructurePreviewManager

    override fun start(
        player: Player,
        blueprint: Blueprint,
        anchorLocation: Location,
        facing: Facing
    ): PreviewSession? {
        return previewManager.startPreview(
            player = player,
            blueprint = blueprint,
            controllerLocation = anchorLocation,
            facing = facing
        )
    }

    override fun start(
        player: Player,
        blueprintId: String,
        anchorLocation: Location,
        facing: Facing
    ): PreviewSession? {
        val api = MonolithAPI.getInstance()
        val blueprint = api.registry.getBlueprint(blueprintId) ?: return null
        return previewManager.startPreview(
            player = player,
            blueprint = blueprint,
            controllerLocation = anchorLocation,
            facing = facing
        )
    }

    override fun stop(player: Player) {
        previewManager.cancelPreview(player)
    }

    override fun stopAtLocation(location: Location) {
        
    }

    override fun hasActive(player: Player): Boolean {
        return previewManager.hasActivePreview(player)
    }

    override fun getSession(location: Location): PreviewSession? {
        return null
    }

    override fun getPlayerSessions(player: Player): List<PreviewSession> {
        val session = previewManager.getPreview(player)
        return if (session != null) listOf(session) else emptyList()
    }

    override fun setLayer(player: Player, layer: Int): Boolean {
        val session = previewManager.getPreview(player) ?: return false
        return session.setLayer(layer)
    }

    override fun nextLayer(player: Player): Boolean {
        val session = previewManager.getPreview(player) ?: return false
        return session.nextLayer()
    }

    override fun prevLayer(player: Player): Boolean {
        val session = previewManager.getPreview(player) ?: return false
        return session.prevLayer()
    }

    internal fun getManager() = previewManager

    fun cleanup() {
        previewManager.cleanup()
    }
}

class MonolithAPIImpl(
    private val dataFolder: File
) : MonolithAPI {

    override val registry: ShapeRegistry = BlueprintRegistry()

    override val io: IOFacade = ShapeIOFacade(dataFolder)

    override val preview: PreviewFacade = ShapePreviewFacade()

    override fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing) {
    }

    override fun stopValidation(player: Player) {
    }

    override fun hasActiveValidation(player: Player): Boolean {
        return false
    }

    override fun getMaterialStats(player: Player, structureId: String): MaterialStats? {
        return null
    }

    override fun reloadStructures() {
        registry.clear()
    }
}
