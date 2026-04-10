package top.mc506lw.monolith.validation

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AsyncValidator(
    private val validationIntervalMs: Long = 1500L
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Monolith-Validation-Thread").apply { isDaemon = true }
    }
    
    private val tasks = mutableMapOf<String, ValidationTask>()
    
    fun startValidation(taskId: String, callback: () -> Unit) {
        stopValidation(taskId)
        
        val task = ValidationTask(callback)
        scheduler.scheduleAtFixedRate(task, 0L, validationIntervalMs, TimeUnit.MILLISECONDS)
        tasks[taskId] = task
    }
    
    fun stopValidation(taskId: String) {
        tasks.remove(taskId)?.cancel()
    }
    
    fun shutdown() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        scheduler.shutdown()
    }
    
    inner class ValidationTask(private val callback: () -> Unit) : Runnable {
        @Volatile
        var cancelled = false
            private set
        
        override fun run() {
            if (!cancelled) {
                callback()
            }
        }
        
        fun cancel() {
            cancelled = true
        }
    }
}
