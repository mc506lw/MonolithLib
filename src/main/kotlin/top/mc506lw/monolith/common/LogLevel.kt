package top.mc506lw.monolith.common

enum class LogLevel(val level: Int, val displayName: String, val ansiColor: String) {
    TRACE(-1, "TRACE", "\u001B[37m"),
    DEBUG(0, "DEBUG", "\u001B[90m"),
    INFO(1, "INFO", "\u001B[32m"),
    WARN(2, "WARN", "\u001B[33m"),
    ERROR(3, "ERROR", "\u001B[31m");

    companion object {
        fun fromString(name: String): LogLevel = entries.firstOrNull { it.name == name.uppercase() } ?: INFO
    }
}
