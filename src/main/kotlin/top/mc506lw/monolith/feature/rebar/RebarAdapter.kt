package top.mc506lw.monolith.feature.rebar

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.RebarPredicate

object RebarAdapter {
    
    @JvmStatic
    fun isRebarBlock(block: Block): Boolean {
        return try {
            block.chunk.isLoaded && BlockStorage.isRebarBlock(block)
        } catch (e: Exception) {
            false
        }
    }
    
    @JvmStatic
    fun isRebarBlock(block: Block, key: NamespacedKey): Boolean {
        return try {
            if (!block.chunk.isLoaded) return false
            val rebarBlock = BlockStorage.get(block) ?: return false
            rebarBlock.schema.key == key
        } catch (e: Exception) {
            false
        }
    }
    
    @JvmStatic
    fun getRebarBlock(block: Block): io.github.pylonmc.rebar.block.RebarBlock? {
        return try {
            if (block.chunk.isLoaded) BlockStorage.get(block) else null
        } catch (e: Exception) {
            null
        }
    }
    
    @JvmStatic
    fun getRebarBlockKey(block: Block): NamespacedKey? {
        return try {
            if (!block.chunk.isLoaded) return null
            BlockStorage.get(block)?.schema?.key
        } catch (e: Exception) {
            null
        }
    }
    
    @JvmStatic
    fun createRebarPredicate(block: Block): Predicate? {
        return if (isRebarBlock(block)) {
            val key = getRebarBlockKey(block) ?: return null
            val previewBlockData = block.blockData
            RebarPredicate(key, previewBlockData)
        } else {
            null
        }
    }
}
