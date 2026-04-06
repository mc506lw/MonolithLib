package top.mc506lw.monolith.engine.validation

import org.bukkit.Location
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.predicate.Predicate
import top.mc506lw.monolith.core.structure.MonolithStructure
import top.mc506lw.monolith.core.transform.CoordinateTransform
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed class ValidationResult {
    data class Success(val matchedBlocks: Int, val totalBlocks: Int) : ValidationResult()
    data class Failure(val errors: List<ValidationError>) : ValidationResult()
    object Pending : ValidationResult()
}

data class ValidationError(
    val position: Location,
    val expected: Predicate,
    val actual: BlockData,
    val message: String
)

class ValidationEngine(
    private val structure: MonolithStructure,
    private val transform: CoordinateTransform
) {
    private val lock = ReentrantLock()
    
    fun validateAsync(controllerLocation: Location): ValidationResult {
        if (!lock.tryLock()) {
            return ValidationResult.Pending
        }
        
        return try {
            validateInternal(controllerLocation)
        } finally {
            lock.unlock()
        }
    }
    
    fun validateSync(controllerLocation: Location): ValidationResult {
        return lock.withLock {
            validateInternal(controllerLocation)
        }
    }
    
    private fun validateInternal(controllerLocation: Location): ValidationResult {
        val controllerPos = Vector3i(
            controllerLocation.blockX,
            controllerLocation.blockY,
            controllerLocation.blockZ
        )
        val errors = mutableListOf<ValidationError>()
        var matchedCount = 0
        
        for (entry in structure.flattenedBlocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = entry.relativePosition,
                centerOffset = structure.centerOffset
            )
            
            val blockLocation = Location(
                controllerLocation.world,
                worldPos.x.toDouble(),
                worldPos.y.toDouble(),
                worldPos.z.toDouble()
            )
            
            if (!blockLocation.chunk.isLoaded) {
                continue
            }
            
            val block = blockLocation.block
            val blockData = block.blockData
            
            if (entry.predicate.testMaterialOnly(blockData, Predicate.PredicateContext(position = entry.relativePosition))) {
                matchedCount++
            } else {
                errors.add(ValidationError(
                    position = blockLocation,
                    expected = entry.predicate,
                    actual = blockData,
                    message = "位置 ${entry.relativePosition} 匹配失败"
                ))
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success(matchedCount, structure.flattenedBlocks.size)
        } else {
            ValidationResult.Failure(errors)
        }
    }
}
