package com.noximity.remmyChat;

import com.noximity.remmyChat.models.ChatUser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemmyChatPlaceholders extends PlaceholderExpansion {

    private final RemmyChat plugin;

    public RemmyChatPlaceholders(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "remmychat";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the expansion on reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.equalsIgnoreCase("msgtoggle")) {
            ChatUser user = plugin.getChatService().getChatUser(player.getUniqueId());
            return String.valueOf(user.isMsgToggle());
        }

        if (params.equalsIgnoreCase("socialspy")) {
            ChatUser user = plugin.getChatService().getChatUser(player.getUniqueId());
            return String.valueOf(user.isSocialSpy());
        }

        if (params.equalsIgnoreCase("channel")) {
            if (player.isOnline()) {
                ChatUser user = plugin.getChatService().getChatUser(player.getUniqueId());
                return user.getCurrentChannel();
            }
            return plugin.getConfigManager().getDefaultChannel().getName();
        }

        if (params.equalsIgnoreCase("group") && plugin.getPermissionService().isLuckPermsHooked()) {
            if (player.isOnline()) {
                return plugin.getPermissionService().getPrimaryGroup(player.getPlayer());
            }
            return "";
        }

        return null;
    }
}
