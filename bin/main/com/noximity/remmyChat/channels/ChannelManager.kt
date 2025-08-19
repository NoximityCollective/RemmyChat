package com.noximity.remmyChat.channels

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.models.ChatUser
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.Location
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Manages chat channels with advanced features from channels.yml
 * Handles channel switching, permissions, cross-server functionality, and member management
 */
class ChannelManager(private val plugin: RemmyChat) {

    // Configuration
    private lateinit var channelsConfig: FileConfiguration

    // Channel data
    private val channels = ConcurrentHashMap<String, Channel>()
    private val channelMembers = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val playerChannels = ConcurrentHashMap<UUID, String>()
    private val channelGroups = ConcurrentHashMap<String, ChannelGroup>()

    // Auto-join settings
    private val newPlayerChannels = mutableSetOf<String>()
    private val permissionBasedChannels = ConcurrentHashMap<String, MutableSet<String>>()

    // Channel switching
    private var switchingEnabled = true
    private var switchCommand = "/ch"
    private val switchAliases = mutableSetOf<String>()
    private val quickSwitchMap = ConcurrentHashMap<String, String>()
    private var rememberChannel = true
    private var showListOnSwitch = true

    // Notifications
    private var joinLeaveNotifications = false
    private var mentionNotifications = true
    private var staffAlerts = true
    private val staffAlertKeywords = mutableSetOf<String>()

    // History
    private var historyEnabled = true
    private var maxMessagesPerChannel = 100
    private var retentionHours = 24
    private var playerHistoryAccess = true
    private var historyCommand = "/ch history"

    /**
     * Initialize the channel manager
     */
    fun initialize() {
        loadChannelsConfig()
        loadChannels()
        loadChannelGroups()
        loadAutoJoinSettings()
        loadSwitchingSettings()
        loadNotificationSettings()
        loadHistorySettings()

        plugin.debugLog("ChannelManager initialized with ${channels.size} channels")
    }

    /**
     * Load channels configuration
     */
    private fun loadChannelsConfig() {
        val configFile = File(plugin.dataFolder, "channels.yml")
        if (!configFile.exists()) {
            plugin.saveResource("channels.yml", false)
        }

        channelsConfig = YamlConfiguration.loadConfiguration(configFile)
    }

    /**
     * Load all channels from configuration
     */
    private fun loadChannels() {
        channels.clear()
        channelMembers.clear()

        val channelsSection = channelsConfig.getConfigurationSection("channels")
        channelsSection?.getKeys(false)?.forEach { channelName ->
            val channelSection = channelsSection.getConfigurationSection(channelName)
            if (channelSection != null) {
                val channel = createChannelFromConfig(channelName, channelSection)
                channels[channelName] = channel
                channelMembers[channelName] = ConcurrentHashMap.newKeySet()
                plugin.debugLog("Loaded channel: $channelName")
            }
        }
    }

    /**
     * Create a channel from configuration section
     */
    private fun createChannelFromConfig(name: String, config: org.bukkit.configuration.ConfigurationSection): Channel {
        val channel = Channel(
            name = name,
            permission = config.getString("permission", "") ?: "",
            radius = config.getInt("radius", -1),
            displayName = config.getString("display-name", "") ?: "",
            description = config.getString("description", "") ?: "",
            format = config.getString("format", "%player_display_name% <dark_gray>»</dark_gray> %message%") ?: "%player_display_name% <dark_gray>»</dark_gray> %message%",
            hoverTemplate = config.getString("hover-template", "player-info") ?: "player-info",
            crossServer = config.getBoolean("cross-server", false),
            localOnly = config.getBoolean("local-only", false),
            enabled = config.getBoolean("enabled", true)
        )

        // Load moderation settings
        val moderationSection = config.getConfigurationSection("moderation")
        if (moderationSection != null) {
            channel.rateLimit = moderationSection.getInt("rate-limit", 30)
            channel.cooldown = moderationSection.getInt("cooldown", 3)
            channel.maxLength = moderationSection.getInt("max-length", 256)
            channel.spamProtection = moderationSection.getBoolean("spam-protection", true)
        }

        // Load feature settings
        val featuresSection = config.getConfigurationSection("features")
        if (featuresSection != null) {
            channel.urlDetection = featuresSection.getBoolean("url-detection", true)
            channel.mentionSystem = featuresSection.getBoolean("mention-system", true)
            channel.emojiSupport = featuresSection.getBoolean("emoji-support", true)
            channel.placeholderParsing = featuresSection.getBoolean("placeholder-parsing", true)
        }

        // Load advanced features
        val advancedSection = config.getConfigurationSection("${name}-features")
        if (advancedSection != null) {
            when (name) {
                "help" -> {
                    channel.autoNotifyStaff = advancedSection.getBoolean("auto-notify-staff", false)
                    channel.ticketSystem = advancedSection.getBoolean("ticket-system", false)
                    channel.faqIntegration = advancedSection.getBoolean("faq-integration", false)
                }
                "trade" -> {
                    channel.requireKeywords = advancedSection.getBoolean("require-keywords", false)
                    channel.priceDetection = advancedSection.getBoolean("price-detection", false)
                    channel.itemLinking = advancedSection.getBoolean("item-linking", false)
                    channel.autoExpire = advancedSection.getInt("auto-expire", 0)
                }
                "event" -> {
                    // Event-specific features can be added here
                }
            }
        }

        return channel
    }

