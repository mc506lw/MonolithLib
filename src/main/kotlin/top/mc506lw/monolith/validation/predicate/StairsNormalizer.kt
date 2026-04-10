package top.mc506lw.monolith.validation.predicate

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Stairs

object StairsNormalizer {
    
    private val STAIR_MATERIALS = setOf(
        Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
        Material.JUNGLE_STAIRS, Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS,
        Material.MANGROVE_STAIRS, Material.CHERRY_STAIRS, Material.BAMBOO_STAIRS,
        Material.CRIMSON_STAIRS, Material.WARPED_STAIRS,
        Material.STONE_STAIRS, Material.COBBLESTONE_STAIRS, Material.MOSSY_COBBLESTONE_STAIRS,
        Material.STONE_BRICK_STAIRS, Material.MOSSY_STONE_BRICK_STAIRS,
        Material.GRANITE_STAIRS, Material.POLISHED_GRANITE_STAIRS,
        Material.DIORITE_STAIRS, Material.POLISHED_DIORITE_STAIRS,
        Material.ANDESITE_STAIRS, Material.POLISHED_ANDESITE_STAIRS,
        Material.SANDSTONE_STAIRS, Material.SMOOTH_SANDSTONE_STAIRS,
        Material.RED_SANDSTONE_STAIRS, Material.SMOOTH_RED_SANDSTONE_STAIRS,
        Material.BRICK_STAIRS, Material.PRISMARINE_STAIRS,
        Material.PRISMARINE_BRICK_STAIRS, Material.DARK_PRISMARINE_STAIRS,
        Material.NETHER_BRICK_STAIRS, Material.RED_NETHER_BRICK_STAIRS,
        Material.QUARTZ_STAIRS, Material.SMOOTH_QUARTZ_STAIRS,
        Material.PURPUR_STAIRS, Material.END_STONE_BRICK_STAIRS,
        Material.CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS,
        Material.WEATHERED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS,
        Material.WAXED_CUT_COPPER_STAIRS, Material.WAXED_EXPOSED_CUT_COPPER_STAIRS,
        Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
        Material.COBBLED_DEEPSLATE_STAIRS, Material.POLISHED_DEEPSLATE_STAIRS,
        Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_TILE_STAIRS,
        Material.TUFF_STAIRS, Material.POLISHED_TUFF_STAIRS, Material.TUFF_BRICK_STAIRS
    )
    
    fun isStairs(material: Material): Boolean = material in STAIR_MATERIALS
    
    fun isStairs(blockData: BlockData): Boolean = blockData is Stairs
    
    fun normalize(stairs: Stairs): NormalizedStairs {
        val facing = stairs.facing
        val shape = stairs.shape
        val half = stairs.half
        
        val normalizedShape = when (shape) {
            Stairs.Shape.OUTER_LEFT -> {
                when (facing) {
                    BlockFace.WEST -> NormalizedShape(facing = BlockFace.SOUTH, shape = Stairs.Shape.OUTER_RIGHT)
                    BlockFace.SOUTH -> NormalizedShape(facing = BlockFace.EAST, shape = Stairs.Shape.OUTER_RIGHT)
                    BlockFace.EAST -> NormalizedShape(facing = BlockFace.NORTH, shape = Stairs.Shape.OUTER_RIGHT)
                    BlockFace.NORTH -> NormalizedShape(facing = BlockFace.WEST, shape = Stairs.Shape.OUTER_RIGHT)
                    else -> NormalizedShape(facing, shape)
                }
            }
            Stairs.Shape.INNER_LEFT -> {
                when (facing) {
                    BlockFace.WEST -> NormalizedShape(facing = BlockFace.SOUTH, shape = Stairs.Shape.INNER_RIGHT)
                    BlockFace.SOUTH -> NormalizedShape(facing = BlockFace.EAST, shape = Stairs.Shape.INNER_RIGHT)
                    BlockFace.EAST -> NormalizedShape(facing = BlockFace.NORTH, shape = Stairs.Shape.INNER_RIGHT)
                    BlockFace.NORTH -> NormalizedShape(facing = BlockFace.WEST, shape = Stairs.Shape.INNER_RIGHT)
                    else -> NormalizedShape(facing, shape)
                }
            }
            else -> NormalizedShape(facing, shape)
        }
        
        return NormalizedStairs(
            facing = normalizedShape.facing,
            shape = normalizedShape.shape,
            half = half
        )
    }
    
    fun areEquivalent(a: Stairs, b: Stairs): Boolean {
        if (a.half != b.half) return false
        
        val normA = normalize(a)
        val normB = normalize(b)
        
        return normA.facing == normB.facing && normA.shape == normB.shape
    }
    
    data class NormalizedShape(val facing: BlockFace, val shape: Stairs.Shape)
    
    data class NormalizedStairs(
        val facing: BlockFace,
        val shape: Stairs.Shape,
        val half: Bisected.Half
    )
}
