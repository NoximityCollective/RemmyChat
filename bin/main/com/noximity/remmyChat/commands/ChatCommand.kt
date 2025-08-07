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
import java.util.stream.Collectors

class ChatCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) {
                sendHelpMessage(sender)
            } else {
                sender.sendMessage("RemmyChat Commands:")
                sender.sendMessage("/remchat reload - Reload the plugin configuration")
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

            "reload" -> handleReloadCommand(sender)
            else -> {
                if (sender is Player) {
                    sendHelpMessage(sender)
                } else {
                    sender.sendMessage("RemmyChat Commands:")
                    sender.sendMessage("/remchat reload - Reload the plugin configuration")
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

        val channelName: String = args[1].lowercase(Locale.getDefault())
        val channel = plugin.configManager.getChannel(channelName)

        if (channel == null) {
            val message = plugin.formatService.formatSystemMessage(
                "error.channel-not-found",
                Placeholder.parsed("channel", channelName)
            )
            if (message != null) {
                player.sendMessage(message)
            } else {
                player.sendMessage("Channel '$channelName' not found!")
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

    private fun handleReloadCommand(sender: CommandSender) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            val message = plugin.formatService.formatSystemMessage("error.no-permission")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return
        }

        plugin.configManager.reloadConfig()
        plugin.messages.reloadMessages()
        val message = plugin.formatService.formatSystemMessage("plugin-reloaded")
        if (message != null) {
            sender.sendMessage(message)
        } else {
            sender.sendMessage("Plugin reloaded successfully!")
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

        if (args.size == 1) {
            completions.add("channel")
            if (sender.hasPermission("remmychat.admin")) {
                completions.add("reload")
            }
        } else if (args.size == 2 && args[0].equals("channel", ignoreCase = true)) {
            val channels = plugin.configManager.channels
            for ((channelName, channel) in channels) {
                val permission = channel.permission
                if (permission == null || permission.isEmpty() || sender.hasPermission(permission as String)) {
                    completions.add(channelName)
                }
            }
        }

        return completions
    }
}
