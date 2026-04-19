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
import top.mc506lw.monolith.internal.selection.SelectionManager
import top.mc506lw.monolith.internal.selection.SelectionWand
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
            args[0].equals("wand", ignoreCase = true) -> handleWand(sender)
            args[0].equals("save", ignoreCase = true) -> handleSave(sender, args)
            args[0].equals("merge", ignoreCase = true) -> handleMerge(sender, args)
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
    }

    private fun initTestModule() {
        try {
            TestModule.init()
        } catch (e: Exception) {
            logWarning("示例模块初始化失败: ${e.message}")
        }
    }

    private fun initMachines() {
        try {
            BlueprintTableMachine.registerAll()
        } catch (e: Exception) {
            logWarning("机器模块初始化失败: ${e.message}")
        }
        
        try {
            io.github.pylonmc.rebar.item.RebarItem.register(SelectionWand::class.java, SelectionWand.STACK, SelectionWand.KEY)
        } catch (e: Exception) {
            logWarning("选区魔杖初始化失败: ${e.message}")
        }
    }

    private fun loadStructures() {
        val blueprints = ioModule.loadAllBlueprints()
        
        blueprints.forEach { blueprint ->
            api.registry.register(blueprint)
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
            api.registry.register(blueprint)
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
        
        val blueprints = api.registry.getAll().values.filter { 
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
        val blueprint = api.registry.get(blueprintId)

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
        val blueprints = api.registry.getAll()
        
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
            val blueprint = api.registry.get(blueprintId)
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

    private fun handleWand(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        val leftover = sender.inventory.addItem(SelectionWand.STACK)
        if (leftover.isNotEmpty()) {
            sender.sendMessage("\u00a7c[MonolithLib] 背包已满，物品已掉落")
            sender.world.dropItemNaturally(sender.location, SelectionWand.STACK)
        } else {
            sender.sendMessage("\u00a7a[MonolithLib] 已给予选区魔杖")
        }
        sender.sendMessage("\u00a77左键方块设置 Pos1，右键方块设置 Pos2")
    }
    
    private fun handleSave(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("\u00a7c[MonolithLib] 用法: /ml save <文件名> [--scaffold|--assembled]")
            sender.sendMessage("\u00a77[MonolithLib] --scaffold  保存为脚手架阶段 (建造前)")
            sender.sendMessage("\u00a77[MonolithLib] --assembled  保存为组装阶段 (建造后，默认)")
            sender.sendMessage("\u00a77[MonolithLib] 不加标志: 保存为单阶段蓝图")
            return
        }
        
        val selection = SelectionManager.getSelection(sender)
        if (!selection.isComplete) {
            sender.sendMessage("\u00a7c[MonolithLib] 选区不完整！请先设置 Pos1 和 Pos2")
            return
        }
        
        val fileName = args[1]
        if (!fileName.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            sender.sendMessage("\u00a7c[MonolithLib] 文件名只能包含字母、数字、下划线和连字符")
            return
        }
        
        val flags = args.drop(2).map { it.lowercase() }
        val isScaffold = flags.contains("--scaffold")
        val isAssembled = flags.contains("--assembled")
        val isStaged = isScaffold || isAssembled
        
        val world = Bukkit.getWorld(selection.worldName!!)
        if (world == null) {
            sender.sendMessage("\u00a7c[MonolithLib] 世界不存在: ${selection.worldName}")
            return
        }
        
        val minPos = selection.getMinPos()!!
        val maxPos = selection.getMaxPos()!!
        
        val blocks = mutableListOf<top.mc506lw.monolith.core.model.BlockEntry>()
        var rebarBlockCount = 0
        
        for (x in minPos.x..maxPos.x) {
            for (y in minPos.y..maxPos.y) {
                for (z in minPos.z..maxPos.z) {
                    val block = world.getBlockAt(x, y, z)
                    if (!block.type.isAir) {
                        val position = top.mc506lw.monolith.core.math.Vector3i(x - minPos.x, y - minPos.y, z - minPos.z)
                        val blockData = block.blockData.clone()
                        
                        val rebarPredicate = top.mc506lw.monolith.feature.rebar.RebarAdapter.createRebarPredicate(block)
                        if (rebarPredicate != null) {
                            rebarBlockCount++
                        }
                        
                        blocks.add(top.mc506lw.monolith.core.model.BlockEntry(
                            position = position,
                            blockData = blockData,
                            predicate = rebarPredicate
                        ))
                    }
                }
            }
        }
        
        if (blocks.isEmpty()) {
            sender.sendMessage("\u00a7c[MonolithLib] 选区内没有非空方块!")
            return
        }
        
        if (rebarBlockCount > 0) {
            sender.sendMessage("\u00a77[MonolithLib] 检测到 $rebarBlockCount 个 Rebar 方块")
        }
        
        val displayEntities = mutableListOf<top.mc506lw.monolith.core.model.DisplayEntityData>()
        val centerLoc = org.bukkit.Location(world, minPos.x.toDouble(), minPos.y.toDouble(), minPos.z.toDouble())
        val searchRadiusX = (maxPos.x - minPos.x + 10).toDouble()
        val searchRadiusY = (maxPos.y - minPos.y + 10).toDouble()
        val searchRadiusZ = (maxPos.z - minPos.z + 10).toDouble()
        
        for (entity in world.getNearbyEntities(centerLoc, searchRadiusX, searchRadiusY, searchRadiusZ)) {
            if (entity !is org.bukkit.entity.Display) continue
            
            val relX = entity.location.blockX - minPos.x
            val relY = entity.location.blockY - minPos.y
            val relZ = entity.location.blockZ - minPos.z
            
            if (relX < 0 || relY < 0 || relZ < 0) continue
            if (relX > maxPos.x - minPos.x + 5 || relY > maxPos.y - minPos.y + 5 || relZ > maxPos.z - minPos.z + 5) continue
            
            val transformation = entity.transformation
            val displayData = top.mc506lw.monolith.core.model.DisplayEntityData(
                position = top.mc506lw.monolith.core.math.Vector3i(relX, relY, relZ),
                entityType = when (entity) {
                    is org.bukkit.entity.BlockDisplay -> top.mc506lw.monolith.core.model.DisplayType.BLOCK
                    is org.bukkit.entity.ItemDisplay -> top.mc506lw.monolith.core.model.DisplayType.ITEM
                    else -> continue
                },
                rotation = org.joml.Quaternionf(transformation.leftRotation),
                scale = transformation.scale,
                translation = transformation.translation,
                blockData = if (entity is org.bukkit.entity.BlockDisplay) entity.block else null,
                itemStack = if (entity is org.bukkit.entity.ItemDisplay) entity.itemStack else null
            )
            displayEntities.add(displayData)
        }
        
        val shape = top.mc506lw.monolith.core.model.Shape(blocks)
        val importsDir = File(dataFolder, "imports")
        importsDir.mkdirs()
        
        if (isStaged) {
            val stageSuffix = if (isScaffold) ".scaffold" else ".assembled"
            val stageName = if (isScaffold) "脚手架(建造前)" else "组装(建造后)"
            
            val blueprint = top.mc506lw.monolith.core.model.Blueprint.fromSingleShape(
                id = fileName,
                shape = shape,
                meta = top.mc506lw.monolith.core.model.BlueprintMeta(
                    displayName = fileName,
                    description = "Raw recording by ${sender.name} [$stageName]",
                    controllerOffset = top.mc506lw.monolith.core.math.Vector3i.ZERO
                ),
                displayEntities = displayEntities.takeIf { it.isNotEmpty() }
            )
            
            val outputFile = File(importsDir, "$fileName$stageSuffix.raw.mnb")
            try {
                top.mc506lw.monolith.core.io.formats.BinaryFormat.save(blueprint, outputFile, 2, "")
                sender.sendMessage("\u00a7a[MonolithLib] 已保存 $stageName 阶段: $fileName$stageSuffix.raw.mnb")
                sender.sendMessage("\u00a77[MonolithLib] 方块数: ${blocks.size} | 显示实体: ${displayEntities.size}")
                sender.sendMessage("\u00a77[MonolithLib] 尺寸: ${maxPos.x - minPos.x + 1}x${maxPos.y - minPos.y + 1}x${maxPos.z - minPos.z + 1}")
                sender.sendMessage("\u00a77[MonolithLib] 文件位置: imports/$fileName$stageSuffix.raw.mnb")
                
                if (isScaffold) {
                    val assembledFile = File(importsDir, "$fileName.assembled.raw.mnb")
                    if (!assembledFile.exists()) {
                        sender.sendMessage("\u00a7e[MonolithLib] 提示: 还需要保存组装阶段!")
                        sender.sendMessage("\u00a7e[MonolithLib] 请搭建最终结构后执行: /ml save $fileName --assembled")
                    } else {
                        sender.sendMessage("\u00a7a[MonolithLib] 组装阶段已存在，可使用 /ml merge $fileName 合并")
                    }
                } else {
                    val scaffoldFile = File(importsDir, "$fileName.scaffold.raw.mnb")
                    if (!scaffoldFile.exists()) {
                        sender.sendMessage("\u00a7e[MonolithLib] 提示: 还需要保存脚手架阶段!")
                        sender.sendMessage("\u00a7e[MonolithLib] 请搭建建造前的结构后执行: /ml save $fileName --scaffold")
                    } else {
                        sender.sendMessage("\u00a7a[MonolithLib] 脚手架阶段已存在，可使用 /ml merge $fileName 合并")
                    }
                }
            } catch (e: Exception) {
                sender.sendMessage("\u00a7c[MonolithLib] 保存失败: ${e.message}")
                logWarning("保存蓝图失败: ${e.message}")
            }
        } else {
            val blueprint = top.mc506lw.monolith.core.model.Blueprint.fromSingleShape(
                id = fileName,
                shape = shape,
                meta = top.mc506lw.monolith.core.model.BlueprintMeta(
                    displayName = fileName,
                    description = "Raw recording by ${sender.name}",
                    controllerOffset = top.mc506lw.monolith.core.math.Vector3i.ZERO
                ),
                displayEntities = displayEntities.takeIf { it.isNotEmpty() }
            )
            
            val outputFile = File(importsDir, "$fileName.raw.mnb")
            try {
                top.mc506lw.monolith.core.io.formats.BinaryFormat.save(blueprint, outputFile, 2, "")
                sender.sendMessage("\u00a7a[MonolithLib] 已保存: $fileName.raw.mnb")
                sender.sendMessage("\u00a77[MonolithLib] 方块数: ${blocks.size} | 显示实体: ${displayEntities.size}")
                sender.sendMessage("\u00a77[MonolithLib] 尺寸: ${maxPos.x - minPos.x + 1}x${maxPos.y - minPos.y + 1}x${maxPos.z - minPos.z + 1}")
                sender.sendMessage("\u00a77[MonolithLib] 文件位置: imports/$fileName.raw.mnb")
                
                SelectionManager.clearSelection(sender)
            } catch (e: Exception) {
                sender.sendMessage("\u00a7c[MonolithLib] 保存失败: ${e.message}")
                logWarning("保存蓝图失败: ${e.message}")
            }
        }
    }

    private fun handleMerge(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("\u00a7c[MonolithLib] 用法: /ml merge <文件名>")
            sender.sendMessage("\u00a77[MonolithLib] 合并分阶段录制的脚手架和组装蓝图")
            return
        }
        
        val fileName = args[1]
        val importsDir = File(dataFolder, "imports")
        
        val scaffoldFile = File(importsDir, "$fileName.scaffold.raw.mnb")
        val assembledFile = File(importsDir, "$fileName.assembled.raw.mnb")
        
        if (!scaffoldFile.exists()) {
            sender.sendMessage("\u00a7c[MonolithLib] 脚手架阶段文件不存在: $fileName.scaffold.raw.mnb")
            sender.sendMessage("\u00a7e[MonolithLib] 请先使用 /ml save $fileName --scaffold 保存脚手架阶段")
            return
        }
        
        if (!assembledFile.exists()) {
            sender.sendMessage("\u00a7c[MonolithLib] 组装阶段文件不存在: $fileName.assembled.raw.mnb")
            sender.sendMessage("\u00a7e[MonolithLib] 请先使用 /ml save $fileName --assembled 保存组装阶段")
            return
        }
        
        val scaffoldBlueprint = top.mc506lw.monolith.core.io.formats.BinaryFormat.load(scaffoldFile)
        if (scaffoldBlueprint == null) {
            sender.sendMessage("\u00a7c[MonolithLib] 无法加载脚手架阶段文件")
            return
        }
        
        val assembledBlueprint = top.mc506lw.monolith.core.io.formats.BinaryFormat.load(assembledFile)
        if (assembledBlueprint == null) {
            sender.sendMessage("\u00a7c[MonolithLib] 无法加载组装阶段文件")
            return
        }
        
        val scaffoldShape = scaffoldBlueprint.assembledShape
        val assembledShape = assembledBlueprint.assembledShape
        val displayEntities = assembledBlueprint.displayEntities
        
        val mergedBlueprint = top.mc506lw.monolith.core.model.Blueprint(
            id = fileName,
            stages = mapOf(
                top.mc506lw.monolith.core.model.BuildStage.SCAFFOLD to scaffoldShape,
                top.mc506lw.monolith.core.model.BuildStage.ASSEMBLED to assembledShape
            ),
            meta = assembledBlueprint.meta.copy(
                description = "Merged dual-stage blueprint by ${sender.name}"
            ),
            displayEntities = displayEntities,
            slots = assembledBlueprint.slots,
            customData = assembledBlueprint.customData,
            controllerRebarKey = assembledBlueprint.controllerRebarKey
        )
        
        val outputFile = File(importsDir, "$fileName.raw.mnb")
        try {
            top.mc506lw.monolith.core.io.formats.BinaryFormat.save(mergedBlueprint, outputFile, 2, "")
            sender.sendMessage("\u00a7a[MonolithLib] 已合并双阶段蓝图: $fileName.raw.mnb")
            sender.sendMessage("\u00a77[MonolithLib] 脚手架方块: ${scaffoldShape.blocks.size} | 组装方块: ${assembledShape.blocks.size}")
            sender.sendMessage("\u00a77[MonolithLib] 显示实体: ${displayEntities.size}")
            sender.sendMessage("\u00a77[MonolithLib] 文件位置: imports/$fileName.raw.mnb")
        } catch (e: Exception) {
            sender.sendMessage("\u00a7c[MonolithLib] 合并失败: ${e.message}")
            logWarning("合并蓝图失败: ${e.message}")
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
