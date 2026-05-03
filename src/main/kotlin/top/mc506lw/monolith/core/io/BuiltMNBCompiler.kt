package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.Stairs
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.BlockEntry
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.core.model.BuildStage
import top.mc506lw.monolith.core.model.DisplayEntityData
import top.mc506lw.monolith.core.model.DisplayType
import top.mc506lw.monolith.core.model.Shape
import top.mc506lw.monolith.validation.predicate.*
import java.io.File

object BuiltMNBCompiler {

    fun compile(rawFile: File, configFile: File): Blueprint? {
        val rawBlueprint = try {
            top.mc506lw.monolith.core.io.formats.BinaryFormat.load(rawFile)
        } catch (_: Exception) {
            return null
        } ?: return null

        val bpConfig = if (configFile.exists()) {
            BlueprintConfigLoader.load(configFile)
        } else {
            return rawBlueprint
        } ?: return rawBlueprint

        return compile(rawBlueprint, bpConfig)
    }

    fun compile(rawBlueprint: Blueprint, config: BlueprintConfig): Blueprint {
        val scaffoldMaterialMap = config.scaffoldMaterials
        val overrides = config.overrides
        val hasDualStage = rawBlueprint.scaffoldShape !== rawBlueprint.assembledShape &&
            rawBlueprint.scaffoldShape.blocks != rawBlueprint.assembledShape.blocks

        val scaffoldBlocks = mutableListOf<BlockEntry>()
        val assembledBlocks = mutableListOf<BlockEntry>()

        if (hasDualStage) {
            val rawScaffold = rawBlueprint.scaffoldShape
            val rawAssembled = rawBlueprint.assembledShape

            for (blockEntry in rawScaffold.blocks) {
                val pos = blockEntry.position
                val overrideEntry = overrides[pos]
                if (overrideEntry != null) {
                    scaffoldBlocks.add(applyOverrideToScaffold(blockEntry, overrideEntry, scaffoldMaterialMap))
                } else {
                    scaffoldBlocks.add(applyScaffoldMaterial(blockEntry, scaffoldMaterialMap))
                }
            }

            for (blockEntry in rawAssembled.blocks) {
                val pos = blockEntry.position
                val overrideEntry = overrides[pos]
                if (overrideEntry != null) {
                    assembledBlocks.add(applyOverrideToAssembled(blockEntry, overrideEntry))
                } else {
                    assembledBlocks.add(blockEntry)
                }
            }
        } else {
            for (blockEntry in rawBlueprint.assembledShape.blocks) {
                val pos = blockEntry.position
                val overrideEntry = overrides[pos]

                if (overrideEntry != null) {
                    scaffoldBlocks.add(applyOverrideToScaffold(blockEntry, overrideEntry, scaffoldMaterialMap))
                    assembledBlocks.add(applyOverrideToAssembled(blockEntry, overrideEntry))
                } else {
                    scaffoldBlocks.add(applyScaffoldMaterial(blockEntry, scaffoldMaterialMap))
                    assembledBlocks.add(blockEntry)
                }
            }
        }

        var scaffoldShape = Shape(scaffoldBlocks)
        var finalAssembledShape = if (assembledBlocks.toList() != rawBlueprint.assembledShape.blocks.toList()) {
            Shape(assembledBlocks)
        } else {
            rawBlueprint.assembledShape
        }

        if (config.scaffoldRotation != 0 || config.assembledRotation != 0) {
            val center = config.rotationCenter ?: calculateGeometryCenter(scaffoldShape, finalAssembledShape)

            if (config.scaffoldRotation != 0) {
                val steps = config.scaffoldRotation / 90
                Bukkit.getLogger().info("[BuiltMNBCompiler] 应用脚手架旋转: ${config.scaffoldRotation}° ($steps 步), 旋转中心=$center")
                Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转前 scaffoldShape 方块数=${scaffoldShape.blocks.size}, 前3个方块:")
                scaffoldShape.blocks.take(3).forEach { block ->
                    Bukkit.getLogger().info("[BuiltMNBCompiler]   → pos=${block.position}, blockData=${block.blockData.asString}")
                }

                scaffoldShape = rotateShape(scaffoldShape, center, steps)

                Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转后 scaffoldShape 方块数=${scaffoldShape.blocks.size}, 前3个方块:")
                scaffoldShape.blocks.take(3).forEach { block ->
                    Bukkit.getLogger().info("[BuiltMNBCompiler]   → pos=${block.position}, blockData=${block.blockData.asString}")
                }
            }

            if (config.assembledRotation != 0) {
                val steps = config.assembledRotation / 90
                Bukkit.getLogger().info("[BuiltMNBCompiler] 应用成型旋转: ${config.assembledRotation}° ($steps 步), 旋转中心=$center")
                Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转前 assembledShape 方块数=${finalAssembledShape.blocks.size}, 前3个方块:")
                finalAssembledShape.blocks.take(3).forEach { block ->
                    Bukkit.getLogger().info("[BuiltMNBCompiler]   → pos=${block.position}, blockData=${block.blockData.asString}")
                }

                finalAssembledShape = rotateShape(finalAssembledShape, center, steps)
                
                Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转后 assembledShape 方块数=${finalAssembledShape.blocks.size}, 前3个方块:")
                finalAssembledShape.blocks.take(3).forEach { block ->
                    Bukkit.getLogger().info("[BuiltMNBCompiler]   → pos=${block.position}, blockData=${block.blockData.asString}")
                }
            }
        }

        var displayEntitiesToCorrect = rawBlueprint.displayEntities
        if (config.assembledRotation != 0) {
            val center = config.rotationCenter ?: calculateGeometryCenter(scaffoldShape, finalAssembledShape)
            val steps = config.assembledRotation / 90
            
            if (displayEntitiesToCorrect.isNotEmpty()) {
                Bukkit.getLogger().info("[BuiltMNBCompiler] 修正 displayEntities blockData (${displayEntitiesToCorrect.size} 个实体), 步数=$steps")
                displayEntitiesToCorrect = rotateDisplayEntities(displayEntitiesToCorrect, center, steps)
            }
        }
        
        val correctedDisplayEntities = correctDisplayEntities(
            displayEntitiesToCorrect,
            scaffoldShape,
            finalAssembledShape
        )

        val controllerRebarKey = config.controllerRebarKey ?: rawBlueprint.controllerRebarKey
        var controllerOffset = config.controllerPosition ?: rawBlueprint.meta.controllerOffset

        if ((config.scaffoldRotation != 0 || config.assembledRotation != 0) && config.controllerPosition == null) {
            val center = config.rotationCenter ?: calculateGeometryCenter(scaffoldShape, finalAssembledShape)
            val scaffoldSteps = config.scaffoldRotation / 90
            val assembledSteps = config.assembledRotation / 90
            if (scaffoldSteps != 0) controllerOffset = rotateCoordinate(controllerOffset, center, scaffoldSteps)
            Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转后的 controllerOffset: $controllerOffset")
        }

        var finalDisplayOffset = config.displayOffset ?: rawBlueprint.meta.displayOffset

        if (config.assembledRotation != 0) {
            val steps = config.assembledRotation / 90
            finalDisplayOffset = when (steps % 4) {
                1 -> Vector3i(finalDisplayOffset.z, finalDisplayOffset.y, -finalDisplayOffset.x)
                2 -> Vector3i(-finalDisplayOffset.x, finalDisplayOffset.y, -finalDisplayOffset.z)
                3 -> Vector3i(-finalDisplayOffset.z, finalDisplayOffset.y, finalDisplayOffset.x)
                else -> finalDisplayOffset
            }
            Bukkit.getLogger().info("[BuiltMNBCompiler] 旋转 displayOffset: ${config.displayOffset ?: rawBlueprint.meta.displayOffset} → $finalDisplayOffset")
        }

        Bukkit.getLogger().info("[BuiltMNBCompiler] displayOffset: YML配置=${config.displayOffset}, 蓝图默认=${rawBlueprint.meta.displayOffset}, 最终=$finalDisplayOffset")

        val meta = rawBlueprint.meta.copy(
            displayName = if (config.metaName.isNotEmpty()) config.metaName else rawBlueprint.meta.displayName,
            description = if (config.metaDescription.isNotEmpty()) config.metaDescription else rawBlueprint.meta.description,
            controllerOffset = controllerOffset,
            displayOffset = finalDisplayOffset
        )

        val slots = if (config.slots.isNotEmpty()) {
            if (config.scaffoldRotation != 0 || config.assembledRotation != 0) {
                val center = config.rotationCenter ?: calculateGeometryCenter(scaffoldShape, finalAssembledShape)
                val scaffoldSteps = config.scaffoldRotation / 90
                config.slots.mapValues { (_, pos) -> rotateCoordinate(pos, center, scaffoldSteps) }
            } else {
                config.slots
            }
        } else {
            rawBlueprint.slots
        }

        val customData = if (config.customData.isNotEmpty()) config.customData else rawBlueprint.customData

        return Blueprint(
            id = config.id.ifEmpty { rawBlueprint.id },
            stages = mapOf(
                BuildStage.SCAFFOLD to scaffoldShape,
                BuildStage.ASSEMBLED to finalAssembledShape
            ),
            meta = meta,
            displayEntities = correctedDisplayEntities,
            slots = slots,
            customData = customData,
            controllerRebarKey = controllerRebarKey
        )
    }

