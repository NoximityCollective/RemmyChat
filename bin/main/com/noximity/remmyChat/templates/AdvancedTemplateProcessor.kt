package com.noximity.remmyChat.templates

import com.noximity.remmyChat.RemmyChat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.bukkit.entity.Player
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

class AdvancedTemplateProcessor(private val plugin: RemmyChat) {

    private val animationExecutor = Executors.newScheduledThreadPool(2)

    // Animation data
    private val activeAnimations = ConcurrentHashMap<String, AnimationData>()
    private val animationConfigs = ConcurrentHashMap<String, AnimationConfig>()

    // Statistics tracking
    private val templateRenders = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val conditionalEvaluations = AtomicLong(0)
    private val loopExecutions = AtomicLong(0)

    // Conditional template cache
    private val conditionalCache = ConcurrentHashMap<String, String>()
    private val conditionalEvaluator = ConditionalEvaluator()

    // Seasonal data
    private val seasonalTemplates = ConcurrentHashMap<String, SeasonalConfig>()
    private var currentSeason = ""

    // Configuration
    private var animationsEnabled = true
    private var conditionalsEnabled = true
    private var seasonalEnabled = true
    private var cacheEnabled = true
    private var maxCacheSize = 1000

    fun initialize() {
        plugin.debugLog("Initializing AdvancedTemplateProcessor...")
        loadConfiguration()
        loadAnimationConfigs()
        loadSeasonalConfigs()
        updateCurrentSeason()
        startAnimationTasks()
        plugin.debugLog("AdvancedTemplateProcessor initialized")
    }

    private fun loadConfiguration() {
        val templatesConfig = plugin.configManager.getTemplatesConfig()

        animationsEnabled = templatesConfig.getBoolean("advanced.animations.enabled", true)
        conditionalsEnabled = templatesConfig.getBoolean("advanced.conditionals.enabled", true)
        seasonalEnabled = templatesConfig.getBoolean("advanced.seasonal.enabled", true)
        cacheEnabled = templatesConfig.getBoolean("advanced.cache.enabled", true)
        maxCacheSize = templatesConfig.getInt("advanced.cache.max-size", 1000)

        plugin.debugLog("Advanced template configuration loaded")
    }

    private fun loadAnimationConfigs() {
        val templatesConfig = plugin.configManager.getTemplatesConfig()
        val animationsSection = templatesConfig.getConfigurationSection("animations")

        animationsSection?.getKeys(false)?.forEach { animationName ->
            val animationSection = animationsSection.getConfigurationSection(animationName)
            if (animationSection != null) {
                val frames = animationSection.getStringList("frames")
                val speed = animationSection.getLong("speed", 1000L)
                val loop = animationSection.getBoolean("loop", true)

                animationConfigs[animationName] = AnimationConfig(
                    name = animationName,
                    frames = frames,
                    speed = speed,
                    loop = loop
                )

                plugin.debugLog("Loaded animation config: $animationName (${frames.size} frames, ${speed}ms)")
            }
        }
    }

    private fun loadSeasonalConfigs() {
        val templatesConfig = plugin.configManager.getTemplatesConfig()
        val seasonalSection = templatesConfig.getConfigurationSection("seasonal")

        seasonalSection?.getKeys(false)?.forEach { seasonName ->
            val seasonSection = seasonalSection.getConfigurationSection(seasonName)
            if (seasonSection != null) {
                val activeDates = seasonSection.getStringList("active-dates")
                val decorations = mutableMapOf<String, String>()

                val decorationsSection = seasonSection.getConfigurationSection("decorations")
                decorationsSection?.getKeys(false)?.forEach { key ->
                    decorations[key] = decorationsSection.getString(key) ?: ""
                }

                val format = seasonSection.getString("format", "") ?: ""

                seasonalTemplates[seasonName] = SeasonalConfig(
                    name = seasonName,
                    activeDates = activeDates,
                    decorations = decorations,
                    format = format
                )

                plugin.debugLog("Loaded seasonal config: $seasonName")
            }
        }
    }

