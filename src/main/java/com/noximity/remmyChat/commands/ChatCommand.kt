package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class ChatCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) {
                sendHelpMessage(sender)
            } else {
                sender.sendMessage("RemmyChat Commands:")
                sender.sendMessage("/remmychat reload - Reload the plugin configuration")
                sender.sendMessage("/remmychat bstats - Check bStats metrics status")
                sender.sendMessage("/remmychat discord - Manage Discord integration")
                sender.sendMessage("/remmychat debug - Show debug information")
            }
            return true
        }

        when (args[0].lowercase(Locale.getDefault())) {
            "channel", "ch" -> {
                if (sender !is Player) {
                    val errorMsg = plugin.formatService.formatSystemMessage("error.players-only")
                    if (errorMsg != null) {
                        sender.sendMessage(errorMsg)
                    } else {
                        sender.sendMessage("This command is for players only!")
                    }
                    return true
                }
                handleChannelCommand(sender, args)
            }

            "reload" -> handleReloadCommand(sender, args)
            "bstats", "metrics" -> handleBStatsCommand(sender)
            "discord" -> handleDiscordCommand(sender, args)
            "debug" -> handleDebugCommand(sender, args)
            "info" -> handleInfoCommand(sender, args)
            else -> {
                if (sender is Player) {
                    sendHelpMessage(sender)
                } else {
                    sender.sendMessage("RemmyChat Commands:")
                    sender.sendMessage("/remmychat reload - Reload the plugin configuration")
                    sender.sendMessage("/remmychat bstats - Check bStats metrics status")
                    sender.sendMessage("/remmychat discord - Manage Discord integration")
                    sender.sendMessage("/remmychat debug - Show debug information")
                    sender.sendMessage("/remmychat info - Show system information")
                }
            }
        }

        return true
    }

    private fun handleChannelCommand(player: Player, args: Array<String>) {
        if (args.size < 2) {
            // Show current channel
            val chatUser = plugin.chatService.getChatUser(player.uniqueId)
            val currentChannel = chatUser?.currentChannel ?: "unknown"
            val message = plugin.formatService.formatSystemMessage(
                "current-channel",
                Placeholder.parsed("channel", currentChannel)
            )
            if (message != null) {
                player.sendMessage(message)
            } else {
                player.sendMessage("Current channel: $currentChannel")
            }
            return
        }

        val channelName = args[1].lowercase(Locale.getDefault())
        val channel = plugin.configManager.getChannel(channelName)

        if (channel == null) {
            val message = plugin.formatService.formatSystemMessage(
                "error.channel-not-found",
                Placeholder.parsed("channel", channelName)
            )
            if (message != null) {
                player.sendMessage(message)
            } else {
                player.sendMessage("Channel not found: $channelName")
            }
            return
        }

        // Check permission
        val permission = channel.permission
        if (permission != null && permission.isNotEmpty() && !player.hasPermission(permission)) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                player.sendMessage(message)
            } else {
                player.sendMessage("You don't have permission to use this channel!")
            }
            return
        }

        // Set channel
        plugin.chatService.setChannel(player.uniqueId, channelName)
        val message = plugin.formatService.formatSystemMessage(
            "channel-changed",
            Placeholder.parsed("channel", channelName)
        )
        if (message != null) {
            player.sendMessage(message)
        } else {
            player.sendMessage("Channel changed to: $channelName")
        }
    }

    private fun handleReloadCommand(sender: CommandSender, args: Array<String>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        // Use the new ReloadService
        val reloadService = plugin.reloadService

        // Determine component to reload
        val component = if (args.size < 2) "all" else args[1].lowercase()
        val resolvedComponent = reloadService.resolveComponent(component)

        if (resolvedComponent == null) {
            val errorMsg = plugin.formatService.formatSystemMessage("reload-component-unknown",
                Placeholder.parsed("component", component))
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("§cUnknown config component: $component")
            }

            val availableMsg = plugin.formatService.formatSystemMessage("reload-available-components")
            if (availableMsg != null) {
                sender.sendMessage(availableMsg)
            } else {
                val aliases = reloadService.getAllAliases().joinToString(", ")
                sender.sendMessage("§7Available components: $aliases")
            }
            return
        }

        // Execute reload using the service
        val future = if (resolvedComponent == "all") {
            reloadService.reloadAll(sender)
        } else {
            reloadService.reloadComponent(sender, resolvedComponent)
        }

        // Handle completion asynchronously
        future.whenComplete { result, throwable ->
            if (throwable != null) {
                plugin.logger.severe("Reload command error: ${throwable.message}")
                sender.sendMessage("§cAn error occurred during reload. Check console for details.")
            }
        }
    }

    private fun handleBStatsCommand(sender: CommandSender) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        val metricsEnabled = plugin.config.getBoolean("metrics.enabled", true)
        if (metricsEnabled) {
            sender.sendMessage("§abStats metrics are enabled and collecting anonymous usage data.")
            sender.sendMessage("§7Data helps improve the plugin. You can disable this in config.yml")
        } else {
            sender.sendMessage("§cbStats metrics are disabled.")
            sender.sendMessage("§7Enable in config.yml to help improve the plugin with anonymous usage data.")
        }
    }

    private fun handleDiscordCommand(sender: CommandSender, args: Array<String>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        if (plugin.server.pluginManager.getPlugin("DiscordSRV") == null) {
            sender.sendMessage("§cDiscordSRV is not installed or enabled!")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§6Discord Integration Status:")
            sender.sendMessage("§7- Plugin: §aDiscordSRV detected")
            sender.sendMessage("§7- Integration: §a" + if (plugin.discordSRVIntegration.isEnabled()) "Enabled" else "Disabled")
            sender.sendMessage("§7Use '/remmychat discord reload' to reload Discord settings")
            return
        }

        when (args[1].lowercase()) {
            "reload" -> {
                sender.sendMessage("§6Reloading Discord integration...")
                try {
                    plugin.discordSRVIntegration.reloadChannelMappings()
                    sender.sendMessage("§aDiscord integration reloaded successfully!")
                } catch (e: Exception) {
                    sender.sendMessage("§cFailed to reload Discord integration!")
                    plugin.logger.warning("Discord reload failed: ${e.message}")
                }
            }
            "status" -> {
                sender.sendMessage("§6Discord Integration Status:")
                sender.sendMessage("§7- Plugin: §aDiscordSRV detected")
                sender.sendMessage("§7- Integration: §a" + if (plugin.discordSRVIntegration.isEnabled()) "Enabled" else "Disabled")
                val channelCount = plugin.discordSRVIntegration.getChannelMappings().size
                sender.sendMessage("§7- Channel mappings: §b$channelCount")
            }
            else -> {
                sender.sendMessage("§cUsage: /remmychat discord <reload|status>")
            }
        }
    }

    private fun handleDebugCommand(sender: CommandSender, args: Array<String>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§6Debug Information:")
            sender.sendMessage("§7- Debug mode: §" + if (plugin.configManager.debugEnabled) "aEnabled" else "cDisabled")
            sender.sendMessage("§7- Online players: §b${plugin.server.onlinePlayers.size}")
            sender.sendMessage("§7- Loaded channels: §b${plugin.configManager.channels.size}")
            sender.sendMessage("§7- Group formats: §b${plugin.configManager.groups.size}")
            sender.sendMessage("§7Use '/remmychat debug <channels|groups|performance>' for detailed info")
            return
        }

        when (args[1].lowercase()) {
            "channels" -> {
                sender.sendMessage("§6Channel Debug Information:")
                plugin.configManager.channels.forEach { (name, channel) ->
                    sender.sendMessage("§7- §b$name§7: format=§e${channel.format}, permission=§e${channel.permission}")
                }

                if (sender is Player) {
                    sender.sendMessage("§6Player Channel Status:")
                    val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
                    val managerChannel = plugin.channelManager.getPlayerChannel(sender)
                    sender.sendMessage("§7- ChatService channel: §e${chatUser?.currentChannel}")
                    sender.sendMessage("§7- ChannelManager channel: §e${managerChannel?.name}")
                    sender.sendMessage("§7- Synchronized: §${if (chatUser?.currentChannel == managerChannel?.name) "aYes" else "cNo"}")
                }
            }
            "groups" -> {
                sender.sendMessage("§6Group Debug Information:")
                plugin.configManager.groups.forEach { (name, format) ->
                    sender.sendMessage("§7- §b$name§7: §e${format.chatFormat}")
                }
            }
            "performance" -> {
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory() / 1024 / 1024
                val freeMemory = runtime.freeMemory() / 1024 / 1024
                val usedMemory = totalMemory - freeMemory

                sender.sendMessage("§6Performance Debug Information:")
                sender.sendMessage("§7- Memory usage: §b${usedMemory}MB§7/§b${totalMemory}MB")
                sender.sendMessage("§7- Online players: §b${plugin.server.onlinePlayers.size}")
            }
            else -> {
                sender.sendMessage("§cUsage: /remmychat debug <channels|groups|performance>")
            }
        }
    }

    private fun handleInfoCommand(sender: CommandSender, args: Array<String>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        sender.sendMessage("§6RemmyChat System Information:")
        sender.sendMessage("§7- Version: §b${plugin.description.version}")
        sender.sendMessage("§7- Server: §b${plugin.server.name} ${plugin.server.version}")
        sender.sendMessage("§7- Java: §b${System.getProperty("java.version")}")
        sender.sendMessage("§7- Plugin author: §b${plugin.description.authors.joinToString(", ")}")

        if (args.size > 1) {
            when (args[1].lowercase()) {
                "integrations" -> {
                    sender.sendMessage("§6Integration Status:")
                    sender.sendMessage("§7- DiscordSRV: §" + if (plugin.server.pluginManager.getPlugin("DiscordSRV") != null) "aDetected" else "cNot found")
                    sender.sendMessage("§7- PlaceholderAPI: §" + if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) "aDetected" else "cNot found")
                    sender.sendMessage("§7- Vault: §" + if (plugin.server.pluginManager.getPlugin("Vault") != null) "aDetected" else "cNot found")
                }
                "database" -> {
                    sender.sendMessage("§6Database Information:")
                    sender.sendMessage("§7- Type: §b${plugin.configManager.getDatabaseType()}")
                    sender.sendMessage("§7- Host: §b${plugin.configManager.getDatabaseHost()}")
                }
            }
        }
    }

    private fun sendHelpMessage(player: Player) {
        val formatService = plugin.formatService

        val headerMsg = formatService.formatSystemMessage("help-header")
        if (headerMsg != null) player.sendMessage(headerMsg)

        val channelMsg = formatService.formatSystemMessage("help-channel")
        if (channelMsg != null) player.sendMessage(channelMsg)

        if (player.hasPermission("remmychat.admin")) {
            val reloadMsg = formatService.formatSystemMessage("help-reload")
            if (reloadMsg != null) player.sendMessage(reloadMsg)

            val debugMsg = formatService.formatSystemMessage("help-debug")
            if (debugMsg != null) player.sendMessage(debugMsg)
        }

        val footerMsg = formatService.formatSystemMessage("help-footer")
        if (footerMsg != null) player.sendMessage(footerMsg)
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): MutableList<String> {
        val completions: MutableList<String> = ArrayList()
        val input = if (args.isNotEmpty()) args.last().lowercase() else ""

        when (args.size) {
            1 -> {
                // Main subcommands
                val subcommands = mutableListOf("help", "version", "channel")

                if (sender.hasPermission("remmychat.admin")) {
                    subcommands.addAll(listOf("reload", "bstats", "discord", "debug", "info"))
                }

                completions.addAll(subcommands.filter { it.startsWith(input) })
            }

            2 -> {
                when (args[0].lowercase()) {
                    "reload" -> {
                        if (sender.hasPermission("remmychat.admin")) {
                            val reloadComponents = plugin.reloadService.getAllAliases()
                            completions.addAll(reloadComponents.filter { it.startsWith(input) })
                        }
                    }

                    "channel" -> {
                        // Add available channels
                        val channels = plugin.configManager.channels.keys
                        completions.addAll(channels.filter { it.startsWith(input, true) })
                    }

                    "discord" -> {
                        if (sender.hasPermission("remmychat.admin")) {
                            val discordSubcommands = listOf("reload", "status")
                            completions.addAll(discordSubcommands.filter { it.startsWith(input) })
                        }
                    }

                    "debug" -> {
                        if (sender.hasPermission("remmychat.admin")) {
                            val debugSubcommands = listOf("channels", "groups", "performance")
                            completions.addAll(debugSubcommands.filter { it.startsWith(input) })
                        }
                    }

                    "info" -> {
                        if (sender.hasPermission("remmychat.admin")) {
                            val infoSubcommands = listOf("integrations", "database")
                            completions.addAll(infoSubcommands.filter { it.startsWith(input) })
                        }
                    }
                }
            }

            3 -> {
                when (args[0].lowercase()) {
                    "debug" -> {
                        when (args[1].lowercase()) {
                            "channels", "groups" -> {
                                if (sender.hasPermission("remmychat.admin")) {
                                    // Add specific channel/group names
                                    val channels = plugin.configManager.channels.keys
                                    completions.addAll(channels.filter { it.startsWith(input, true) })
                                }
                            }
                        }
                    }
                }
            }
        }

        return completions.sorted().toMutableList()
    }
}
