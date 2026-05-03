package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.validation.predicate.*
import java.io.File

data class BlueprintConfig(
    val id: String,
    val version: String = "1.0",
    val controllerType: String = "rebar",
    val controllerRebarKey: NamespacedKey? = null,
    val controllerPosition: Vector3i? = null,
    val overrides: Map<Vector3i, OverrideEntry> = emptyMap(),
    val slots: Map<String, Vector3i> = emptyMap(),
    val customData: Map<String, Any> = emptyMap(),
    val metaName: String = "",
    val metaDescription: String = "",
    val metaAuthor: String = "",
    val scaffoldMaterials: Map<Material, Material> = emptyMap(),
    val displayOffset: Vector3i? = null,
    val scaffoldRotation: Int = 0,
    val assembledRotation: Int = 0,
    val rotationCenter: Vector3i? = null
) {
    data class OverrideEntry(
        val type: String,
        val material: Material? = null,
        val rebarKey: NamespacedKey? = null,
        val previewMaterial: Material? = null,
        val ignoreStates: Set<String> = emptySet()
    )
}

object BlueprintConfigLoader {
    
    fun load(file: File): BlueprintConfig? {
        if (!file.exists()) return null
        
        val config = YamlConfiguration.loadConfiguration(file)
        
        val id = config.getString("id") ?: file.parentFile?.name ?: return null
        val version = config.getString("version", "1.0") ?: "1.0"
        
        val controllerType = config.getString("controller.type", "rebar") ?: "rebar"
        val controllerRebarKey = config.getString("controller.rebar_key")?.let { parseNamespacedKey(it) }
        val controllerPosition = parsePosition(config.getString("controller.position"))
        
        val overrides = mutableMapOf<Vector3i, BlueprintConfig.OverrideEntry>()
        val overridesSection = config.getConfigurationSection("overrides")
        if (overridesSection != null) {
            for (posKey in overridesSection.getKeys(false)) {
                val pos = parsePosition(posKey) ?: continue
                val entrySection = overridesSection.getConfigurationSection(posKey) ?: continue
                val entry = parseOverrideEntry(entrySection) ?: continue
                overrides[pos] = entry
            }
        }
        
        val slots = mutableMapOf<String, Vector3i>()
        val slotsSection = config.getConfigurationSection("slots")
        if (slotsSection != null) {
            for (slotId in slotsSection.getKeys(false)) {
                val posStr = slotsSection.getString(slotId) ?: continue
                val pos = parsePosition(posStr)
                if (pos != null) {
                    slots[slotId] = pos
                }
            }
        }
        
        val customData = config.getConfigurationSection("custom")?.getValues(false) ?: emptyMap()
        
        val scaffoldMaterials = mutableMapOf<Material, Material>()
        val scaffoldSection = config.getConfigurationSection("scaffold_materials")
        if (scaffoldSection != null) {
            for (assembledMatKey in scaffoldSection.getKeys(false)) {
                val assembledMat = try { Material.valueOf(assembledMatKey.uppercase()) } catch (_: Exception) { continue }
                val scaffoldMatStr = scaffoldSection.getString(assembledMatKey) ?: continue
                val scaffoldMat = try { Material.valueOf(scaffoldMatStr.uppercase()) } catch (_: Exception) { continue }
                scaffoldMaterials[assembledMat] = scaffoldMat
            }
        }

        val displayOffsetRaw = config.getString("display_offset")
        val displayOffset = parsePosition(displayOffsetRaw)
        Bukkit.getLogger().info("[BlueprintConfigLoader] display_offset: 原始值='$displayOffsetRaw', 解析结果=$displayOffset")

        val scaffoldRotation = config.getInt("rotation.scaffold", 0)
        val assembledRotation = config.getInt("rotation.assembled", 0)
        val rotationCenterRaw = config.getString("rotation.center")
        val rotationCenter = parsePosition(rotationCenterRaw)

        if (scaffoldRotation != 0 || assembledRotation != 0) {
            val validRotations = setOf(0, 90, 180, 270)
            require(scaffoldRotation in validRotations) { "rotation.scaffold 必须是 0, 90, 180, 270 之一，当前值: $scaffoldRotation" }
            require(assembledRotation in validRotations) { "rotation.assembled 必须是 0, 90, 180, 270 之一，当前值: $assembledRotation" }
            Bukkit.getLogger().info("[BlueprintConfigLoader] 检测到分阶段旋转配置: scaffold=$scaffoldRotation°, assembled=$assembledRotation°, center=$rotationCenter")
        }

        return BlueprintConfig(
            id = id,
            version = version,
            controllerType = controllerType,
            controllerRebarKey = controllerRebarKey,
            controllerPosition = controllerPosition,
            overrides = overrides,
            slots = slots,
            customData = customData,
            metaName = config.getString("meta.name", id) ?: id,
            metaDescription = config.getString("meta.description", "") ?: "",
            metaAuthor = config.getString("meta.author", "") ?: "",
            scaffoldMaterials = scaffoldMaterials,
            displayOffset = displayOffset,
            scaffoldRotation = scaffoldRotation,
            assembledRotation = assembledRotation,
            rotationCenter = rotationCenter
        )
    }
    
