package top.mc506lw.monolith.core.io

import top.mc506lw.monolith.core.model.Shape
import java.io.File
import java.io.InputStream

interface ShapeReader {
    val formatName: String
    val supportedExtensions: Set<String>
    
    fun canRead(file: File): Boolean {
        return file.extension.lowercase() in supportedExtensions
    }
    
    fun read(file: File): Shape?
    
    fun read(input: InputStream): Shape?
}

interface ShapeWriter {
    val formatName: String
    val fileExtension: String
    
    fun write(shape: Shape, file: File)
    
    fun write(shape: Shape, output: InputStream)
}
