package top.mc506lw.monolith.common

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStream

object LogConfig {

    fun load(pluginDataFolder: File) {
        val configFile = File(pluginDataFolder, "logging.yml")
        if (!configFile.exists()) {
            saveDefault(configFile)
        }

        try {
            val config = YamlConfiguration.loadConfiguration(configFile)
            val level = LogLevel.fromString(config.getString("monolith.logging.level") ?: "INFO")
            val color = config.getBoolean("monolith.logging.color", true)
            val fullStack = config.getBoolean("monolith.logging.full-stacktrace", false)
            val timestamp = config.getBoolean("monolith.logging.timestamp", true)

            MonolithLogger.configure(level, color, fullStack, timestamp)
        } catch (e: Exception) {
            MonolithLogger.configure()
        }
    }

    private fun saveDefault(file: File) {
        file.parentFile?.mkdirs()
        val defaultContent = """
monolith:
  logging:
    level: INFO           # DEBUG | INFO | WARN | ERROR
    color: true           # Enable ANSI colors in console (set false for log files)
    full-stacktrace: false # Show full stack traces for exceptions
    timestamp: true       # Include timestamps in log messages

# Module-specific overrides (optional):
# modules:
#   Core:
#     level: DEBUG
#   IO:
#     level: DEBUG
""".trimIndent()
        file.writeText(defaultContent)
    }
}
