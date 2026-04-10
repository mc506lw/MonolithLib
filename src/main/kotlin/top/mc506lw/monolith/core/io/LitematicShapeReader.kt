package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.model.ShapeBuilder
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.io.*
import java.io.*
import java.util.zip.GZIPInputStream

object LitematicShapeReader : ShapeReader {
    
    override val formatName: String = "Litematica"
    override val supportedExtensions: Set<String> = setOf("litematic")
    
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
        
        val regions = root.getNbtCompound("Regions") ?: emptyMap()
        
        val builder = ShapeBuilder()
        
        for ((regionName, regionValue) in regions) {
            val region = (regionValue as? NbtCompound)?.value ?: continue
            
            val position = region.getNbtCompound("Position") ?: continue
            val size = region.getNbtCompound("Size") ?: continue
            val blockStates = region.getNbtLongArray("BlockStates") ?: continue
            val paletteList = region.getNbtList("BlockStatePalette") ?: continue
            
            var posX = (position.getNbtInt("x") ?: 0).toInt()
            var posY = (position.getNbtInt("y") ?: 0).toInt()
            var posZ = (position.getNbtInt("z") ?: 0).toInt()
            
            var sizeX = (size.getNbtInt("x") ?: 1).toInt()
            var sizeY = (size.getNbtInt("y") ?: 1).toInt()
            var sizeZ = (size.getNbtInt("z") ?: 1).toInt()
            
            if (sizeX < 0) { posX += sizeX + 1; sizeX = -sizeX }
            if (sizeY < 0) { posY += sizeY + 1; sizeY = -sizeY }
            if (sizeZ < 0) { posZ += sizeZ + 1; sizeZ = -sizeZ }
            
            val palette = parsePalette(paletteList)
            val bits = calculateBits(palette.size)
            
            for (y in 0 until sizeY) {
                for (z in 0 until sizeZ) {
                    for (x in 0 until sizeX) {
                        val index = (y * sizeX * sizeZ + z * sizeX + x).toLong()
                        val stateId = getBlockState(index, bits, blockStates)
                        
                        val blockData = palette.getOrNull(stateId.toInt())
                        if (blockData != null && !blockData.material.isAir) {
                            builder.addBlock(x + posX, y + posY, z + posZ, blockData)
                        }
                    }
                }
            }
        }
        
        return builder.build()
    }
    
    private fun parsePalette(paletteList: List<Any>): List<BlockData> {
        return paletteList.mapNotNull { entry ->
            val compound = (entry as? NbtCompound)?.value ?: return@mapNotNull null
            val name = compound.getNbtString("Name") ?: "minecraft:air"
            val properties = compound.getNbtCompound("Properties") ?: emptyMap()
            
            try {
                val stateStr = if (properties.isNotEmpty()) {
                    "$name[${properties.entries.joinToString(",") { "${it.key}=${it.value}" }}]"
                } else {
                    name
                }
                Bukkit.createBlockData(stateStr)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun calculateBits(paletteSize: Int): Int {
        var bits = 4
        while ((1 shl bits) < paletteSize && bits < 32) bits++
        return bits
    }
    
    private fun getBlockState(index: Long, bits: Int, blockStates: LongArray): Long {
        if (bits == 0) return 0
        val blocksPerLong = 64 / bits
        val longIndex = (index / blocksPerLong).toInt()
        if (longIndex >= blockStates.size) return 0
        val bitIndex = (index % blocksPerLong).toInt()
        return (blockStates[longIndex] ushr (bitIndex * bits)) and ((1L shl bits) - 1)
    }
}
