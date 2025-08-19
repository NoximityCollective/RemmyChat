package com.noximity.remmyChat.features

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Advanced Discord integration features including webhooks, role sync, statistics, and moderation
 */
class AdvancedDiscordFeatures(private val plugin: RemmyChat) : Listener {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()
    private val webhookExecutor = Executors.newFixedThreadPool(3)
    private val statisticsExecutor = Executors.newSingleThreadScheduledExecutor()

    // Configuration
    private var webhooksEnabled = false
    private var usePlayerAvatars = true
    private var avatarService = "crafatar"
    private var roleSyncEnabled = false
    private var syncGroupsToRoles = false
    private var statisticsEnabled = false
    private var periodicStats = false
    private var moderationIntegrationEnabled = false

    // Webhook URLs
    private val channelWebhooks = ConcurrentHashMap<String, String>()

    // Role mappings
    private val groupToRoleMappings = ConcurrentHashMap<String, String>()
    private val roleToGroupMappings = ConcurrentHashMap<String, String>()

    // Statistics
    private var statisticsChannel = ""
    private var statisticsInterval = 3600L // 1 hour
    private var lastStatisticsUpdate = 0L

    // Moderation
    private var moderationAlertsEnabled = false
    private var moderationAlertChannel = ""
    private var chatViolationsEnabled = false
    private var chatViolationsChannel = ""

    // Rate limiting
    private val webhookRateLimit = ConcurrentHashMap<String, MutableList<Long>>()
    private val maxWebhooksPerMinute = 30

    // Statistics tracking
    private var messagesSent = 0L
    private var messagesReceived = 0L
    private var webhooksSent = 0L
    private var errorCount = 0L

    fun initialize() {
        loadConfiguration()

        if (webhooksEnabled || roleSyncEnabled || statisticsEnabled || moderationIntegrationEnabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin)
        }

        if (statisticsEnabled && periodicStats) {
            startStatisticsTask()
        }

