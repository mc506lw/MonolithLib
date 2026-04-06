package top.mc506lw.monolith.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.api.dsl.buildStructure
import top.mc506lw.monolith.common.I18n
import top.mc506lw.monolith.core.predicate.Predicates
import top.mc506lw.monolith.core.structure.StructureRegistry
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
        val simpleStructure = buildStructure("test_simple_3x3") {
            size(3, 1, 3)
            center(1, 0, 1)
            
            controllerRebar("monolith:test_controller")
            
            val ironBlock = Bukkit.createBlockData("minecraft:iron_block")
            
            for (x in 0..2) {
                for (z in 0..2) {
                    if (x == 1 && z == 1) continue
                    block(x, 0, z, ironBlock)
                }
            }
            
            slotRebar("controller", 1, 0, 1, "monolith:test_controller", Material.CRAFTING_TABLE)
            
            custom("base_power", 100)
            custom("description", "简单3x3测试结构")
            
            meta("简单3x3", "一个简单的测试结构", "MonolithLib", "1.0")
        }
        StructureRegistry.getInstance().register(simpleStructure)
        
        val rebarMachine = buildStructure("test_rebar_machine") {
            size(5, 3, 5)
            center(2, 0, 2)
            
            controllerRebar("monolith:machine_core")
            
            val casing = Bukkit.createBlockData("minecraft:iron_block")
            val glass = Bukkit.createBlockData("minecraft:glass")
            
            for (x in 0..4) {
                for (z in 0..4) {
                    block(x, 0, z, casing)
                    block(x, 2, z, casing)
                }
            }
            
            for (x in listOf(0, 4)) {
                for (z in 0..4) {
                    block(x, 1, z, glass)
                }
            }
            for (z in listOf(0, 4)) {
                for (x in 1..3) {
                    block(x, 1, z, glass)
                }
            }
            
            slotRebar("controller", 2, 1, 2, "monolith:machine_core", Material.BLAST_FURNACE)
            slotRebar("heat_sink", 0, 1, 2, "rebar:heat_sink", Material.IRON_BLOCK)
            slotRebar("power_port", 4, 1, 2, "rebar:power_port", Material.REDSTONE_BLOCK)
            slotRebar("input", 2, 1, 0, "rebar:item_port", Material.HOPPER)
            slotRebar("output", 2, 1, 4, "rebar:item_port", Material.HOPPER)
            
            custom("base_power", 1000)
            custom("power_per_sink", 500)
            custom("processing_speed", 1.0)
            
            meta("Rebar机器", "需要Rebar组件的机器", "MonolithLib", "1.0")
        }
        StructureRegistry.getInstance().register(rebarMachine)
        
        val looseStructure = buildStructure("test_loose_furnace") {
            size(3, 2, 3)
            center(1, 0, 1)
            
            controllerRebar("monolith:furnace_core")
            
            val stone = Bukkit.createBlockData("minecraft:stone")
            
            for (x in 0..2) {
                for (z in 0..2) {
                    block(x, 0, z, stone)
                }
            }
            
            slotRebar("controller", 1, 0, 1, "monolith:furnace_core", Material.FURNACE)
            
            loose(1, 1, 1, Material.HOPPER)
            
            custom("fuel_consumption", 1.0)
            
            meta("宽松熔炉", "测试宽松匹配", "MonolithLib", "1.0")
        }
        StructureRegistry.getInstance().register(looseStructure)
        
        println("[MonolithLib] 已注册 3 个测试结构")
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
            args[0] == "slots" -> handleSlots(sender, args)
            args[0] == "custom" -> handleCustom(sender, args)
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
        sender.sendMessage(I18n.Message.Command.TestHelp.slots)
        sender.sendMessage(I18n.Message.Command.TestHelp.custom)
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
        val structures = StructureRegistry.getInstance().getAll()
        sender.sendMessage(I18n.Message.Command.List.title(structures.size))
        structures.forEach { (id, structure) ->
            val isTest = id.startsWith("test_")
            val prefix = if (isTest) "§e[TEST] " else "§7"
            val rebarInfo = if (structure.controllerRebarKey != null) " §b[Rebar]" else ""
            sender.sendMessage("  $prefix$id §7(${structure.sizeX}x${structure.sizeY}x${structure.sizeZ})$rebarInfo")
        }
    }
    
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest info <结构ID>"))
            return
        }
        
        val structure = StructureRegistry.getInstance().get(args[1])
        if (structure == null) {
            sender.sendMessage(I18n.Message.Structure.notFound(args[1]))
            return
        }
        
        sender.sendMessage(I18n.Message.Command.Info.title)
        sender.sendMessage("  §7名称: §f${structure.meta.name}")
        sender.sendMessage("  §7描述: §f${structure.meta.description}")
        sender.sendMessage("  §7作者: §f${structure.meta.author}")
        sender.sendMessage("  §7版本: §f${structure.meta.version}")
        sender.sendMessage("  §7尺寸: §f${structure.sizeX} x ${structure.sizeY} x ${structure.sizeZ}")
        sender.sendMessage("  §7中心: §f(${structure.centerOffset.x}, ${structure.centerOffset.y}, ${structure.centerOffset.z})")
        sender.sendMessage("  §7非空方块: §f${structure.flattenedBlocks.size}")
        sender.sendMessage("  §7槽位数量: §f${structure.slots.size}")
        sender.sendMessage("  §7控制器: §f${structure.controllerRebarKey?.toString() ?: "原版"}")
    }
    
    private fun handleSlots(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest slots <结构ID>"))
            return
        }
        
        val structure = StructureRegistry.getInstance().get(args[1])
        if (structure == null) {
            sender.sendMessage(I18n.Message.Structure.notFound(args[1]))
            return
        }
        
        sender.sendMessage("§6[MonolithLib Test] 槽位: ${structure.id}")
        structure.slots.forEach { (slotId, pos) ->
            sender.sendMessage("  §7$slotId: §f(${pos.x}, ${pos.y}, ${pos.z})")
        }
    }
    
    private fun handleCustom(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest custom <结构ID>"))
            return
        }
        
        val structure = StructureRegistry.getInstance().get(args[1])
        if (structure == null) {
            sender.sendMessage(I18n.Message.Structure.notFound(args[1]))
            return
        }
        
        sender.sendMessage("§6[MonolithLib Test] 自定义数据: ${structure.id}")
        structure.customData.forEach { (key, value) ->
            sender.sendMessage("  §7$key: §f$value")
        }
    }
    
    private fun handlePreview(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(I18n.Message.Command.usage("/mltest preview <结构ID> [north/south/east/west]"))
            return
        }
        
        val facing = try {
            Facing.valueOf(args.getOrNull(2)?.uppercase() ?: "NORTH")
        } catch (e: Exception) {
            Facing.NORTH
        }
        
        val session = MonolithAPI.getInstance().startPreview(player, player.location.block.location, args[1], facing)
        if (session == null) {
            sender.sendMessage(I18n.Message.Preview.notFound(args[1]))
            return
        }
        
        sender.sendMessage(I18n.Message.Preview.started(args[1], facing.name))
        sender.sendMessage(I18n.Message.Preview.currentPosition(player.location.blockX, player.location.blockY, player.location.blockZ))
        sender.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
        sender.sendMessage(I18n.Message.Preview.useLayerCommand)
    }
    
    private fun handleLayer(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(I18n.Message.Command.playerOnly)
            return
        }
        
        val sessions = MonolithAPI.getInstance().getPlayerPreviewSessions(player)
        if (sessions.isEmpty()) {
            sender.sendMessage(I18n.Message.Preview.noActive)
            sender.sendMessage(I18n.Message.Preview.startPreview)
            return
        }
        
        when {
            args.size < 2 -> {
                sessions.forEach { session ->
                    val (correct, total) = session.getLayerProgress()
                    sender.sendMessage("§6[MonolithLib Test] 结构: §f${session.structureId}")
                    sender.sendMessage(I18n.Message.Preview.currentLayer(session.currentLayer, session.maxLayer))
                    sender.sendMessage(I18n.Message.Preview.progress(correct, total, if (correct == total) "✓" else ""))
                }
            }
            args[1] == "up" -> {
                if (MonolithAPI.getInstance().nextPreviewLayer(player)) {
                    sender.sendMessage(I18n.Message.Preview.layerUp)
                } else {
                    sender.sendMessage(I18n.Message.Preview.layerTop)
                }
            }
            args[1] == "down" -> {
                if (MonolithAPI.getInstance().prevPreviewLayer(player)) {
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
                if (MonolithAPI.getInstance().setPreviewLayer(player, layer)) {
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
        val sessions = api.getPlayerPreviewSessions(player)
        
        if (sessions.isEmpty()) {
            sender.sendMessage(I18n.Message.Preview.noActive)
            return
        }
        
        api.stopPreview(player)
        sender.sendMessage(I18n.Message.Preview.stopped(sessions.size))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("list", "info", "preview", "layer", "slots", "custom", "stop")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                when (args[0].lowercase()) {
                    "info", "preview", "slots", "custom" -> 
                        StructureRegistry.getInstance().getAll().keys
                            .filter { it.startsWith(args[1].lowercase()) }
                    "layer" -> listOf("up", "down").filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                if (args[0].lowercase() == "preview") {
                    listOf("north", "south", "east", "west")
                        .filter { it.startsWith(args[2].lowercase()) }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
