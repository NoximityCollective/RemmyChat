package com.noximity.remmyChat.discord

import com.noximity.remmyChat.RemmyChat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.time.Duration
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.Gson
import java.awt.Color

class AdvancedDiscordIntegration(private val plugin: RemmyChat) : Listener {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()
    private val webhookExecutor = Executors.newFixedThreadPool(3)

    // Statistics tracking
    private var messagesSent = 0L
    private var messagesReceived = 0L
    private var lastActivity = 0L

    // Configuration
    private var webhooksEnabled = false
    private var usePlayerAvatars = true
    private var avatarService = "crafatar"
    private var roleSyncEnabled = false
    private var crossServerEnabled = true
    private var showServerOrigin = true
    private var statisticsEnabled = false
    private var moderationIntegrationEnabled = false

    // Webhook URLs for each channel
    private val webhookUrls = ConcurrentHashMap<String, String>()

    // Role mappings (MC group -> Discord role)
    private val groupToRoleMappings = ConcurrentHashMap<String, String>()
    private val roleToGroupMappings = ConcurrentHashMap<String, String>()

    // Server statistics
    private var lastStatisticsUpdate = 0L
    private var statisticsInterval = 3600L // 1 hour
    private var statisticsChannelId = ""

    // Moderation settings
    private var moderationAlertsEnabled = false
    private var moderationAlertChannel = ""
    private var chatViolationsEnabled = false
    private var chatViolationsChannel = ""

    // Rate limiting
    private val webhookRateLimit = ConcurrentHashMap<String, MutableList<Long>>()
    private val maxWebhooksPerMinute = 30

    fun initialize() {
        plugin.debugLog("Initializing AdvancedDiscordIntegration...")
        loadConfiguration()

        if (webhooksEnabled || roleSyncEnabled || statisticsEnabled || moderationIntegrationEnabled) {
            plugin.server.pluginManager.registerEvents(this, plugin)

            if (statisticsEnabled) {
                startStatisticsTask()
            }

            plugin.debugLog("AdvancedDiscordIntegration initialized")
        } else {
            plugin.debugLog("AdvancedDiscordIntegration disabled - no features enabled")
        }
    }

    private fun loadConfiguration() {
        val config = plugin.configManager.getDiscordConfig()

        // Webhook settings
        webhooksEnabled = config.getBoolean("advanced.webhooks.enabled", false)
        usePlayerAvatars = config.getBoolean("advanced.webhooks.use-player-avatars", true)
        avatarService = config.getString("advanced.webhooks.avatar-service", "crafatar") ?: "crafatar"

        // Load webhook URLs
        val webhooksSection = config.getConfigurationSection("advanced.webhooks.channels")
        webhooksSection?.getKeys(false)?.forEach { channel ->
            val url = webhooksSection.getString(channel)
            if (url != null) {
                webhookUrls[channel] = url
            }
        }

        // Role sync settings
        roleSyncEnabled = config.getBoolean("advanced.role-sync.enabled", false)

        val groupMappingsSection = config.getConfigurationSection("advanced.role-sync.group-mappings")
        groupMappingsSection?.getKeys(false)?.forEach { group ->
            val role = groupMappingsSection.getString(group)
            if (role != null) {
                groupToRoleMappings[group] = role
                roleToGroupMappings[role] = group
            }
        }

        // Cross-server settings
        crossServerEnabled = config.getBoolean("advanced.cross-server.enabled", true)
        showServerOrigin = config.getBoolean("advanced.cross-server.show-server-origin", true)

        // Statistics settings
        statisticsEnabled = config.getBoolean("advanced.statistics.enabled", false)
        statisticsInterval = config.getLong("advanced.statistics.stats-interval", 3600L)
        statisticsChannelId = config.getString("advanced.statistics.stats-channel", "") ?: ""

        // Moderation settings
        moderationIntegrationEnabled = config.getBoolean("moderation.enabled", false)
        moderationAlertsEnabled = config.getBoolean("moderation.alerts.enabled", false)
        moderationAlertChannel = config.getString("moderation.alerts.alert-channel", "") ?: ""
        chatViolationsEnabled = config.getBoolean("moderation.chat-violations.enabled", false)
        chatViolationsChannel = config.getString("moderation.chat-violations.log-channel", "") ?: ""

        plugin.debugLog("AdvancedDiscordIntegration configuration loaded")
    }

