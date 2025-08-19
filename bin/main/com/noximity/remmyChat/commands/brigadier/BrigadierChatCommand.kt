package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.CompletableFuture

class BrigadierChatCommand(private val plugin: RemmyChat) : Command<CommandSourceStack>, SuggestionProvider<CommandSourceStack> {

    private val mm = MiniMessage.miniMessage()

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Get subcommand argument (optional)
        val subcommand = try {
            context.getArgument("subcommand", String::class.java)
        } catch (e: Exception) {
            null
        }

        if (subcommand == null) {
            if (sender is Player) {
                sendHelpMessage(sender)
            } else {
                sender.sendMessage(mm.deserialize("<gold>RemmyChat Commands:"))
                sender.sendMessage(mm.deserialize("<yellow>/remmychat reload</yellow> <white>- Reload the plugin configuration</white>"))
            }
            return 1
        }

        when (subcommand.lowercase(Locale.getDefault())) {
            "channel", "ch" -> {
                if (sender !is Player) {
                    MessageUtil.sendMessage(stack.sender, "error", "player_only")
                    return 0
                }
                handleChannelCommand(sender, context)
            }
            "reload" -> handleReloadCommand(sender, context)
            "bstats", "metrics" -> handleBStatsCommand(sender)
            "discord" -> handleDiscordCommand(sender, context)
            "debug" -> handleDebugCommand(sender)
            else -> {
                if (sender is Player) {
                    sendHelpMessage(sender)
                } else {
                    sender.sendMessage(mm.deserialize("<gold>RemmyChat Commands:"))
                    sender.sendMessage(mm.deserialize("<yellow>/remmychat reload</yellow> <white>- Reload the plugin configuration</white>"))
                    sender.sendMessage(mm.deserialize("<yellow>/remmychat bstats</yellow> <white>- Check bStats metrics status</white>"))
                    sender.sendMessage(mm.deserialize("<yellow>/remmychat discord</yellow> <white>- Manage Discord integration</white>"))
                    sender.sendMessage(mm.deserialize("<yellow>/remmychat debug</yellow> <white>- Show debug information</white>"))
                }
            }
        }

