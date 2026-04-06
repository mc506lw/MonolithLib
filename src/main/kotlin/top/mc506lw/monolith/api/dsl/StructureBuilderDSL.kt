package top.mc506lw.monolith.api.dsl

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.Predicate
import top.mc506lw.monolith.core.predicate.Predicates
import top.mc506lw.monolith.core.structure.MonolithStructure

class StructureBuilderDSL(private val id: String) {
    private var sizeX: Int = 1
    private var sizeY: Int = 1
    private var sizeZ: Int = 1
    private var centerOffset: Vector3i = Vector3i.ZERO
    
    private val blocks = mutableListOf<BlockEntry>()
    private var controllerRebarKey: NamespacedKey? = null
    private val slots = mutableMapOf<String, Vector3i>()
    private val customData = mutableMapOf<String, Any>()
    private var metaName: String = id
    private var metaDescription: String = ""
    private var metaAuthor: String = ""
    private var metaVersion: String = "1.0"
    
    private data class BlockEntry(val x: Int, val y: Int, val z: Int, val predicate: Predicate, val slotId: String? = null)
    
    fun size(x: Int, y: Int, z: Int) {
        this.sizeX = x
        this.sizeY = y
        this.sizeZ = z
    }
    
    fun center(x: Int, y: Int, z: Int) {
        this.centerOffset = Vector3i(x, y, z)
    }
    
    fun controllerRebar(key: String) {
        val parts = key.split(":")
        this.controllerRebarKey = when {
            parts.size == 2 -> NamespacedKey(parts[0], parts[1])
            parts.size == 1 -> NamespacedKey("minecraft", parts[0])
            else -> null
        }
    }
    
    fun controllerRebar(key: NamespacedKey) {
        this.controllerRebarKey = key
    }
    
    fun block(x: Int, y: Int, z: Int, predicate: Predicate) {
        blocks.add(BlockEntry(x, y, z, predicate))
    }
    
    fun block(x: Int, y: Int, z: Int, blockData: BlockData) {
        blocks.add(BlockEntry(x, y, z, Predicates.strict(blockData)))
    }
    
    fun block(x: Int, y: Int, z: Int, material: Material) {
        blocks.add(BlockEntry(x, y, z, Predicates.strict(Bukkit.createBlockData(material))))
    }
    
    fun block(x: Int, y: Int, z: Int, blockDataStr: String) {
        val blockData = Bukkit.createBlockData(blockDataStr)
        blocks.add(BlockEntry(x, y, z, Predicates.strict(blockData)))
    }
    
    fun loose(x: Int, y: Int, z: Int, material: Material, ignoredStates: Set<String> = emptySet()) {
        val blockData = Bukkit.createBlockData(material)
        blocks.add(BlockEntry(x, y, z, Predicates.loose(material = material, preview = blockData)))
    }
    
    fun loose(x: Int, y: Int, z: Int, blockData: BlockData, ignoredStates: Set<String> = emptySet()) {
        blocks.add(BlockEntry(x, y, z, Predicates.loose(material = blockData.material, preview = blockData)))
    }
    
    fun rebar(x: Int, y: Int, z: Int, key: String, preview: Material) {
        blocks.add(BlockEntry(x, y, z, Predicates.rebar(key, preview)))
    }
    
    fun rebar(x: Int, y: Int, z: Int, key: String, preview: BlockData) {
        blocks.add(BlockEntry(x, y, z, Predicates.rebar(key, preview)))
    }
    
    fun rebar(x: Int, y: Int, z: Int, key: NamespacedKey, preview: BlockData) {
        blocks.add(BlockEntry(x, y, z, Predicates.rebar(key, preview)))
    }
    
    fun air(x: Int, y: Int, z: Int) {
        blocks.add(BlockEntry(x, y, z, Predicates.air()))
    }
    
    fun any(x: Int, y: Int, z: Int) {
        blocks.add(BlockEntry(x, y, z, Predicates.any()))
    }
    
    fun slot(slotId: String, x: Int, y: Int, z: Int) {
        slots[slotId] = Vector3i(x, y, z)
    }
    
    fun slotBlock(slotId: String, x: Int, y: Int, z: Int, predicate: Predicate) {
        blocks.add(BlockEntry(x, y, z, predicate, slotId))
        slots[slotId] = Vector3i(x, y, z)
    }
    
    fun slotBlock(slotId: String, x: Int, y: Int, z: Int, blockData: BlockData) {
        blocks.add(BlockEntry(x, y, z, Predicates.strict(blockData), slotId))
        slots[slotId] = Vector3i(x, y, z)
    }
    
    fun slotRebar(slotId: String, x: Int, y: Int, z: Int, key: String, preview: Material) {
        blocks.add(BlockEntry(x, y, z, Predicates.rebar(key, preview), slotId))
        slots[slotId] = Vector3i(x, y, z)
    }
    
    fun custom(key: String, value: Any) {
        customData[key] = value
    }
    
    fun meta(name: String, description: String = "", author: String = "", version: String = "1.0") {
        this.metaName = name
        this.metaDescription = description
        this.metaAuthor = author
        this.metaVersion = version
    }
    
    internal fun build(): MonolithStructure {
        val builder = MonolithStructure.Builder(id)
            .size(sizeX, sizeY, sizeZ)
            .center(centerOffset)
        
        if (controllerRebarKey != null) {
            builder.controllerRebar(controllerRebarKey!!)
        }
        
        for ((slotId, pos) in slots) {
            builder.slot(slotId, pos)
        }
        
        for ((key, value) in customData) {
            builder.customData(key, value)
        }
        
        builder.meta(metaName, metaDescription, metaAuthor, metaVersion)
        
        for (entry in blocks) {
            builder.set(entry.x, entry.y, entry.z, entry.predicate)
        }
        
        return builder.build()
    }
}

fun buildStructure(id: String, init: StructureBuilderDSL.() -> Unit): MonolithStructure {
    return StructureBuilderDSL(id).apply(init).build()
}

fun org.bukkit.plugin.java.JavaPlugin.registerMonolithStructure(id: String, init: StructureBuilderDSL.() -> Unit): MonolithStructure {
    val structure = buildStructure(id, init)
    top.mc506lw.monolith.core.structure.StructureRegistry.getInstance().register(structure)
    return structure
}
