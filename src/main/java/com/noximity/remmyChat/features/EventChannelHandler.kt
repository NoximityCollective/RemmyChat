package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Handles event channel specific features including announcement mode, auto-broadcast, and scheduled messages
 */
class EventChannelHandler(private val plugin: RemmyChat) : Listener {

    private val scheduledExecutor = Executors.newScheduledThreadPool(2)

    // Announcement mode settings
    private var announcementModeEnabled = false
    private val allowedAnnouncers = mutableSetOf<UUID>()
    private val pendingAnnouncements = ConcurrentHashMap<String, PendingAnnouncement>()

    // Auto-broadcast settings
    private var autoBroadcastEnabled = false
    private var broadcastInterval = 300L // 5 minutes default
    private val broadcastMessages = mutableListOf<BroadcastMessage>()
    private var currentBroadcastIndex = 0
    private var broadcastTask: ScheduledFuture<*>? = null

    // Scheduled messages
    private var scheduledMessagesEnabled = true
    private val scheduledMessages = ConcurrentHashMap<String, ScheduledMessage>()
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

    // Event statistics
    private var announcementsSent = 0L
    private var broadcastsSent = 0L
    private var scheduledMessagesSent = 0L

    // Configuration
    private var requireApproval = false
    private var announcementCooldown = 60L // 1 minute
    private val playerLastAnnouncement = ConcurrentHashMap<UUID, Long>()

    fun initialize() {
        loadConfiguration()
        loadBroadcastMessages()
        loadScheduledMessages()
        setupAnnouncementMode()
        setupAutoBroadcast()
        setupScheduledMessages()

        // Register as listener
        Bukkit.getPluginManager().registerEvents(this, plugin)

        plugin.debugLog("EventChannelHandler initialized - Announcements: $announcementModeEnabled, AutoBroadcast: $autoBroadcastEnabled, Scheduled: $scheduledMessagesEnabled")
    }

    /**
     * Process an event channel message
     */
    fun processEventMessage(player: Player, channel: Channel, message: String): EventMessageResult {
        val result = EventMessageResult()

        // Check if announcement mode is enabled
        if (announcementModeEnabled) {
            // Only allowed announcers can post
            if (!allowedAnnouncers.contains(player.uniqueId) && !player.hasPermission("remmychat.event.announce")) {
                result.isValid = false
                result.reason = "Only designated announcers can post in announcement mode"
                return result
            }

            // Check cooldown
            if (!checkAnnouncementCooldown(player)) {
                result.isValid = false
                result.reason = "You must wait before making another announcement"
                return result
            }

            // Format as announcement
            result.processedMessage = formatAsAnnouncement(message, player)
            result.isAnnouncement = true

            // Update cooldown
            playerLastAnnouncement[player.uniqueId] = System.currentTimeMillis()
            announcementsSent++
        } else {
            result.processedMessage = message
        }

        result.isValid = true
        return result
    }

    /**
     * Create a scheduled message
     */
    fun createScheduledMessage(
        id: String,
        message: String,
        scheduledTime: LocalDateTime,
        recurring: Boolean = false,
        interval: Long = 0,
        createdBy: UUID
    ): ScheduledMessageResult {

        val now = LocalDateTime.now()
        if (scheduledTime.isBefore(now)) {
            return ScheduledMessageResult(false, "Cannot schedule messages in the past")
        }

        val scheduledMessage = ScheduledMessage(
            id = id,
            message = message,
            scheduledTime = scheduledTime,
            recurring = recurring,
            intervalMinutes = interval,
            createdBy = createdBy,
            createdAt = now,
            timesExecuted = 0,
            active = true
        )

        scheduledMessages[id] = scheduledMessage
        scheduleMessageExecution(scheduledMessage)

        return ScheduledMessageResult(true, "Message scheduled successfully", id)
    }

