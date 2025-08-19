package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager for all advanced features
 * Coordinates feature interactions and provides unified access
 */
class FeatureManager(private val plugin: RemmyChat) {

    // Feature handlers
    private lateinit var tradeChannelHandler: TradeChannelHandler
    private lateinit var helpChannelHandler: HelpChannelHandler
    private lateinit var groupBehaviorManager: GroupBehaviorManager
    private lateinit var advancedDiscordFeatures: AdvancedDiscordFeatures
    private lateinit var databaseSecurityManager: DatabaseSecurityManager
    private lateinit var eventChannelHandler: EventChannelHandler

    // Feature status tracking
    private val featureStatus = ConcurrentHashMap<String, Boolean>()
    private val featureMetrics = ConcurrentHashMap<String, Map<String, Any>>()

    // Integration flags
    private var tradeChannelEnabled = false
    private var helpChannelEnabled = false
    private var groupBehaviorEnabled = false
    private var advancedDiscordEnabled = false
    private var databaseSecurityEnabled = false
    private var eventChannelEnabled = false

    fun initialize() {
        // Initialize feature handlers
        tradeChannelHandler = plugin.tradeChannelHandler
        helpChannelHandler = plugin.helpChannelHandler
        groupBehaviorManager = plugin.groupBehaviorManager
        advancedDiscordFeatures = plugin.advancedDiscordFeatures
        databaseSecurityManager = plugin.databaseSecurityManager
        eventChannelHandler = plugin.eventChannelHandler

        // Check feature availability
        checkFeatureAvailability()

        // Update feature status
        updateFeatureStatus()

        plugin.debugLog("FeatureManager initialized with ${getEnabledFeatures().size} active features")
    }

    /**
     * Process a chat message through all applicable features
     */
    fun processMessage(player: Player, channel: Channel, message: String): ProcessedMessageResult {
        val result = ProcessedMessageResult(
            originalMessage = message,
            processedMessage = message,
            isValid = true
        )

        try {
            // Group behavior checks (run first as they can block messages)
            if (groupBehaviorEnabled) {
                val accessResult = groupBehaviorManager.canAccessChannel(player, channel)
                if (!accessResult.allowed) {
                    result.isValid = false
                    result.blockReason = accessResult.reason
                    return result
                }

                val lengthResult = groupBehaviorManager.validateMessageLength(player, message)
                if (!lengthResult.valid) {
                    result.isValid = false
                    result.blockReason = lengthResult.reason
                    return result
                }

                val mentionResult = groupBehaviorManager.processMentions(player, channel, message)
                if (!mentionResult.isValid) {
                    result.isValid = false
                    result.blockReason = mentionResult.reason
                    return result
                }

                result.processedMessage = mentionResult.processedMessage
                result.mentionData = mentionResult
            }

            // Channel-specific processing
            when (channel.name.lowercase()) {
                "trade" -> {
                    if (tradeChannelEnabled) {
                        val tradeResult = tradeChannelHandler.processTradeMessage(player, channel, result.processedMessage)
                        if (!tradeResult.isValid) {
                            result.isValid = false
                            result.blockReason = tradeResult.errorMessage
                            return result
                        }
                        result.processedMessage = tradeResult.processedMessage
                        result.tradeData = tradeResult
                    }
                }

                "help" -> {
                    if (helpChannelEnabled) {
                        val helpResult = helpChannelHandler.processHelpMessage(player, channel, result.processedMessage)
                        result.processedMessage = helpResult.processedMessage
                        result.helpData = helpResult

                        // Add FAQ suggestions to response
                        if (helpResult.faqSuggestion != null && helpResult.suggestedResponse != null) {
                            result.additionalResponses.add(helpResult.suggestedResponse!!)
                        }

                        // Add ticket suggestion
                        if (helpResult.ticketSuggested && helpResult.ticketCreateMessage != null) {
                            result.additionalResponses.add(helpResult.ticketCreateMessage!!)
                        }
                    }
                }

                "event" -> {
                    if (eventChannelEnabled) {
                        val eventResult = eventChannelHandler.processEventMessage(player, channel, result.processedMessage)
                        if (!eventResult.isValid) {
                            result.isValid = false
                            result.blockReason = eventResult.reason
                            return result
                        }
                        result.processedMessage = eventResult.processedMessage
                        result.eventData = eventResult
                    }
                }
            }

            // Database security processing
            if (databaseSecurityEnabled) {
                // Audit the message
                databaseSecurityManager.auditOperation(
                    "CHAT_MESSAGE",
                    "message_history",
                    player.uniqueId.toString(),
                    mapOf(
                        "channel" to channel.name,
                        "message_length" to message.length,
                        "has_mentions" to (result.mentionData?.mentionedPlayers?.isNotEmpty() == true)
                    )
                )

                // Encrypt sensitive parts if needed
                if (message.contains("password") || message.contains("token")) {
                    result.processedMessage = databaseSecurityManager.encryptData(result.processedMessage, "message")
                }
            }

            // Discord integration
            if (advancedDiscordEnabled) {
                advancedDiscordFeatures.incrementMessagesSent()

                // Send to Discord webhook if configured
                advancedDiscordFeatures.sendWebhookMessage(
                    channel.name,
                    player.name,
                    result.processedMessage
                )
            }

            result.success = true

        } catch (e: Exception) {
            plugin.logger.warning("Error processing message through features: ${e.message}")
            result.isValid = true // Don't block messages due to feature errors
            result.processedMessage = message // Use original message
        }

        return result
    }

