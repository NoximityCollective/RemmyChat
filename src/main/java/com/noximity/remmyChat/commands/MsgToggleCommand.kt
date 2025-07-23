package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MsgToggleCommand(private val plugin: RemmyChat) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            val message = plugin.formatService.formatSystemMessage("error.players-only")
            if (message != null) {
                sender.sendMessage(message)
            } else {
                sender.sendMessage("This command is for players only!")
            }
            return true
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
                    sender.sendMessage("Private messages enabled!")
                }
            } else {
                val message = plugin.formatService.formatSystemMessage("msgtoggle-disabled")
                if (message != null) {
                    sender.sendMessage(message)
                } else {
                    sender.sendMessage("Private messages disabled!")
                }
            }
        } else {
            sender.sendMessage("Error: Could not load user data!")
        }

        return true
    }
}