    fun save(config: BlueprintConfig, file: File) {
        val yaml = YamlConfiguration()
        
        yaml.set("id", config.id)
        yaml.set("version", config.version)
        
        yaml.set("meta.name", config.metaName)
        yaml.set("meta.description", config.metaDescription)
        yaml.set("meta.author", config.metaAuthor)
        
        yaml.set("controller.type", config.controllerType)
        config.controllerRebarKey?.let { yaml.set("controller.rebar_key", "${it.namespace}:${it.key}") }
        config.controllerPosition?.let { yaml.set("controller.position", "${it.x}, ${it.y}, ${it.z}") }
        
        if (config.overrides.isNotEmpty()) {
            val overridesSection = yaml.createSection("overrides")
            for ((pos, entry) in config.overrides) {
                val posKey = "${pos.x}, ${pos.y}, ${pos.z}"
                val entrySection = overridesSection.createSection(posKey)
                entrySection.set("type", entry.type)
                entry.material?.let { entrySection.set("material", it.name.lowercase()) }
                entry.rebarKey?.let { entrySection.set("key", "${it.namespace}:${it.key}") }
                entry.previewMaterial?.let { entrySection.set("preview", it.name.lowercase()) }
                if (entry.ignoreStates.isNotEmpty()) {
                    entrySection.set("ignore_states", entry.ignoreStates.toList())
                }
            }
        }
        
        if (config.slots.isNotEmpty()) {
            val slotsSection = yaml.createSection("slots")
            for ((slotId, pos) in config.slots) {
                slotsSection.set(slotId, "${pos.x}, ${pos.y}, ${pos.z}")
            }
        }
        
        if (config.customData.isNotEmpty()) {
            yaml.createSection("custom", config.customData)
        }
        
        if (config.scaffoldMaterials.isNotEmpty()) {
            val scaffoldSection = yaml.createSection("scaffold_materials")
            for ((assembled, scaffold) in config.scaffoldMaterials) {
                scaffoldSection.set(assembled.name.lowercase(), scaffold.name.lowercase())
            }
        }

        config.displayOffset?.let { yaml.set("display_offset", "${it.x}, ${it.y}, ${it.z}") }

        if (config.scaffoldRotation != 0 || config.assembledRotation != 0) {
            val rotationSection = yaml.createSection("rotation")
            rotationSection.set("scaffold", config.scaffoldRotation)
            rotationSection.set("assembled", config.assembledRotation)
            config.rotationCenter?.let { rotationSection.set("center", "${it.x}, ${it.y}, ${it.z}") }
        }

        yaml.save(file)
    }
    
