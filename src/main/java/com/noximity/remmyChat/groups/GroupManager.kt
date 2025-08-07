package com.noximity.remmyChat.groups

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.GroupFormat
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

// Data classes for GroupManager
data class InheritanceRule(
    val parent: String,
    val children: List<String>,
    val inheritProperties: List<String>
)

data class CachedGroupInfo(
    val group: GroupFormat?,
    val expiryTime: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiryTime
}

data class ChannelRestriction(
    val allowedGroups: List<String>,
    val deniedGroups: List<String>
)

data class MentionRestriction(
    val everyoneMentions: List<String>,
    val staffMentions: List<String>,
    val cooldowns: Map<String, Int>
)

enum class DetectionMethod {
    PERMISSION, VAULT, LUCKPERMS, MANUAL
}

/**
 * Manages group detection and formatting for RemmyChat
 * Handles groups.yml configuration and provides group-based chat formatting
 */
class GroupManager(private val plugin: RemmyChat) {

    // Configuration
    private lateinit var groupsConfig: FileConfiguration

    // Group data
    private val groups = ConcurrentHashMap<String, GroupFormat>()
    private val inheritanceRules = mutableListOf<InheritanceRule>()
    private val groupCache = ConcurrentHashMap<UUID, CachedGroupInfo>()

    // Detection method
    private var detectionMethod = DetectionMethod.PERMISSION
    private var checkAllGroups = true
    private var cacheDuration = 300L // 5 minutes in seconds
    private var autoUpdate = true

    // Vault integration
    private var vaultEnabled = false
    private var usePrimaryGroup = true
    private val vaultGroupMapping = ConcurrentHashMap<String, String>()

    // LuckPerms integration
    private var luckPermsEnabled = false
    private var checkParents = true
    private var useWeight = true

    // Group behaviors
    private val channelRestrictions = ConcurrentHashMap<String, ChannelRestriction>()
    private val messageLimits = ConcurrentHashMap<String, Int>()
    private val mentionRestrictions = ConcurrentHashMap<String, MentionRestriction>()

    /**
     * Initialize the group manager
     */
    fun initialize() {
        loadGroupsConfig()
        loadGroups()
        loadInheritanceRules()
        loadDetectionSettings()
        loadVaultIntegration()
        loadLuckPermsIntegration()
        loadGroupBehaviors()

        plugin.debugLog("GroupManager initialized with ${groups.size} groups")
    }

    /**
     * Load groups configuration
     */
    private fun loadGroupsConfig() {
        val configFile = File(plugin.dataFolder, "groups.yml")
        if (!configFile.exists()) {
            plugin.saveResource("groups.yml", false)
        }

        groupsConfig = YamlConfiguration.loadConfiguration(configFile)
    }

    /**
     * Load all groups from configuration
     */
    private fun loadGroups() {
        groups.clear()

        val groupsSection = groupsConfig.getConfigurationSection("groups")
        groupsSection?.getKeys(false)?.forEach { groupName ->
            val groupSection = groupsSection.getConfigurationSection(groupName)
            if (groupSection != null) {
                val group = createGroupFromConfig(groupName, groupSection)
                groups[groupName] = group
                plugin.debugLog("Loaded group: $groupName with priority ${group.priority}")
            }
        }
    }