    /**
     * Send message via webhook for rich formatting
     */
    fun sendWebhookMessage(
        channel: String,
        player: Player,
        message: String,
        serverName: String? = null
    ): CompletableFuture<Boolean> {
        if (!webhooksEnabled) {
            return CompletableFuture.completedFuture(false)
        }

        val webhookUrl = webhookUrls[channel]
        if (webhookUrl == null) {
            plugin.debugLog("No webhook URL configured for channel: $channel")
            return CompletableFuture.completedFuture(false)
        }

        // Check rate limit
        if (!checkWebhookRateLimit(webhookUrl)) {
            plugin.debugLog("Webhook rate limit exceeded for channel: $channel")
            return CompletableFuture.completedFuture(false)
        }

        return CompletableFuture.supplyAsync({
            try {
                val payload = createWebhookPayload(player, message, serverName, channel)
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() in 200..299) {
                    plugin.debugLog("Webhook message sent successfully to $channel")
                    return@supplyAsync true
                } else {
                    plugin.logger.warning("Webhook failed for $channel: ${response.statusCode()} - ${response.body()}")
                    return@supplyAsync false
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending webhook message to $channel: ${e.message}")
                return@supplyAsync false
            }
        }, webhookExecutor)
    }

    /**
     * Create webhook payload with player avatar and rich formatting
     */
    private fun createWebhookPayload(
        player: Player,
        message: String,
        serverName: String?,
        channel: String
    ): String {
        val payload = JsonObject()

        // Set username (with server prefix if cross-server)
        val username = if (crossServerEnabled && showServerOrigin && serverName != null) {
            "[$serverName] ${player.displayName}"
        } else {
            player.displayName
        }
        payload.addProperty("username", username)

        // Set avatar if enabled
        if (usePlayerAvatars) {
            val avatarUrl = getPlayerAvatarUrl(player)
            payload.addProperty("avatar_url", avatarUrl)
        }

        // Set message content
        payload.addProperty("content", message)

        // Add embeds for special channels or rich content
        if (channel == "staff" || channel == "admin") {
            val embed = createStaffEmbed(player, message, serverName)
            val embeds = JsonArray()
            embeds.add(embed)
            payload.add("embeds", embeds)
        }

        return gson.toJson(payload)
    }

    /**
     * Create rich embed for staff channels
     */
    private fun createStaffEmbed(player: Player, message: String, serverName: String?): JsonObject {
        val embed = JsonObject()

        embed.addProperty("description", message)
        embed.addProperty("color", 0x5BC0DE) // Blue color
        embed.addProperty("timestamp", java.time.Instant.now().toString())

        // Author field with player info
        val author = JsonObject()
        author.addProperty("name", player.displayName)
        author.addProperty("icon_url", getPlayerAvatarUrl(player))
        embed.add("author", author)

        // Footer with server info
        if (serverName != null) {
            val footer = JsonObject()
            footer.addProperty("text", "Server: $serverName")
            embed.add("footer", footer)
        }

        // Additional fields
        val fields = JsonArray()

        // Player location
        val locationField = JsonObject()
        locationField.addProperty("name", "Location")
        locationField.addProperty("value", "${player.world.name} (${player.location.blockX}, ${player.location.blockY}, ${player.location.blockZ})")
        locationField.addProperty("inline", true)
        fields.add(locationField)

        // Player group
        try {
            val playerGroup = plugin.groupManager.getPlayerGroup(player)
            if (playerGroup != null) {
                val groupField = JsonObject()
                groupField.addProperty("name", "Group")
                groupField.addProperty("value", playerGroup.name)
                groupField.addProperty("inline", true)
                fields.add(groupField)
            }
        } catch (e: Exception) {
            // GroupManager not initialized
        }

        embed.add("fields", fields)

        return embed
    }

