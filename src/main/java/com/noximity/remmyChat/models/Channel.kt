package com.noximity.remmyChat.models

/**
 * Represents a chat channel with all its configuration and features
 */
data class Channel(
    val name: String,
    val permission: String = "",
    val radius: Int = -1,
    val prefix: String = "",
    val displayName: String = "",
    val description: String = "",
    val format: String = "%player_display_name% <dark_gray>»</dark_gray> %message%",
    val hoverTemplate: String = "player-info",
    val crossServer: Boolean = false,
    val localOnly: Boolean = false,
    val enabled: Boolean = true
) {
    // Moderation settings
    var rateLimit: Int = 30 // messages per minute
    var cooldown: Int = 3 // seconds between messages
    var maxLength: Int = 256 // maximum message length
    var spamProtection: Boolean = true

    // Feature flags
    var urlDetection: Boolean = true
    var mentionSystem: Boolean = true
    var emojiSupport: Boolean = true
    var placeholderParsing: Boolean = true

    // Advanced features
    var autoNotifyStaff: Boolean = false
    var ticketSystem: Boolean = false
    var faqIntegration: Boolean = false
    var requireKeywords: Boolean = false
    var priceDetection: Boolean = false
    var itemLinking: Boolean = false
    var autoExpire: Int = 0 // 0 = no expiry

    // Channel state
    var memberCount: Int = 0
    var lastActivity: Long = System.currentTimeMillis()

    /**
     * Check if a player has permission to use this channel
     */
    fun hasPermission(player: org.bukkit.entity.Player): Boolean {
        return permission.isEmpty() || player.hasPermission(permission)
    }

    /**
     * Check if this channel is global (no radius limit)
     */
    fun isGlobal(): Boolean = radius < 0

    /**
     * Check if this channel is local (has radius limit)
     */
    fun isLocal(): Boolean = radius >= 0

    /**
     * Check if this channel supports cross-server messaging
     */
    fun isCrossServer(): Boolean = crossServer && !localOnly

    /**
     * Check if this channel is for local server only
     */
    fun isLocalOnly(): Boolean = localOnly || (!crossServer && isLocal())

    /**
     * Check if this channel has a custom display name
     */
    fun hasDisplayName(): Boolean = displayName.isNotEmpty()

    /**
     * Get the effective display name (use name if display name is empty)
     */
    fun getEffectiveDisplayName(): String = if (displayName.isNotEmpty()) displayName else name

    /**
     * Get formatted prefix with template resolution
     */
    fun getFormattedPrefix(): String {
        return if (prefix.isNotEmpty()) {
            // This will be resolved by the template system
            "%channel-prefix-$prefix%"
        } else {
            ""
        }
    }

    /**
     * Check if a message length is within limits
     */
    fun isMessageLengthValid(messageLength: Int): Boolean {
        return messageLength <= maxLength
    }

    /**
     * Check if player can send message based on rate limiting
     */
    fun canSendMessage(player: org.bukkit.entity.Player, lastMessageTime: Long): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastMessage = (now - lastMessageTime) / 1000

        return timeSinceLastMessage >= cooldown
    }

    /**
     * Get channel type description
     */
    fun getChannelType(): String {
        return when {
            isGlobal() && isCrossServer() -> "Global Cross-Server"
            isGlobal() && !isCrossServer() -> "Global Local"
            isLocal() && !isCrossServer() -> "Local Proximity"
            else -> "Unknown"
        }
    }

    /**
     * Check if this channel should receive messages from another server
     */
    fun shouldReceiveCrossServerMessage(sourceServer: String): Boolean {
        return isCrossServer() && enabled
    }

    /**
     * Get channel statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "type" to getChannelType(),
            "members" to memberCount,
            "crossServer" to isCrossServer(),
            "enabled" to enabled,
            "rateLimit" to rateLimit,
            "maxLength" to maxLength,
            "lastActivity" to lastActivity
        )
    }

    override fun toString(): String {
        return "Channel(name='$name', type='${getChannelType()}', enabled=$enabled)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Channel) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