    /**
     * Load channel groups
     */
    private fun loadChannelGroups() {
        channelGroups.clear()

        val groupsSection = channelsConfig.getConfigurationSection("channel-groups")
        groupsSection?.getKeys(false)?.forEach { groupName ->
            val groupSection = groupsSection.getConfigurationSection(groupName)
            if (groupSection != null) {
                val channels = groupSection.getStringList("channels")
                val description = groupSection.getString("description", "") ?: ""
                channelGroups[groupName] = ChannelGroup(groupName, channels, description)
            }
        }
    }

    /**
     * Load auto-join settings
     */
    private fun loadAutoJoinSettings() {
        val autoJoinSection = channelsConfig.getConfigurationSection("auto-join")
        if (autoJoinSection != null) {
            newPlayerChannels.clear()
            newPlayerChannels.addAll(autoJoinSection.getStringList("new-players"))

            val permissionSection = autoJoinSection.getConfigurationSection("permission-based")
            permissionSection?.getKeys(false)?.forEach { permission ->
                val channels = permissionSection.getStringList(permission)
                permissionBasedChannels[permission] = channels.toMutableSet()
            }
        }
    }

    /**
     * Load channel switching settings
     */
    private fun loadSwitchingSettings() {
        val switchingSection = channelsConfig.getConfigurationSection("switching")
        if (switchingSection != null) {
            switchingEnabled = switchingSection.getBoolean("enabled", true)
            switchCommand = switchingSection.getString("command", "/ch") ?: "/ch"

            switchAliases.clear()
            switchAliases.addAll(switchingSection.getStringList("aliases"))

            val quickSwitchSection = switchingSection.getConfigurationSection("quick-switch")
            if (quickSwitchSection != null && quickSwitchSection.getBoolean("enabled", true)) {
                quickSwitchMap.clear()
                quickSwitchSection.getKeys(false).forEach { key ->
                    if (key != "enabled") {
                        val channel = quickSwitchSection.getString(key)
                        if (!channel.isNullOrEmpty()) {
                            quickSwitchMap[key] = channel
                        }
                    }
                }
            }

            rememberChannel = switchingSection.getBoolean("remember-channel", true)
            showListOnSwitch = switchingSection.getBoolean("show-list", true)
        }
    }

    /**
     * Load notification settings
     */
    private fun loadNotificationSettings() {
        val notificationsSection = channelsConfig.getConfigurationSection("notifications")
        if (notificationsSection != null) {
            val joinLeaveSection = notificationsSection.getConfigurationSection("join-leave")
            joinLeaveNotifications = joinLeaveSection?.getBoolean("enabled", false) ?: false

            val mentionsSection = notificationsSection.getConfigurationSection("mentions")
            mentionNotifications = mentionsSection?.getBoolean("enabled", true) ?: true

            val staffAlertsSection = notificationsSection.getConfigurationSection("staff-alerts")
            if (staffAlertsSection != null) {
                staffAlerts = staffAlertsSection.getBoolean("enabled", true)
                staffAlertKeywords.clear()
                staffAlertKeywords.addAll(staffAlertsSection.getStringList("keywords"))
            }
        }
    }

    /**
     * Load history settings
     */
    private fun loadHistorySettings() {
        val historySection = channelsConfig.getConfigurationSection("history")
        if (historySection != null) {
            historyEnabled = historySection.getBoolean("enabled", true)
            maxMessagesPerChannel = historySection.getInt("max-messages", 100)
            retentionHours = historySection.getInt("retention", 24)
            playerHistoryAccess = historySection.getBoolean("player-access", true)
            historyCommand = historySection.getString("command", "/ch history") ?: "/ch history"
        }
    }

    /**
     * Get a channel by name
     */
    fun getChannel(name: String): Channel? {
        return channels[name.lowercase()]
    }

    /**
     * Get all channels
     */
    fun getAllChannels(): Map<String, Channel> {
        return channels.toMap()
    }

    /**
     * Get channels a player can access
     */
    fun getAccessibleChannels(player: Player): List<Channel> {
        return channels.values.filter { channel ->
            channel.enabled && channel.hasPermission(player)
        }
    }