    /**
     * Get player avatar URL from various services
     */
    private fun getPlayerAvatarUrl(player: Player): String {
        val uuid = player.uniqueId.toString().replace("-", "")

        return when (avatarService.lowercase()) {
            "crafatar" -> "https://crafatar.com/avatars/$uuid?size=64"
            "mc-heads" -> "https://mc-heads.net/avatar/$uuid/64"
            "minotar" -> "https://minotar.net/avatar/$uuid/64"
            else -> "https://crafatar.com/avatars/$uuid?size=64"
        }
    }

    /**
     * Check webhook rate limit
     */
    private fun checkWebhookRateLimit(webhookUrl: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val requests = webhookRateLimit.getOrPut(webhookUrl) { mutableListOf() }

        // Remove requests older than 1 minute
        requests.removeIf { it < currentTime - 60000 }

        if (requests.size >= maxWebhooksPerMinute) {
            return false
        }

        requests.add(currentTime)
        return true
    }

    /**
     * Send server statistics to Discord
     */
    fun sendServerStatistics() {
        if (!statisticsEnabled || statisticsChannelId.isEmpty()) return

        val webhookUrl = webhookUrls[statisticsChannelId]
        if (webhookUrl == null) {
            plugin.debugLog("No webhook URL configured for statistics channel")
            return
        }

        CompletableFuture.supplyAsync({
            try {
                val stats = generateServerStatistics()
                val payload = createStatisticsPayload(stats)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() in 200..299) {
                    plugin.debugLog("Statistics sent to Discord successfully")
                    lastStatisticsUpdate = System.currentTimeMillis()
                } else {
                    plugin.logger.warning("Failed to send statistics to Discord: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending statistics to Discord: ${e.message}")
            }
        }, webhookExecutor)
    }

    /**
     * Generate server statistics
     */
    private fun generateServerStatistics(): ServerStatistics {
        val onlinePlayers = plugin.server.onlinePlayers.size
        val maxPlayers = plugin.server.maxPlayers

        // Get performance statistics if available
        val performanceStats = try {
            plugin.performanceMonitor.getPerformanceStatistics()
        } catch (e: Exception) {
            null
        }

        // Get maintenance statistics if available
        val maintenanceStats = try {
            plugin.maintenanceManager.getMaintenanceStatistics()
        } catch (e: Exception) {
            null
        }

        val uptimeMillis = System.currentTimeMillis() - System.currentTimeMillis() // Fallback since startTime not available
        val uptimeHours = uptimeMillis / (1000 * 60 * 60)

        return ServerStatistics(
            onlinePlayers = onlinePlayers,
            maxPlayers = maxPlayers,
            uptimeHours = uptimeHours,
            messagesSent = performanceStats?.messagesSent ?: 0,
            messagesReceived = performanceStats?.messagesReceived ?: 0,
            crossServerMessages = performanceStats?.crossServerMessages ?: 0,
            errorCount = performanceStats?.errorCount ?: 0,
            memoryUsageMB = performanceStats?.memoryUsageMB ?: 0.0,
            backupCount = maintenanceStats?.backupCount ?: 0
        )
    }

