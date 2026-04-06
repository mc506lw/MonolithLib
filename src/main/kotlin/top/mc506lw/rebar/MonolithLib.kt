package top.mc506lw.rebar

import io.github.pylonmc.rebar.addon.RebarAddon
import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.api.StructureAPI
import top.mc506lw.monolith.common.Constants
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.structure.StructureRegistry
import top.mc506lw.monolith.engine.lifecycle.ChunkHandler
import top.mc506lw.monolith.feature.io.IOModule
import top.mc506lw.monolith.feature.material.MaterialModule
import top.mc506lw.monolith.feature.preview.PreviewModule
import top.mc506lw.monolith.feature.preview.StructurePreviewManager
import top.mc506lw.monolith.feature.builder.StructureBuildManager
import top.mc506lw.monolith.feature.rebar.RebarModule
import top.mc506lw.monolith.internal.listener.MonolithBlockListener
import top.mc506lw.monolith.internal.listener.RebarControllerListener
import top.mc506lw.monolith.internal.scheduler.TickScheduler
import top.mc506lw.monolith.test.TestModule
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
    
    private lateinit var api: StructureAPI
    private lateinit var scheduler: TickScheduler
    private lateinit var ioModule: IOModule
    private lateinit var previewModule: PreviewModule
    private lateinit var materialModule: MaterialModule
    private lateinit var rebarModule: RebarModule
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
        registerListeners()
        registerCommands()
        initTestModule()
        
        log("初始化完成! 版本: ${pluginMeta.version}")
    }

    override fun onDisable() {
        log("正在关闭...")
        
        scheduler.shutdown()
        previewModule.onDisable()
        materialModule.clearCache()
        StructurePreviewManager.cleanup()
        StructureBuildManager.cleanup()
        
        log("已关闭")
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!command.name.equals("monolith", ignoreCase = true)) return false
        
        when {
            args.isEmpty() -> sendHelp(sender)
            args[0].equals("reload", ignoreCase = true) -> handleReload(sender)
            args[0].equals("list", ignoreCase = true) -> handleList(sender)
            args[0].equals("info", ignoreCase = true) -> handleInfo(sender)
            args[0].equals("files", ignoreCase = true) -> handleFiles(sender)
            args[0].equals("preview", ignoreCase = true) -> handlePreview(sender, args)
            args[0].equals("construct", ignoreCase = true) -> handleConstruct(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }

    private fun initializeCore() {
        scheduler = TickScheduler(this)
        api = StructureAPI()
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

    private fun registerListeners() {
        server.pluginManager.registerEvents(blockListener, this)
        server.pluginManager.registerEvents(chunkHandler, this)
        server.pluginManager.registerEvents(RebarControllerListener, this)
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

    private fun loadStructures() {
        val structures = ioModule.loadAllStructures()
        
        structures.forEach { structure ->
            api.registerStructure(structure)
            log("注册结构: ${structure.id} (${structure.sizeX}x${structure.sizeY}x${structure.sizeZ}, ${structure.flattenedBlocks.size} 非空方块)")
        }
        
        log("共加载 ${structures.size} 个结构")
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(Constants.Permissions.RELOAD)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        sender.sendMessage(I18n.Message.Command.Reload.starting)
        
        scheduler.cancelAllTasks()
        StructureRegistry.getInstance().clear()
        
        val structures = ioModule.loadAllStructures()
        structures.forEach { structure ->
            api.registerStructure(structure)
        }
        
        sender.sendMessage(I18n.Message.Command.Reload.complete(structures.size))
    }
    
    private fun getControllerFromTarget(player: Player): Pair<Block, top.mc506lw.monolith.core.structure.MonolithStructure>? {
        val targetBlock = player.getTargetBlockExact(8)
        if (targetBlock == null || targetBlock.type.isAir) {
            return null
        }
        
        val rebarBlock = BlockStorage.get(targetBlock)
        if (rebarBlock == null) {
            return null
        }
        
        val structures = StructureRegistry.getInstance().getByControllerKey(rebarBlock.key)
        if (structures.isEmpty()) {
            return null
        }
        
        return Pair(targetBlock, structures.first())
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
        
        val (targetBlock, structure) = controllerInfo
        
        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)
        
        val session = StructurePreviewManager.startPreview(
            player = sender,
            structure = structure,
            controllerLocation = targetBlock.location,
            facing = facing
        )
        
        if (session != null) {
            sender.sendMessage(I18n.Message.Preview.started(structure.id, facing.name))
            sender.sendMessage(I18n.Message.Preview.previewWillExpire)
        } else {
            sender.sendMessage(I18n.Message.Preview.previewFailed)
        }
    }
    
    private fun handleConstruct(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (!sender.hasPermission(Constants.Permissions.PREVIEW)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        if (args.size >= 2 && args[1].equals("cancel", ignoreCase = true)) {
            StructureBuildManager.cancelBuild(sender)
            return
        }
        
        val controllerInfo = getControllerFromTarget(sender)
        if (controllerInfo == null) {
            sender.sendMessage(I18n.Message.Command.pointAtController)
            sender.sendMessage(I18n.Message.Command.controllerMustBeRebar)
            return
        }
        
        val (targetBlock, structure) = controllerInfo
        
        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)
        
        val builder = StructureBuildManager.startBuild(
            player = sender,
            structure = structure,
            controllerLocation = targetBlock.location,
            facing = facing
        )
        
        if (builder == null) {
            sender.sendMessage(I18n.Message.Command.constructFailed)
        }
    }

    private fun handleList(sender: CommandSender) {
        val structures = StructureRegistry.getInstance().getAll()
        
        if (structures.isEmpty()) {
            sender.sendMessage(I18n.Message.Command.List.empty)
            sender.sendMessage(I18n.Message.Command.List.hint(importsDirectory.absolutePath))
            sender.sendMessage(I18n.Message.Command.List.formats)
            return
        }
        
        sender.sendMessage(I18n.Message.Command.List.title(structures.size))
        structures.forEach { (id, structure) ->
            val rebarInfo = if (structure.controllerRebarKey != null) " §b[Rebar]" else ""
            sender.sendMessage("  §7- §f$id §7(${structure.sizeX}x${structure.sizeY}x${structure.sizeZ}, ${structure.flattenedBlocks.size} 方块)$rebarInfo")
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

    private fun handleInfo(sender: CommandSender) {
        val rebarStatus = if (rebarModule.isAvailable()) 
            I18n.Message.Command.Info.rebarEnabled 
        else 
            I18n.Message.Command.Info.rebarDisabled
        
        sender.sendMessage(I18n.Message.Command.Info.title)
        sender.sendMessage(I18n.Message.Command.Info.version(Constants.PLUGIN_VERSION))
        sender.sendMessage(I18n.Message.Command.Info.registered(StructureRegistry.getInstance().size))
        sender.sendMessage(I18n.Message.Command.Info.importDir(importsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.blueprintDir(blueprintsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.productDir(productsDirectory.absolutePath))
        sender.sendMessage(I18n.Message.Command.Info.formats)
        sender.sendMessage(I18n.Message.Command.Info.rebarIntegration(rebarStatus))
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(I18n.Message.Command.Help.title)
        sender.sendMessage(I18n.Message.Command.Help.reload)
        sender.sendMessage(I18n.Message.Command.Help.list)
        sender.sendMessage(I18n.Message.Command.Help.files)
        sender.sendMessage(I18n.Message.Command.Help.preview)
        sender.sendMessage(I18n.Message.Command.Help.construct)
        sender.sendMessage(I18n.Message.Command.Help.info)
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
    
    private data class Pair<A, B>(val first: A, val second: B)
}
