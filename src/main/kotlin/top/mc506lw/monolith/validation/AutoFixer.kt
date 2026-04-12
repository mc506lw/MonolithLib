package top.mc506lw.monolith.validation

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.rebar.MonolithLib
import java.util.concurrent.CompletableFuture

object AutoFixer {
    
    private const val MAX_FIXES_PER_TICK = 500
    
    fun fixBlocks(
        blocksToFix: List<BlockFixEntry>,
        world: World,
        maxPerTick: Int = MAX_FIXES_PER_TICK
    ): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        
        if (blocksToFix.isEmpty()) {
            future.complete(0)
            return future
        }
        
        val iterator = blocksToFix.iterator()
        val fixedCount = intArrayOf(0)
        
        val task = object : Runnable {
            override fun run() {
                var fixedThisTick = 0
                
                while (iterator.hasNext() && fixedThisTick < maxPerTick) {
                    val entry = iterator.next()
                    val block = world.getBlockAt(entry.worldPos.x, entry.worldPos.y, entry.worldPos.z)
                    
                    if (block.blockData.material == entry.targetBlockData.material) {
                        block.blockData = entry.targetBlockData
                        fixedCount[0]++
                        fixedThisTick++
                    }
                }
                
                if (!iterator.hasNext()) {
                    if (fixedCount[0] > 0) {
                        playCompletionEffects(world, blocksToFix.first().worldPos)
                    }
                    future.complete(fixedCount[0])
                }
            }
        }
        
        Bukkit.getScheduler().runTaskTimer(MonolithLib.instance, task, 1L, 1L)
        
        return future
    }
    
    fun fixBlocksSync(
        blocksToFix: List<BlockFixEntry>,
        world: World
    ): Int {
        var fixedCount = 0
        
        for (entry in blocksToFix) {
            val block = world.getBlockAt(entry.worldPos.x, entry.worldPos.y, entry.worldPos.z)
            
            if (block.blockData.material == entry.targetBlockData.material) {
                block.blockData = entry.targetBlockData
                fixedCount++
            }
        }
        
        if (fixedCount > 0 && blocksToFix.isNotEmpty()) {
            playCompletionEffects(world, blocksToFix.first().worldPos)
        }
        
        return fixedCount
    }
    
    private fun playCompletionEffects(world: World, pos: Vector3i) {
        val location = org.bukkit.Location(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        
        world.spawnParticle(
            Particle.HAPPY_VILLAGER,
            location,
            20,
            1.0, 1.0, 1.0,
            0.1
        )
        
        world.playSound(location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f)
    }
}
