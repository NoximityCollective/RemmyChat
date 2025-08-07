package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class BrigadierReplyCommand(private val plugin: RemmyChat) : Command<CommandSourceStack> {

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Only players can use this command
        if (sender !is Player) {
            MessageUtil.sendMessage(stack.sender, "error", "player_only")
            return 0
        }

        // Get message argument
        val message: String
        try {
            message = context.getArgument("message", String::class.java)
        } catch (e: Exception) {
            MessageUtil.sendMessage(sender, "error", "reply_usage")
            return 0
        }

        val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
        if (chatUser == null) {
            sender.sendMessage("Error: Could not load user data!")
            return 0
        }

        val lastMessagedUUID = chatUser.lastMessagedPlayer
        if (lastMessagedUUID == null) {
            MessageUtil.sendMessage(sender, "error", "nobody_to_reply")
            return 0
        }

        val target = Bukkit.getPlayer(lastMessagedUUID)
        if (target == null || !target.isOnline) {
            MessageUtil.sendMessage(sender, "error", "player_not_online")
            return 0
        }

        val targetUser = plugin.chatService.getChatUser(target.uniqueId)
        if (targetUser != null && !targetUser.isMsgToggle && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
            MessageUtil.sendMessage(sender, "error", "player_messages_disabled", "player" to target.name)
            return 0
        }

        val toMsg = plugin.formatService.formatSystemMessage(
            "msg-to-format",
            Placeholder.parsed("player", target.name),
            Placeholder.parsed("message", message)
        )
        if (toMsg != null) {
            sender.sendMessage(toMsg)
        } else {
            MessageUtil.sendMessage(sender, "", "msg-to-format", "player" to target.name, "message" to message)
        }

        val fromMsg = plugin.formatService.formatSystemMessage(
            "msg-from-format",
            Placeholder.parsed("player", sender.name),
            Placeholder.parsed("message", message)
        )
        if (fromMsg != null) {
            target.sendMessage(fromMsg)
        } else {
            MessageUtil.sendMessage(target, "", "msg-from-format", "player" to sender.name, "message" to message)
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
                } else {
                    MessageUtil.sendMessage(spy, "", "socialspy-format",
                        "sender" to sender.name, "receiver" to target.name, "message" to message)
                }
            }
        }

        targetUser?.lastMessagedPlayer = sender.uniqueId
        chatUser.lastMessagedPlayer = target.uniqueId

        return 1
    }
}