    /**
     * Process a template with advanced features
     */
    fun processAdvancedTemplate(
        template: String,
        player: Player? = null,
        context: Map<String, Any> = emptyMap()
    ): String {
        var processed = template

        // Process conditionals first
        if (conditionalsEnabled) {
            processed = processConditionals(processed, player, context)
        }

        // Process animations
        if (animationsEnabled) {
            processed = processAnimations(processed, player)
        }

        // Process seasonal content
        if (seasonalEnabled) {
            processed = processSeasonalContent(processed, context)
        }

        // Process dynamic placeholders
        processed = processDynamicPlaceholders(processed, player, context)

        return processed
    }

    /**
     * Process conditional templates
     */
    private fun processConditionals(template: String, player: Player?, context: Map<String, Any>): String {
        val conditionalPattern = Pattern.compile("\\{if:([^}]+)\\}(.+?)\\{/if\\}", Pattern.DOTALL)
        val matcher = conditionalPattern.matcher(template)
        var result = template

        while (matcher.find()) {
            val condition = matcher.group(1)
            val content = matcher.group(2)

            val shouldShow = conditionalEvaluator.evaluate(condition, player, context)

            if (shouldShow) {
                result = result.replace(matcher.group(0), content)
            } else {
                result = result.replace(matcher.group(0), "")
            }
        }

        // Process else conditions
        val elsePattern = Pattern.compile("\\{else:([^}]+)\\}(.+?)\\{/else\\}", Pattern.DOTALL)
        val elseMatcher = elsePattern.matcher(result)

        while (elseMatcher.find()) {
            val condition = elseMatcher.group(1)
            val content = elseMatcher.group(2)

            val shouldShow = !conditionalEvaluator.evaluate(condition, player, context)

            if (shouldShow) {
                result = result.replace(elseMatcher.group(0), content)
            } else {
                result = result.replace(elseMatcher.group(0), "")
            }
        }

        return result
    }

    /**
     * Process animations in template
     */
    private fun processAnimations(template: String, player: Player?): String {
        val animationPattern = Pattern.compile("\\{anim:([^}]+)\\}")
        val matcher = animationPattern.matcher(template)
        var result = template

        while (matcher.find()) {
            val animationName = matcher.group(1)
            val animationConfig = animationConfigs[animationName]

            if (animationConfig != null) {
                val playerId = player?.uniqueId?.toString() ?: "global"
                val animationKey = "${playerId}_$animationName"

                val currentFrame = getCurrentAnimationFrame(animationKey, animationConfig)
                result = result.replace(matcher.group(0), currentFrame)
            } else {
                // Animation not found, remove placeholder
                result = result.replace(matcher.group(0), "")
                plugin.debugLog("Animation not found: $animationName")
            }
        }

        return result
    }

    /**
     * Get current frame for an animation
     */
    private fun getCurrentAnimationFrame(animationKey: String, config: AnimationConfig): String {
        val animationData = activeAnimations.getOrPut(animationKey) {
            AnimationData(
                config = config,
                currentFrame = 0,
                lastUpdate = System.currentTimeMillis(),
                isActive = true
            )
        }

        val currentTime = System.currentTimeMillis()

        if (currentTime - animationData.lastUpdate >= config.speed) {
            animationData.currentFrame++

            if (animationData.currentFrame >= config.frames.size) {
                if (config.loop) {
                    animationData.currentFrame = 0
                } else {
                    animationData.currentFrame = config.frames.size - 1
                    animationData.isActive = false
                }
            }

            animationData.lastUpdate = currentTime
        }

        return if (animationData.currentFrame < config.frames.size) {
            config.frames[animationData.currentFrame]
        } else {
            config.frames.lastOrNull() ?: ""
        }
    }

    /**
     * Process seasonal content
     */
    private fun processSeasonalContent(template: String, context: Map<String, Any>): String {
        var result = template

        // Apply current seasonal template if active
        val activeSeason = getCurrentActiveSeason()
        if (activeSeason != null) {
            // Replace seasonal decorations
            activeSeason.decorations.forEach { (key, decoration) ->
                result = result.replace("%$key%", decoration)
            }

            // Apply seasonal format if template contains seasonal marker
            if (result.contains("{seasonal}") && activeSeason.format.isNotEmpty()) {
                result = result.replace("{seasonal}", activeSeason.format)
            }
        } else {
            // Remove seasonal markers if no season is active
            result = result.replace("{seasonal}", "")
        }

        return result
    }

