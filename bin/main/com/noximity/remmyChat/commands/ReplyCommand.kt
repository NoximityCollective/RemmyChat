package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ReplyCommand(private val plugin: RemmyChat) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"))
            return true
        }

        if (args.size < 1) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.reply-usage"))
            return true
        }

        val chatUser = plugin.getChatService().getChatUser(sender.getUniqueId())
        val lastMessagedUUID = chatUser.getLastMessagedPlayer()

        if (lastMessagedUUID == null) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.nobody-to-reply"))
            return true
        }

        val target = Bukkit.getPlayer(lastMessagedUUID)
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.player-not-online"))
            return true
        }

        val targetUser = plugin.getChatService().getChatUser(target.getUniqueId())
        if (!targetUser.isMsgToggle() && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
            sender.sendMessage(
                plugin.getFormatService().formatSystemMessage(
                    "error.player-messages-disabled",
                    Placeholder.parsed("player", target.getName())
                )
            )
            return true
        }

        val messageBuilder = StringBuilder()
        for (arg in args) {
            messageBuilder.append(arg).append(" ")
        }
        val message: String = messageBuilder.toString().trim { it <= ' ' }
        sender.sendMessage(
            plugin.getFormatService().formatSystemMessage(
                "msg-to-format",
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("message", message)
            )
        )

        target.sendMessage(
            plugin.getFormatService().formatSystemMessage(
                "msg-from-format",
                Placeholder.parsed("player", sender.getName()),
                Placeholder.parsed("message", message)
            )
        )

        val spyMessage = plugin.getFormatService().formatSystemMessage(
            "socialspy-format",
            Placeholder.parsed("sender", sender.getName()),
            Placeholder.parsed("receiver", target.getName()),
            Placeholder.parsed("message", message)
        )

        for (spyUser in plugin.getChatService().getSocialSpyUsers()) {
            val spy = plugin.getServer().getPlayer(spyUser.getUuid())
            if (spy != null && spy.isOnline() && (spy != sender) && (spy != target)) {
                spy.sendMessage(spyMessage)
            }
        }

        targetUser.setLastMessagedPlayer(sender.getUniqueId())

        return true
    }
}