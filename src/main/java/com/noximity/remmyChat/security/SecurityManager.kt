package com.noximity.remmyChat.security

import com.noximity.remmyChat.RemmyChat
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.time.Instant
import java.time.temporal.ChronoUnit

class SecurityManager(private val plugin: RemmyChat) {

    // Rate limiting data
    private val playerMessageCounts = ConcurrentHashMap<String, MutableList<Long>>()
    private val playerLastMessage = ConcurrentHashMap<String, Long>()
    private val playerBurstCounts = ConcurrentHashMap<String, Int>()

    // Spam protection data
    private val playerRecentMessages = ConcurrentHashMap<String, MutableList<String>>()
    private val duplicateMessageCounts = ConcurrentHashMap<String, Int>()

    // Content filtering
    private val blockedWords = mutableSetOf<String>()
    private val blockedPatterns = mutableListOf<Pattern>()

    // IP tracking for advanced security
    private val playerIPs = ConcurrentHashMap<String, String>()
    private val ipConnections = ConcurrentHashMap<String, MutableList<String>>()
    private val suspiciousIPs = mutableSetOf<String>()

    // Configuration cache
    private var rateLimitEnabled = true
    private var messagesPerMinute = 30
    private var burstLimit = 5
    private var spamProtectionEnabled = true
    private var duplicateThreshold = 3
    private var similarityThreshold = 0.8
    private var contentFilterEnabled = false
    private var replacementChar = "*"

    fun initialize() {
        plugin.debugLog("Initializing SecurityManager...")
        loadConfiguration()
        startCleanupTask()
        plugin.debugLog("SecurityManager initialized")
    }

    private fun loadConfiguration() {
        val config = plugin.configManager
        val mainConfig = config.getMainConfig()

        // Rate limiting settings
        rateLimitEnabled = mainConfig.getBoolean("security.rate-limit.enabled", true)
        messagesPerMinute = mainConfig.getInt("security.rate-limit.messages-per-minute", 30)
        burstLimit = mainConfig.getInt("security.rate-limit.burst-limit", 5)

        // Spam protection settings
        spamProtectionEnabled = mainConfig.getBoolean("security.spam-protection.enabled", true)
        duplicateThreshold = mainConfig.getInt("security.spam-protection.duplicate-threshold", 3)
        similarityThreshold = mainConfig.getDouble("security.spam-protection.similarity-threshold", 0.8)

        // Content filter settings
        contentFilterEnabled = mainConfig.getBoolean("security.content-filter.enabled", false)
        replacementChar = mainConfig.getString("security.content-filter.replacement-char", "*") ?: "*"

        // Load blocked words
        val blockedWordsList = mainConfig.getStringList("security.content-filter.blocked-words")
        blockedWords.clear()
        blockedWords.addAll(blockedWordsList.map { it.lowercase() })

        // Compile blocked patterns
        blockedPatterns.clear()
        blockedWordsList.forEach { word ->
            try {
                val pattern = Pattern.compile("\\b${Pattern.quote(word.lowercase())}\\b", Pattern.CASE_INSENSITIVE)
                blockedPatterns.add(pattern)
            } catch (e: Exception) {
                plugin.logger.warning("Invalid regex pattern for blocked word: $word")
            }
        }

        plugin.debugLog("Security configuration loaded: rateLimitEnabled=$rateLimitEnabled, spamProtectionEnabled=$spamProtectionEnabled, contentFilterEnabled=$contentFilterEnabled")
    }

    /**
     * Check if a player is allowed to send a message based on rate limiting
     */
    fun checkRateLimit(player: Player): RateLimitResult {
        if (!rateLimitEnabled) return RateLimitResult.ALLOWED

        val playerUUID = player.uniqueId.toString()
        val currentTime = System.currentTimeMillis()

        // Check burst limit (messages in quick succession)
        val lastMessageTime = playerLastMessage[playerUUID] ?: 0L
        if (currentTime - lastMessageTime < 1000) { // Less than 1 second
            val burstCount = playerBurstCounts.getOrDefault(playerUUID, 0) + 1
            playerBurstCounts[playerUUID] = burstCount

            if (burstCount > burstLimit) {
                return RateLimitResult.BURST_LIMIT_EXCEEDED
            }
        } else {
            playerBurstCounts[playerUUID] = 0
        }

        playerLastMessage[playerUUID] = currentTime

        // Check messages per minute limit
        val messageTimes = playerMessageCounts.getOrPut(playerUUID) { mutableListOf() }
        val oneMinuteAgo = currentTime - TimeUnit.MINUTES.toMillis(1)

        // Remove old messages
        messageTimes.removeIf { it < oneMinuteAgo }

        if (messageTimes.size >= messagesPerMinute) {
            return RateLimitResult.RATE_LIMIT_EXCEEDED
        }

        messageTimes.add(currentTime)
        return RateLimitResult.ALLOWED
    }

