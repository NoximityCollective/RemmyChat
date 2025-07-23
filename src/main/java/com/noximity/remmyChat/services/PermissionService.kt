package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.GroupFormat
import org.bukkit.entity.Player
import java.util.*

class PermissionService(private val plugin: RemmyChat) {
    private var luckPermsApi: Any? = null
    var isLuckPermsHooked: Boolean = false
        private set

    init {
        hookLuckPerms()
    }

    private fun hookLuckPerms() {
        try {
            if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                // Use reflection to access LuckPerms API to prevent class loading issues when LP is not present
                val lpProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider")
                luckPermsApi = lpProviderClass.getMethod("get").invoke(null)
                this.isLuckPermsHooked = true
                plugin.logger.info("LuckPerms found and hooked successfully!")
            } else {
                plugin.logger.info("LuckPerms not found, group-based formatting will be disabled.")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to hook into LuckPerms: ${e.message}")
            this.isLuckPermsHooked = false
        }
    }

    /**
     * Gets the primary group name for a player
     * @param player The player to check
     * @return The primary group name or null if LuckPerms is not hooked
     */
    fun getPrimaryGroup(player: Player): String? {
        if (!this.isLuckPermsHooked) return null

        try {
            // Get the User object using reflection
            val userManager: Any = luckPermsApi!!.javaClass.getMethod("getUserManager").invoke(luckPermsApi)
            val user: Any? = userManager.javaClass.getMethod("getUser", UUID::class.java)
                .invoke(userManager, player.uniqueId)

            if (user == null) return null

            // Get the primary group from the User object
            return user.javaClass.getMethod("getPrimaryGroup").invoke(user) as String?
        } catch (e: Exception) {
            plugin.logger.warning("Error getting primary group for ${player.name}: ${e.message}")
            return null
        }
    }

    /**
     * Finds the highest priority group format that the player has permission for
     * @param player The player to check
     * @return The group format or null if no matching format found
     */
    fun getHighestGroupFormat(player: Player): GroupFormat? {
        if (!this.isLuckPermsHooked || !plugin.configManager.isUseGroupFormat) {
            return null
        }

        try {
            // Get primary group using our method that handles reflection
            val primaryGroup = getPrimaryGroup(player)
            if (primaryGroup != null) {
                val primaryGroupFormat = plugin.configManager.getGroupFormat(primaryGroup)
                // If we have a format for the primary group, use that
                if (primaryGroupFormat != null) {
                    return primaryGroupFormat
                }
            }

            // Otherwise check all configured groups by permission
            for (groupName in plugin.configManager.groupFormats.keys) {
                if (player.hasPermission("group." + groupName)) {
                    return plugin.configManager.getGroupFormat(groupName)
                }
            }

            // No matching group format found
            return null
        } catch (e: Exception) {
            plugin.logger.warning("Error getting group format for ${player.name}: ${e.message}")
            return null
        }
    }


}
