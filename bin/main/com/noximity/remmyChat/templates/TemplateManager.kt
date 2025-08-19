package com.noximity.remmyChat.templates

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.models.GroupFormat
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Manages template processing and placeholder resolution for RemmyChat
 * Handles templates.yml configuration and provides dynamic template resolution
 */
class TemplateManager(private val plugin: RemmyChat) {

    // Template caches
    private val placeholderCache = ConcurrentHashMap<String, String>()
    private val hoverTemplateCache = ConcurrentHashMap<String, String>()
    private val messageFormatCache = ConcurrentHashMap<String, String>()
    private val rankTemplateCache = ConcurrentHashMap<String, Map<String, String>>()
    private val componentCache = ConcurrentHashMap<String, String>()

    // Compiled patterns for performance
    private val placeholderPattern = Pattern.compile("%([^%]+)%")
    private val hoverPattern = Pattern.compile("<hover:([^>]+)>([^<]+)</hover>")
    private val clickPattern = Pattern.compile("<click:([^>]+)>([^<]+)</click>")

    // Configuration
    private lateinit var templatesConfig: FileConfiguration

    // Cache settings
    private var cacheEnabled = true
    private var cacheSize = 200
    private var cacheTtl = 3600L // seconds
    private val cacheTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Initialize the template manager
     */
    fun initialize() {
        loadTemplatesConfig()
        loadPlaceholders()
        loadHoverTemplates()
        loadMessageFormats()
        loadRankTemplates()
        loadComponents()

        plugin.debugLog("TemplateManager initialized with ${placeholderCache.size} placeholders")
    }

    /**
     * Load templates configuration
     */
    private fun loadTemplatesConfig() {
        val configFile = java.io.File(plugin.dataFolder, "templates.yml")
        if (!configFile.exists()) {
            plugin.saveResource("templates.yml", false)
        }

        templatesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile)