    /**
     * Process dynamic placeholders
     */
    private fun processDynamicPlaceholders(template: String, player: Player?, context: Map<String, Any>): String {
        var result = template

        // Time-based placeholders
        val now = LocalTime.now()
        result = result.replace("%time_hour%", now.hour.toString())
        result = result.replace("%time_minute%", now.minute.toString())
        result = result.replace("%time_period%", if (now.hour < 12) "AM" else "PM")

        // Date-based placeholders
        val today = LocalDate.now()
        result = result.replace("%date_day%", today.dayOfMonth.toString())
        result = result.replace("%date_month%", today.monthValue.toString())
        result = result.replace("%date_year%", today.year.toString())
        result = result.replace("%date_weekday%", today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() })

        // Random placeholders
        result = result.replace("%random_number%", Random.nextInt(1, 101).toString())
        result = result.replace("%random_color%", getRandomColor())

        // Player-specific dynamic placeholders
        if (player != null) {
            result = result.replace("%player_world%", player.world.name)
            result = result.replace("%player_x%", player.location.blockX.toString())
            result = result.replace("%player_y%", player.location.blockY.toString())
            result = result.replace("%player_z%", player.location.blockZ.toString())
            result = result.replace("%player_health%", String.format("%.1f", player.health))
            result = result.replace("%player_food%", player.foodLevel.toString())
            result = result.replace("%player_level%", player.level.toString())
        }

        // Server dynamic placeholders
        result = result.replace("%server_online%", plugin.server.onlinePlayers.size.toString())
        result = result.replace("%server_max%", plugin.server.maxPlayers.toString())
        result = result.replace("%server_tps%", String.format("%.1f", plugin.server.tps[0]))

        // Context-specific placeholders
        context.forEach { (key, value) ->
            result = result.replace("%$key%", value.toString())
        }

