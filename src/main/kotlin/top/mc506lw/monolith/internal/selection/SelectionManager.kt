package top.mc506lw.monolith.internal.selection

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Player
import top.mc506lw.monolith.core.math.Vector3i
import java.util.UUID

data class PlayerSelection(
    var pos1: Vector3i? = null,
    var pos2: Vector3i? = null,
    var worldName: String? = null
) {
    val isComplete: Boolean get() = pos1 != null && pos2 != null && worldName != null
    
    fun getMinPos(): Vector3i? {
        if (!isComplete) return null
        return Vector3i(
            minOf(pos1!!.x, pos2!!.x),
            minOf(pos1!!.y, pos2!!.y),
            minOf(pos1!!.z, pos2!!.z)
        )
    }
    
    fun getMaxPos(): Vector3i? {
        if (!isComplete) return null
        return Vector3i(
            maxOf(pos1!!.x, pos2!!.x),
            maxOf(pos1!!.y, pos2!!.y),
            maxOf(pos1!!.z, pos2!!.z)
        )
    }
    
    fun getVolume(): Int {
        val min = getMinPos() ?: return 0
        val max = getMaxPos() ?: return 0
        return (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1)
    }
}

object SelectionManager {
    
    private val selections = mutableMapOf<UUID, PlayerSelection>()
    
    fun getSelection(player: Player): PlayerSelection {
        return selections.getOrPut(player.uniqueId) { PlayerSelection() }
    }
    
    fun setPos1(player: Player, block: Block): Boolean {
        val selection = getSelection(player)
        selection.pos1 = Vector3i(block.x, block.y, block.z)
        selection.worldName = block.world.name
        
        player.sendMessage("\u00a7a[MonolithLib] Pos1 \u00a7fset to (${block.x}, ${block.y}, ${block.z})")
        
        if (selection.isComplete) {
            val vol = selection.getVolume()
            player.sendMessage("\u00a77[MonolithLib] 选区已完整: ${vol} 个方块")
        } else {
            player.sendMessage("\u00a77[MonolithLib] 请右键设置 Pos2")
        }
        
        showSelectionParticles(player)
        return true
    }
    
    fun setPos2(player: Player, block: Block): Boolean {
        val selection = getSelection(player)
        
        if (selection.pos1 == null || selection.worldName == null) {
            player.sendMessage("\u00a7c[MonolithLib] 请先左键设置 Pos1!")
            return false
        }
        
        if (block.world.name != selection.worldName) {
            player.sendMessage("\u00a7c[MonolithLib] Pos1 和 Pos2 必须在同一世界!")
            return false
        }
        
        selection.pos2 = Vector3i(block.x, block.y, block.z)
        
        player.sendMessage("\u00a7a[MonolithLib] Pos2 \u00a7fset to (${block.x}, ${block.y}, ${block.z})")
        
        if (selection.isComplete) {
            val vol = selection.getVolume()
            player.sendMessage("\u00a77[MonolithLib] 选区已完整: ${vol} 个方块")
            player.sendMessage("\u00a77[MonolithLib] 使用 /ml save <名称> 保存为 .raw.mnb")
        }
        
        showSelectionParticles(player)
        return true
    }
    
    fun clearSelection(player: Player) {
        selections.remove(player.uniqueId)
    }
    
    private fun showSelectionParticles(player: Player) {
        val selection = getSelection(player)
        if (!selection.isComplete) return
        
        val world = Bukkit.getWorld(selection.worldName!!) ?: return
        val min = selection.getMinPos()!!
        val max = selection.getMaxPos()!!
        
        val particle = Particle.END_ROD
        
        for (x in min.x..max.x) {
            for (z in min.z..max.z) {
                world.spawnParticle(particle, Location(world, x.toDouble() + 0.5, min.y.toDouble(), z.toDouble() + 0.5), 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, Location(world, x.toDouble() + 0.5, max.y.toDouble() + 1.0, z.toDouble() + 0.5), 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        
        for (y in min.y..max.y) {
            for (x in min.x..max.x) {
                world.spawnParticle(particle, Location(world, x.toDouble() + 0.5, y.toDouble() + 0.5, min.z.toDouble()), 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, Location(world, x.toDouble() + 0.5, y.toDouble() + 0.5, max.z.toDouble() + 1.0), 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        
        for (y in min.y..max.y) {
            for (z in min.z..max.z) {
                world.spawnParticle(particle, Location(world, min.x.toDouble(), y.toDouble() + 0.5, z.toDouble() + 0.5), 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, Location(world, max.x.toDouble() + 1.0, y.toDouble() + 0.5, z.toDouble() + 0.5), 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }
}
