package top.mc506lw.monolith.feature.io.formats

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.StrictPredicate
import top.mc506lw.monolith.core.structure.FlattenedBlock
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.feature.io.StructureSerializer
import top.mc506lw.monolith.feature.io.nbt.*
import java.io.*
import java.util.zip.GZIPInputStream

object NbtStructureFormat : StructureSerializer {
    
    override val formatName: String = "NBT Structure"
    override val fileExtension: String = ".nbt"
    
    fun load(file: File): MonolithStructure? {
        return try {
            file.inputStream().use { input ->
                deserialize(input)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun serialize(structure: MonolithStructure, output: OutputStream) {
        throw UnsupportedOperationException("暂不支持导出为 .nbt 格式")
    }
    
    override fun deserialize(input: InputStream): MonolithStructure {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        
        val sizeList = root.getNbtList("size") ?: return MonolithStructure.Builder("empty").build()
        val blocksList = root.getNbtList("blocks") ?: emptyList()
        val paletteList = root.getNbtList("palette") ?: emptyList()
        
        val sizeX = (sizeList.getOrNull(0) as? Number)?.toInt() ?: 1
        val sizeY = (sizeList.getOrNull(1) as? Number)?.toInt() ?: 1
        val sizeZ = (sizeList.getOrNull(2) as? Number)?.toInt() ?: 1
        
        val palette = parsePalette(paletteList)
        
        val allBlocks = mutableListOf<FlattenedBlock>()
        
        for (blockEntry in blocksList) {
            val compound = (blockEntry as? NbtCompound)?.value ?: continue
            
            val posList = compound.getNbtList("pos") ?: continue
            val state = compound.getNbtInt("state") ?: 0
            
            val x = (posList.getOrNull(0) as? Number)?.toInt() ?: 0
            val y = (posList.getOrNull(1) as? Number)?.toInt() ?: 0
            val z = (posList.getOrNull(2) as? Number)?.toInt() ?: 0
            
            val blockData = palette.getOrNull(state)
            if (blockData != null && !blockData.material.isAir) {
                allBlocks.add(FlattenedBlock(
                    relativePosition = Vector3i(x, y, z),
                    predicate = StrictPredicate(blockData),
                    previewBlockData = blockData,
                    hint = blockData.asString
                ))
            }
        }
        
        val builder = MonolithStructure.Builder("nbt_structure")
            .size(sizeX, sizeY, sizeZ)
            .center(sizeX / 2, 0, sizeZ / 2)
        
        for (block in allBlocks) {
            builder.set(
                block.relativePosition.x,
                block.relativePosition.y,
                block.relativePosition.z,
                block.predicate
            )
        }
        
        return builder.build()
    }
    
    private fun parsePalette(paletteList: List<Any>): List<BlockData> {
        return paletteList.mapNotNull { entry ->
            val compound = (entry as? NbtCompound)?.value ?: return@mapNotNull null
            
            val name = compound.getNbtString("Name") ?: "minecraft:air"
            val properties = compound.getNbtCompound("Properties") ?: emptyMap()
            
            val blockStateStr = buildString {
                append(name)
                if (properties.isNotEmpty()) {
                    append("[")
                    properties.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) append(",")
                        append("$k=$v")
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
