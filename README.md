<p align="center">
  <img src="./readme-header.png" alt="MonolithLib Banner" width="100%">
</p>

<div align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-green?style=for-the-badge&logo=minecraft" alt="Minecraft">
  <img src="https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License">
</div>

<p align="center">
  <a href="https://github.com/pylonmc/rebar" target="_blank">
    <img src="https://img.shields.io/badge/dependency-Rebar%200.36.2-9370DB?style=flat-square" alt="Rebar">
  </a>
</p>

<div align="center">
  <h1>MonolithLib</h1>
  <p><strong>强大的 Minecraft 多方块结构库</strong></p>
  <p>支持结构定义、预览、建造检测与 Rebar 集成</p>
</div>

<div align="center">
  <a href="#-特性"><strong>特性</strong></a> ·
  <a href="#-快速开始"><strong>快速开始</strong></a> ·
  <a href="./API_GUIDE.md"><strong>API 文档</strong></a> ·
  <a href="#-示例"><strong>示例</strong></a> ·
  <a href="#-贡献"><strong>贡献</strong></a>
</div>

<br/>

<div align="center">
  <a href="https://github.com/mc506lw/MonolithLib/issues">
    <img src="https://img.shields.io/github/issues/mc506lw/MonolithLib?color=red" alt="Issues">
  </a>
  <a href="https://github.com/mc506lw/MonolithLib/pulls">
    <img src="https://img.shields.io/github/issues-pr/mc506lw/MonolithLib?color=blue" alt="Pull Requests">
  </a>
  <a href="https://github.com/mc506lw/MonolithLib/stargazers">
    <img src="https://img.shields.io/github/stars/mc506lw/MonolithLib?color=yellow" alt="Stars">
  </a>
  <a href="https://github.com/mc506lw/MonolithLib/releases">
    <img src="https://img.shields.io/github/v/release/mc506lw/MonolithLib?color=green" alt="Release">
  </a>
</div>

---

## ✨ 特性

<div align="center">
  <table>
    <tr>
      <td align="center">
        <h3>🏗️ 结构定义</h3>
        <p>使用 DSL 或编程式 API 定义多方块结构</p>
      </td>
      <td align="center">
        <h3>👁️ 实时预览</h3>
        <p>幽灵方块预览，逐层引导建造</p>
      </td>
      <td align="center">
        <h3>🔍 智能检测</h3>
        <p>自动检测结构形成与损坏</p>
      </td>
    </tr>
    <tr>
      <td align="center">
        <h3>🔄 方向支持</h3>
        <p>支持四个方向的旋转与镜像</p>
      </td>
      <td align="center">
        <h3>📦 多格式支持</h3>
        <p>支持 .mnb/.schem/.litematic/.nbt</p>
      </td>
      <td align="center">
        <h3>🔌 Rebar 集成</h3>
        <p>与 Rebar 自定义方块无缝集成</p>
      </td>
    </tr>
  </table>
</div>

### 🎯 核心功能

- **灵活的方块谓词系统**：严格匹配、宽松匹配、Rebar 方块匹配
- **高效的二进制格式**：自定义 .mnb 格式，快速加载
- **多语言支持**：内置中英文翻译系统
- **完整的事件系统**：结构形成/损坏事件回调

---

## 🚀 快速开始

### 添加依赖

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("top.mc506lw:monolithlib:1.0.0")
}
```

**Maven:**

```xml
<dependency>
    <groupId>top.mc506lw</groupId>
    <artifactId>monolithlib</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### 基础用法

```kotlin
import top.mc506lw.monolith.api.MonolithAPI
import top.mc506lw.monolith.api.dsl.buildStructure
import top.mc506lw.monolith.core.predicate.Predicates

class MyPlugin : JavaPlugin() {
    
    override fun onEnable() {
        val structure = buildStructure("my_furnace") {
            size(3, 3, 3)
            center(1, 0, 1)
            
            layer(0) {
                fill(0, 0, 0, 2, 0, 2, Predicates.strict(Material.BRICKS))
            }
            
            layer(1) {
                outline(0, 0, 0, 2, 0, 2, Predicates.strict(Material.BRICKS))
                set(1, 0, 1, Predicates.air())
            }
            
            set(1, 1, 1, Predicates.loose(Material.FURNACE, "facing"))
            
            slot("input", 0, 1, 1)
            slot("output", 2, 1, 1)
            
            meta(
                name = "简易熔炉",
                description = "一个简单的熔炉结构",
                author = "YourName"
            )
        }
        
        MonolithAPI.getInstance().registerStructure(structure)
    }
}
```

