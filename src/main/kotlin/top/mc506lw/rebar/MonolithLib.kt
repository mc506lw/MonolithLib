package top.mc506lw.rebar

import io.github.pylonmc.rebar.addon.RebarAddon
import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.api.BlueprintAPI
import top.mc506lw.monolith.common.Constants
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.lifecycle.ChunkHandler
import top.mc506lw.monolith.core.io.IOModule
import top.mc506lw.monolith.feature.material.MaterialModule
import top.mc506lw.monolith.feature.preview.PreviewModule
import top.mc506lw.monolith.feature.preview.StructurePreviewManager
import top.mc506lw.monolith.feature.builder.StructureBuildManager
import top.mc506lw.monolith.feature.rebar.RebarModule
import top.mc506lw.monolith.feature.buildsite.BuildSiteManager
import top.mc506lw.monolith.feature.buildsite.BuildSiteListener
import top.mc506lw.monolith.internal.listener.MonolithBlockListener
import top.mc506lw.monolith.internal.listener.RebarControllerListener
import top.mc506lw.monolith.internal.scheduler.TickScheduler
import top.mc506lw.monolith.test.TestModule
import top.mc506lw.monolith.feature.machine.BlueprintTableMachine
import java.io.File
import java.util.Locale

class MonolithLib : JavaPlugin(), RebarAddon {

    companion object {
        @JvmStatic
        lateinit var instance: MonolithLib
            private set
        
        private const val LOG_PREFIX = "[MonolithLib]"
    }
    
    override val javaPlugin: JavaPlugin get() = this
    override val languages: Set<Locale> = setOf(Locale.ENGLISH, Locale.CHINESE)
    override val material: Material = Material.STRUCTURE_BLOCK
    
    private lateinit var api: BlueprintAPI
    private lateinit var scheduler: TickScheduler
    private lateinit var ioModule: IOModule
    private lateinit var previewModule: PreviewModule
    private lateinit var materialModule: MaterialModule
    private lateinit var rebarModule: RebarModule
    private lateinit var buildSiteListener: BuildSiteListener
    private lateinit var blockListener: MonolithBlockListener
    private lateinit var chunkHandler: ChunkHandler
    
    val importsDirectory: File
        get() = ioModule.importsDirectory
    
    val blueprintsDirectory: File
        get() = ioModule.blueprintsDirectory
    
    val productsDirectory: File
        get() = ioModule.productsDirectory

    override fun onEnable() {
        instance = this
        
        log("正在初始化...")
        
        registerWithRebar()
        
        initializeCore()
        initializeModules()
        loadStructures()
        initBuildSiteSystem()
        registerListeners()
        registerCommands()
        initTestModule()
        initMachines()
        
        log("初始化完成! 版本: ${pluginMeta.version}")
    }

    override fun onDisable() {
        log("正在关闭...")
        
        scheduler.shutdown()
        previewModule.onDisable()
        materialModule.clearCache()
        StructurePreviewManager.cleanup()
        StructureBuildManager.cleanup()
        BuildSiteManager.stopTracking()
        BuildSiteManager.cleanup()
        top.mc506lw.monolith.feature.buildsite.EasyBuildManager.cleanup()
        top.mc506lw.monolith.feature.buildsite.PrinterManager.cleanup()
        top.mc506lw.monolith.feature.buildsite.LitematicaModeManager.cleanup()
        
        log("已关闭")
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!command.name.equals("monolith", ignoreCase = true)) return false
        
