package top.mc506lw.monolith.core.io

import top.mc506lw.monolith.core.io.formats.BinaryFormat
import top.mc506lw.monolith.core.io.formats.LitematicFormat
import top.mc506lw.monolith.core.io.formats.NbtStructureFormat
import top.mc506lw.monolith.core.io.formats.SchemFormat
import top.mc506lw.monolith.core.model.Blueprint
import java.io.File
import java.security.MessageDigest

object LoadPriority {
    const val MNB = 0
    const val LITEMATIC = 1
    const val SCHEM = 2
    const val NBT = 3
    
    val EXTENSIONS = mapOf(
        "mnb" to MNB,
        "litematic" to LITEMATIC,
        "schem" to SCHEM,
        "nbt" to NBT
    )
}

class IOModule(private val dataFolder: File) {
    
    private val importsFolder = File(dataFolder, "imports")
    private val blueprintsFolder = File(dataFolder, "blueprints")
    private val productsFolder = File(dataFolder, "products")
    
    private val formatVersion = 2
    
    private val configHashes = mutableMapOf<String, String>()
    
    init {
        importsFolder.mkdirs()
        blueprintsFolder.mkdirs()
        productsFolder.mkdirs()
    }
    
    val importsDirectory: File get() = importsFolder
    val blueprintsDirectory: File get() = blueprintsFolder
    val productsDirectory: File get() = productsFolder
    
    fun loadAllBlueprints(): List<Blueprint> {
        val blueprints = mutableListOf<Blueprint>()
        
        processImports()
        
        buildAllProducts()
        
        val productFiles = productsFolder.listFiles()?.filter { it.isFile && it.extension == "mnb" } ?: emptyList()
        
        for (file in productFiles) {
            val blueprint = loadBlueprintFromMnb(file)
            if (blueprint != null) {
                blueprints.add(blueprint)
            }
        }
        
        return blueprints
    }
    
    fun rebuildProduct(blueprintId: String): Blueprint? {
        val blueprintDir = File(blueprintsFolder, blueprintId)
        if (!blueprintDir.exists()) return null
        
        val baseMnb = File(blueprintDir, "$blueprintId.mnb")
        val configFile = File(blueprintDir, "$blueprintId.yml")
        
        if (!baseMnb.exists()) return null
        
        val baseBlueprint = loadBlueprintFromMnb(baseMnb) ?: return null
        val config = if (configFile.exists()) BlueprintConfigLoader.load(configFile) else null
        
        val finalBlueprint = if (config != null) {
            ProductBuilder.build(baseBlueprint, config)
        } else {
            baseBlueprint
        }
        
        val productFile = File(productsFolder, "$blueprintId.mnb")
        saveBlueprintToMnb(finalBlueprint, productFile)
        
        if (configFile.exists()) {
            configHashes[blueprintId] = calculateHash(configFile)
        }
        
        println("[MonolithLib] 已重建产品: $blueprintId")
        return finalBlueprint
    }
    
    fun hasConfigChanged(blueprintId: String): Boolean {
        val blueprintDir = File(blueprintsFolder, blueprintId)
        val configFile = File(blueprintDir, "$blueprintId.yml")
        
        if (!configFile.exists()) return false
        
        val currentHash = calculateHash(configFile)
        val storedHash = configHashes[blueprintId]
        
        return currentHash != storedHash
    }
    
    private fun processImports() {
        val importFiles = importsFolder.listFiles()?.filter { it.isFile } ?: emptyList()
        
        val groupedFiles = importFiles.groupBy { file ->
            file.nameWithoutExtension
        }
        
        for ((baseName, files) in groupedFiles) {
            val blueprintDir = File(blueprintsFolder, baseName)
            val baseMnb = File(blueprintDir, "$baseName.mnb")
            
            if (baseMnb.exists()) {
                continue
            }
            
            val sortedFiles = files.sortedBy { file ->
                LoadPriority.EXTENSIONS[file.extension.lowercase()] ?: 99
            }
            
            val sourceFile = sortedFiles.firstOrNull() ?: continue
            
            val blueprint = loadBlueprintFromSourceFile(sourceFile)
            if (blueprint != null) {
                blueprintDir.mkdirs()
                
                saveBlueprintToMnb(blueprint, baseMnb)
                
                val configFile = File(blueprintDir, "$baseName.yml")
                if (!configFile.exists()) {
                    BlueprintConfigLoader.generateDefault(baseName, blueprint, configFile)
                }
                
                for (file in files) {
                    file.delete()
                }
                
                println("[MonolithLib] 已导入蓝图: $baseName (${blueprint.blockCount} 方块)")
            }
        }
    }
    
    private fun buildAllProducts() {
        val blueprintDirs = blueprintsFolder.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        for (dir in blueprintDirs) {
            val blueprintId = dir.name
            val baseMnb = File(dir, "$blueprintId.mnb")
            val configFile = File(dir, "$blueprintId.yml")
            
            if (!baseMnb.exists()) continue
            
            val productFile = File(productsFolder, "$blueprintId.mnb")
            
            val needsRebuild = !productFile.exists() || hasConfigChanged(blueprintId)
            
            if (needsRebuild) {
                rebuildProduct(blueprintId)
            } else {
                if (configFile.exists()) {
                    configHashes[blueprintId] = calculateHash(configFile)
                }
            }
        }
        
        val standaloneProducts = productsFolder.listFiles()
            ?.filter { it.isFile && it.extension == "mnb" }
            ?.filter { file ->
                val blueprintDir = File(blueprintsFolder, file.nameWithoutExtension)
                !blueprintDir.exists()
            }
            ?: emptyList()
        
        for (file in standaloneProducts) {
            println("[MonolithLib] 加载独立产品: ${file.nameWithoutExtension}")
        }
    }
    
    private fun loadBlueprintFromSourceFile(file: File): Blueprint? {
        return when (file.extension.lowercase()) {
            "mnb" -> loadBlueprintFromMnb(file)
            "litematic" -> LitematicFormat.load(file)
            "schem" -> SchemFormat.load(file)
            "nbt" -> NbtStructureFormat.load(file)
            else -> null
        }
    }
    
    private fun loadBlueprintFromMnb(file: File): Blueprint? {
        return try {
            BinaryFormat.load(file)
        } catch (e: Exception) {
            println("[MonolithLib] 加载 .mnb 文件失败: ${file.name} - ${e.message}")
            null
        }
    }
    
    private fun saveBlueprintToMnb(blueprint: Blueprint, file: File, configHash: String = "") {
        try {
            BinaryFormat.save(blueprint, file, formatVersion, configHash)
        } catch (e: Exception) {
            println("[MonolithLib] 保存 .mnb 文件失败: ${file.name} - ${e.message}")
        }
    }
    
    private fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.readBytes().forEach { digest.update(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
