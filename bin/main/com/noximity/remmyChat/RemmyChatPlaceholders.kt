package com.noximity.remmyChat

import com.noximity.remmyChat.models.ChatUser
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.lang.String
import kotlin.Boolean

class RemmyChatPlaceholders(private val plugin: RemmyChat) : PlaceholderExpansion() {
    override fun getIdentifier(): String {
        return "remmychat"
    }

    override fun getAuthor(): String {
        return String.join(", ", plugin.getDescription().getAuthors())
    }

    override fun getVersion(): kotlin.String {
        return plugin.getDescription().getVersion()
    }

    override fun persist(): Boolean {
        return true // This is required or else PlaceholderAPI will unregister the expansion on reload
    }

    override fun onRequest(player: OfflinePlayer?, params: kotlin.String): kotlin.String? {
        if (player == null) {
            return ""
        }

        if (params.equals("msgtoggle", ignoreCase = true)) {
            val user: ChatUser = plugin.getChatService().getChatUser(player.getUniqueId())
            return user.isMsgToggle().toString()
        }

        if (params.equals("socialspy", ignoreCase = true)) {
            val user: ChatUser = plugin.getChatService().getChatUser(player.getUniqueId())
            return user.isSocialSpy().toString()
        }

        if (params.equals("channel", ignoreCase = true)) {
            if (player.isOnline()) {
                val user: ChatUser = plugin.getChatService().getChatUser(player.getUniqueId())
                return user.getCurrentChannel()
            }
            return plugin.getConfigManager().getDefaultChannel().getName()
        }

        if (params.equals("group", ignoreCase = true) && plugin.getPermissionService().isLuckPermsHooked()) {
            if (player.isOnline()) {
                return plugin.getPermissionService().getPrimaryGroup(player.getPlayer())
            }
            return ""
        }

        return null
    }
}
