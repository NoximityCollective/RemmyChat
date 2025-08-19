package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class BrigadierMessageCommand(private val plugin: RemmyChat) : Command<CommandSourceStack>, SuggestionProvider<CommandSourceStack> {

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Only players can use this command
        if (sender !is Player) {
            MessageUtil.sendMessage(stack.sender, "error", "player_only")
            return 0
        }

        // Get player argument
        val targetPlayer: Player
        try {
            targetPlayer = context.getArgument("player", Player::class.java)
        } catch (e: Exception) {
            MessageUtil.sendMessage(sender, "error", "msg_usage")
            return 0
        }

        // Get message argument
        val message: String
        try {
            message = context.getArgument("message", String::class.java)
        } catch (e: Exception) {
            MessageUtil.sendMessage(sender, "error", "msg_usage")
            return 0
        }

        val target = targetPlayer
        val targetName = target.name

        // Check if player exists and is online
        if (!target.isOnline) {
            MessageUtil.sendMessage(sender, "error", "player_not_online")
            return 0
        }

        if (target.uniqueId == sender.uniqueId) {
            MessageUtil.sendMessage(sender, "error", "self_message")
            return 0
        }

        // Check if target has messages disabled
        val targetUser = plugin.chatService.getChatUser(target.uniqueId)
        if (targetUser != null && !targetUser.isMsgToggle && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
            MessageUtil.sendMessage(sender, "error", "player_messages_disabled", "player" to target.name)
            return 0
        }

        val toMsg = plugin.formatService.formatSystemMessage(
            "msg-to-format",
            Placeholder.parsed("player", targetName),
            Placeholder.parsed("message", message)
        )
        if (toMsg != null) {
            sender.sendMessage(toMsg)
        } else {
            MessageUtil.sendMessage(sender, "", "msg-to-format", "player" to targetName, "message" to message)
        }

        // Send to target player
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

        // Update last messaged player for recipient
        targetUser?.lastMessagedPlayer = sender.uniqueId

        // Send to social spy users on local server
        val spyMessage = plugin.formatService.formatSystemMessage(
            "socialspy-format",
            Placeholder.parsed("sender", sender.name),
            Placeholder.parsed("receiver", targetName),
            Placeholder.parsed("message", message)
        )

        for (spyUser in plugin.chatService.getSocialSpyUsers()) {
            val spy = plugin.server.getPlayer(spyUser.uuid)
            if (spy != null && spy.isOnline && spy.uniqueId != sender.uniqueId && spy.uniqueId != target.uniqueId) {
                if (spyMessage != null) {
                    spy.sendMessage(spyMessage)
                } else {
                    MessageUtil.sendMessage(spy, "", "socialspy-format",
                        "sender" to sender.name, "receiver" to targetName, "message" to message)
                }
            }
        }

        // Update last messaged player for sender
        val senderUser = plugin.chatService.getChatUser(sender.uniqueId)
        senderUser?.lastMessagedPlayer = target.uniqueId

        return 1
    }

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = context.source.sender

        // For player argument suggestions
        if (context.input.split(" ").size <= 2) {
            Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                .forEach { builder.suggest(it) }
        }

        return builder.buildFuture()
    }
}
