package com.noximity.remmyChat.moderation

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.database.DatabaseManager.MuteInfo
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class ModerationManager(private val plugin: RemmyChat) : Listener {

    // Filter configurations
    private val blockedWords = mutableSetOf<String>()
    private val blockedPatterns = mutableListOf<Pattern>()
    private val allowedWords = mutableSetOf<String>() // Words that bypass filters
    private val severityLevels = ConcurrentHashMap<String, FilterSeverity>()

    // Player violation tracking
    private val playerViolations = ConcurrentHashMap<String, MutableList<Violation>>()
    private val playerWarnings = ConcurrentHashMap<String, Int>()
    private val playerMutes = ConcurrentHashMap<String, MuteData>()

    // Auto-moderation settings
    private var autoModerationEnabled = true
    private var autoMuteEnabled = true
    private var autoKickEnabled = false
    private var autoBanEnabled = false
    private var escalationEnabled = true

    // Thresholds
    private var warningThreshold = 3
    private var muteThreshold = 5
    private var kickThreshold = 8
    private var banThreshold = 10
    private var violationTimeWindow = 3600L // 1 hour

    // Filter types
    private var profanityFilterEnabled = true
    private var spamFilterEnabled = true
    private var capsFilterEnabled = true
    private var repeatedCharFilterEnabled = true
    private var advertisingFilterEnabled = true
    private var toxicityFilterEnabled = true

    // Filter settings
    private var capsPercentageThreshold = 70
    private var repeatedCharThreshold = 5
    private var toxicityThreshold = 0.7
    private var replacementChar = "*"
    private var filterBypass = false

    // Anti-spam settings
    private val playerMessageHistory = ConcurrentHashMap<String, MutableList<MessageData>>()
    private var spamMessageThreshold = 4
    private var spamTimeWindow = 10L // seconds

    fun initialize() {
        plugin.debugLog("Initializing ModerationManager...")
        loadConfiguration()
        loadWordFilters()
        plugin.server.pluginManager.registerEvents(this, plugin)
        startCleanupTask()
        plugin.debugLog("ModerationManager initialized")
    }

    private fun loadConfiguration() {
        val config = plugin.configManager.getMainConfig()

        // Auto-moderation settings
        autoModerationEnabled = config.getBoolean("moderation.auto-moderation.enabled", true)
        autoMuteEnabled = config.getBoolean("moderation.auto-moderation.auto-mute", true)
        autoKickEnabled = config.getBoolean("moderation.auto-moderation.auto-kick", false)
        autoBanEnabled = config.getBoolean("moderation.auto-moderation.auto-ban", false)
        escalationEnabled = config.getBoolean("moderation.auto-moderation.escalation", true)

        // Thresholds
        warningThreshold = config.getInt("moderation.thresholds.warning", 3)
        muteThreshold = config.getInt("moderation.thresholds.mute", 5)
        kickThreshold = config.getInt("moderation.thresholds.kick", 8)
        banThreshold = config.getInt("moderation.thresholds.ban", 10)
        violationTimeWindow = config.getLong("moderation.thresholds.time-window", 3600L)

        // Filter types
        profanityFilterEnabled = config.getBoolean("moderation.filters.profanity", true)
        spamFilterEnabled = config.getBoolean("moderation.filters.spam", true)
        capsFilterEnabled = config.getBoolean("moderation.filters.caps", true)
        repeatedCharFilterEnabled = config.getBoolean("moderation.filters.repeated-chars", true)
        advertisingFilterEnabled = config.getBoolean("moderation.filters.advertising", true)
        toxicityFilterEnabled = config.getBoolean("moderation.filters.toxicity", true)

        // Filter settings
        capsPercentageThreshold = config.getInt("moderation.settings.caps-percentage", 70)
        repeatedCharThreshold = config.getInt("moderation.settings.repeated-char-threshold", 5)
        toxicityThreshold = config.getDouble("moderation.settings.toxicity-threshold", 0.7)
        replacementChar = config.getString("moderation.settings.replacement-char", "*") ?: "*"
        filterBypass = config.getBoolean("moderation.settings.filter-bypass", false)

        // Anti-spam settings
        spamMessageThreshold = config.getInt("moderation.anti-spam.message-threshold", 4)
        spamTimeWindow = config.getLong("moderation.anti-spam.time-window", 10L)

        plugin.debugLog("Moderation configuration loaded")
    }

    private fun loadWordFilters() {
        val config = plugin.configManager.getMainConfig()

        // Load blocked words
        val blockedWordsList = config.getStringList("moderation.blocked-words")
        blockedWords.clear()
        blockedWords.addAll(blockedWordsList.map { it.lowercase() })

        // Load allowed words (whitelist)
        val allowedWordsList = config.getStringList("moderation.allowed-words")
        allowedWords.clear()
        allowedWords.addAll(allowedWordsList.map { it.lowercase() })

        // Load severity levels
        val severitySection = config.getConfigurationSection("moderation.severity-levels")
        severitySection?.getKeys(false)?.forEach { word ->
            val severity = severitySection.getString(word)?.let { FilterSeverity.valueOf(it.uppercase()) } ?: FilterSeverity.MEDIUM
            severityLevels[word.lowercase()] = severity
        }

        // Compile patterns for common violations
        blockedPatterns.clear()
        compileCommonPatterns()

        plugin.debugLog("Loaded ${blockedWords.size} blocked words and ${blockedPatterns.size} patterns")
    }

    private fun compileCommonPatterns() {
        // IP address pattern
        if (advertisingFilterEnabled) {
            blockedPatterns.add(Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"))
            blockedPatterns.add(Pattern.compile("\\b[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}\\b"))
        }

        // Discord invite pattern
        blockedPatterns.add(Pattern.compile("discord\\.gg/[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE))
        blockedPatterns.add(Pattern.compile("discordapp\\.com/invite/[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE))

        // Minecraft server addresses
        blockedPatterns.add(Pattern.compile("\\b[a-zA-Z0-9-]+\\.(net|com|org|co|me|us|eu)\\b", Pattern.CASE_INSENSITIVE))
    }

    /**
     * Check if a message should be filtered
     */
    fun checkMessage(player: Player, message: String, channel: String): ModerationResult {
        if (!autoModerationEnabled) {
            return ModerationResult(FilterAction.ALLOW, message, emptyList())
        }

        // Check if player has bypass permission
        if (filterBypass && (player.hasPermission("remmychat.moderation.bypass") || player.isOp)) {
            return ModerationResult(FilterAction.ALLOW, message, emptyList())
        }

        val violations = mutableListOf<ViolationType>()
        var filteredMessage = message

        // Check spam
        if (spamFilterEnabled && checkSpam(player, message)) {
            violations.add(ViolationType.SPAM)
        }

        // Check caps
        if (capsFilterEnabled && checkExcessiveCaps(message)) {
            violations.add(ViolationType.EXCESSIVE_CAPS)
            filteredMessage = filterCaps(filteredMessage)
        }

        // Check repeated characters
        if (repeatedCharFilterEnabled && checkRepeatedCharacters(message)) {
            violations.add(ViolationType.REPEATED_CHARACTERS)
            filteredMessage = filterRepeatedCharacters(filteredMessage)
        }

        // Check profanity
        if (profanityFilterEnabled) {
            val profanityResult = checkProfanity(message)
            if (profanityResult.isNotEmpty()) {
                violations.add(ViolationType.PROFANITY)
                filteredMessage = filterProfanity(filteredMessage)
            }
        }

        // Check advertising
        if (advertisingFilterEnabled && checkAdvertising(message)) {
            violations.add(ViolationType.ADVERTISING)
            return ModerationResult(FilterAction.BLOCK, message, violations)
        }

        // Check toxicity
        if (toxicityFilterEnabled && checkToxicity(message)) {
            violations.add(ViolationType.TOXICITY)
        }

        // Determine action based on violations
        val action = determineAction(player, violations)

        // Record violations
        if (violations.isNotEmpty()) {
            recordViolations(player, violations, message, channel)
        }

        return ModerationResult(action, filteredMessage, violations)
    }

    /**
     * Check for spam patterns
     */
    private fun checkSpam(player: Player, message: String): Boolean {
        val playerId = player.uniqueId.toString()
        val currentTime = System.currentTimeMillis()
        val messages = playerMessageHistory.getOrPut(playerId) { mutableListOf() }

        // Remove old messages
        messages.removeIf { it.timestamp < currentTime - (spamTimeWindow * 1000) }

        // Check for identical messages
        val identicalCount = messages.count { it.content.equals(message, ignoreCase = true) }
        if (identicalCount >= 2) {
            return true
        }

        // Check for rapid messaging
        if (messages.size >= spamMessageThreshold) {
            return true
        }

        // Check for similar messages (character similarity)
        val similarMessages = messages.count { calculateSimilarity(it.content, message) > 0.8 }
        if (similarMessages >= 2) {
            return true
        }

        // Add current message to history
        messages.add(MessageData(message, currentTime))

        return false
    }

    /**
     * Check for excessive caps
     */
    private fun checkExcessiveCaps(message: String): Boolean {
        if (message.length < 5) return false

        val letters = message.count { it.isLetter() }
        if (letters == 0) return false

        val caps = message.count { it.isUpperCase() }
        val capsPercentage = (caps.toDouble() / letters.toDouble()) * 100

        return capsPercentage > capsPercentageThreshold
    }

    /**
     * Check for repeated characters
     */
    private fun checkRepeatedCharacters(message: String): Boolean {
        var consecutiveCount = 1
        var lastChar = '\u0000'

        for (char in message) {
            if (char == lastChar) {
                consecutiveCount++
                if (consecutiveCount > repeatedCharThreshold) {
                    return true
                }
            } else {
                consecutiveCount = 1
                lastChar = char
            }
        }

        return false
    }

    /**
     * Check for profanity
     */
    private fun checkProfanity(message: String): List<String> {
        val foundWords = mutableListOf<String>()
        val lowerMessage = message.lowercase()

        // Check blocked words
        for (word in blockedWords) {
            if (lowerMessage.contains(word) && !allowedWords.contains(word)) {
                foundWords.add(word)
            }
        }

        return foundWords
    }

    /**
     * Check for advertising
     */
    private fun checkAdvertising(message: String): Boolean {
        for (pattern in blockedPatterns) {
            if (pattern.matcher(message).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Check for toxicity using simple keyword matching
     */
    private fun checkToxicity(message: String): Boolean {
        val toxicKeywords = listOf(
            "kys", "kill yourself", "hate", "stupid", "idiot", "noob",
            "trash", "garbage", "worthless", "useless"
        )

        val lowerMessage = message.lowercase()
        return toxicKeywords.any { lowerMessage.contains(it) }
    }

    /**
     * Filter caps in message
     */
    private fun filterCaps(message: String): String {
        return message.map { if (it.isUpperCase()) it.lowercaseChar() else it }.joinToString("")
    }

    /**
     * Filter repeated characters
     */
    private fun filterRepeatedCharacters(message: String): String {
        val result = StringBuilder()
        var consecutiveCount = 1
        var lastChar = '\u0000'

        for (char in message) {
            if (char == lastChar && consecutiveCount >= repeatedCharThreshold) {
                // Skip this character
                continue
            } else if (char == lastChar) {
                consecutiveCount++
            } else {
                consecutiveCount = 1
                lastChar = char
            }
            result.append(char)
        }

        return result.toString()
    }

    /**
     * Filter profanity in message
     */
    private fun filterProfanity(message: String): String {
        var filtered = message

        for (word in blockedWords) {
            if (!allowedWords.contains(word)) {
                val replacement = replacementChar.repeat(word.length)
                filtered = filtered.replace(word, replacement, ignoreCase = true)
            }
        }

        return filtered
    }

    /**
     * Determine what action to take based on violations
     */
    private fun determineAction(player: Player, violations: List<ViolationType>): FilterAction {
        if (violations.isEmpty()) {
            return FilterAction.ALLOW
        }

        // Check for severe violations that warrant immediate blocking
        if (violations.contains(ViolationType.ADVERTISING) ||
            violations.contains(ViolationType.SEVERE_PROFANITY)) {
            return FilterAction.BLOCK
        }

        // Check violation count for escalation
        if (escalationEnabled) {
            val totalViolations = getRecentViolationCount(player)

            when {
                totalViolations >= banThreshold && autoBanEnabled -> return FilterAction.BAN
                totalViolations >= kickThreshold && autoKickEnabled -> return FilterAction.KICK
                totalViolations >= muteThreshold && autoMuteEnabled -> return FilterAction.MUTE
                totalViolations >= warningThreshold -> return FilterAction.WARN
            }
        }

        // Default to filtering the message
        return FilterAction.FILTER
    }

    /**
     * Record violations for a player
     */
    private fun recordViolations(player: Player, violations: List<ViolationType>, message: String, channel: String) {
        val playerId = player.uniqueId.toString()
        val playerViolationList = playerViolations.getOrPut(playerId) { mutableListOf() }

        val currentTime = Instant.now()

        for (violationType in violations) {
            val violation = Violation(
                type = violationType,
                message = message,
                channel = channel,
                timestamp = currentTime,
                severity = getSeverity(violationType)
            )
            playerViolationList.add(violation)

            // Notify Discord if integration is enabled
            try {
                plugin.advancedDiscordIntegration?.sendChatViolation(
                    violationType.name,
                    player.name,
                    message,
                    channel
                )
            } catch (e: Exception) {
                plugin.logger.warning("Error sending Discord violation: ${e.message}")
            }

            plugin.debugLog("Recorded violation: ${violationType.name} for player ${player.name}")
        }

        // Clean old violations
        cleanOldViolations(playerId)
    }

    /**
     * Get recent violation count for a player
     */
    private fun getRecentViolationCount(player: Player): Int {
        val playerId = player.uniqueId.toString()
        val violations = playerViolations[playerId] ?: return 0

        val cutoffTime = Instant.now().minus(violationTimeWindow, ChronoUnit.SECONDS)
        return violations.count { it.timestamp.isAfter(cutoffTime) }
    }

    /**
     * Get severity for violation type
     */
    private fun getSeverity(violationType: ViolationType): FilterSeverity {
        return when (violationType) {
            ViolationType.ADVERTISING -> FilterSeverity.HIGH
            ViolationType.SEVERE_PROFANITY -> FilterSeverity.HIGH
            ViolationType.TOXICITY -> FilterSeverity.MEDIUM
            ViolationType.PROFANITY -> FilterSeverity.MEDIUM
            ViolationType.SPAM -> FilterSeverity.MEDIUM
            ViolationType.EXCESSIVE_CAPS -> FilterSeverity.LOW
            ViolationType.REPEATED_CHARACTERS -> FilterSeverity.LOW
        }
    }

    /**
     * Clean old violations for a player
     */
    private fun cleanOldViolations(playerId: String) {
        val violations = playerViolations[playerId] ?: return
        val cutoffTime = Instant.now().minus(violationTimeWindow, ChronoUnit.SECONDS)
        violations.removeIf { it.timestamp.isBefore(cutoffTime) }
    }

    /**
     * Calculate message similarity
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        val maxLength = maxOf(str1.length, str2.length)
        if (maxLength == 0) return 1.0

        val distance = levenshteinDistance(str1.lowercase(), str2.lowercase())
        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Calculate Levenshtein distance
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
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[str1.length][str2.length]
    }

    /**
     * Apply moderation action
     */
    fun applyModerationAction(player: Player, action: FilterAction, violations: List<ViolationType>) {
        when (action) {
            FilterAction.WARN -> {
                val warnings = playerWarnings.getOrDefault(player.uniqueId.toString(), 0) + 1
                playerWarnings[player.uniqueId.toString()] = warnings

                player.sendMessage("§cWarning: Your message violated chat rules. (Warning $warnings/$warningThreshold)")

                if (warnings >= warningThreshold && autoMuteEnabled) {
                    mutePlayer(player, 5 * 60) // 5 minutes
                }
            }

            FilterAction.MUTE -> {
                mutePlayer(player, 10 * 60) // 10 minutes
            }

            FilterAction.KICK -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.kickPlayer("§cKicked for violating chat rules.")
                })
            }

            FilterAction.BAN -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.banPlayerFull("Banned for repeated chat violations")
                })
            }

            else -> { /* No action needed */ }
        }

        // Send moderation alert
        if (action != FilterAction.ALLOW && action != FilterAction.FILTER) {
            try {
                plugin.advancedDiscordIntegration?.sendModerationAlert(
                    action.name,
                    player.name,
                    violations.joinToString(", ") { it.name },
                    "AutoMod"
                )
            } catch (e: Exception) {
                plugin.logger.warning("Error sending Discord moderation alert: ${e.message}")
            }
        }
    }

    /**
     * Mute a player
     */
    private fun mutePlayer(player: Player, durationSeconds: Int) {
        val playerId = player.uniqueId.toString()
        val endTime = Instant.now().plusSeconds(durationSeconds.toLong())

        playerMutes[playerId] = MuteData(endTime, "Chat violations")

        val minutes = durationSeconds / 60
        player.sendMessage("§cYou have been muted for $minutes minutes due to chat violations.")

        plugin.logger.info("Player ${player.name} has been auto-muted for $minutes minutes")
    }

    /**
     * Check if a player is muted
     */
    fun isPlayerMuted(player: Player): Boolean {
        val playerId = player.uniqueId.toString()
        val muteData = playerMutes[playerId] ?: return false

        if (muteData.endTime.isBefore(Instant.now())) {
            playerMutes.remove(playerId)
            return false
        }

        return true
    }

    /**
     * Get player's moderation statistics
     */
    fun getPlayerStatistics(player: Player): PlayerModerationStats {
        val playerId = player.uniqueId.toString()
        val violations = playerViolations[playerId] ?: emptyList()
        val warnings = playerWarnings[playerId] ?: 0
        val muteData = playerMutes[playerId]

        return PlayerModerationStats(
            totalViolations = violations.size,
            recentViolations = getRecentViolationCount(player),
            warnings = warnings,
            isMuted = muteData != null && muteData.endTime.isAfter(Instant.now()),
            muteEndTime = muteData?.endTime
        )
    }

    /**
     * Start cleanup task
     */
    private fun startCleanupTask() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            cleanupExpiredData()
        }, 20L * 60 * 5, 20L * 60 * 5) // Every 5 minutes
    }

    /**
     * Clean up expired data
     */
    private fun cleanupExpiredData() {
        val currentTime = Instant.now()

        // Clean up expired mutes
        playerMutes.entries.removeIf { (_, muteData) ->
            muteData.endTime.isBefore(currentTime)
        }

        // Clean up old message history
        val historyThreshold = currentTime.minusSeconds(300) // 5 minutes
        playerMessageHistory.values.forEach { messages ->
            messages.removeIf { it.timestamp < historyThreshold.toEpochMilli() }
        }

        // Clean up old violations
        playerViolations.keys.forEach { playerId ->
            cleanOldViolations(playerId)
        }

        plugin.debugLog("Moderation data cleanup completed")
    }

    /**
     * Reload moderation configuration
     */
    fun reload() {
        loadConfiguration()
        loadWordFilters()
        plugin.logger.info("Moderation configuration reloaded")
    }

    /**
     * Mute a player
     */
    fun mutePlayer(targetName: String, moderatorName: String, duration: String, reason: String): Boolean {
        return try {
            val player = plugin.server.getPlayer(targetName)
            val uuid = player?.uniqueId ?: plugin.databaseManager.getUserByName(targetName)?.uuid ?: return false

            val endTime = if (duration.equals("permanent", ignoreCase = true)) {
                -1L
            } else {
                parseDuration(duration)
            }

            val muteInfo = MuteInfo(
                uuid = uuid,
                playerName = targetName,
                mutedBy = plugin.server.getPlayer(moderatorName)?.uniqueId,
                reason = reason,
                startTime = System.currentTimeMillis(),
                endTime = endTime,
                permanent = endTime == -1L
            )

            plugin.databaseManager.savePlayerMute(muteInfo)
            plugin.databaseManager.saveModerationAction(
                uuid, targetName,
                plugin.server.getPlayer(moderatorName)?.uniqueId, moderatorName,
                "MUTE", reason, if (endTime == -1L) 0 else endTime - System.currentTimeMillis()
            )

            true
        } catch (e: Exception) {
            plugin.logger.warning("Error muting player $targetName: ${e.message}")
            false
        }
    }

    /**
     * Unmute a player
     */
    fun unmutePlayer(targetName: String): Boolean {
        return try {
            val player = plugin.server.getPlayer(targetName)
            val uuid = player?.uniqueId ?: plugin.databaseManager.getUserByName(targetName)?.uuid ?: return false

            plugin.databaseManager.removePlayerMute(uuid)
            plugin.databaseManager.saveModerationAction(
                uuid, targetName, null, "SYSTEM", "UNMUTE", "Manual unmute", 0
            )

            true
        } catch (e: Exception) {
            plugin.logger.warning("Error unmuting player $targetName: ${e.message}")
            false
        }
    }

    /**
     * Warn a player
     */
    fun warnPlayer(targetName: String, moderatorName: String, reason: String) {
        try {
            val player = plugin.server.getPlayer(targetName)
            val uuid = player?.uniqueId ?: plugin.databaseManager.getUserByName(targetName)?.uuid ?: return

            plugin.databaseManager.saveModerationAction(
                uuid, targetName,
                plugin.server.getPlayer(moderatorName)?.uniqueId, moderatorName,
                "WARN", reason, 0
            )

            // Update warning count
            val currentWarnings = playerWarnings.getOrDefault(uuid.toString(), 0)
            playerWarnings[uuid.toString()] = currentWarnings + 1

        } catch (e: Exception) {
            plugin.logger.warning("Error warning player $targetName: ${e.message}")
        }
    }

    /**
     * Get moderation history for a player
     */
    fun getModerationHistory(targetName: String): List<ModerationRecord> {
        val history = mutableListOf<ModerationRecord>()

        try {
            val player = plugin.server.getPlayer(targetName)
            val uuid = player?.uniqueId ?: plugin.databaseManager.getUserByName(targetName)?.uuid ?: return history

            val connection = plugin.databaseManager.getConnection()
            connection?.prepareStatement(
                "SELECT action_type, moderator_name, reason, timestamp FROM moderation_logs WHERE target_uuid = ? ORDER BY timestamp DESC LIMIT 20"
            )?.use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        history.add(
                            ModerationRecord(
                                action = rs.getString("action_type"),
                                moderator = rs.getString("moderator_name") ?: "SYSTEM",
                                reason = rs.getString("reason") ?: "",
                                timestamp = rs.getLong("timestamp")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting moderation history for $targetName: ${e.message}")
        }

        return history
    }

    /**
     * Get filter statistics
     */
    fun getFilterStatistics(): FilterStatistics {
        return FilterStatistics(
            profanityDetections = filterStats.getOrDefault("profanity", 0),
            spamDetections = filterStats.getOrDefault("spam", 0),
            capsDetections = filterStats.getOrDefault("caps", 0),
            advertisingDetections = filterStats.getOrDefault("advertising", 0),
            totalFilteredMessages = filterStats.values.sum()
        )
    }

    private fun parseDuration(duration: String): Long {
        val currentTime = System.currentTimeMillis()
        val regex = Regex("(\\d+)([smhd])")
        val matches = regex.findAll(duration.lowercase())

        var totalMillis = 0L
        for (match in matches) {
            val amount = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2]

            totalMillis += when (unit) {
                "s" -> amount * 1000
                "m" -> amount * 60 * 1000
                "h" -> amount * 60 * 60 * 1000
                "d" -> amount * 24 * 60 * 60 * 1000
                else -> 0
            }
        }

        return if (totalMillis > 0) currentTime + totalMillis else currentTime + (24 * 60 * 60 * 1000) // Default 1 day
    }

    // Statistics tracking
    private val filterStats = ConcurrentHashMap<String, Int>()

    /**
     * Data classes for admin interface
     */
    data class ModerationRecord(
        val action: String,
        val moderator: String,
        val reason: String,
        val timestamp: Long
    )

    data class FilterStatistics(
        val profanityDetections: Int,
        val spamDetections: Int,
        val capsDetections: Int,
        val advertisingDetections: Int,
        val totalFilteredMessages: Int
    )

    // Data classes and enums
    enum class FilterAction {
        ALLOW, FILTER, WARN, MUTE, KICK, BAN, BLOCK
    }

    enum class ViolationType {
        PROFANITY, SEVERE_PROFANITY, SPAM, EXCESSIVE_CAPS,
        REPEATED_CHARACTERS, ADVERTISING, TOXICITY
    }

    enum class FilterSeverity {
        LOW, MEDIUM, HIGH
    }

    data class ModerationResult(
        val action: FilterAction,
        val filteredMessage: String,
        val violations: List<ViolationType>
    )

    data class Violation(
        val type: ViolationType,
        val message: String,
        val channel: String,
        val timestamp: Instant,
        val severity: FilterSeverity
    )

    data class MuteData(
        val endTime: Instant,
        val reason: String
    )

    data class MessageData(
        val content: String,
        val timestamp: Long
    )

    data class PlayerModerationStats(
        val totalViolations: Int,
        val recentViolations: Int,
        val warnings: Int,
        val isMuted: Boolean,
        val muteEndTime: Instant?
    )
}