        return 1
    }

    private fun handleChannelCommand(player: Player, context: CommandContext<CommandSourceStack>) {
        val channelName = try {
            context.getArgument("channel", String::class.java)
        } catch (e: Exception) {
            null
        }

        if (channelName == null) {
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
                MessageUtil.sendMessage(player, "", "current-channel", "channel" to currentChannel)
            }
            return
        }

        val channelNameLower = channelName.lowercase(Locale.getDefault())
        val channel = plugin.configManager.getChannel(channelNameLower)

        if (channel == null) {
            val message = plugin.formatService.formatSystemMessage(
                "error.channel-not-found",
                Placeholder.parsed("channel", channelName)
            )
            if (message != null) {
                player.sendMessage(message)
            } else {
                MessageUtil.sendMessage(player, "error", "channel_not_found", "channel" to channelName)
            }
            return
        }

        // Check permission
        val permission = channel.permission
        if (permission != null && permission.isNotEmpty() && !player.hasPermission(permission)) {
            MessageUtil.sendMessage(player, "error", "no_permission")
            return
        }

        // Set channel
        plugin.chatService.setChannel(player.uniqueId, channelNameLower)
        val message = plugin.formatService.formatSystemMessage(
            "channel-changed",
            Placeholder.parsed("channel", channelName)
        )
        if (message != null) {
            player.sendMessage(message)
        } else {
            MessageUtil.sendMessage(player, "", "channel-changed", "channel" to channelName)
        }
    }

    private fun handleReloadCommand(sender: org.bukkit.command.CommandSender, context: CommandContext<CommandSourceStack>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            MessageUtil.sendMessage(sender, "error", "no_permission")
            return
        }

        // Use the new ReloadService
        val reloadService = plugin.reloadService

        // Get reload component argument (optional)
        val component = try {
            context.getArgument("reload_component", String::class.java)?.lowercase() ?: "all"
        } catch (e: Exception) {
            "all"
        }

        val resolvedComponent = reloadService.resolveComponent(component)

        if (resolvedComponent == null) {
            sender.sendMessage(mm.deserialize("<red>Unknown config component: $component"))
            val aliases = reloadService.getAllAliases().joinToString(", ")
            sender.sendMessage(mm.deserialize("<gray>Available components: $aliases"))
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
                sender.sendMessage(mm.deserialize("<red>An error occurred during reload. Check console for details."))
            }
        }
    }



    private fun handleDiscordCommand(sender: org.bukkit.command.CommandSender, context: CommandContext<CommandSourceStack>) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            MessageUtil.sendMessage(sender, "error", "no_permission")
            return
        }

        val discordSubcommand = try {
            context.getArgument("discord_subcommand", String::class.java)
        } catch (e: Exception) {
            null
        }

        if (discordSubcommand == null) {
            // Show Discord integration status
            sender.sendMessage(mm.deserialize("<gold>Discord Integration Status:"))
            sender.sendMessage(mm.deserialize("<gray>- DiscordSRV Plugin: ${if (plugin.isDiscordSRVEnabled) "<green>Enabled" else "<red>Disabled"}"))
            sender.sendMessage(mm.deserialize("<gray>- Integration Active: ${if (plugin.discordSRVIntegration.isEnabled()) "<green>Yes" else "<red>No"}"))

            if (plugin.discordSRVIntegration.isEnabled()) {
                sender.sendMessage(mm.deserialize("<yellow>Channel Mappings:"))
                plugin.discordSRVIntegration.getChannelMappings().forEach { (remmy, discord) ->
                    sender.sendMessage(mm.deserialize("  <blue>$remmy</blue> <white>-></white> <light_purple>$discord</light_purple>"))
                }
            }
            return
        }

        when (discordSubcommand.lowercase()) {
            "reload" -> {
                if (plugin.isDiscordSRVEnabled) {
                    plugin.discordSRVIntegration.reloadChannelMappings()
                    sender.sendMessage(mm.deserialize("<green>Discord integration reloaded!"))
                } else {
                    sender.sendMessage(mm.deserialize("<red>DiscordSRV is not available"))
                }
            }
            "status" -> {
                sender.sendMessage(mm.deserialize("<gold>Discord Integration Details:"))
                sender.sendMessage(mm.deserialize("<gray>- DiscordSRV Plugin: ${if (plugin.isDiscordSRVEnabled) "<green>Found" else "<red>Not Found"}"))
                sender.sendMessage(mm.deserialize("<gray>- Integration Status: ${if (plugin.discordSRVIntegration.isEnabled()) "<green>Active" else "<red>Inactive"}"))
                sender.sendMessage(mm.deserialize("<gray>- Channel Mappings: <blue>${plugin.discordSRVIntegration.getChannelMappings().size}"))

                sender.sendMessage(mm.deserialize("<yellow>Channel Mappings:"))
                plugin.discordSRVIntegration.getChannelMappings().forEach { (remmy, discord) ->
                    sender.sendMessage(mm.deserialize("  <blue>$remmy</blue> <white>-></white> <light_purple>$discord</light_purple>"))
                }
            }
            "validate" -> {
                sender.sendMessage(mm.deserialize("<gold>Validating Discord channels..."))
                val validationResults = plugin.discordSRVIntegration.validateDiscordChannels()

                var validCount = 0
                var invalidCount = 0

                validationResults.forEach { (remmyChannel, isValid) ->
                    val discordChannel = plugin.discordSRVIntegration.getChannelMappings()[remmyChannel]
                    if (isValid) {
                        sender.sendMessage(mm.deserialize("<green>✅ $remmyChannel -> $discordChannel"))
                        validCount++
                    } else {
                        sender.sendMessage(mm.deserialize("<red>❌ $remmyChannel -> $discordChannel"))
                        invalidCount++
                    }
                }

                sender.sendMessage(mm.deserialize("<yellow>Validation Results: <green>$validCount valid, <red>$invalidCount invalid"))
                if (invalidCount > 0) {
                    sender.sendMessage(mm.deserialize("<red>Please check that the Discord channel names in discord.yml match your actual Discord channels"))
                }
            }
            "diagnostics" -> {
                sender.sendMessage(mm.deserialize("<gold>Generating Discord diagnostics..."))
                val diagnostics = plugin.discordSRVIntegration.getDiagnosticInfo()
                diagnostics.split("\n").forEach { line ->
                    if (line.isNotEmpty()) {
                        sender.sendMessage(mm.deserialize("<gray>$line"))
                    }
                }
            }
            "configure" -> {
                sender.sendMessage(mm.deserialize("<gold>Generating Discord configuration suggestions..."))

                try {
                    val configHelper = com.noximity.remmyChat.discord.DiscordConfigHelper(plugin)
                    val validation = configHelper.validateConfiguration()

                    if (validation.isValid) {
                        sender.sendMessage(mm.deserialize("<green>✅ Discord configuration is valid!"))
                    } else {
                        sender.sendMessage(mm.deserialize("<red>❌ Discord configuration has issues:"))
                        validation.issues.forEach { issue ->
                            sender.sendMessage(mm.deserialize("<red>  • $issue"))
                        }

                        if (validation.suggestions.isNotEmpty()) {
                            sender.sendMessage(mm.deserialize("<yellow>💡 Suggestions:"))
                            validation.suggestions.forEach { suggestion ->
                                sender.sendMessage(mm.deserialize("<yellow>  • $suggestion"))
                            }
                        }

                        if (validation.warnings.isNotEmpty()) {
                            sender.sendMessage(mm.deserialize("<gold>⚠️  Warnings:"))
                            validation.warnings.forEach { warning ->
                                sender.sendMessage(mm.deserialize("<gold>  • $warning"))
                            }
                        }
                    }
                } catch (e: Exception) {
                    sender.sendMessage(mm.deserialize("<red>Error generating configuration: ${e.message}"))
                }
            }
            "fix" -> {
                sender.sendMessage(mm.deserialize("<gold>Generating corrected Discord configuration..."))

                try {
                    val configHelper = com.noximity.remmyChat.discord.DiscordConfigHelper(plugin)
                    val correctedFile = configHelper.saveCorrectedConfigToFile()

                    sender.sendMessage(mm.deserialize("<green>✅ Corrected configuration saved to: <blue>${correctedFile.name}"))
                    sender.sendMessage(mm.deserialize("<yellow>Review the file and replace your discord.yml if satisfied"))
                    sender.sendMessage(mm.deserialize("<gray>Then run '/remmychat discord reload' to apply changes"))
                } catch (e: Exception) {
                    sender.sendMessage(mm.deserialize("<red>Error generating corrected configuration: ${e.message}"))
                }
            }
            "test" -> {
                val testTarget = try {
                    context.getArgument("test_target", String::class.java)
                } catch (e: Exception) {
                    null
                }

                if (testTarget == null) {
                    sender.sendMessage(mm.deserialize("<yellow>Available channels to test:"))
                    plugin.discordSRVIntegration.getChannelMappings().forEach { (remmy, discord) ->
                        sender.sendMessage(mm.deserialize("  <blue>$remmy</blue> <gray>(</gray><light_purple>$discord</light_purple><gray>)</gray>"))
                    }
                    sender.sendMessage(mm.deserialize("<gray>Usage: /remmychat discord test <channel>"))
                    return
                }

                sender.sendMessage(mm.deserialize("<gold>Testing Discord channel: <blue>$testTarget"))

                val success = plugin.discordSRVIntegration.testChannelMessage(testTarget)
                if (success) {
                    sender.sendMessage(mm.deserialize("<green>✅ Test message sent successfully! Check your Discord server."))
                } else {
                    sender.sendMessage(mm.deserialize("<red>❌ Failed to send test message. Check console for details."))
                    sender.sendMessage(mm.deserialize("<gray>Run '/remmychat discord validate' to check channel configuration"))
                }
            }
            else -> {
                sender.sendMessage(mm.deserialize("<yellow>Discord subcommands:"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord</yellow> <white>- Show integration status</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord status</yellow> <white>- Show detailed status</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord validate</yellow> <white>- Validate channel mappings</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord diagnostics</yellow> <white>- Show detailed diagnostics</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord test <channel></yellow> <white>- Test specific channel</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord configure</yellow> <white>- Check configuration and get suggestions</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord fix</yellow> <white>- Generate corrected configuration file</white>"))
                sender.sendMessage(mm.deserialize("  <yellow>/remmychat discord reload</yellow> <white>- Reload channel mappings</white>"))
            }
        }
    }

    private fun handleBStatsCommand(sender: org.bukkit.command.CommandSender) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            MessageUtil.sendMessage(sender, "error", "no_permission")
            return
        }

        sender.sendMessage(mm.deserialize("<gold><bold>=== bStats Metrics Status ==="))
        sender.sendMessage(mm.deserialize("<gray>Plugin ID: <blue>26691"))
        sender.sendMessage(mm.deserialize("<gray>Plugin Version: <blue>${plugin.description.version}"))
        sender.sendMessage(mm.deserialize("<gray>Server Software: <blue>${plugin.server.name}"))
        sender.sendMessage(mm.deserialize("<gray>Server Version: <blue>${plugin.server.version}"))
        sender.sendMessage(mm.deserialize("<gray>Java Version: <blue>${System.getProperty("java.version")}"))

        // Check bStats folder
        val bStatsFolder = java.io.File(plugin.dataFolder.parent, "bStats")
        sender.sendMessage(mm.deserialize("<gray>bStats Folder: <blue>${if (bStatsFolder.exists()) "Found" else "Not Found"}"))

        if (bStatsFolder.exists()) {
            val configFile = java.io.File(bStatsFolder, "config.yml")
            sender.sendMessage(mm.deserialize("<gray>bStats Config: <blue>${if (configFile.exists()) "Found" else "Not Found"}"))

            if (configFile.exists()) {
                try {
                    val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile)
                    val enabled = config.getBoolean("enabled", true)
                    sender.sendMessage(mm.deserialize("<gray>bStats Enabled: <blue>$enabled"))
                    val logErrors = config.getBoolean("logFailedRequests", false)
                    sender.sendMessage(mm.deserialize("<gray>Log Errors: <blue>$logErrors"))
                } catch (e: Exception) {
                    sender.sendMessage(mm.deserialize("<gray>bStats Config: <red>Error reading config"))
                }
            }
        }

        sender.sendMessage(mm.deserialize("<gray>Debug Logging: <blue>${plugin.config.getBoolean("debug.enabled", false)}"))
        sender.sendMessage(Component.empty())
        sender.sendMessage(mm.deserialize("<green>Check your plugin on bStats.org:"))
        sender.sendMessage(mm.deserialize("<blue>https://bstats.org/plugin/bukkit/RemmyChat/26691"))
        sender.sendMessage(Component.empty())
        sender.sendMessage(mm.deserialize("<gray>If you don't see data after 30 minutes:"))
        sender.sendMessage(mm.deserialize("<gray>- Check that bStats is enabled globally"))
        sender.sendMessage(mm.deserialize("<gray>- Restart the server to initialize metrics"))
        sender.sendMessage(mm.deserialize("<gray>- Ensure server has internet access"))
    }

    private fun sendHelpMessage(player: Player) {
        val formatService = plugin.formatService

        val headerMsg = formatService.formatSystemMessage("help-header")
        if (headerMsg != null) {
            player.sendMessage(headerMsg)
        } else {
            MessageUtil.sendMessage(player, "", "help-header")
        }

        val channelMsg = formatService.formatSystemMessage("help-channel")
        if (channelMsg != null) {
            player.sendMessage(channelMsg)
        } else {
            MessageUtil.sendMessage(player, "", "help-channel")
        }

        if (player.hasPermission("remmychat.admin")) {
            val reloadMsg = formatService.formatSystemMessage("help-reload")
            if (reloadMsg != null) {
                player.sendMessage(reloadMsg)
            } else {
                MessageUtil.sendMessage(player, "", "help-reload")
            }

            val discordMsg = formatService.formatSystemMessage("help-discord")
            if (discordMsg != null) {
                player.sendMessage(discordMsg)
            } else {
                MessageUtil.sendMessage(player, "", "help-discord")
            }
        }

        val footerMsg = formatService.formatSystemMessage("help-footer")
        if (footerMsg != null) {
            player.sendMessage(footerMsg)
        } else {
            MessageUtil.sendMessage(player, "", "help-footer")
        }
    }

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = context.source.sender
        val args = context.input.split(" ")

        when (args.size) {
            2 -> {
                // First argument - subcommands
                val input = args[1].lowercase()
                val suggestions = mutableListOf("channel")

                if (sender.hasPermission("remmychat.admin")) {
                    suggestions.addAll(listOf("reload", "bstats", "discord", "debug"))
                }

                suggestions.filter { it.startsWith(input) }
                    .forEach { builder.suggest(it) }
            }
            3 -> {
                when (args[1].lowercase()) {
                    "reload" -> {
                        // Reload component suggestions
                        if (sender.hasPermission("remmychat.admin")) {
                            val input = args[2].lowercase()
                            val reloadComponents = plugin.reloadService.getAllAliases()
                            reloadComponents
                                .filter { it.startsWith(input) }
                                .forEach { builder.suggest(it) }
                        }
                    }
                    "test" -> {
                        if (sender.hasPermission("remmychat.admin") && args.size >= 4) {
                            val input = args[3].lowercase()
                            plugin.discordSRVIntegration.getChannelMappings().keys
                                .filter { it.startsWith(input) }
                                .forEach { builder.suggest(it) }
                        }
                    }
                    "channel" -> {
                        // Channel names
                        if (sender is Player) {
                            val input = args[2].lowercase()
                            val channels = plugin.configManager.channels
                            for ((channelName, channel) in channels) {
                                val permission = channel.permission
                                if ((permission == null || permission.isEmpty() || sender.hasPermission(permission as String))
                                    && channelName.startsWith(input)) {
                                    builder.suggest(channelName)
                                }
                            }
                        }
                    }
                    "discord" -> {
                        if (sender.hasPermission("remmychat.admin")) {
                            val input = args[2].lowercase()
                            listOf("status", "reload", "validate", "diagnostics", "test", "configure", "fix")
                                .filter { it.startsWith(input) }
                                .forEach { builder.suggest(it) }
                        }
                    }
                }
            }
        }

        return builder.buildFuture()
    }

    private fun handleDebugCommand(sender: CommandSender) {
        if (!sender.hasPermission("remmychat.admin")) {
            sender.sendMessage(mm.deserialize("<red>You don't have permission to use this command!"))
            return
        }

        sender.sendMessage(mm.deserialize("<gold>=== RemmyChat Debug Information ==="))

        // Integration status
        sender.sendMessage(mm.deserialize("<yellow>Integrations:"))
        sender.sendMessage(mm.deserialize("  <white>LuckPerms Hooked: <${if (plugin.permissionService.isLuckPermsHooked) "green>Yes" else "red>No"}>"))
        sender.sendMessage(mm.deserialize("  <white>PlaceholderAPI: <${if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) "green>Found" else "red>Not Found"}>"))
        sender.sendMessage(mm.deserialize("  <white>DiscordSRV: <${if (plugin.isDiscordSRVEnabled) "green>Enabled" else "red>Disabled"}>"))

        // Configuration status
        sender.sendMessage(mm.deserialize("<yellow>Configuration:"))
        sender.sendMessage(mm.deserialize("  <white>Use Group Format: <${if (plugin.configManager.isUseGroupFormat) "green>Yes" else "red>No"}>"))
        sender.sendMessage(mm.deserialize("  <white>Debug Enabled: <${if (plugin.config.getBoolean("debug.enabled", false)) "green>Yes" else "red>No"}>"))

        // If sender is a player, show their specific info
        if (sender is Player) {
            sender.sendMessage(mm.deserialize("<yellow>Your Information:"))

            // Try to get group info
            if (plugin.permissionService.isLuckPermsHooked) {
                val primaryGroup = plugin.permissionService.getPrimaryGroup(sender)
                sender.sendMessage(mm.deserialize("  <white>Primary Group: <gray>${primaryGroup ?: "Unknown"}"))

                val groupFormat = plugin.permissionService.getHighestGroupFormat(sender)
                if (groupFormat != null) {
                    sender.sendMessage(mm.deserialize("  <white>Group Format Name: <gray>${groupFormat.name}"))
                    sender.sendMessage(mm.deserialize("  <white>Group Prefix: <gray>'${groupFormat.prefix}'"))
                    sender.sendMessage(mm.deserialize("  <white>Group Name Style: <gray>'${groupFormat.nameStyle}'"))
                    sender.sendMessage(mm.deserialize("  <white>Chat Format: <gray>'${groupFormat.chatFormat}'"))
                } else {
                    sender.sendMessage(mm.deserialize("  <white>Group Format: <red>None found"))
                }
            } else {
                sender.sendMessage(mm.deserialize("  <white>LuckPerms not hooked - cannot show group info"))
            }

            // Show current channel
            val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
            val currentChannel = chatUser?.currentChannel ?: "unknown"
            sender.sendMessage(mm.deserialize("  <white>Current Channel: <gray>$currentChannel"))
        }
    }
}
