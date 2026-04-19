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
    val scaffoldMaterials: Map<Material, Material> = emptyMap()
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
            scaffoldMaterials = scaffoldMaterials
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
        
        yaml.save(file)
    }
    
    fun generateDefault(id: String, blueprint: Blueprint, file: File, defaultControllerKey: String = "monolithlib:custom_controller") {
        val centerPos = blueprint.meta.controllerOffset
        
        val yaml = YamlConfiguration()
        
        yaml.set("id", id)
        yaml.set("version", "1.0")
        
        yaml.set("meta.name", id)
        yaml.set("meta.description", "结构配置 - 编辑此文件以自定义结构")
        yaml.set("meta.author", "")
        
        yaml.set("controller.type", "rebar")
        yaml.set("controller.rebar_key", defaultControllerKey)
        yaml.set("controller.position", "${centerPos.x}, ${centerPos.y}, ${centerPos.z}")
        
        yaml.createSection("overrides")
        yaml.createSection("slots")
        yaml.createSection("custom")
        
        val scaffoldSection = yaml.createSection("scaffold_materials")
        scaffoldSection.set("iron_block", "concrete")
        scaffoldSection.set("gold_block", "concrete_powder")
        scaffoldSection.set("_comment", "assembled_material -> scaffold_material, 脚手架阶段使用的材料映射")
        
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
