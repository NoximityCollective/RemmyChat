package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ReplyCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {
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

        if (args.isEmpty()) {
            val usageMsg = plugin.formatService.formatSystemMessage("error.reply-usage")
            if (usageMsg != null) {
                sender.sendMessage(usageMsg)
            } else {
                sender.sendMessage("Usage: /reply <message>")
            }
            return true
        }

        val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
        if (chatUser == null) {
            sender.sendMessage("Error: Could not load user data!")
            return true
        }

        val lastMessagedUUID = chatUser.lastMessagedPlayer
        if (lastMessagedUUID == null) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.nobody-to-reply")
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("Nobody to reply to!")
            }
            return true
        }

        val target = Bukkit.getPlayer(lastMessagedUUID)
        if (target == null || !target.isOnline) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.player-not-online")
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("Player is not online!")
            }
            return true
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
        for (arg in args) {
            messageBuilder.append(arg).append(" ")
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
        chatUser.lastMessagedPlayer = target.uniqueId

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): MutableList<String> {
        // Reply command doesn't need tab completion for arguments
        // since it just takes the message text
        return mutableListOf()
    }
}
