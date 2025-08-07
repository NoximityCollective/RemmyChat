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
        val luckPermsPlugin = plugin.server.pluginManager.getPlugin("LuckPerms")
        if (luckPermsPlugin != null) {
            plugin.logger.info("LuckPerms detected - Version: ${luckPermsPlugin.description.version}")
            try {
                // Check if LuckPerms is properly loaded and enabled
                if (!luckPermsPlugin.isEnabled) {
                    plugin.logger.warning("LuckPerms plugin found but not enabled - group formatting disabled")
                    this.isLuckPermsHooked = false
                    return
                }

                // Wait a bit for LuckPerms to fully initialize
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    tryInitializeLuckPerms()
                }, 20L) // Wait 1 second (20 ticks)

                // Try immediate initialization first
                tryInitializeLuckPerms()
            } catch (e: Exception) {
                plugin.logger.warning("Failed to hook into LuckPerms during detection: ${e.javaClass.simpleName} - ${e.message}")
                this.isLuckPermsHooked = false
            }
        } else {
            plugin.logger.info("LuckPerms not found - group-based formatting will be disabled")
            this.isLuckPermsHooked = false
        }
    }

    private fun tryInitializeLuckPerms() {
        if (isLuckPermsHooked) return // Already hooked successfully

        try {
            // Use the plugin's class loader to find LuckPerms classes
            val luckPermsPlugin = plugin.server.pluginManager.getPlugin("LuckPerms") ?: return
            val luckPermsClassLoader = luckPermsPlugin.javaClass.classLoader

            // Try to load LuckPerms API class using its class loader
            val lpProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider", true, luckPermsClassLoader)
            luckPermsApi = lpProviderClass.getMethod("get").invoke(null)

            // Test the API by trying to get the UserManager
            val userManager = luckPermsApi!!.javaClass.getMethod("getUserManager").invoke(luckPermsApi)
            if (userManager != null) {
                this.isLuckPermsHooked = true
                plugin.logger.info("LuckPerms integration enabled successfully!")
            } else {
                throw Exception("UserManager is null")
            }
        } catch (e: ClassNotFoundException) {
            plugin.logger.warning("LuckPerms API classes not found: ${e.message}")
            this.isLuckPermsHooked = false
        } catch (e: NoClassDefFoundError) {
            plugin.logger.warning("LuckPerms API classes could not be loaded: ${e.message}")
            this.isLuckPermsHooked = false
        } catch (e: Exception) {
            plugin.logger.warning("Failed to initialize LuckPerms API: ${e.javaClass.simpleName} - ${e.message}")
            this.isLuckPermsHooked = false
        }
    }

    /**
     * Gets the primary group name for a player
     * @param player The player to check
     * @return The primary group name or null if LuckPerms is not hooked
     */
    fun getPrimaryGroup(player: Player): String? {
        if (!this.isLuckPermsHooked || luckPermsApi == null) {
            // Try to re-initialize if it failed before
            if (!isLuckPermsHooked) {
                tryInitializeLuckPerms()
            }
            if (!this.isLuckPermsHooked || luckPermsApi == null) return null
        }

        try {
            // Get the User object using reflection
            val userManager: Any = luckPermsApi!!.javaClass.getMethod("getUserManager").invoke(luckPermsApi)
            val user: Any? = userManager.javaClass.getMethod("getUser", UUID::class.java)
                .invoke(userManager, player.uniqueId)

            if (user == null) {
                // Try loading the user data if not cached
                val loadUserMethod = userManager.javaClass.getMethod("loadUser", UUID::class.java)
                val completableFuture = loadUserMethod.invoke(userManager, player.uniqueId)

                // For now, return null if user data isn't immediately available
                // In a real implementation, you might want to handle the CompletableFuture
                plugin.debugLog("User data not cached for ${player.name}, attempting to load...")
                return null
            }

            // Get the primary group from the User object
            return user.javaClass.getMethod("getPrimaryGroup").invoke(user) as String?
        } catch (e: Exception) {
            plugin.logger.warning("Error getting primary group for ${player.name}: ${e.message}")
            // Try to re-initialize LuckPerms connection on error
            this.isLuckPermsHooked = false
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
