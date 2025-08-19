package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Channel command for switching between chat channels
 * Handles /ch, /channel, /c commands and quick switching
 */
class ChannelCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!plugin.channelManager.isSwitchingEnabled()) {
            sender.sendMessage("§cChannel switching is disabled!")
            return true
        }

        when (args.size) {
            0 -> {
                // Show current channel and available channels
                showChannelInfo(sender)
                return true
            }
            1 -> {
                val input = args[0].lowercase()

                when (input) {
                    "list", "ls" -> {
                        showChannelList(sender)
                        return true
                    }
                    "info" -> {
                        showCurrentChannelInfo(sender)
                        return true
                    }
                    "history", "hist" -> {
                        showChannelHistory(sender)
                        return true
                    }
                    "help", "?" -> {
                        showHelp(sender)
                        return true
                    }
                    else -> {
                        // Try to switch to channel
                        switchToChannel(sender, input)
                        return true
                    }
                }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "info" -> {
                        showChannelInfo(sender, args[1])
                        return true
                    }
                    "join" -> {
                        joinChannel(sender, args[1])
                        return true
                    }
                    "leave" -> {
                        leaveChannel(sender, args[1])
                        return true
                    }
                    "history", "hist" -> {
                        showChannelHistory(sender, args[1])
                        return true
                    }
                    else -> {
                        sender.sendMessage("§cUnknown subcommand. Use /ch help for usage.")
                        return true
                    }
                }
            }
            else -> {
                sender.sendMessage("§cToo many arguments. Use /ch help for usage.")
                return true
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
            player.sendMessage("§cChannel '$targetChannel' not found!")
            if (plugin.channelManager.shouldShowListOnSwitch()) {
                showChannelList(player)
            }
            return
        }

        if (!channel.enabled) {
            player.sendMessage("§cChannel '${channel.getEffectiveDisplayName()}' is currently disabled!")
            return
        }

        if (!channel.hasPermission(player)) {
            player.sendMessage("§cYou don't have permission to use channel '${channel.getEffectiveDisplayName()}'!")
            return
        }

        val currentChannel = plugin.channelManager.getPlayerChannel(player)
        if (currentChannel?.name == channel.name) {
            player.sendMessage("§eYou are already in channel '${channel.getEffectiveDisplayName()}'!")
            return
        }

        if (plugin.channelManager.setPlayerChannel(player, channel.name)) {
            val displayName = if (channel.getEffectiveDisplayName().isNotEmpty()) {
                channel.getEffectiveDisplayName()
            } else {
                channel.name
            }

            player.sendMessage("§aYou are now chatting in §f${displayName}§a!")

            if (channel.description.isNotEmpty()) {
                player.sendMessage("§7${channel.description}")
            }

            // Show channel info
            showChannelQuickInfo(player, channel)
        } else {
            player.sendMessage("§cFailed to switch to channel '${channel.getEffectiveDisplayName()}'!")
        }
    }

    /**
     * Join a channel (without leaving current)
     */
    private fun joinChannel(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage("§cChannel '$channelName' not found!")
            return
        }

        if (!channel.enabled) {
            player.sendMessage("§cChannel '${channel.getEffectiveDisplayName()}' is currently disabled!")
            return
        }

        if (!channel.hasPermission(player)) {
            player.sendMessage("§cYou don't have permission to join channel '${channel.getEffectiveDisplayName()}'!")
            return
        }

        if (plugin.channelManager.joinChannel(player, channel.name)) {
            player.sendMessage("§aJoined channel '${channel.getEffectiveDisplayName()}'!")
        } else {
            player.sendMessage("§cFailed to join channel '${channel.getEffectiveDisplayName()}'!")
        }
    }

    /**
     * Leave a channel
     */
    private fun leaveChannel(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage("§cChannel '$channelName' not found!")
            return
        }

        if (plugin.channelManager.leaveChannel(player, channel.name)) {
            player.sendMessage("§aLeft channel '${channel.getEffectiveDisplayName()}'!")
        } else {
            player.sendMessage("§cFailed to leave channel '${channel.getEffectiveDisplayName()}'!")
        }
    }

    /**
     * Show current channel info and available channels
     */
    private fun showChannelInfo(player: Player) {
        val currentChannel = plugin.channelManager.getPlayerChannel(player)

        if (currentChannel != null) {
            player.sendMessage("§6=== Current Channel ===")
            player.sendMessage("§eChannel: §f${currentChannel.getEffectiveDisplayName()}")
            if (currentChannel.description.isNotEmpty()) {
                player.sendMessage("§eDescription: §f${currentChannel.description}")
            }
            player.sendMessage("§eType: §f${currentChannel.getChannelType()}")
            player.sendMessage("§eMembers: §f${plugin.channelManager.getChannelMembers(currentChannel.name).size}")
        } else {
            player.sendMessage("§cYou are not in any channel!")
        }

        if (plugin.channelManager.shouldShowListOnSwitch()) {
            player.sendMessage("")
            showChannelList(player)
        }
    }

    /**
     * Show information about a specific channel
     */
    private fun showChannelInfo(player: Player, channelName: String) {
        val channel = plugin.channelManager.getChannel(channelName)
        if (channel == null) {
            player.sendMessage("§cChannel '$channelName' not found!")
            return
        }

        player.sendMessage("§6=== Channel Information ===")
        player.sendMessage("§eName: §f${channel.name}")
        player.sendMessage("§eDisplay Name: §f${channel.getEffectiveDisplayName()}")
        player.sendMessage("§eDescription: §f${channel.description}")
        player.sendMessage("§eType: §f${channel.getChannelType()}")
        player.sendMessage("§eEnabled: §f${if (channel.enabled) "§aYes" else "§cNo"}")
        player.sendMessage("§ePermission: §f${if (channel.permission.isEmpty()) "§aNone" else channel.permission}")
        player.sendMessage("§eMembers: §f${plugin.channelManager.getChannelMembers(channel.name).size}")
        player.sendMessage("§eRange: §f${if (channel.radius < 0) "Global" else "${channel.radius} blocks"}")
        player.sendMessage("§eCross-Server: §f${if (channel.isCrossServer()) "§aYes" else "§cNo"}")

        if (channel.hasPermission(player)) {
            player.sendMessage("§aYou can use this channel!")
        } else {
            player.sendMessage("§cYou cannot use this channel!")
        }
    }

    /**
     * Show current channel detailed info
     */
    private fun showCurrentChannelInfo(player: Player) {
        val currentChannel = plugin.channelManager.getPlayerChannel(player)
        if (currentChannel == null) {
            player.sendMessage("§cYou are not in any channel!")
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
            player.sendMessage("§cNo channels available!")
            return
        }

        player.sendMessage("§6=== Available Channels ===")

        for (channel in accessibleChannels.sortedBy { it.name }) {
            val displayName = channel.getEffectiveDisplayName()
            val memberCount = plugin.channelManager.getChannelMembers(channel.name).size
            val current = if (currentChannel?.name == channel.name) " §a(current)" else ""
            val crossServer = if (channel.isCrossServer()) " §b[Cross-Server]" else ""

            player.sendMessage("§e${channel.name}§f: ${displayName} §7(${memberCount} members)${crossServer}${current}")

            if (channel.description.isNotEmpty()) {
                player.sendMessage("  §7${channel.description}")
            }
        }

        // Show quick switch info
        val quickSwitchMap = plugin.channelManager.getQuickSwitchChannel("1")
        if (quickSwitchMap != null) {
            player.sendMessage("")
            player.sendMessage("§7Quick switch: Use §e/ch 1-5§7 for quick channel switching")
        }
    }

    /**
     * Show quick channel info
     */
    private fun showChannelQuickInfo(player: Player, channel: Channel) {
        val memberCount = plugin.channelManager.getChannelMembers(channel.name).size
        val type = channel.getChannelType()
        player.sendMessage("§7Type: $type §8| §7Members: $memberCount")
    }

    /**
     * Show channel history (placeholder)
     */
    private fun showChannelHistory(player: Player, channelName: String? = null) {
        val targetChannel = channelName ?: plugin.channelManager.getPlayerChannel(player)?.name

        if (targetChannel == null) {
            player.sendMessage("§cNo channel specified and you're not in any channel!")
            return
        }

        // TODO: Implement history system
        player.sendMessage("§eChannel history for '$targetChannel' is not yet implemented!")
    }

    /**
     * Show command help
     */
    private fun showHelp(player: Player) {
        player.sendMessage("§6=== Channel Commands ===")
        player.sendMessage("§e/ch §f- Show current channel and list")
        player.sendMessage("§e/ch <channel> §f- Switch to channel")
        player.sendMessage("§e/ch list §f- Show all available channels")
        player.sendMessage("§e/ch info [channel] §f- Show channel information")
        player.sendMessage("§e/ch join <channel> §f- Join a channel")
        player.sendMessage("§e/ch leave <channel> §f- Leave a channel")
        player.sendMessage("§e/ch history [channel] §f- Show channel history")
        player.sendMessage("§e/ch help §f- Show this help")

        // Show quick switch info
        val quickSwitchMap = plugin.channelManager.getQuickSwitchChannel("1")
        if (quickSwitchMap != null) {
            player.sendMessage("")
            player.sendMessage("§7Quick switch: §e/ch 1§7, §e/ch 2§7, etc.")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()

        val input = if (args.isNotEmpty()) args.last().lowercase() else ""

        when (args.size) {
            1 -> {
                val suggestions = mutableListOf<String>()

                // Add subcommands
                val subcommands = listOf("list", "info", "join", "leave", "history", "help", "current", "members", "create", "delete", "settings")
                suggestions.addAll(subcommands.filter { it.startsWith(input) })

                // Add channel names with priority for current channel
                val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                val currentChannel = if (sender is Player) {
                    plugin.channelManager.getPlayerChannel(sender)
                } else {
                    null
                }

                // Add current channel first if it matches
                currentChannel?.let { current ->
                    if (current.name.lowercase().startsWith(input)) {
                        suggestions.add(current.name)
                    }
                }

                // Add other channels
                accessibleChannels.forEach { channel ->
                    if (channel.name.lowercase().startsWith(input) && channel.name != currentChannel?.name) {
                        suggestions.add(channel.name)
                    }
                }

                // Add quick switch numbers
                for (i in 1..9) {
                    val quickChannel = plugin.channelManager.getQuickSwitchChannel(i.toString())
                    if (quickChannel != null && i.toString().startsWith(input)) {
                        suggestions.add(i.toString())
                    }
                }

                // Add aliases for common channels
                val aliases = mapOf(
                    "g" to "global",
                    "l" to "local",
                    "s" to "staff",
                    "t" to "trade",
                    "h" to "help",
                    "a" to "admin",
                    "e" to "event"
                )

                aliases.forEach { (alias, channel) ->
                    if (alias.startsWith(input) && accessibleChannels.any { it.name == channel }) {
                        suggestions.add(alias)
                    }
                }

                return suggestions.distinct().sorted()
            }

            2 -> {
                when (args[0].lowercase()) {
                    "info", "join", "leave", "history", "settings" -> {
                        val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                        return accessibleChannels.map { it.name }.filter { it.startsWith(input) }.sorted()
                    }

                    "members" -> {
                        val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                        val suggestions = accessibleChannels.map { it.name }.filter { it.startsWith(input) }.toMutableList()

                        // Add "all" option to show all channel members
                        if ("all".startsWith(input)) {
                            suggestions.add(0, "all")
                        }

                        return suggestions
                    }

                    "create" -> {
                        if (sender.hasPermission("remmychat.admin") || sender.hasPermission("remmychat.channel.create")) {
                            return listOf("<channel_name>").filter { it.startsWith(input) }
                        }
                    }

                    "delete" -> {
                        if (sender.hasPermission("remmychat.admin") || sender.hasPermission("remmychat.channel.delete")) {
                            val accessibleChannels = plugin.channelManager.getAccessibleChannels(sender)
                            return accessibleChannels.map { it.name }.filter { it.startsWith(input) }.sorted()
                        }
                    }
                }
            }

            3 -> {
                when (args[0].lowercase()) {
                    "create" -> {
                        if (sender.hasPermission("remmychat.admin") || sender.hasPermission("remmychat.channel.create")) {
                            when (args[1].lowercase()) {
                                else -> {
                                    val createOptions = listOf("global", "local", "private", "staff", "public")
                                    return createOptions.filter { it.startsWith(input) }
                                }
                            }
                        }
                    }

                    "settings" -> {
                        if (sender.hasPermission("remmychat.admin") || sender.hasPermission("remmychat.channel.settings")) {
                            val settingOptions = listOf("permission", "radius", "format", "enabled", "cross-server")
                            return settingOptions.filter { it.startsWith(input) }
                        }
                    }
                }
            }
        }

        return emptyList()
    }
}
