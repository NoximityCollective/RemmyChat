package com.noximity.remmyChat.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaceholderUtils {

    private static final boolean placeholderApiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private PlaceholderUtils() {
    }

    public static String applyPlaceholders(Player player, String text) {
        if (placeholderApiEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }
}