    /**
     * Check if a message should be blocked due to spam protection
     */
    fun checkSpamProtection(player: Player, message: String): SpamCheckResult {
        if (!spamProtectionEnabled) return SpamCheckResult.ALLOWED

        val playerUUID = player.uniqueId.toString()
        val normalizedMessage = message.lowercase().trim()

        // Get recent messages for this player
        val recentMessages = playerRecentMessages.getOrPut(playerUUID) { mutableListOf() }

        // Check for exact duplicates
        val duplicateCount = recentMessages.count { it == normalizedMessage }
        if (duplicateCount >= duplicateThreshold) {
            return SpamCheckResult.DUPLICATE_MESSAGE
        }

        // Check for similar messages
        for (recentMessage in recentMessages) {
            val similarity = calculateSimilarity(normalizedMessage, recentMessage)
            if (similarity >= similarityThreshold) {
                return SpamCheckResult.SIMILAR_MESSAGE
            }
        }

        // Add message to recent messages (keep last 10)
        recentMessages.add(normalizedMessage)
        if (recentMessages.size > 10) {
            recentMessages.removeAt(0)
        }

        return SpamCheckResult.ALLOWED
    }

    /**
     * Filter message content based on blocked words and patterns
     */
    fun filterContent(message: String): ContentFilterResult {
        if (!contentFilterEnabled) return ContentFilterResult(message, false)

        var filteredMessage = message
        var wasFiltered = false

        // Check blocked patterns
        for (pattern in blockedPatterns) {
            val matcher = pattern.matcher(filteredMessage)
            if (matcher.find()) {
                wasFiltered = true
                filteredMessage = matcher.replaceAll { matchResult ->
                    replacementChar.repeat(matchResult.group().length)
                }
            }
        }

        return ContentFilterResult(filteredMessage, wasFiltered)
    }

    /**
     * Validate message for security threats
     */
    fun validateMessage(player: Player, message: String): MessageValidationResult {
        // Check for potentially malicious content
        val dangerousPatterns = listOf(
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in dangerousPatterns) {
            if (pattern.matcher(message).find()) {
                plugin.logger.warning("Potentially malicious message blocked from ${player.name}: $message")
                return MessageValidationResult.MALICIOUS_CONTENT
            }
        }

        // Check message length
        if (message.length > 2048) {
            return MessageValidationResult.TOO_LONG
        }

        // Check for excessive special characters
        val specialCharCount = message.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (specialCharCount > message.length * 0.5) {
            return MessageValidationResult.EXCESSIVE_SPECIAL_CHARS
        }

        return MessageValidationResult.VALID
    }

    /**
     * Track player IP for security monitoring
     */
    fun trackPlayerIP(player: Player) {
        val playerUUID = player.uniqueId.toString()
        val playerIP = player.address?.address?.hostAddress ?: return

        playerIPs[playerUUID] = playerIP

        // Track connections per IP
        val connections = ipConnections.getOrPut(playerIP) { mutableListOf() }
        if (!connections.contains(playerUUID)) {
            connections.add(playerUUID)
        }

        // Check for suspicious activity (multiple accounts from same IP)
        if (connections.size > 5) {
            suspiciousIPs.add(playerIP)
            plugin.logger.warning("Suspicious IP detected: $playerIP (${connections.size} accounts)")
        }
    }

    /**
     * Check if a player's IP is flagged as suspicious
     */
    fun isSuspiciousPlayer(player: Player): Boolean {
        val playerIP = player.address?.address?.hostAddress ?: return false
        return suspiciousIPs.contains(playerIP)
    }

    /**
     * Get security statistics for monitoring
     */
    fun getSecurityStatistics(): SecurityStatistics {
        return SecurityStatistics(
            rateLimitViolations = playerMessageCounts.values.sumOf { it.size },
            spamDetections = duplicateMessageCounts.values.sum(),
            suspiciousIPs = suspiciousIPs.size,
            trackedIPs = ipConnections.size,
            totalConnections = ipConnections.values.sumOf { it.size }
        )
    }

    /**
     * Clear security data for a player (on disconnect)
     */
    fun clearPlayerData(player: Player) {
        val playerUUID = player.uniqueId.toString()
        playerMessageCounts.remove(playerUUID)
        playerLastMessage.remove(playerUUID)
        playerBurstCounts.remove(playerUUID)
        playerRecentMessages.remove(playerUUID)
        duplicateMessageCounts.remove(playerUUID)
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        val maxLength = maxOf(str1.length, str2.length)
        if (maxLength == 0) return 1.0

        val distance = levenshteinDistance(str1, str2)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) {
            dp[i][0] = i
        }

