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
        if (args.size == 0) {
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
                    sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"))
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
            player.sendMessage(
                plugin.getFormatService().formatSystemMessage(
                    "current-channel",
                    Placeholder.parsed(
                        "channel",
                        plugin.getChatService().getChatUser(player.getUniqueId()).getCurrentChannel()
                    )
                )
            )
            return
        }

        val channelName: String = args[1].lowercase(Locale.getDefault())
        val channel = plugin.getConfigManager().getChannel(channelName)

        if (channel == null) {
            player.sendMessage(
                plugin.getFormatService().formatSystemMessage(
                    "error.channel-not-found",
                    Placeholder.parsed("channel", channelName)
                )
            )
            return
        }

        // Check permission
        if (channel.getPermission() != null && !channel.getPermission()
                .isEmpty() && !player.hasPermission(channel.getPermission())
        ) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"))
            return
        }

        // Set channel
        plugin.getChatService().setChannel(player.getUniqueId(), channelName)
        player.sendMessage(
            plugin.getFormatService().formatSystemMessage(
                "channel-changed",
                Placeholder.parsed("channel", channelName)
            )
        )
    }

    private fun handleReloadCommand(sender: CommandSender) {
        if (sender is Player && !sender.hasPermission("remmychat.admin")) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"))
            return
        }

        plugin.getConfigManager().reloadConfig()
        plugin.getMessages().reloadMessages()
        sender.sendMessage(plugin.getFormatService().formatSystemMessage("plugin-reloaded"))
    }

    private fun sendHelpMessage(player: Player) {
        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-header"))
        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-channel"))

        if (player.hasPermission("remmychat.admin")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("help-reload"))
        }

        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-footer"))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): MutableList<String?>? {
        val completions: MutableList<String?> = ArrayList<String?>()

        if (args.size == 1) {
            completions.add("channel")
            if (sender.hasPermission("remmychat.admin")) {
                completions.add("reload")
            }
        } else if (args.size == 2 && args[0].equals("channel", ignoreCase = true)) {
            val channels = plugin.getConfigManager().getChannels()
            completions.addAll(
                channels.keys.stream()
                    .filter { channel: String? ->
                        val ch: Channel = channels.get(channel)!!
                        ch.getPermission() == null || ch.getPermission().isEmpty()
                                || sender.hasPermission(ch.getPermission())
                    }
                    .collect(Collectors.toList()))
        }

        return completions
    }
}