        // Load cache settings
        cacheEnabled = templatesConfig.getBoolean("settings.processing.cache-compiled", true)
        cacheSize = templatesConfig.getInt("settings.processing.cache-size", 200)
        cacheTtl = templatesConfig.getLong("settings.processing.cache-ttl", 3600)
    }

    /**
     * Load custom placeholders from templates.yml
     */
    private fun loadPlaceholders() {
        // Load main placeholders section
        val placeholdersSection = templatesConfig.getConfigurationSection("placeholders")
        placeholdersSection?.getKeys(false)?.forEach { key ->
            val value = placeholdersSection.getString(key) ?: ""
            placeholderCache[key] = value
        }

        // Load templates section hovers
        val templatesHoversSection = templatesConfig.getConfigurationSection("templates.hovers")
        templatesHoversSection?.getKeys(false)?.forEach { key ->
            val value = templatesHoversSection.getString(key) ?: ""
            placeholderCache["hover-$key"] = value
        }

        // Load templates section name-styles
        val templatesNameStylesSection = templatesConfig.getConfigurationSection("templates.name-styles")
        templatesNameStylesSection?.getKeys(false)?.forEach { key ->
            val value = templatesNameStylesSection.getString(key) ?: ""
            placeholderCache["name-style-$key"] = value
        }

        // Load templates section click-actions
        val templatesClickActionsSection = templatesConfig.getConfigurationSection("templates.click-actions")
        templatesClickActionsSection?.getKeys(false)?.forEach { key ->
            val value = templatesClickActionsSection.getString(key) ?: ""
            placeholderCache["click-$key"] = value
        }

        plugin.debugLog("Loaded ${placeholderCache.size} placeholders from templates.yml")
    }

    /**
     * Load hover templates
     */
    private fun loadHoverTemplates() {
        val hoverSection = templatesConfig.getConfigurationSection("hover-templates")
        hoverSection?.getKeys(false)?.forEach { key ->
            val value = hoverSection.getString(key) ?: ""
            hoverTemplateCache[key] = value
        }
    }

    /**
     * Load message format templates
     */
    private fun loadMessageFormats() {
        val formatsSection = templatesConfig.getConfigurationSection("message-formats")
        formatsSection?.getKeys(false)?.forEach { key ->
            val value = formatsSection.getString(key) ?: ""
            messageFormatCache[key] = value
        }
    }

    /**
     * Load rank templates
     */
    private fun loadRankTemplates() {
        val ranksSection = templatesConfig.getConfigurationSection("rank-templates")
        ranksSection?.getKeys(false)?.forEach { rankName ->
            val rankSection = ranksSection.getConfigurationSection(rankName)
            if (rankSection != null) {
                val rankMap = mutableMapOf<String, String>()
                rankSection.getKeys(false).forEach { key ->
                    val value = rankSection.getString(key) ?: ""
                    rankMap[key] = value
                }
                rankTemplateCache[rankName] = rankMap
            }
        }
    }

    /**
     * Load component templates
     */
    private fun loadComponents() {
        val componentsSection = templatesConfig.getConfigurationSection("components")
        componentsSection?.getKeys(false)?.forEach { key ->
            val value = componentsSection.getString(key) ?: ""
            componentCache[key] = value
        }
    }

    /**
     * Process a template with placeholders
     */
    fun processTemplate(template: String, player: Player? = null, channel: Channel? = null, group: GroupFormat? = null): String {
        var processed = template

        // Apply cached placeholders
        placeholderCache.forEach { (key, value) ->
            processed = processed.replace("%$key%", value)
        }

        // Apply player-specific placeholders
        if (player != null) {
            processed = processed.replace("%player_name%", player.name)
            processed = processed.replace("%player_display_name%", PlainTextComponentSerializer.plainText().serialize(player.displayName()))
        }

        // Apply channel-specific placeholders
        if (channel != null) {
            processed = processed.replace("%channel_name%", channel.name)
            processed = processed.replace("%channel_display_name%", channel.getEffectiveDisplayName())
        }

        // Apply group-specific placeholders
        if (group != null) {
            processed = processed.replace("%group_name%", group.name)
            processed = processed.replace("%group_display_name%", group.displayName ?: group.name)
            processed = processed.replace("%group_prefix%", group.prefix ?: "")
        }

        return processed
    }

    /**
     * Get a placeholder value
     */
    fun getPlaceholder(key: String): String? {
        return placeholderCache[key]
    }

    /**
     * Get a hover template
     */
    fun getHoverTemplate(key: String): String? {
        return hoverTemplateCache[key]
    }

    /**
     * Get a message format
     */
    fun getMessageFormat(key: String): String? {
        return messageFormatCache[key]
    }

    /**
     * Reload templates
     */
    fun reload() {
        clearCache()
        initialize()
        plugin.debugLog("TemplateManager reloaded")
    }

    /**
     * Clear all caches
     */
    private fun clearCache() {
        placeholderCache.clear()
        hoverTemplateCache.clear()
        messageFormatCache.clear()
        rankTemplateCache.clear()
        componentCache.clear()
        cacheTimestamps.clear()
    }

    /**
     * Clean up expired cache entries
     */
    fun cleanupCache() {
        if (!cacheEnabled) return

        val currentTime = System.currentTimeMillis()
        val expiredKeys = cacheTimestamps.entries.filter {
            (currentTime - it.value) / 1000 > cacheTtl
        }.map { it.key }

        expiredKeys.forEach { key ->
            placeholderCache.remove(key)
            hoverTemplateCache.remove(key)
            messageFormatCache.remove(key)
            componentCache.remove(key)
            cacheTimestamps.remove(key)
        }

        plugin.debugLog("Cleaned up ${expiredKeys.size} expired template cache entries")
    }

    /**
     * Get template statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "placeholders" to placeholderCache.size,
            "hoverTemplates" to hoverTemplateCache.size,
            "messageFormats" to messageFormatCache.size,
            "rankTemplates" to rankTemplateCache.size,
            "components" to componentCache.size,
            "cacheEnabled" to cacheEnabled,
            "cacheSize" to cacheSize,
            "cacheTtl" to cacheTtl
        )
    }

    /**
     * Get placeholder cache for external access
     */
    fun getPlaceholderCache(): Map<String, String> {
        return placeholderCache.toMap()
    }

    /**
     * Set a placeholder value
     */
    fun setPlaceholder(key: String, value: String) {
        placeholderCache[key] = value
        cacheTimestamps[key] = System.currentTimeMillis()
    }
}
