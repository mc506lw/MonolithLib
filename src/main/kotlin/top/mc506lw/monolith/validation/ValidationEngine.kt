package top.mc506lw.monolith.validation

import org.bukkit.Location
import org.bukkit.block.data.BlockData
import top.mc506lw.monolith.core.math.Vector3i
import top.mc506lw.monolith.core.model.Blueprint
import top.mc506lw.monolith.validation.predicate.Predicate
import top.mc506lw.monolith.validation.predicate.Predicates
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
    private val blueprint: Blueprint,
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
        
        val shape = blueprint.shape
        val centerOffset = blueprint.meta.controllerOffset
        
        for (blockEntry in shape.blocks) {
            val worldPos = transform.toWorldPosition(
                controllerPos = controllerPos,
                relativePos = blockEntry.position,
                centerOffset = centerOffset
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
            
            val predicate = Predicates.strict(blockEntry.blockData)
            if (predicate.testMaterialOnly(blockData, Predicate.PredicateContext(position = blockEntry.position))) {
                matchedCount++
            } else {
                errors.add(ValidationError(
                    position = blockLocation,
                    expected = predicate,
                    actual = blockData,
                    message = "位置 ${blockEntry.position} 匹配失败"
                ))
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success(matchedCount, shape.blocks.size)
        } else {
            ValidationResult.Failure(errors)
        }
    }
}
