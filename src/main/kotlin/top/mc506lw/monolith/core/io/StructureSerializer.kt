package top.mc506lw.monolith.core.io

import top.mc506lw.monolith.core.model.Blueprint
import java.io.InputStream
import java.io.OutputStream

interface StructureSerializer {
    val formatName: String
    val fileExtension: String
    
    fun serialize(blueprint: Blueprint, output: OutputStream)
    fun deserialize(input: InputStream): Blueprint?
}