    private fun calculateGeometryCenter(vararg shapes: Shape): Vector3i {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (shape in shapes) {
            for (block in shape.blocks) {
                minX = minOf(minX, block.position.x)
                minY = minOf(minY, block.position.y)
                minZ = minOf(minZ, block.position.z)
                maxX = maxOf(maxX, block.position.x)
                maxY = maxOf(maxY, block.position.y)
                maxZ = maxOf(maxZ, block.position.z)
            }
        }

        return Vector3i(
            (minX + maxX) / 2,
            (minY + maxY) / 2,
            (minZ + maxZ) / 2
        )
    }

    private fun rotateCoordinate(pos: Vector3i, center: Vector3i, steps: Int): Vector3i {
        if (steps == 0) return pos

        val dx = pos.x - center.x
        val dz = pos.z - center.z

        return when (steps % 4) {
            1 -> Vector3i(center.x + dz, pos.y, center.z - dx)
            2 -> Vector3i(center.x - dx, pos.y, center.z - dz)
            3 -> Vector3i(center.x - dz, pos.y, center.z + dx)
            else -> pos
        }
    }

    private fun rotateBlockData(blockData: BlockData, steps: Int): BlockData {
        if (steps == 0) return blockData

        val rotated = blockData.clone()

        when (rotated) {
            is Stairs -> {
                rotated.facing = rotateFacing(rotated.facing, steps)
                rotated.shape = rotateStairShape(rotated.shape, steps)
            }
            is Directional -> {
                rotated.facing = rotateFacing(rotated.facing, steps)
            }
        }

        return rotated
    }

