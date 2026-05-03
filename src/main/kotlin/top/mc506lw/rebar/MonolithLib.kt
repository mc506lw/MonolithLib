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
import top.mc506lw.monolith.internal.command.MonolithTabCompleter
import top.mc506lw.monolith.test.TestModule
import top.mc506lw.monolith.feature.machine.BlueprintTableMachine
import top.mc506lw.monolith.feature.virtual.VirtualDisplayAnchorRegistry
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
        
        println("$LOG_PREFIX 正在初始化...")
        
        registerWithRebar()
        
        initializeCore()
        initializeModules()
        loadStructures()
        initBuildSiteSystem()
        registerListeners()
        registerCommands()
        initTestModule()
        initMachines()
        SelectionManager.init()
        
        println("$LOG_PREFIX 初始化完成! 版本: ${pluginMeta.version}")
    }

    override fun onDisable() {
        println("$LOG_PREFIX 正在关闭...")
        
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
        SelectionManager.shutdown()
        
        println("$LOG_PREFIX 已关闭")
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!command.name.equals("monolith", ignoreCase = true)) return false
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        return when (args[0].lowercase()) {
            "preview" -> { handlePreviewDomain(sender, args.drop(1)); true }
            "build" -> { handleBuildDomain(sender, args.drop(1)); true }
            "bp" -> { handleBlueprintDomain(sender, args.drop(1)); true }
            "site" -> { handleSiteDomain(sender, args.drop(1)); true }
            "edit" -> { handleEditDomain(sender, args.drop(1)); true }
            "reload" -> { handleReload(sender); true }
            else -> { sendHelp(sender); true }
        }
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
            println("$LOG_PREFIX 恢复 $siteCount 个存档工地")
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
        getCommand("monolith")?.tabCompleter = MonolithTabCompleter()
    }

    private fun initTestModule() {
        try {
            TestModule.init()
        } catch (e: Exception) {
            println("$LOG_PREFIX [WARN] 示例模块初始化失败: ${e.message}")
        }
    }

    private fun initMachines() {
        try {
            BlueprintTableMachine.registerAll()
            VirtualDisplayAnchorRegistry.register()
        } catch (e: Exception) {
            println("$LOG_PREFIX [WARN] 机器模块初始化失败: ${e.message}")
        }

        try {
            io.github.pylonmc.rebar.item.RebarItem.register(SelectionWand::class.java, SelectionWand.STACK, SelectionWand.KEY)
        } catch (e: Exception) {
            println("$LOG_PREFIX [WARN] 选区魔杖初始化失败: ${e.message}")
        }
    }

    private fun loadStructures() {
        val blueprints = ioModule.loadAllBlueprints()
        
        blueprints.forEach { blueprint ->
            api.registry.register(blueprint)
            println("$LOG_PREFIX 注册蓝图: ${blueprint.id} (${blueprint.sizeX}x${blueprint.sizeY}x${blueprint.sizeZ}, ${blueprint.blockCount} 非空方块)")
        }
        
        println("$LOG_PREFIX 共加载 ${blueprints.size} 个蓝图")
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage("§6§l[MonolithLib] §f命令帮助")
        sender.sendMessage("")
        sender.sendMessage("§7§m                  §r")
        sender.sendMessage("  §e/ml preview §7- 全貌预览 §c[OP]")
        sender.sendMessage("    §f<ID> [facing]           §7预览完整结构")
        sender.sendMessage("    §fstop                    §7停止预览")
        sender.sendMessage("")
        sender.sendMessage("  §e/ml build §7- 建造域")
        sender.sendMessage("    §fhere <ID> [facing]      §7一键建造")
        sender.sendMessage("    §feasy [on|off]           §7轻松放置模式")
        sender.sendMessage("    §fprinter [on|off]        §7自动打印模式")
        sender.sendMessage("")
        sender.sendMessage("  §e/ml bp §7- 蓝图管理域")
        sender.sendMessage("    §flist                    §7列出蓝图")
        sender.sendMessage("    §finfo <ID>               §7蓝图详情")
        sender.sendMessage("    §fgive <ID>               §7给予蓝图物品")
        sender.sendMessage("")
        sender.sendMessage("  §e/ml site §7- 工地域")
        sender.sendMessage("    §flist                    §7活跃工地列表")
        sender.sendMessage("    §finfo                    §7附近工地状态")
        sender.sendMessage("    §fcancel                  §7取消工地")
        sender.sendMessage("")
        sender.sendMessage("  §e/ml edit §7- 编辑器域")
        sender.sendMessage("    §fwand                    §7获取选区魔杖")
        sender.sendMessage("    §fsave <name> [--scaffold|--assembled]")
        sender.sendMessage("    §fmerge <a> <b>           §7合并结构")
        sender.sendMessage("")
        sender.sendMessage("  §e/ml reload §7- 重载所有结构 §c[管理员]")
        sender.sendMessage("§7§m                  §r")
        sender.sendMessage("")
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

    private fun handlePreviewDomain(sender: CommandSender, args: List<String>) {
        if (!sender.isOp) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        when {
            args.isEmpty() -> handlePreviewShow(sender, emptyList())
            args[0].equals("stop", ignoreCase = true) -> handlePreviewStop(sender)
            else -> handlePreviewShow(sender, args.toList())
        }
    }

    private fun handlePreviewShow(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        val blueprintId = args.getOrNull(0)
        if (blueprintId == null) {
            sender.sendMessage("§c用法: /ml preview <蓝图ID> [朝向]")
            sender.sendMessage("§7  stop                  停止预览")
            sender.sendMessage("§7使用 Tab 键自动补全蓝图名称")
            return
        }

        val blueprint = api.registry.get(blueprintId)
        if (blueprint == null) {
            sender.sendMessage("§c未找到蓝图: §f$blueprintId")
            sender.sendMessage("§7使用 /ml bp list 查看可用蓝图")
            return
        }

        val targetLocation = sender.location.clone().add(sender.location.direction.normalize())
        targetLocation.x = targetLocation.blockX.toDouble()
        targetLocation.y = targetLocation.blockY.toDouble()
        targetLocation.z = targetLocation.blockZ.toDouble()

        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)

        val session = StructurePreviewManager.startPreview(
            player = sender,
            blueprint = blueprint,
            controllerLocation = targetLocation,
            facing = facing
        )

        if (session != null) {
            val legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            sender.sendMessage("§b[全貌预览] §f${legacy.serialize(I18n.Message.Preview.started(blueprint.id, facing.name))}")
            sender.sendMessage(legacy.serialize(I18n.Message.Preview.previewWillExpire))
        } else {
            sender.sendMessage(I18n.Message.Preview.previewFailed)
        }
    }

    private fun handlePreviewStop(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        StructurePreviewManager.cancelPreview(sender)
        sender.sendMessage(I18n.Message.Preview.previewCancelled)
    }

    private fun handleBuildDomain(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /ml build <here|easy|printer>")
            sender.sendMessage("§7  here <ID> [facing]   一键建造")
            sender.sendMessage("§7  easy [on|off]        轻松放置模式")
            sender.sendMessage("§7  printer [on|off]     自动打印模式")
            return
        }
        
        when (args[0].lowercase()) {
            "here" -> handleBuildHere(sender, args.drop(1))
            "easy" -> handleBuildEasy(sender, args.drop(1))
            "printer" -> handleBuildPrinter(sender, args.drop(1))
            else -> {
                sender.sendMessage("§c未知子命令: §f${args[0]}")
                sender.sendMessage("§7可用: here, easy, printer")
            }
        }
    }

    private fun handleBuildHere(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (!sender.hasPermission(Constants.Permissions.BUILD)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }
        
        val blueprintId = args.getOrNull(0)
        if (blueprintId == null) {
            sender.sendMessage("§c用法: /ml build here <蓝图ID> [朝向]")
            sender.sendMessage("§7在当前位置一键建造结构")
            return
        }

        val blueprint = api.registry.get(blueprintId)
        if (blueprint == null) {
            sender.sendMessage("§c未找到蓝图: §f$blueprintId")
            return
        }

        val targetLocation = sender.location.clone().add(sender.location.direction.normalize())
        targetLocation.x = targetLocation.blockX.toDouble()
        targetLocation.y = targetLocation.blockY.toDouble()
        targetLocation.z = targetLocation.blockZ.toDouble()

        val facing = top.mc506lw.monolith.core.transform.Facing.fromYaw(sender.location.yaw)
        
        val builder = StructureBuildManager.startBuild(
            player = sender,
            blueprint = blueprint,
            controllerLocation = targetLocation,
            facing = facing
        )
        
        if (builder == null) {
            sender.sendMessage(I18n.Message.Command.buildFailed)
        }
    }

    private fun handleBuildEasy(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

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

    private fun handleBuildPrinter(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission("monolith.printer")) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val result = top.mc506lw.monolith.feature.buildsite.PrinterManager.toggle(sender)

        when (result) {
            true -> {
                sender.sendMessage(I18n.Message.Litematica.printerEnabled)
                sender.sendMessage("§7自动放置周身4格范围内的幽灵方块")
                sender.sendMessage("§7创造模式下无需任何材料")
                sender.sendMessage("§7离开工地范围1分钟后自动关闭")
            }
            false -> {
                sender.sendMessage(I18n.Message.Litematica.printerDisabled)
            }
            null -> {
                sender.sendMessage(I18n.Message.Litematica.printerNoSite)
            }
        }
    }

    private fun handleBlueprintDomain(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /ml bp <list|info|give>")
            sender.sendMessage("§7  list              列出所有蓝图")
            sender.sendMessage("§7  info <ID>         蓝图详情")
            sender.sendMessage("§7  give <ID>         给予蓝图物品")
            return
        }
        
        when (args[0].lowercase()) {
            "list" -> handleBpList(sender)
            "info" -> handleBpInfo(sender, args.drop(1))
            "give" -> handleBpGive(sender, args.drop(1))
            else -> {
                sender.sendMessage("§c未知子命令: §f${args[0]}")
                sender.sendMessage("§7可用: list, info, give")
            }
        }
    }

    private fun handleBpList(sender: CommandSender) {
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

    private fun handleBpInfo(sender: CommandSender, args: List<String>) {
        val blueprintId = args.getOrNull(0)
        if (blueprintId == null) {
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
            return
        }
        
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
    }

    private fun handleBpGive(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.BLUEPRINT)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val blueprintId = args.getOrNull(0)
        if (blueprintId == null) {
            sender.sendMessage(I18n.Message.Command.Blueprint.noId)
            sender.sendMessage(I18n.Message.Command.Blueprint.hint)
            return
        }

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

    private fun handleSiteDomain(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /ml site <list|info|cancel>")
            sender.sendMessage("§7  list              活跃工地列表")
            sender.sendMessage("§7  info              附近工地状态")
            sender.sendMessage("§7  cancel            取消附近工地")
            return
        }
        
        when (args[0].lowercase()) {
            "list" -> handleSiteList(sender)
            "info" -> handleSiteInfo(sender)
            "cancel" -> handleSiteCancel(sender)
            else -> {
                sender.sendMessage("§c未知子命令: §f${args[0]}")
                sender.sendMessage("§7可用: list, info, cancel")
            }
        }
    }

    private fun handleSiteList(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.SITE)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val sites = BuildSiteManager.getAllActiveSites()
        if (sites.isEmpty()) {
            sender.sendMessage("§7当前没有活跃的工地")
            return
        }

        sender.sendMessage("§6§l[MonolithLib] §f活跃工地 (${sites.size})")
        sites.forEach { site ->
            val stateColor = when (site.state) {
                top.mc506lw.monolith.feature.buildsite.BuildSiteState.BUILDING -> "§a"
                top.mc506lw.monolith.feature.buildsite.BuildSiteState.AWAITING_CORE -> "§e"
                top.mc506lw.monolith.feature.buildsite.BuildSiteState.VIRTUAL -> "§7"
            }
            val stateName = site.state.name
            sender.sendMessage("  ${stateColor}[${stateName}] §f${site.blueprintId} §7@ (${site.anchorLocation.blockX}, ${site.anchorLocation.blockY}, ${site.anchorLocation.blockZ})")
        }
    }

    private fun handleSiteInfo(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.SITE)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val playerPos = top.mc506lw.monolith.core.math.Vector3i(
            sender.location.blockX,
            sender.location.blockY,
            sender.location.blockZ
        )

        val nearbySites = BuildSiteManager.getAllActiveSites().filter { 
            it.containsPosition(playerPos) 
        }

        if (nearbySites.isEmpty()) {
            sender.sendMessage("§7附近没有工地")
            return
        }

        nearbySites.forEach { site ->
            sender.sendMessage("")
            sender.sendMessage("§6§l[工地] §f${site.blueprintId}")
            sender.sendMessage("  §7状态: ${site.state.name}")
            sender.sendMessage("  §7位置: (${site.anchorLocation.blockX}, ${site.anchorLocation.blockY}, ${site.anchorLocation.blockZ})")
            sender.sendMessage("  §7朝向: ${site.facing.name}")

            val (placed, total) = site.getProgress()
            sender.sendMessage("  §7进度: §f$placed/$total §7(${String.format("%.1f", if (total > 0) placed.toDouble() / total.toDouble() * 100 else 100.0)}%)")
            
            if (site.isCompleted) {
                sender.sendMessage("  §a✓ 已完工")
            }
        }
    }

    private fun handleSiteCancel(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.SITE)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val playerPos = top.mc506lw.monolith.core.math.Vector3i(
            sender.location.blockX,
            sender.location.blockY,
            sender.location.blockZ
        )

        val nearbySites = BuildSiteManager.getAllActiveSites().filter { 
            it.containsPosition(playerPos) 
        }

        if (nearbySites.isEmpty()) {
            sender.sendMessage("§7附近没有可取消的工地")
            return
        }

        nearbySites.forEach { site ->
            BuildSiteManager.removeSite(site.id)
            sender.sendMessage("§a已取消工地: §f${site.blueprintId}")
        }
    }

    private fun handleEditDomain(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /ml edit <wand|save|merge>")
            sender.sendMessage("§7  wand                        获取选区魔杖")
            sender.sendMessage("§7  save <name> [--scaffold|--assembled]")
            sender.sendMessage("§7  merge <name1> <name2>")
            return
        }
        
        when (args[0].lowercase()) {
            "wand" -> handleEditWand(sender)
            "save" -> handleEditSave(sender, args.drop(1))
            "merge" -> handleEditMerge(sender, args.drop(1))
            else -> {
                sender.sendMessage("§c未知子命令: §f${args[0]}")
                sender.sendMessage("§7可用: wand, save, merge")
            }
        }
    }

    private fun handleEditWand(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.EDIT)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val leftover = sender.inventory.addItem(SelectionWand.STACK.clone())
        if (leftover.isNotEmpty()) {
            sender.sendMessage("§c背包已满，物品已掉落")
            sender.world.dropItemNaturally(sender.location, SelectionWand.STACK.clone())
        } else {
            sender.sendMessage("§a已获得选区魔杖")
            sender.sendMessage("§7左键设置起点，右键设置终点")
        }
    }

    private fun handleEditSave(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.EDIT)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        val name = args.getOrNull(0)
        if (name == null) {
            sender.sendMessage("§c用法: /ml edit save <名称> [--scaffold|--assembled]")
            return
        }

        val selection = SelectionManager.getSelection(sender)
        if (selection == null || !selection.isComplete) {
            sender.sendMessage("§c请先用选区魔杖选择区域")
            return
        }

        val stage = when {
            args.any { it.equals("--scaffold", ignoreCase = true) } -> "SCAFFOLD"
            args.any { it.equals("--assembled", ignoreCase = true) } -> "ASSEMBLED"
            else -> "ASSEMBLED"
        }

        try {
            sender.sendMessage("§e编辑器保存功能开发中...")
        } catch (e: Exception) {
            sender.sendMessage("§c保存失败: ${e.message}")
        }
    }

    private fun handleEditMerge(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }

        if (!sender.hasPermission(Constants.Permissions.EDIT)) {
            sender.sendMessage(I18n.Message.Command.permissionDenied)
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§c用法: /ml edit merge <结构A> <结构B>")
            return
        }

        try {
            sender.sendMessage("§e编辑器合并功能开发中...")
        } catch (e: Exception) {
            sender.sendMessage("§c合并失败: ${e.message}")
        }
    }
}
