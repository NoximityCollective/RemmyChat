package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SocialSpyCommand(private val plugin: RemmyChat) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"))
            return true
        }

        if (!sender.hasPermission("remmychat.socialspy")) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"))
            return true
        }

        val chatUser = plugin.getChatService().getChatUser(sender.getUniqueId())
        val newState = !chatUser.isSocialSpy()
        chatUser.setSocialSpy(newState)

        // Save the new state to the database
        plugin.getDatabaseManager().saveUserPreferences(chatUser)

        if (newState) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("socialspy-enabled"))
        } else {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("socialspy-disabled"))
        }

        return true
    }
}
