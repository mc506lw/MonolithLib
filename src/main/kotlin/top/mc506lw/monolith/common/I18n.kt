package top.mc506lw.monolith.common

import io.github.pylonmc.rebar.i18n.RebarArgument
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import top.mc506lw.rebar.MonolithLib

object I18n {
    
    private const val PREFIX = "monolithlib"
    
    fun key(path: String): String = "$PREFIX.$path"
    
    fun translatable(path: String): TranslatableComponent {
        return Component.translatable(key(path))
    }
    
    fun translatable(path: String, vararg args: Pair<String, Any>): TranslatableComponent {
        val translationArgs = args.map { (name, value) ->
            RebarArgument.of(name, Component.text(value.toString()))
        }
        return Component.translatable(key(path), translationArgs)
    }
    
    fun translatable(path: String, vararg args: RebarArgument): TranslatableComponent {
        return Component.translatable(key(path), args.toList())
    }
    
    fun arg(name: String, value: Any): RebarArgument {
        return RebarArgument.of(name, Component.text(value.toString()))
    }
    
    fun arg(name: String, component: Component): RebarArgument {
        return RebarArgument.of(name, component)
    }
    
    object Message {
        object Preview {
            fun started(structureId: String, facing: String) = translatable(
                "message.preview.started",
                arg("structure_id", structureId),
                arg("facing", facing)
            )
            
            fun stopped(count: Int) = translatable(
                "message.preview.stopped",
                arg("count", count)
            )
            
            val expired = translatable("message.preview.expired")
            val noActive = translatable("message.preview.no_active")
            
            fun layerChanged(layer: Int) = translatable(
                "message.preview.layer_changed",
                arg("layer", layer)
            )
            
            val layerUp = translatable("message.preview.layer_up")
            val layerDown = translatable("message.preview.layer_down")
            val layerTop = translatable("message.preview.layer_top")
            val layerBottom = translatable("message.preview.layer_bottom")
            
            fun layerInvalid(layer: String) = translatable(
                "message.preview.layer_invalid",
                arg("layer", layer)
            )
            
            val layerOutOfRange = translatable("message.preview.layer_out_of_range")
            
            fun currentLayer(current: Int, max: Int) = translatable(
                "message.preview.current_layer",
                arg("current", current),
                arg("max", max)
            )
            
            fun currentPosition(x: Int, y: Int, z: Int) = translatable(
                "message.preview.current_position",
                arg("x", x),
                arg("y", y),
                arg("z", z)
            )
            
            val useLayerCommand = translatable("message.preview.use_layer_command")
            
            fun progress(correct: Int, total: Int, completed: String = "") = translatable(
                "message.preview.progress",
                arg("correct", correct),
                arg("total", total),
                arg("completed", completed)
            )
            
            fun completed(structureId: String) = translatable(
                "message.preview.completed",
                arg("structure_id", structureId)
            )
            
            val controllerBroken = translatable("message.preview.controller_broken")
            
            fun notFound(structureId: String) = translatable(
                "message.preview.not_found",
                arg("structure_id", structureId)
            )
            
            val startPreview = translatable("message.preview.start_preview")
            val previewWillExpire = translatable("message.preview.preview_will_expire")
            val previewCancelled = translatable("message.preview.preview_cancelled")
            val previewFailed = translatable("message.preview.preview_failed")
        }
        
        object Structure {
            fun formed(structureId: String) = translatable(
                "message.structure.formed",
                arg("structure_id", structureId)
            )
            
            fun broken(structureId: String) = translatable(
                "message.structure.broken",
                arg("structure_id", structureId)
            )
            
            fun notFound(structureId: String) = translatable(
                "message.structure.not_found",
                arg("structure_id", structureId)
            )
            
            val noBlocks = translatable("message.structure.no_blocks")
            val addBlocksHint = translatable("message.structure.add_blocks_hint")
            
            fun controllerDetected(key: String) = translatable(
                "message.structure.controller_detected",
                arg("key", key)
            )
            
            fun associatedStructure(structureId: String) = translatable(
                "message.structure.associated_structure",
                arg("structure_id", structureId)
            )
            
