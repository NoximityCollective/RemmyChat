package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MsgToggleCommand(private val plugin: RemmyChat) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"))
            return true
        }

        val chatUser = plugin.getChatService().getChatUser(sender.getUniqueId())
        val newState = !chatUser.isMsgToggle()
        chatUser.setMsgToggle(newState)

        // Save the new state to the database
        plugin.getDatabaseManager().saveUserPreferences(chatUser)

        if (newState) {
            val message = plugin.getFormatService().formatSystemMessage("msgtoggle-enabled")
            if (message != null) {
                sender.sendMessage(message)
            }
        } else {
            val message = plugin.getFormatService().formatSystemMessage("msgtoggle-disabled")
            if (message != null) {
                sender.sendMessage(message)
            }
        }

        return true
    }
}