    /**
     * Create an immediate announcement
     */
    fun createAnnouncement(player: Player, message: String, urgent: Boolean = false): AnnouncementResult {
        if (!allowedAnnouncers.contains(player.uniqueId) && !player.hasPermission("remmychat.event.announce")) {
            return AnnouncementResult(false, "You don't have permission to create announcements")
        }

        if (!urgent && !checkAnnouncementCooldown(player)) {
            return AnnouncementResult(false, "You must wait before making another announcement")
        }

        val formattedMessage = formatAsAnnouncement(message, player)
        broadcastToEventChannel(formattedMessage)

        playerLastAnnouncement[player.uniqueId] = System.currentTimeMillis()
        announcementsSent++

        return AnnouncementResult(true, "Announcement sent successfully")
    }

    /**
     * Add a new broadcast message
     */
    fun addBroadcastMessage(message: String, weight: Int = 1): Boolean {
        val broadcastMessage = BroadcastMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            weight = weight,
            timesShown = 0,
            active = true
        )

        broadcastMessages.add(broadcastMessage)
        saveBroadcastMessages()

        plugin.debugLog("Added broadcast message: $message")
        return true
    }

    /**
     * Load configuration settings
     */
    private fun loadConfiguration() {
        val channelsConfig = plugin.configManager.getChannelsConfig()
        val eventSection = channelsConfig.getConfigurationSection("channels.event.event-features")

        if (eventSection != null) {
            announcementModeEnabled = eventSection.getBoolean("announcement-mode", false)
            autoBroadcastEnabled = eventSection.getBoolean("auto-broadcast", false)
            scheduledMessagesEnabled = eventSection.getBoolean("scheduled-messages", true)
            requireApproval = eventSection.getBoolean("require-approval", false)
            announcementCooldown = eventSection.getLong("announcement-cooldown", 60L)
            broadcastInterval = eventSection.getLong("broadcast-interval", 300L)
        }

        // Load allowed announcers
        val mainConfig = plugin.config
        val announcersList = mainConfig.getStringList("event.allowed-announcers")
        announcersList.forEach { uuidString ->
            try {
                allowedAnnouncers.add(UUID.fromString(uuidString))
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid UUID in allowed announcers: $uuidString")
            }
        }
    }

    /**
     * Load broadcast messages from configuration
     */
    private fun loadBroadcastMessages() {
        broadcastMessages.clear()

        val mainConfig = plugin.config
        val broadcastSection = mainConfig.getConfigurationSection("event.broadcasts")

        broadcastSection?.getKeys(false)?.forEach { messageId ->
            val messageSection = broadcastSection.getConfigurationSection(messageId)
            if (messageSection != null) {
                val message = messageSection.getString("message", "")
                val weight = messageSection.getInt("weight", 1)
                val active = messageSection.getBoolean("active", true)

                if (message.isNotEmpty() && active) {
                    broadcastMessages.add(BroadcastMessage(
                        id = messageId,
                        message = message,
                        weight = weight,
                        timesShown = 0,
                        active = active
                    ))
                }
            }
        }

        plugin.debugLog("Loaded ${broadcastMessages.size} broadcast messages")
    }

    /**
     * Load scheduled messages from configuration
     */
    private fun loadScheduledMessages() {
        scheduledMessages.clear()

        val mainConfig = plugin.config
        val scheduledSection = mainConfig.getConfigurationSection("event.scheduled-messages")

        scheduledSection?.getKeys(false)?.forEach { messageId ->
            val messageSection = scheduledSection.getConfigurationSection(messageId)
            if (messageSection != null) {
                try {
                    val message = messageSection.getString("message", "")
                    val timeString = messageSection.getString("time", "")
                    val recurring = messageSection.getBoolean("recurring", false)
                    val interval = messageSection.getLong("interval", 0)
                    val createdByString = messageSection.getString("created-by", "")
                    val active = messageSection.getBoolean("active", true)

                    if (message.isNotEmpty() && timeString.isNotEmpty() && active) {
                        val scheduledTime = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val createdBy = if (createdByString.isNotEmpty()) UUID.fromString(createdByString) else UUID.randomUUID()

                        val scheduledMessage = ScheduledMessage(
                            id = messageId,
                            message = message,
                            scheduledTime = scheduledTime,
                            recurring = recurring,
                            intervalMinutes = interval,
                            createdBy = createdBy,
                            createdAt = LocalDateTime.now(),
                            timesExecuted = 0,
                            active = active
                        )

                        scheduledMessages[messageId] = scheduledMessage
                        scheduleMessageExecution(scheduledMessage)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load scheduled message $messageId: ${e.message}")
                }
            }
        }

        plugin.debugLog("Loaded ${scheduledMessages.size} scheduled messages")
    }

    /**
     * Setup announcement mode
     */
    private fun setupAnnouncementMode() {
        if (announcementModeEnabled) {
            plugin.debugLog("Announcement mode enabled for event channel")
        }
    }

    /**
     * Setup auto-broadcast system
     */
    private fun setupAutoBroadcast() {
        if (autoBroadcastEnabled && broadcastMessages.isNotEmpty()) {
            broadcastTask = scheduledExecutor.scheduleAtFixedRate({
                try {
                    sendNextBroadcast()
                } catch (e: Exception) {
                    plugin.logger.warning("Error in auto-broadcast task: ${e.message}")
                }
            }, broadcastInterval, broadcastInterval, TimeUnit.SECONDS)

            plugin.debugLog("Auto-broadcast started with ${broadcastMessages.size} messages, interval: ${broadcastInterval}s")
        }
    }

    /**
     * Setup scheduled messages
     */
    private fun setupScheduledMessages() {
        if (scheduledMessagesEnabled) {
            // Scheduled messages are set up individually as they're loaded
            plugin.debugLog("Scheduled messages system enabled")
        }
    }

    /**
     * Check if player can make an announcement (cooldown check)
     */
    private fun checkAnnouncementCooldown(player: Player): Boolean {
        val lastAnnouncement = playerLastAnnouncement[player.uniqueId] ?: return true
        val currentTime = System.currentTimeMillis()
        val cooldownTime = announcementCooldown * 1000L

        return (currentTime - lastAnnouncement) >= cooldownTime
    }

    /**
     * Format message as announcement
     */
    private fun formatAsAnnouncement(message: String, player: Player): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return "📢 <bold><color:#FFD700>[ANNOUNCEMENT]</color></bold> <gray>[$timestamp]</gray> <white>$message</white>"
    }

    /**
     * Send next broadcast message
     */
    private fun sendNextBroadcast() {
        if (broadcastMessages.isEmpty()) return

        // Use weighted random selection
        val totalWeight = broadcastMessages.sumOf { it.weight }
        val random = (0 until totalWeight).random()
        var weightSum = 0

        for (broadcast in broadcastMessages) {
            weightSum += broadcast.weight
            if (random < weightSum) {
                sendBroadcastMessage(broadcast)
                break
            }
        }
    }

    /**
     * Send a specific broadcast message
     */
    private fun sendBroadcastMessage(broadcast: BroadcastMessage) {
        val formattedMessage = "🔔 <color:#00FF7F><bold>[AUTO-BROADCAST]</bold></color> <white>${broadcast.message}</white>"
        broadcastToEventChannel(formattedMessage)

        broadcast.timesShown++
        broadcastsSent++

        plugin.debugLog("Sent auto-broadcast: ${broadcast.message}")
    }

    /**
     * Schedule execution of a scheduled message
     */
    private fun scheduleMessageExecution(scheduledMessage: ScheduledMessage) {
        val now = LocalDateTime.now()
        val delay = java.time.Duration.between(now, scheduledMessage.scheduledTime).toMillis()

        if (delay > 0) {
            val task = scheduledExecutor.schedule({
                executeScheduledMessage(scheduledMessage)
            }, delay, TimeUnit.MILLISECONDS)

            scheduledTasks[scheduledMessage.id] = task
            plugin.debugLog("Scheduled message '${scheduledMessage.id}' will execute in ${delay}ms")
        }
    }

    /**
     * Execute a scheduled message
     */
    private fun executeScheduledMessage(scheduledMessage: ScheduledMessage) {
        if (!scheduledMessage.active) return

        val formattedMessage = "⏰ <color:#FF69B4><bold>[SCHEDULED]</bold></color> <white>${scheduledMessage.message}</white>"
        broadcastToEventChannel(formattedMessage)

        scheduledMessage.timesExecuted++
        scheduledMessagesSent++

        plugin.debugLog("Executed scheduled message: ${scheduledMessage.message}")

        // Handle recurring messages
        if (scheduledMessage.recurring && scheduledMessage.intervalMinutes > 0) {
            val nextTime = scheduledMessage.scheduledTime.plusMinutes(scheduledMessage.intervalMinutes)
            scheduledMessage.scheduledTime = nextTime
            scheduleMessageExecution(scheduledMessage)
        } else {
            // Mark as completed
            scheduledMessage.active = false
            scheduledTasks.remove(scheduledMessage.id)
        }
    }

    /**
     * Broadcast message to event channel
     */
    private fun broadcastToEventChannel(message: String) {
        val eventChannel = plugin.channelManager.getChannel("event")
        if (eventChannel != null) {
            val component = plugin.templateManager.processMessage(message, null, mapOf("channel" to "event"))

            // Send to all players who can access the event channel
            Bukkit.getOnlinePlayers().forEach { player ->
                if (eventChannel.hasPermission(player)) {
                    player.sendMessage(component)
                }
            }

            // Send to Discord if enabled
            if (plugin.isDiscordSRVEnabled) {
                plugin.advancedDiscordIntegration.sendWebhookMessage("event", "Server", message)
            }
        }
    }

    /**
     * Save broadcast messages to configuration
     */
    private fun saveBroadcastMessages() {
        // This would save the current broadcast messages to configuration
        // Implementation depends on your configuration saving mechanism
        plugin.debugLog("Broadcast messages saved")
    }

    /**
     * Cancel a scheduled message
     */
    fun cancelScheduledMessage(messageId: String): Boolean {
        val scheduledMessage = scheduledMessages[messageId]
        if (scheduledMessage != null) {
            scheduledMessage.active = false
            scheduledTasks[messageId]?.cancel(false)
            scheduledTasks.remove(messageId)
            scheduledMessages.remove(messageId)

            plugin.debugLog("Cancelled scheduled message: $messageId")
            return true
        }
        return false
    }

    /**
     * Get event channel statistics
     */
    fun getEventStatistics(): EventStatistics {
        return EventStatistics(
            announcementModeEnabled = announcementModeEnabled,
            autoBroadcastEnabled = autoBroadcastEnabled,
            scheduledMessagesEnabled = scheduledMessagesEnabled,
            announcementsSent = announcementsSent,
            broadcastsSent = broadcastsSent,
            scheduledMessagesSent = scheduledMessagesSent,
            activeBroadcasts = broadcastMessages.size,
            activeScheduledMessages = scheduledMessages.values.count { it.active },
            allowedAnnouncers = allowedAnnouncers.size
        )
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Could send welcome announcement or scheduled message to joining players
    }

    fun shutdown() {
        broadcastTask?.cancel(false)
        scheduledTasks.values.forEach { it.cancel(false) }
        scheduledExecutor.shutdown()
    }

    // Data classes
    data class EventMessageResult(
        var isValid: Boolean = false,
        var reason: String = "",
        var processedMessage: String = "",
        var isAnnouncement: Boolean = false
    )

    data class ScheduledMessageResult(
        val success: Boolean,
        val message: String,
        val messageId: String? = null
    )

    data class AnnouncementResult(
        val success: Boolean,
        val message: String
    )

    data class BroadcastMessage(
        val id: String,
        val message: String,
        val weight: Int,
        var timesShown: Int,
        var active: Boolean
    )

    data class ScheduledMessage(
        val id: String,
        val message: String,
        var scheduledTime: LocalDateTime,
        val recurring: Boolean,
        val intervalMinutes: Long,
        val createdBy: UUID,
        val createdAt: LocalDateTime,
        var timesExecuted: Int,
        var active: Boolean
    )

    data class PendingAnnouncement(
        val id: String,
        val message: String,
        val submittedBy: UUID,
        val submittedAt: Long,
        var approved: Boolean = false,
        var approvedBy: UUID? = null
    )

    data class EventStatistics(
        val announcementModeEnabled: Boolean,
        val autoBroadcastEnabled: Boolean,
        val scheduledMessagesEnabled: Boolean,
        val announcementsSent: Long,
        val broadcastsSent: Long,
        val scheduledMessagesSent: Long,
        val activeBroadcasts: Int,
        val activeScheduledMessages: Int,
        val allowedAnnouncers: Int
    )
}
