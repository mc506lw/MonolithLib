package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.validation.predicate.*

object ProductBuilder {
    
    fun build(baseBlueprint: Blueprint, config: BlueprintConfig): Blueprint {
        val baseShape: Shape = baseBlueprint.shape
        
        val builder = Blueprint.builder(config.id)
            .shape(baseShape)
            .displayName(config.metaName ?: baseBlueprint.meta.displayName)
            .description(config.metaDescription ?: baseBlueprint.meta.description)
            .author(config.metaAuthor ?: baseBlueprint.meta.author)
            .version(config.version ?: baseBlueprint.meta.version)
            .controllerOffset(config.controllerPosition ?: baseBlueprint.meta.controllerOffset)
        
        if (config.controllerRebarKey != null) {
            builder.controllerRebar(config.controllerRebarKey)
        }
        
        for ((slotId, pos) in config.slots) {
            builder.slot(slotId, pos)
        }
        
        for ((key, value) in config.customData) {
            builder.customData(key, value)
        }
        
        val controllerPos = config.controllerPosition ?: baseBlueprint.meta.controllerOffset
        
        val newBlocks = mutableListOf<BlockEntry>()
        
        for (blockEntry in baseShape.blocks) {
            val pos = blockEntry.position
            
            val predicate = if (pos == controllerPos && config.controllerRebarKey != null) {
                createControllerPredicate(config.controllerRebarKey, blockEntry.blockData)
            } else {
                config.overrides[pos]?.let { createPredicateFromOverride(it) }
                    ?: Predicates.strict(blockEntry.blockData)
            }
            
            if (predicate != null && predicate !is AirPredicate) {
                val newData = when (predicate) {
                    is StrictPredicate -> predicate.previewBlockData ?: blockEntry.blockData
                    is LoosePredicate -> predicate.previewBlockData ?: blockEntry.blockData
                    is RebarPredicate -> predicate.previewBlockData ?: blockEntry.blockData
                    else -> blockEntry.blockData
                }
                
                newBlocks.add(BlockEntry(
                    position = pos,
                    blockData = newData.clone()
                ))
            }
        }
        
        builder.shape(newBlocks)
        
        return builder.build()
    }
    
    private fun createControllerPredicate(key: org.bukkit.NamespacedKey, originalData: BlockData?): Predicate {
        val preview = originalData ?: Bukkit.createBlockData(Material.STRUCTURE_BLOCK)
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
