package top.mc506lw.monolith.feature.rebar

import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.monolith.common.MonolithLogger

class RebarModule(plugin: JavaPlugin) {

    private val logger = MonolithLogger.getLogger("Rebar")

    init {
        initializeRebarIntegration()
    }

    private fun initializeRebarIntegration() {
        try {
            if (isAvailable()) {
                logger.info { "Rebar integration enabled" }
            } else {
                logger.warn { "Rebar not detected, skipping integration" }
            }
        } catch (e: NoClassDefFoundError) {
            logger.warn { "Rebar not installed, skipping integration" }
        } catch (e: Exception) {
            logger.warn(e) { "Rebar initialization failed" }
        }
    }

    fun isAvailable(): Boolean {
        return try {
            Class.forName("io.github.pylonmc.rebar.Rebar")
            true
        } catch (e: NoClassDefFoundError) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