        return result
    }

    /**
     * Get random color for dynamic content
     */
    private fun getRandomColor(): String {
        val colors = listOf(
            "<red>", "<green>", "<blue>", "<yellow>", "<aqua>",
            "<light_purple>", "<gold>", "<gray>", "<white>"
        )
        return colors[Random.nextInt(colors.size)]
    }

    /**
     * Update current season based on date
     */
    private fun updateCurrentSeason() {
        val today = LocalDate.now()
        val currentDate = String.format("%02d-%02d", today.monthValue, today.dayOfMonth)

        for ((seasonName, config) in seasonalTemplates) {
            for (dateRange in config.activeDates) {
                if (isDateInRange(currentDate, dateRange)) {
                    currentSeason = seasonName
                    plugin.debugLog("Current season updated to: $seasonName")
                    return
                }
            }
        }

        currentSeason = ""
    }

    /**
     * Check if current date is in seasonal range
     */
    private fun isDateInRange(currentDate: String, dateRange: String): Boolean {
        val parts = dateRange.split("-")
        if (parts.size != 2) return false

        val startDate = parts[0]
        val endDate = parts[1]

        return currentDate >= startDate && currentDate <= endDate
    }

    /**
     * Get currently active season
     */
    private fun getCurrentActiveSeason(): SeasonalConfig? {
        return if (currentSeason.isNotEmpty()) {
            seasonalTemplates[currentSeason]
        } else null
    }

    /**
     * Start animation update tasks
     */
    private fun startAnimationTasks() {
        if (!animationsEnabled) return

        // Animation update task
        animationExecutor.scheduleAtFixedRate({
            try {
                cleanupInactiveAnimations()
            } catch (e: Exception) {
                plugin.logger.warning("Error in animation cleanup task: ${e.message}")
            }
        }, 30L, 30L, TimeUnit.SECONDS)

        // Seasonal update task
        animationExecutor.scheduleAtFixedRate({
            try {
                updateCurrentSeason()
            } catch (e: Exception) {
                plugin.logger.warning("Error in seasonal update task: ${e.message}")
            }
        }, 1L, 1L, TimeUnit.HOURS)

        plugin.debugLog("Animation tasks started")
    }

    /**
     * Clean up inactive animations
     */
    private fun cleanupInactiveAnimations() {
        val currentTime = System.currentTimeMillis()
        val inactiveThreshold = 5 * 60 * 1000L // 5 minutes

        activeAnimations.entries.removeIf { (key, data) ->
            val isInactive = !data.isActive || (currentTime - data.lastUpdate) > inactiveThreshold
            if (isInactive) {
                plugin.debugLog("Cleaning up inactive animation: $key")
            }
            isInactive
        }

        // Clean cache if it's too large
        if (cacheEnabled && conditionalCache.size > maxCacheSize) {
            val keysToRemove = conditionalCache.keys.take(conditionalCache.size - maxCacheSize)
            keysToRemove.forEach { conditionalCache.remove(it) }
        }
    }

    /**
     * Force update animations for a player
     */
    fun updatePlayerAnimations(player: Player) {
        val playerId = player.uniqueId.toString()

        activeAnimations.keys.filter { it.startsWith(playerId) }.forEach { animationKey ->
            val animationData = activeAnimations[animationKey]
            if (animationData != null) {
                animationData.lastUpdate = 0L // Force immediate update
            }
        }
    }

    /**
     * Stop all animations for a player
     */
    fun stopPlayerAnimations(player: Player) {
        val playerId = player.uniqueId.toString()
        activeAnimations.keys.removeIf { it.startsWith(playerId) }
    }

    /**
     * Get animation statistics
     */
    fun getAnimationStatistics(): AnimationStatistics {
        return AnimationStatistics(
            activeAnimations = activeAnimations.size,
            configuredAnimations = animationConfigs.size,
            currentSeason = currentSeason,
            cacheSize = conditionalCache.size,
            seasonalConfigs = seasonalTemplates.size
        )
    }

    /**
     * Shutdown advanced template processor
     */
    fun shutdown() {
        animationExecutor.shutdown()
        try {
            if (!animationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                animationExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            animationExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Reload configuration
     */
    fun reload() {
        loadConfiguration()
        loadAnimationConfigs()
        loadSeasonalConfigs()
        updateCurrentSeason()
        activeAnimations.clear()
        conditionalCache.clear()
        plugin.logger.info("Advanced template processor configuration reloaded")
    }

    // Inner classes
    inner class ConditionalEvaluator {
        fun evaluate(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            return try {
                when {
                    condition.contains("==") -> evaluateEquals(condition, player, context)
                    condition.contains("!=") -> !evaluateEquals(condition.replace("!=", "=="), player, context)
                    condition.contains(">=") -> evaluateGreaterEquals(condition, player, context)
                    condition.contains("<=") -> evaluateLessEquals(condition, player, context)
                    condition.contains(">") -> evaluateGreater(condition, player, context)
                    condition.contains("<") -> evaluateLess(condition, player, context)
                    condition.contains("has_permission") -> evaluatePermission(condition, player)
                    condition.contains("in_world") -> evaluateWorld(condition, player)
                    condition.contains("is_online") -> player != null
                    condition.contains("is_op") -> player?.isOp ?: false
                    condition.contains("time_between") -> evaluateTimeBetween(condition)
                    else -> false
                }
            } catch (e: Exception) {
                plugin.debugLog("Error evaluating condition '$condition': ${e.message}")
                false
            }
        }

        private fun evaluateEquals(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            val parts = condition.split("==").map { it.trim() }
            if (parts.size != 2) return false

            val left = resolvePlaceholder(parts[0], player, context)
            val right = resolvePlaceholder(parts[1], player, context)

            return left == right
        }

        private fun evaluateGreaterEquals(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            val parts = condition.split(">=").map { it.trim() }
            if (parts.size != 2) return false

            val left = resolvePlaceholder(parts[0], player, context).toDoubleOrNull() ?: return false
            val right = resolvePlaceholder(parts[1], player, context).toDoubleOrNull() ?: return false

            return left >= right
        }

        private fun evaluateLessEquals(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            val parts = condition.split("<=").map { it.trim() }
            if (parts.size != 2) return false

            val left = resolvePlaceholder(parts[0], player, context).toDoubleOrNull() ?: return false
            val right = resolvePlaceholder(parts[1], player, context).toDoubleOrNull() ?: return false

            return left <= right
        }

        private fun evaluateGreater(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            val parts = condition.split(">").map { it.trim() }
            if (parts.size != 2) return false

            val left = resolvePlaceholder(parts[0], player, context).toDoubleOrNull() ?: return false
            val right = resolvePlaceholder(parts[1], player, context).toDoubleOrNull() ?: return false

            return left > right
        }

        private fun evaluateLess(condition: String, player: Player?, context: Map<String, Any>): Boolean {
            val parts = condition.split("<").map { it.trim() }
            if (parts.size != 2) return false

            val left = resolvePlaceholder(parts[0], player, context).toDoubleOrNull() ?: return false
            val right = resolvePlaceholder(parts[1], player, context).toDoubleOrNull() ?: return false

            return left < right
        }

        private fun evaluatePermission(condition: String, player: Player?): Boolean {
            val permissionPattern = Pattern.compile("has_permission\\('([^']+)'\\)")
            val matcher = permissionPattern.matcher(condition)

            return if (matcher.find() && player != null) {
                val permission = matcher.group(1)
                player.hasPermission(permission)
            } else false
        }

        private fun evaluateWorld(condition: String, player: Player?): Boolean {
            val worldPattern = Pattern.compile("in_world\\('([^']+)'\\)")
            val matcher = worldPattern.matcher(condition)

            return if (matcher.find() && player != null) {
                val worldName = matcher.group(1)
                player.world.name == worldName
            } else false
        }

        private fun evaluateTimeBetween(condition: String): Boolean {
            val timePattern = Pattern.compile("time_between\\('([^']+)', '([^']+)'\\)")
            val matcher = timePattern.matcher(condition)

            return if (matcher.find()) {
                val startTime = LocalTime.parse(matcher.group(1))
                val endTime = LocalTime.parse(matcher.group(2))
                val currentTime = LocalTime.now()

                if (startTime.isBefore(endTime)) {
                    currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
                } else {
                    // Crosses midnight
                    currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
                }
            } else false
        }

        private fun resolvePlaceholder(placeholder: String, player: Player?, context: Map<String, Any>): String {
            return when {
                placeholder.startsWith("'") && placeholder.endsWith("'") ->
                    placeholder.substring(1, placeholder.length - 1)
                placeholder.startsWith("%") && placeholder.endsWith("%") -> {
                    val key = placeholder.substring(1, placeholder.length - 1)
                    context[key]?.toString() ?: placeholder
                }
                placeholder == "player_level" -> player?.level?.toString() ?: "0"
                placeholder == "player_health" -> player?.health?.toString() ?: "0"
                placeholder == "server_online" -> plugin.server.onlinePlayers.size.toString()
                else -> placeholder
            }
        }
    }

    // Data classes
    data class AnimationConfig(
        val name: String,
        val frames: List<String>,
        val speed: Long,
        val loop: Boolean
    )

    data class AnimationData(
        val config: AnimationConfig,
        var currentFrame: Int,
        var lastUpdate: Long,
        var isActive: Boolean
    )

    data class SeasonalConfig(
        val name: String,
        val activeDates: List<String>,
        val decorations: Map<String, String>,
        val format: String
    )

    data class AnimationStatistics(
        val activeAnimations: Int,
        val configuredAnimations: Int,
        val currentSeason: String,
        val cacheSize: Int,
        val seasonalConfigs: Int
    )

    /**
     * Get template statistics for admin interface
     */
    fun getTemplateStatistics(): TemplateStatistics {
        return TemplateStatistics(
            templatesLoaded = animationConfigs.size,
            templateRenders = templateRenders.get(),
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            averageRenderTime = 1.5, // Placeholder - could be calculated
            conditionalEvaluations = conditionalEvaluations.get(),
            loopExecutions = loopExecutions.get()
        )
    }

    /**
     * Get available templates for admin interface
     */
    fun getAvailableTemplates(): List<String> {
        return animationConfigs.keys.toList() + seasonalTemplates.keys.toList()
    }



    data class TemplateStatistics(
        val templatesLoaded: Int,
        val templateRenders: Long,
        val cacheHits: Long,
        val cacheMisses: Long,
        val averageRenderTime: Double,
        val conditionalEvaluations: Long,
        val loopExecutions: Long
    )
}