    fun generateDefault(id: String, blueprint: Blueprint, file: File, defaultControllerKey: String = "monolithlib:custom_controller") {
        val centerPos = blueprint.meta.controllerOffset
        
        val yaml = YamlConfiguration()

        yaml.set("id", id)
        yaml.setComments("id", listOf(
            "=== 基础信息 / Basic Info ===",
            "蓝图的唯一标识符 / Unique identifier for this blueprint"
        ))
        
        yaml.set("version", "1.0")
        yaml.setComments("version", listOf(
            "配置文件版本号 / Config file version",
            "用于追踪配置变更 / Track config changes"
        ))

        yaml.set("meta.name", id)
        yaml.set("meta.description", "结构配置 - 编辑此文件以自定义结构 / Structure config - Edit this file to customize")
        yaml.set("meta.author", "")
        yaml.setComments("meta", listOf(
            "",
            "=== 元数据 / Metadata ===",
            "显示名称、描述和作者信息 / Display name, description, and author info",
            "- name: 游戏内显示的名称 / In-game display name",
            "- description: 详细说明 (用于 /ml info) / Detailed description",
            "- author: 创作者名字 / Creator name"
        ))

        yaml.set("controller.type", "rebar")
        yaml.set("controller.rebar_key", defaultControllerKey)
        yaml.set("controller.position", "${centerPos.x}, ${centerPos.y}, ${centerPos.z}")
        yaml.setComments("controller", listOf(
            "",
            "=== 控制器配置 / Controller Settings ===",
            "定义多方块结构的核心控制器 / Define the core controller of this multiblock structure",
            "- type: 控制器类型 (rebar=Rebar组件, custom=自定义方块)",
            "         Controller type (rebar=Rebar component, custom=custom block)",
            "- rebar_key: Rebar组件的命名空间键 (格式: namespace:key)",
            "              Rebar component namespaced key (format: namespace:key)",
            "- position: 控制器相对于结构原点的坐标 (x, y, z)",
            "             Controller position relative to structure origin",
            "              +X=东/East, +Y=上/Up, +Z=南/South"
        ))

        yaml.createSection("overrides")
        yaml.setComments("overrides", listOf(
            "",
            "=== 位置覆盖 / Position Overrides ===",
            "对特定位置进行精细控制 / Fine-grained control over specific positions",
            "格式示例 / Format example:",
            "  overrides:",
            "    \"x, y, z\":",
            "      type: rebar          # 覆盖类型: strict(严格) | loose(松散) | rebar(Rebar组件)",
            "      key: \"plugin:block\"  # Rebar组件键 (type=rebar时必填)",
            "      material: hopper     # 材料名 (type=strict/loose时使用)",
            "      preview: furnace     # 预览时显示的材料",
            "      ignore_states:       # 忽略的方块状态 (type=loose时)",
            "        - \"facing\""
        ))

        yaml.createSection("slots")
        yaml.setComments("slots", listOf(
            "",
            "=== 槽位定义 / Slot Definitions ===",
            "标记结构中的功能位置 (输入口、输出口等) / Mark functional positions in the structure",
            "这些数据不会自动传递给Rebar，需在代码中通过 blueprint.getSlotPosition() 主动读取",
            "This data is NOT auto-passed to Rebar; read via blueprint.getSlotPosition() in your code",
            "格式示例 / Format example:",
            "  slots:",
            "    input: \"0, 1, 0\"    # 输入口坐标 / Input position",
            "    output: \"4, 1, 0\"   # 输出口坐标 / Output position"
        ))

        yaml.createSection("custom")
        yaml.setComments("custom", listOf(
            "",
            "=== 自定义数据 / Custom Data ===",
            "存储任意自定义参数，供你的RebarBlock代码读取 / Store arbitrary params for your RebarBlock code",
            "支持类型: String, Integer, Double, Boolean, List, Map (可嵌套)",
            "Supported types: String, Integer, Double, Boolean, List, Map (nestable)",
            "读取方式 / How to read:",
            "  blueprint.getCustomInt(\"key\")      // 整数 / Int",
            "  blueprint.getCustomString(\"key\")   // 字符串 / String",
            "  blueprint.getCustomDouble(\"key\")   // 小数 / Double",
            "  blueprint.getCustomBoolean(\"key\")  // 布尔值 / Boolean",
            "  blueprint.customData[\"key\"]        // 任意类型 / Any type",
            "格式示例 / Format example:",
            "  custom:",
            "    tier: 2",
            "    speed: 1.5",
            "    capacity: 1000"
        ))

        val scaffoldSection = yaml.createSection("scaffold_materials")
        scaffoldSection.set("iron_block", "concrete")
        scaffoldSection.set("gold_block", "concrete_powder")
        scaffoldSection.set("_comment", "assembled -> scaffold, 组装材料->脚手架材料的映射 / Material mapping for dual-stage building")
        yaml.setComments("scaffold_materials", listOf(
            "",
            "=== 脚手架材料映射 / Scaffold Material Mapping ===",
            "双阶段建造系统: 先用廉价材料搭建脚手架，再替换为最终材料",
            "Dual-stage build: First build scaffold with cheap materials, then replace with final ones",
            "- 键(Key): 组装阶段使用的原始材料 / Material used in assembled stage",
            "- 值(Value): 脚手架阶段使用的替代材料 / Replacement material for scaffold stage",
            "常见映射 / Common mappings:",
            "  iron_block -> concrete           铁块→混凝土",
            "  gold_block -> concrete_powder    金块→混凝土粉末",
            "  diamond_block -> concrete        钻石块→混凝土",
            "不在此列表中的材料，两个阶段使用相同方块 / Materials not listed use same block in both stages"
        ))

        yaml.set("display_offset", "1, 0, 1")
        yaml.setComments("display_offset", listOf(
            "",
            "=== 显示偏移 / Display Offset ===",
            "修正展示实体(displayEntities)坐标系与脚手架形状(scaffoldShape)之间的偏差",
            "Correct offset between displayEntities coordinate system and scaffoldShape",
            "默认值 Default: 1, 0, 1",
            "格式 / Format: x, y, z",
            "+X = 向东偏移 / East   -X = 向西偏移 / West",
            "+Y = 向上偏移 / Up     -Y = 向下偏移 / Down",
            "+Z = 向南偏移 / South  -Z = 向北偏移 / North"
        ))

        val rotationSection = yaml.createSection("rotation")
        rotationSection.set("scaffold", 0)
        rotationSection.set("assembled", 0)
        rotationSection.set("center", "")
        yaml.setComments("rotation", listOf(
            "",
            "=== 分阶段旋转 / Per-Stage Rotation ===",
            "分别控制脚手架阶段和成型阶段的旋转，修正录制时方向不一致的问题",
            "Control rotation for scaffold and assembled stages separately to fix direction mismatch",
            "",
            "使用场景 / Use cases:",
            "- 脚手架录制时朝向正确，但成型后反了 → 只设置 assembled: 180",
            "  Scaffold recorded correctly but assembled faces wrong way → set only assembled: 180",
            "- 两个阶段都需要调整 → 分别设置不同的角度",
            "  Both stages need adjustment → set different angles for each",
            "",
            "可选值 / Allowed values (每个字段): 0 | 90 | 180 | 270",
            "- 0:   不旋转 (默认) / No rotation (default)",
            "- 90:  顺时针旋转90° / Rotate 90° clockwise",
            "- 180: 旋转180° (掉头) / Rotate 180° (flip)",
            "- 270: 顺时针旋转270° (=逆时针90°) / Rotate 270° CW (= 90° CCW)",
            "",
            "示例 / Examples:",
            "  # 成型阶段反了180度（最常见的情况）:",
            "  # Assembled stage is flipped 180° (most common case):",
            "  rotation:",
            "    scaffold: 0       # 脚手架不转",
            "    assembled: 180    # 成型掉头",
            "",
            "  # 两个阶段都转90度:",
            "  # Both stages rotate 90°:",
            "  rotation:",
            "    scaffold: 90",
            "    assembled: 90"
        ))
        yaml.setComments("rotation.scaffold", listOf(
            "脚手架阶段的旋转角度 / Rotation angle for scaffold stage"
        ))
        yaml.setComments("rotation.assembled", listOf(
            "成型阶段的旋转角度 / Rotation angle for assembled stage"
        ))
        yaml.setComments("rotation.center", listOf(
            "自定义旋转中心（可选，留空则自动计算几何中心）",
            "Custom rotation center (optional, leave empty to auto-calculate geometry center)",
            "",
            "格式 / Format: x, y, z (相对于结构原点的坐标)",
            "",
            "说明 / Note:",
            "- 默认自动计算整个结构的 bounding box 中心点",
            "  Default: auto-calculate the center of structure's bounding box",
            "- 如果你的控制器不在中心但想绕特定位置旋转，可以自定义",
            "  If controller is not at center but you want to rotate around a specific point, customize here"
        ))
        
        yaml.save(file)
    }
    
