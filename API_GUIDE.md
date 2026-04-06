# MonolithLib API 使用指南

MonolithLib 是一个强大的 Minecraft 多方块结构库，支持结构定义、预览、建造检测和 Rebar 集成。

## 目录

- [快速开始](#快速开始)
- [核心概念](#核心概念)
- [API 参考](#api-参考)
- [方块谓词系统](#方块谓词系统)
- [坐标变换系统](#坐标变换系统)
- [结构文件格式](#结构文件格式)
- [Rebar 集成](#rebar-集成)
- [完整示例](#完整示例)

---

## 快速开始

### 添加依赖

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("top.mc506lw:monolithlib:1.0.0")
}
```

### 获取 API 实例

```kotlin
val api = MonolithAPI.getInstance()
```

### 注册一个简单结构

```kotlin
val structure = buildStructure("my_furnace") {
    size(3, 3, 3)
    center(1, 0, 1)
    
    set(0, 0, 0, Predicates.strict(Material.BRICKS))
    set(1, 0, 0, Predicates.strict(Material.BRICKS))
    set(2, 0, 0, Predicates.strict(Material.BRICKS))
    
    set(0, 0, 1, Predicates.strict(Material.BRICKS))
    set(1, 0, 1, Predicates.air())
    set(2, 0, 1, Predicates.strict(Material.BRICKS))
    
    set(0, 0, 2, Predicates.strict(Material.BRICKS))
    set(1, 0, 2, Predicates.strict(Material.FURNACE))
    set(2, 0, 2, Predicates.strict(Material.BRICKS))
    
    slot("input", 1, 0, 1)
    slot("output", 1, 0, 2)
    
    meta(
        name = "简易熔炉",
        description = "一个简单的熔炉结构",
        author = "YourName"
    )
}

api.registerStructure(structure)
```

---

## 核心概念

### MonolithStructure

`MonolithStructure` 是核心数据类，包含：

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | String | 结构唯一标识符 |
| `sizeX/Y/Z` | Int | 结构尺寸 |
| `centerOffset` | Vector3i | 中心偏移（控制器位置） |
| `flattenedBlocks` | List<FlattenedBlock> | 扁平化方块列表 |
| `controllerRebarKey` | NamespacedKey? | Rebar 控制器键（可选） |
| `slots` | Map<String, Vector3i> | 功能槽位 |
| `customData` | Map<String, Any> | 自定义数据 |
| `meta` | StructureMeta | 元数据（名称、描述、作者等） |

### FlattenedBlock

```kotlin
data class FlattenedBlock(
    val relativePosition: Vector3i,    // 相对坐标
    val predicate: Predicate,           // 方块谓词
    val previewBlockData: BlockData?,   // 预览显示
    val hint: String?                   // 提示文本
)
```

### Facing（朝向）

```kotlin
enum class Facing(val rotationSteps: Int) {
    NORTH(0),   // 无旋转
    EAST(1),    // 顺时针 90°
    SOUTH(2),   // 180°
    WEST(3);    // 顺时针 270°
    
    companion object {
        fun fromYaw(yaw: Float): Facing
        fun fromBlockFace(face: BlockFace): Facing
    }
}
```

---

## API 参考

### MonolithAPI

主要 API 入口，提供所有核心功能。

#### 结构注册

```kotlin
interface MonolithAPI {
    fun registerStructure(structure: MonolithStructure)
    fun unregisterStructure(id: String): Boolean
    fun getStructure(id: String): MonolithStructure?
    fun getAllStructures(): Map<String, MonolithStructure>
}
```

#### 预览功能

```kotlin
interface MonolithAPI {
    fun startPreview(
        player: Player,
        controllerLocation: Location,
        structureId: String,
        facing: Facing = Facing.NORTH
    ): PreviewSession?
    
    fun stopPreview(player: Player)
    fun getPlayerPreviewSessions(player: Player): List<PreviewSession>
    
    fun nextPreviewLayer(player: Player): Boolean
    fun prevPreviewLayer(player: Player): Boolean
    fun setPreviewLayer(player: Player, layer: Int): Boolean
}
```

#### 结构检测

```kotlin
interface MonolithAPI {
    fun checkStructure(
        controllerLocation: Location,
        structureId: String,
        facing: Facing = Facing.NORTH
    ): StructureCheckResult
    
    fun findMatchingStructures(
        controllerLocation: Location,
        facing: Facing = Facing.NORTH
    ): List<Pair<MonolithStructure, StructureCheckResult>>
}
```

#### 建造功能

```kotlin
interface MonolithAPI {
    fun startBuild(
        player: Player,
        controllerLocation: Location,
        structureId: String,
        facing: Facing = Facing.NORTH
    ): BuildSession?
    
    fun cancelBuild(player: Player)
    fun getBuildSession(player: Player): BuildSession?
}
```

### StructureBuilderDSL

使用 DSL 风格构建结构：

```kotlin
val structure = buildStructure("my_structure") {
    size(5, 3, 5)
    center(2, 0, 2)
    
    controllerRebar(NamespacedKey("myplugin", "controller"))
    
    layer(0) {
        fill(0, 0, 0, 4, 0, 4, Predicates.strict(Material.STONE))
    }
    
    layer(1) {
        outline(0, 0, 0, 4, 0, 4, Predicates.strict(Material.STONE))
    }
    
    slot("input", 1, 1, 2)
    slot("output", 3, 1, 2)
    
    customData("processing_time", 200)
    customData("fuel_consumption", 1.5)
    
    meta(
        name = "我的机器",
        description = "一个自定义机器",
        author = "Author",
        version = "1.0"
    )
}
```

### MonolithStructure.Builder

编程式构建器：

```kotlin
val structure = MonolithStructure.Builder("my_structure")
    .size(5, 3, 5)
    .center(Vector3i(2, 0, 2))
    .controllerRebar(NamespacedKey("myplugin", "controller"))
    .set(0, 0, 0, Predicates.strict(Material.STONE))
    .set(1, 0, 0, Predicates.strict(Material.STONE))
    .slot("input", Vector3i(1, 1, 2))
    .customData("key", "value")
    .meta("名称", "描述", "作者", "版本")
    .build()
```

---

## 方块谓词系统

谓词系统是 MonolithLib 的核心，用于定义方块匹配规则。

### Predicate 接口

```kotlin
interface Predicate {
    fun matches(block: Block): Boolean
    val previewBlockData: BlockData?
    val hint: String?
}
```

### 内置谓词类型

#### AirPredicate

匹配空气方块：

```kotlin
Predicates.air()
```

#### AnyPredicate

匹配任意非空气方块：

```kotlin
Predicates.any()
```

#### StrictPredicate

严格匹配方块类型和状态：

```kotlin
Predicates.strict(Material.STONE)
Predicates.strict(Bukkit.createBlockData("minecraft:furnace[facing=north]"))
```

#### LoosePredicate

宽松匹配，忽略指定状态：

```kotlin
Predicates.loose(Material.FURNACE, "facing", "lit")
Predicates.loose(
    Bukkit.createBlockData("minecraft:furnace[facing=north]"),
    setOf("facing", "lit")
)
```

#### RebarPredicate

匹配 Rebar 方块：

```kotlin
Predicates.rebar(
    NamespacedKey("myplugin", "machine_core"),
    Material.BLAST_FURNACE
)
```

### Predicates 工厂方法

```kotlin
object Predicates {
    fun air(): AirPredicate
    fun any(): AnyPredicate
    fun strict(material: Material): StrictPredicate
    fun strict(blockData: BlockData): StrictPredicate
    fun loose(material: Material, vararg ignoredStates: String): LoosePredicate
    fun loose(blockData: BlockData, ignoredStates: Set<String>): LoosePredicate
    fun rebar(key: NamespacedKey, previewMaterial: Material): RebarPredicate
    fun rebar(key: NamespacedKey, previewBlockData: BlockData): RebarPredicate
}
```

### 自定义谓词

实现 `Predicate` 接口创建自定义谓词：

```kotlin
class TagPredicate(
    private val tag: Tag<Material>,
    override val previewBlockData: BlockData? = null
) : Predicate {
    
    override fun matches(block: Block): Boolean {
        return tag.isTagged(block.type)
    }
    
    override val hint: String? = "需要 ${tag.key} 标签的方块"
}

val structure = buildStructure("tag_example") {
    set(0, 0, 0, TagPredicate(Tag.LOGS, Material.OAK_LOG.createBlockData()))
}
```

---

## 坐标变换系统

### CoordinateTransform

处理结构旋转和镜像：

```kotlin
val transform = CoordinateTransform(
    facing = Facing.EAST,
    isFlipped = false
)

val relativePos = Vector3i(1, 0, 2)
val worldPos = transform.toWorldPosition(
    controllerPos = Vector3i(100, 64, 200),
    relativePos = relativePos,
    centerOffset = Vector3i(2, 0, 2)
)

val backToRelative = transform.toRelativePosition(
    worldPos = worldPos,
    controllerPos = Vector3i(100, 64, 200),
    centerOffset = Vector3i(2, 0, 2)
)
```

### Facing 工具方法

```kotlin
val facing = Facing.fromYaw(player.location.yaw)
val facing = Facing.fromBlockFace(BlockFace.NORTH)

val rotated = facing.rotateClockwise()
val opposite = facing.opposite()
```

---

## 结构文件格式

### Monolith Binary (.mnb)

高效的二进制格式，支持：
- 完整结构数据
- 所有谓词类型
- 槽位和自定义数据
- 版本控制

```kotlin
BinaryFormat.save(structure, File("structure.mnb"), formatVersion = 3, configHash = "")
val loaded = BinaryFormat.load(File("structure.mnb"))
```

### 导入外部格式

支持导入：
- `.schem` (Sponge Schematic)
- `.litematic` (Litematica)
- `.nbt` (结构方块 NBT)

```kotlin
val structure = SchemFormat.load(File("structure.schem"))
val structure = LitematicFormat.load(File("structure.litematic"))
val structure = NbtFormat.load(File("structure.nbt"))
```

### YAML 配置

蓝图配置文件示例：

```yaml
id: my_machine
name: "我的机器"
description: "一个自定义机器"
author: "Author"
version: "1.0"

controller:
  type: rebar
  key: "myplugin:machine_controller"

size:
  x: 5
  y: 3
  z: 5

center:
  x: 2
  y: 0
  z: 2

slots:
  input: { x: 1, y: 1, z: 2 }
  output: { x: 3, y: 1, z: 2 }
  fuel: { x: 2, y: 1, z: 0 }

custom_data:
  processing_time: 200
  fuel_consumption: 1.5
  recipes:
    - "iron_ingot"
    - "gold_ingot"

blocks:
  - pos: { x: 0, y: 0, z: 0 }
    type: strict
    material: STONE
  - pos: { x: 1, y: 0, z: 0 }
    type: loose
    material: FURNACE
    ignored_states: ["facing", "lit"]
  - pos: { x: 2, y: 0, z: 0 }
    type: rebar
    key: "myplugin:component"
    preview: BLAST_FURNACE
```

---

## Rebar 集成

### RebarAdapter

```kotlin
object RebarAdapter {
    fun isRebarBlock(block: Block): Boolean
    fun isRebarBlock(block: Block, key: NamespacedKey): Boolean
    fun getRebarBlock(block: Block): RebarBlock?
    fun getRebarBlockKey(block: Block): NamespacedKey?
    fun createRebarPredicate(block: Block): Predicate?
}
```

### 控制器绑定

将 Rebar 方块绑定为结构控制器：

```kotlin
val structure = buildStructure("rebar_machine") {
    controllerRebar(NamespacedKey("myplugin", "machine_controller"))
    
    set(0, 0, 0, Predicates.rebar(
        NamespacedKey("myplugin", "component_a"),
        Material.IRON_BLOCK
    ))
    set(1, 0, 0, Predicates.rebar(
        NamespacedKey("myplugin", "component_b"),
        Material.GOLD_BLOCK
    ))
}
```

### 自动检测

放置绑定的 Rebar 控制器时自动触发预览：

```kotlin
@EventHandler
fun onPlace(event: BlockPlaceEvent) {
    val block = event.block
    
    if (RebarAdapter.isRebarBlock(block)) {
        val key = RebarAdapter.getRebarBlockKey(block) ?: return
        
        val structures = StructureRegistry.getInstance()
            .getByControllerKey(key)
        
        if (structures.isNotEmpty()) {
            val structure = structures.first()
            MonolithAPI.getInstance().startPreview(
                event.player,
                block.location,
                structure.id
            )
        }
    }
}
```

---

## 完整示例

### 示例 1: 简单熔炉

```kotlin
class SimpleFurnace {
    
    fun register() {
        val structure = buildStructure("simple_furnace") {
            size(3, 2, 3)
            center(1, 0, 1)
            
            layer(0) {
                fill(0, 0, 0, 2, 0, 2, Predicates.strict(Material.COBBLESTONE))
            }
            
            layer(1) {
                outline(0, 0, 0, 2, 0, 2, Predicates.strict(Material.COBBLESTONE))
                set(1, 0, 1, Predicates.air())
            }
            
            set(1, 1, 1, Predicates.loose(Material.FURNACE, "facing"))
            
            slot("fuel", 0, 1, 1)
            slot("input", 2, 1, 1)
            slot("output", 1, 1, 0)
            
            meta(
                name = "简易熔炉",
                description = "一个简单的熔炉多方块结构",
                author = "MonolithLib"
            )
        }
        
        MonolithAPI.getInstance().registerStructure(structure)
    }
}
```

### 示例 2: Rebar 机器

```kotlin
class RebarMachine {
    
    fun register() {
        val structure = buildStructure("rebar_machine") {
            size(5, 3, 5)
            center(2, 0, 2)
            
            controllerRebar(NamespacedKey("myplugin", "machine_controller"))
            
            layer(0) {
                fill(0, 0, 0, 4, 0, 4, Predicates.strict(Material.IRON_BLOCK))
            }
            
            layer(1) {
                outline(0, 0, 0, 4, 0, 4, Predicates.strict(Material.IRON_BLOCK))
                set(2, 0, 2, Predicates.air())
            }
            
            layer(2) {
                fill(0, 0, 0, 4, 0, 4, Predicates.strict(Material.IRON_BLOCK))
                set(2, 0, 2, Predicates.air())
            }
            
            set(0, 1, 2, Predicates.rebar(
                NamespacedKey("myplugin", "input_hatch"),
                Material.HOPPER
            ))
            set(4, 1, 2, Predicates.rebar(
                NamespacedKey("myplugin", "output_hatch"),
                Material.HOPPER
            ))
            set(2, 1, 0, Predicates.rebar(
                NamespacedKey("myplugin", "energy_port"),
                Material.LIGHTNING_ROD
            ))
            
            slot("input", 0, 1, 2)
            slot("output", 4, 1, 2)
            slot("energy", 2, 1, 0)
            
            customData("energy_capacity", 10000)
            customData("processing_speed", 1.5)
            
            meta(
                name = "工业机器",
                description = "一个使用 Rebar 组件的工业机器",
                author = "MonolithLib",
                version = "1.0"
            )
        }
        
        MonolithAPI.getInstance().registerStructure(structure)
    }
}
```

### 示例 3: 结构检测与回调

```kotlin
class StructureManager : Listener {
    
    private val activeStructures = mutableMapOf<Location, ActiveStructure>()
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val block = event.block
        
        val api = MonolithAPI.getInstance()
        
        for (facing in Facing.entries) {
            val matches = api.findMatchingStructures(block.location, facing)
            
            for ((structure, result) in matches) {
                if (result.isComplete) {
                    onStructureFormed(player, block.location, structure, facing)
                    return
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val active = activeStructures[block.location]
        
        if (active != null) {
            onStructureBroken(event.player, active)
        }
    }
    
    private fun onStructureFormed(
        player: Player,
        location: Location,
        structure: MonolithStructure,
        facing: Facing
    ) {
        player.sendMessage("§a结构 ${structure.id} 已形成!")
        
        val active = ActiveStructure(
            structure = structure,
            location = location,
            facing = facing,
            createdAt = System.currentTimeMillis()
        )
        
        activeStructures[location] = active
        
        val event = StructureFormEvent(active)
        Bukkit.getPluginManager().callEvent(event)
    }
    
    private fun onStructureBroken(player: Player, active: ActiveStructure) {
        player.sendMessage("§c结构 ${active.structure.id} 已损坏!")
        
        activeStructures.remove(active.location)
        
        val event = StructureBreakEvent(active)
        Bukkit.getPluginManager().callEvent(event)
    }
}

data class ActiveStructure(
    val structure: MonolithStructure,
    val location: Location,
    val facing: Facing,
    val createdAt: Long
)

class StructureFormEvent(val structure: ActiveStructure) : Event() {
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = handlers
    }
    override fun getHandlers() = handlers
}

class StructureBreakEvent(val structure: ActiveStructure) : Event() {
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = handlers
    }
    override fun getHandlers() = handlers
}
```

---

## 翻译系统

MonolithLib 使用 Rebar 的翻译系统，支持多语言。

### 翻译文件结构

```
src/main/resources/lang/
├── en.yml
└── zh.yml
```

### 翻译键格式

```yaml
addon: "MonolithLib"

item:
  test_controller:
    name: "Test Controller"
    lore: "A test controller block"

message:
  preview:
    started: "<green>Preview started: %structure_id%</green>"
    stopped: "<green>Stopped %count% preview(s)</green>"
```

### 使用翻译

```kotlin
import top.mc506lw.monolith.common.I18n

player.sendMessage(I18n.Message.Preview.started("my_structure", "NORTH"))
player.sendMessage(I18n.Message.Structure.notFound("unknown_structure"))
```

---

## 性能优化建议

1. **使用 FlattenedBlock**: 避免使用三维数组存储方块，使用扁平化列表
2. **缓存结构**: 结构注册后会被缓存，避免重复解析
3. **异步加载**: 大型结构文件应在异步线程加载
4. **按需检测**: 只在需要时检测结构，避免每 tick 检测

---

## 常见问题

### Q: 如何处理大型结构？

A: 使用 `.mnb` 二进制格式，加载速度更快。对于特别大的结构，考虑分块加载。

### Q: 如何支持自定义方块？

A: 使用 `RebarPredicate` 或实现自定义 `Predicate`。

### Q: 结构检测失败怎么办？

A: 检查：
1. 中心偏移是否正确
2. 谓词是否匹配
3. Facing 方向是否正确

---

## 版本兼容性

| MonolithLib | Minecraft | Rebar |
|-------------|-----------|-------|
| 1.0.0       | 1.21.11    | 最新  |

---

## 联系与支持

- GitHub Issues: [提交问题](https://github.com/mc506lw/MonolithLib/issues)
