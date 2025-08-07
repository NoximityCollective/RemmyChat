package com.noximity.remmyChat.integrations

import com.noximity.remmyChat.RemmyChat
import org.bukkit.entity.Player

/**
 * Utility class for DiscordSRV integration and channel detection.
 * This class provides methods that DiscordSRV and other plugins can use
 * to detect and interact with RemmyChat channels.
 */
class DiscordSRVUtil(private val plugin: RemmyChat) {

    companion object {
        private var instance: DiscordSRVUtil? = null

        /**
         * Get the DiscordSRVUtil instance
         */
        @JvmStatic
        fun getInstance(): DiscordSRVUtil? {
            return instance
        }

        /**
         * Initialize the utility instance
         */
        @JvmStatic
        fun initialize(plugin: RemmyChat) {
            instance = DiscordSRVUtil(plugin)
        }

        /**
         * Shutdown the utility instance
         */
        @JvmStatic
        fun shutdown() {
            instance = null
        }
    }

    /**
     * Get the current channel for a player
     * @param player The player to check
     * @return The channel name, or null if player not found
     */
    fun getPlayerChannel(player: Player): String? {
        val chatUser = plugin.chatService.getChatUser(player.uniqueId)
        return chatUser?.currentChannel
    }

    /**
     * Get the current channel for a player by UUID
     * @param uuid The player's UUID as string
     * @return The channel name, or null if player not found
     */
    fun getPlayerChannel(uuid: String): String? {
        try {
            val playerUuid = java.util.UUID.fromString(uuid)
            val chatUser = plugin.chatService.getChatUser(playerUuid)
            return chatUser?.currentChannel
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    /**
     * Get the Discord channel mapping for a RemmyChat channel
     * @param remmyChannel The RemmyChat channel name
     * @return The Discord channel name, or null if no mapping exists
     */
    fun getDiscordChannelForRemmyChannel(remmyChannel: String): String? {
        return if (plugin.isDiscordSRVEnabled) {
            plugin.discordSRVIntegration.getChannelMappings()[remmyChannel]
        } else {
            null
        }
    }

    /**
     * Get the RemmyChat channel mapping for a Discord channel
     * @param discordChannel The Discord channel name
     * @return The RemmyChat channel name, or null if no mapping exists
     */
    fun getRemmyChannelForDiscordChannel(discordChannel: String): String? {
        return if (plugin.isDiscordSRVEnabled) {
            plugin.discordSRVIntegration.getChannelMappings().entries
                .find { it.value == discordChannel }?.key
        } else {
            null
        }
    }

    /**
     * Check if a player has permission to use a specific channel
     * @param player The player to check
     * @param channelName The channel name to check
     * @return True if the player has permission, false otherwise
     */
    fun hasChannelPermission(player: Player, channelName: String): Boolean {
        val channel = plugin.configManager.getChannel(channelName) ?: return false
        val permission = channel.permission

        return if (permission.isNullOrEmpty()) {
            true
        } else {
            player.hasPermission(permission)
        }
    }

    /**
     * Get all available channels for a player
     * @param player The player to check
     * @return A list of channel names the player can access
     */
    fun getAvailableChannels(player: Player): List<String> {
        val availableChannels = mutableListOf<String>()

        for ((channelName, channel) in plugin.configManager.channels) {
            val permission = channel.permission
            if (permission.isNullOrEmpty() || player.hasPermission(permission)) {
                availableChannels.add(channelName)
            }
        }

        return availableChannels
    }

    /**
     * Get all channel mappings
     * @return A map of RemmyChat channels to Discord channels
     */
    fun getAllChannelMappings(): Map<String, String> {
        return if (plugin.isDiscordSRVEnabled) {
            plugin.discordSRVIntegration.getChannelMappings()
        } else {
            emptyMap()
        }
    }

    /**
     * Check if DiscordSRV integration is enabled and active
     * @return True if integration is active, false otherwise
     */
    fun isDiscordIntegrationActive(): Boolean {
        return plugin.isDiscordSRVEnabled && plugin.discordSRVIntegration.isEnabled()
    }

    /**
     * Set a player's channel
     * @param player The player
     * @param channelName The channel to set
     * @return True if successful, false if channel doesn't exist or no permission
     */
    fun setPlayerChannel(player: Player, channelName: String): Boolean {
        if (!hasChannelPermission(player, channelName)) {
            return false
        }

        return plugin.chatService.setChannel(player.uniqueId, channelName)
    }

    /**
     * Get the display name for a channel
     * @param channelName The channel name
     * @return The display name, or the channel name if no display name is set
     */
    fun getChannelDisplayName(channelName: String): String {
        val channel = plugin.configManager.getChannel(channelName)
        return if (channel?.hasDisplayName() == true) {
            channel.displayName ?: channelName
        } else {
            channelName
        }
    }

    /**
     * Check if a channel is a local/radius-based channel
     * @param channelName The channel name
     * @return True if the channel has a radius limit, false if global
     */
    fun isLocalChannel(channelName: String): Boolean {
        val channel = plugin.configManager.getChannel(channelName) ?: return false
        return channel.radius > 0
    }

    /**
     * Get the radius for a channel (for local channels)
     * @param channelName The channel name to check
     * @return The radius in blocks, or -1 if global channel or channel doesn't exist
     */
    fun getChannelRadius(channelName: String): Double {
        val channel = plugin.configManager.getChannel(channelName) ?: return -1.0
        return channel.radius.toDouble()
    }

    /**
     * Get metadata about a channel for external integrations
     * @param channelName The channel name
     * @return A map containing channel metadata
     */
    fun getChannelMetadata(channelName: String): Map<String, Any> {
        val channel = plugin.configManager.getChannel(channelName) ?: return emptyMap()

        return mapOf(
            "name" to channelName,
            "displayName" to (channel.displayName ?: channelName),
            "permission" to (channel.permission ?: ""),
            "radius" to channel.radius,
            "isLocal" to (channel.radius > 0),
            "isGlobal" to (channel.radius <= 0),
            "prefix" to (channel.prefix ?: ""),
            "hover" to (channel.hoverTemplate ?: ""),
            "discordChannel" to (getDiscordChannelForRemmyChannel(channelName) ?: "")
        )
    }
}