    private fun parsePosition(str: String?): Vector3i? {
        if (str == null) return null
        val parts = str.split(",").map { it.trim().toIntOrNull() ?: return null }
        if (parts.size != 3) return null
        return Vector3i(parts[0], parts[1], parts[2])
    }
    
    private fun parseNamespacedKey(str: String): NamespacedKey? {
        val parts = str.split(":")
        if (parts.size != 2) return null
        return try {
            NamespacedKey(parts[0], parts[1])
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseOverrideEntry(section: org.bukkit.configuration.ConfigurationSection): BlueprintConfig.OverrideEntry? {
        val type = section.getString("type") ?: return null
        val material = section.getString("material")?.let { 
            try { Material.valueOf(it.uppercase()) } catch (e: Exception) { null } 
        }
        val rebarKey = section.getString("key")?.let { parseNamespacedKey(it) }
        val previewMaterial = section.getString("preview")?.let { 
            try { Material.valueOf(it.uppercase()) } catch (e: Exception) { null } 
        }
        val ignoreStates = section.getStringList("ignore_states").toSet()
        
        return BlueprintConfig.OverrideEntry(
            type = type,
            material = material,
            rebarKey = rebarKey,
            previewMaterial = previewMaterial,
            ignoreStates = ignoreStates
        )
    }
}
