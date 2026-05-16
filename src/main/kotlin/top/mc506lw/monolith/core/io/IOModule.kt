package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import top.mc506lw.monolith.core.io.formats.BinaryFormat
import top.mc506lw.monolith.core.io.formats.LitematicFormat
import top.mc506lw.monolith.core.io.formats.NbtStructureFormat
import top.mc506lw.monolith.core.io.formats.SchemFormat
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.common.MonolithLogger
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

    private val log = MonolithLogger.getLogger("IO")
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
            BuiltMNBCompiler.compile(baseBlueprint, config)
        } else {
            baseBlueprint
        }

        val productFile = File(productsFolder, "$blueprintId.mnb")
        saveBlueprintToMnb(finalBlueprint, productFile)

        if (configFile.exists()) {
            configHashes[blueprintId] = calculateHash(configFile)
        }

        log.info("rebuild", "产品已重建", "blueprintId" to blueprintId)
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

        if (importFiles.isEmpty()) {
            log.warn("import", "导入文件夹为空", "path" to importsFolder.absolutePath)
            return
        }

        val groupedFiles = importFiles.groupBy { file ->
            file.nameWithoutExtension
        }

        var importedCount = 0
        var failedCount = 0

        for ((baseName, files) in groupedFiles) {
            val blueprintDir = File(blueprintsFolder, baseName)
            val baseMnb = File(blueprintDir, "$baseName.mnb")

            if (baseMnb.exists()) {
                log.trace("import", "跳过已导入蓝图", "blueprintId" to baseName)
                continue
            }

            val sortedFiles = files.sortedBy { file ->
                LoadPriority.EXTENSIONS[file.extension.lowercase()] ?: 99
            }

            val sourceFile = sortedFiles.firstOrNull() ?: continue

            val blueprint = loadBlueprintFromSourceFile(sourceFile, baseName)
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

                importedCount++
                log.info("import", "蓝图导入成功", "blueprintId" to baseName, "blockCount" to blueprint.blockCount)
            } else {
                failedCount++
                log.warn("import", "蓝图导入失败", "blueprintId" to baseName, "file" to sourceFile.name)
            }
        }

        log.info("import", "导入完成", "success" to importedCount, "failed" to failedCount)
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
            log.debug("load", "加载独立产品", "blueprintId" to file.nameWithoutExtension)
        }
    }

    private fun loadBlueprintFromSourceFile(file: File, blueprintId: String): Blueprint? {
        return when (file.extension.lowercase()) {
            "mnb" -> loadBlueprintFromMnb(file)
            "litematic" -> LitematicFormat.load(file, blueprintId)
            "schem" -> SchemFormat.load(file, blueprintId)
            "nbt" -> NbtStructureFormat.load(file, blueprintId)
            else -> {
                log.warn("load", "不支持的文件格式", "ext" to file.extension)
                null
            }
        }
    }

    private fun loadBlueprintFromMnb(file: File): Blueprint? {
        return try {
            BinaryFormat.load(file)
        } catch (e: Exception) {
            log.warn { "MNB文件加载失败 - ${e.message}" }
            null
        }
    }

    private fun saveBlueprintToMnb(blueprint: Blueprint, file: File, configHash: String = "") {
        try {
            BinaryFormat.save(blueprint, file, formatVersion, configHash)
        } catch (e: Exception) {
            log.error { "保存MNB文件失败: ${file.name} - ${e.message}" }
        }
    }

    private fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.readBytes().forEach { digest.update(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}