    /**
     * Handle player joining - trigger relevant features
     */
    fun handlePlayerJoin(player: Player) {
        if (groupBehaviorEnabled) {
            // Check for role sync if Discord integration is enabled
            if (advancedDiscordEnabled) {
                advancedDiscordFeatures.syncPlayerRole(player)
            }
        }

        if (databaseSecurityEnabled) {
            databaseSecurityManager.auditOperation(
                "PLAYER_JOIN",
                "players",
                player.uniqueId.toString(),
                mapOf("player_name" to player.name)
            )
        }
    }

    /**
     * Handle player leaving
     */
    fun handlePlayerQuit(player: Player) {
        if (databaseSecurityEnabled) {
            databaseSecurityManager.auditOperation(
                "PLAYER_QUIT",
                "players",
                player.uniqueId.toString(),
                mapOf("player_name" to player.name)
            )
        }
    }

    /**
     * Create a support ticket through help channel handler
     */
    fun createSupportTicket(player: Player, title: String, description: String): TicketCreationResult {
        if (!helpChannelEnabled) {
            return TicketCreationResult(false, "Help channel features are disabled")
        }

        val result = helpChannelHandler.createTicket(player, title, description)

        if (result.success && databaseSecurityEnabled) {
            databaseSecurityManager.auditOperation(
                "TICKET_CREATE",
                "support_tickets",
                player.uniqueId.toString(),
                mapOf(
                    "ticket_id" to (result.ticketId ?: "unknown"),
                    "title" to title
                )
            )
        }

        return TicketCreationResult(result.success, result.message, result.ticketId)
    }

    /**
     * Create a trade post
     */
    fun createTradePost(player: Player, message: String, expireHours: Int): TradePostResult {
        if (!tradeChannelEnabled) {
            return TradePostResult(false, "Trade channel features are disabled")
        }

        val channel = plugin.channelManager.getChannel("trade")
        if (channel == null) {
            return TradePostResult(false, "Trade channel not found")
        }

        // Set auto-expire temporarily
        val originalExpire = channel.autoExpire
        channel.autoExpire = expireHours * 3600 // Convert to seconds

        val result = tradeChannelHandler.processTradeMessage(player, channel, message)

        // Restore original setting
        channel.autoExpire = originalExpire

        return TradePostResult(result.isValid, result.errorMessage, result.tradePostId)
    }

