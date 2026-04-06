package top.mc506lw.monolith.feature.io

import top.mc506lw.monolith.core.structure.MonolithStructure
import java.io.InputStream
import java.io.OutputStream

interface StructureSerializer {
    val formatName: String
    val fileExtension: String
    
    fun serialize(structure: MonolithStructure, output: OutputStream)
    fun deserialize(input: InputStream): MonolithStructure
}