    private fun rotateFacing(facing: org.bukkit.block.BlockFace, steps: Int): org.bukkit.block.BlockFace {
        val horizontalFaces = listOf(
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.WEST
        )
        val currentIndex = horizontalFaces.indexOf(facing)
        if (currentIndex == -1) return facing

        val newIndex = (currentIndex + steps) % 4
        return horizontalFaces[newIndex]
    }

    private fun rotateStairShape(shape: Stairs.Shape, steps: Int): Stairs.Shape {
        return when (shape) {
            Stairs.Shape.STRAIGHT -> Stairs.Shape.STRAIGHT
            Stairs.Shape.INNER_LEFT, Stairs.Shape.INNER_RIGHT -> {
                if (steps % 2 == 1) {
                    if (shape == Stairs.Shape.INNER_LEFT) Stairs.Shape.INNER_RIGHT else Stairs.Shape.INNER_LEFT
                } else {
                    shape
                }
            }
            Stairs.Shape.OUTER_LEFT, Stairs.Shape.OUTER_RIGHT -> {
                if (steps % 2 == 1) {
                    if (shape == Stairs.Shape.OUTER_LEFT) Stairs.Shape.OUTER_RIGHT else Stairs.Shape.OUTER_LEFT
                } else {
                    shape
                }
            }
        }
    }

