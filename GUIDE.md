# MonolithLib 使用指南

> 高性能巨构多方块结构前置库 — 基于 Rebar 框架

---

## 目录

- [一、玩家指南](#一玩家指南)
  - [1.1 快速上手](#11-快速上手)
  - [1.2 预览结构](#12-预览结构)
  - [1.3 放置建造](#13-放置建造)
  - [1.4 辅助建造模式](#14-辅助建造模式)
  - [1.5 蓝图物品](#15-蓝图物品)
  - [1.6 命令速查](#16-命令速查)
- [二、开发者指南](#二开发者指南)
  - [2.1 核心概念](#21-核心概念)
  - [2.2 录制蓝图](#22-录制蓝图)
  - [2.3 分阶段录制](#23-分阶段录制)
  - [2.4 蓝图文件管理](#24-蓝图文件管理)
  - [2.5 配置与编译](#25-配置与编译)
  - [2.6 自定义 Rebar 集成](#26-自定义-rebar-集成)
  - [2.7 API 参考](#27-api-参考)

---

## 一、玩家指南

### 1.1 快速上手

MonolithLib 的核心工作流：

```
获取蓝图物品 → 放置工地 → 逐层建造 → 完成激活
```

**前提条件**：服务器已安装 **Rebar** 插件，MonolithLib 作为其依赖运行。

所有玩家命令通过 `/ml` 或 `/monolith` 执行。

---

### 1.2 预览结构

在放置前可以预览蓝图的三维形态：

```
/ml preview          # 预览准星指向的 Rebar 控制器对应的蓝图
/ml preview cancel   # 取消当前预览
```

**使用方式**：执行 `/ml preview` 后，准星瞄准一个已注册的 **Rebar 控制器方块**，即可在当前位置以你面向的方向预览该蓝图。

**分层查看**：

```
/mltest preview <蓝图ID> [north/south/east/west]   # 启动预览
/mltest layer up        # 上一层
/mltest layer down      # 下一层
/mltest layer <数字>    # 跳转到指定层
/mltest stop            # 停止预览
```

---

### 1.3 放置建造

#### 获取蓝图物品

```
/ml blueprint <蓝图ID>
```

获得一个蓝图物品，右键空气方块即可在该位置创建一个**建造工地**。

#### 工地状态

每个工地经历以下阶段：

```
SCAFFOLD（脚手架阶段）
    │
    ├─ 玩家按幽灵提示逐个放置方块
    ├─ 只验证材料类型是否匹配（宽松验证）
    │
    ▼
ASSEMBLED（成型阶段 / 魔法时刻）
    │
    ├─ 所有方块放置完成时自动触发
    ├─ 自动修正所有方块的 BlockData（朝向、状态等）
    ├─ 生成静态显示实体
    ├─ 绑定 Rebar 组件逻辑
    └─ 结构正式激活
```

#### 直接建造

```
/ml build             # 对准 Rebar 控制器直接开始建造
/ml build cancel      # 取消当前建造任务
```

---

### 1.4 辅助建造模式

MonolithLib 提供两种半自动建造辅助模式：

#### EasyBuild（轻松放置）

```
/ml litematica easybuild
```

- 进入后对着**幽灵方块**右键即可自动放置匹配的方块
- 创造模式下无需任何材料
- 离开工地范围 1 分钟后自动关闭

#### Printer（自动打印）

```
/ml litematica printer
```

- 自动放置周身 **4 格范围内**的所有幽灵方块
- 创造模式下无需任何材料
- 离开工地范围 1 分钟后自动关闭

---

### 1.5 蓝图物品

通过 `/ml blueprint <ID>` 获得的蓝图物品是一个物理道具：
- **右键空气方块** → 在该位置创建工地
- 可放入背包、箱子交易给其他玩家

---

### 1.6 命令速查

| 命令 | 说明 |
|------|------|
| `/ml preview` | 预览结构（对准控制器） |
| `/ml preview cancel` | 取消预览 |
| `/ml build` | 开始建造（对准控制器） |
| `/ml build cancel` | 取消建造 |
| `/ml blueprint <ID>` | 获取蓝图物品 |
| `/ml list` | 列出所有蓝图 |
| `/ml info [ID]` | 查看蓝图详情 |
| `/ml reload` | 重载所有蓝图 |
| `/ml litematica easybuild` | 切换轻松放置模式 |
| `/ml litematica printer` | 切换自动打印模式 |

**权限节点**：

| 权限 | 默认 | 说明 |
|------|------|------|
| `monolithlib.base` | 所有人 | 基础权限 |
| `monolithlib.admin` | OP | 管理员（包含 reload） |
| `monolithlib.preview` | 所有人 | 预览功能 |
| `monolithlib.build` | 所有人 | 建造功能 |
| `monolithlib.blueprint` | 所有人 | 蓝图物品 |
| `monolith.easybuild` | 所有人 | 轻松放置模式 |
| `monolith.printer` | 所有人 | 自动打印模式 |

---

## 二、开发者指南

### 2.1 核心概念

MonolithLib 遵循 **"物理与逻辑分离"** 的设计原则：

| 层次 | 职责 | 实现 |
|------|------|------|
| **物理层** (MonolithLib) | 形状定义、方块状态、显示实体、工地管理 | 本插件 |
| **逻辑层** (Rebar) | 组件行为、库存、自动化、GUI | Rebar 框架 |

关键设计原则：
- **验改分离**：建造阶段只验证材料类型；成型瞬间（Magic Moment）自动修正 BlockData
- **形态分裂**：蓝图包含 `SCAFFOLD` 和 `ASSEMBLED` 两个阶段的形状定义
- **动静分离**：MonolithLib 管理静态显示实体，Rebar 管理动态实体

---

### 2.2 录制蓝图

#### 步骤一：获取选区魔杖

```
/ml wand
```

获得一根 **烈焰棒（Blaze Rod）** 形式的选区工具。手持魔杖时：
- **左键方块** → 设置选区第一个点（Pos1）
- **右键方块** → 设置选区第二个点（Pos2）

设置完成后会显示粒子效果标记选区边界。

#### 步骤二：保存为原始蓝图

```
/ml save <文件名>
```

将当前选区内的所有非空方块和显示实体保存为一个 `.raw.mnb` 文件。

> **示例**：`/ml save my_furnace` → 生成 `imports/my_furnace.raw.mnb`

**注意事项**：
- 文件名只能包含字母、数字、下划线 `_` 和连字符 `-`
- Pos1 和 Pos2 必须在同一世界
- 保存成功后会自动清除当前选区
- 文件保存在服务器的 `plugins/MonolithLib/imports/` 目录下
- 选区内的 **BlockDisplay** 和 **ItemDisplay** 实体会被自动录制
- **Rebar 方块会被自动识别并记录其 NamespacedKey**，保存时会显示检测到的 Rebar 方块数量

---

### 2.3 分阶段录制

当你的结构需要**建造前和建造后使用不同的方块**时（例如：建造前是混凝土，完成后变成铁块+显示实体），可以使用分阶段录制。

#### 工作流

```
1. 搭建脚手架形态（建造前的样子）
2. /ml save <文件名> --scaffold
3. 修改为最终形态（建造后的样子）
4. /ml save <文件名> --assembled
5. /ml merge <文件名>
```

#### 命令详解

| 命令 | 说明 | 生成文件 |
|------|------|----------|
| `/ml save <名称> --scaffold` | 保存脚手架阶段（建造前） | `<名称>.scaffold.raw.mnb` |
| `/ml save <名称> --assembled` | 保存组装阶段（建造后） | `<名称>.assembled.raw.mnb` |
| `/ml merge <名称>` | 合并两个阶段为完整蓝图 | `<名称>.raw.mnb` |

#### 示例：录制一个双阶段高炉

```
# 第一步：搭建脚手架（用混凝土填充）
/ml wand                          # 获取选区工具
# ... 用左键/右键选择区域 ...
/ml save blast_furnace --scaffold # 保存脚手架阶段

# 第二步：修改为最终形态（铁块+显示实体）
# ... 替换方块、添加显示实体 ...
/ml save blast_furnace --assembled # 保存组装阶段

# 第三步：合并
/ml merge blast_furnace            # 生成 blast_furnace.raw.mnb
```

合并后的蓝图：
- **脚手架阶段**：玩家需要放置混凝土（建造前的样子）
- **组装阶段**：完成后自动变成铁块+显示实体（最终形态）
- **魔法时刻**：所有混凝土放置完成后，自动替换为最终形态

#### 替代方案：使用配置文件

如果你不想手动录制两个阶段，也可以只录制最终形态，然后通过配置文件自动生成脚手架阶段：

```yaml
# blueprints/blast_furnace/blast_furnace.yml
scaffold_materials:
  iron_block: concrete          # 铁块 → 混凝土
  gold_block: concrete_powder   # 金块 → 混凝土粉末
  diamond_block: concrete       # 钻石块 → 混凝土
```

编译时会自动将 `assembled` 阶段中的材料替换为对应的脚手架材料。

---

### 2.4 蓝图文件管理

#### 目录结构

```
plugins/MonolithLib/
├── imports/          # 原始文件存放（录制或外部导入）
│   ├── my_struct.raw.mnb              # 单阶段蓝图
│   ├── my_struct.scaffold.raw.mnb     # 脚手架阶段
│   ├── my_struct.assembled.raw.mnb    # 组装阶段
│   └── export.litematic
├── blueprints/       # 标准化蓝图 + 配置
│   ├── my_struct/
│   │   ├── my_struct.mnb
│   │   └── my_struct.yml     # 材料替换、控制器绑定等配置
│   └── other/
└── products/         # 编译后的最终产品（运行时读取）
    ├── my_struct.mnb
    └── other.mnb
```

#### 支持的导入格式

| 文件类型 | 说明 |
|----------|------|
| `*.raw.mnb` | MonolithLib 原始录制格式（优先级最高） |
| `*.scaffold.raw.mnb` | 分阶段录制的脚手架阶段 |
| `*.assembled.raw.mnb` | 分阶段录制的组装阶段 |
| `*.litematic` | Litematica 模组导出格式 |
| `*.schem` | Sponge Schematic 格式 |
| `*.nbt` | Minecraft NBT Structure 格式 |

#### 自动导入流程

服务器启动或执行 `/ml reload` 时，`imports/` 目录下的文件会被自动处理：

```
imports/*.raw.mnb  ──导入──→  blueprints/<id>/<id>.mnb
                               │
                        blueprints/<id>/<id>.yml  (配置)
                               │
                          编译 (BuiltMNBCompiler)
                               │
                               ▼
                      products/<id>.mnb  (最终产品)
```

---

### 2.5 配置与编译

#### 配置文件示例

```yaml
# blueprints/<blueprint_id>/<blueprint_id>.yml
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

# 脚手架材料映射（可选）
# 编译时自动将 assembled 阶段中的材料替换为对应脚手架材料
scaffold_materials:
  iron_block: concrete          # 铁块 → 混凝土
  gold_block: concrete_powder   # 金块 → 混凝土粉末

# 方块覆盖规则（可选）
overrides:
  "2, 0, 2":
    type: rebar
    key: "myplugin:machine_core"
    preview: blast_furnace

# 槽位定义（可选）
slots:
  input: "0, 1, 0"
  output: "4, 1, 0"

# 自定义数据（可选）
custom:
  tier: 2
  fuel_capacity: 1000
```

#### scaffold_materials 详解

`scaffold_materials` 是一个材料映射表，键为 **assembled（最终）阶段的材料**，值为 **scaffold（脚手架）阶段的材料**。

编译时的处理逻辑：
1. 读取 `.raw.mnb` 文件中的 assembled 形状
2. 遍历每个方块，查找其材料是否在 `scaffold_materials` 映射中
3. 如果找到映射，生成对应的脚手架方块（保留位置，替换材料）
4. 如果没有映射，脚手架阶段使用与组装阶段相同的方块
5. 同时应用 `overrides` 中的逐位置覆盖规则

---

### 2.6 自定义 Rebar 集成

要让你的 Rebar 方块作为 MonolithLib 结构的控制器：

```kotlin
// 1. 定义 Rebar 方块
class MyMachineCore(block: Block, context: BlockCreateContext) : RebarBlock(block, context) {
    companion object {
        val KEY = NamespacedKey(MonolithLib.instance, "my_machine")
        val MATERIAL = Material.BLAST_FURNACE
    }
}

// 2. 注册方块 + 物品
RebarBlock.register(MyMachineCore.KEY, MyMachineCore.MATERIAL, MyMachineCore::class.java)
val itemStack = ItemStackBuilder.rebar(MyMachineCore.MATERIAL, MyMachineCore.KEY).build()
RebarItem.register(RebarItem::class.java, itemStack, MyMachineCore.KEY)

// 3. 注册蓝图并绑定控制器
val blueprint = Blueprint.builder("my_machine")
    .shape(machineShape)
    .controllerOffset(2, 1, 2)
    .controllerRebar(MyMachineCore.KEY)  // ← 关键：绑定到你的 Rebar 方块
    .build()

MonolithAPI.getInstance().registry.register(blueprint)
```

绑定后：
- 玩家准星对准该 Rebar 方块时可用 `/ml preview` 和 `/ml build`
- 成型时自动关联 Rebar 组件逻辑

---

### 2.7 API 参考

以下 API 详见 [API_GUIDE.md](API_GUIDE.md)：

| API | 说明 |
|-----|------|
| [MonolithAPI](API_GUIDE.md#monolithapi-主入口) | 核心入口，提供所有 Facade |
| [Blueprint](API_GUIDE.md#蓝图-blueprint) | 蓝图数据模型 |
| [Shape](API_GUIDE.md#形状-shape) | 形状定义 |
| [BuildStage](API_GUIDE.md#构建阶段-buildstage) | 构建阶段枚举 |
| [BlueprintDSL](API_GUIDE.md#blueprintdsl-编程式构建) | DSL 构建蓝图 |
| [Predicate](API_GUIDE.md#谓词系统-predicate) | 方块匹配规则 |
| [DisplayEntityData](API_GUIDE.md#显示实体-displayentitydata) | 显示实体数据 |
| [事件系统](API_GUIDE.md#事件系统) | 结构生命周期事件 |
| [Facing](API_GUIDE.md#面向-facing) | 方向枚举 |
| [BlueprintConfig](API_GUIDE.md#蓝图配置-blueprintconfig) | 蓝图配置系统 |
| [BuiltMNBCompiler](API_GUIDE.md#编译器-builtmnbcompiler) | 蓝图编译器 |
