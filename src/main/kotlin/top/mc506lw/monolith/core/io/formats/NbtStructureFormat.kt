package top.mc506lw.monolith.core.io.formats

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.io.NbtCompound
import top.mc506lw.monolith.core.io.NbtReader
import top.mc506lw.monolith.core.io.StructureSerializer
import top.mc506lw.monolith.core.io.getNbtCompound
import top.mc506lw.monolith.core.io.getNbtInt
import top.mc506lw.monolith.core.io.getNbtList
import top.mc506lw.monolith.core.io.getNbtString
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.Shape
import java.io.*
import java.util.zip.GZIPInputStream

object NbtStructureFormat : StructureSerializer {
    
    override val formatName: String = "NBT Structure"
    override val fileExtension: String = ".nbt"
    
    fun load(file: File): Blueprint? {
        return try {
            file.inputStream().use { input ->
                deserialize(input)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun serialize(blueprint: Blueprint, output: OutputStream) {
        throw UnsupportedOperationException("暂不支持导出为 .nbt 格式")
    }
    
    override fun deserialize(input: InputStream): Blueprint {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        
        val sizeList = root.getNbtList("size") ?: return Blueprint(
            id = "empty",
            shape = Shape(emptyList()),
            meta = BlueprintMeta(displayName = "Empty")
        )
        
        val blocksList = root.getNbtList("blocks") ?: emptyList()
        val paletteList = root.getNbtList("palette") ?: emptyList()
        
        val sizeX = (sizeList.getOrNull(0) as? Number)?.toInt() ?: 1
        val sizeY = (sizeList.getOrNull(1) as? Number)?.toInt() ?: 1
        val sizeZ = (sizeList.getOrNull(2) as? Number)?.toInt() ?: 1
        
        val palette = parsePalette(paletteList)
        
        val allBlocks = mutableListOf<BlockEntry>()
        
        for (blockEntry in blocksList) {
            val compound = blockEntry as? Map<String, Any> ?: continue
            
            val posList = compound.getNbtList("pos") ?: continue
            val state = compound.getNbtInt("state") ?: 0
            
            val x = (posList.getOrNull(0) as? Number)?.toInt() ?: 0
            val y = (posList.getOrNull(1) as? Number)?.toInt() ?: 0
            val z = (posList.getOrNull(2) as? Number)?.toInt() ?: 0
            
            val blockData = palette.getOrNull(state)
            if (blockData != null && !blockData.material.isAir) {
                allBlocks.add(BlockEntry(
                    position = Vector3i(x, y, z),
                    blockData = blockData
                ))
            }
        }
        
        val shape = Shape(allBlocks)
        
        return Blueprint(
            id = "nbt_structure",
            shape = shape,
            meta = BlueprintMeta(displayName = "NBT Structure")
        )
    }
    
    private fun parsePalette(paletteList: List<Any>): List<BlockData> {
        return paletteList.mapNotNull { entry ->
            val compound = entry as? Map<String, Any> ?: return@mapNotNull null
            
            val name = compound.getNbtString("Name") ?: "minecraft:air"
            val properties = compound.getNbtCompound("Properties") ?: emptyMap()
            
            val blockStateStr = buildString {
                append(name)
                if (properties.isNotEmpty()) {
                    append("[")
                    properties.entries.forEachIndexed { i, entry ->
                        if (i > 0) append(",")
                        append("${entry.key}=${entry.value}")
                    }
                    append("]")
                }
            }
            
            try {
                Bukkit.createBlockData(blockStateStr)
            } catch (e: Exception) {
                Bukkit.createBlockData("minecraft:air")
            }
        }
    }
}