    private fun rotateShape(shape: Shape, center: Vector3i, steps: Int): Shape {
        if (steps == 0) return shape

        val rotatedBlocks = shape.blocks.map { blockEntry ->
            BlockEntry(
                position = rotateCoordinate(blockEntry.position, center, steps),
                blockData = rotateBlockData(blockEntry.blockData, steps)
            )
        }

        return Shape(rotatedBlocks)
    }

    private fun rotateDisplayEntities(
        displayEntities: List<DisplayEntityData>,
        center: Vector3i,
        steps: Int
    ): List<DisplayEntityData> {
        if (steps == 0 || displayEntities.isEmpty()) return displayEntities

        Bukkit.getLogger().info("[BuiltMNBCompiler] rotateDisplayEntities: 步数=$steps (只旋转position和blockData, translation由运行时处理), 实体数=${displayEntities.size}")

        return displayEntities.mapIndexed { index, entity ->
            val rp = rotateCoordinate(entity.position, center, steps)
            val rb = entity.blockData?.let { rotateBlockData(it.clone(), steps) }

            if (index < 3) {
                Bukkit.getLogger().info("[BuiltMNBCompiler]   [$index/${displayEntities.size}] pos=${entity.position}→$rp | trans保持=(${entity.translation.x},${entity.translation.y},${entity.translation.z})")
            }

            entity.copy(position = rp, translation = entity.translation, blockData = rb)
        }
    }

    private fun correctDisplayEntities(
        displayEntities: List<DisplayEntityData>,
        scaffoldShape: Shape,
        assembledShape: Shape
    ): List<DisplayEntityData> {
        if (displayEntities.isEmpty()) return displayEntities

        val scaffoldBlockMap = scaffoldShape.blocks.associateBy { it.position }

        return displayEntities.map { entity ->
            if (entity.entityType == DisplayType.BLOCK && entity.blockData != null) {
                val mat = entity.blockData.material
                if (mat == Material.STRUCTURE_VOID || mat == Material.BARRIER) {
                    val scaffoldEntry = scaffoldBlockMap[entity.position]
                    if (scaffoldEntry != null) {
                        entity.copy(blockData = scaffoldEntry.blockData.clone())
                    } else {
                        entity
                    }
                } else {
                    entity
                }
            } else {
                entity
            }
        }
    }

    private fun applyScaffoldMaterial(
        blockEntry: BlockEntry,
        scaffoldMaterialMap: Map<Material, Material>
    ): BlockEntry {
        if (scaffoldMaterialMap.isEmpty()) return blockEntry

        val material = blockEntry.blockData.material
        val scaffoldMaterial = scaffoldMaterialMap[material] ?: return blockEntry

        val newBlockData = try {
            Bukkit.createBlockData(scaffoldMaterial)
        } catch (_: Exception) {
            return blockEntry
        }

        return BlockEntry(
            position = blockEntry.position,
            blockData = newBlockData
        )
    }

    private fun applyOverrideToScaffold(
        blockEntry: BlockEntry,
        override: BlueprintConfig.OverrideEntry,
        scaffoldMaterialMap: Map<Material, Material>
    ): BlockEntry {
        val scaffoldEntry = applyScaffoldMaterial(blockEntry, scaffoldMaterialMap)

        return when (override.type.lowercase()) {
            "strict", "material" -> {
                val mat = override.material ?: return scaffoldEntry
                val newBlockData = try {
                    Bukkit.createBlockData(mat)
                } catch (_: Exception) {
                    return scaffoldEntry
                }
                BlockEntry(position = scaffoldEntry.position, blockData = newBlockData)
            }
            "loose" -> {
                scaffoldEntry
            }
            "rebar" -> {
                scaffoldEntry
            }
            else -> scaffoldEntry
        }
    }

    private fun applyOverrideToAssembled(
        blockEntry: BlockEntry,
        override: BlueprintConfig.OverrideEntry
    ): BlockEntry {
        return when (override.type.lowercase()) {
            "strict", "material" -> {
                val mat = override.material ?: return blockEntry
                val newBlockData = try {
                    Bukkit.createBlockData(mat)
                } catch (_: Exception) {
                    return blockEntry
                }
                BlockEntry(position = blockEntry.position, blockData = newBlockData)
            }
            "loose" -> {
                blockEntry
            }
            "rebar" -> {
                blockEntry
            }
            else -> blockEntry
        }
    }
}