    /**
     * Get player's current channel
     */
    fun getPlayerChannel(player: Player): Channel? {
        val channelName = playerChannels[player.uniqueId]
        return if (channelName != null) getChannel(channelName) else getDefaultChannel()
    }

    /**
     * Get the default channel (usually "global")
     */
    fun getDefaultChannel(): Channel? {
        return channels.values.firstOrNull { it.name == "global" }
            ?: channels.values.firstOrNull { it.enabled }
    }

    /**
     * Set player's current channel
     */
    fun setPlayerChannel(player: Player, channelName: String): Boolean {
        val channel = getChannel(channelName) ?: return false

        if (!channel.enabled || !channel.hasPermission(player)) {
            return false
        }

        val oldChannel = getPlayerChannel(player)

        // Remove from old channel
        oldChannel?.let { old ->
            channelMembers[old.name]?.remove(player.uniqueId)
        }

        // Add to new channel
        playerChannels[player.uniqueId] = channel.name
        channelMembers[channel.name]?.add(player.uniqueId)

        // Update ChatService to keep both systems synchronized
        plugin.chatService.setChannel(player.uniqueId, channel.name)

        // Send notifications if enabled
        if (joinLeaveNotifications && oldChannel != null && oldChannel.name != channel.name) {
            sendChannelChangeNotification(player, oldChannel, channel)
        }

        return true
    }

    /**
     * Join a player to a channel (without leaving current)
     */
    fun joinChannel(player: Player, channelName: String): Boolean {
        val channel = getChannel(channelName) ?: return false

        if (!channel.enabled || !channel.hasPermission(player)) {
            return false
        }

        channelMembers[channel.name]?.add(player.uniqueId)
        return true
    }

    /**
     * Leave a channel
     */
    fun leaveChannel(player: Player, channelName: String): Boolean {
        val channel = getChannel(channelName) ?: return false

        channelMembers[channel.name]?.remove(player.uniqueId)

        // If this was their current channel, switch to default
        if (playerChannels[player.uniqueId] == channelName) {
            val defaultChannel = getDefaultChannel()
            if (defaultChannel != null) {
                setPlayerChannel(player, defaultChannel.name)
            }
        }

        return true
    }

    /**
     * Get players in a channel
     */
    fun getChannelMembers(channelName: String): Set<Player> {
        val memberUUIDs = channelMembers[channelName] ?: return emptySet()
        return memberUUIDs.mapNotNull { plugin.server.getPlayer(it) }.toSet()
    }

    /**
     * Get players in range for local channels
     */
    fun getPlayersInRange(player: Player, channel: Channel): Set<Player> {
        if (channel.isGlobal()) {
            return getChannelMembers(channel.name)
        }

        val location = player.location
        return getChannelMembers(channel.name).filter { otherPlayer ->
            otherPlayer.world == player.world &&
            location.distance(otherPlayer.location) <= channel.radius
        }.toSet()
    }

    /**
     * Check if a player can send messages in a channel
     */
    fun canSendMessage(player: Player, channel: Channel): Boolean {
        if (!channel.enabled || !channel.hasPermission(player)) {
            return false
        }

        // Check cooldown
        val chatUser = plugin.chatService.getUser(player)
        val lastMessageTime = chatUser?.getLastMessageTime(channel.name) ?: 0L

        return channel.canSendMessage(player, lastMessageTime)
    }

    /**
     * Auto-join player to appropriate channels
     */
    fun autoJoinPlayer(player: Player, isNewPlayer: Boolean = false) {
        // Join new player channels
        if (isNewPlayer) {
            newPlayerChannels.forEach { channelName ->
                joinChannel(player, channelName)
            }
        }

        // Join permission-based channels
        permissionBasedChannels.forEach { (permission, channels) ->
            if (player.hasPermission(permission)) {
                channels.forEach { channelName ->
                    joinChannel(player, channelName)
                }
            }
        }

        // Set default channel if not already set
        if (playerChannels[player.uniqueId] == null) {
            val defaultChannel = getDefaultChannel()
            if (defaultChannel != null) {
                setPlayerChannel(player, defaultChannel.name)
            }
        }
    }

    /**
     * Handle player disconnect
     */
    fun handlePlayerDisconnect(player: Player) {
        val playerId = player.uniqueId

        // Remove from all channel member lists
        channelMembers.values.forEach { members ->
            members.remove(playerId)
        }

        // Keep current channel if remember is enabled
        if (!rememberChannel) {
            playerChannels.remove(playerId)
        }
    }

