package top.mc506lw.monolith.internal.command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import top.mc506lw.monolith.api.MonolithAPI

object MonolithTabCompleter : TabCompleter {
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!command.name.equals("monolith", ignoreCase = true) && 
            !command.name.equals("ml", ignoreCase = true)) {
            return emptyList()
        }
        
        return when (args.size) {
            1 -> getSubCommands(args[0])
            2 -> getSecondArg(args[0], args[1])
            else -> emptyList()
        }
    }
    
    private fun getSubCommands(prefix: String): List<String> {
        val commands = listOf(
            "reload", "list", "info",
            "preview", "build", "blueprint", "litematica"
        )
        return commands.filter { it.startsWith(prefix.lowercase()) }
    }
    
    private fun getSecondArg(subCommand: String, prefix: String): List<String> {
        return when (subCommand.lowercase()) {
            "preview", "build" -> {
                if ("cancel".startsWith(prefix.lowercase())) {
                    listOf("cancel")
                } else {
                    emptyList()
                }
            }
            "litematica" -> {
                listOf("easybuild", "printer").filter { it.startsWith(prefix.lowercase()) }
            }
            "info", "blueprint" -> {
                val api = MonolithAPI.getInstance()
                api.registry.getAllBlueprints().keys
                    .filter { it.startsWith(prefix.lowercase()) }
                    .take(10)
            }
            else -> emptyList()
        }
    }
}
