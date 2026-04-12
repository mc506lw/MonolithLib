package top.mc506lw.monolith.core.io.formats

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.io.NbtCompound
import top.mc506lw.monolith.core.io.NbtReader
import top.mc506lw.monolith.core.io.StructureSerializer
import top.mc506lw.monolith.core.io.getNbtCompound
import top.mc506lw.monolith.core.io.getNbtInt
import top.mc506lw.monolith.core.io.getNbtList
import java.util.logging.Level
import top.mc506lw.monolith.core.io.getNbtLongArray
import top.mc506lw.monolith.core.io.getNbtString
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.Shape
import java.io.*
import java.util.zip.GZIPInputStream

object LitematicFormat : StructureSerializer {
    
    override val formatName: String = "Litematica"
    override val fileExtension: String = ".litematic"
    
    fun load(file: File): Blueprint? {
        return load(file, file.nameWithoutExtension)
    }
    
    fun load(file: File, blueprintId: String): Blueprint? {
        return try {
            file.inputStream().use { input ->
                deserialize(input, blueprintId)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "[MonolithLib] LitematicFormat 加载失败: ${file.absolutePath}", e)
            null
        }
    }
    
    override fun serialize(blueprint: Blueprint, output: OutputStream) {
        throw UnsupportedOperationException("暂不支持导出为 .litematic 格式")
    }
    
    override fun deserialize(input: InputStream): Blueprint? {
        return deserialize(input, null)
    }
    
    fun deserialize(input: InputStream, blueprintId: String? = null): Blueprint {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        Bukkit.getLogger().info("[MonolithLib] LitematicFormat 根标签: ${root.keys}")
        
        val metadata = root.getNbtCompound("Metadata") ?: emptyMap()
        val schematicName = metadata.getNbtString("Name") ?: "unnamed"
        
        val finalId = blueprintId ?: schematicName
        
        val regions = root.getNbtCompound("Regions") ?: emptyMap()
        Bukkit.getLogger().info("[MonolithLib] LitematicFormat: name=$schematicName, regions=${regions.keys}")
        
        val allBlocks = mutableListOf<BlockEntry>()
        
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        
        for ((regionName, regionValue) in regions) {
            val region = (regionValue as? NbtCompound)?.value ?: continue
            Bukkit.getLogger().info("[MonolithLib] LitematicFormat 区域 '$regionName' keys: ${region.keys}")
            
            val position = region.getNbtCompound("Position") ?: continue
            val size = region.getNbtCompound("Size") ?: continue
            val blockStates = region.getNbtLongArray("BlockStates") ?: continue
            val paletteList = region.getNbtList("BlockStatePalette") ?: continue
            
            var posX = position.getNbtInt("x") ?: 0
            var posY = position.getNbtInt("y") ?: 0
            var posZ = position.getNbtInt("z") ?: 0
            
            var sizeX = size.getNbtInt("x") ?: 1
            var sizeY = size.getNbtInt("y") ?: 1
            var sizeZ = size.getNbtInt("z") ?: 1
            
            Bukkit.getLogger().info("[MonolithLib] LitematicFormat 区域 '$regionName': pos=($posX,$posY,$posZ), size=($sizeX,$sizeY,$sizeZ), palette=${paletteList.size}, blockStates=${blockStates.size}")
            
            if (sizeX < 0) {
                posX += sizeX + 1
                sizeX = -sizeX
            }
            if (sizeY < 0) {
                posY += sizeY + 1
                sizeY = -sizeY
            }
            if (sizeZ < 0) {
                posZ += sizeZ + 1
                sizeZ = -sizeZ
            }
            
            val palette = parsePalette(paletteList)
            val bits = calculateBits(palette.size)
            
            for (y in 0 until sizeY) {
                for (z in 0 until sizeZ) {
                    for (x in 0 until sizeX) {
                        val index = (y * sizeX * sizeZ + z * sizeX + x).toLong()
                        val stateId = getBlockState(index, bits, blockStates)
                        
                        val blockData = palette.getOrNull(stateId.toInt())
                        if (blockData != null && !blockData.material.isAir) {
                            val worldX = x + posX
                            val worldY = y + posY
                            val worldZ = z + posZ
                            
                            allBlocks.add(BlockEntry(
                                position = Vector3i(worldX, worldY, worldZ),
                                blockData = blockData
                            ))
                            
                            minX = minOf(minX, worldX)
                            minY = minOf(minY, worldY)
                            minZ = minOf(minZ, worldZ)
                            maxX = maxOf(maxX, worldX)
                            maxY = maxOf(maxY, worldY)
                            maxZ = maxOf(maxZ, worldZ)
                        }
                    }
                }
            }
        }
        
        val offsetX = if (minX != Int.MAX_VALUE) minX else 0
        val offsetY = if (minY != Int.MAX_VALUE) minY else 0
        val offsetZ = if (minZ != Int.MAX_VALUE) minZ else 0
        
        val adjustedBlocks = allBlocks.map { block ->
            BlockEntry(
                position = Vector3i(
                    block.position.x - offsetX,
                    block.position.y - offsetY,
                    block.position.z - offsetZ
                ),
                blockData = block.blockData
            )
        }.filter { !it.blockData.material.isAir }
        
        val shape = Shape(adjustedBlocks)
        
        return Blueprint(
            id = finalId,
            shape = shape,
            meta = BlueprintMeta(displayName = finalId)
        )
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
    
    private fun calculateBits(paletteSize: Int): Int {
        if (paletteSize <= 1) return 2
        var bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1)
        return maxOf(bits, 2)
    }
    
    private fun getBlockState(index: Long, bits: Int, longArray: LongArray): Int {
        val startOffset = index * bits
        val startArrIndex = (startOffset ushr 6).toInt()
        val endArrIndex = (((index + 1) * bits - 1) ushr 6).toInt()
        
        val startBitOffset = (startOffset and 0x3F).toInt()
        val maxEntryValue = (-1L ushr (64 - bits))
        
        return if (startArrIndex == endArrIndex) {
            val value = longArray[startArrIndex]
            ((value ushr startBitOffset) and maxEntryValue).toInt()
        } else {
            if (endArrIndex >= longArray.size) return 0
            val endBitOffset = 64 - startBitOffset
            val firstPart = longArray[startArrIndex]
            val secondPart = longArray[endArrIndex]
            ((firstPart ushr startBitOffset) or (secondPart shl endBitOffset) and maxEntryValue).toInt()
        }
    }
}