            fun blockCount(count: Int) = translatable(
                "message.structure.block_count",
                arg("count", count)
            )
            
            fun slots(slots: String) = translatable(
                "message.structure.slots",
                arg("slots", slots)
            )
            
            fun customData(count: Int) = translatable(
                "message.structure.custom_data",
                arg("count", count)
            )
        }
        
        object Command {
            val permissionDenied = translatable("message.permission_denied")
            val playerOnly = translatable("message.player_only")
            
            object Help {
                val title = translatable("message.command.help.title")
                val reload = translatable("message.command.help.reload")
                val list = translatable("message.command.help.list")
                val info = translatable("message.command.help.info")
                val preview = translatable("message.command.help.preview")
                val build = translatable("message.command.help.build")
                val usage = translatable("message.command.help.usage")
                val step1 = translatable("message.command.help.step1")
                val step2 = translatable("message.command.help.step2")
                val step3 = translatable("message.command.help.step3")
            }
            
            object TestHelp {
                val title = translatable("message.command.test_help.title")
                val list = translatable("message.command.test_help.list")
                val info = translatable("message.command.test_help.info")
                val preview = translatable("message.command.test_help.preview")
                val layer = translatable("message.command.test_help.layer")
                val slots = translatable("message.command.test_help.slots")
                val custom = translatable("message.command.test_help.custom")
                val stop = translatable("message.command.test_help.stop")
                val previewFeatures = translatable("message.command.test_help.preview_features")
                val feature1 = translatable("message.command.test_help.feature1")
                val feature2 = translatable("message.command.test_help.feature2")
                val feature3 = translatable("message.command.test_help.feature3")
                val testStructures = translatable("message.command.test_help.test_structures")
                val testSimple = translatable("message.command.test_help.test_simple")
                val testRebar = translatable("message.command.test_help.test_rebar")
                val testLoose = translatable("message.command.test_help.test_loose")
                val testBlocks = translatable("message.command.test_help.test_blocks")
                val blockTestController = translatable("message.command.test_help.block_test_controller")
                val blockFurnaceCore = translatable("message.command.test_help.block_furnace_core")
                val blockMachineCore = translatable("message.command.test_help.block_machine_core")
                val blockCustomController = translatable("message.command.test_help.block_custom_controller")
            }
            
            object Reload {
                val starting = translatable("message.command.reload.starting")
                fun complete(count: Int) = translatable(
                    "message.command.reload.complete",
                    arg("count", count)
                )
            }
            
            object List {
                fun title(count: Int) = translatable(
                    "message.command.list.title",
                    arg("count", count)
                )
                val empty = translatable("message.command.list.empty")
                fun hint(path: String) = translatable(
                    "message.command.list.hint",
                    arg("path", path)
                )
                val formats = translatable("message.command.list.formats")
            }
            
            object Files {
                val title = translatable("message.command.files.title")
                val imports = translatable("message.command.files.imports")
                val blueprints = translatable("message.command.files.blueprints")
                val products = translatable("message.command.files.products")
                fun importsTitle(count: Int) = translatable(
                    "message.command.files.imports_title",
                    arg("count", count)
                )
                fun blueprintsTitle(count: Int) = translatable(
                    "message.command.files.blueprints_title",
                    arg("count", count)
                )
                fun productsTitle(count: Int) = translatable(
                    "message.command.files.products_title",
                    arg("count", count)
                )
                val statusReady = translatable("message.command.files.status_ready")
                val statusPending = translatable("message.command.files.status_pending")
                val statusMissing = translatable("message.command.files.status_missing")
                fun moreFiles(count: Int) = translatable(
                    "message.command.files.more_files",
                    arg("count", count)
                )
                val noFiles = translatable("message.command.files.no_files")
                val noFilesHint = translatable("message.command.files.no_files_hint")
            }
            
