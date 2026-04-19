package top.mc506lw.monolith.core.io.formats

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.io.NbtReader
import top.mc506lw.monolith.core.io.StructureSerializer
import top.mc506lw.monolith.core.io.getNbtByteArray
import top.mc506lw.monolith.core.io.getNbtCompound
import top.mc506lw.monolith.core.io.getNbtShort
import top.mc506lw.monolith.core.math.Vector3i
import java.util.logging.Level
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.BuildStage
import top.mc506lw.monolith.core.model.Shape
import java.io.*
import java.util.zip.GZIPInputStream

object SchemFormat : StructureSerializer {
    
    override val formatName: String = "Sponge Schematic"
    override val fileExtension: String = ".schem"
    
    fun load(file: File): Blueprint? {
        return load(file, file.nameWithoutExtension)
    }
    
    fun load(file: File, blueprintId: String): Blueprint? {
        return try {
            file.inputStream().use { input ->
                deserialize(input, blueprintId)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "[MonolithLib] SchemFormat 加载失败: ${file.absolutePath}", e)
            null
        }
    }
    
    override fun serialize(blueprint: Blueprint, output: OutputStream) {
        throw UnsupportedOperationException("暂不支持导出为 .schem 格式")
    }
    
    override fun deserialize(input: InputStream): Blueprint? {
        return deserialize(input, "schematic")
    }
    
    fun deserialize(input: InputStream, blueprintId: String = "schematic"): Blueprint {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        
        val width = root.getNbtShort("Width")?.toInt() ?: 1
        val height = root.getNbtShort("Height")?.toInt() ?: 1
        val length = root.getNbtShort("Length")?.toInt() ?: 1
        
        val paletteCompound = root.getNbtCompound("Palette") ?: emptyMap()
        val blockDataArray = root.getNbtByteArray("BlockData") ?: ByteArray(0)
        
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
        
        val allBlocks = mutableListOf<BlockEntry>()
        
        var index = 0
        var x = 0
        var y = 0
        var z = 0
        
        while (index < blockDataArray.size) {
            val (stateId, bytesRead) = readVarInt(blockDataArray, index)
            index += bytesRead
            
            val block = palette[stateId]
            if (block != null && !block.material.isAir) {
                allBlocks.add(BlockEntry(
                    position = Vector3i(x, y, z),
                    blockData = block
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
        
        val shape = Shape(allBlocks)
        
        return Blueprint(
            id = blueprintId,
            stages = mapOf(BuildStage.SCAFFOLD to shape, BuildStage.ASSEMBLED to shape),
            meta = BlueprintMeta(
                displayName = blueprintId
            )
        )
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
