package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.model.ShapeBuilder
import top.mc506lw.monolith.core.io.*
import java.io.*
import java.util.zip.GZIPInputStream

object NbtShapeReader : ShapeReader {
    
    override val formatName: String = "NBT Structure"
    override val supportedExtensions: Set<String> = setOf("nbt")
    
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
        
        val size = root.getNbtCompound("size") ?: emptyMap()
        val sizeX = size.getNbtInt("x") ?: 1
        val sizeY = size.getNbtInt("y") ?: 1
        val sizeZ = size.getNbtInt("z") ?: 1
        
        val paletteCompound = root.getNbtCompound("palette")?.getNbtCompound("default") ?: emptyMap()
        val blockDataArray = root.getNbtByteArray("block_data") ?: root.getNbtByteArray("blocks") ?: ByteArray(0)
        
        val palette = mutableMapOf<Int, BlockData>()
        for ((key, value) in paletteCompound) {
            val id = (value as? Number)?.toInt() ?: continue
            try {
                palette[id] = Bukkit.createBlockData(key)
            } catch (e: Exception) {
                palette[id] = Bukkit.createBlockData("minecraft:air")
            }
        }
        
        val builder = ShapeBuilder()
        
        var index = 0
        for (y in 0 until sizeY) {
            for (z in 0 until sizeZ) {
                for (x in 0 until sizeX) {
                    if (index < blockDataArray.size) {
                        val stateId = blockDataArray[index].toInt() and 0xFF
                        val block = palette[stateId]
                        
                        if (block != null && !block.material.isAir) {
                            builder.addBlock(x, y, z, block)
                        }
                        index++
                    }
                }
            }
        }
        
        return builder.build()
    }
}
