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

object SchemFormat : StructureSerializer {
    
    override val formatName: String = "Sponge Schematic"
    override val fileExtension: String = ".schem"
    
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
        throw UnsupportedOperationException("暂不支持导出为 .schem 格式")
    }
    
    override fun deserialize(input: InputStream): MonolithStructure {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        
        val width = root.getNbtShort("Width")?.toInt() ?: 1
        val height = root.getNbtShort("Height")?.toInt() ?: 1
        val length = root.getNbtShort("Length")?.toInt() ?: 1
        
        val paletteCompound = root.getNbtCompound("Palette") ?: emptyMap()
        val blockData = root.getNbtByteArray("BlockData") ?: ByteArray(0)
        
        val palette = mutableMapOf<Int, BlockData>()
        for ((key, value) in paletteCompound) {
            val id = (value as? Number)?.toInt() ?: continue
            val blockDataStr = key
            try {
                palette[id] = Bukkit.createBlockData(blockDataStr)
            } catch (e: Exception) {
                palette[id] = Bukkit.createBlockData("minecraft:air")
            }
        }
        
        val allBlocks = mutableListOf<FlattenedBlock>()
        
        var index = 0
        var x = 0
        var y = 0
        var z = 0
        
        while (index < blockData.size) {
            val (stateId, bytesRead) = readVarInt(blockData, index)
            index += bytesRead
            
            val block = palette[stateId]
            if (block != null && !block.material.isAir) {
                allBlocks.add(FlattenedBlock(
                    relativePosition = Vector3i(x, y, z),
                    predicate = StrictPredicate(block),
                    previewBlockData = block,
                    hint = block.asString
                ))
            }
            
            x++
            if (x >= width) {
                x = 0
                z++
                if (z >= length) {
                    z = 0
                    y++
                }
            }
        }
        
        val builder = MonolithStructure.Builder("schematic")
            .size(width, height, length)
            .center(width / 2, 0, length / 2)
        
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
    
    private fun readVarInt(data: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = offset
        
        while (index < data.size) {
            val b = data[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            index++
            
            if ((b and 0x80) == 0) {
                break
            }
            shift += 7
        }
        
        return Pair(result, index - offset)
    }
}