    /**
     * Process channel-specific features for a message
     */
    fun processChannelFeatures(player: Player, message: String, channel: Channel): String {
        var processedMessage = message

        // Trade channel keyword requirements
        if (channel.name == "trade" && channel.requireKeywords) {
            val tradeKeywords = listOf("WTS", "WTB", "WTT", "SELLING", "BUYING", "TRADING")
            val hasKeyword = tradeKeywords.any { keyword ->
                processedMessage.uppercase().contains(keyword)
            }

            if (!hasKeyword) {
                player.sendMessage("§cTrade messages must include WTS (Want To Sell), WTB (Want To Buy), or WTT (Want To Trade)")
                return ""
            }
        }

        // Help channel staff notification
        if (channel.name == "help" && channel.autoNotifyStaff) {
            notifyStaffOfHelpRequest(player, processedMessage)
        }

        // Staff alert keyword detection
        if (staffAlerts) {
            staffAlertKeywords.forEach { keyword ->
                if (processedMessage.lowercase().contains(keyword.lowercase())) {
                    notifyStaffOfAlert(player, processedMessage, keyword, channel)
                }
            }
        }

        return processedMessage
    }

    /**
     * Send channel change notification
     */
    private fun sendChannelChangeNotification(player: Player, oldChannel: Channel, newChannel: Channel) {
        val format = channelsConfig.getString("notifications.switch.format",
            "<yellow>%player% switched from %oldchannel% to %newchannel%</yellow>")

        val message = format?.replace("%player%", player.name)
            ?.replace("%channel%", newChannel.getEffectiveDisplayName()) ?: ""

        // Send to both channels
        getChannelMembers(oldChannel.name).forEach { member ->
            member.sendMessage(plugin.formatService.formatMessage(message))
        }
        getChannelMembers(newChannel.name).forEach { member ->
            member.sendMessage(plugin.formatService.formatMessage(message))
        }
    }

    /**
     * Notify staff of help request
     */
    private fun notifyStaffOfHelpRequest(player: Player, message: String) {
        plugin.server.onlinePlayers.filter { staff ->
            staff.hasPermission("remmychat.channel.staff") ||
            staff.hasPermission("remmychat.staff")
        }.forEach { staff ->
            staff.sendMessage("§6[Help Alert] §e${player.name}: §f$message")
        }
    }

    /**
     * Notify staff of alert keyword
     */
    private fun notifyStaffOfAlert(player: Player, message: String, keyword: String, channel: Channel) {
        val format = channelsConfig.getString("notifications.staff-alerts.format",
            "<red>[Alert]</red> Keyword detected in %channel%: %message%")

        val alertMessage = format?.replace("%channel%", channel.getEffectiveDisplayName())
            ?.replace("%message%", message)
            ?.replace("%player%", player.name)
            ?.replace("%keyword%", keyword) ?: ""

        plugin.server.onlinePlayers.filter { staff ->
            staff.hasPermission("remmychat.staff.alerts")
        }.forEach { staff ->
            staff.sendMessage(plugin.formatService.formatMessage(alertMessage))
        }
    }

    /**
     * Save channel data to disk
     */
    fun saveChannelData() {
        try {
            channelsConfig.save(File(plugin.dataFolder, "channels.yml"))
            plugin.debugLog("Channel data saved successfully")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save channel data: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Cleanup cached data
     */
    fun cleanupCache() {
        plugin.debugLog("Cleaning up ChannelManager cache")
        // Clear any cached data that might need periodic cleanup
        // For now, this is a placeholder for future cache implementations
    }

    /**
     * Get channel statistics
     */
    fun getChannelStats(): Map<String, Any> {
        return mapOf(
            "totalChannels" to channels.size,
            "enabledChannels" to channels.values.count { it.enabled },
            "crossServerChannels" to channels.values.count { it.isCrossServer() },
            "localChannels" to channels.values.count { it.isLocal() },
            "totalMembers" to channelMembers.values.sumOf { it.size },
            "channelGroups" to channelGroups.size
        )
    }

    /**
     * Reload channel configuration
     */
    fun reload() {
        initialize()
        plugin.debugLog("ChannelManager reloaded")
    }

    /**
     * Get quick switch mapping
     */
    fun getQuickSwitchChannel(key: String): String? {
        return quickSwitchMap[key]
    }

    /**
     * Check if switching is enabled
     */
    fun isSwitchingEnabled(): Boolean = switchingEnabled

    /**
     * Get switch command
     */
    fun getSwitchCommand(): String = switchCommand

    /**
     * Get switch aliases
     */
    fun getSwitchAliases(): Set<String> = switchAliases

    /**
     * Check if should show list on switch
     */
    fun shouldShowListOnSwitch(): Boolean = showListOnSwitch

    /**
     * Channel group data class
     */
    data class ChannelGroup(
        val name: String,
        val channels: List<String>,
        val description: String
    )
}
