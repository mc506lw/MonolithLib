package top.mc506lw.monolith.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.transform.Facing
import top.mc506lw.monolith.test.blocks.TestBlocks

object TestModule : CommandExecutor, TabCompleter {
    
    private var initialized = false
    
    fun init() {
        if (initialized) return
        initialized = true
        
        println("[MonolithLib] 初始化测试模块...")
        TestBlocks.registerAll()
        registerTestStructures()
        registerTestListeners()
        println("[MonolithLib] 测试模块初始化完成! 使用 /mltest 查看命令")
    }
    
    private fun registerTestStructures() {
        val api = MonolithAPI.getInstance()
        
        val ironBlock = Bukkit.createBlockData("minecraft:iron_block")
        val simpleBlocks = mutableListOf<BlockEntry>()
        
        for (x in 0..2) {
            for (z in 0..2) {
                if (x == 1 && z == 1) continue
                simpleBlocks.add(BlockEntry(Vector3i(x, 0, z), ironBlock))
            }
        }
        
        val simpleShape = Shape(simpleBlocks)
        
        val simpleBlueprint = Blueprint(
            id = "test_simple_3x3",
            shape = simpleShape,
            meta = BlueprintMeta(
                displayName = "简单3x3",
                description = "一个简单的测试结构",
                author = "MonolithLib",
                version = "1.0",
                controllerOffset = Vector3i(1, 0, 1)
            )
        )
        
        api.registry.registerBlueprint(simpleBlueprint)
        
        val casing = Bukkit.createBlockData("minecraft:iron_block")
        val glass = Bukkit.createBlockData("minecraft:glass")
        val rebarBlocks = mutableListOf<BlockEntry>()
        
        for (x in 0..4) {
            for (z in 0..4) {
                rebarBlocks.add(BlockEntry(Vector3i(x, 0, z), casing))
                rebarBlocks.add(BlockEntry(Vector3i(x, 2, z), casing))
            }
        }
        
        for (x in listOf(0, 4)) {
            for (z in 0..4) {
                rebarBlocks.add(BlockEntry(Vector3i(x, 1, z), glass))
            }
        }
        for (z in listOf(0, 4)) {
            for (x in 1..3) {
                rebarBlocks.add(BlockEntry(Vector3i(x, 1, z), glass))
            }
        }
        
        val rebarShape = Shape(rebarBlocks)
        
        val rebarBlueprint = Blueprint(
            id = "test_rebar_machine",
            shape = rebarShape,
            meta = BlueprintMeta(
                displayName = "Rebar机器",
                description = "需要Rebar组件的机器",
                author = "MonolithLib",
                version = "1.0",
                controllerOffset = Vector3i(2, 1, 2)
            )
        )
        
        api.registry.registerBlueprint(rebarBlueprint)
        
        val stone = Bukkit.createBlockData("minecraft:stone")
        val looseBlocks = mutableListOf<BlockEntry>()
        
        for (x in 0..2) {
            for (z in 0..2) {
                looseBlocks.add(BlockEntry(Vector3i(x, 0, z), stone))
            }
        }
        
        val looseShape = Shape(looseBlocks)
        
        val looseBlueprint = Blueprint(
            id = "test_loose_furnace",
            shape = looseShape,
            meta = BlueprintMeta(
                displayName = "宽松熔炉",
                description = "测试宽松匹配",
                author = "MonolithLib",
                version = "1.0",
                controllerOffset = Vector3i(1, 0, 1)
            )
        )
        
        api.registry.registerBlueprint(looseBlueprint)
        
        println("[MonolithLib] 已注册 3 个测试蓝图")
    }
    
