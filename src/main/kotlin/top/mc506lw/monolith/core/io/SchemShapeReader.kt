package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.model.ShapeBuilder
import top.mc506lw.monolith.core.io.*
import java.io.*
import java.util.zip.GZIPInputStream

object SchemShapeReader : ShapeReader {
    
    override val formatName: String = "Sponge Schematic"
    override val supportedExtensions: Set<String> = setOf("schem")
    
    override fun read(file: File): Shape? {
        return try {
            file.inputStream().use { input ->
                deserialize(input)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun read(input: InputStream): Shape? {
        return try {
            deserialize(input)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun deserialize(input: InputStream): Shape {
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
        
        val builder = ShapeBuilder()
        
        var index = 0
        var x = 0
        var y = 0
        var z = 0
        
        while (index < blockData.size) {
            val (stateId, bytesRead) = readVarInt(blockData, index)
            index += bytesRead
            
            val block = palette[stateId]
            if (block != null && !block.material.isAir) {
                builder.addBlock(x, y, z, block)
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
