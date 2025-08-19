package com.noximity.remmyChat.utils

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object MessageUtil {

    /**
     * Send a formatted message to a command sender
     * @param sender The command sender
     * @param category The message category (e.g., "error", "flycommand")
     * @param key The message key
     * @param placeholders Optional placeholders as vararg pairs
     */
    fun sendMessage(sender: CommandSender, category: String, key: String, vararg placeholders: Pair<String, String>) {
        val plugin = RemmyChat.instance
        val messageKey = if (category.isNotEmpty()) "$category.$key" else key

        val resolvers = mutableListOf<TagResolver>()
        for ((placeholder, value) in placeholders) {
            resolvers.add(Placeholder.parsed(placeholder, value))
        }

        val message = plugin.formatService.formatSystemMessage(messageKey, *resolvers.toTypedArray())
        if (message != null) {
            sender.sendMessage(message)
        } else {
            // Fallback message if formatting fails
            val fallback = getFallbackMessage(category, key, placeholders.toMap())
            sender.sendMessage(fallback)
        }
    }

    /**
     * Send a formatted message to a player
     * @param player The player
     * @param category The message category
     * @param key The message key
     * @param placeholders Optional placeholders as vararg pairs
     */
    fun sendMessage(player: Player, category: String, key: String, vararg placeholders: Pair<String, String>) {
        sendMessage(player as CommandSender, category, key, *placeholders)
    }

    /**
     * Get a formatted message component without sending it
     * @param category The message category
     * @param key The message key
     * @param placeholders Optional placeholders as vararg pairs
     * @return The formatted component or null if not found
     */
    fun getMessage(category: String, key: String, vararg placeholders: Pair<String, String>): Component? {
        val plugin = RemmyChat.instance
        val messageKey = if (category.isNotEmpty()) "$category.$key" else key

        val resolvers = mutableListOf<TagResolver>()
        for ((placeholder, value) in placeholders) {
            resolvers.add(Placeholder.parsed(placeholder, value))
        }

        return plugin.formatService.formatSystemMessage(messageKey, *resolvers.toTypedArray())
    }

    /**
     * Get fallback messages for common scenarios
     */
    private fun getFallbackMessage(category: String, key: String, placeholders: Map<String, String>): Component {
        val mm = MiniMessage.miniMessage()
        val message = when ("$category.$key") {
            "error.player_only" -> "<red>This command can only be used by players!"
            "error.no_permission" -> "<red>You don't have permission to use this command!"
            "error.insufficient_permission_others" -> "<red>You don't have permission to use this command on other players!"
            "error.player_not_found" -> "<red>Player '<yellow>${placeholders["player"] ?: "unknown"}</yellow>' not found!"
            "error.player_not_online" -> "<red>Player is not online!"
            "error.self_message" -> "<red>You cannot send a message to yourself!"
            "error.player_messages_disabled" -> "<red>${placeholders["player"] ?: "Player"} has messages disabled!"
            "error.nobody_to_reply" -> "<red>Nobody to reply to!"
            "error.msg_usage" -> "<red>Usage: /msg <player> <message>"
            "error.reply_usage" -> "<red>Usage: /reply <message>"
            "error.channel_not_found" -> "<red>Channel '<yellow>${placeholders["channel"] ?: "unknown"}</yellow>' not found!"
            "error.failed_to_send_message" -> "<red>Failed to send message!"

            "flycommand.fly_enabled" -> "<green>Flight mode enabled!"
            "flycommand.fly_disabled" -> "<red>Flight mode disabled!"
            "flycommand.fly_enabled_other" -> "<green>Enabled flight mode for <white>${placeholders["player"] ?: "player"}</white>!"
            "flycommand.fly_disabled_other" -> "<red>Disabled flight mode for <white>${placeholders["player"] ?: "player"}</white>!"
            "flycommand.fly_enabled_by_other" -> "<green>Flight mode enabled by <white>${placeholders["admin"] ?: "admin"}</white>!"
            "flycommand.fly_disabled_by_other" -> "<red>Flight mode disabled by <white>${placeholders["admin"] ?: "admin"}</white>!"

            "msgtoggle-enabled" -> "<green>Private messages enabled!"
            "msgtoggle-disabled" -> "<red>Private messages disabled!"

            "socialspy-enabled" -> "<green>Social spy enabled!"
            "socialspy-disabled" -> "<red>Social spy disabled!"

            "msg-to-format" -> "<gray>[<yellow>To</yellow>] <white>${placeholders["player"] ?: "Player"}</white>: ${placeholders["message"] ?: ""}"
            "msg-from-format" -> "<gray>[<yellow>From</yellow>] <white>${placeholders["player"] ?: "Player"}</white>: ${placeholders["message"] ?: ""}"
            "socialspy-format" -> "<gray>[<gold>Spy</gold>] <white>${placeholders["sender"] ?: "Sender"}</white> <gray>-></gray> <white>${placeholders["receiver"] ?: "Receiver"}</white>: <gray>${placeholders["message"] ?: ""}</gray>"

            "current-channel" -> "<yellow>Current channel: <white>${placeholders["channel"] ?: "unknown"}</white>"
            "channel-changed" -> "<green>Channel changed to: <white>${placeholders["channel"] ?: "unknown"}</white>"
            "plugin-reloaded" -> "<green>Plugin reloaded successfully!"

            "help-header" -> "<gold>=== RemmyChat Commands ==="
            "help-channel" -> "<yellow>/remmychat channel <name></yellow> <white>- Switch to a channel</white>"
            "help-reload" -> "<yellow>/remmychat reload</yellow> <white>- Reload plugin configuration</white>"
            "help-discord" -> "<yellow>/remmychat discord</yellow> <white>- Manage Discord integration</white>"
            "help-footer" -> "<gray>Use /help for more information</gray>"

            else -> "<gray>Message: $category.$key</gray>"
        }
        return mm.deserialize(message)
    }
}
