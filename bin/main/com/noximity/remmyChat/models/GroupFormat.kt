package com.noximity.remmyChat.models

/**
 * Represents a group format with all its configuration, permissions, and features
 */
data class GroupFormat(
    val name: String,
    val priority: Int = 100,
    val displayName: String = "",
    val description: String = "",
    val permissions: List<String> = emptyList(),
    val prefix: String = "",
    val suffix: String = "",
    val nameStyle: String = "%player_name%",
    val chatFormat: String = "%name-style% <dark_gray>»</dark_gray> %message%",
    var hoverTemplate: String = "player-info",
    var clickAction: String = "suggest",
    var clickCommand: String = "/msg %player_name% "
) {
    // Feature flags
    var bypassCooldown: Boolean = false
    var bypassFilters: Boolean = false
    var useColors: Boolean = false
    var useFormatting: Boolean = false
    var mentionEveryone: Boolean = false
    var priorityMessages: Boolean = false
    var socialSpyAccess: Boolean = false
    var moderateChat: Boolean = false
    var staffChannels: Boolean = false

    // VIP-specific features
    var customJoinMessage: Boolean = false
    var nameColors: Boolean = false
    var chatEffects: Boolean = false

    // Message limits
    var messageLimit: Int = 200 // max message length
    var mentionCooldown: Int = 600 // seconds between mentions

    // Restrictions for guests
    var restrictedChannels: List<String> = emptyList()
    var chatDelay: Int = 0 // extra delay between messages

    // Group state
    var onlineCount: Int = 0
    var lastUpdated: Long = System.currentTimeMillis()

    /**
     * Check if a player has any of the required permissions for this group
     */
    fun hasPermission(player: org.bukkit.entity.Player): Boolean {
        if (permissions.isEmpty()) return name == "default"

        return permissions.any { permission ->
            when {
                permission == "*" -> player.isOp
                permission.endsWith(".*") -> {
                    val basePermission = permission.substring(0, permission.length - 2)
                    player.hasPermission(basePermission) || player.hasPermission(permission)
                }
                else -> player.hasPermission(permission)
            }
        }
    }

    /**
     * Check if this group has staff privileges
     */
    fun isStaff(): Boolean {
        return staffChannels || moderateChat || socialSpyAccess ||
               permissions.any { it.contains("staff") || it.contains("admin") || it.contains("mod") }
    }

    /**
     * Check if this group has admin privileges
     */
    fun isAdmin(): Boolean {
        return permissions.any { it.contains("admin") || it == "*" || it.contains("owner") }
    }

    /**
     * Check if this group can access a specific channel
     */
    fun canAccessChannel(channelName: String): Boolean {
        return !restrictedChannels.contains(channelName)
    }

    /**
     * Check if this group can bypass rate limiting
     */
    fun canBypassRateLimit(): Boolean {
        return bypassCooldown || isStaff()
    }

    /**
     * Check if this group can use chat formatting
     */
    fun canUseFormatting(): Boolean {
        return useFormatting || useColors
    }

    /**
     * Check if this group can mention everyone
     */
    fun canMentionEveryone(): Boolean {
        return mentionEveryone || isAdmin()
    }

    /**
     * Get the effective message length limit for this group
     */
    fun getMessageLengthLimit(): Int {
        return when {
            isAdmin() -> 1000
            isStaff() -> 500
            else -> messageLimit
        }
    }

    /**
     * Get the effective cooldown for this group
     */
    fun getCooldown(): Int {
        return when {
            bypassCooldown -> 0
            isStaff() -> 0
            else -> chatDelay
        }
    }

    /**
     * Get the effective mention cooldown for this group
     */
    fun getEffectiveMentionCooldown(): Int {
        return when {
            isAdmin() -> 0
            isStaff() -> 30
            else -> mentionCooldown
        }
    }

    /**
     * Get formatted name with styling applied
     */
    fun getFormattedName(playerName: String): String {
        return nameStyle.replace("%player_name%", playerName)
    }

    /**
     * Get complete formatted chat message
     */
    fun getFormattedChatMessage(playerName: String, message: String): String {
        var formatted = chatFormat
        formatted = formatted.replace("%name-style%", getFormattedName(playerName))
        formatted = formatted.replace("%player_name%", playerName)
        formatted = formatted.replace("%prefix%", prefix)
        formatted = formatted.replace("%suffix%", suffix)
        formatted = formatted.replace("%message%", message)
        return formatted
    }

    /**
     * Get click component for player name
     */
    fun getClickComponent(): String {
        return when (clickAction.lowercase()) {
            "suggest" -> "<click:suggest_command:'$clickCommand'>"
            "run" -> "<click:run_command:'$clickCommand'>"
            "url" -> "<click:open_url:'$clickCommand'>"
            else -> ""
        }
    }

    /**
     * Get hover component for player name
     */
    fun getHoverComponent(): String {
        return if (hoverTemplate.isNotEmpty()) {
            "<hover:show_text:'%hover-$hoverTemplate%'>"
        } else {
            ""
        }
    }

    /**
     * Get interactive name format (with click and hover)
     */
    fun getInteractiveName(playerName: String): String {
        val click = getClickComponent()
        val hover = getHoverComponent()
        val formattedName = getFormattedName(playerName)

        return if (click.isNotEmpty() && hover.isNotEmpty()) {
            "$click$hover$formattedName</hover></click>"
        } else if (hover.isNotEmpty()) {
            "$hover$formattedName</hover>"
        } else if (click.isNotEmpty()) {
            "$click$formattedName</click>"
        } else {
            formattedName
        }
    }

    /**
     * Get group features as a map
     */
    fun getFeatures(): Map<String, Boolean> {
        return mapOf(
            "bypassCooldown" to bypassCooldown,
            "bypassFilters" to bypassFilters,
            "useColors" to useColors,
            "useFormatting" to useFormatting,
            "mentionEveryone" to mentionEveryone,
            "priorityMessages" to priorityMessages,
            "socialSpyAccess" to socialSpyAccess,
            "moderateChat" to moderateChat,
            "staffChannels" to staffChannels,
            "customJoinMessage" to customJoinMessage,
            "nameColors" to nameColors,
            "chatEffects" to chatEffects
        )
    }

    /**
     * Get group statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "displayName" to displayName,
            "priority" to priority,
            "onlineCount" to onlineCount,
            "isStaff" to isStaff(),
            "isAdmin" to isAdmin(),
            "permissions" to permissions.size,
            "features" to getFeatures().count { it.value },
            "lastUpdated" to lastUpdated
        )
    }

    /**
     * Compare groups by priority (higher priority first)
     */
    fun isHigherPriorityThan(other: GroupFormat): Boolean {
        return this.priority > other.priority
    }

    /**
     * Check if this group inherits from another group
     */
    fun inheritsFrom(parent: GroupFormat): Boolean {
        // This would be implemented based on inheritance rules from config
        return parent.priority > this.priority
    }

    override fun toString(): String {
        return "GroupFormat(name='$name', priority=$priority, displayName='$displayName')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupFormat) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        /**
         * Create a default group format for fallback
         */
        fun createDefault(): GroupFormat {
            return GroupFormat(
                name = "default",
                priority = 1,
                displayName = "Player",
                description = "Default player group",
                permissions = emptyList(),
                prefix = "",
                suffix = "",
                nameStyle = "<white>%player_name%</white>",
                chatFormat = "%name-style% <dark_gray>»</dark_gray> %message%",
                hoverTemplate = "player-info",
                clickAction = "suggest",
                clickCommand = "/msg %player_name% "
            )
        }

        /**
         * Get group priority order (for sorting)
         */
        fun priorityComparator(): Comparator<GroupFormat> {
            return Comparator { g1, g2 -> g2.priority.compareTo(g1.priority) }
        }
    }
}