---

## 📝 示例

### 结构检测与事件

```kotlin
class StructureManager : Listener {
    
    private val activeStructures = mutableMapOf<Location, ActiveStructure>()
    
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val api = MonolithAPI.getInstance()
        
        for (facing in Facing.entries) {
            val matches = api.findMatchingStructures(event.block.location, facing)
            
            for ((structure, result) in matches) {
                if (result.isComplete) {
                    onStructureFormed(event.player, event.block.location, structure, facing)
                    return
                }
            }
        }
    }
    
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        activeStructures[event.block.location]?.let { active ->
            onStructureBroken(event.player, active)
        }
    }
    
    private fun onStructureFormed(player: Player, location: Location, structure: MonolithStructure, facing: Facing) {
        player.sendMessage("§a结构 ${structure.id} 已形成!")
        activeStructures[location] = ActiveStructure(structure, location, facing)
    }
    
    private fun onStructureBroken(player: Player, active: ActiveStructure) {
        player.sendMessage("§c结构 ${active.structure.id} 已损坏!")
        activeStructures.remove(active.location)
    }
}
```

### 导入外部结构文件

```kotlin
// 从 Sponge Schematic 导入
val structure = SchemFormat.load(File("structure.schem"))

// 从 Litematica 导入
val structure = LitematicFormat.load(File("structure.litematic"))

// 从 NBT 导入
val structure = NbtFormat.load(File("structure.nbt"))

// 保存为高效二进制格式
BinaryFormat.save(structure, File("structure.mnb"), formatVersion = 3, configHash = "")
```

---

## 🔌 Rebar 集成

MonolithLib 与 [Rebar](https://github.com/pylonmc/rebar) 无缝集成，支持自定义方块作为结构组件。

### 控制器绑定

```kotlin
buildStructure("rebar_machine") {
    // 将 Rebar 方块绑定为控制器
    controllerRebar(NamespacedKey("myplugin", "machine_controller"))
    
    // 使用 Rebar 组件
    set(0, 0, 0, Predicates.rebar(
        NamespacedKey("myplugin", "component_a"),
        Material.IRON_BLOCK
    ))
}
```

### 自动预览

放置绑定的 Rebar 控制器时自动触发结构预览：

```kotlin
@EventHandler
fun onPlace(event: BlockPlaceEvent) {
    if (RebarAdapter.isRebarBlock(event.block)) {
        val key = RebarAdapter.getRebarBlockKey(event.block) ?: return
        val structures = StructureRegistry.getInstance().getByControllerKey(key)
        
        if (structures.isNotEmpty()) {
            MonolithAPI.getInstance().startPreview(
                event.player,
                event.block.location,
                structures.first().id
            )
        }
    }
}
```

---

## 📁 项目结构

```
MonolithLib/
├── src/main/kotlin/top/mc506lw/
│   ├── monolith/
│   │   ├── api/                    # 公共 API
│   │   │   ├── MonolithAPI.kt      # 主 API 接口
│   │   │   └── dsl/                # DSL 构建器
│   │   ├── common/                 # 公共工具
│   │   │   ├── Constants.kt        # 常量定义
│   │   │   └── I18n.kt             # 翻译系统
│   │   ├── core/                   # 核心功能
│   │   │   ├── math/               # 数学工具
│   │   │   ├── predicate/          # 方块谓词
│   │   │   ├── structure/          # 结构定义
│   │   │   └── transform/          # 坐标变换
│   │   ├── feature/                # 功能模块
│   │   │   ├── io/                 # 文件 I/O
│   │   │   ├── preview/            # 预览系统
│   │   │   └── rebar/              # Rebar 集成
│   │   └── test/                   # 测试模块
│   └── rebar/
│       └── MonolithLib.kt          # 插件主类
└── src/main/resources/
    ├── lang/                       # 翻译文件
    │   ├── en.yml
    │   └── zh.yml
    └── plugin.yml
```

---

## 🛠️ 构建

```bash
# 克隆仓库
git clone https://github.com/mc506lw/MonolithLib.git
cd MonolithLib

# 构建
./gradlew build

# 输出文件位于 build/libs/
```

---

## 📋 路线图

- [ ] 1

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](./LICENSE) 文件。

---

## 💬 支持

- **问题反馈**: [GitHub Issues](https://github.com/mc506lw/MonolithLib/issues)
---

<div align="center">
  <p>如果这个项目对你有帮助，请给一个 ⭐️ 支持一下！</p>
</div>
