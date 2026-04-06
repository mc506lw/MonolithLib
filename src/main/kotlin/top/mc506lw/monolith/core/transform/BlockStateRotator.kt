package top.mc506lw.monolith.core.transform

import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.Stairs

object BlockStateRotator {
    
    fun rotate(blockData: BlockData, rotationSteps: Int): BlockData {
        if (rotationSteps == 0) return blockData
        
        val rotated = blockData.clone()
        
        when (rotated) {
            is Stairs -> rotateStairs(rotated, rotationSteps)
            is Directional -> rotateDirectional(rotated, rotationSteps)
        }
        
        return rotated
    }
    
    private fun rotateStairs(stairs: Stairs, rotationSteps: Int) {
        val originalFacing = stairs.facing
        val originalShape = stairs.shape
        
        stairs.facing = rotateBlockFace(originalFacing, rotationSteps)
        
        stairs.shape = when (originalShape) {
            Stairs.Shape.STRAIGHT -> Stairs.Shape.STRAIGHT
            Stairs.Shape.INNER_LEFT, Stairs.Shape.INNER_RIGHT -> {
                when (rotationSteps % 4) {
                    0 -> originalShape
                    1, 3 -> if (originalShape == Stairs.Shape.INNER_LEFT) Stairs.Shape.INNER_RIGHT else Stairs.Shape.INNER_LEFT
                    2 -> originalShape
                    else -> originalShape
                }
            }
            Stairs.Shape.OUTER_LEFT, Stairs.Shape.OUTER_RIGHT -> {
                when (rotationSteps % 4) {
                    0 -> originalShape
                    1, 3 -> if (originalShape == Stairs.Shape.OUTER_LEFT) Stairs.Shape.OUTER_RIGHT else Stairs.Shape.OUTER_LEFT
                    2 -> originalShape
                    else -> originalShape
                }
            }
        }
    }
    
    private fun rotateDirectional(directional: Directional, rotationSteps: Int) {
        directional.facing = rotateBlockFace(directional.facing, rotationSteps)
    }
    
    private fun rotateBlockFace(face: BlockFace, steps: Int): BlockFace {
        val horizontalFaces = listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
        val currentIndex = horizontalFaces.indexOf(face)
        if (currentIndex == -1) return face
        
        val newIndex = (currentIndex + steps) % 4
        return horizontalFaces[newIndex]
    }
}