    private fun registerTestListeners() {
        Bukkit.getPluginManager().registerEvents(TestBlockListener, top.mc506lw.rebar.MonolithLib.instance)
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when {
            args.isEmpty() -> sendHelp(sender)
            args[0] == "list" -> handleList(sender)
            args[0] == "info" -> handleInfo(sender, args)
            args[0] == "preview" -> handlePreview(sender, args)
            args[0] == "layer" -> handleLayer(sender, args)
            args[0] == "stop" -> handleStop(sender)
            else -> sendHelp(sender)
        }
        return true
    }
    
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(I18n.Message.Command.TestHelp.title)
        sender.sendMessage(I18n.Message.Command.TestHelp.list)
        sender.sendMessage(I18n.Message.Command.TestHelp.info)
        sender.sendMessage(I18n.Message.Command.TestHelp.preview)
        sender.sendMessage(I18n.Message.Command.TestHelp.layer)
        sender.sendMessage(I18n.Message.Command.TestHelp.stop)
        sender.sendMessage(I18n.Message.Command.TestHelp.previewFeatures)
        sender.sendMessage(I18n.Message.Command.TestHelp.feature1)
        sender.sendMessage(I18n.Message.Command.TestHelp.feature2)
        sender.sendMessage(I18n.Message.Command.TestHelp.feature3)
        sender.sendMessage(I18n.Message.Command.TestHelp.testStructures)
        sender.sendMessage(I18n.Message.Command.TestHelp.testSimple)
        sender.sendMessage(I18n.Message.Command.TestHelp.testRebar)
        sender.sendMessage(I18n.Message.Command.TestHelp.testLoose)
        sender.sendMessage(I18n.Message.Command.TestHelp.testBlocks)
        sender.sendMessage(I18n.Message.Command.TestHelp.blockTestController)
        sender.sendMessage(I18n.Message.Command.TestHelp.blockFurnaceCore)
        sender.sendMessage(I18n.Message.Command.TestHelp.blockMachineCore)
        sender.sendMessage(I18n.Message.Command.TestHelp.blockCustomController)
    }
    
    private fun handleList(sender: CommandSender) {
        val blueprints = MonolithAPI.getInstance().registry.getAllBlueprints()
        sender.sendMessage(I18n.Message.Command.List.title(blueprints.size))
        blueprints.forEach { (id, blueprint) ->
            val isTest = id.startsWith("test_")
            val prefix = if (isTest) "§e[TEST] " else "§7"
            sender.sendMessage("  $prefix$id §7(${blueprint.sizeX}x${blueprint.sizeY}x${blueprint.sizeZ})")
        }
    }
    
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest info <蓝图ID>"))
            return
        }
        
        val blueprint = MonolithAPI.getInstance().registry.getBlueprint(args[1])
        if (blueprint == null) {
            sender.sendMessage(I18n.Message.Structure.notFound(args[1]))
            return
        }
        
        sender.sendMessage(I18n.Message.Command.Info.blueprintTitle(blueprint.id))
        sender.sendMessage(I18n.Message.Command.Info.name(blueprint.meta.displayName))
        sender.sendMessage(I18n.Message.Command.Info.description(blueprint.meta.description))
        sender.sendMessage(I18n.Message.Command.Info.size(blueprint.sizeX, blueprint.sizeY, blueprint.sizeZ))
        sender.sendMessage(I18n.Message.Command.Info.blockCount(blueprint.shape.blocks.size))
    }
    
    private fun handlePreview(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest preview <蓝图ID> [north/south/east/west]"))
            return
        }
        
        val facing = try {
            Facing.valueOf(args.getOrNull(2)?.uppercase() ?: "NORTH")
        } catch (e: Exception) {
            Facing.NORTH
        }
        
        val session = MonolithAPI.getInstance().preview.start(player, args[1], player.location.block.location, facing)
        if (session != null) {
            sender.sendMessage(I18n.Message.Preview.started(args[1], facing.name))
            sender.sendMessage(I18n.Message.Preview.currentPosition(player.location.blockX, player.location.blockY, player.location.blockZ))
            sender.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
            sender.sendMessage(I18n.Message.Preview.useLayerCommand)
        } else {
            sender.sendMessage(I18n.Message.Preview.notFound(args[1]))
        }
    }
    
    private fun handleLayer(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        val sessions = MonolithAPI.getInstance().preview.getPlayerSessions(player)
        if (sessions.isEmpty()) {
            sender.sendMessage(I18n.Message.Preview.noActive)
            sender.sendMessage(I18n.Message.Preview.startPreview)
            return
        }
        
        when {
            args.size < 2 -> {
                sessions.forEach { session ->
                    val (correct, total) = session.getLayerProgress()
                    sender.sendMessage("§6[MonolithLib Test] 蓝图: §f${session.structureId}")
                    sender.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
                    sender.sendMessage(I18n.Message.Preview.progress(correct, total, if (correct == total) "✓" else ""))
                }
            }
            args[1] == "up" -> {
                if (MonolithAPI.getInstance().preview.nextLayer(player)) {
                    sender.sendMessage(I18n.Message.Preview.layerUp)
                } else {
                    sender.sendMessage(I18n.Message.Preview.layerTop)
                }
            }
            args[1] == "down" -> {
                if (MonolithAPI.getInstance().preview.prevLayer(player)) {
                    sender.sendMessage(I18n.Message.Preview.layerDown)
                } else {
                    sender.sendMessage(I18n.Message.Preview.layerBottom)
                }
            }
            else -> {
                val layer = args[1].toIntOrNull()
                if (layer == null) {
                    sender.sendMessage(I18n.Message.Preview.layerInvalid(args[1]))
                    return
                }
                if (MonolithAPI.getInstance().preview.setLayer(player, layer)) {
                    sender.sendMessage(I18n.Message.Preview.layerChanged(layer))
                } else {
                    sender.sendMessage(I18n.Message.Preview.layerOutOfRange)
                }
            }
        }
    }
    
    private fun handleStop(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        val api = MonolithAPI.getInstance()
        val sessions = api.preview.getPlayerSessions(player)
        
        if (sessions.isEmpty()) {
            sender.sendMessage(I18n.Message.Preview.noActive)
            return
        }
        
        api.preview.stop(player)
        sender.sendMessage(I18n.Message.Preview.stopped(sessions.size))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("list", "info", "preview", "layer", "stop")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                when (args[0].lowercase()) {
                    "info", "preview" -> 
                        MonolithAPI.getInstance().registry.getAllBlueprints().keys
                            .filter { it.startsWith(args[1].lowercase()) }
                            .take(10)
                    "layer" -> listOf("up", "down").filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                if (args[0].lowercase() == "preview") {
                    listOf("north", "south", "east", "west")
                        .filter { it.startsWith(args[2].lowercase()) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