    /**
     * Create a group format from configuration section
     */
    private fun createGroupFromConfig(name: String, config: org.bukkit.configuration.ConfigurationSection): GroupFormat {
        val group = GroupFormat(
            name = name,
            priority = config.getInt("priority", 100),
            displayName = config.getString("display-name", name) ?: name,
            description = config.getString("description", "") ?: "",
            permissions = config.getStringList("permissions"),
            prefix = config.getString("formatting.prefix", "") ?: "",
            suffix = config.getString("formatting.suffix", "") ?: "",
            nameStyle = config.getString("formatting.name-style", "%player_name%") ?: "%player_name%",
            chatFormat = config.getString("formatting.chat-format", "%prefix% %name-style% <dark_gray>»</dark_gray> %message%") ?: "%prefix% %name-style% <dark_gray>»</dark_gray> %message%"
        )

        // Load interactive elements
        val interactionsSection = config.getConfigurationSection("interactions")
        if (interactionsSection != null) {
            group.hoverTemplate = interactionsSection.getString("hover-template", "player-info") ?: "player-info"
            group.clickAction = interactionsSection.getString("click-action", "suggest") ?: "suggest"
            group.clickCommand = interactionsSection.getString("click-command", "/msg %player_name% ") ?: "/msg %player_name% "
        }

        // Load features
        val featuresSection = config.getConfigurationSection("features")
        if (featuresSection != null) {
            group.bypassCooldown = featuresSection.getBoolean("bypass-cooldown", false)
            group.bypassFilters = featuresSection.getBoolean("bypass-filters", false)
            group.useColors = featuresSection.getBoolean("use-colors", false)
            group.useFormatting = featuresSection.getBoolean("use-formatting", false)
            group.mentionEveryone = featuresSection.getBoolean("mention-everyone", false)
            group.priorityMessages = featuresSection.getBoolean("priority-messages", false)
            group.socialSpyAccess = featuresSection.getBoolean("social-spy-access", false)
            group.moderateChat = featuresSection.getBoolean("moderate-chat", false)
            group.staffChannels = featuresSection.getBoolean("staff-channels", false)
            group.customJoinMessage = featuresSection.getBoolean("custom-join-message", false)
            group.nameColors = featuresSection.getBoolean("name-colors", false)
            group.chatEffects = featuresSection.getBoolean("chat-effects", false)

            // Special features
            group.restrictedChannels = featuresSection.getStringList("restricted-channels")
            group.chatDelay = featuresSection.getInt("chat-delay", 0)
        }

        return group
    }

    /**
     * Load inheritance rules
     */
    private fun loadInheritanceRules() {
        inheritanceRules.clear()

        val inheritanceSection = groupsConfig.getConfigurationSection("inheritance")
        if (inheritanceSection?.getBoolean("enabled", true) == true) {
            val rulesSection = inheritanceSection.getConfigurationSection("rules")
            rulesSection?.getKeys(false)?.forEach { ruleIndex ->
                val ruleSection = rulesSection.getConfigurationSection(ruleIndex)
                if (ruleSection != null) {
                    val parent = ruleSection.getString("parent", "") ?: ""
                    val children = ruleSection.getStringList("children")
                    val inherit = ruleSection.getStringList("inherit")

                    if (parent.isNotEmpty() && children.isNotEmpty()) {
                        inheritanceRules.add(InheritanceRule(parent, children, inherit))
                    }
                }
            }
        }
    }

    /**
     * Load detection settings
     */
    private fun loadDetectionSettings() {
        val detectionSection = groupsConfig.getConfigurationSection("detection")
        if (detectionSection != null) {
            detectionMethod = DetectionMethod.valueOf(
                (detectionSection.getString("method", "PERMISSION") ?: "PERMISSION").uppercase()
            )

            val permissionSection = detectionSection.getConfigurationSection("permission-detection")
            if (permissionSection != null) {
                checkAllGroups = permissionSection.getBoolean("check-all", true)
                cacheDuration = permissionSection.getLong("cache-duration", 300)
                autoUpdate = permissionSection.getBoolean("auto-update", true)
            }
        }
    }

    /**
     * Load Vault integration settings
     */
    private fun loadVaultIntegration() {
        val vaultSection = groupsConfig.getConfigurationSection("detection.vault-integration")
        if (vaultSection != null) {
            vaultEnabled = vaultSection.getBoolean("enabled", true)
            usePrimaryGroup = vaultSection.getBoolean("use-primary-group", true)

            val mappingSection = vaultSection.getConfigurationSection("group-mapping")
            mappingSection?.getKeys(false)?.forEach { vaultGroup ->
                val remmyGroup = mappingSection.getString(vaultGroup)
                if (!remmyGroup.isNullOrEmpty()) {
                    vaultGroupMapping[vaultGroup] = remmyGroup
                }
            }
        }
    }

    /**
     * Load LuckPerms integration settings
     */
    private fun loadLuckPermsIntegration() {
        val luckPermsSection = groupsConfig.getConfigurationSection("detection.luckperms-integration")
        if (luckPermsSection != null) {
            luckPermsEnabled = luckPermsSection.getBoolean("enabled", true)
            usePrimaryGroup = luckPermsSection.getBoolean("use-primary-group", true)
            checkParents = luckPermsSection.getBoolean("check-parents", true)
            useWeight = luckPermsSection.getBoolean("use-weight", true)
        }
    }

