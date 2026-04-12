package top.mc506lw.monolith.core.io.formats

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import top.mc506lw.monolith.core.io.StructureSerializer
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BlueprintMeta
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.validation.predicate.*
import java.io.*
import java.util.logging.Level

object BinaryFormat : StructureSerializer {
    
    private const val MAGIC: Int = 0x4D4E4231
    private const val CURRENT_VERSION = 3
    
    override val formatName: String = "Monolith Binary"
    override val fileExtension: String = ".mnb"
    
    fun save(blueprint: Blueprint, file: File, formatVersion: Int, configHash: String) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            serialize(blueprint, output, formatVersion, configHash)
        }
    }
    
    fun load(file: File): Blueprint? {
        return try {
            file.inputStream().use { input ->
                deserialize(input)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "[MonolithLib] BinaryFormat 加载失败: ${file.absolutePath}", e)
            null
        }
    }
    
    fun serialize(blueprint: Blueprint, output: OutputStream, formatVersion: Int = CURRENT_VERSION, configHash: String = "") {
        DataOutputStream(BufferedOutputStream(output)).use { dos ->
            dos.writeInt(MAGIC)
            dos.writeInt(CURRENT_VERSION)
            dos.writeInt(formatVersion)
            
            writeString(dos, configHash)
            
            writeString(dos, blueprint.id)
            
            val shape = blueprint.shape
            dos.writeShort(shape.boundingBox.width)
            dos.writeShort(shape.boundingBox.height)
            dos.writeShort(shape.boundingBox.depth)
            
            val centerOffset = blueprint.meta.controllerOffset
            dos.writeShort(centerOffset.x)
            dos.writeShort(centerOffset.y)
            dos.writeShort(centerOffset.z)
            
            val controllerKey = blueprint.controllerRebarKey
            dos.writeBoolean(controllerKey != null)
            if (controllerKey != null) {
                writeString(dos, controllerKey.toString())
            }
            
            writeString(dos, blueprint.meta.displayName)
            writeString(dos, blueprint.meta.description)
            writeString(dos, blueprint.meta.author)
            writeString(dos, blueprint.meta.version)
            
            dos.writeInt(blueprint.slots.size)
            for ((slotId, pos) in blueprint.slots) {
                writeString(dos, slotId)
                dos.writeShort(pos.x)
                dos.writeShort(pos.y)
                dos.writeShort(pos.z)
            }
            
            dos.writeInt(blueprint.customData.size)
            for ((key, value) in blueprint.customData) {
                writeString(dos, key)
                writeValue(dos, value)
            }
            
            dos.writeInt(shape.blocks.size)
            
            for (blockEntry in shape.blocks) {
                dos.writeShort(blockEntry.position.x)
                dos.writeShort(blockEntry.position.y)
                dos.writeShort(blockEntry.position.z)
                
                val predicate = Predicates.strict(blockEntry.blockData)
                writePredicate(dos, predicate)
                
                writeString(dos, blockEntry.blockData.asString)
            }
        }
    }
    
    override fun serialize(blueprint: Blueprint, output: OutputStream) {
        @Suppress("UNCHECKED_CAST")
        serialize(blueprint as Blueprint, output, CURRENT_VERSION, "")
    }
    
    override fun deserialize(input: InputStream): Blueprint {
        DataInputStream(BufferedInputStream(input)).use { dis ->
            val magic = dis.readInt()
            require(magic == MAGIC) { "无效的 Monolith Binary 文件" }
            
            val version = dis.readInt()
            require(version <= CURRENT_VERSION) { "不支持的版本: $version" }
            
            val formatVersion = if (version >= 3) dis.readInt() else 1
            val configHash = if (version >= 3) readString(dis) else ""
            
            val id = readString(dis)
            val sizeX = dis.readUnsignedShort()
            val sizeY = dis.readUnsignedShort()
            val sizeZ = dis.readUnsignedShort()
            
            val centerX = dis.readShort().toInt()
            val centerY = dis.readShort().toInt()
            val centerZ = dis.readShort().toInt()
            
            val controllerRebarKey = if (version >= 3 && dis.readBoolean()) {
                parseNamespacedKey(readString(dis))
            } else null
            
            val meta = if (version >= 3) {
                BlueprintMeta(
                    displayName = readString(dis),
                    description = readString(dis),
                    author = readString(dis),
                    version = readString(dis),
                    controllerOffset = Vector3i(centerX, centerY, centerZ)
                )
            } else BlueprintMeta(controllerOffset = Vector3i(centerX, centerY, centerZ))
            
            val slots = mutableMapOf<String, Vector3i>()
            if (version >= 3) {
                val slotCount = dis.readInt()
                repeat(slotCount) {
                    val slotId = readString(dis)
                    val x = dis.readShort().toInt()
                    val y = dis.readShort().toInt()
                    val z = dis.readShort().toInt()
                    slots[slotId] = Vector3i(x, y, z)
                }
            }
            
            val customData = mutableMapOf<String, Any>()
            if (version >= 3) {
                val dataCount = dis.readInt()
                repeat(dataCount) {
                    val key = readString(dis)
                    val value = readValue(dis)
                    customData[key] = value
                }
            }
            
            val blockCount = dis.readInt()
            Bukkit.getLogger().info("[MonolithLib] BinaryFormat 诊断: version=$version, blockCount=$blockCount, id=$id")
            val blocks = mutableListOf<BlockEntry>()
            var skippedBlocks = 0
            
            repeat(blockCount) { blockIndex ->
                var x = 0
                var y = 0
                var z = 0
                try {
                    x = dis.readShort().toInt()
                    y = dis.readShort().toInt()
                    z = dis.readShort().toInt()
                    
                    val predicate = if (version >= 3) {
                        readPredicate(dis)
                    } else {
                        val blockDataStr = readString(dis)
                        val blockData = if (blockDataStr.isNotEmpty()) {
                            try { Bukkit.createBlockData(blockDataStr) } catch (e: Exception) { null }
                        } else null
                        if (blockData != null) StrictPredicate(blockData) else AirPredicate
                    }
                    
                    val blockDataStr = readString(dis)
                    val blockData = if (blockDataStr.isNotEmpty()) {
                        try { Bukkit.createBlockData(blockDataStr) } catch (e: Exception) { null }
                    } else predicate.previewBlockData
                    
                    if (blockData != null) {
                        blocks.add(BlockEntry(
                            position = Vector3i(x, y, z),
                            blockData = blockData
                        ))
                    } else {
                        skippedBlocks++
                    }
                } catch (e: EOFException) {
                    if (blockIndex == 0) {
                        Bukkit.getLogger().warning("[MonolithLib] 第一个方块读取失败 (EOF): version=$version, 位置已读取: ($x,$y,$z)")
                    }
                    skippedBlocks++
                } catch (e: Exception) {
                    if (blockIndex == 0) {
                        Bukkit.getLogger().warning("[MonolithLib] 第一个方块读取失败: ${e.message}")
                    }
                    skippedBlocks++
                }
            }
            
            if (skippedBlocks > 0) {
                Bukkit.getLogger().warning("[MonolithLib] 跳过 $skippedBlocks 个损坏的方块数据 (文件版本: $version)")
            }
            
            val shape = Shape(blocks)
            
            return Blueprint(
                id = id,
                shape = shape,
                meta = meta,
                slots = slots.toMap(),
                customData = customData.toMap(),
                controllerRebarKey = controllerRebarKey
            )
        }
    }
    
    private fun writePredicate(dos: DataOutputStream, predicate: Predicate) {
        when (predicate) {
            is AirPredicate -> {
                dos.writeByte(0)
            }
            is AnyPredicate -> {
                dos.writeByte(1)
            }
            is StrictPredicate -> {
                dos.writeByte(2)
                writeString(dos, predicate.previewBlockData?.asString ?: "")
            }
            is LoosePredicate -> {
                dos.writeByte(3)
                writeString(dos, predicate.previewBlockData?.asString ?: "")
                val ignoredStates = predicate.ignoredStates
                dos.writeInt(ignoredStates.size)
                ignoredStates.forEach { writeString(dos, it) }
            }
            is RebarPredicate -> {
                dos.writeByte(4)
                writeString(dos, predicate.key.toString())
                writeString(dos, predicate.previewBlockData?.asString ?: "")
            }
            else -> {
                dos.writeByte(5)
                writeString(dos, predicate.previewBlockData?.asString ?: "")
                writeString(dos, predicate.hint ?: "")
            }
        }
    }
    
    private fun readPredicate(dis: DataInputStream): Predicate {
        val type = dis.readUnsignedByte()
        return when (type) {
            0 -> AirPredicate
            1 -> AnyPredicate
            2 -> {
                val blockDataStr = readString(dis)
                val blockData = if (blockDataStr.isNotEmpty()) {
                    try { Bukkit.createBlockData(blockDataStr) } catch (e: Exception) { null }
                } else null
                if (blockData != null) StrictPredicate(blockData) else AirPredicate
            }
            3 -> {
                val previewStr = readString(dis)
                val preview = if (previewStr.isNotEmpty()) {
                    try { Bukkit.createBlockData(previewStr) } catch (e: Exception) { null }
                } else null
                val ignoredCount = dis.readInt()
                val ignoredStates = mutableSetOf<String>()
                repeat(ignoredCount) {
                    ignoredStates.add(readString(dis))
                }
                if (preview != null) LoosePredicate(preview, ignoredStates) else AirPredicate
            }
            4 -> {
                val keyStr = readString(dis)
                val previewStr = readString(dis)
                val key = parseNamespacedKey(keyStr)
                val preview = if (previewStr.isNotEmpty()) {
                    try { Bukkit.createBlockData(previewStr) } catch (e: Exception) { null }
                } else null
                if (preview != null) RebarPredicate(key, preview) else AirPredicate
            }
            else -> {
                val previewStr = readString(dis)
                val hint = readString(dis)
                val preview = if (previewStr.isNotEmpty()) {
                    try { Bukkit.createBlockData(previewStr) } catch (e: Exception) { null }
                } else null
                if (preview != null) StrictPredicate(preview) else AirPredicate
            }
        }
    }
    
    private fun writeValue(dos: DataOutputStream, value: Any) {
        when (value) {
            is String -> {
                dos.writeByte(0)
                writeString(dos, value)
            }
            is Int -> {
                dos.writeByte(1)
                dos.writeInt(value)
            }
            is Double -> {
                dos.writeByte(2)
                dos.writeDouble(value)
            }
            is Boolean -> {
                dos.writeByte(3)
                dos.writeBoolean(value)
            }
            is List<*> -> {
                dos.writeByte(4)
                dos.writeInt(value.size)
                for (item in value) {
                    if (item != null) writeValue(dos, item)
                }
            }
            is Map<*, *> -> {
                dos.writeByte(5)
                dos.writeInt(value.size)
                @Suppress("UNCHECKED_CAST")
                for ((k, v) in value as Map<String, Any>) {
                    writeString(dos, k)
                    writeValue(dos, v)
                }
            }
            else -> {
                dos.writeByte(6)
                writeString(dos, value.toString())
            }
        }
    }
    
    private fun readValue(dis: DataInputStream): Any {
        return when (val type = dis.readUnsignedByte()) {
            0 -> readString(dis)
            1 -> dis.readInt()
            2 -> dis.readDouble()
            3 -> dis.readBoolean()
            4 -> {
                val count = dis.readInt()
                val list = mutableListOf<Any>()
                repeat(count) {
                    list.add(readValue(dis))
                }
                list
            }
            5 -> {
                val count = dis.readInt()
                val map = mutableMapOf<String, Any>()
                repeat(count) {
                    val key = readString(dis)
                    val value = readValue(dis)
                    map[key] = value
                }
                map
            }
            else -> readString(dis)
        }
    }
    
    private fun writeString(dos: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        dos.writeShort(bytes.size)
        dos.write(bytes)
    }
    
    private fun readString(dis: DataInputStream): String {
        val length = dis.readUnsignedShort()
        val bytes = ByteArray(length)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    
    private fun parseNamespacedKey(key: String): NamespacedKey {
        val parts = key.split(":")
        return when {
            parts.size == 2 -> NamespacedKey(parts[0], parts[1])
            parts.size == 1 -> NamespacedKey("minecraft", parts[0])
            else -> NamespacedKey("minecraft", key)
        }
    }
}
