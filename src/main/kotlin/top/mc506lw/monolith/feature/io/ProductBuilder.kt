package top.mc506lw.monolith.feature.io

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.*
import top.mc506lw.monolith.core.structure.MonolithStructure

object ProductBuilder {
    
    fun build(baseStructure: MonolithStructure, config: BlueprintConfig): MonolithStructure {
        val builder = MonolithStructure.Builder(config.id)
            .size(baseStructure.sizeX, baseStructure.sizeY, baseStructure.sizeZ)
            .center(baseStructure.centerOffset)
        
        if (config.controllerRebarKey != null) {
            builder.controllerRebar(config.controllerRebarKey)
        }
        
        for ((slotId, pos) in config.slots) {
            builder.slot(slotId, pos)
        }
        
        for ((key, value) in config.customData) {
            builder.customData(key, value)
        }
        
        builder.meta(config.metaName, config.metaDescription, config.metaAuthor, config.version)
        
        val controllerPos = config.controllerPosition ?: baseStructure.centerOffset
        
        for (z in 0 until baseStructure.sizeZ) {
            for (y in 0 until baseStructure.sizeY) {
                for (x in 0 until baseStructure.sizeX) {
                    val pos = Vector3i(x, y, z)
                    
                    val basePredicate = baseStructure.getPredicate(x, y, z)
                    
                    val predicate = if (pos == controllerPos && config.controllerRebarKey != null) {
                        val originalPreview = basePredicate?.previewBlockData
                        createControllerPredicate(config.controllerRebarKey, originalPreview)
                    } else {
                        config.overrides[pos]?.let { createPredicateFromOverride(it) }
                            ?: basePredicate
                    }
                    
                    if (predicate != null && predicate !is AirPredicate) {
                        builder.set(x, y, z, predicate)
                    }
                }
            }
        }
        
        return builder.build()
    }
    
    private fun createControllerPredicate(key: org.bukkit.NamespacedKey, originalPreview: BlockData?): Predicate {
        val preview = originalPreview ?: Bukkit.createBlockData(Material.STRUCTURE_BLOCK)
        return RebarPredicate(key, preview)
    }
    
    private fun createPredicateFromOverride(entry: BlueprintConfig.OverrideEntry): Predicate? {
        return when (entry.type.lowercase()) {
            "strict" -> {
                val material = entry.material ?: return null
                StrictPredicate(Bukkit.createBlockData(material), entry.ignoreStates)
            }
            "loose" -> {
                val material = entry.material ?: return null
                val preview = entry.previewMaterial?.let { Bukkit.createBlockData(it) }
                    ?: Bukkit.createBlockData(material)
                LoosePredicate(material = material, previewBlockData = preview)
            }
            "rebar" -> {
                val key = entry.rebarKey ?: return null
                val previewMaterial = entry.previewMaterial ?: Material.STONE
                RebarPredicate(key, Bukkit.createBlockData(previewMaterial))
            }
            "air" -> AirPredicate
            "any" -> AnyPredicate
            else -> null
        }
    }
}
