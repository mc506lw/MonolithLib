package top.mc506lw.monolith.feature.io.formats

import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.Predicates
import top.mc506lw.monolith.core.predicate.StrictPredicate
import top.mc506lw.monolith.core.structure.FlattenedBlock
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.feature.io.StructureSerializer
import top.mc506lw.monolith.feature.io.nbt.*
import java.io.*
import java.util.zip.GZIPInputStream

object LitematicFormat : StructureSerializer {
    
    override val formatName: String = "Litematica"
    override val fileExtension: String = ".litematic"
    
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
        throw UnsupportedOperationException("暂不支持导出为 .litematic 格式")
    }
    
    override fun deserialize(input: InputStream): MonolithStructure {
        val gzipInput = GZIPInputStream(input)
        val dataInput = DataInputStream(gzipInput)
        
        val root = NbtReader(dataInput).readRoot()
        
        val metadata = root.getNbtCompound("Metadata") ?: emptyMap()
        val schematicName = metadata.getNbtString("Name") ?: "unnamed"
        
        val regions = root.getNbtCompound("Regions") ?: emptyMap()
        
        val allBlocks = mutableListOf<FlattenedBlock>()
        
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        
        for ((regionName, regionValue) in regions) {
            val region = (regionValue as? NbtCompound)?.value ?: continue
            
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
                        
                        val blockData = palette.getOrNull(stateId)
                        if (blockData != null && !blockData.material.isAir) {
                            val worldX = x + posX
                            val worldY = y + posY
                            val worldZ = z + posZ
                            
                            allBlocks.add(FlattenedBlock(
                                relativePosition = Vector3i(worldX, worldY, worldZ),
                                predicate = StrictPredicate(blockData),
                                previewBlockData = blockData,
                                hint = blockData.asString
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
        
        val actualWidth = if (maxX >= minX) maxX - minX + 1 else 1
        val actualHeight = if (maxY >= minY) maxY - minY + 1 else 1
        val actualLength = if (maxZ >= minZ) maxZ - minZ + 1 else 1
        
        val builder = MonolithStructure.Builder(schematicName)
            .size(actualWidth, actualHeight, actualLength)
            .center(actualWidth / 2, 0, actualLength / 2)
        
        for (block in allBlocks) {
            val adjustedX = block.relativePosition.x - offsetX
            val adjustedY = block.relativePosition.y - offsetY
            val adjustedZ = block.relativePosition.z - offsetZ
            
            builder.set(
                adjustedX,
                adjustedY,
                adjustedZ,
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
