package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Manages group-based behavioral restrictions including channel access, message limits, and mention restrictions
 */
class GroupBehaviorManager(private val plugin: RemmyChat) {

    // Channel access control
    private val channelAccessRules = ConcurrentHashMap<String, ChannelAccessRule>()

    // Message limits per group
    private val groupMessageLimits = ConcurrentHashMap<String, Int>()

    // Mention restrictions
    private val mentionRestrictions = ConcurrentHashMap<String, MentionRestriction>()
    private val playerMentionCooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    // Configuration
    private var channelAccessEnabled = true
    private var messageLimitsEnabled = true
    private var mentionRestrictionsEnabled = true

    // Patterns for mention detection
    private val everyoneMentionPattern = Pattern.compile("@everyone|@all|@here", Pattern.CASE_INSENSITIVE)
    private val staffMentionPattern = Pattern.compile("@staff|@admin|@mod|@moderator", Pattern.CASE_INSENSITIVE)
    private val playerMentionPattern = Pattern.compile("@(\\w+)", Pattern.CASE_INSENSITIVE)

    fun initialize() {
        loadConfiguration()
        loadChannelAccessRules()
        loadMessageLimits()
        loadMentionRestrictions()

        plugin.debugLog("GroupBehaviorManager initialized")
    }

    /**
     * Check if a player can access a specific channel based on their group
     */
    fun canAccessChannel(player: Player, channel: Channel): ChannelAccessResult {
        if (!channelAccessEnabled) {
            return ChannelAccessResult(true)
        }

        val playerGroup = plugin.groupManager.getPlayerGroup(player)
        val accessRule = channelAccessRules[channel.name]

        if (accessRule != null) {
            // Check if group is explicitly denied
            if (accessRule.deniedGroups.contains(playerGroup) ||
                accessRule.deniedGroups.contains("*")) {
                return ChannelAccessResult(false, "Your group ($playerGroup) is not allowed in this channel")
            }

            // Check if group is explicitly allowed or if all groups are allowed
            if (accessRule.allowedGroups.contains(playerGroup) ||
                accessRule.allowedGroups.contains("*")) {
                return ChannelAccessResult(true)
            }

            // If there are specific allowed groups and player's group is not in it
            if (accessRule.allowedGroups.isNotEmpty() &&
                !accessRule.allowedGroups.contains(playerGroup)) {
                return ChannelAccessResult(false, "This channel is restricted to specific groups")
            }
        }

        return ChannelAccessResult(true)
    }

    /**
     * Check if a message length is valid for the player's group
     */
    fun validateMessageLength(player: Player, message: String): MessageLengthResult {
        if (!messageLimitsEnabled) {
            return MessageLengthResult(true)
        }

        val playerGroup = plugin.groupManager.getPlayerGroup(player)
        val maxLength = groupMessageLimits[playerGroup] ?: groupMessageLimits["default"] ?: 256

        return if (message.length <= maxLength) {
            MessageLengthResult(true)
        } else {
            MessageLengthResult(
                false,
                "Message too long (${message.length}/$maxLength characters)",
                maxLength
            )
        }
    }

    /**
     * Process and validate mentions in a message
     */
    fun processMentions(player: Player, channel: Channel, message: String): MentionProcessResult {
        if (!mentionRestrictionsEnabled) {
            return MentionProcessResult(true, message)
        }

        val playerGroup = plugin.groupManager.getPlayerGroup(player)
        val restriction = mentionRestrictions[playerGroup] ?: mentionRestrictions["default"]
            ?: MentionRestriction()

        val result = MentionProcessResult(true, message)
        var processedMessage = message

        // Check @everyone mentions
        if (everyoneMentionPattern.matcher(message).find()) {
            if (!restriction.canMentionEveryone) {
                result.isValid = false
                result.reason = "You don't have permission to mention everyone"
                return result
            }

            if (!checkMentionCooldown(player, "everyone", restriction.everyoneCooldown)) {
                result.isValid = false
                result.reason = "You must wait before mentioning everyone again"
                return result
            }

            // Process @everyone mention
            processedMessage = processEveryoneMention(processedMessage, channel)
            result.mentionedEveryone = true
            setMentionCooldown(player, "everyone")
        }

        // Check @staff mentions
        if (staffMentionPattern.matcher(message).find()) {
            if (!restriction.canMentionStaff) {
                result.isValid = false
                result.reason = "You don't have permission to mention staff"
                return result
            }

            if (!checkMentionCooldown(player, "staff", restriction.staffCooldown)) {
                result.isValid = false
                result.reason = "You must wait before mentioning staff again"
                return result
            }

            // Process @staff mention
            processedMessage = processStaffMention(processedMessage, channel)
            result.mentionedStaff = true
            setMentionCooldown(player, "staff")
        }

        // Check individual player mentions
        val playerMentions = findPlayerMentions(message)
        if (playerMentions.isNotEmpty()) {
            if (!checkMentionCooldown(player, "player", restriction.playerCooldown)) {
                result.isValid = false
                result.reason = "You must wait before mentioning players again"
                return result
            }

            processedMessage = processPlayerMentions(processedMessage, playerMentions)
            result.mentionedPlayers = playerMentions
            setMentionCooldown(player, "player")
        }

        result.processedMessage = processedMessage
        return result
    }

