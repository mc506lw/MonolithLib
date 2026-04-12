package top.mc506lw.monolith.common

object Constants {
    const val PLUGIN_NAME = "MonolithLib"
    const val PLUGIN_VERSION = "1.0"
    
    const val DEFAULT_VALIDATION_INTERVAL_MS = 1500L
    const val MAX_STRUCTURE_SIZE = 4096
    const val MAX_PREVIEW_DISTANCE = 64
    const val ENTITY_POOL_SIZE = 100
    
    val SUPPORTED_INPUT_EXTENSIONS = listOf(".schem", ".litematic", ".nbt")
    const val CACHE_EXTENSION = ".mnb"
    
    object ConfigKeys {
        const val VALIDATION_INTERVAL = "validation.interval-ms"
        const val MAX_STRUCTURE_BLOCKS = "structure.max-blocks"
        const val PREVIEW_ENABLED = "preview.enabled"
        const val DEBUG_MODE = "debug.enabled"
    }
    
    object Permissions {
        const val BASE = "monolithlib.base"
        const val ADMIN = "monolithlib.admin"
        const val RELOAD = "monolithlib.reload"
        const val PREVIEW = "monolithlib.preview"
        const val BUILD = "monolithlib.build"
        const val BLUEPRINT = "monolithlib.blueprint"
        const val DEBUG = "monolithlib.debug"
    }
}
