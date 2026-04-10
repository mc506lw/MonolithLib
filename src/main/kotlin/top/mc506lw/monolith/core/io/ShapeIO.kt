package top.mc506lw.monolith.core.io

import top.mc506lw.monolith.core.model.Shape
import java.io.File

class ShapeIO(private val dataFolder: File) {
    
    private val readers = mutableListOf<ShapeReader>()
    private val writers = mutableListOf<ShapeWriter>()
    
    init {
        registerDefaultReaders()
    }
    
    private fun registerDefaultReaders() {
        readers.add(MnbShapeReader)
        readers.add(LitematicShapeReader)
        readers.add(SchemShapeReader)
        readers.add(NbtShapeReader)
    }
    
    fun loadShape(file: File, format: String? = null): Shape? {
        if (format != null) {
            val reader = readers.find { it.formatName.equals(format, ignoreCase = true) }
            return reader?.read(file)
        }
        
        for (reader in readers) {
            if (reader.canRead(file)) {
                return reader.read(file)
            }
        }
        
        return null
    }
    
    fun loadShapeRotated(file: File, facingRotationSteps: Int): Shape? {
        val shape = loadShape(file) ?: return null
        
        if (facingRotationSteps == 0) {
            return shape
        }
        
        return RotatedShape.create(shape, facingRotationSteps)
    }
    
    fun getSupportedFormats(): List<String> {
        return readers.map { it.formatName }
    }
    
    fun getSupportedExtensions(): Set<String> {
        return readers.flatMap { it.supportedExtensions }.toSet()
    }
    
    companion object {
        fun create(dataFolder: File): ShapeIO = ShapeIO(dataFolder)
    }
}