    /**
     * Load configuration settings
     */
    private fun loadConfiguration() {
        val groupsConfig = plugin.configManager.getGroupsConfig()
        val behaviorsSection = groupsConfig.getConfigurationSection("behaviors")

        if (behaviorsSection != null) {
            channelAccessEnabled = behaviorsSection.getBoolean("channel-access.enabled", true)
            messageLimitsEnabled = behaviorsSection.getBoolean("message-limits.enabled", true)
            mentionRestrictionsEnabled = behaviorsSection.getBoolean("mention-restrictions.enabled", true)
        }
    }

    /**
     * Load channel access rules
     */
    private fun loadChannelAccessRules() {
        channelAccessRules.clear()

        val groupsConfig = plugin.configManager.getGroupsConfig()
        val accessSection = groupsConfig.getConfigurationSection("behaviors.channel-access.restrictions")

        accessSection?.getKeys(false)?.forEach { channelName ->
            val channelSection = accessSection.getConfigurationSection(channelName)
            if (channelSection != null) {
                val allowedGroups = channelSection.getStringList("allowed-groups").toMutableSet()
                val deniedGroups = channelSection.getStringList("denied-groups").toMutableSet()

                channelAccessRules[channelName] = ChannelAccessRule(
                    channelName = channelName,
                    allowedGroups = allowedGroups,
                    deniedGroups = deniedGroups
                )
            }
        }

        plugin.debugLog("Loaded ${channelAccessRules.size} channel access rules")
    }

    /**
     * Load message limits per group
     */
    private fun loadMessageLimits() {
        groupMessageLimits.clear()

        val groupsConfig = plugin.configManager.getGroupsConfig()
        val limitsSection = groupsConfig.getConfigurationSection("behaviors.message-limits.limits")

        limitsSection?.getKeys(false)?.forEach { groupName ->
            val limit = limitsSection.getInt(groupName, 256)
            groupMessageLimits[groupName] = limit
        }

        plugin.debugLog("Loaded message limits for ${groupMessageLimits.size} groups")
    }

    /**
     * Load mention restrictions per group
     */
    private fun loadMentionRestrictions() {
        mentionRestrictions.clear()

        val groupsConfig = plugin.configManager.getGroupsConfig()
        val mentionSection = groupsConfig.getConfigurationSection("behaviors.mention-restrictions")

        if (mentionSection != null) {
            // Load everyone mention permissions
            val everyoneAllowed = mentionSection.getStringList("everyone-mentions.allowed-groups")

            // Load staff mention permissions
            val staffAllowed = mentionSection.getStringList("staff-mentions.allowed-groups")

            // Load mention cooldowns
            val cooldownSection = mentionSection.getConfigurationSection("mention-cooldowns")

            // Create restrictions for each group mentioned in cooldowns
            cooldownSection?.getKeys(false)?.forEach { groupName ->
                val everyoneCooldown = cooldownSection.getLong("$groupName.everyone", 600)
                val staffCooldown = cooldownSection.getLong("$groupName.staff", 300)
                val playerCooldown = cooldownSection.getLong("$groupName.player", 60)

                mentionRestrictions[groupName] = MentionRestriction(
                    canMentionEveryone = everyoneAllowed.contains(groupName),
                    canMentionStaff = staffAllowed.contains(groupName) || everyoneAllowed.contains(groupName),
                    everyoneCooldown = everyoneCooldown,
                    staffCooldown = staffCooldown,
                    playerCooldown = playerCooldown
                )
            }
        }

        plugin.debugLog("Loaded mention restrictions for ${mentionRestrictions.size} groups")
    }

    /**
     * Check if player is on cooldown for a mention type
     */
    private fun checkMentionCooldown(player: Player, mentionType: String, cooldownSeconds: Long): Boolean {
        val playerCooldowns = playerMentionCooldowns[player.uniqueId] ?: return true
        val lastMention = playerCooldowns[mentionType] ?: return true

        val currentTime = System.currentTimeMillis()
        val cooldownTime = cooldownSeconds * 1000L

        return (currentTime - lastMention) >= cooldownTime
    }

    /**
     * Set mention cooldown for a player
     */
    private fun setMentionCooldown(player: Player, mentionType: String) {
        playerMentionCooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[mentionType] = System.currentTimeMillis()
    }

