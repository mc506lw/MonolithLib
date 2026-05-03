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
                val blueprint = translatable("message.command.help.blueprint")
                val usage = translatable("message.command.help.usage")
                val step1 = translatable("message.command.help.step1")
                val step2 = translatable("message.command.help.step2")
                val step3 = translatable("message.command.help.step3")
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
            
            object Blueprint {
                val title = translatable("message.command.blueprint.title")
                fun given(id: String) = translatable(
                    "message.command.blueprint.given",
                    arg("id", id)
                )
                fun notFound(id: String) = translatable(
                    "message.command.blueprint.not_found",
                    arg("id", id)
                )
                val noId = translatable("message.command.blueprint.no_id")
                val inventoryFull = translatable("message.command.blueprint.inventory_full")
                val hint = translatable("message.command.blueprint.hint")
            }
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
        
        object BuildSite {
            val previewCancelled = translatable("message.buildsite.preview_cancelled")
            val targetNotAir = translatable("message.buildsite.target_not_air")
            fun siteExists(blueprintId: String) = translatable(
                "message.buildsite.site_exists",
                arg("blueprint_id", blueprintId)
            )
            val blueprintCorrupted = translatable("message.buildsite.blueprint_corrupted")
            fun blueprintNotRegistered(blueprintId: String) = translatable(
                "message.buildsite.blueprint_not_registered",
                arg("blueprint_id", blueprintId)
            )
            val previewFailed = translatable("message.buildsite.preview_failed")
            val validationFailed = translatable("message.buildsite.validation_failed")
            val fixIssues = translatable("message.buildsite.fix_issues")
            val siteCreateFailed = translatable("message.buildsite.site_create_failed")
            val siteCreated = translatable("message.buildsite.site_created")
            fun siteInfo(blueprintId: String, facing: String) = translatable(
                "message.buildsite.site_info",
                arg("blueprint_id", blueprintId),
                arg("facing", facing)
            )
            fun siteLayers(total: Int, current: Int) = translatable(
                "message.buildsite.site_layers",
                arg("total", total),
                arg("current", current)
            )
            val siteCoreHint = translatable("message.buildsite.site_core_hint")
            fun existingBlocks(count: Int) = translatable(
                "message.buildsite.existing_blocks",
                arg("count", count)
            )
            fun layerCompleted(layer: Int) = translatable(
                "message.buildsite.layer_completed",
                arg("layer", layer)
            )
            val placeCorrectController = translatable("message.buildsite.place_correct_controller")
            fun controllerRequired(key: String) = translatable(
                "message.buildsite.controller_required",
                arg("key", key)
            )
            val placeControllerFirst = translatable("message.buildsite.place_controller_first")
            val coreCannotPlace = translatable("message.buildsite.core_cannot_place")
            val corePlacedValidating = translatable("message.buildsite.core_placed_validating")
            fun structureIncomplete(missing: Int) = translatable(
                "message.buildsite.structure_incomplete",
                arg("missing", missing)
            )
            fun completionRate(rate: Int) = translatable(
                "message.buildsite.completion_rate",
                arg("rate", rate)
            )
            fun blocksNeedFix(count: Int) = translatable(
                "message.buildsite.blocks_need_fix",
                arg("count", count)
            )
            fun blocksFixed(count: Int) = translatable(
                "message.buildsite.blocks_fixed",
                arg("count", count)
            )
            val structureActivated = translatable("message.buildsite.structure_activated")
            val structureCompleteWaiting = translatable("message.buildsite.structure_complete_waiting")
            fun siteCompletedBroadcast(blueprintId: String, player: String) = translatable(
                "message.buildsite.site_completed_broadcast",
                arg("blueprint_id", blueprintId),
                arg("player", player)
            )
            val allLayersComplete = translatable("message.buildsite.all_layers_complete")
            fun allLayersProgress(rate: Int, matched: Int, total: Int) = translatable(
                "message.buildsite.all_layers_progress",
                arg("rate", rate),
                arg("matched", matched),
                arg("total", total)
            )
            fun allLayersFix(count: Int) = translatable(
                "message.buildsite.all_layers_fix",
                arg("count", count)
            )
            val shellCompleteController = translatable("message.buildsite.shell_complete_controller")
            fun shellControllerKey(key: String) = translatable(
                "message.buildsite.shell_controller_key",
                arg("key", key)
            )
            val shellCoreMarker = translatable("message.buildsite.shell_core_marker")
            val shellCompleteNoController = translatable("message.buildsite.shell_complete_no_controller")
            val siteCancelled = translatable("message.buildsite.site_cancelled")
            fun siteCancelledBroadcast(blueprintId: String, player: String) = translatable(
                "message.buildsite.site_cancelled_broadcast",
                arg("blueprint_id", blueprintId),
                arg("player", player)
            )
            fun buildBlockDestroyed(layer: Int) = translatable(
                "message.buildsite.build_block_destroyed",
                arg("layer", layer)
            )
            val structureDisassembled = translatable("message.buildsite.structure_disassembled")
            val hasActivePreviewBlockMode = translatable("message.buildsite.has_active_preview_block_mode")
            val previewExpired = translatable("message.buildsite.preview_expired")
            fun previewCountdown(countdown: Int) = translatable(
                "message.buildsite.preview_countdown",
                arg("countdown", countdown)
            )
            val previewHeader = translatable("message.buildsite.preview_header")
            fun previewBlueprint(blueprintId: String) = translatable(
                "message.buildsite.preview_blueprint",
                arg("blueprint_id", blueprintId)
            )
            fun previewFacing(facing: String) = translatable(
                "message.buildsite.preview_facing",
                arg("facing", facing)
            )
            fun previewSize(width: Int, height: Int, depth: Int) = translatable(
                "message.buildsite.preview_size",
                arg("width", width),
                arg("height", height),
                arg("depth", depth)
            )
            fun previewPosition(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = translatable(
                "message.buildsite.preview_position",
                arg("min_x", minX),
                arg("min_y", minY),
                arg("min_z", minZ),
                arg("max_x", maxX),
                arg("max_y", maxY),
                arg("max_z", maxZ)
            )
            val previewValid = translatable("message.buildsite.preview_valid")
            fun previewErrors(count: Int) = translatable(
                "message.buildsite.preview_errors",
                arg("count", count)
            )
            val previewWarnings = translatable("message.buildsite.preview_warnings")
            val previewInstructions = translatable("message.buildsite.preview_instructions")
            fun previewAutoCancel(seconds: Int) = translatable(
                "message.buildsite.preview_auto_cancel",
                arg("seconds", seconds)
            )
            val previewFooter = translatable("message.buildsite.preview_footer")
        }
        
        object EasyBuild {
            fun layerCompleted(layer: Int) = translatable(
                "message.easybuild.layer_completed",
                arg("layer", layer)
            )
            val allComplete = translatable("message.easybuild.all_complete")
            fun progress(rate: Int, matched: Int, total: Int) = translatable(
                "message.easybuild.progress",
                arg("rate", rate),
                arg("matched", matched),
                arg("total", total)
            )
            fun needFix(count: Int) = translatable(
                "message.easybuild.need_fix",
                arg("count", count)
            )
            fun fixed(count: Int) = translatable(
                "message.easybuild.fixed",
                arg("count", count)
            )
            val shellComplete = translatable("message.easybuild.shell_complete")
            fun shellController(key: String) = translatable(
                "message.easybuild.shell_controller",
                arg("key", key)
            )
            val shellCompleteNoCore = translatable("message.easybuild.shell_complete_no_core")
        }
        
        object Printer {
            fun layerCompleted(layer: Int) = translatable(
                "message.printer.layer_completed",
                arg("layer", layer)
            )
            val allComplete = translatable("message.printer.all_complete")
            fun progress(rate: Int, matched: Int, total: Int) = translatable(
                "message.printer.progress",
                arg("rate", rate),
                arg("matched", matched),
                arg("total", total)
            )
            fun needFix(count: Int) = translatable(
                "message.printer.need_fix",
                arg("count", count)
            )
            fun fixed(count: Int) = translatable(
                "message.printer.fixed",
                arg("count", count)
            )
            val shellComplete = translatable("message.printer.shell_complete")
            fun shellController(key: String) = translatable(
                "message.printer.shell_controller",
                arg("key", key)
            )
            val shellCompleteNoCore = translatable("message.printer.shell_complete_no_core")
        }
        
        object Litematica {
            fun modeAutoDisabled(modes: String) = translatable(
                "message.litematica.mode_auto_disabled",
                arg("modes", modes)
            )
            fun leftRangeCountdown(countdown: Int) = translatable(
                "message.litematica.left_range_countdown",
                arg("countdown", countdown)
            )
            val easybuildEnabled = translatable("message.litematica.easybuild_enabled")
            val easybuildDisabled = translatable("message.litematica.easybuild_disabled")
            val noSiteNearby = translatable("message.litematica.no_site_nearby")
            val printerEnabled = translatable("message.litematica.printer_enabled")
            val printerDisabled = translatable("message.litematica.printer_disabled")
            val printerNoSite = translatable("message.litematica.printer_no_site")
            val usage = translatable("message.litematica.usage")
        }
        
        object BlueprintTable {
            val craftSuccess = translatable("message.blueprint_table.craft_success")
            val guiTitle = translatable("message.blueprint_table.gui_title")
        }
        
        object Builder {
            val worldUnloaded = translatable("message.builder.world_unloaded")
            val conflictingBlocks = translatable("message.builder.conflicting_blocks")
            fun conflictPositions(positions: String) = translatable(
                "message.builder.conflict_positions",
                arg("positions", positions)
            )
            fun moreConflicts(count: Int) = translatable(
                "message.builder.more_conflicts",
                arg("count", count)
            )
            val insufficientMaterials = translatable("message.builder.insufficient_materials")
            fun missingMaterial(material: String, count: Int) = translatable(
                "message.builder.missing_material",
                arg("material", material),
                arg("count", count)
            )
            val worldUnloadedCancelled = translatable("message.builder.world_unloaded_cancelled")
            fun buildProgress(placed: Int, total: Int, progress: String) = translatable(
                "message.builder.build_progress",
                arg("placed", placed),
                arg("total", total),
                arg("progress", progress)
            )
            fun startBuild(blueprintId: String) = translatable(
                "message.builder.start_build",
                arg("blueprint_id", blueprintId)
            )
            fun buildInfo(count: Int) = translatable(
                "message.builder.build_info",
                arg("count", count)
            )
            val buildCancelled = translatable("message.builder.build_cancelled")
            val buildComplete = translatable("message.builder.build_complete")
            fun structureInfo(blueprintId: String) = translatable(
                "message.builder.structure_info",
                arg("blueprint_id", blueprintId)
            )
        }
    }
}
