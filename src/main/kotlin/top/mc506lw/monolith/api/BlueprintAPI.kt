package top.mc506lw.monolith.api

import org.bukkit.Location
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.transform.CoordinateTransform
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.lifecycle.StateManager
import top.mc506lw.monolith.lifecycle.BuildState
import top.mc506lw.monolith.lifecycle.BlueprintLifecycle
import top.mc506lw.monolith.validation.AsyncValidator
import top.mc506lw.monolith.validation.ValidationEngine
import top.mc506lw.monolith.core.io.IOModule
import top.mc506lw.monolith.feature.material.MaterialModule
import top.mc506lw.monolith.feature.preview.PreviewModule
import top.mc506lw.monolith.feature.preview.PreviewSession
import top.mc506lw.monolith.feature.material.MaterialStats
import top.mc506lw.rebar.MonolithLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BlueprintAPI : MonolithAPI {

    private val activeValidations = ConcurrentHashMap<UUID, BlueprintLifecycle>()
    private val previewModule = PreviewModule(MonolithLib.instance)
    private val materialModule = MaterialModule(MonolithLib.instance)

    private val newApi: MonolithAPIImpl = MonolithAPIImpl(MonolithLib.instance.dataFolder)

    override val registry: ShapeRegistry get() = newApi.registry

    override val io: IOFacade get() = newApi.io

    override val preview: PreviewFacade get() = newApi.preview

    override fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing) {
        val blueprint: Blueprint = registry.getBlueprint(structureId) ?: return

        val transform = CoordinateTransform(facing)

        stopValidation(player)

        val stateManager = StateManager(
            structureId = structureId,
            controllerLocation = controllerLocation,
            blueprint = blueprint,
            transform = transform,
            state = BuildState.FORMING
        )

        val validationEngine = ValidationEngine(blueprint, transform)
        val asyncValidator = AsyncValidator(1500L)

        val lifecycle = BlueprintLifecycle(
            stateManager = stateManager,
            blueprint = blueprint,
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

    override fun getMaterialStats(player: Player, structureId: String): MaterialStats? {
        val blueprint: Blueprint = registry.getBlueprint(structureId) ?: return null

        val shape: Shape = blueprint.shape
        val requirements = materialModule.calculateForStructure(structureId, shape.blocks)
        materialModule.updatePlayerInventory(player, structureId, requirements)

        return materialModule.getPlayerStats(player, structureId)
    }

    override fun reloadStructures() {
        newApi.registry.clear()

        val ioModule = IOModule(MonolithLib.instance.dataFolder)
        val blueprints = ioModule.loadAllBlueprints()

        blueprints.forEach { blueprint ->
            registry.registerBlueprint(blueprint)
        }

        println("[MonolithLib] 重新加载了 ${blueprints.size} 个蓝图")
    }

    internal fun getLegacyPreviewModule(): PreviewModule = previewModule

    internal fun getLegacyBlueprint(id: String): Blueprint? = registry.getBlueprint(id)
}
