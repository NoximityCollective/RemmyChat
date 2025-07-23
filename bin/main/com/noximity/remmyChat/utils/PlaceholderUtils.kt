package com.noximity.remmyChat.utils

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object PlaceholderUtils {
    val isPlaceholderApiEnabled: Boolean = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null

    fun applyPlaceholders(player: Player?, text: String): String? {
        if (isPlaceholderApiEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text)
        }
        return text
    }
}