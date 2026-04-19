package top.mc506lw.monolith.core.io

import org.bukkit.Bukkit
import org.bukkit.Material
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

        val scaffoldShape = Shape(scaffoldBlocks)
        val finalAssembledShape = if (assembledBlocks.toList() != rawBlueprint.assembledShape.blocks.toList()) {
            Shape(assembledBlocks)
        } else {
            rawBlueprint.assembledShape
        }

        val correctedDisplayEntities = correctDisplayEntities(
            rawBlueprint.displayEntities,
            scaffoldShape,
            finalAssembledShape
        )

        val controllerRebarKey = config.controllerRebarKey ?: rawBlueprint.controllerRebarKey
        val controllerOffset = config.controllerPosition ?: rawBlueprint.meta.controllerOffset

        val meta = rawBlueprint.meta.copy(
            displayName = if (config.metaName.isNotEmpty()) config.metaName else rawBlueprint.meta.displayName,
            description = if (config.metaDescription.isNotEmpty()) config.metaDescription else rawBlueprint.meta.description,
            controllerOffset = controllerOffset
        )

        val slots = if (config.slots.isNotEmpty()) config.slots else rawBlueprint.slots
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
