package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.utils.MessageUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class BrigadierMsgToggleCommand(private val plugin: RemmyChat) : Command<CommandSourceStack> {

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Only players can use this command
        if (sender !is Player) {
            MessageUtil.sendMessage(stack.sender, "error", "player_only")
            return 0
        }

        val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
        if (chatUser != null) {
            val newState = !chatUser.isMsgToggle
            chatUser.isMsgToggle = newState

            // Save the new state to the database
            plugin.databaseManager.saveUserPreferences(chatUser)

            if (newState) {
                val message = plugin.formatService.formatSystemMessage("msgtoggle-enabled")
                if (message != null) {
                    sender.sendMessage(message)
                } else {
                    MessageUtil.sendMessage(sender, "", "msgtoggle-enabled")
                }
            } else {
                val message = plugin.formatService.formatSystemMessage("msgtoggle-disabled")
                if (message != null) {
                    sender.sendMessage(message)
                } else {
                    MessageUtil.sendMessage(sender, "", "msgtoggle-disabled")
                }
            }
        } else {
            sender.sendMessage("Error: Could not load user data!")
        }

        return 1
    }
}
