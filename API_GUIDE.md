# MonolithLib API 开发者指南

## 目录

1. [快速开始](#快速开始)
2. [核心概念](#核心概念)
3. [MonolithAPI 主入口](#monolithapi-主入口)
4. [蓝图 (Blueprint)](#蓝图-blueprint)
5. [形状 (Shape)](#形状-shape)
6. [BlueprintDSL 编程式构建](#blueprintdsl-编程式构建)
7. [谓词系统 (Predicate)](#谓词系统-predicate)
8. [事件系统](#事件系统)
9. [面向 (Facing)](#面向-facing)
10. [示例代码](#示例代码)

---

## 快速开始

### 获取 API 实例

```kotlin
val api = MonolithAPI.getInstance()
```

### 注册一个蓝图

```kotlin
api.registry.registerBlueprint(myBlueprint)
```

---

## 核心概念

| 概念 | 说明 |
|------|------|
| **Blueprint** | 蓝图，定义一个多方块结构的完整信息（形状、元数据、槽位） |
| **Shape** | 形状，定义结构中方块的相对位置和类型 |
| **Predicate** | 谓词，定义方块匹配规则（严格匹配、松散匹配、Rebar等） |
| **Slot** | 槽位，结构中的特殊位置（如控制器位置） |
| **Facing** | 面向，结构在世界中放置的方向（东南西北） |

---

## MonolithAPI 主入口

`MonolithAPI` 是插件的核心入口点，提供以下功能：

### 获取实例

```kotlin
val api = MonolithAPI.getInstance()
```

### 接口概览

```kotlin
interface MonolithAPI {
    val registry: ShapeRegistry      // 蓝图注册表
    val io: IOFacade                  // 文件输入输出
    val preview: PreviewFacade        // 预览功能

    fun startValidation(player: Player, controllerLocation: Location, structureId: String, facing: Facing)
    fun stopValidation(player: Player)
    fun hasActiveValidation(player: Player): Boolean
    fun getMaterialStats(player: Player, structureId: String): MaterialStats?
    fun reloadStructures()
}
```

### ShapeRegistry - 蓝图注册表

管理所有已加载的蓝图：

```kotlin
interface ShapeRegistry {
    fun registerBlueprint(blueprint: Blueprint)
    fun getBlueprint(id: String): Blueprint?
    fun getShape(id: String): Shape?
    fun getAllBlueprints(): Map<String, Blueprint>
    fun getBlueprintsByControllerKey(key: NamespacedKey): List<Blueprint>
    fun contains(id: String): Boolean
    fun remove(id: String): Blueprint?
    fun clear()
    val size: Int
}
```

### IOFacade - 文件输入输出

加载各种格式的结构文件：

```kotlin
interface IOFacade {
    fun loadShape(file: File, format: String? = null): Shape?
    fun loadShapeRotated(file: File, facingRotationSteps: Int): Shape?
    fun getSupportedFormats(): List<String>      // ["litematic", "schem", "nbt", "mnb"]
    fun getSupportedExtensions(): Set<String>    // {"litematic", "schem", "nbt", "mnb"}
}
```

**支持格式：**
- `.litematic` - Litematica 模组格式
- `.schem` - Schematic 格式
- `.nbt` - NBT 结构格式
- `.mnb` - 自定义二进制格式

### PreviewFacade - 预览功能

在游戏中以半透明 Ghost 形式预览结构：

```kotlin
interface PreviewFacade {
    fun start(player: Player, blueprint: Blueprint, anchorLocation: Location, facing: Facing = Facing.NORTH): PreviewSession?
    fun start(player: Player, blueprintId: String, anchorLocation: Location, facing: Facing = Facing.NORTH): PreviewSession?
    fun stop(player: Player)
    fun stopAtLocation(location: Location)
    fun hasActive(player: Player): Boolean
    fun getSession(location: Location): PreviewSession?
    fun getPlayerSessions(player: Player): List<PreviewSession>
    fun setLayer(player: Player, layer: Int): Boolean
    fun nextLayer(player: Player): Boolean
    fun prevLayer(player: Player): Boolean
}
```

---

## 蓝图 (Blueprint)

Blueprint 是定义多方块结构的核心数据类：

```kotlin
class Blueprint(
    val id: String,                    // 唯一标识符
    val shape: Shape,                   // 结构形状
    val meta: BlueprintMeta = BlueprintMeta(),  // 元数据
    val slots: Map<String, Vector3i> = emptyMap(),  // 槽位映射
    val customData: Map<String, Any> = emptyMap(),   // 自定义数据
    val controllerRebarKey: NamespacedKey? = null   // 控制器 Rebar 键
) {
    val sizeX: Int get() = shape.boundingBox.width
    val sizeY: Int get() = shape.boundingBox.height
    val sizeZ: Int get() = shape.boundingBox.depth
    val blockCount: Int get() = shape.blocks.size

    fun getSlotPosition(slotId: String): Vector3i?
    fun getCustomData(key: String): Any?
    fun getCustomString(key: String): String?
    fun getCustomInt(key: String): Int?
    fun getCustomDouble(key: String): Double?
    fun getCustomBoolean(key: String): Boolean?

    companion object {
        fun builder(id: String): BlueprintBuilder = BlueprintBuilder(id)
    }
}

data class BlueprintMeta(
    val displayName: String = "",           // 显示名称
    val description: String = "",           // 描述
    val author: String = "",                // 作者
    val version: String = "1.0",            // 版本
    val controllerOffset: Vector3i = Vector3i.ZERO  // 控制器偏移
)
```

### 使用 Builder 构建蓝图

```kotlin
val blueprint = Blueprint.builder("my_structure")
    .shape(myShape)
    .displayName("My Structure")
    .description("A test structure")
    .author("Developer")
    .version("1.0")
    .controllerOffset(0, 1, 0)
    .slot("controller", 5, 0, 5)
    .customData("tier", 1)
    .build()
```

---

## 形状 (Shape)

Shape 定义结构中方块的位置和类型：

```kotlin
class Shape(
    val blocks: List<BlockEntry>,
    val boundingBox: BoundingBox
) {
    val blockCount: Int get() = blocks.size

    fun getBlockAt(x: Int, y: Int, z: Int): BlockEntry?
    fun getBlocksInLayer(y: Int): List<BlockEntry>
    fun getUniqueYLevels(): Set<Int>

    companion object {
        val EMPTY = Shape(emptyList(), BoundingBox(0, 0, 0, 0, 0, 0))
        fun builder(): ShapeBuilder = ShapeBuilder()
    }
}

data class BlockEntry(
    val position: Vector3i,      // 相对坐标
    val blockData: BlockData     // 方块状态
)

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
    val center: Vector3i get() = Vector3i((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
}
```

---

## BlueprintDSL 编程式构建

使用 DSL 语法以声明式方式构建蓝图：

```kotlin
val blueprint = buildBlueprint("my_blueprint") {
    // 定义尺寸
    size(10, 5, 10)

    // 定义控制器偏移（相对于结构中心）
    center(5, 0, 5)

    // 添加方块 - 严格匹配（blockData必须完全一致）
    block(0, 0, 0, Material.STONE)
    block(1, 0, 0, Bukkit.createBlockData("minecraft:diamond_block"))
    block(2, 0, 0, "minecraft:gold_block")

    // 添加方块 - 松散匹配（忽略部分属性变化）
    loose(3, 0, 0, Material.IRON_BLOCK)

    // 添加 Rebar 钢筋混凝土方块
    rebar(4, 0, 0, "my_mod:reinforced_concrete", Material.GRAY_CONCRETE)

    // 定义槽位
    slot("controller", 5, 0, 5)
    slotBlock("input", 0, 1, 0, Material.HOPPER)

    // 定义元数据
    meta(
        name = "My Structure",
        description = "A test multi-block structure",
        author = "Developer",
        version = "1.0"
    )
}
```

### DSL 方法参考

| 方法 | 说明 |
|------|------|
| `size(x, y, z)` | 定义结构尺寸 |
| `center(x, y, z)` | 设置控制器偏移 |
| `block(x, y, z, material)` | 添加严格匹配方块 |
| `block(x, y, z, blockData)` | 添加严格匹配方块 |
| `block(x, y, z, predicate)` | 添加自定义谓词方块 |
| `loose(x, y, z, material)` | 添加松散匹配方块 |
| `rebar(x, y, z, key, preview)` | 添加 Rebar 钢筋混凝土 |
| `air(x, y, z)` | 定义空气（无方块要求） |
| `any(x, y, z)` | 任意非空气方块 |
| `slot(slotId, x, y, z)` | 定义槽位 |
| `slotBlock(slotId, x, y, z, ...)` | 带槽位的方块 |
| `slotRebar(slotId, x, y, z, key, preview)` | 带槽位的 Rebar |
| `meta(name, desc, author, version)` | 设置元数据 |

### 注册到插件

```kotlin
// 在你的 JavaPlugin 中
registerMonolithStructure("my_id") {
    block(0, 0, 0, Material.STONE)
    // ...
}
```

---

## 谓词系统 (Predicate)

Predicate 定义方块匹配规则，用于验证结构建造是否正确。

### Predicate 接口

```kotlin
interface Predicate {
    fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean
    fun testMaterialOnly(blockData: BlockData, context: Predicate.PredicateContext): Boolean
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

| 类型 | 说明 | 预览显示 |
|------|------|----------|
| `Predicates.strict(blockData)` | 严格匹配所有属性 | 指定 blockData |
| `Predicates.loose(material)` | 仅匹配材质，忽略状态 | 指定材质 |
| `Predicates.rebar(key, preview)` | 匹配 Rebar 模组方块 | 预览材质 |
| `Predicates.air()` | 必须是空气 | 无 |
| `Predicates.any()` | 任意非空气方块 | 无 |

### 自定义谓词

```kotlin
val customPredicate = object : Predicate {
    override fun test(blockData: BlockData, context: Predicate.PredicateContext): Boolean {
        return blockData.material == Material.DIAMOND_BLOCK &&
               context.position.y > 0
    }

    override val previewBlockData: BlockData? = Bukkit.createBlockData(Material.DIAMOND_BLOCK)
    override val hint: String? = "钻石方块"
}
```

---

## 事件系统

### BlueprintFormEvent - 蓝图形成

当所有方块正确放置、结构形成时触发：

```kotlin
class BlueprintFormEvent(
    val blueprint: Blueprint,
    val controllerLocation: Location,
    val componentBlocks: Map<String, Block>   // slotId -> Block
) : Event()
```

### BlueprintBreakEvent - 蓝图损坏

当结构的关键方块被破坏时触发：

```kotlin
class BlueprintBreakEvent(
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
fun onBlueprintForm(event: BlueprintFormEvent) {
    val player = event.componentBlocks["controller"]?.location?.player
    player?.sendMessage("§a结构 ${event.blueprint.id} 已形成！")
}

@EventHandler
fun onBlueprintBreak(event: BlueprintBreakEvent) {
    event.player?.sendMessage("§c结构已损坏！")
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

    fun rotateClockwise(): Facing = entries[(ordinal + 1) % 4]
    fun rotateCounterClockwise(): Facing = entries[(ordinal + 3) % 4]
    fun opposite(): Facing = entries[(ordinal + 2) % 4]

    companion object {
        fun fromBlockFace(face: BlockFace): Facing?
        fun fromYaw(yaw: Float): Facing
    }
}
```

---

## Vector3i

三维整数坐标向量：

```kotlin
data class Vector3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: Vector3i) = Vector3i(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3i) = Vector3i(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus() = Vector3i(-x, -y, -z)

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

---

## 示例代码

### 完整示例：创建并注册蓝图

```kotlin
// 在你的插件主类中
class MyPlugin : JavaPlugin() {

    override fun onEnable() {
        // 注册蓝图
        registerMonolithStructure("simple_furnace") {
            size(3, 4, 3)
            center(1, 0, 1)

            // 底层
            block(0, 0, 0, Material.FURNACE)
            block(1, 0, 0, Material.FURNACE)
            block(2, 0, 0, Material.FURNACE)
            block(0, 0, 1, Material.FURNACE)
            block(1, 0, 1, Material.FURNACE)  // 控制器位置
            block(2, 0, 1, Material.FURNACE)
            block(0, 0, 2, Material.FURNACE)
            block(1, 0, 2, Material.FURNACE)
            block(2, 0, 2, Material.FURNACE)

            // 中层
            block(0, 1, 0, Material.AIR)
            block(1, 1, 0, Material.AIR)
            block(2, 1, 0, Material.AIR)
            // ... 更多方块

            slot("controller", 1, 0, 1)

            meta(
                name = "Simple Furnace",
                description = "A basic multi-block furnace",
                author = "Developer",
                version = "1.0"
            )
        }
    }
}
```

### 验证结构

```kotlin
val api = MonolithAPI.getInstance()

// 开始验证
api.startValidation(player, controllerLocation, "my_structure", Facing.NORTH)

// 检查是否有活跃验证
if (api.hasActiveValidation(player)) {
    // ...
}

// 停止验证
api.stopValidation(player)
```

### 预览结构

```kotlin
val api = MonolithAPI.getInstance()

// 开始预览
val session = api.preview.start(player, "my_structure", anchorLocation, Facing.NORTH)

// 分层查看
api.preview.setLayer(player, 1)

// 切换层级
api.preview.nextLayer(player)
api.preview.prevLayer(player)

// 停止预览
api.preview.stop(player)
```

### 监听结构形成

```kotlin
server.pluginManager.registerEvents(object : Listener {
    @EventHandler
    fun onForm(event: BlueprintFormEvent) {
        event.blueprint.logger.info("Structure ${event.blueprint.id} formed!")
    }

    @EventHandler
    fun onBreak(event: BlueprintBreakEvent) {
        event.blueprint.logger.warning("Structure ${event.blueprint.id} broken!")
    }
}, this)
```

---

## 权限

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `monolithlib.base` | 基础权限 | true |
| `monolithlib.admin` | 管理员权限 | op |
| `monolithlib.reload` | 重新加载结构 | op |
| `monolithlib.preview` | 使用预览功能 | true |
| `monolithlib.debug` | 调试功能 | op |
| `monolithlib.test` | 测试命令 | true |

---

## 命令

| 命令 | 说明 |
|------|------|
| `/monolith` 或 `/ml` | 主命令 |
| `/ml reload` | 重新加载所有蓝图 |
| `/ml list` | 列出已加载的蓝图 |
| `/ml info <id>` | 查看蓝图详情 |
| `/ml preview <id>` | 预览指定蓝图 |
| `/ml build <id>` | 构建指定蓝图 |
| `/mltest` | 测试命令 |
