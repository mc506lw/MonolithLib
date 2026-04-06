package top.mc506lw.monolith.core.transform

import org.bukkit.block.BlockFace

enum class Facing(val blockFace: BlockFace) {
    SOUTH(BlockFace.SOUTH),
    WEST(BlockFace.WEST),
    NORTH(BlockFace.NORTH),
    EAST(BlockFace.EAST);
    
    val rotationSteps: Int get() = ordinal
    
    fun rotateClockwise(): Facing = entries[(ordinal + 1) % 4]
    
    fun rotateCounterClockwise(): Facing = entries[(ordinal + 3) % 4]
    
    fun opposite(): Facing = entries[(ordinal + 2) % 4]
    
    companion object {
        fun fromBlockFace(face: BlockFace): Facing? = when (face) {
            BlockFace.NORTH -> NORTH
            BlockFace.EAST -> EAST
            BlockFace.SOUTH -> SOUTH
            BlockFace.WEST -> WEST
            else -> null
        }
        
        fun fromYaw(yaw: Float): Facing {
            val normalizedYaw = ((yaw % 360) + 360) % 360
            return when {
                normalizedYaw < 45 || normalizedYaw >= 315 -> SOUTH
                normalizedYaw < 135 -> WEST
                normalizedYaw < 225 -> NORTH
                else -> EAST
            }
        }
    }
}