    /**
     * Create a scheduled announcement
     */
    fun createScheduledAnnouncement(
        player: Player,
        message: String,
        scheduledTime: LocalDateTime,
        recurring: Boolean = false,
        intervalMinutes: Long = 0
    ): ScheduledAnnouncementResult {
        if (!eventChannelEnabled) {
            return ScheduledAnnouncementResult(false, "Event channel features are disabled")
        }

        val messageId = UUID.randomUUID().toString()
        val result = eventChannelHandler.createScheduledMessage(
            messageId,
            message,
            scheduledTime,
            recurring,
            intervalMinutes,
            player.uniqueId
        )

        if (result.success && databaseSecurityEnabled) {
            databaseSecurityManager.auditOperation(
                "SCHEDULED_ANNOUNCEMENT",
                "scheduled_messages",
                player.uniqueId.toString(),
                mapOf(
                    "message_id" to messageId,
                    "scheduled_time" to scheduledTime.toString(),
                    "recurring" to recurring
                )
            )
        }

        return ScheduledAnnouncementResult(result.success, result.message, result.messageId)
    }

    /**
     * Send moderation alert to Discord
     */
    fun sendModerationAlert(type: String, player: String, moderator: String?, reason: String?, action: String) {
        if (advancedDiscordEnabled) {
            advancedDiscordFeatures.sendModerationAlert(type, player, moderator, reason, action)
        }

        if (databaseSecurityEnabled) {
            databaseSecurityManager.auditOperation(
                "MODERATION_ACTION",
                "moderation_log",
                null,
                mapOf(
                    "type" to type,
                    "target_player" to player,
                    "moderator" to (moderator ?: "system"),
                    "action" to action,
                    "reason" to (reason ?: "none")
                )
            )
        }
    }

    /**
     * Perform security check on database
     */
    fun performSecurityCheck(): SecurityCheckSummary {
        if (!databaseSecurityEnabled) {
            return SecurityCheckSummary(
                enabled = false,
                riskLevel = "UNKNOWN",
                issues = listOf("Database security features are disabled"),
                recommendations = listOf("Enable database security features")
            )
        }

        val checkResult = databaseSecurityManager.performSecurityCheck()
        return SecurityCheckSummary(
            enabled = true,
            riskLevel = checkResult.riskLevel,
            issues = checkResult.issues,
            recommendations = checkResult.recommendations,
            lastCheck = checkResult.lastCheck
        )
    }

    /**
     * Check which features are available and enabled
     */
    private fun checkFeatureAvailability() {
        val channelsConfig = plugin.configManager.getChannelsConfig()

        // Check if trade channel has advanced features enabled
        tradeChannelEnabled = channelsConfig.getConfigurationSection("channels.trade.trade-features") != null

        // Check if help channel has advanced features enabled
        helpChannelEnabled = channelsConfig.getConfigurationSection("channels.help.help-features") != null

        // Check if event channel has advanced features enabled
        eventChannelEnabled = channelsConfig.getConfigurationSection("channels.event.event-features") != null

        // Check if group behaviors are enabled
        val groupsConfig = plugin.configManager.getGroupsConfig()
        groupBehaviorEnabled = groupsConfig.getBoolean("behaviors.channel-access.enabled", false) ||
                              groupsConfig.getBoolean("behaviors.message-limits.enabled", false) ||
                              groupsConfig.getBoolean("behaviors.mention-restrictions.enabled", false)

        // Check if advanced Discord features are enabled
        val discordConfig = plugin.configManager.getDiscordConfig()
        advancedDiscordEnabled = discordConfig.getBoolean("advanced.webhooks.enabled", false) ||
                                 discordConfig.getBoolean("advanced.role-sync.enabled", false) ||
                                 discordConfig.getBoolean("advanced.statistics.enabled", false)

        // Check if database security is enabled
        val databaseConfig = plugin.configManager.getDatabaseConfig()
        databaseSecurityEnabled = databaseConfig.getBoolean("security.encryption.enabled", false) ||
                                  databaseConfig.getBoolean("security.audit.enabled", false) ||
                                  databaseConfig.getBoolean("backup.location.remote.enabled", false)
    }