    /**
     * Process @everyone mention
     */
    private fun processEveryoneMention(message: String, channel: Channel): String {
        return everyoneMentionPattern.matcher(message).replaceAll { matchResult ->
            "<click:suggest_command:'/list'><hover:show_text:'Click to see online players'><color:#FFD700>@everyone</color></hover></click>"
        }
    }

    /**
     * Process @staff mention
     */
    private fun processStaffMention(message: String, channel: Channel): String {
        return staffMentionPattern.matcher(message).replaceAll { matchResult ->
            "<click:suggest_command:'/staff list'><hover:show_text:'Click to see online staff'><color:#FF6B35>@staff</color></hover></click>"
        }
    }

    /**
     * Find individual player mentions in message
     */
    private fun findPlayerMentions(message: String): List<String> {
        val mentions = mutableListOf<String>()
        val matcher = playerMentionPattern.matcher(message)

        while (matcher.find()) {
            val mentionedName = matcher.group(1)
            // Check if it's not a special mention (everyone, staff, etc.)
            if (!setOf("everyone", "all", "here", "staff", "admin", "mod", "moderator").contains(mentionedName.lowercase())) {
                mentions.add(mentionedName)
            }
        }

        return mentions
    }

    /**
     * Process individual player mentions
     */
    private fun processPlayerMentions(message: String, mentions: List<String>): String {
        var processedMessage = message

        mentions.forEach { mentionedName ->
            val mentionedPlayer = plugin.server.getPlayer(mentionedName)
            if (mentionedPlayer != null && mentionedPlayer.isOnline) {
                // Replace with clickable mention
                val pattern = Pattern.compile("@$mentionedName\\b", Pattern.CASE_INSENSITIVE)
                processedMessage = pattern.matcher(processedMessage).replaceAll(
                    "<click:suggest_command:'/msg $mentionedName '><hover:show_text:'Click to message ${mentionedPlayer.name}'><color:#00FF00>@${mentionedPlayer.name}</color></hover></click>"
                )

                // Send notification to mentioned player
                mentionedPlayer.sendMessage(
                    Component.text("You were mentioned in chat!", NamedTextColor.YELLOW)
                )
            } else {
                // Player not found or offline, keep original mention but make it gray
                val pattern = Pattern.compile("@$mentionedName\\b", Pattern.CASE_INSENSITIVE)
                processedMessage = pattern.matcher(processedMessage).replaceAll(
                    "<color:#888888>@$mentionedName</color>"
                )
            }
        }

        return processedMessage
    }

    /**
     * Get mention cooldown remaining time
     */
    fun getMentionCooldownRemaining(player: Player, mentionType: String): Long {
        val playerCooldowns = playerMentionCooldowns[player.uniqueId] ?: return 0
        val lastMention = playerCooldowns[mentionType] ?: return 0

        val playerGroup = plugin.groupManager.getPlayerGroup(player)
        val restriction = mentionRestrictions[playerGroup] ?: return 0

        val cooldownSeconds = when (mentionType) {
            "everyone" -> restriction.everyoneCooldown
            "staff" -> restriction.staffCooldown
            "player" -> restriction.playerCooldown
            else -> 0
        }

        val currentTime = System.currentTimeMillis()
        val cooldownTime = cooldownSeconds * 1000L
        val elapsed = currentTime - lastMention

        return maxOf(0, (cooldownTime - elapsed) / 1000)
    }

    /**
     * Get channel access statistics
     */
    fun getChannelAccessStats(): Map<String, Any> {
        return mapOf(
            "channelAccessEnabled" to channelAccessEnabled,
            "channelRulesCount" to channelAccessRules.size,
            "messageLimitsEnabled" to messageLimitsEnabled,
            "groupLimitsCount" to groupMessageLimits.size,
            "mentionRestrictionsEnabled" to mentionRestrictionsEnabled,
            "mentionRulesCount" to mentionRestrictions.size
        )
    }

    // Data classes
    data class ChannelAccessResult(
        val allowed: Boolean,
        val reason: String = ""
    )

    data class MessageLengthResult(
        val valid: Boolean,
        val reason: String = "",
        val maxLength: Int = 0
    )

    data class MentionProcessResult(
        var isValid: Boolean,
        var processedMessage: String,
        var reason: String = "",
        var mentionedEveryone: Boolean = false,
        var mentionedStaff: Boolean = false,
        var mentionedPlayers: List<String> = emptyList()
    )

    data class ChannelAccessRule(
        val channelName: String,
        val allowedGroups: MutableSet<String>,
        val deniedGroups: MutableSet<String>
    )

    data class MentionRestriction(
        val canMentionEveryone: Boolean = false,
        val canMentionStaff: Boolean = false,
        val everyoneCooldown: Long = 600, // 10 minutes
        val staffCooldown: Long = 300,    // 5 minutes
        val playerCooldown: Long = 60     // 1 minute
    )
}
