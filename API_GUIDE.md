# MonolithLib API 开发者指南

## 目录

1. [快速开始](#快速开始)
2. [核心概念](#核心概念)
3. [MonolithAPI 主入口](#monolithapi-主入口)
4. [蓝图 (Blueprint)](#蓝图-blueprint)
5. [形状 (Shape)](#形状-shape)
6. [构建阶段 (BuildStage)](#构建阶段-buildstage)
7. [BlueprintDSL 编程式构建](#blueprintdsl-编程式构建)
8. [谓词系统 (Predicate)](#谓词系统-predicate)
9. [显示实体 (DisplayEntityData)](#显示实体-displayentitydata)
10. [事件系统](#事件系统)
11. [面向 (Facing)](#面向-facing)
12. [Vector3i](#vector3i)
13. [蓝图配置 (BlueprintConfig)](#蓝图配置-blueprintconfig)
14. [编译器 (BuiltMNBCompiler)](#编译器-builtmnbcompiler)
15. [BinaryFormat (二进制序列化)](#binaryformat-二进制序列化)
16. [示例代码](#示例代码)

---

## 快速开始

### 获取 API 实例

```kotlin
val api = MonolithAPI.getInstance()
```

### 注册一个蓝图

```kotlin
api.registry.register(blueprint)
```

### 使用 DSL 快速构建

```kotlin
registerMonolithStructure("my_structure") {
    size(5, 5, 5)
    center(2, 0, 2)
    block(0, 0, 0, Material.STONE)
    // ...
}
```

---

## 核心概念

| 概念 | 说明 |
|------|------|
| **Blueprint** | 蓝图，定义一个多方块结构的完整信息（形状、元数据、槽位、显示实体） |
| **Shape** | 形状，定义结构中方块的相对位置和类型 |
| **BuildStage** | 构建阶段，SCAFFOLD（脚手架）和 ASSEMBLED（成型）两个阶段 |
| **Predicate** | 谓词，定义方块匹配规则（严格匹配、松散匹配、Rebar等） |
| **Slot** | 槽位，结构中的特殊位置（如控制器位置） |
| **Facing** | 面向，结构在世界中放置的方向（东南西北） |
| **DisplayEntityData** | 显示实体数据，定义静态展示实体 |

---

## MonolithAPI 主入口

`MonolithAPI` 是插件的核心入口点，提供四个核心 Facade：

### 获取实例

```kotlin
val api = MonolithAPI.getInstance()
```

### 接口定义

```kotlin
interface MonolithAPI {
    val registry: BlueprintRegistry     // 蓝图注册表
    val io: IOFacade                    // 文件输入输出
    val preview: PreviewFacade          // 预览功能
    val buildSite: BuildSiteFacade      // 工地管理

    fun reloadStructures()

    companion object {
        fun getInstance(): MonolithAPI
    }
}
```

### BlueprintRegistry - 蓝图注册表

管理所有已加载的蓝图：

```kotlin
interface BlueprintRegistry {
    fun register(blueprint: Blueprint)
    fun get(id: String): Blueprint?
    fun getAll(): Map<String, Blueprint>
    fun getByControllerKey(key: NamespacedKey): List<Blueprint>
    fun contains(id: String): Boolean
    fun remove(id: String): Blueprint?
    fun clear()
    val size: Int
}
```

**使用示例**：

```kotlin
val registry = api.registry

// 注册蓝图
registry.register(myBlueprint)

// 获取蓝图
val blueprint = registry.get("my_structure")

// 按控制器键查询
val blueprints = registry.getByControllerKey(MyControllerBlock.KEY)

// 检查是否存在
if (registry.contains("my_structure")) {
    // ...
}
```

### IOFacade - 文件输入输出

加载各种格式的结构文件：

```kotlin
interface IOFacade {
    fun loadShape(file: File, format: String? = null): Shape?
    fun loadRawMNB(file: File): Blueprint?
    fun loadBuiltMNB(file: File): Blueprint?
    fun compileRawToBuilt(rawFile: File, configFile: File): Blueprint?
    fun getSupportedFormats(): List<String>
    fun getSupportedExtensions(): Set<String>
}
```

**支持格式**：
- `.mnb` - 自定义二进制格式
- `.litematic` - Litematica 模组格式
- `.schem` - Schematic 格式
- `.nbt` - NBT 结构格式

**使用示例**：

```kotlin
val io = api.io

// 加载 Shape
val shape = io.loadShape(File("structure.litematic"))

// 加载原始蓝图
val rawBlueprint = io.loadRawMNB(File("my_struct.raw.mnb"))

// 加载编译后的蓝图
val blueprint = io.loadBuiltMNB(File("my_struct.mnb"))

// 查看支持的格式
val formats = io.getSupportedFormats()      // ["mnb", "litematic", "schem", "nbt"]
val extensions = io.getSupportedExtensions() // {".mnb", ".litematic", ".schem", ".nbt"}
```

### PreviewFacade - 预览功能

在游戏中以半透明 Ghost 形式预览结构：

```kotlin
interface PreviewFacade {
    fun start(player: Player, blueprint: Blueprint, location: Location, facing: Facing = Facing.NORTH)
    fun start(player: Player, blueprintId: String, location: Location, facing: Facing = Facing.NORTH): PreviewSession?
    fun stop(player: Player)
    fun getPlayerSessions(player: Player): List<PreviewSession>
    fun nextLayer(player: Player): Boolean
    fun prevLayer(player: Player): Boolean
    fun setLayer(player: Player, layer: Int): Boolean
}
```

**使用示例**：

```kotlin
val preview = api.preview

// 启动预览
preview.start(player, blueprint, location, Facing.NORTH)
preview.start(player, "my_structure", location, Facing.EAST)

// 层控制
preview.nextLayer(player)
preview.prevLayer(player)
preview.setLayer(player, 5)

// 停止预览
preview.stop(player)

// 获取玩家会话
val sessions = preview.getPlayerSessions(player)
```

### BuildSiteFacade - 工地管理

管理建造工地：

```kotlin
interface BuildSiteFacade {
    fun createSite(player: Player, blueprint: Blueprint, location: Location, facing: Facing): BuildSite?
    fun getSite(location: Location): BuildSite?
}
```

**使用示例**：

```kotlin
val buildSite = api.buildSite

// 创建工地
val site = buildSite.createSite(player, blueprint, location, Facing.NORTH)

// 查询工地
val existingSite = buildSite.getSite(location)
```

---

## 蓝图 (Blueprint)

Blueprint 是定义多方块结构的核心数据类：

### 数据结构

```kotlin
class Blueprint(
    val id: String,                                    // 唯一标识符
    val stages: Map<BuildStage, Shape>,               // 各阶段形状
    val meta: BlueprintMeta = BlueprintMeta(),        // 元数据
    val displayEntities: List<DisplayEntityData> = emptyList(),  // 显示实体
    val slots: Map<String, Vector3i> = emptyMap(),    // 槽位映射
    val customData: Map<String, Any> = emptyMap(),    // 自定义数据
    val controllerRebarKey: NamespacedKey? = null     // 控制器 Rebar 键
) {
    // 便捷属性
    val scaffoldShape: Shape    // 脚手架阶段形状
    val assembledShape: Shape   // 成型阶段形状
    val sizeX: Int              // X 轴尺寸
    val sizeY: Int              // Y 轴尺寸
    val sizeZ: Int              // Z 轴尺寸
    val blockCount: Int         // 方块数量

    // 方法
    fun getSlotPosition(slotId: String): Vector3i?
    fun getCustomData(key: String): Any?
    fun getCustomString(key: String): String?
    fun getCustomInt(key: String): Int?
    fun getCustomDouble(key: String): Double?
    fun getCustomBoolean(key: String): Boolean?

    companion object {
        fun builder(id: String): BlueprintBuilder
        fun fromSingleShape(id: String, shape: Shape, meta: BlueprintMeta = BlueprintMeta()): Blueprint
    }
}
```

### BlueprintMeta - 元数据

```kotlin
data class BlueprintMeta(
    val displayName: String = "",                              // 显示名称
    val description: String = "",                              // 描述
    val controllerOffset: Vector3i = Vector3i.ZERO            // 控制器偏移
)
```

### 使用 Builder 构建蓝图

```kotlin
val blueprint = Blueprint.builder("my_structure")
    .shape(myShape)                        // 单一形状（自动生成双阶段）
    .displayName("My Structure")
    .description("A test structure")
    .controllerOffset(0, 1, 0)
    .slot("controller", 5, 0, 5)
    .slot("input", 0, 1, 0)
    .customData("tier", 1)
    .controllerRebar(MyControllerBlock.KEY)
    .displayEntity(displayData)
    .build()
```

### 双阶段构建（形态分裂）

```kotlin
val scaffoldShape = Shape(scaffoldBlocks)  // 建造时的脚手架形态
val assembledShape = Shape(finalBlocks)    // 成品形态

val blueprint = Blueprint.builder("complex")
    .scaffoldShape(scaffoldShape)
    .assembledShape(assembledShape)
    .displayEntities(displayEntities)
    .controllerRebar(controllerKey)
    .build()
```

### 从单一生成双阶段

```kotlin
val shape = Shape(blocks)
val blueprint = Blueprint.fromSingleShape(
    id = "simple",
    shape = shape,
    meta = BlueprintMeta(displayName = "简单结构")
)
// 自动生成 SCAFFOLD 和 ASSEMBLED 两个相同阶段
```

---

## 形状 (Shape)

Shape 定义结构中方块的位置和类型：

### 数据结构

```kotlin
data class BlockEntry(
    val position: Vector3i,          // 相对坐标
    val blockData: BlockData,        // 方块状态
    val predicate: Predicate? = null // 可选谓词（用于 Rebar 方块等特殊验证）
) {
    val effectivePredicate: Predicate  // 实际使用的谓词（predicate ?: Predicates.strict(blockData)）
}

data class BoundingBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int
) {
    val sizeX: Int get() = maxX - minX + 1
    val sizeY: Int get() = maxY - minY + 1
    val sizeZ: Int get() = maxZ - minZ + 1
    val width: Int get() = sizeX
    val height: Int get() = sizeY
    val depth: Int get() = sizeZ
    val volume: Int get() = sizeX * sizeY * sizeZ
    val center: Vector3i
    
    fun contains(x: Int, y: Int, z: Int): Boolean
    
    companion object {
        fun fromBlocks(blocks: List<BlockEntry>): BoundingBox
        fun fromDimensions(sizeX: Int, sizeY: Int, sizeZ: Int): BoundingBox
    }
}

class Shape(
    val blocks: List<BlockEntry>,
    val boundingBox: BoundingBox
) {
    val blockCount: Int get() = blocks.size

    constructor(blocks: List<BlockEntry>)  // 自动计算包围盒

    fun getBlockAt(x: Int, y: Int, z: Int): BlockEntry?
    fun getBlocksInLayer(y: Int): List<BlockEntry>
    fun getUniqueYLevels(): Set<Int>

    companion object {
        val EMPTY: Shape
        fun builder(): ShapeBuilder
    }
}
```

### 使用示例

```kotlin
// 创建 Shape
val blocks = listOf(
    BlockEntry(Vector3i(0, 0, 0), Bukkit.createBlockData(Material.STONE)),
    BlockEntry(Vector3i(1, 0, 0), Bukkit.createBlockData(Material.IRON_BLOCK))
)
val shape = Shape(blocks)

// 查询
val block = shape.getBlockAt(0, 0, 0)
val layer = shape.getBlocksInLayer(0)
val yLevels = shape.getUniqueYLevels()

// 属性
println("尺寸: ${shape.boundingBox.width}x${shape.boundingBox.height}x${shape.boundingBox.depth}")
println("方块数: ${shape.blockCount}")
```

---

## 构建阶段 (BuildStage)

定义蓝图的两个构建阶段：

```kotlin
enum class BuildStage {
    SCAFFOLD,    // 脚手架阶段：玩家建造时的形态
    ASSEMBLED    // 成型阶段：最终完成的形态
}
```

### 阶段说明

| 阶段 | 说明 |
|------|------|
| **SCAFFOLD** | 脚手架阶段，玩家按幽灵提示逐个放置方块，只验证材料类型 |
| **ASSEMBLED** | 成型阶段，所有方块放置完成后自动修正 BlockData，生成显示实体，绑定 Rebar 逻辑 |

### 设计理念

- **验改分离**：建造阶段只验证材料类型；成型瞬间（Magic Moment）自动修正 BlockData
- **形态分裂**：蓝图包含两个阶段的形状定义，允许建造形态与最终形态不同

---

## BlueprintDSL 编程式构建

使用 DSL 语法以声明式方式构建蓝图：

### 基本用法

```kotlin
val blueprint = buildBlueprint("my_blueprint") {
    // 定义尺寸
    size(10, 5, 10)

    // 定义控制器偏移（相对于结构中心）
    center(5, 0, 5)

    // 添加方块 - 严格匹配
    block(0, 0, 0, Material.STONE)
    block(1, 0, 0, Bukkit.createBlockData("minecraft:diamond_block"))
    block(2, 0, 0, "minecraft:gold_block")

    // 添加方块 - 松散匹配（忽略部分属性变化）
    loose(3, 0, 0, Material.IRON_BLOCK)

    // 添加 Rebar 方块
    rebar(4, 0, 0, "my_mod:reinforced_concrete", Material.GRAY_CONCRETE)

    // 定义槽位
    slot("controller", 5, 0, 5)
    slotBlock("input", 0, 1, 0, Material.HOPPER)

    // 定义元数据
    meta(
        name = "My Structure",
        description = "A test multi-block structure"
    )
}
```

### 注册到插件

```kotlin
// 在你的 JavaPlugin 中
registerMonolithStructure("my_id") {
    size(5, 5, 5)
    center(2, 0, 2)
    block(0, 0, 0, Material.STONE)
    // ...
}
```

### DSL 方法参考

| 方法 | 签名 | 说明 |
|------|------|------|
| `size` | `size(x: Int, y: Int, z: Int)` | 定义结构尺寸 |
| `center` | `center(x: Int, y: Int, z: Int)` | 设置控制器偏移 |
| `block` | `block(x, y, z, material: Material)` | 添加严格匹配方块 |
| `block` | `block(x, y, z, blockData: BlockData)` | 添加严格匹配方块 |
| `block` | `block(x, y, z, blockDataStr: String)` | 添加严格匹配方块 |
| `block` | `block(x, y, z, predicate: Predicate)` | 添加自定义谓词方块 |
| `loose` | `loose(x, y, z, material: Material)` | 添加松散匹配方块 |
| `loose` | `loose(x, y, z, blockData: BlockData)` | 添加松散匹配方块 |
| `rebar` | `rebar(x, y, z, key: String, preview: Material)` | 添加 Rebar 方块 |
| `rebar` | `rebar(x, y, z, key: String, preview: BlockData)` | 添加 Rebar 方块 |
| `rebar` | `rebar(x, y, z, key: NamespacedKey, preview: BlockData)` | 添加 Rebar 方块 |
| `air` | `air(x, y, z)` | 定义空气（无方块要求） |
| `any` | `any(x, y, z)` | 任意非空气方块 |
| `slot` | `slot(slotId: String, x, y, z)` | 定义槽位 |
| `slotBlock` | `slotBlock(slotId, x, y, z, predicate)` | 带槽位的方块 |
| `slotBlock` | `slotBlock(slotId, x, y, z, blockData)` | 带槽位的方块 |
| `slotRebar` | `slotRebar(slotId, x, y, z, key, preview)` | 带槽位的 Rebar |
| `meta` | `meta(name, description, author, version)` | 设置元数据 |

---

## 谓词系统 (Predicate)

Predicate 定义方块匹配规则，用于验证结构建造是否正确。

### Predicate 接口

```kotlin
interface Predicate {
    fun test(blockData: BlockData, context: PredicateContext): Boolean
    fun testMaterialOnly(blockData: BlockData, context: PredicateContext): Boolean
    val previewBlockData: BlockData?   // 预览时显示的方块
    val hint: String?                   // 提示信息
}

data class PredicateContext(
    val position: Vector3i,             // 相对位置
    val facing: BlockFace? = null,       // 面向
    val properties: Map<String, Any?> = emptyMap(),
    val block: Block? = null             // 实际方块
)
```

### 内置谓词类型

| 类型 | 方法 | 说明 | 预览显示 |
|------|------|------|----------|
| Strict | `Predicates.strict(blockData)` | 严格匹配所有属性 | 指定 blockData |
| Loose | `Predicates.loose(material, preview)` | 仅匹配材质，忽略状态 | 预览材质 |
| Rebar | `Predicates.rebar(key, preview)` | 匹配 Rebar 模组方块 | 预览材质 |
| Air | `Predicates.air()` | 必须是空气 | 无 |
| Any | `Predicates.any()` | 任意非空气方块 | 无 |

### 使用示例

```kotlin
// 严格匹配
val strict = Predicates.strict(Bukkit.createBlockData(Material.STONE))

// 松散匹配
val loose = Predicates.loose(Material.STONE, Bukkit.createBlockData(Material.STONE))

// Rebar 匹配
val rebar = Predicates.rebar(NamespacedKey("my_mod", "machine"), Material.IRON_BLOCK)

// 在 DSL 中使用
buildBlueprint("test") {
    block(0, 0, 0, Predicates.strict(myBlockData))
    loose(1, 0, 0, Material.STONE)
    rebar(2, 0, 0, "my_mod:machine", Material.IRON_BLOCK)
}
```

### 录制时的自动检测

使用 `/ml save` 录制蓝图时，MonolithLib 会自动检测选区内的 Rebar 方块：

1. 使用 `RebarAdapter.createRebarPredicate(block)` 检测每个方块
2. 如果是 Rebar 方块，自动创建 `RebarPredicate` 并存储到 `BlockEntry.predicate`
3. 保存时会显示检测到的 Rebar 方块数量

```kotlin
// BlockEntry 中的谓词会在序列化时自动保存
val entry = BlockEntry(
    position = Vector3i(0, 0, 0),
    blockData = block.blockData,
    predicate = RebarAdapter.createRebarPredicate(block)  // 自动检测
)

// effectivePredicate 属性提供统一的访问方式
val predicate = entry.effectivePredicate  // 返回 predicate 或 Predicates.strict(blockData)
```

### 自定义谓词

```kotlin
val customPredicate = object : Predicate {
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == Material.DIAMOND_BLOCK &&
               context.position.y > 0
    }

    override fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == Material.DIAMOND_BLOCK
    }

    override val previewBlockData: BlockData? = Bukkit.createBlockData(Material.DIAMOND_BLOCK)
    override val hint: String? = "钻石方块"
}
```

---

## 显示实体 (DisplayEntityData)

定义蓝图中的静态显示实体：

### 数据结构

```kotlin
enum class DisplayType {
    BLOCK,    // 方块显示
    ITEM      // 物品显示
}

data class DisplayEntityData(
    val position: Vector3i,           // 相对位置
    val entityType: DisplayType,      // 实体类型
    val rotation: Quaternionf,        // 旋转四元数
    val scale: Vector3f,              // 缩放
    val translation: Vector3f,        // 平移
    val itemStack: ItemStack? = null, // 物品（ITEM 类型）
    val blockData: BlockData? = null  // 方块数据（BLOCK 类型）
)
```

### 使用示例

```kotlin
import org.joml.Quaternionf
import org.joml.Vector3f

// 创建物品显示实体
val itemDisplay = DisplayEntityData(
    position = Vector3i(2, 1, 2),
    entityType = DisplayType.ITEM,
    rotation = Quaternionf(),
    scale = Vector3f(0.5f, 0.5f, 0.5f),
    translation = Vector3f(0f, 0f, 0f),
    itemStack = ItemStack(Material.DIAMOND)
)

// 创建方块显示实体
val blockDisplay = DisplayEntityData(
    position = Vector3i(3, 1, 2),
    entityType = DisplayType.BLOCK,
    rotation = Quaternionf(),
    scale = Vector3f(1f, 1f, 1f),
    translation = Vector3f(0f, 0f, 0f),
    blockData = Bukkit.createBlockData(Material.GOLD_BLOCK)
)

// 添加到蓝图
val blueprint = Blueprint.builder("with_display")
    .shape(myShape)
    .displayEntity(itemDisplay)
    .displayEntity(blockDisplay)
    .build()
```

---

## 事件系统

### StructureFormEvent - 结构形成

当所有方块正确放置、结构形成时触发：

```kotlin
class StructureFormEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val componentBlocks: Map<String, Block>   // slotId -> Block
) : Event()
```

### StructureBreakEvent - 结构损坏

当结构的关键方块被破坏时触发：

```kotlin
class StructureBreakEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val brokenBlock: Block,
    val isController: Boolean   // 是否为控制器方块
) : Event()
```

### BlueprintBlockPlaceEvent - 方块放置

当玩家在结构范围内放置方块时触发：

```kotlin
class BlueprintBlockPlaceEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val block: Block,
    val slotId: String?,      // 如果属于槽位
    val isCorrect: Boolean    // 是否符合要求
) : Event()
```

### BlueprintBlockBreakEvent - 方块破坏

当玩家在结构范围内破坏方块时触发：

```kotlin
class BlueprintBlockBreakEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val block: Block,
    val slotId: String?
) : Event()
```

### BlueprintLoadEvent / BlueprintUnloadEvent

蓝图加载/卸载事件：

```kotlin
class BlueprintLoadEvent(
    val blueprint: Blueprint,
    val source: String   // 来源路径
) : Event()

class BlueprintUnloadEvent(
    val structureId: String
) : Event()
```

### 监听事件示例

```kotlin
@EventHandler
fun onStructureForm(event: StructureFormEvent) {
    val controller = event.componentBlocks["controller"]
    val player = controller?.location?.world?.players?.firstOrNull()
    player?.sendMessage("§a结构 ${event.blueprint.id} 已形成！")
}

@EventHandler
fun onStructureBreak(event: StructureBreakEvent) {
    event.player?.sendMessage("§c结构已损坏！")
}

@EventHandler
fun onBlueprintLoad(event: BlueprintLoadEvent) {
    logger.info("蓝图已加载: ${event.blueprint.id} from ${event.source}")
}
```

---

## 面向 (Facing)

Facing 定义结构在世界中放置的方向：

```kotlin
enum class Facing(val blockFace: BlockFace) {
    SOUTH(BlockFace.SOUTH),
    WEST(BlockFace.WEST),
    NORTH(BlockFace.NORTH),
    EAST(BlockFace.EAST);

    val rotationSteps: Int get() = ordinal

    fun rotateClockwise(): Facing
    fun rotateCounterClockwise(): Facing
    fun opposite(): Facing

    companion object {
        fun fromBlockFace(face: BlockFace): Facing?
        fun fromYaw(yaw: Float): Facing
    }
}
```

### 使用示例

```kotlin
// 获取面向
val facing = Facing.NORTH
val fromYaw = Facing.fromYaw(player.location.yaw)
val fromBlockFace = Facing.fromBlockFace(BlockFace.EAST)

// 旋转
val clockwise = facing.rotateClockwise()      // NORTH -> EAST
val counterClockwise = facing.rotateCounterClockwise()  // NORTH -> WEST
val opposite = facing.opposite()              // NORTH -> SOUTH

// 获取旋转步数
val steps = facing.rotationSteps  // 0=NORTH, 1=EAST, 2=SOUTH, 3=WEST
```

---

## Vector3i

三维整数坐标向量：

```kotlin
data class Vector3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: Vector3i): Vector3i
    operator fun minus(other: Vector3i): Vector3i
    operator fun unaryMinus(): Vector3i

    companion object {
        val ZERO = Vector3i(0, 0, 0)
        val UP = Vector3i(0, 1, 0)
        val DOWN = Vector3i(0, -1, 0)
        val NORTH = Vector3i(0, 0, -1)
        val SOUTH = Vector3i(0, 0, 1)
        val WEST = Vector3i(-1, 0, 0)
        val EAST = Vector3i(1, 0, 0)
    }
}
```

### 使用示例

```kotlin
val pos = Vector3i(5, 10, 3)
val offset = Vector3i(1, 0, 0)

// 运算
val newPos = pos + offset      // Vector3i(6, 10, 3)
val diff = pos - offset        // Vector3i(4, 10, 3)
val negated = -pos             // Vector3i(-5, -10, -3)

// 常量
val origin = Vector3i.ZERO
val above = pos + Vector3i.UP
```

---

## 示例代码

### 完整示例：创建并注册蓝图

```kotlin
class MyPlugin : JavaPlugin() {

    override fun onEnable() {
        // 方式一：使用 DSL
        registerMonolithStructure("simple_furnace") {
            size(3, 4, 3)
            center(1, 0, 1)

            // 底层
            block(0, 0, 0, Material.FURNACE)
            block(1, 0, 0, Material.FURNACE)
            block(2, 0, 0, Material.FURNACE)
            block(0, 0, 1, Material.FURNACE)
            block(1, 0, 1, Material.FURNACE)
            block(2, 0, 1, Material.FURNACE)
            block(0, 0, 2, Material.FURNACE)
            block(1, 0, 2, Material.FURNACE)
            block(2, 0, 2, Material.FURNACE)

            // 中层
            for (x in 0..2) {
                for (z in 0..2) {
                    if (x == 1 && z == 1) {
                        air(x, 1, z)  // 中间留空
                    } else {
                        block(x, 1, z, Material.IRON_BLOCK)
                    }
                }
            }

            // 顶层
            block(0, 2, 0, Material.IRON_BLOCK)
            block(1, 2, 0, Material.IRON_BLOCK)
            block(2, 2, 0, Material.IRON_BLOCK)
            block(0, 2, 1, Material.IRON_BLOCK)
            block(1, 2, 1, Material.BLAST_FURNACE)  // 控制器
            block(2, 2, 1, Material.IRON_BLOCK)
            block(0, 2, 2, Material.IRON_BLOCK)
            block(1, 2, 2, Material.IRON_BLOCK)
            block(2, 2, 2, Material.IRON_BLOCK)

            // 槽位
            slot("controller", 1, 2, 1)
            slotBlock("input", 1, 1, 1, Material.HOPPER)

            // 元数据
            meta(
                name = "Simple Furnace",
                description = "A basic multi-block furnace"
            )
        }

        // 方式二：使用 Builder
        val blocks = listOf(
            BlockEntry(Vector3i(0, 0, 0), Bukkit.createBlockData(Material.STONE)),
            BlockEntry(Vector3i(1, 0, 0), Bukkit.createBlockData(Material.STONE)),
            BlockEntry(Vector3i(0, 1, 0), Bukkit.createBlockData(Material.DIAMOND_BLOCK))
        )
        val shape = Shape(blocks)

        val blueprint = Blueprint.builder("simple_pillar")
            .shape(shape)
            .displayName("Simple Pillar")
            .description("A simple stone pillar with diamond top")
            .controllerOffset(0, 1, 0)
            .build()

        MonolithAPI.getInstance().registry.register(blueprint)
    }
}
```

### 完整示例：从文件加载蓝图

```kotlin
fun loadBlueprintFromFile(plugin: JavaPlugin) {
    val api = MonolithAPI.getInstance()
    val io = api.io

    // 从 imports 目录加载
    val importDir = File(plugin.dataFolder, "imports")
    val rawFile = File(importDir, "my_machine.raw.mnb")

    if (rawFile.exists()) {
        val blueprint = io.loadRawMNB(rawFile)
        if (blueprint != null) {
            api.registry.register(blueprint)
            plugin.logger.info("已加载蓝图: ${blueprint.id}")
        }
    }
}
```

### 完整示例：事件监听

```kotlin
class StructureListener : Listener {

    @EventHandler
    fun onStructureForm(event: StructureFormEvent) {
        val blueprint = event.blueprint
        val location = event.controllerLocation

        // 获取控制器方块
        val controller = event.componentBlocks["controller"]

        // 发送消息给附近玩家
        location.world?.getNearbyPlayers(location, 10.0)?.forEach { player ->
            player.sendMessage("§a[MonolithLib] §e${blueprint.meta.displayName}§a 已完成建造！")
        }

        // 触发自定义逻辑
        when (blueprint.id) {
            "simple_furnace" -> {
                // 初始化熔炉逻辑
            }
            "advanced_machine" -> {
                // 初始化机器逻辑
            }
        }
    }

    @EventHandler
    fun onStructureBreak(event: StructureBreakEvent) {
        if (event.isController) {
            // 控制器被破坏，结构完全失效
            event.controllerLocation.world?.getNearbyPlayers(event.controllerLocation, 10.0)?.forEach { player ->
                player.sendMessage("§c[MonolithLib] 结构的核心已被破坏！")
            }
        }
    }
}
```

---

## 蓝图配置 (BlueprintConfig)

BlueprintConfig 定义蓝图的编译配置，用于从原始蓝图生成最终产品。

### 数据结构

```kotlin
data class BlueprintConfig(
    val id: String,                                        // 蓝图ID
    val version: String = "1.0",                           // 配置版本
    val controllerType: String = "rebar",                  // 控制器类型
    val controllerRebarKey: NamespacedKey? = null,         // 控制器 Rebar 键
    val controllerPosition: Vector3i? = null,              // 控制器位置覆盖
    val overrides: Map<Vector3i, OverrideEntry> = emptyMap(), // 逐位置覆盖
    val slots: Map<String, Vector3i> = emptyMap(),         // 槽位定义
    val customData: Map<String, Any> = emptyMap(),         // 自定义数据
    val metaName: String = "",                             // 显示名称
    val metaDescription: String = "",                      // 描述
    val metaAuthor: String = "",                           // 作者
    val scaffoldMaterials: Map<Material, Material> = emptyMap() // 脚手架材料映射
) {
    data class OverrideEntry(
        val type: String,              // "strict", "loose", "rebar"
        val material: Material? = null,
        val rebarKey: NamespacedKey? = null,
        val previewMaterial: Material? = null,
        val ignoreStates: Set<String> = emptySet()
    )
}
```

### BlueprintConfigLoader

加载和保存配置文件：

```kotlin
object BlueprintConfigLoader {
    fun load(file: File): BlueprintConfig?
    fun save(config: BlueprintConfig, file: File)
    fun generateDefault(id: String, blueprint: Blueprint, file: File, defaultControllerKey: String = "monolithlib:custom_controller")
}
```

### scaffoldMaterials 详解

`scaffoldMaterials` 是一个 `Map<Material, Material>`，定义了从组装阶段材料到脚手架阶段材料的映射：

```kotlin
val config = BlueprintConfig(
    id = "my_machine",
    scaffoldMaterials = mapOf(
        Material.IRON_BLOCK to Material.CONCRETE,       // 铁块 → 混凝土
        Material.GOLD_BLOCK to Material.CONCRETE_POWDER  // 金块 → 混凝土粉末
    )
)
```

编译时，BuiltMNBCompiler 会：
1. 遍历 assembled 形状中的每个方块
2. 查找其材料是否在 scaffoldMaterials 映射中
3. 如果找到映射，在脚手架阶段使用映射后的材料
4. 如果没有映射，脚手架阶段使用与组装阶段相同的方块

### YAML 配置示例

```yaml
id: my_machine
version: "1.0"

meta:
  name: "我的机器"
  description: "一台高级的多方块机器"
  author: "PlayerName"

controller:
  type: rebar
  rebar_key: "myplugin:machine_core"
  position: "2, 0, 2"

scaffold_materials:
  iron_block: concrete
  gold_block: concrete_powder
  diamond_block: concrete

overrides:
  "2, 0, 2":
    type: rebar
    key: "myplugin:machine_core"
    preview: blast_furnace

slots:
  input: "0, 1, 0"
  output: "4, 1, 0"

custom:
  tier: 2
  fuel_capacity: 1000
```

---

## 编译器 (BuiltMNBCompiler)

BuiltMNBCompiler 将原始蓝图和配置编译为最终产品蓝图。

### 接口

```kotlin
object BuiltMNBCompiler {
    fun compile(rawFile: File, configFile: File): Blueprint?
    fun compile(rawBlueprint: Blueprint, config: BlueprintConfig): Blueprint
}
```

### 编译流程

```
原始蓝图 (.raw.mnb) + 配置文件 (.yml)
        │
        ▼
  1. 加载原始蓝图
  2. 读取配置文件
  3. 应用 scaffoldMaterials 生成脚手架形状
  4. 应用 overrides 覆盖特定位置
  5. 合并元数据、槽位、自定义数据
  6. 绑定控制器 Rebar 键
        │
        ▼
  最终蓝图 (双阶段 Blueprint)
```

### 使用示例

```kotlin
// 从文件编译
val blueprint = BuiltMNBCompiler.compile(
    rawFile = File("imports/my_machine.raw.mnb"),
    configFile = File("blueprints/my_machine/my_machine.yml")
)

// 从对象编译
val rawBlueprint = BinaryFormat.load(File("my_machine.raw.mnb"))!!
val config = BlueprintConfig(
    id = "my_machine",
    scaffoldMaterials = mapOf(
        Material.IRON_BLOCK to Material.CONCRETE
    ),
    controllerRebarKey = NamespacedKey("myplugin", "machine_core")
)
val compiled = BuiltMNBCompiler.compile(rawBlueprint, config)
```

### 编译细节

#### 脚手架生成

编译器根据 `scaffoldMaterials` 映射自动生成脚手架阶段：

```kotlin
// 原始蓝图只有 assembled 形状
// 编译后自动生成 scaffold 形状
val compiled = BuiltMNBCompiler.compile(rawBlueprint, config)

// scaffold 形状中的 IRON_BLOCK 被替换为 CONCRETE
// assembled 形状保持不变
```

#### Override 处理

`overrides` 允许对特定位置进行精细控制：

```yaml
overrides:
  "2, 0, 2":
    type: rebar
    key: "myplugin:machine_core"
    preview: blast_furnace
  "0, 1, 0":
    type: loose
    material: hopper
    ignore_states:
      - "facing"
      - "enabled"
```

Override 类型：
- **strict/material**：严格匹配指定材料，脚手架和组装阶段都使用该材料
- **loose**：松散匹配，忽略指定属性变化
- **rebar**：Rebar 方块匹配，绑定到指定的 Rebar 组件

---

## BinaryFormat (二进制序列化)

BinaryFormat 负责蓝图的二进制序列化和反序列化。

### 接口

```kotlin
object BinaryFormat : StructureSerializer {
    val formatName: String   // "Monolith Binary"
    val fileExtension: String  // ".mnb"

    fun save(blueprint: Blueprint, file: File, formatVersion: Int, configHash: String)
    fun load(file: File): Blueprint?
    
    override fun serialize(blueprint: Blueprint, output: OutputStream)
    override fun deserialize(input: InputStream): Blueprint?
}
```

### 文件格式版本

| 版本 | 说明 |
|------|------|
| v1-2 | 旧格式，仅支持单阶段 Shape |
| v3 | 添加谓词系统、槽位、自定义数据 |
| v4 | 添加双阶段 Shape、显示实体序列化 |

### 向后兼容

BinaryFormat 支持读取旧版本文件：
- v1-v3 文件会自动转换为双阶段蓝图（SCAFFOLD 和 ASSEMBLED 使用相同 Shape）
- v4 文件支持独立的双阶段 Shape 和显示实体

### 序列化内容

v4 格式包含：
1. 魔数 + 版本号
2. 双阶段标志（hasDualStage）
3. Assembled Shape（方块列表 + 谓词）
4. Scaffold Shape（仅当 hasDualStage=true）
5. 元数据（名称、描述、控制器偏移）
6. 控制器 Rebar 键
7. 槽位映射
8. 自定义数据
9. 显示实体列表（位置、类型、旋转、缩放、平移、BlockData/ItemStack）

### 谓词序列化

BinaryFormat 支持以下谓词类型的序列化：

| 谓词类型 | 序列化内容 |
|----------|------------|
| StrictPredicate | 类型标识 + BlockData 字符串 |
| LoosePredicate | 类型标识 + Material 名称 + 预览 BlockData |
| RebarPredicate | 类型标识 + NamespacedKey + 预览 BlockData |
| AirPredicate | 类型标识 |
| AnyPredicate | 类型标识 |

**注意**：录制时检测到的 Rebar 方块会自动序列化为 RebarPredicate，加载时会正确恢复。