            object Info {
                val title = translatable("message.command.info.title")
                fun version(version: String) = translatable(
                    "message.command.info.version",
                    arg("version", version)
                )
                fun registered(count: Int) = translatable(
                    "message.command.info.registered",
                    arg("count", count)
                )
                fun importDir(path: String) = translatable(
                    "message.command.info.import_dir",
                    arg("path", path)
                )
                fun blueprintDir(path: String) = translatable(
                    "message.command.info.blueprint_dir",
                    arg("path", path)
                )
                fun productDir(path: String) = translatable(
                    "message.command.info.product_dir",
                    arg("path", path)
                )
                val formats = translatable("message.command.info.formats")
                val rebarEnabled = translatable("message.command.info.rebar_enabled")
                val rebarDisabled = translatable("message.command.info.rebar_disabled")
                fun rebarIntegration(status: Component) = translatable(
                    "message.command.info.rebar_integration",
                    arg("status", status)
                )
                fun blueprintNotFound(id: String) = translatable(
                    "message.command.info.blueprint_not_found",
                    arg("id", id)
                )
                fun blueprintTitle(id: String) = translatable(
                    "message.command.info.blueprint_title",
                    arg("id", id)
                )
                fun size(x: Int, y: Int, z: Int) = translatable(
                    "message.command.info.size",
                    arg("x", x),
                    arg("y", y),
                    arg("z", z)
                )
                fun blockCount(count: Int) = translatable(
                    "message.command.info.block_count",
                    arg("count", count)
                )
                fun name(name: String) = translatable(
                    "message.command.info.name",
                    arg("name", name)
                )
                fun description(description: String) = translatable(
                    "message.command.info.description",
                    arg("description", description)
                )
            }
            
            fun usage(usage: String) = translatable(
                "message.command.usage",
                arg("usage", usage)
            )
            val pointAtController = translatable("message.command.point_at_controller")
            val controllerMustBeRebar = translatable("message.command.controller_must_be_rebar")
            val buildCancelled = translatable("message.command.build_cancelled")
            val buildFailed = translatable("message.command.build_failed")
        }
        
        object Test {
            val controllerDetected = translatable("message.test.controller_detected")
            val furnaceDetected = translatable("message.test.furnace_detected")
            val machineDetected = translatable("message.test.machine_detected")
            val rebarRequired = translatable("message.test.rebar_required")
            val customDetected = translatable("message.test.custom_detected")
            val customHint = translatable("message.test.custom_hint")
            fun previewStarted(structureId: String) = translatable(
                "message.test.preview_started",
                arg("structure_id", structureId)
            )
        }
        
        object Rebar {
            val integrationEnabled = translatable("message.rebar.integration_enabled")
            val integrationDisabled = translatable("message.rebar.integration_disabled")
            fun integrationFailed(error: String) = translatable(
                "message.rebar.integration_failed",
                arg("error", error)
            )
        }
        
        object Io {
            fun importedBlueprint(name: String, count: Int) = translatable(
                "message.io.imported_blueprint",
                arg("name", name),
                arg("count", count)
            )
            fun rebuiltProduct(name: String) = translatable(
                "message.io.rebuilt_product",
                arg("name", name)
            )
            fun loadedStandalone(name: String) = translatable(
                "message.io.loaded_standalone",
                arg("name", name)
            )
            fun loadFailed(file: String, error: String) = translatable(
                "message.io.load_failed",
                arg("file", file),
                arg("error", error)
            )
            fun saveFailed(file: String, error: String) = translatable(
                "message.io.save_failed",
                arg("file", file),
                arg("error", error)
            )
        }
        
        object Init {
            val starting = translatable("message.init.starting")
            fun complete(version: String) = translatable(
                "message.init.complete",
                arg("version", version)
            )
            val shuttingDown = translatable("message.init.shutting_down")
            val shutdownComplete = translatable("message.init.shutdown_complete")
            fun registeringStructure(id: String, size: String, count: Int) = translatable(
                "message.init.registering_structure",
                arg("id", id),
                arg("size", size),
                arg("count", count)
            )
            fun loadedStructures(count: Int) = translatable(
                "message.init.loaded_structures",
                arg("count", count)
            )
            val testModuleInit = translatable("message.init.test_module_init")
            val testModuleComplete = translatable("message.init.test_module_complete")
            fun testStructuresRegistered(count: Int) = translatable(
                "message.init.test_structures_registered",
                arg("count", count)
            )
            fun testBlocksRegistered(count: Int) = translatable(
                "message.init.test_blocks_registered",
                arg("count", count)
            )
        }
    }
}