    /**
     * Load group behaviors
     */
    private fun loadGroupBehaviors() {
        val behaviorsSection = groupsConfig.getConfigurationSection("behaviors")

        // Channel access restrictions
        val channelAccessSection = behaviorsSection?.getConfigurationSection("channel-access")
        if (channelAccessSection?.getBoolean("enabled", true) == true) {
            val restrictionsSection = channelAccessSection.getConfigurationSection("restrictions")
            restrictionsSection?.getKeys(false)?.forEach { channelName ->
                val channelSection = restrictionsSection.getConfigurationSection(channelName)
                if (channelSection != null) {
                    val allowedGroups = channelSection.getStringList("allowed-groups")
                    val deniedGroups = channelSection.getStringList("denied-groups")
                    channelRestrictions[channelName] = ChannelRestriction(allowedGroups, deniedGroups)
                }
            }
        }

        // Message length limits
        val messageLimitsSection = behaviorsSection?.getConfigurationSection("message-limits")
        if (messageLimitsSection?.getBoolean("enabled", true) == true) {
            val limitsSection = messageLimitsSection.getConfigurationSection("limits")
            limitsSection?.getKeys(false)?.forEach { groupName ->
                val limit = limitsSection.getInt(groupName, 200)
                messageLimits[groupName] = limit
            }
        }

        // Mention restrictions
        val mentionRestrictionsSection = behaviorsSection?.getConfigurationSection("mention-restrictions")
        if (mentionRestrictionsSection?.getBoolean("enabled", true) == true) {
            val everyoneMentions = mentionRestrictionsSection.getStringList("everyone-mentions.allowed-groups")
            val staffMentions = mentionRestrictionsSection.getStringList("staff-mentions.allowed-groups")

            val cooldownsSection = mentionRestrictionsSection.getConfigurationSection("mention-cooldowns")
            val cooldowns = mutableMapOf<String, Int>()
            cooldownsSection?.getKeys(false)?.forEach { groupName ->
                cooldowns[groupName] = cooldownsSection.getInt(groupName, 0)
            }

            mentionRestrictions["global"] = MentionRestriction(everyoneMentions, staffMentions, cooldowns)
        }
    }

    /**
     * Detect a player's group
     */
    fun detectPlayerGroup(player: Player): GroupFormat? {
        val playerId = player.uniqueId

        // Check cache first
        val cached = groupCache[playerId]
        if (cached != null && !cached.isExpired()) {
            return cached.group
        }

        val detectedGroup = when (detectionMethod) {
            DetectionMethod.PERMISSION -> detectByPermission(player)
            DetectionMethod.VAULT -> detectByVault(player)
            DetectionMethod.LUCKPERMS -> detectByLuckPerms(player)
            DetectionMethod.MANUAL -> groups["default"] // Manual assignment not implemented yet
        }

        // Apply inheritance
        val finalGroup = applyInheritance(detectedGroup)

        // Cache the result
        if (cacheDuration > 0) {
            groupCache[playerId] = CachedGroupInfo(finalGroup, System.currentTimeMillis() + (cacheDuration * 1000))
        }

        return finalGroup
    }

    /**
     * Detect group by permissions
     */
    private fun detectByPermission(player: Player): GroupFormat? {
        if (checkAllGroups) {
            // Check all groups and return highest priority
            return groups.values
                .filter { group ->
                    group.permissions.any { permission ->
                        player.hasPermission(permission)
                    }
                }
                .maxByOrNull { it.priority }
        } else {
            // Return first matching group
            return groups.values
                .sortedByDescending { it.priority }
                .firstOrNull { group ->
                    group.permissions.any { permission ->
                        player.hasPermission(permission)
                    }
                }
        }
    }

    /**
     * Detect group by Vault
     */
    private fun detectByVault(player: Player): GroupFormat? {
        if (!vaultEnabled || !plugin.server.pluginManager.isPluginEnabled("Vault")) {
            return detectByPermission(player)
        }

        try {
            // This would integrate with Vault's permission system
            // For now, fallback to permission detection
            return detectByPermission(player)
        } catch (e: Exception) {
            plugin.debugLog("Vault group detection failed: ${e.message}")
            return detectByPermission(player)
        }
    }

    /**
     * Detect group by LuckPerms
     */
    private fun detectByLuckPerms(player: Player): GroupFormat? {
        if (!luckPermsEnabled || !plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
            return detectByPermission(player)
        }

        try {
            // This would integrate with LuckPerms API
            // For now, fallback to permission detection
            return detectByPermission(player)
        } catch (e: Exception) {
            plugin.debugLog("LuckPerms group detection failed: ${e.message}")
            return detectByPermission(player)
        }
    }

