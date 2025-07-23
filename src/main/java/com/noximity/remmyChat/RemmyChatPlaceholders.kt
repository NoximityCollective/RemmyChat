package com.noximity.remmyChat

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

class RemmyChatPlaceholders(private val plugin: RemmyChat) : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "remmychat"
    }

    override fun getAuthor(): String {
        return plugin.description.authors.joinToString(", ")
    }

    override fun getVersion(): String {
        return plugin.description.version
    }

    override fun persist(): Boolean {
        return true // This is required or else PlaceholderAPI will unregister the expansion on reload
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) {
            return ""
        }

        when (params.lowercase()) {
            "msgtoggle" -> {
                val user = plugin.chatService.getChatUser(player.uniqueId) ?: return ""
                return user.isMsgToggle.toString()
            }

            "socialspy" -> {
                val user = plugin.chatService.getChatUser(player.uniqueId) ?: return ""
                return user.isSocialSpy.toString()
            }

            "channel" -> {
                if (player.isOnline) {
                    val user = plugin.chatService.getChatUser(player.uniqueId) ?: return ""
                    return user.currentChannel
                }
                return plugin.configManager.defaultChannel?.name ?: ""
            }

            "group" -> {
                if (plugin.permissionService.isLuckPermsHooked && player.isOnline) {
                    val onlinePlayer = player.player
                    if (onlinePlayer != null) {
                        return plugin.permissionService.getPrimaryGroup(onlinePlayer) ?: ""
                    }
                }
                return ""
            }
        }

        return null
    }
}