        for (j in 0..str2.length) {
            dp[0][j] = j
        }

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[str1.length][str2.length]
    }

    /**
     * Start cleanup task to remove old data
     */
    private fun startCleanupTask() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            cleanupOldData()
        }, 20L * 60 * 5, 20L * 60 * 5) // Every 5 minutes
    }

    /**
     * Clean up old security data
     */
    private fun cleanupOldData() {
        val currentTime = System.currentTimeMillis()
        val fiveMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(5)

        // Clean up old message times
        for (messageTimes in playerMessageCounts.values) {
            messageTimes.removeIf { it < fiveMinutesAgo }
        }

        // Clean up old last message times
        playerLastMessage.entries.removeIf { it.value < fiveMinutesAgo }

        // Clean up burst counts for players who haven't messaged recently
        playerBurstCounts.entries.removeIf { entry ->
            val lastMessage = playerLastMessage[entry.key] ?: 0L
            lastMessage < fiveMinutesAgo
        }

        plugin.debugLog("Security data cleanup completed")
    }

    /**
     * Reload security configuration
     */
    fun reload() {
        loadConfiguration()
        plugin.logger.info("Security configuration reloaded")
    }

    /**
     * Check if rate limiting is enabled
     */
    fun isRateLimitingEnabled(): Boolean {
        return rateLimitEnabled
    }

    /**
     * Check if spam protection is enabled
     */
    fun isSpamProtectionEnabled(): Boolean {
        return spamProtectionEnabled
    }

    /**
     * Check if content filtering is enabled
     */
    fun isContentFilterEnabled(): Boolean {
        return contentFilterEnabled
    }

    /**
     * Get player violations for admin interface
     */
    fun getPlayerViolations(playerName: String): List<SecurityViolation> {
        val violations = mutableListOf<SecurityViolation>()

        // Get player UUID by name
        val player = plugin.server.getPlayer(playerName)
        val uuid = player?.uniqueId?.toString() ?: return violations

        try {
            val connection = plugin.databaseManager.getConnection()
            connection?.prepareStatement(
                "SELECT violation_type, severity, message, timestamp FROM security_violations WHERE uuid = ? ORDER BY timestamp DESC LIMIT 50"
            )?.use { ps ->
                ps.setString(1, uuid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        violations.add(
                            SecurityViolation(
                                type = rs.getString("violation_type"),
                                severity = rs.getString("severity"),
                                message = rs.getString("message") ?: "",
                                timestamp = rs.getLong("timestamp")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player violations: ${e.message}")
        }

        return violations
    }

    /**
     * Clear player violations for admin interface
     */
    fun clearPlayerViolations(playerName: String) {
        val player = plugin.server.getPlayer(playerName)
        val uuid = player?.uniqueId?.toString() ?: return

        try {
            val connection = plugin.databaseManager.getConnection()
            connection?.prepareStatement(
                "UPDATE security_violations SET resolved = 1 WHERE uuid = ?"
            )?.use { ps ->
                ps.setString(1, uuid)
                ps.executeUpdate()
            }

            // Also clear in-memory data
            playerMessageCounts.remove(uuid)
            playerRecentMessages.remove(uuid)
            duplicateMessageCounts.remove(uuid)

            plugin.logger.info("Cleared security violations for player: $playerName")
        } catch (e: Exception) {
            plugin.logger.warning("Error clearing player violations: ${e.message}")
        }
    }

    /**
     * Data class for security violations
     */
    data class SecurityViolation(
        val type: String,
        val severity: String,
        val message: String,
        val timestamp: Long
    )

    // Result classes
    enum class RateLimitResult {
        ALLOWED,
        RATE_LIMIT_EXCEEDED,
        BURST_LIMIT_EXCEEDED
    }

    enum class SpamCheckResult {
        ALLOWED,
        DUPLICATE_MESSAGE,
        SIMILAR_MESSAGE
    }

    data class ContentFilterResult(
        val filteredMessage: String,
        val wasFiltered: Boolean
    )

    enum class MessageValidationResult {
        VALID,
        MALICIOUS_CONTENT,
        TOO_LONG,
        EXCESSIVE_SPECIAL_CHARS
    }

    data class SecurityStatistics(
        val rateLimitViolations: Int,
        val spamDetections: Int,
        val suspiciousIPs: Int,
        val trackedIPs: Int,
        val totalConnections: Int
    )
}