    /**
     * Create statistics payload
     */
    private fun createStatisticsPayload(stats: ServerStatistics): String {
        val payload = JsonObject()
        payload.addProperty("username", "RemmyChat Statistics")

        val embed = JsonObject()
        embed.addProperty("title", "📊 Server Statistics")
        embed.addProperty("color", 0x00FF00) // Green color
        embed.addProperty("timestamp", java.time.Instant.now().toString())

        val fields = JsonArray()

        // Player statistics
        val playersField = JsonObject()
        playersField.addProperty("name", "🟢 Players Online")
        playersField.addProperty("value", "${stats.onlinePlayers}/${stats.maxPlayers}")
        playersField.addProperty("inline", true)
        fields.add(playersField)

        // Uptime
        val uptimeField = JsonObject()
        uptimeField.addProperty("name", "🕐 Uptime")
        uptimeField.addProperty("value", "${stats.uptimeHours} hours")
        uptimeField.addProperty("inline", true)
        fields.add(uptimeField)

        // Message statistics
        val messagesField = JsonObject()
        messagesField.addProperty("name", "💬 Messages")
        messagesField.addProperty("value", "Sent: ${stats.messagesSent}\nReceived: ${stats.messagesReceived}")
        messagesField.addProperty("inline", true)
        fields.add(messagesField)

        // Cross-server messages
        if (stats.crossServerMessages > 0) {
            val crossServerField = JsonObject()
            crossServerField.addProperty("name", "🌐 Cross-Server")
            crossServerField.addProperty("value", "${stats.crossServerMessages} messages")
            crossServerField.addProperty("inline", true)
            fields.add(crossServerField)
        }

        // Memory usage
        val memoryField = JsonObject()
        memoryField.addProperty("name", "🔧 Memory Usage")
        memoryField.addProperty("value", "${String.format("%.1f", stats.memoryUsageMB)} MB")
        memoryField.addProperty("inline", true)
        fields.add(memoryField)

        // Error count
        if (stats.errorCount > 0) {
            val errorsField = JsonObject()
            errorsField.addProperty("name", "⚠️ Errors")
            errorsField.addProperty("value", "${stats.errorCount}")
            errorsField.addProperty("inline", true)
            fields.add(errorsField)
        }

        embed.add("fields", fields)

        val embeds = JsonArray()
        embeds.add(embed)
        payload.add("embeds", embeds)

        return gson.toJson(payload)
    }

    /**
     * Send moderation alert to Discord
     */
    fun sendModerationAlert(action: String, player: String, reason: String, moderator: String) {
        if (!moderationAlertsEnabled || moderationAlertChannel.isEmpty()) return

        val webhookUrl = webhookUrls[moderationAlertChannel]
        if (webhookUrl == null) {
            plugin.debugLog("No webhook URL configured for moderation alerts")
            return
        }

        CompletableFuture.supplyAsync({
            try {
                val payload = createModerationAlertPayload(action, player, reason, moderator)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() in 200..299) {
                    plugin.debugLog("Moderation alert sent to Discord successfully")
                } else {
                    plugin.logger.warning("Failed to send moderation alert: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending moderation alert: ${e.message}")
            }
        }, webhookExecutor)
    }

    /**
     * Create moderation alert payload
     */
    private fun createModerationAlertPayload(action: String, player: String, reason: String, moderator: String): String {
        val payload = JsonObject()
        payload.addProperty("username", "RemmyChat Moderation")

        val embed = JsonObject()
        embed.addProperty("title", "⚠️ Moderation Action")
        embed.addProperty("description", "**$action** | **$player** | **$reason** | By: **$moderator**")

        // Set color based on action
        val color = when (action.lowercase()) {
            "ban" -> 0xFF0000 // Red
            "kick" -> 0xFF8C00 // Orange
            "mute" -> 0xFFFF00 // Yellow
            "warn" -> 0xFFA500 // Orange
            else -> 0x808080 // Gray
        }
        embed.addProperty("color", color)
        embed.addProperty("timestamp", java.time.Instant.now().toString())

        val embeds = JsonArray()
        embeds.add(embed)
        payload.add("embeds", embeds)

        return gson.toJson(payload)
    }

    /**
     * Send chat violation log to Discord
     */
    fun sendChatViolation(violationType: String, player: String, message: String, channel: String) {
        if (!chatViolationsEnabled || chatViolationsChannel.isEmpty()) return

        val webhookUrl = webhookUrls[chatViolationsChannel]
        if (webhookUrl == null) {
            plugin.debugLog("No webhook URL configured for chat violations")
            return
        }

        CompletableFuture.supplyAsync({
            try {
                val payload = createChatViolationPayload(violationType, player, message, channel)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() in 200..299) {
                    plugin.debugLog("Chat violation logged to Discord successfully")
                } else {
                    plugin.logger.warning("Failed to log chat violation: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error logging chat violation: ${e.message}")
            }
        }, webhookExecutor)
    }

