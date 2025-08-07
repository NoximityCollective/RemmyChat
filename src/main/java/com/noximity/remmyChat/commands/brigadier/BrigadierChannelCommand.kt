package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class BrigadierChannelCommand(private val plugin: RemmyChat) : Command<CommandSourceStack>, SuggestionProvider<CommandSourceStack> {

    private val mm = MiniMessage.miniMessage()

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Only players can use this command
        if (sender !is Player) {
            MessageUtil.sendMessage(stack.sender, "error", "player_only")
            return 0
        }

        if (!plugin.channelManager.isSwitchingEnabled()) {
            sender.sendMessage(mm.deserialize("<red>Channel switching is disabled!"))
            return 0
        }

        // Get subcommand argument (optional)
        val subcommand = try {
            context.getArgument("subcommand", String::class.java)
        } catch (e: Exception) {
            null
        }

        // Get channel argument (optional)
        val channelArg = try {
            context.getArgument("channel", String::class.java)
        } catch (e: Exception) {
            null
        }

        when {
            subcommand == null && channelArg == null -> {
                // Show current channel and available channels
                showChannelInfo(sender)
                return 1
            }
            subcommand != null -> {
                handleSubcommand(sender, subcommand, channelArg)
                return 1
            }
            channelArg != null -> {
                // Try to switch to channel
                switchToChannel(sender, channelArg)
                return 1
            }
            else -> {
                showChannelInfo(sender)
                return 1
            }
        }
    }

    private fun handleSubcommand(player: Player, subcommand: String, channelArg: String?) {
        when (subcommand.lowercase()) {
            "list", "ls" -> {
                showChannelList(player)
            }
            "info" -> {
                if (channelArg != null) {
                    showChannelInfo(player, channelArg)
                } else {
                    showCurrentChannelInfo(player)
                }
            }
            "history", "hist" -> {
                showChannelHistory(player, channelArg)
            }
            "help", "?" -> {
                showHelp(player)
            }
            "join" -> {
                if (channelArg != null) {
                    joinChannel(player, channelArg)
                } else {
                    player.sendMessage(mm.deserialize("<red>Usage: /ch join <channel>"))
                }
            }
            "leave" -> {
                if (channelArg != null) {
                    leaveChannel(player, channelArg)
                } else {
                    player.sendMessage(mm.deserialize("<red>Usage: /ch leave <channel>"))
                }
            }
            else -> {
                // Try to switch to channel if subcommand is not recognized
                switchToChannel(player, subcommand)
            }
        }
    }

    /**
     * Switch player to a channel
     */
    private fun switchToChannel(player: Player, channelInput: String) {
        // Check for quick switch first
        val quickSwitchChannel = plugin.channelManager.getQuickSwitchChannel(channelInput)
        val targetChannel = quickSwitchChannel ?: channelInput

        val channel = plugin.channelManager.getChannel(targetChannel)
        if (channel == null) {
            player.sendMessage(mm.deserialize("<red>Channel '$targetChannel' not found!"))
            if (plugin.channelManager.shouldShowListOnSwitch()) {
                showChannelList(player)
            }
            return
        }

        if (!channel.enabled) {
            player.sendMessage(mm.deserialize("<red>Channel '${channel.getEffectiveDisplayName()}' is currently disabled!"))
            return
        }

        if (!channel.hasPermission(player)) {
            player.sendMessage(mm.deserialize("<red>You don't have permission to use channel '${channel.getEffectiveDisplayName()}'!"))
            return
        }

        val currentChannel = plugin.channelManager.getPlayerChannel(player)
        if (currentChannel?.name == channel.name) {
            player.sendMessage(mm.deserialize("<yellow>You are already in channel '${channel.getEffectiveDisplayName()}'!"))
            return
        }

        if (plugin.channelManager.setPlayerChannel(player, channel.name)) {
            val displayName = if (channel.getEffectiveDisplayName().isNotEmpty()) {
                channel.getEffectiveDisplayName()
            } else {
                channel.name
            }

            player.sendMessage(mm.deserialize("<green>You are now chatting in <white>${displayName}</white>!"))

            if (channel.description.isNotEmpty()) {
                player.sendMessage(mm.deserialize("<gray>${channel.description}"))
            }

            // Show channel info
            showChannelQuickInfo(player, channel)
        } else {
            player.sendMessage(mm.deserialize("<red>Failed to switch to channel '${channel.getEffectiveDisplayName()}'!"))
        }
    }

    /**
     * Join a channel (without leaving current)
     */
    private fun joinChannel(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage(mm.deserialize("<red>Channel '$channelName' not found!"))
            return
        }

        if (!channel.enabled) {
            player.sendMessage(mm.deserialize("<red>Channel '${channel.getEffectiveDisplayName()}' is currently disabled!"))
            return
        }

        if (!channel.hasPermission(player)) {
            player.sendMessage(mm.deserialize("<red>You don't have permission to join channel '${channel.getEffectiveDisplayName()}'!"))
            return
        }

        if (plugin.channelManager.joinChannel(player, channel.name)) {
            player.sendMessage(mm.deserialize("<green>Joined channel '${channel.getEffectiveDisplayName()}'!"))
        } else {
            player.sendMessage(mm.deserialize("<red>Failed to join channel '${channel.getEffectiveDisplayName()}'!"))
        }
    }

    /**
     * Leave a channel
     */
    private fun leaveChannel(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage(mm.deserialize("<red>Channel '$channelName' not found!"))
            return
        }

        if (plugin.channelManager.leaveChannel(player, channel.name)) {
            player.sendMessage(mm.deserialize("<green>Left channel '${channel.getEffectiveDisplayName()}'!"))
        } else {
            player.sendMessage(mm.deserialize("<red>Failed to leave channel '${channel.getEffectiveDisplayName()}'!"))
        }
    }

    /**
     * Show current channel info and available channels
     */
    private fun showChannelInfo(player: Player) {
        val currentChannel = plugin.channelManager.getPlayerChannel(player)

        if (currentChannel != null) {
            player.sendMessage(mm.deserialize("<gold>=== Current Channel ==="))
            player.sendMessage(mm.deserialize("<yellow>Channel: <white>${currentChannel.getEffectiveDisplayName()}"))
            if (currentChannel.description.isNotEmpty()) {
                player.sendMessage(mm.deserialize("<yellow>Description: <white>${currentChannel.description}"))
            }
            player.sendMessage(mm.deserialize("<yellow>Type: <white>${currentChannel.getChannelType()}"))
            player.sendMessage(mm.deserialize("<yellow>Members: <white>${plugin.channelManager.getChannelMembers(currentChannel.name).size}"))
        } else {
            player.sendMessage(mm.deserialize("<red>You are not in any channel!"))
        }

        if (plugin.channelManager.shouldShowListOnSwitch()) {
            player.sendMessage(Component.empty())
            showChannelList(player)
        }
    }

    /**
     * Show information about a specific channel
     */
    private fun showChannelInfo(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage(mm.deserialize("<red>Channel '$channelName' not found!"))
            return
        }

        player.sendMessage(mm.deserialize("<gold>=== Channel Information ==="))
        player.sendMessage(mm.deserialize("<yellow>Name: <white>${channel.name}"))
        player.sendMessage(mm.deserialize("<yellow>Display Name: <white>${channel.getEffectiveDisplayName()}"))
        player.sendMessage(mm.deserialize("<yellow>Description: <white>${channel.description}"))
        player.sendMessage(mm.deserialize("<yellow>Type: <white>${channel.getChannelType()}"))
        player.sendMessage(mm.deserialize("<yellow>Enabled: <white>${if (channel.enabled) "<green>Yes" else "<red>No"}"))
        player.sendMessage(mm.deserialize("<yellow>Permission: <white>${if (channel.permission.isEmpty()) "<green>None" else channel.permission}"))
        player.sendMessage(mm.deserialize("<yellow>Members: <white>${plugin.channelManager.getChannelMembers(channel.name).size}"))
        player.sendMessage(mm.deserialize("<yellow>Range: <white>${if (channel.radius < 0) "Global" else "${channel.radius} blocks"}"))
        player.sendMessage(mm.deserialize("<yellow>Cross-Server: <white>${if (channel.isCrossServer()) "<green>Yes" else "<red>No"}"))

        if (channel.hasPermission(player)) {
            player.sendMessage(mm.deserialize("<green>You can use this channel!"))
        } else {
            player.sendMessage(mm.deserialize("<red>You cannot use this channel!"))
        }
    }

    /**
     * Show current channel detailed info
     */
    private fun showCurrentChannelInfo(player: Player) {
        val currentChannel = plugin.channelManager.getPlayerChannel(player)
        if (currentChannel == null) {
            player.sendMessage(mm.deserialize("<red>You are not in any channel!"))
            return
        }

        showChannelInfo(player, currentChannel.name)
    }

    /**
     * Show list of available channels
     */
    private fun showChannelList(player: Player) {
        val accessibleChannels = plugin.channelManager.getAccessibleChannels(player)
        val currentChannel = plugin.channelManager.getPlayerChannel(player)

        if (accessibleChannels.isEmpty()) {
            player.sendMessage(mm.deserialize("<red>No channels available!"))
            return
        }

        player.sendMessage(mm.deserialize("<gold>=== Available Channels ==="))

        for (channel in accessibleChannels.sortedBy { it.name }) {
            val displayName = channel.getEffectiveDisplayName()
            val memberCount = plugin.channelManager.getChannelMembers(channel.name).size
            val current = if (currentChannel?.name == channel.name) " <green>(current)" else ""
            val crossServer = if (channel.isCrossServer()) " <blue>[Cross-Server]" else ""

            player.sendMessage(mm.deserialize("<yellow>${channel.name}</yellow>: ${displayName} <gray>(${memberCount} members)</gray>${crossServer}${current}"))

            if (channel.description.isNotEmpty()) {
                player.sendMessage(mm.deserialize("  <gray>${channel.description}"))
            }
        }

        // Show quick switch info
        val quickSwitchMap = plugin.channelManager.getQuickSwitchChannel("1")
        if (quickSwitchMap != null) {
            player.sendMessage(Component.empty())
            player.sendMessage(mm.deserialize("<gray>Quick switch: Use <yellow>/ch 1-5</yellow> for quick channel switching"))
        }
    }

    /**
     * Show quick channel info
     */
    private fun showChannelQuickInfo(player: Player, channel: Channel) {
        val memberCount = plugin.channelManager.getChannelMembers(channel.name).size
        val type = channel.getChannelType()
        player.sendMessage(mm.deserialize("<gray>Type: $type <dark_gray>|</dark_gray> <gray>Members: $memberCount"))
    }

    /**
     * Show channel history (placeholder)
     */
    private fun showChannelHistory(player: Player, channelName: String? = null) {
        val targetChannel = channelName ?: plugin.channelManager.getPlayerChannel(player)?.name

        if (targetChannel == null) {
            player.sendMessage(mm.deserialize("<red>No channel specified and you're not in any channel!"))
            return
        }

        // TODO: Implement history system
        player.sendMessage(mm.deserialize("<yellow>Channel history for '$targetChannel' is not yet implemented!"))
    }

    /**
     * Show command help
     */
    private fun showHelp(player: Player) {
        player.sendMessage(mm.deserialize("<gold>=== Channel Commands ==="))
        player.sendMessage(mm.deserialize("<yellow>/ch</yellow> <white>- Show current channel and list</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch <channel></yellow> <white>- Switch to channel</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch list</yellow> <white>- Show all available channels</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch info [channel]</yellow> <white>- Show channel information</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch join <channel></yellow> <white>- Join a channel</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch leave <channel></yellow> <white>- Leave a channel</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch history [channel]</yellow> <white>- Show channel history</white>"))
        player.sendMessage(mm.deserialize("<yellow>/ch help</yellow> <white>- Show this help</white>"))

        // Show quick switch info
        val quickSwitchMap = plugin.channelManager.getQuickSwitchChannel("1")
        if (quickSwitchMap != null) {
            player.sendMessage(Component.empty())
            player.sendMessage(mm.deserialize("<gray>Quick switch: <yellow>/ch 1</yellow>, <yellow>/ch 2</yellow>, etc."))
        }
    }

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = context.source.sender
        if (sender !is Player) return builder.buildFuture()

        val args = context.input.split(" ")

        when (args.size) {
            2 -> {
                // First argument suggestions
                val input = args[1].lowercase()
                val suggestions = mutableListOf<String>()

                // Add subcommands
                val subcommands = listOf("list", "info", "join", "leave", "history", "help")
                suggestions.addAll(subcommands.filter { it.startsWith(input) })

                // Add channel names
                val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                suggestions.addAll(accessibleChannels.map { it.name }.filter { it.startsWith(input) })

                // Add quick switch numbers
                for (i in 1..5) {
                    val quickChannel = plugin.channelManager.getQuickSwitchChannel(i.toString())
                    if (quickChannel != null && i.toString().startsWith(input)) {
                        suggestions.add(i.toString())
                    }
                }

                suggestions.sorted().forEach { builder.suggest(it) }
            }
            3 -> {
                // Second argument suggestions
                when (args[1].lowercase()) {
                    "info", "join", "leave", "history" -> {
                        val input = args[2].lowercase()
                        val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                        accessibleChannels.map { it.name }
                            .filter { it.startsWith(input) }
                            .sorted()
                            .forEach { builder.suggest(it) }
                    }
                }
            }
        }

        return builder.buildFuture()
    }
}