    /**
     * Update feature status for monitoring
     */
    private fun updateFeatureStatus() {
        featureStatus["trade_channel"] = tradeChannelEnabled
        featureStatus["help_channel"] = helpChannelEnabled
        featureStatus["event_channel"] = eventChannelEnabled
        featureStatus["group_behavior"] = groupBehaviorEnabled
        featureStatus["advanced_discord"] = advancedDiscordEnabled
        featureStatus["database_security"] = databaseSecurityEnabled

        // Update metrics
        if (tradeChannelEnabled) {
            featureMetrics["trade_channel"] = tradeChannelHandler.getTradeStatistics().let {
                mapOf(
                    "active_posts" to it.activePosts,
                    "recent_posts" to it.recentPosts,
                    "trading_players" to it.totalPlayersTrading
                )
            }
        }

        if (helpChannelEnabled) {
            // Help channel metrics would go here
        }

        if (eventChannelEnabled) {
            featureMetrics["event_channel"] = eventChannelHandler.getEventStatistics().let {
                mapOf(
                    "announcements_sent" to it.announcementsSent,
                    "broadcasts_sent" to it.broadcastsSent,
                    "scheduled_messages_sent" to it.scheduledMessagesSent,
                    "active_broadcasts" to it.activeBroadcasts,
                    "active_scheduled" to it.activeScheduledMessages
                )
            }
        }

        if (groupBehaviorEnabled) {
            featureMetrics["group_behavior"] = groupBehaviorManager.getChannelAccessStats()
        }

        if (advancedDiscordEnabled) {
            featureMetrics["advanced_discord"] = advancedDiscordFeatures.getStatistics()
        }

        if (databaseSecurityEnabled) {
            featureMetrics["database_security"] = databaseSecurityManager.getSecurityStatistics()
        }
    }

    /**
     * Get list of enabled features
     */
    fun getEnabledFeatures(): List<String> {
        return featureStatus.filter { it.value }.keys.toList()
    }

    /**
     * Get feature metrics
     */
    fun getFeatureMetrics(): Map<String, Map<String, Any>> {
        updateFeatureStatus() // Refresh metrics
        return featureMetrics.toMap()
    }

    /**
     * Get comprehensive feature status report
     */
    fun getFeatureReport(): FeatureReport {
        updateFeatureStatus()

        return FeatureReport(
            enabledFeatures = getEnabledFeatures(),
            featureMetrics = getFeatureMetrics(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun shutdown() {
        // Shutdown all feature handlers
        if (::tradeChannelHandler.isInitialized) tradeChannelHandler.shutdown()
        if (::helpChannelHandler.isInitialized) helpChannelHandler.shutdown()
        if (::advancedDiscordFeatures.isInitialized) advancedDiscordFeatures.shutdown()
        if (::databaseSecurityManager.isInitialized) databaseSecurityManager.shutdown()
        if (::eventChannelHandler.isInitialized) eventChannelHandler.shutdown()
    }

    // Data classes
    data class ProcessedMessageResult(
        val originalMessage: String,
        var processedMessage: String,
        var isValid: Boolean,
        var blockReason: String = "",
        var success: Boolean = false,
        var mentionData: GroupBehaviorManager.MentionProcessResult? = null,
        var tradeData: TradeChannelHandler.TradeMessageResult? = null,
        var helpData: HelpChannelHandler.HelpMessageResult? = null,
        var eventData: EventChannelHandler.EventMessageResult? = null,
        val additionalResponses: MutableList<Component> = mutableListOf()
    )

    data class TicketCreationResult(
        val success: Boolean,
        val message: String,
        val ticketId: String? = null
    )

    data class TradePostResult(
        val success: Boolean,
        val message: String,
        val tradePostId: String? = null
    )

    data class ScheduledAnnouncementResult(
        val success: Boolean,
        val message: String,
        val messageId: String? = null
    )

    data class SecurityCheckSummary(
        val enabled: Boolean,
        val riskLevel: String,
        val issues: List<String>,
        val recommendations: List<String>,
        val lastCheck: Long = System.currentTimeMillis()
    )

    data class FeatureReport(
        val enabledFeatures: List<String>,
        val featureMetrics: Map<String, Map<String, Any>>,
        val lastUpdated: Long
    )
}
