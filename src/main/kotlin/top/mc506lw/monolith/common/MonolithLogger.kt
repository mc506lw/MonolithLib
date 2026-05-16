package top.mc506lw.monolith.common

import top.mc506lw.monolith.core.math.Vector3i
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

object MonolithLogger {

    private val loggers = ConcurrentHashMap<String, ModuleLogger>()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    var globalLevel: LogLevel = LogLevel.INFO
        set(value) {
            field = value
            loggers.values.forEach { it.minLevel = value }
        }

    var colorEnabled: Boolean = true
    var fullStackTrace: Boolean = false
    var timestampEnabled: Boolean = true

    fun getLogger(moduleName: String): ModuleLogger = loggers.getOrPut(moduleName) { ModuleLogger(moduleName) }

    fun configure(level: LogLevel = LogLevel.INFO, color: Boolean = true, fullStack: Boolean = false, timestamp: Boolean = true) {
        globalLevel = level
        colorEnabled = color
        fullStackTrace = fullStack
        timestampEnabled = timestamp
    }

    class ModuleLogger internal constructor(val moduleName: String) {
        var minLevel: LogLevel = globalLevel

        fun trace(message: () -> String) {
            if (minLevel.level <= LogLevel.TRACE.level) {
                log(LogLevel.TRACE, message())
            }
        }

        fun trace(context: String, action: String, vararg details: Pair<String, Any?>) {
            if (minLevel.level <= LogLevel.TRACE.level) {
                log(LogLevel.TRACE, formatMessage(context, action, *details))
            }
        }

        fun debug(message: () -> String) {
            if (minLevel.level <= LogLevel.DEBUG.level) {
                log(LogLevel.DEBUG, message())
            }
        }

        fun debug(context: String, action: String, vararg details: Pair<String, Any?>) {
            if (minLevel.level <= LogLevel.DEBUG.level) {
                log(LogLevel.DEBUG, formatMessage(context, action, *details))
            }
        }

        fun info(message: () -> String) {
            if (minLevel.level <= LogLevel.INFO.level) {
                log(LogLevel.INFO, message())
            }
        }

        fun info(context: String, action: String, vararg details: Pair<String, Any?>) {
            if (minLevel.level <= LogLevel.INFO.level) {
                log(LogLevel.INFO, formatMessage(context, action, *details))
            }
        }

        fun warn(message: () -> String) {
            if (minLevel.level <= LogLevel.WARN.level) {
                log(LogLevel.WARN, message())
            }
        }

        fun warn(context: String, action: String, vararg details: Pair<String, Any?>) {
            if (minLevel.level <= LogLevel.WARN.level) {
                log(LogLevel.WARN, formatMessage(context, action, *details))
            }
        }

        fun warn(throwable: Throwable, message: () -> String) {
            if (minLevel.level <= LogLevel.WARN.level) {
                val msg = message()
                if (fullStackTrace) {
                    log(LogLevel.WARN, "$msg\n${throwable.stackTraceToString()}")
                } else {
                    log(LogLevel.WARN, "$msg - ${throwable.message}")
                }
            }
        }

        fun error(message: () -> String) {
            if (minLevel.level <= LogLevel.ERROR.level) {
                log(LogLevel.ERROR, message())
            }
        }

        fun error(context: String, action: String, vararg details: Pair<String, Any?>) {
            if (minLevel.level <= LogLevel.ERROR.level) {
                log(LogLevel.ERROR, formatMessage(context, action, *details))
            }
        }

        fun error(throwable: Throwable, message: () -> String) {
            if (minLevel.level <= LogLevel.ERROR.level) {
                val msg = message()
                if (fullStackTrace) {
                    log(LogLevel.ERROR, "$msg\n${throwable.stackTraceToString()}")
                } else {
                    log(LogLevel.ERROR, "$msg - ${throwable.message}")
                }
            }
        }

        internal fun log(level: LogLevel, message: String) {
            val timestamp = if (timestampEnabled) " ${LocalDateTime.now().format(formatter)}" else ""
            val colorPrefix = if (colorEnabled) level.ansiColor else ""
            val colorReset = if (colorEnabled) "\u001B[0m" else ""

            val formattedMessage = "$colorPrefix[MonolithLib][$moduleName]$timestamp $message$colorReset"
            println(formattedMessage)
        }

        private fun formatMessage(context: String, action: String, vararg details: Pair<String, Any?>): String {
            val contextStr = if (context.isNotEmpty()) "[$context] " else ""
            val detailsStr = if (details.isNotEmpty()) {
                " | " + details.joinToString(", ") { "${it.first}=${formatValue(it.second)}" }
            } else {
                ""
            }
            return "$contextStr$action$detailsStr"
        }

        private fun formatValue(value: Any?): String {
            return when (value) {
                null -> "null"
                is Double -> String.format("%.1f", value)
                is Float -> String.format("%.1f", value)
                is Int -> value.toString()
                is Long -> value.toString()
                is Short -> value.toString()
                is Byte -> value.toString()
                is Boolean -> value.toString()
                is Vector3i -> "(${value.x},${value.y},${value.z})"
                is org.joml.Vector3f -> "(${String.format("%.1f", value.x())},${String.format("%.1f", value.y())},${String.format("%.1f", value.z())})"
                is org.joml.Quaternionf -> "(${String.format("%.3f", value.x())},${String.format("%.3f", value.y())},${String.format("%.3f", value.z())},${String.format("%.3f", value.w())})"
                is Enum<*> -> value.name
                else -> value.toString()
            }
        }

        companion object {
            fun formatCoord(x: Int, y: Int, z: Int): String = "($x,$y,$z)"
            fun formatCoordRange(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): String = "($minX,$minY,$minZ)→($maxX,$maxY,$maxZ)"
            fun formatYaw(yaw: Float): String = String.format("%.1f", yaw.toDouble())
            fun formatPitch(pitch: Float): String = String.format("%.1f", pitch.toDouble())
        }
    }
}