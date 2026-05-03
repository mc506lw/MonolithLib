package top.mc506lw.monolith.internal.command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import top.mc506lw.monolith.api.MonolithAPI

class MonolithTabCompleter : TabCompleter {

    companion object {
        private val DOMAINS = listOf("preview", "build", "bp", "site", "edit", "reload")
        
        val BUILD_SUBS = listOf("here", "easy", "printer")
        val BP_SUBS = listOf("list", "info", "give")
        val SITE_SUBS = listOf("list", "info", "cancel")
        val EDIT_SUBS = listOf("wand", "save", "merge")
        
        val TOGGLE_OPTIONS = listOf("on", "off")
        val SAVE_FLAGS = listOf("--scaffold", "--assembled")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (!command.name.equals("monolith", ignoreCase = true)) return null
        
        return when (args.size) {
            1 -> filter(args[0], DOMAINS)
            2 -> filterSecondLevel(sender, args)
            3 -> filterThirdLevel(args)
            else -> mutableListOf()
        }
    }

    private fun filterSecondLevel(sender: CommandSender, args: Array<out String>): MutableList<String> {
        return when (args[0].lowercase()) {
            "preview" -> filterPreviewSecondLevel(args[1])
            "build" -> filter(args[1], BUILD_SUBS)
            "bp" -> filter(args[1], BP_SUBS)
            "site" -> filter(args[1], SITE_SUBS)
            "edit" -> filter(args[1], EDIT_SUBS)
            else -> mutableListOf()
        }
    }

    private fun filterPreviewSecondLevel(input: String): MutableList<String> {
        val lowerInput = input.lowercase()
        val results = mutableListOf<String>()
        results.addAll(filterBlueprintIds(lowerInput))
        if ("stop".startsWith(lowerInput)) results.add("stop")
        return results
    }

    private fun filterThirdLevel(args: Array<out String>): MutableList<String> {
        val domain = args[0].lowercase()
        val subCmd = args[1].lowercase()
        
        return when {
            domain == "build" && subCmd == "here" -> filterBlueprintIds(args[2])
            domain == "build" && (subCmd == "easy" || subCmd == "printer") -> filter(args[2], TOGGLE_OPTIONS)
            
            domain == "bp" && (subCmd == "info" || subCmd == "give") -> filterBlueprintIds(args[2])
            
            domain == "edit" && subCmd == "save" -> filter(args[2], SAVE_FLAGS)
            
            else -> mutableListOf()
        }
    }

    private fun filter(input: String, candidates: List<String>): MutableList<String> {
        val lowerInput = input.lowercase()
        return candidates.filter { it.startsWith(lowerInput) }.toMutableList()
    }

    private fun filterBlueprintIds(input: String): MutableList<String> {
        val lowerInput = input.lowercase()
        return try {
            MonolithAPI.getInstance().registry.getAll().keys
                .filter { it.startsWith(lowerInput) }
                .toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }
}
