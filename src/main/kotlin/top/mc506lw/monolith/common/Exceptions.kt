package top.mc506lw.monolith.common

open class MonolithException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class StructureNotFoundException(id: String) : MonolithException("结构未找到: $id")

class StructureLoadException(file: String, cause: Throwable? = null) : MonolithException("加载结构失败: $file", cause)

class StructureSaveException(file: String, cause: Throwable? = null) : MonolithException("保存结构失败: $file", cause)

class ValidationException(message: String) : MonolithException(message)

class StructureSizeExceededException(val actual: Int, val maximum: Int) : 
    MonolithException("结构大小超出限制: $actual > $maximum")

class InvalidCoordinateException(x: Int, y: Int, z: Int, bounds: String) : 
    MonolithException("无效坐标 ($x, $y, $z), 边界: $bounds")

class RebarIntegrationException(message: String, cause: Throwable? = null) : 
    MonolithException("Rebar 集成错误: $message", cause)

class PreviewException(message: String) : MonolithException("预览错误: $message")
