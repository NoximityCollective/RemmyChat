package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class MessageCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.players-only")
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("This command is for players only!")
            }
            return true
        }

        if (args.size < 2) {
            val usageMsg = plugin.formatService.formatSystemMessage("error.msg-usage")
            if (usageMsg != null) {
                sender.sendMessage(usageMsg)
            } else {
                sender.sendMessage("Usage: /msg <player> <message>")
            }
            return true
        }

        // First try to get an exact match to prevent partial matches
        var target: Player? = null
        val targetName = args[0]

        // Check for exact match first
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.name.equals(targetName, ignoreCase = true)) {
                target = onlinePlayer
                break
            }
        }

        // If no exact match was found, show error
        if (target == null || !target.isOnline) {
            val errorMsg = plugin.formatService.formatSystemMessage(
                "error.player-not-found",
                Placeholder.parsed("player", args[0])
            )
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("Player ${args[0]} not found!")
            }
            return true
        }

        if (target == sender) {
            // Only block self-messaging if not allowed in config
            if (!plugin.configManager.isAllowSelfMessaging) {
                val errorMsg = plugin.formatService.formatSystemMessage("error.cannot-message-self")
                if (errorMsg != null) {
                    sender.sendMessage(errorMsg)
                } else {
                    sender.sendMessage("You cannot message yourself!")
                }
                return true
            }
        }

        val targetUser = plugin.chatService.getChatUser(target.uniqueId)
        if (targetUser != null && !targetUser.isMsgToggle && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
            val errorMsg = plugin.formatService.formatSystemMessage(
                "error.player-messages-disabled",
                Placeholder.parsed("player", target.name)
            )
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("${target.name} has messages disabled!")
            }
            return true
        }

        val messageBuilder = StringBuilder()
        for (i in 1..<args.size) {
            messageBuilder.append(args[i]).append(" ")
        }
        val message: String = messageBuilder.toString().trim()

        val toMsg = plugin.formatService.formatSystemMessage(
            "msg-to-format",
            Placeholder.parsed("player", target.name),
            Placeholder.parsed("message", message)
        )
        if (toMsg != null) {
            sender.sendMessage(toMsg)
        }

        val fromMsg = plugin.formatService.formatSystemMessage(
            "msg-from-format",
            Placeholder.parsed("player", sender.name),
            Placeholder.parsed("message", message)
        )
        if (fromMsg != null) {
            target.sendMessage(fromMsg)
        }

        val spyMessage = plugin.formatService.formatSystemMessage(
            "socialspy-format",
            Placeholder.parsed("sender", sender.name),
            Placeholder.parsed("receiver", target.name),
            Placeholder.parsed("message", message)
        )

        for (spyUser in plugin.chatService.getSocialSpyUsers()) {
            val spy = plugin.server.getPlayer(spyUser.uuid)
            if (spy != null && spy.isOnline && (spy != sender) && (spy != target)) {
                if (spyMessage != null) {
                    spy.sendMessage(spyMessage)
                }
            }
        }

        targetUser?.lastMessagedPlayer = sender.uniqueId

        val senderUser = plugin.chatService.getChatUser(sender.uniqueId)
        senderUser?.lastMessagedPlayer = target.uniqueId

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): MutableList<String> {
        val completions = mutableListOf<String>()

        if (args.size == 1) {
            for (player in Bukkit.getOnlinePlayers()) {
                if (args[0].isEmpty() || player.name.lowercase().startsWith(args[0].lowercase())) {
                    completions.add(player.name)
                }
            }
        }

        return completions
    }
}