    /**
     * Apply inheritance rules to a group
     */
    private fun applyInheritance(group: GroupFormat?): GroupFormat? {
        if (group == null) return null

        val inherited = group.copy()

        inheritanceRules.forEach { rule ->
            if (rule.children.contains(group.name)) {
                val parentGroup = groups[rule.parent]
                if (parentGroup != null) {
                    rule.inheritProperties.forEach { property ->
                        when (property) {
                            "staff-channels" -> inherited.staffChannels = parentGroup.staffChannels || inherited.staffChannels
                            "moderate-chat" -> inherited.moderateChat = parentGroup.moderateChat || inherited.moderateChat
                            "name-colors" -> inherited.nameColors = parentGroup.nameColors || inherited.nameColors
                            "custom-join-message" -> inherited.customJoinMessage = parentGroup.customJoinMessage || inherited.customJoinMessage
                            "basic-chat" -> {
                                // Copy basic chat properties using data class copy
                                return inherited.copy(
                                    chatFormat = if (inherited.chatFormat.isEmpty()) parentGroup.chatFormat else inherited.chatFormat,
                                    prefix = if (inherited.prefix.isEmpty()) parentGroup.prefix else inherited.prefix
                                )
                            }
                        }
                    }
                }
            }
        }

        return inherited
    }

    /**
     * Check if player has channel access
     */
    fun hasChannelAccess(player: Player, channelName: String): Boolean {
        val restriction = channelRestrictions[channelName] ?: return true
        val group = detectPlayerGroup(player) ?: return true

        // Check denied groups first
        if (restriction.deniedGroups.contains(group.name)) {
            return false
        }

        // Check allowed groups
        if (restriction.allowedGroups.contains("*")) {
            return true
        }

        return restriction.allowedGroups.contains(group.name)
    }

    /**
     * Get player's group
     */
    fun getPlayerGroup(player: Player): GroupFormat? {
        return detectPlayerGroup(player)
    }

    /**
     * Reload the group manager
     */
    fun reload() {
        plugin.debugLog("Reloading GroupManager...")
        loadGroupsConfig()
        loadGroups()
        loadInheritanceRules()
        loadDetectionSettings()
        loadVaultIntegration()
        loadLuckPermsIntegration()
        loadGroupBehaviors()
        groupCache.clear()
        plugin.debugLog("GroupManager reloaded")
    }

    /**
     * Clean up cached data
     */
    fun cleanupCache() {
        val currentTime = System.currentTimeMillis()
        val expired = groupCache.entries.filter { it.value.expiryTime < currentTime }
        expired.forEach { groupCache.remove(it.key) }
        plugin.debugLog("Cleaned up ${expired.size} expired group cache entries")
    }

    /**
     * Get all groups
     */
    fun getGroups(): Map<String, GroupFormat> = groups

    /**
     * Get a specific group
     */
    fun getGroup(name: String): GroupFormat? = groups[name]

    /**
     * Get message length limit for player
     */
    fun getMessageLengthLimit(player: Player): Int {
        val group = detectPlayerGroup(player) ?: return 200
        return messageLimits[group.name] ?: 200
    }

    /**
     * Check if player can mention everyone
     */
    fun canMentionEveryone(player: Player): Boolean {
        val group = detectPlayerGroup(player) ?: return false
        return group.mentionEveryone
    }

    /**
     * Get mention cooldown for player
     */
    fun getMentionCooldown(player: Player): Int {
        val group = detectPlayerGroup(player) ?: return 600
        val restriction = mentionRestrictions["global"] ?: return 600
        return restriction.cooldowns[group.name] ?: 600
    }

    /**
     * Get all groups
     */
    fun getAllGroups(): Map<String, GroupFormat> {
        return groups.toMap()
    }

    /**
     * Clear player cache
     */
    fun clearPlayerCache(playerId: UUID) {
        groupCache.remove(playerId)
    }

    /**
     * Get group statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalGroups" to groups.size,
            "detectionMethod" to detectionMethod.name,
            "cachedPlayers" to groupCache.size,
            "inheritanceRules" to inheritanceRules.size,
            "channelRestrictions" to channelRestrictions.size,
            "vaultEnabled" to vaultEnabled,
            "luckPermsEnabled" to luckPermsEnabled
        )
    }
}
