package com.noximity.remmyChat.services;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.GroupFormat;
import org.bukkit.entity.Player;

public class PermissionService {

    private final RemmyChat plugin;
    private Object luckPermsApi;
    private boolean luckPermsHooked = false;

    public PermissionService(RemmyChat plugin) {
        this.plugin = plugin;
        hookLuckPerms();
    }

    private void hookLuckPerms() {
        try {
            if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                // Use reflection to access LuckPerms API to prevent class loading issues when LP is not present
                Class<?> lpProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPermsApi = lpProviderClass.getMethod("get").invoke(null);
                luckPermsHooked = true;
                plugin.getLogger().info("LuckPerms found and hooked successfully!");
            } else {
                plugin.getLogger().info("LuckPerms not found, group-based formatting will be disabled.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into LuckPerms: " + e.getMessage());
            luckPermsHooked = false;
        }
    }

    public boolean isLuckPermsHooked() {
        return luckPermsHooked;
    }

    /**
     * Gets the primary group name for a player
     * @param player The player to check
     * @return The primary group name or null if LuckPerms is not hooked
     */
    public String getPrimaryGroup(Player player) {
        if (!luckPermsHooked) return null;

        try {
            // Get the User object using reflection
            Object userManager = luckPermsApi.getClass().getMethod("getUserManager").invoke(luckPermsApi);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                    .invoke(userManager, player.getUniqueId());

            if (user == null) return null;

            // Get the primary group from the User object
            return (String) user.getClass().getMethod("getPrimaryGroup").invoke(user);
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting primary group for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Finds the highest priority group format that the player has permission for
     * @param player The player to check
     * @return The group format or null if no matching format found
     */
    public GroupFormat getHighestGroupFormat(Player player) {
        if (!luckPermsHooked || !plugin.getConfigManager().isUseGroupFormat()) {
            return null;
        }

        try {
            // Get primary group using our method that handles reflection
            String primaryGroup = getPrimaryGroup(player);
            if (primaryGroup != null) {
                GroupFormat primaryGroupFormat = plugin.getConfigManager().getGroupFormat(primaryGroup);
                // If we have a format for the primary group, use that
                if (primaryGroupFormat != null) {
                    return primaryGroupFormat;
                }
            }

            // Otherwise check all configured groups by permission
            for (String groupName : plugin.getConfigManager().getGroupFormats().keySet()) {
                if (player.hasPermission("group." + groupName)) {
                    return plugin.getConfigManager().getGroupFormat(groupName);
                }
            }

            // No matching group format found
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting group format for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
