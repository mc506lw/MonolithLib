package top.mc506lw.monolith.core.io

import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.transform.BlockStateRotator

object RotatedShape {
    
    fun create(shape: Shape, rotationSteps: Int): Shape {
        if (rotationSteps == 0 || shape.blocks.isEmpty()) {
            return shape
        }
        
        val rotatedBlocks = mutableListOf<BlockEntry>()
        
        for (block in shape.blocks) {
            val rotatedPos = rotatePosition(block.position, rotationSteps)
            val rotatedData = BlockStateRotator.rotate(block.blockData, rotationSteps)
            
            rotatedBlocks.add(BlockEntry(rotatedPos, rotatedData))
        }
        
        return Shape(rotatedBlocks)
    }
    
    private fun rotatePosition(pos: Vector3i, steps: Int): Vector3i {
        return when (steps % 4) {
            0 -> pos
            1 -> Vector3i(-pos.z, pos.y, pos.x)
            2 -> Vector3i(-pos.x, pos.y, -pos.z)
            3 -> Vector3i(pos.z, pos.y, -pos.x)
            else -> pos
        }
    }
}