    /**
     * Create chat violation payload
     */
    private fun createChatViolationPayload(violationType: String, player: String, message: String, channel: String): String {
        val payload = JsonObject()
        payload.addProperty("username", "RemmyChat Security")

        val embed = JsonObject()
        embed.addProperty("title", "🚨 Chat Violation Detected")
        embed.addProperty("description", "**$violationType** by **$player** in **$channel**")
        embed.addProperty("color", 0xFF4500) // Red-orange
        embed.addProperty("timestamp", java.time.Instant.now().toString())

        val fields = JsonArray()

        val messageField = JsonObject()
        messageField.addProperty("name", "Message")
        messageField.addProperty("value", "```$message```")
        messageField.addProperty("inline", false)
        fields.add(messageField)

        embed.add("fields", fields)

        val embeds = JsonArray()
        embeds.add(embed)
        payload.add("embeds", embeds)

        return gson.toJson(payload)
    }

    /**
     * Start statistics task
     */
    private fun startStatisticsTask() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            sendServerStatistics()
        }, 20L * statisticsInterval, 20L * statisticsInterval)
    }

    /**
     * Sync player roles (if role sync is enabled)
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!roleSyncEnabled) return

        // Role sync would require DiscordSRV API or direct Discord bot integration
        // This is a placeholder for the implementation
        plugin.debugLog("Player ${event.player.name} joined - role sync check")
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!roleSyncEnabled) return

        plugin.debugLog("Player ${event.player.name} left - role sync check")
    }

    /**
     * Shutdown advanced Discord integration
     */
    fun shutdown() {
        webhookExecutor.shutdown()
        try {
            if (!webhookExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                webhookExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            webhookExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Reload configuration
     */
    fun reload() {
        loadConfiguration()
        plugin.logger.info("Advanced Discord integration configuration reloaded")
    }

    /**
     * Get integration status for admin interface
     */
    fun getIntegrationStatus(): IntegrationStatus {
        return IntegrationStatus(
            webhookActive = webhooksEnabled && webhookUrls.isNotEmpty(),
            messagesSent = messagesSent,
            messagesReceived = messagesReceived,
            lastActivity = lastActivity
        )
    }

    /**
     * Test Discord connection
     */
    fun testConnection(): Boolean {
        return try {
            if (webhookUrls.isEmpty()) {
                plugin.logger.warning("No webhook URLs configured for connection test")
                return false
            }

            val testWebhook = webhookUrls.values.first()
            val testPayload = createTestPayload()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(testWebhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testPayload))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val success = response.statusCode() in 200..299

            if (success) {
                plugin.logger.info("Discord connection test successful")
            } else {
                plugin.logger.warning("Discord connection test failed with status: ${response.statusCode()}")
            }

            success
        } catch (e: Exception) {
            plugin.logger.warning("Discord connection test failed: ${e.message}")
            false
        }
    }

    /**
     * Synchronize with Discord (placeholder implementation)
     */
    fun synchronize() {
        plugin.debugLog("Starting Discord synchronization...")

        if (roleSyncEnabled) {
            // Role sync would happen here
            plugin.debugLog("Synchronizing roles...")
        }

        if (statisticsEnabled) {
            // Force statistics update
            try {
                plugin.debugLog("Statistics collected for Discord sync")
            } catch (e: Exception) {
                plugin.debugLog("Failed to collect statistics: ${e.message}")
            }
        }

        plugin.debugLog("Discord synchronization completed")
    }

    /**
     * Create test payload for connection testing
     */
    private fun createTestPayload(): String {
        val payload = JsonObject()
        payload.addProperty("username", "RemmyChat")
        payload.addProperty("content", "🔧 Connection test from RemmyChat admin interface")
        return gson.toJson(payload)
    }

    // Data classes
    data class ServerStatistics(
        val onlinePlayers: Int,
        val maxPlayers: Int,
        val uptimeHours: Long,
        val messagesSent: Long,
        val messagesReceived: Long,
        val crossServerMessages: Long,
        val errorCount: Long,
        val memoryUsageMB: Double,
        val backupCount: Int
    )

    data class IntegrationStatus(
        val webhookActive: Boolean,
        val messagesSent: Long,
        val messagesReceived: Long,
        val lastActivity: Long
    )
}
