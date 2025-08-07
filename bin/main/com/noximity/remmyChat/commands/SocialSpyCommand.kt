package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SocialSpyCommand(private val plugin: RemmyChat) : CommandExecutor {
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

        if (!sender.hasPermission("remmychat.socialspy")) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.no-permission")
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("You don't have permission to use this command!")
            }
            return true
        }

        val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
        if (chatUser == null) {
            sender.sendMessage("Error: Could not load user data!")
            return true
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
                sender.sendMessage("Social spy enabled!")
            }
        } else {
            val disabledMsg = plugin.formatService.formatSystemMessage("socialspy-disabled")
            if (disabledMsg != null) {
                sender.sendMessage(disabledMsg)
            } else {
                sender.sendMessage("Social spy disabled!")
            }
        }

        return true
    }
}
