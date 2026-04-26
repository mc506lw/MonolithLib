package top.mc506lw.monolith.test

import org.bukkit.Bukkit
import org.bukkit.Material
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.BuildStage
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.test.blocks.TestBlocks
import top.mc506lw.monolith.test.blocks.CustomStructureController
import top.mc506lw.monolith.test.blocks.FurnaceCoreBlock

object TestModule {

    fun init() {
        println("[MonolithLib] 初始化示例蓝图和测试方块...")
        TestBlocks.registerAll()
        registerExampleBlueprints()
        println("[MonolithLib] 示例模块初始化完成!")
    }

    private fun registerExampleBlueprints() {
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
            id = "example_simple_3x3",
            stages = mapOf(BuildStage.SCAFFOLD to simpleShape, BuildStage.ASSEMBLED to simpleShape),
            meta = BlueprintMeta(
                displayName = "示例3x3结构",
                description = "一个简单的3x3示例结构 - 用于演示MonolithLib的基本功能"
            )
        )

        api.registry.register(simpleBlueprint)

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
            id = "example_rebar_machine",
            stages = mapOf(BuildStage.SCAFFOLD to rebarShape, BuildStage.ASSEMBLED to rebarShape),
            meta = BlueprintMeta(
                displayName = "示例Rebar机器",
                description = "一个需要Rebar组件的5x3x5机器 - 演示验改分离的完整流程",
                controllerOffset = Vector3i(2, 1, 2)
            ),
            controllerRebarKey = CustomStructureController.KEY
        )

        api.registry.register(rebarBlueprint)

        val stone = Bukkit.createBlockData("minecraft:stone")
        val stairNorth = Bukkit.createBlockData("minecraft:oak_stairs[facing=north]")
        val stairEast = Bukkit.createBlockData("minecraft:oak_stairs[facing=east]")
        val stairSouth = Bukkit.createBlockData("minecraft:oak_stairs[facing=south]")
        val stairWest = Bukkit.createBlockData("minecraft:oak_stairs[facing=west]")
        val furnace = Bukkit.createBlockData("minecraft:furnace[facing=north]")
        val looseBlocks = mutableListOf<BlockEntry>()

        looseBlocks.add(BlockEntry(Vector3i(0, 0, 0), stone))
        looseBlocks.add(BlockEntry(Vector3i(1, 0, 0), stairNorth))
        looseBlocks.add(BlockEntry(Vector3i(2, 0, 0), stone))

        looseBlocks.add(BlockEntry(Vector3i(0, 0, 1), stairEast))
        looseBlocks.add(BlockEntry(Vector3i(1, 0, 1), furnace))
        looseBlocks.add(BlockEntry(Vector3i(2, 0, 1), stairWest))

        looseBlocks.add(BlockEntry(Vector3i(0, 0, 2), stone))
        looseBlocks.add(BlockEntry(Vector3i(1, 0, 2), stairSouth))
        looseBlocks.add(BlockEntry(Vector3i(2, 0, 2), stone))

        val looseShape = Shape(looseBlocks)

        val looseBlueprint = Blueprint.builder("example_loose_furnace")
            .shape(looseShape)
            .displayName("宽松熔炉示例")
            .description("演示宽松匹配模式 - 楼梯朝向不影响建造进度")
            .controllerOffset(Vector3i(1, 0, 1))
            .controllerRebar(FurnaceCoreBlock.KEY)
            .build()

        api.registry.register(looseBlueprint)

        println("[MonolithLib] 已注册 3 个示例蓝图")
    }
}
