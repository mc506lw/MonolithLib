package top.mc506lw.monolith.validation

import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.base.RebarMultiblock
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.block.BlockPlaceEvent
import top.mc506lw.rebar.MonolithLib
import java.util.concurrent.CompletableFuture

object RebarTrigger {
    
    fun triggerFormation(controllerLocation: Location): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        Bukkit.getScheduler().runTask(MonolithLib.instance, Runnable {
            val result = triggerFormationSync(controllerLocation)
            future.complete(result)
        })
        
        return future
    }
    
    fun triggerFormationSync(controllerLocation: Location): Boolean {
        val block = controllerLocation.block
        
        val rebarBlock = BlockStorage.get(block) ?: return false
        
        if (rebarBlock !is RebarMultiblock) {
            return false
        }
        
        try {
            val multiblock = rebarBlock as RebarMultiblock
            
            if (multiblock.checkFormed()) {
                if (!multiblock.isFormedAndFullyLoaded()) {
                    multiblock.onMultiblockFormed()
                }
                return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    fun triggerFormationByBlockModification(controllerLocation: Location): Boolean {
        val block = controllerLocation.block
        val world = block.world
        
        val type = block.type
        val blockData = block.blockData
        
        block.type = Material.AIR
        
        Bukkit.getScheduler().runTaskLater(MonolithLib.instance, Runnable {
            block.type = type
            block.blockData = blockData
        }, 1L)
        
        return true
    }
    
    fun isRebarMultiblockController(location: Location): Boolean {
        val block = location.block
        val rebarBlock = BlockStorage.get(block) ?: return false
        return rebarBlock is RebarMultiblock
    }
    
    fun getRebarMultiblock(location: Location): RebarMultiblock? {
        val block = location.block
        val rebarBlock = BlockStorage.get(block) ?: return null
        return rebarBlock as? RebarMultiblock
    }
}