        plugin.debugLog("AdvancedDiscordFeatures initialized - Webhooks: $webhooksEnabled, RoleSync: $roleSyncEnabled, Stats: $statisticsEnabled")
    }

    /**
     * Send message via webhook
     */
    fun sendWebhookMessage(channelName: String, playerName: String, message: String, serverName: String? = null) {
        if (!webhooksEnabled) return

        val webhookUrl = channelWebhooks[channelName] ?: return

        if (!checkWebhookRateLimit(channelName)) {
            plugin.debugLog("Webhook rate limit exceeded for channel: $channelName")
            return
        }

        webhookExecutor.submit {
            try {
                val payload = createWebhookPayload(playerName, message, serverName)
                val response = sendWebhookRequest(webhookUrl, payload)

                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    webhooksSent++
                    plugin.debugLog("Webhook sent successfully for $channelName")
                } else {
                    errorCount++
                    plugin.logger.warning("Webhook failed for $channelName: ${response.statusCode()}")
                }

                updateWebhookRateLimit(channelName)

            } catch (e: Exception) {
                errorCount++
                plugin.logger.warning("Error sending webhook for $channelName: ${e.message}")
            }
        }
    }

    /**
     * Sync player's Minecraft group to Discord role
     */
    fun syncPlayerRole(player: Player) {
        if (!roleSyncEnabled || !syncGroupsToRoles) return

        val playerGroup = plugin.groupManager.getPlayerGroup(player)
        val discordRole = groupToRoleMappings[playerGroup]

        if (discordRole != null) {
            // This would require DiscordSRV integration or direct Discord API calls
            // For now, we'll log the intended action
            plugin.debugLog("Would sync ${player.name} (group: $playerGroup) to Discord role: $discordRole")

            // If DiscordSRV is available, we could call:
            // DiscordSRV.getPlugin().syncPlayerRole(player, discordRole)
        }
    }

    /**
     * Send periodic server statistics to Discord
     */
    fun sendServerStatistics() {
        if (!statisticsEnabled || !periodicStats || statisticsChannel.isEmpty()) return

        val stats = collectServerStatistics()
        val embed = createStatisticsEmbed(stats)

        webhookExecutor.submit {
            try {
                val webhookUrl = channelWebhooks[statisticsChannel]
                if (webhookUrl != null) {
                    val response = sendWebhookRequest(webhookUrl, embed)
                    if (response.statusCode() == 200 || response.statusCode() == 204) {
                        lastStatisticsUpdate = System.currentTimeMillis()
                        plugin.debugLog("Statistics sent to Discord")
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending statistics to Discord: ${e.message}")
            }
        }
    }

    /**
     * Send moderation alert to Discord
     */
    fun sendModerationAlert(type: String, player: String, moderator: String?, reason: String?, action: String) {
        if (!moderationIntegrationEnabled || !moderationAlertsEnabled || moderationAlertChannel.isEmpty()) return

        val embed = createModerationAlertEmbed(type, player, moderator, reason, action)

        webhookExecutor.submit {
            try {
                val webhookUrl = channelWebhooks[moderationAlertChannel]
                if (webhookUrl != null) {
                    sendWebhookRequest(webhookUrl, embed)
                    plugin.debugLog("Moderation alert sent to Discord: $type for $player")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending moderation alert to Discord: ${e.message}")
            }
        }
    }

    /**
     * Send chat violation alert to Discord
     */
    fun sendChatViolationAlert(player: String, channel: String, message: String, violationType: String) {
        if (!moderationIntegrationEnabled || !chatViolationsEnabled || chatViolationsChannel.isEmpty()) return

        val embed = createChatViolationEmbed(player, channel, message, violationType)

        webhookExecutor.submit {
            try {
                val webhookUrl = channelWebhooks[chatViolationsChannel]
                if (webhookUrl != null) {
                    sendWebhookRequest(webhookUrl, embed)
                    plugin.debugLog("Chat violation alert sent to Discord: $violationType by $player")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error sending chat violation alert to Discord: ${e.message}")
            }
        }
    }

    /**
     * Load configuration settings
     */
    private fun loadConfiguration() {
        val discordConfig = plugin.configManager.getDiscordConfig()

        // Webhook settings
        val webhookSection = discordConfig.getConfigurationSection("advanced.webhooks")
        if (webhookSection != null) {
            webhooksEnabled = webhookSection.getBoolean("enabled", false)
            usePlayerAvatars = webhookSection.getBoolean("use-player-avatars", true)
            avatarService = webhookSection.getString("avatar-service", "crafatar") ?: "crafatar"

            val channelsSection = webhookSection.getConfigurationSection("channels")
            channelsSection?.getKeys(false)?.forEach { channelName ->
                val webhookUrl = channelsSection.getString(channelName)
                if (webhookUrl != null && webhookUrl.isNotEmpty()) {
                    channelWebhooks[channelName] = webhookUrl
                }
            }
        }

        // Role sync settings
        val roleSyncSection = discordConfig.getConfigurationSection("advanced.role-sync")
        if (roleSyncSection != null) {
            roleSyncEnabled = roleSyncSection.getBoolean("enabled", false)
            syncGroupsToRoles = roleSyncSection.getBoolean("sync-groups-to-roles", false)

            val mappingsSection = roleSyncSection.getConfigurationSection("group-mappings")
            mappingsSection?.getKeys(false)?.forEach { groupName ->
                val roleName = mappingsSection.getString(groupName)
                if (roleName != null) {
                    groupToRoleMappings[groupName] = roleName
                    roleToGroupMappings[roleName] = groupName
                }
            }
        }

        // Statistics settings
        val statsSection = discordConfig.getConfigurationSection("advanced.statistics")
        if (statsSection != null) {
            statisticsEnabled = statsSection.getBoolean("enabled", false)
            periodicStats = statsSection.getBoolean("periodic-stats", false)
            statisticsChannel = statsSection.getString("stats-channel", "") ?: ""
            statisticsInterval = statsSection.getLong("stats-interval", 3600L)
        }

        // Moderation settings
        val moderationSection = discordConfig.getConfigurationSection("moderation")
        if (moderationSection != null) {
            moderationIntegrationEnabled = moderationSection.getBoolean("enabled", false)

            val alertsSection = moderationSection.getConfigurationSection("alerts")
            if (alertsSection != null) {
                moderationAlertsEnabled = alertsSection.getBoolean("enabled", false)
                moderationAlertChannel = alertsSection.getString("alert-channel", "") ?: ""
            }

            val violationsSection = moderationSection.getConfigurationSection("chat-violations")
            if (violationsSection != null) {
                chatViolationsEnabled = violationsSection.getBoolean("enabled", false)
                chatViolationsChannel = violationsSection.getString("log-channel", "") ?: ""
            }
        }
    }

    /**
     * Create webhook payload for a chat message
     */
    private fun createWebhookPayload(playerName: String, message: String, serverName: String?): String {
        val payload = JsonObject()

        if (usePlayerAvatars) {
            payload.addProperty("username", playerName)
            payload.addProperty("avatar_url", getPlayerAvatarUrl(playerName))
        } else {
            payload.addProperty("username", "Minecraft Server")
        }

        val content = if (serverName != null) {
            "**[$serverName]** $playerName: $message"
        } else {
            "$playerName: $message"
        }

        payload.addProperty("content", content)

        return gson.toJson(payload)
    }

    /**
     * Create statistics embed
     */
    private fun createStatisticsEmbed(stats: ServerStatistics): String {
        val embed = JsonObject()
        val embedObj = JsonObject()

        embedObj.addProperty("title", "📊 Server Statistics")
        embedObj.addProperty("color", 5814783) // Blue color
        embedObj.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")

        val fields = arrayOf(
            createEmbedField("🟢 Online Players", "${stats.onlinePlayers}/${stats.maxPlayers}", true),
            createEmbedField("📈 Peak Today", stats.peakPlayers.toString(), true),
            createEmbedField("💬 Messages Sent", stats.messagesSent.toString(), true),
            createEmbedField("🔗 Webhooks Sent", stats.webhooksSent.toString(), true),
            createEmbedField("⚠️ Errors", stats.errorCount.toString(), true),
            createEmbedField("🕐 Uptime", formatUptime(stats.uptimeMillis), true)
        )

        embedObj.add("fields", gson.toJsonTree(fields))

        val embeds = arrayOf(embedObj)
        embed.add("embeds", gson.toJsonTree(embeds))

        return gson.toJson(embed)
    }

    /**
     * Create moderation alert embed
     */
    private fun createModerationAlertEmbed(type: String, player: String, moderator: String?, reason: String?, action: String): String {
        val embed = JsonObject()
        val embedObj = JsonObject()

        embedObj.addProperty("title", "⚠️ Moderation Alert")
        embedObj.addProperty("color", 16711680) // Red color
        embedObj.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")

        val fields = mutableListOf(
            createEmbedField("Action", action, true),
            createEmbedField("Player", player, true)
        )

        if (moderator != null) {
            fields.add(createEmbedField("Moderator", moderator, true))
        }

        if (reason != null && reason.isNotEmpty()) {
            fields.add(createEmbedField("Reason", reason, false))
        }

        embedObj.add("fields", gson.toJsonTree(fields))

        val embeds = arrayOf(embedObj)
        embed.add("embeds", gson.toJsonTree(embeds))

        return gson.toJson(embed)
    }

    /**
     * Create chat violation embed
     */
    private fun createChatViolationEmbed(player: String, channel: String, message: String, violationType: String): String {
        val embed = JsonObject()
        val embedObj = JsonObject()

        embedObj.addProperty("title", "🚨 Chat Violation")
        embedObj.addProperty("color", 16776960) // Yellow color
        embedObj.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")

        val fields = arrayOf(
            createEmbedField("Player", player, true),
            createEmbedField("Channel", channel, true),
            createEmbedField("Violation", violationType, true),
            createEmbedField("Message", message.take(1000), false)
        )

        embedObj.add("fields", gson.toJsonTree(fields))

        val embeds = arrayOf(embedObj)
        embed.add("embeds", gson.toJsonTree(embeds))

        return gson.toJson(embed)
    }

    /**
     * Create embed field
     */
    private fun createEmbedField(name: String, value: String, inline: Boolean): JsonObject {
        val field = JsonObject()
        field.addProperty("name", name)
        field.addProperty("value", value)
        field.addProperty("inline", inline)
        return field
    }

    /**
     * Send HTTP request to webhook
     */
    private fun sendWebhookRequest(webhookUrl: String, payload: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Get player avatar URL
     */
    private fun getPlayerAvatarUrl(playerName: String): String {
        return when (avatarService.lowercase()) {
            "crafatar" -> "https://crafatar.com/avatars/$playerName?size=64"
            "minotar" -> "https://minotar.net/avatar/$playerName/64"
            "mc-heads" -> "https://mc-heads.net/avatar/$playerName/64"
            else -> "https://crafatar.com/avatars/$playerName?size=64"
        }
    }

    /**
     * Check webhook rate limit
     */
    private fun checkWebhookRateLimit(channelName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val requests = webhookRateLimit.computeIfAbsent(channelName) { mutableListOf() }

        // Remove requests older than 1 minute
        requests.removeIf { currentTime - it > 60000 }

        return requests.size < maxWebhooksPerMinute
    }

    /**
     * Update webhook rate limit tracking
     */
    private fun updateWebhookRateLimit(channelName: String) {
        val currentTime = System.currentTimeMillis()
        webhookRateLimit.computeIfAbsent(channelName) { mutableListOf() }.add(currentTime)
    }

    /**
     * Collect server statistics
     */
    private fun collectServerStatistics(): ServerStatistics {
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()
        val uptimeMillis = System.currentTimeMillis() - plugin.server.startTime

        return ServerStatistics(
            onlinePlayers = onlinePlayers,
            maxPlayers = maxPlayers,
            peakPlayers = getSessionPeakPlayers(),
            messagesSent = messagesSent,
            webhooksSent = webhooksSent,
            errorCount = errorCount,
            uptimeMillis = uptimeMillis
        )
    }

    /**
     * Get peak players for current session
     */
    private fun getSessionPeakPlayers(): Int {
        // This would ideally be tracked throughout the session
        return Bukkit.getOnlinePlayers().size
    }

    /**
     * Format uptime in human-readable format
     */
    private fun formatUptime(uptimeMillis: Long): String {
        val seconds = uptimeMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Start periodic statistics task
     */
    private fun startStatisticsTask() {
        statisticsExecutor.scheduleAtFixedRate({
            try {
                sendServerStatistics()
            } catch (e: Exception) {
                plugin.logger.warning("Error in statistics task: ${e.message}")
            }
        }, statisticsInterval, statisticsInterval, TimeUnit.SECONDS)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (roleSyncEnabled) {
            // Delay role sync to ensure player is fully loaded
            Bukkit.getScheduler().runTaskLater(plugin, {
                syncPlayerRole(event.player)
            }, 20L) // 1 second delay
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Could implement role removal on quit if needed
    }

    /**
     * Increment messages sent counter
     */
    fun incrementMessagesSent() {
        messagesSent++
    }

    /**
     * Increment messages received counter
     */
    fun incrementMessagesReceived() {
        messagesReceived++
    }

    /**
     * Get Discord integration statistics
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "webhooksEnabled" to webhooksEnabled,
            "roleSyncEnabled" to roleSyncEnabled,
            "statisticsEnabled" to statisticsEnabled,
            "moderationEnabled" to moderationIntegrationEnabled,
            "messagesSent" to messagesSent,
            "messagesReceived" to messagesReceived,
            "webhooksSent" to webhooksSent,
            "errorCount" to errorCount,
            "lastStatisticsUpdate" to lastStatisticsUpdate
        )
    }

    fun shutdown() {
        webhookExecutor.shutdown()
        statisticsExecutor.shutdown()
    }

    // Data classes
    data class ServerStatistics(
        val onlinePlayers: Int,
        val maxPlayers: Int,
        val peakPlayers: Int,
        val messagesSent: Long,
        val webhooksSent: Long,
        val errorCount: Long,
        val uptimeMillis: Long
    )
}
