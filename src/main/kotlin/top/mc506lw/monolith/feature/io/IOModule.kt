package top.mc506lw.monolith.feature.io

import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.feature.io.formats.BinaryFormat
import top.mc506lw.monolith.feature.io.formats.LitematicFormat
import top.mc506lw.monolith.feature.io.formats.NbtStructureFormat
import top.mc506lw.monolith.feature.io.formats.SchemFormat
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
    
    fun loadAllStructures(): List<MonolithStructure> {
        val structures = mutableListOf<MonolithStructure>()
        
        processImports()
        
        buildAllProducts()
        
        val productFiles = productsFolder.listFiles()?.filter { it.isFile && it.extension == "mnb" } ?: emptyList()
        
        for (file in productFiles) {
            val structure = loadFromMnb(file)
            if (structure != null) {
                structures.add(structure)
            }
        }
        
        return structures
    }
    
    fun rebuildProduct(blueprintId: String): MonolithStructure? {
        val blueprintDir = File(blueprintsFolder, blueprintId)
        if (!blueprintDir.exists()) return null
        
        val baseMnb = File(blueprintDir, "$blueprintId.mnb")
        val configFile = File(blueprintDir, "$blueprintId.yml")
        
        if (!baseMnb.exists()) return null
        
        val baseStructure = loadFromMnb(baseMnb) ?: return null
        val config = if (configFile.exists()) BlueprintConfigLoader.load(configFile) else null
        
        val finalStructure = if (config != null) {
            ProductBuilder.build(baseStructure, config)
        } else {
            baseStructure
        }
        
        val productFile = File(productsFolder, "$blueprintId.mnb")
        saveToMnb(finalStructure, productFile)
        
        if (configFile.exists()) {
            configHashes[blueprintId] = calculateHash(configFile)
        }
        
        println("[MonolithLib] 已重建产品: $blueprintId")
        return finalStructure
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
            
            val structure = loadFromSourceFile(sourceFile)
            if (structure != null) {
                blueprintDir.mkdirs()
                
                saveToMnb(structure, baseMnb)
                
                val configFile = File(blueprintDir, "$baseName.yml")
                if (!configFile.exists()) {
                    BlueprintConfigLoader.generateDefault(baseName, structure, configFile)
                }
                
                for (file in files) {
                    file.delete()
                }
                
                println("[MonolithLib] 已导入蓝图: $baseName (${structure.flattenedBlocks.size} 方块)")
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
    
    private fun loadFromSourceFile(file: File): MonolithStructure? {
        return when (file.extension.lowercase()) {
            "mnb" -> loadFromMnb(file)
            "litematic" -> LitematicFormat.load(file)
            "schem" -> SchemFormat.load(file)
            "nbt" -> NbtStructureFormat.load(file)
            else -> null
        }
    }
    
    private fun loadFromMnb(file: File): MonolithStructure? {
        return try {
            BinaryFormat.load(file)
        } catch (e: Exception) {
            println("[MonolithLib] 加载 .mnb 文件失败: ${file.name} - ${e.message}")
            null
        }
    }
    
    private fun saveToMnb(structure: MonolithStructure, file: File, configHash: String = "") {
        try {
            BinaryFormat.save(structure, file, formatVersion, configHash)
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
