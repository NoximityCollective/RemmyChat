package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class BrigadierSocialSpyCommand(private val plugin: RemmyChat) : Command<CommandSourceStack> {

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Only players can use this command
        if (sender !is Player) {
            MessageUtil.sendMessage(stack.sender, "error", "player_only")
            return 0
        }

        if (!sender.hasPermission("remmychat.socialspy")) {
            MessageUtil.sendMessage(sender, "error", "no_permission")
            return 0
        }

        val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
        if (chatUser == null) {
            sender.sendMessage("Error: Could not load user data!")
            return 0
        }

        val newState = !chatUser.isSocialSpy
        chatUser.isSocialSpy = newState

        // Save the new state to the database
        plugin.databaseManager.saveUserPreferences(chatUser)

        if (newState) {
            val enabledMsg = plugin.formatService.formatSystemMessage("socialspy-enabled")
            if (enabledMsg != null) {
                sender.sendMessage(enabledMsg)
            } else {
                MessageUtil.sendMessage(sender, "", "socialspy-enabled")
            }
        } else {
            val disabledMsg = plugin.formatService.formatSystemMessage("socialspy-disabled")
            if (disabledMsg != null) {
                sender.sendMessage(disabledMsg)
            } else {
                MessageUtil.sendMessage(sender, "", "socialspy-disabled")
            }
        }

        return 1
    }
}
