package top.mc506lw.monolith.feature.rebar

import org.bukkit.plugin.java.JavaPlugin

class RebarModule(plugin: JavaPlugin) {
    
    init {
        initializeRebarIntegration()
    }
    
    private fun initializeRebarIntegration() {
        try {
            if (isAvailable()) {
                println("[MonolithLib] Rebar 集成已启用")
            } else {
                println("[MonolithLib] 未检测到 Rebar，跳过集成")
            }
        } catch (e: NoClassDefFoundError) {
            println("[MonolithLib] Rebar 未安装，跳过集成")
        } catch (e: Exception) {
            println("[MonolithLib] Rebar 初始化失败: ${e.message}")
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
