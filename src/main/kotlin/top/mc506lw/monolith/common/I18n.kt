package top.mc506lw.monolith.common

import io.github.pylonmc.rebar.i18n.RebarArgument
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent

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

        object Common {
            val prefix = translatable("chat.common.prefix")
            val errorPermissionDenied = translatable("chat.common.error_permission_denied")
            val errorPlayerOnly = translatable("chat.common.error_player_only")
            val errorInventoryFull = translatable("chat.common.error_inventory_full")
            val hintTabComplete = translatable("chat.common.hint_tab_complete")
        }

        object Command {

            object Help {
                val header = translatable("chat.command.help.header")
                val title = translatable("chat.command.help.title")
                val separator = translatable("chat.command.help.separator")
                val footer = translatable("chat.command.help.footer")
                val footerBlank = translatable("chat.command.help.footer_blank")

                val sectionPreview = translatable("chat.command.help.section_preview")
                val sectionPreviewArg = { arg: String, desc: String ->
                    translatable("chat.command.help.section_preview_arg", arg("arg", arg), arg("desc", desc))
                }
                val sectionBuild = translatable("chat.command.help.section_build")
                val sectionBp = translatable("chat.command.help.section_bp")
                val sectionSite = translatable("chat.command.help.section_site")
                val sectionEdit = translatable("chat.command.help.section_edit")
                val sectionReload = translatable("chat.command.help.section_reload")
            }

            object Reload {
                val starting = translatable("chat.command.reload_starting")
                fun complete(count: Int) = translatable(
                    "chat.command.reload_complete", arg("count", count))
            }

            object List {
                fun title(count: Int) = translatable(
                    "chat.command.list_title", arg("count", count))
                val entry = { id: String, size: String, blockCount: Int, rebarTag: String ->
                    translatable("chat.command.list_entry",
                        arg("id", id), arg("size", size), arg("block_count", blockCount), arg("rebar_tag", rebarTag))
                }
                val empty = translatable("chat.command.list_empty")
                fun hint(path: String) = translatable(
                    "chat.command.list_hint", arg("path", path))
                val formats = translatable("chat.command.list_formats")
            }

            object Info {
                val title = translatable("chat.command.info_title")
                fun version(v: String) = translatable("chat.command.info_version", arg("version", v))
                fun registered(count: Int) = translatable(
                    "chat.command.info_registered", arg("count", count))
                fun importDir(path: String) = translatable(
                    "chat.command.info_import_dir", arg("path", path))
                fun blueprintDir(path: String) = translatable(
                    "chat.command.info_blueprint_dir", arg("path", path))
                fun productDir(path: String) = translatable(
                    "chat.command.info_product_dir", arg("path", path))
                val formats = translatable("chat.command.info_formats")
                val rebarEnabled = translatable("chat.command.info_rebar_enabled")
                val rebarDisabled = translatable("chat.command.info_rebar_disabled")
                fun rebarIntegration(status: Component) = translatable(
                    "chat.command.info_rebar_integration", arg("status", status))
                fun bpNotFound(id: String) = translatable(
                    "chat.command.info_bp_not_found", arg("id", id))

                fun bpTitle(id: String) = translatable(
                    "chat.command.info_bp_title", arg("id", id))
                fun size(x: Int, y: Int, z: Int) = translatable(
                    "chat.command.info_bp_size", arg("x", x), arg("y", y), arg("z", z))
                fun blockCount(count: Int) = translatable(
                    "chat.command.info_bp_blocks", arg("count", count))
                fun name(n: String) = translatable("chat.command.info_bp_name", arg("name", n))
                fun description(d: String) = translatable("chat.command.info_bp_desc", arg("desc", d))
            }

            object Bp {
                fun given(id: String) = translatable("chat.command.bp_given", arg("id", id))
                fun notFound(id: String) = translatable("chat.command.bp_not_found", arg("id", id))
                val noId = translatable("chat.command.bp_no_id")
                val hint = translatable("chat.command.bp_hint")
                val inventoryFull = translatable("chat.command.bp_inventory_full")
            }

            object ErrUsage {
                val preview = translatable("chat.command.err_usage_preview")
                val previewStop = translatable("chat.command.err_usage_preview_stop")
                val build = translatable("chat.command.err_usage_build")
                val buildHere = translatable("chat.command.err_usage_build_here")
                val buildEasy = translatable("chat.command.err_usage_build_easy")
                val buildPrinter = translatable("chat.command.err_usage_build_printer")
                val bp = translatable("chat.command.err_usage_bp")
                val bpList = translatable("chat.command.err_usage_bp_list")
                val bpInfo = translatable("chat.command.err_usage_bp_info")
                val bpGive = translatable("chat.command.err_usage_bp_give")
                val site = translatable("chat.command.err_usage_site")
                val siteList = translatable("chat.command.err_usage_site_list")
                val siteInfo = translatable("chat.command.err_usage_site_info")
                val siteCancel = translatable("chat.command.err_usage_site_cancel")
                val edit = translatable("chat.command.err_usage_edit")
                val editWand = translatable("chat.command.err_usage_edit_wand")
                val editSave = translatable("chat.command.err_usage_edit_save")
                val editMerge = translatable("chat.command.err_usage_edit_merge")
            }

            object ErrUnknown {
                fun preview(arg: String) = translatable(
                    "chat.command.err_unknown_subcmd_preview", arg("arg", arg))
                val availablePreview = translatable("chat.command.err_available_preview")
                fun build(arg: String) = translatable(
                    "chat.command.err_unknown_subcmd_build", arg("arg", arg))
                val availableBuild = translatable("chat.command.err_available_build")
                fun bp(arg: String) = translatable(
                    "chat.command.err_unknown_subcmd_bp", arg("arg", arg))
                val availableBp = translatable("chat.command.err_available_bp")
                fun site(arg: String) = translatable(
                    "chat.command.err_unknown_subcmd_site", arg("arg", arg))
                val availableSite = translatable("chat.command.err_available_site")
                fun edit(arg: String) = translatable(
                    "chat.command.err_unknown_subcmd_edit", arg("arg", arg))
                val availableEdit = translatable("chat.command.err_available_edit")
            }

            fun blueprintNotFound(id: String) = translatable(
                "chat.command.err_blueprint_not_found", arg("id", id))
            val hintBpList = translatable("chat.command.err_hint_bp_list")
            val errBuildFailed = translatable("chat.command.err_build_failed")

            object Edit {
                val noSelection = translatable("chat.command.edit_no_selection")
                val saveDev = translatable("chat.command.edit_save_dev")
                fun saveFailed(error: String) = translatable(
                    "chat.command.edit_save_failed", arg("error", error))
                val mergeDev = translatable("chat.command.edit_merge_dev")
                fun mergeFailed(error: String) = translatable(
                    "chat.command.edit_merge_failed", arg("error", error))
                val wandGiven = translatable("chat.command.wand_given")
                val wandHint = translatable("chat.command.wand_hint")
            }

            object Site {
                fun listTitle(count: Int) = translatable(
                    "chat.command.site.list_title", arg("count", count))
                val stateBuilding = translatable("chat.command.site.list_entry_state_building")
                val stateAwaiting = translatable("chat.command.site.list_entry_state_awaiting")
                val stateVirtual = translatable("chat.command.site.list_entry_state_virtual")
                fun entry(state: Component, blueprintId: String, x: Int, y: Int, z: Int) = translatable(
                    "chat.command.site.list_entry",
                    arg("state", state), arg("blueprint_id", blueprintId),
                    arg("x", x), arg("y", y), arg("z", z))
                val noneNearby = translatable("chat.command.site.list_none")
                val errNoneNearby = translatable("chat.command.site.err_none_nearby")
                val errNoneCancel = translatable("chat.command.site.err_none_cancel")
                fun cancelled(blueprintId: String) = translatable(
                    "chat.command.site.cancelled", arg("blueprint_id", blueprintId))

                fun infoTitle(blueprintId: String) = translatable(
                    "chat.command.site.info_title", arg("blueprint_id", blueprintId))
                fun infoState(state: String) = translatable(
                    "chat.command.site.info_state", arg("state", state))
                fun infoPosition(x: Int, y: Int, z: Int) = translatable(
                    "chat.command.site.info_position",
                    arg("x", x), arg("y", y), arg("z", z))
                fun infoFacing(facing: String) = translatable(
                    "chat.command.site.info_facing", arg("facing", facing))
                fun infoProgress(placed: Int, total: Int, percent: String) = translatable(
                    "chat.command.site.info_progress",
                    arg("placed", placed), arg("total", total), arg("percent", percent))
                val infoCompleted = translatable("chat.command.site.info_completed")
            }

            val permissionDenied = Common.errorPermissionDenied
            val playerOnly = Common.errorPlayerOnly
        }

        object BuildSite {
            val previewCancelled = translatable("chat.build_site.preview_cancelled")
            val errTargetNotAir = translatable("chat.build_site.err_target_not_air")
            fun siteExists(id: String) = translatable(
                "chat.build_site.err_site_exists", arg("blueprint_id", id))
            val errBlueprintCorrupted = translatable("chat.build_site.err_blueprint_corrupted")
            fun blueprintUnregistered(id: String) = translatable(
                "chat.build_site.err_blueprint_unregistered", arg("blueprint_id", id))
            val errPreviewCreateFail = translatable("chat.build_site.err_preview_create_fail")
            val errValidationFailed = translatable("chat.build_site.err_validation_failed")
            val hintFixIssues = translatable("chat.build_site.hint_fix_issues")
            val errCreateFail = translatable("chat.build_site.err_create_fail")
            val created = translatable("chat.build_site.created")
            fun infoLine(blueprintId: String, facing: String) = translatable(
                "chat.build_site.info_line",
                arg("blueprint_id", blueprintId), arg("facing", facing))
            fun layersInfo(total: Int, current: Int) = translatable(
                "chat.build_site.layers_info",
                arg("total", total), arg("current", current))
            val hintCore = translatable("chat.build_site.hint_core")
            fun existingBlocks(count: Int) = translatable(
                "chat.build_site.existing_blocks", arg("count", count))
            fun layerCompleted(layer: Int) = translatable(
                "chat.build_site.layer_completed", arg("layer", layer))
            val errWrongController = translatable("chat.build_site.err_wrong_controller")
            fun controllerRequired(key: String) = translatable(
                "chat.build_site.controller_required", arg("key", key))
            val errPlaceControllerFirst = translatable("chat.build_site.err_place_controller_first")
            val errCoreBlocked = translatable("chat.build_site.err_core_blocked")
            val corePlacedValidating = translatable("chat.build_site.core_placed_validating")
            fun errIncomplete(missing: Int) = translatable(
                "chat.build_site.err_incomplete", arg("missing", missing))
            fun completionRate(rate: Int) = translatable(
                "chat.build_site.completion_rate", arg("rate", rate))
            fun blocksNeedFix(count: Int) = translatable(
                "chat.build_site.warn_blocks_need_fix", arg("count", count))
            fun blocksFixed(count: Int) = translatable(
                "chat.build_site.blocks_fixed", arg("count", count))
            val activated = translatable("chat.build_site.activated")
            val completeWaiting = translatable("chat.build_site.complete_waiting")
            fun completedBroadcast(blueprintId: String, player: String) = translatable(
                "chat.build_site.completed_broadcast",
                arg("blueprint_id", blueprintId), arg("player", player))
            val allLayersComplete = translatable("chat.build_site.all_layers_complete")
            fun allLayersProgress(rate: Int, matched: Int, total: Int) = translatable(
                "chat.build_site.all_layers_progress",
                arg("rate", rate), arg("matched", matched), arg("total", total))
            fun allLayersFix(count: Int) = translatable(
                "chat.build_site.all_layers_fix", arg("count", count))
            val shellDoneController = translatable("chat.build_site.shell_done_controller")
            fun shellControllerKey(key: String) = translatable(
                "chat.build_site.shell_controller_key", arg("key", key))
            val shellCoreMarker = translatable("chat.build_site.shell_core_marker")
            val shellDoneNoController = translatable("chat.build_site.shell_done_no_controller")
            val cancelledReturned = translatable("chat.build_site.cancelled_returned")
            fun cancelledBroadcast(blueprintId: String, player: String) = translatable(
                "chat.build_site.cancelled_broadcast",
                arg("blueprint_id", blueprintId), arg("player", player))
            fun blockDestroyedRevert(layer: Int) = translatable(
                "chat.build_site.block_destroyed_revert", arg("layer", layer))
            val disassembled = translatable("chat.build_site.disassembled")
            val errHasActivePreview = translatable("chat.build_site.err_has_active_preview")
            val previewExpired = translatable("chat.build_site.preview_expired")
            val previewMoved = translatable("chat.build_site.preview_moved")
            val previewRotated = translatable("chat.build_site.preview_rotated")
            fun previewCountdown(countdown: Int) = translatable(
                "chat.build_site.preview_countdown", arg("countdown", countdown))

            val previewHeader = translatable("chat.build_site.preview_header")
            fun previewBlueprint(id: String) = translatable(
                "chat.build_site.preview_blueprint", arg("blueprint_id", id))
            fun previewFacing(facing: String) = translatable(
                "chat.build_site.preview_facing", arg("facing", facing))
            fun previewSize(w: Int, h: Int, d: Int) = translatable(
                "chat.build_site.preview_size",
                arg("width", w), arg("height", h), arg("depth", d))
            fun previewPosition(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) =
                translatable("chat.build_site.preview_position",
                    arg("min_x", minX), arg("min_y", minY), arg("min_z", minZ),
                    arg("max_x", maxX), arg("max_y", maxY), arg("max_z", maxZ))
            val previewValid = translatable("chat.build_site.preview_valid")
            fun previewErrors(count: Int) = translatable(
                "chat.build_site.preview_errors", arg("count", count))
            val previewWarnings = translatable("chat.build_site.preview_warnings")
            val previewInstructions = translatable("chat.build_site.preview_instructions")
            fun previewAutoCancel(seconds: Int) = translatable(
                "chat.build_site.preview_auto_cancel", arg("seconds", seconds))
            val previewFooter = translatable("chat.build_site.preview_footer")
        }

        object BuildMode {
            val easybuildEnabled = translatable("chat.build_mode.easybuild_enabled")
            val easybuildHint1 = translatable("chat.build_mode.easybuild_hint_1")
            val easybuildHint2 = translatable("chat.build_mode.easybuild_hint_2")
            val easybuildHint3 = translatable("chat.build_mode.easybuild_hint_3")
            val easybuildDisabled = translatable("chat.build_mode.easybuild_disabled")
            val printerEnabled = translatable("chat.build_mode.printer_enabled")
            val printerHint1 = translatable("chat.build_mode.printer_hint_1")
            val printerHint2 = translatable("chat.build_mode.printer_hint_2")
            val printerHint3 = translatable("chat.build_mode.printer_hint_3")
            val printerDisabled = translatable("chat.build_mode.printer_disabled")

            val errNoSiteEasybuild = translatable("chat.build_mode.err_no_site_easybuild")
            val errNoSitePrinter = translatable("chat.build_mode.err_no_site_printer")
            fun errModeAutoDisabled(mode: String) = translatable(
                "chat.build_mode.err_mode_auto_disabled", arg("mode", mode))
            fun leftRangeCountdown(countdown: Int) = translatable(
                "chat.build_mode.left_range_countdown", arg("countdown", countdown))
            val usageLitematica = translatable("chat.build_mode.usage_litematica")

            fun layerCompleted(layer: Int) = translatable(
                "chat.build_mode.layer_completed", arg("layer", layer))
            val allComplete = translatable("chat.build_mode.all_complete")
            fun progress(rate: Int, matched: Int, total: Int) = translatable(
                "chat.build_mode.progress",
                arg("rate", rate), arg("matched", matched), arg("total", total))
            fun needFix(count: Int) = translatable(
                "chat.build_mode.need_fix", arg("count", count))
            fun fixed(count: Int) = translatable("chat.build_mode.fixed", arg("count", count))
            val shellComplete = translatable("chat.build_mode.shell_complete")
            fun shellController(key: String) = translatable(
                "chat.build_mode.shell_controller", arg("key", key))
            val shellCompleteNoCore = translatable("chat.build_mode.shell_complete_no_core")
        }

        object Preview {
            fun started(structureId: String, facing: String) = translatable(
                "chat.preview.started",
                arg("structure_id", structureId), arg("facing", facing))
            fun stopped(count: Int) = translatable("chat.preview.stopped", arg("count", count))
            val expired = translatable("chat.preview.expired")
            val errNoActive = translatable("chat.preview.err_no_active")
            fun layerChanged(layer: Int) = translatable(
                "chat.preview.layer_changed", arg("layer", layer))
            val layerUp = translatable("chat.preview.layer_up")
            val layerDown = translatable("chat.preview.layer_down")
            val errLayerTop = translatable("chat.preview.err_layer_top")
            val errLayerBottom = translatable("chat.preview.err_layer_bottom")
            fun errLayerInvalid(layer: String) = translatable(
                "chat.preview.err_layer_invalid", arg("layer", layer))
            val errLayerOutOfRange = translatable("chat.preview.err_layer_out_of_range")
            fun currentLayer(current: Int, max: Int) = translatable(
                "chat.preview.current_layer",
                arg("current", current), arg("max", max))
            fun currentPosition(x: Int, y: Int, z: Int) = translatable(
                "chat.preview.current_position",
                arg("x", x), arg("y", y), arg("z", z))
            val hintLayerCommand = translatable("chat.preview.hint_layer_command")
            fun progress(correct: Int, total: Int, completed: String) = translatable(
                "chat.preview.progress",
                arg("correct", correct), arg("total", total), arg("completed", completed))
            fun completed(structureId: String) = translatable(
                "chat.preview.completed", arg("structure_id", structureId))
            val errControllerBroken = translatable("chat.preview.err_controller_broken")
            fun notFound(structureId: String) = translatable(
                "chat.preview.err_not_found", arg("structure_id", structureId))
            val hintStartPreview = translatable("chat.preview.hint_start_preview")
            val willExpire = translatable("chat.preview.will_expire")
            val cancelled = translatable("chat.preview.cancelled")
            val errCreateFailed = translatable("chat.preview.err_create_failed")

            val summaryTitle = translatable("chat.preview.summary_title")
            fun summaryBlueprint(id: String) = translatable(
                "chat.preview.summary_blueprint", arg("blueprint_id", id))
            fun summaryFacing(facing: String) = translatable(
                "chat.preview.summary_facing", arg("facing", facing))
            fun summaryRange(w: Int, h: Int, d: Int) = translatable(
                "chat.preview.summary_range",
                arg("width", w), arg("height", h), arg("depth", d))
            fun summaryPosition(min: String, max: String) = translatable(
                "chat.preview.summary_position",
                arg("min", min), arg("max", max))
            val summaryValid = translatable("chat.preview.summary_valid")
            fun summaryErrors(count: Int) = translatable(
                "chat.preview.summary_errors", arg("count", count))
            fun summaryErrorItem(msg: String) = translatable(
                "chat.preview.summary_error_item", arg("message", msg))
            val summaryWarning = translatable("chat.preview.summary_warning")
            fun summaryWarningItem(msg: String) = translatable(
                "chat.preview.summary_warning_item", arg("message", msg))
            val summaryConfirm = translatable("chat.preview.summary_confirm")
            val summaryExpire = translatable("chat.preview.summary_expire")
            val summaryFooter = translatable("chat.preview.summary_footer")
            val timeoutCancelled = translatable("chat.preview.timeout_cancelled")
            fun timeoutCountdown(countdown: Int) = translatable(
                "chat.preview.timeout_countdown", arg("countdown", countdown))

            fun errEmptyStage(id: String) = translatable(
                "chat.preview.err_empty_stage", arg("id", id))
            val buildFinished = translatable("chat.preview.build_finished")
            val controllerBrokenCancel = translatable("chat.preview.controller_broken_cancel")
            val timeoutAutoCancel = translatable("chat.preview.timeout_auto_cancel")
            fun structureCompleted(id: String) = translatable(
                "chat.preview.structure_completed", arg("id", id))
            val structureCtrlBroken = translatable("chat.preview.structure_ctrl_broken")
        }

        object Builder {
            val errWorldUnloaded = translatable("chat.builder.err_world_unloaded")
            val errConflictingBlocks = translatable("chat.builder.err_conflicting_blocks")
            fun conflictPositions(positions: String) = translatable(
                "chat.builder.conflict_positions", arg("positions", positions))
            fun moreConflicts(count: Int) = translatable(
                "chat.builder.more_conflicts", arg("count", count))
            val errInsufficientMaterials = translatable("chat.builder.err_insufficient_materials")
            fun missingMaterial(material: String, count: Int) = translatable(
                "chat.builder.missing_material",
                arg("material", material), arg("count", count))
            val errWorldUnloadedCancelled = translatable("chat.builder.err_world_unloaded_cancelled")
            fun progress(placed: Int, total: Int, progress: String) = translatable(
                "chat.builder.progress",
                arg("placed", placed), arg("total", total), arg("progress", progress))
            fun startBuild(blueprintId: String) = translatable(
                "chat.builder.start_build", arg("blueprint_id", blueprintId))
            fun buildInfo(count: Int) = translatable(
                "chat.builder.build_info", arg("count", count))
            val cancelled = translatable("chat.builder.cancelled")
            val complete = translatable("chat.builder.complete")
            fun structureInfo(blueprintId: String) = translatable(
                "chat.builder.structure_info", arg("blueprint_id", blueprintId))
        }

        object Structure {
            fun formed(structureId: String) = translatable(
                "chat.structure.formed", arg("structure_id", structureId))
            fun broken(structureId: String) = translatable(
                "chat.structure.broken", arg("structure_id", structureId))
            fun notFound(structureId: String) = translatable(
                "chat.structure.err_not_found", arg("structure_id", structureId))
            val warnNoBlocks = translatable("chat.structure.warn_no_blocks")
            val hintAddBlocks = translatable("chat.structure.hint_add_blocks")
            fun controllerDetected(key: String) = translatable(
                "chat.structure.controller_detected", arg("key", key))
            fun associatedStructure(structureId: String) = translatable(
                "chat.structure.associated_structure", arg("structure_id", structureId))
            fun blockCount(count: Int) = translatable(
                "chat.structure.block_count", arg("count", count))
            fun slots(slots: String) = translatable(
                "chat.structure.slots", arg("slots", slots))
            fun customData(count: Int) = translatable(
                "chat.structure.custom_data", arg("count", count))
        }

        object BlueprintTable {
            val craftSuccess = translatable("chat.blueprint_table.craft_success")
            val guiTitle = translatable("chat.blueprint_table.gui_title")
        }

        object Test {
            val controllerDetected = translatable("chat.test.controller_detected")
            val furnaceDetected = translatable("chat.test.furnace_detected")
            val machineDetected = translatable("chat.test.machine_detected")
            val rebarRequired = translatable("chat.test.rebar_required")
            val customDetected = translatable("chat.test.custom_detected")
            val customHint = translatable("chat.test.custom_hint")
            fun previewStarted(structureId: String) = translatable(
                "chat.test.preview_started", arg("structure_id", structureId))
        }

        object Rebar {
            val integrationEnabled = translatable("chat.rebar.integration_enabled")
            val integrationDisabled = translatable("chat.rebar.integration_disabled")
            fun integrationFailed(error: String) = translatable(
                "chat.rebar.err_integration_failed", arg("error", error))
        }

        object Io {
            fun importedBlueprint(name: String, count: Int) = translatable(
                "chat.io.imported",
                arg("name", name), arg("count", count))
            fun rebuiltProduct(name: String) = translatable(
                "chat.io.rebuilt", arg("name", name))
            fun loadedStandalone(name: String) = translatable(
                "chat.io.loaded_standalone", arg("name", name))
            fun loadFailed(file: String, error: String) = translatable(
                "chat.io.err_load_failed",
                arg("file", file), arg("error", error))
            fun saveFailed(file: String, error: String) = translatable(
                "chat.io.err_save_failed",
                arg("file", file), arg("error", error))
        }

        object Init {
            val starting = translatable("chat.init.starting")
            fun complete(version: String) = translatable(
                "chat.init.complete", arg("version", version))
            val shuttingDown = translatable("chat.init.shutting_down")
            val shutdownComplete = translatable("chat.init.shutdown_complete")
            fun registeringStructure(id: String, size: String, count: Int) = translatable(
                "chat.init.registering",
                arg("id", id), arg("size", size), arg("count", count))
            fun loadedStructures(count: Int) = translatable(
                "chat.init.loaded_total", arg("count", count))
            val testModuleInit = translatable("chat.init.test_module_init")
            val testModuleComplete = translatable("chat.init.test_module_complete")
            fun testStructuresRegistered(count: Int) = translatable(
                "chat.init.test_structures", arg("count", count))
            fun testBlocksRegistered(count: Int) = translatable(
                "chat.init.test_blocks", arg("count", count))
            fun restoredSites(count: Int) = translatable(
                "chat.init.restored_sites", arg("count", count))
        }
    }
}
