package top.mc506lw.monolith.common

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

        fun debug(message: () -> String) {
            if (minLevel.level <= LogLevel.DEBUG.level) {
                log(LogLevel.DEBUG, message())
            }
        }

        fun info(message: () -> String) {
            if (minLevel.level <= LogLevel.INFO.level) {
                log(LogLevel.INFO, message())
            }
        }

        fun warn(message: () -> String) {
            if (minLevel.level <= LogLevel.WARN.level) {
                log(LogLevel.WARN, message())
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

            val formattedMessage = "$colorPrefix[${level.displayName}] [$moduleName]$timestamp - $message$colorReset"
            println(formattedMessage)
        }
    }
}