        when {
            args.isEmpty() -> sendHelp(sender)
            args[0].equals("reload", ignoreCase = true) -> handleReload(sender)
            args[0].equals("list", ignoreCase = true) -> handleList(sender)
            args[0].equals("info", ignoreCase = true) -> handleInfo(sender, args)
            args[0].equals("preview", ignoreCase = true) -> handlePreview(sender, args)
            args[0].equals("build", ignoreCase = true) -> handleBuild(sender, args)
            args[0].equals("blueprint", ignoreCase = true) -> handleBlueprint(sender, args)
            args[0].equals("litematica", ignoreCase = true) -> handleLitematica(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }

    private fun initializeCore() {
        scheduler = TickScheduler(this)
        api = BlueprintAPI()
        MonolithAPI.setInstance(api)
        
        blockListener = MonolithBlockListener.getInstance()
        chunkHandler = ChunkHandler()
    }

    private fun initializeModules() {
        ioModule = IOModule(dataFolder)
        previewModule = PreviewModule(this)
        materialModule = MaterialModule(this)
        rebarModule = RebarModule(this)
    }

    private fun initBuildSiteSystem() {
        buildSiteListener = BuildSiteListener()
        BuildSiteManager.init(this)
        
        val siteCount = BuildSiteManager.getAllActiveSites().size
        if (siteCount > 0) {
            log("恢复 $siteCount 个存档工地")
            top.mc506lw.monolith.feature.buildsite.EasyBuildManager.rebuildIndex()
        }
        
        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                top.mc506lw.monolith.feature.buildsite.LitematicaModeManager.onPlayerTick(player)
            }
        }, 20L, 20L)
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(blockListener, this)
        server.pluginManager.registerEvents(chunkHandler, this)
        server.pluginManager.registerEvents(RebarControllerListener, this)
        server.pluginManager.registerEvents(buildSiteListener, this)
        server.pluginManager.registerEvents(top.mc506lw.monolith.feature.buildsite.EasyBuildManager, this)
    }

    private fun registerCommands() {
        getCommand("monolith")?.setExecutor(this)
        getCommand("monolith")?.tabCompleter = top.mc506lw.monolith.internal.command.MonolithTabCompleter
        getCommand("mltest")?.setExecutor(TestModule)
    }
    
    private fun initTestModule() {
        try {
            TestModule.init()
        } catch (e: Exception) {
            logWarning("测试模块初始化失败: ${e.message}")
        }
    }

    private fun initMachines() {
        try {
            BlueprintTableMachine.registerAll()
        } catch (e: Exception) {
            logWarning("机器模块初始化失败: ${e.message}")
        }
    }

    private fun loadStructures() {
        val blueprints = ioModule.loadAllBlueprints()
        
        blueprints.forEach { blueprint ->
            api.registry.registerBlueprint(blueprint)
            log("注册蓝图: ${blueprint.id} (${blueprint.sizeX}x${blueprint.sizeY}x${blueprint.sizeZ}, ${blueprint.blockCount} 非空方块)")
        }
        
        log("共加载 ${blueprints.size} 个蓝图")
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(Constants.Permissions.RELOAD)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        sender.sendMessage(I18n.Message.Command.Reload.starting)
        
        scheduler.cancelAllTasks()
        api.registry.clear()
        
        val blueprints = ioModule.loadAllBlueprints()
        blueprints.forEach { blueprint ->
            api.registry.registerBlueprint(blueprint)
        }
        
        sender.sendMessage(I18n.Message.Command.Reload.complete(blueprints.size))
    }
    
    private fun getControllerFromTarget(player: Player): Pair<Block, Blueprint>? {
        val targetBlock = player.getTargetBlockExact(8)
        if (targetBlock == null || targetBlock.type.isAir) {
            return null
        }
        
        val rebarBlock = BlockStorage.get(targetBlock)
        if (rebarBlock == null) {
            return null
        }
        
        val blueprints = api.registry.getAllBlueprints().values.filter { 
            it.controllerRebarKey == rebarBlock.key 
        }
        if (blueprints.isEmpty()) {
            return null
        }
        
        return Pair(targetBlock, blueprints.first())
    }

    private fun handlePreview(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (!sender.hasPermission(Constants.Permissions.PREVIEW)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        if (args.size >= 2 && args[1].equals("cancel", ignoreCase = true)) {
            StructurePreviewManager.cancelPreview(sender)
            sender.sendMessage(I18n.Message.Preview.previewCancelled)
            return
        }
        
        val controllerInfo = getControllerFromTarget(sender)
        if (controllerInfo == null) {
            sender.sendMessage(I18n.Message.Command.pointAtController)
            sender.sendMessage(I18n.Message.Command.controllerMustBeRebar)
            return
        }
        
        val (targetBlock, blueprint) = controllerInfo
        
        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)
        
        val session = StructurePreviewManager.startPreview(
            player = sender,
            blueprint = blueprint,
            controllerLocation = targetBlock.location,
            facing = facing
        )
        
        if (session != null) {
            sender.sendMessage(I18n.Message.Preview.started(blueprint.id, facing.name))
            sender.sendMessage(I18n.Message.Preview.previewWillExpire)
        } else {
            sender.sendMessage(I18n.Message.Preview.previewFailed)
        }
    }
    
    private fun handleBuild(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (!sender.hasPermission(Constants.Permissions.BUILD)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        if (args.size >= 2 && args[1].equals("cancel", ignoreCase = true)) {
            StructureBuildManager.cancelBuild(sender)
            sender.sendMessage(I18n.Message.Command.buildCancelled)
            return
        }
        
        val controllerInfo = getControllerFromTarget(sender)
        if (controllerInfo == null) {
            sender.sendMessage(I18n.Message.Command.pointAtController)
            sender.sendMessage(I18n.Message.Command.controllerMustBeRebar)
            return
        }
        
        val (targetBlock, blueprint) = controllerInfo
        
        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)
        
        val builder = StructureBuildManager.startBuild(
            player = sender,
            blueprint = blueprint,
            controllerLocation = targetBlock.location,
            facing = facing
        )
        
        if (builder == null) {
            sender.sendMessage(I18n.Message.Command.buildFailed)
        }
    }

    private fun handleBlueprint(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.BLUEPRINT)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.Blueprint.noId)
            sender.sendMessage(I18n.Message.Command.Blueprint.hint)
            return
        }

        val blueprintId = args[1]
        val blueprint = api.registry.getBlueprint(blueprintId)

        if (blueprint == null) {
            sender.sendMessage(I18n.Message.Command.Blueprint.notFound(blueprintId))
            return
        }

        val item = top.mc506lw.monolith.feature.buildsite.BlueprintItem.create(blueprintId)

        val leftover = sender.inventory.addItem(item)
        if (leftover.isNotEmpty()) {
            sender.sendMessage(I18n.Message.Command.Blueprint.inventoryFull)
            sender.world.dropItemNaturally(sender.location, item)
        }

        sender.sendMessage(I18n.Message.Command.Blueprint.given(blueprintId))
        sender.sendMessage("\u00a77右键空气方块即可创建工地")
    }

    private fun handleList(sender: CommandSender) {
        val blueprints = api.registry.getAllBlueprints()
        
        if (blueprints.isEmpty()) {
            sender.sendMessage(I18n.Message.Command.List.empty)
            sender.sendMessage(I18n.Message.Command.List.hint(importsDirectory.absolutePath))
            sender.sendMessage(I18n.Message.Command.List.formats)
            return
        }
        
        sender.sendMessage(I18n.Message.Command.List.title(blueprints.size))
        blueprints.forEach { (id, blueprint) ->
            val rebarInfo = if (blueprint.controllerRebarKey != null) " §b[Rebar]" else ""
            sender.sendMessage("  §7- §f$id §7(${blueprint.sizeX}x${blueprint.sizeY}x${blueprint.sizeZ}, ${blueprint.blockCount} 方块)$rebarInfo")
        }
    }
    
    private fun handleFiles(sender: CommandSender) {
        sender.sendMessage(I18n.Message.Command.Files.title)
        sender.sendMessage(I18n.Message.Command.Files.imports)
        sender.sendMessage(I18n.Message.Command.Files.blueprints)
        sender.sendMessage(I18n.Message.Command.Files.products)
        
        val importFiles = importsDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
        val blueprintDirs = blueprintsDirectory.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val productFiles = productsDirectory.listFiles()?.filter { it.isFile && it.extension == "mnb" } ?: emptyList()
        
        if (importFiles.isNotEmpty()) {
            sender.sendMessage(I18n.Message.Command.Files.importsTitle(importFiles.size))
            importFiles.take(5).forEach { file ->
                sender.sendMessage("  §7- §f${file.name}")
            }
            if (importFiles.size > 5) {
                sender.sendMessage(I18n.Message.Command.Files.moreFiles(importFiles.size - 5))
            }
        }
        
        if (blueprintDirs.isNotEmpty()) {
            sender.sendMessage(I18n.Message.Command.Files.blueprintsTitle(blueprintDirs.size))
            blueprintDirs.take(10).forEach { dir ->
                val hasMnb = File(dir, "${dir.name}.mnb").exists()
                val hasConfig = File(dir, "${dir.name}.yml").exists()
                val status = when {
                    hasMnb && hasConfig -> I18n.Message.Command.Files.statusReady
                    hasMnb -> I18n.Message.Command.Files.statusPending
                    else -> I18n.Message.Command.Files.statusMissing
                }
                sender.sendMessage("  §7- §f${dir.name} $status")
            }
        }
        
        if (productFiles.isNotEmpty()) {
            sender.sendMessage(I18n.Message.Command.Files.productsTitle(productFiles.size))
            productFiles.take(10).forEach { file ->
                sender.sendMessage("  §7- §f${file.nameWithoutExtension}")
            }
        }
        
        if (importFiles.isEmpty() && blueprintDirs.isEmpty() && productFiles.isEmpty()) {
            sender.sendMessage(I18n.Message.Command.Files.noFiles)
            sender.sendMessage(I18n.Message.Command.Files.noFilesHint)
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size >= 2) {
            val blueprintId = args[1]
            val blueprint = api.registry.getBlueprint(blueprintId)
            if (blueprint == null) {
                sender.sendMessage(I18n.Message.Command.Info.blueprintNotFound(blueprintId))
                return
            }
            sender.sendMessage(I18n.Message.Command.Info.blueprintTitle(blueprint.id))
            sender.sendMessage(I18n.Message.Command.Info.size(blueprint.sizeX, blueprint.sizeY, blueprint.sizeZ))
            sender.sendMessage(I18n.Message.Command.Info.blockCount(blueprint.blockCount))
            if (blueprint.meta.displayName != null) {
                sender.sendMessage(I18n.Message.Command.Info.name(blueprint.meta.displayName!!))
            }
            if (blueprint.meta.description != null) {
                sender.sendMessage(I18n.Message.Command.Info.description(blueprint.meta.description!!))
            }
            return
        }
        
        val rebarStatus = if (rebarModule.isAvailable()) 
            I18n.Message.Command.Info.rebarEnabled 
        else 
            I18n.Message.Command.Info.rebarDisabled
        
        sender.sendMessage(I18n.Message.Command.Info.title)
        sender.sendMessage(I18n.Message.Command.Info.version(Constants.PLUGIN_VERSION))
        sender.sendMessage(I18n.Message.Command.Info.registered(api.registry.size))
        sender.sendMessage(I18n.Message.Command.Info.importDir(importsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.blueprintDir(blueprintsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.productDir(productsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.formats)
        sender.sendMessage(I18n.Message.Command.Info.rebarIntegration(rebarStatus))
    }

    private fun handleLitematica(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Litematica.usage)
            return
        }
        
        when {
            args[1].equals("easybuild", ignoreCase = true) -> {
                if (!sender.hasPermission("monolith.easybuild")) {
                    sender.sendMessage(I18n.Message.Command.permissionDenied)
                    return
                }
                
                val result = top.mc506lw.monolith.feature.buildsite.EasyBuildManager.toggle(sender)
                
                when (result) {
                    true -> {
                        sender.sendMessage(I18n.Message.Litematica.easybuildEnabled)
                        sender.sendMessage("§7对着幽灵方块右键可自动放置匹配的方块")
                        sender.sendMessage("§7创造模式下无需任何材料")
                        sender.sendMessage("§7离开工地范围1分钟后自动关闭")
                    }
                    false -> {
                        sender.sendMessage(I18n.Message.Litematica.easybuildDisabled)
                    }
                    null -> {
                        sender.sendMessage(I18n.Message.Litematica.noSiteNearby)
                    }
                }
            }
            
            args[1].equals("printer", ignoreCase = true) -> {
                if (!sender.hasPermission("monolith.printer")) {
                    sender.sendMessage(I18n.Message.Command.permissionDenied)
                    return
                }
                
                val result = top.mc506lw.monolith.feature.buildsite.PrinterManager.toggle(sender)
                
                when (result) {
                    true -> {
                        top.mc506lw.monolith.feature.buildsite.PrinterManager.startPrinter(sender)
                        sender.sendMessage(I18n.Message.Litematica.printerEnabled)
                        sender.sendMessage("§7自动放置周身4格范围内的幽灵方块")
                        sender.sendMessage("§7创造模式下无需任何材料")
                        sender.sendMessage("§7离开工地范围1分钟后自动关闭")
                    }
                    false -> {
                        top.mc506lw.monolith.feature.buildsite.PrinterManager.stopPrinter(sender)
                        sender.sendMessage(I18n.Message.Litematica.printerDisabled)
                    }
                    null -> {
                        sender.sendMessage(I18n.Message.Litematica.printerNoSite)
                    }
                }
            }
            
            else -> {
                sender.sendMessage(I18n.Message.Litematica.usage)
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(I18n.Message.Command.Help.title)
        sender.sendMessage(I18n.Message.Command.Help.reload)
        sender.sendMessage(I18n.Message.Command.Help.list)
        sender.sendMessage(I18n.Message.Command.Help.info)
        sender.sendMessage(I18n.Message.Command.Help.preview)
        sender.sendMessage(I18n.Message.Command.Help.build)
        sender.sendMessage(I18n.Message.Command.Help.blueprint)
        sender.sendMessage(I18n.Message.Command.Help.usage)
        sender.sendMessage(I18n.Message.Command.Help.step1)
        sender.sendMessage(I18n.Message.Command.Help.step2)
        sender.sendMessage(I18n.Message.Command.Help.step3)
    }

    internal fun log(message: String) {
        logger.info("$LOG_PREFIX $message")
    }

    internal fun logWarning(message: String) {
        logger.warning("$LOG_PREFIX $message")
    }

    internal fun logError(message: String) {
        logger.severe("$LOG_PREFIX $message")
    }
}
