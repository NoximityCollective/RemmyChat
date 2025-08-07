package com.noximity.remmyChat.utils

import com.noximity.remmyChat.RemmyChat
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet

class PlaceholderManager(private val plugin: RemmyChat) {
    private val customPlaceholders: MutableMap<String, String> = HashMap<String, String>()
    private var debugEnabled = false
    private var debugPlaceholderResolution = false

    fun initialize() {
        updateDebugSettings()
        loadCustomPlaceholders()
    }

    /**
     * Updates debug settings from config
     */
    private fun updateDebugSettings() {
        try {
            this.debugEnabled = plugin.config.getBoolean("debug.enabled", false)
            this.debugPlaceholderResolution =
                debugEnabled && plugin.config.getBoolean("debug.placeholder-resolution", false)
        } catch (e: Exception) {
            // Config not ready yet, use defaults
            this.debugEnabled = false
            this.debugPlaceholderResolution = false
        }
    }

    /**
     * Loads custom placeholders from the TemplateManager
     */
    fun loadCustomPlaceholders() {
        customPlaceholders.clear()
        updateDebugSettings()

        // Load placeholders from config for now
        loadPlaceholdersFromConfig()
    }

    /**
     * Load placeholders from templates config
     */
    private fun loadPlaceholdersFromConfig() {
        // Try to load from templates.yml first
        try {
            val templatesFile = java.io.File(plugin.dataFolder, "templates.yml")
            if (templatesFile.exists()) {
                val templatesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(templatesFile)
                val placeholdersSection = templatesConfig.getConfigurationSection("placeholders")
                if (placeholdersSection != null) {
                    for (key in placeholdersSection.getKeys(false)) {
                        val value = placeholdersSection.getString(key)
                        if (value != null) {
                            customPlaceholders[key] = value
                            if (debugPlaceholderResolution) {
                                plugin.logger.info("Loaded custom placeholder from templates: %$key%")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load templates.yml: ${e.message}")
        }

        // Fallback to main config if needed
        if (customPlaceholders.isEmpty()) {
            val placeholdersSection = plugin.config.getConfigurationSection("placeholders")
            if (placeholdersSection != null) {
                for (key in placeholdersSection.getKeys(false)) {
                    val value = placeholdersSection.getString(key)
                    if (value != null) {
                        customPlaceholders[key] = value
                        if (debugPlaceholderResolution) {
                            plugin.logger.info("Loaded custom placeholder from config: %$key%")
                        }
                    }
                }
            }
        }

        if (debugPlaceholderResolution) {
            plugin.logger.info("Total placeholders loaded: ${customPlaceholders.size}")
        }
    }

    /**
     * Applies all custom placeholders to a string, resolving nested placeholders
     * @param text The text to process
     * @return The text with custom placeholders replaced
     */
    fun applyCustomPlaceholders(text: String): String {
        // Protect %message% placeholder from being processed
        val messageProtectionToken = "<<MESSAGE_PROTECTION_TOKEN>>"
        var result = text.replace("%message%", messageProtectionToken)

        result = resolveAllPlaceholders(result)

        // Restore protected %message% placeholder
        result = result.replace(messageProtectionToken, "%message%")

        return result
    }

    /**
     * Resolves all placeholders in a string, handling recursive dependencies properly
     * @param text The text containing placeholders to resolve
     * @return The text with all placeholders replaced
     */
    private fun resolveAllPlaceholders(text: String): String {
        // Get all placeholder keys mentioned in the text
        val mentionedPlaceholders: MutableSet<String> = HashSet<String>()
        val matcher: Matcher = PLACEHOLDER_PATTERN.matcher(text)
        while (matcher.find()) {
            val placeholderKey = matcher.group(1)
            mentionedPlaceholders.add(placeholderKey!!)
        }

        if (mentionedPlaceholders.isEmpty()) {
            return text // No placeholders to resolve
        }

        // Create a dependency graph and detect circular references
        val dependencies: MutableMap<String, MutableSet<String>> = HashMap<String, MutableSet<String>>()
        for (key in mentionedPlaceholders) {
            dependencies[key] = HashSet<String>()

            val value = customPlaceholders[key]
            if (value != null) {
                val depMatcher: Matcher = PLACEHOLDER_PATTERN.matcher(value)
                while (depMatcher.find()) {
                    val depKey = depMatcher.group(1)
                    dependencies[key]!!.add(depKey!!)
                }
            }
        }

        // Resolve placeholders in order (placeholders without dependencies first)
        val resolvedValues: MutableMap<String, String> = HashMap<String, String>()
        val processingSet: MutableSet<String> = HashSet<String>()

        for (key in mentionedPlaceholders) {
            resolvePlaceholderValue(
                key,
                dependencies,
                resolvedValues,
                processingSet,
                0
            )
        }

        // Apply all resolved placeholders to the text
        var result = text
        for (entry in resolvedValues.entries) {
            result = result.replace(
                "%" + entry.key + "%",
                entry.value
            )
        }

        return result
    }

    /**
     * Recursively resolves a placeholder's value, handling dependencies
     */
    private fun resolvePlaceholderValue(
        key: String,
        dependencies: MutableMap<String, MutableSet<String>>,
        resolvedValues: MutableMap<String, String>,
        processingSet: MutableSet<String>,
        depth: Int
    ): String {
        // Check for infinite recursion
        if (depth > MAX_RECURSION_DEPTH) {
            plugin.logger.warning("Maximum placeholder recursion depth exceeded for key: $key")
            return "⚠️ Recursion limit"
        }

        // Check for circular dependencies
        if (processingSet.contains(key)) {
            plugin.logger.warning("Circular placeholder dependency detected for key: $key")
            return "⚠️ Circular reference"
        }

        // Return cached resolved value if available
        if (resolvedValues.containsKey(key)) {
            return resolvedValues.get(key)!!
        }

        // Get the raw value
        var value = customPlaceholders[key]
        if (value == null) {
            return "%" + key + "%" // Placeholder not found, return as is
        }

        // Debug log
        if (debugPlaceholderResolution && depth == 0) {
            plugin.logger.info("Resolving placeholder %$key% = $value")
        }

        // Mark as being processed (to detect cycles)
        processingSet.add(key)

        // Process dependencies first
        val deps = dependencies.getOrDefault(
            key,
            mutableSetOf<String>()
        )
        for (depKey in deps) {
            if (customPlaceholders.containsKey(depKey)) {
                val depValue = resolvePlaceholderValue(
                    depKey!!,
                    dependencies,
                    resolvedValues,
                    processingSet,
                    depth + 1
                )

                if (debugPlaceholderResolution && depth == 0) {
                    plugin.logger.info(" - Dependency %$depKey% = $depValue")
                }

                value = value!!.replace("%" + depKey + "%", depValue)
            }
        }

        // Remove from processing set
        processingSet.remove(key)

        // Cache and return the resolved value
        resolvedValues[key] = value ?: ""

        if (debugPlaceholderResolution && depth == 0) {
            plugin.logger.info("Final resolved value for %$key% = $value")
        }

        return value ?: ""
    }

    /**
     * Refresh placeholders from TemplateManager after initialization
     */
    fun refreshFromTemplateManager() {
        try {
            val templateCache = plugin.templateManager.getPlaceholderCache()
            templateCache.forEach { (key, value) ->
                customPlaceholders[key] = value
            }
            if (debugPlaceholderResolution) {
                plugin.logger.info("Refreshed ${templateCache.size} placeholders from TemplateManager")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to refresh from TemplateManager: ${e.message}")
        }
    }

    /**
     * Applies only PlaceholderAPI placeholders to a string
     * @param player The player context for PlaceholderAPI
     * @param text The text to process
     * @return The text with PAPI placeholders replaced
     */
    fun applyPapiPlaceholders(player: Player, text: String): String {
        // Apply PAPI placeholders if available
        try {
            if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                return PlaceholderAPI.setPlaceholders(player, text)
            }
        } catch (e: NoClassDefFoundError) {
            // PlaceholderAPI classes not available, skip PAPI processing
        } catch (e: Exception) {
            // Other PlaceholderAPI error, skip PAPI processing
            plugin.logger.warning("Error applying PlaceholderAPI placeholders: ${e.message}")
        }

        return text
    }

    /**
     * Applies all placeholders to a string (including PlaceholderAPI if available)
     * @param player The player context
     * @param text The text to process
     * @return The text with all placeholders replaced
     */
    fun applyAllPlaceholders(player: Player, text: String): String {
        // Protect %message% placeholder from being processed
        val messageProtectionToken = "<<MESSAGE_PROTECTION_TOKEN>>"
        var result = text.replace("%message%", messageProtectionToken)

        // First apply our custom placeholders
        result = applyCustomPlaceholders(result)

        // Then apply PAPI placeholders if available
        try {
            if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                result = PlaceholderAPI.setPlaceholders(player, result)
            }
        } catch (e: NoClassDefFoundError) {
            // PlaceholderAPI classes not available, skip PAPI processing
        } catch (e: Exception) {
            // Other PlaceholderAPI error, skip PAPI processing
            plugin.logger.warning("Error applying PlaceholderAPI placeholders: ${e.message}")
        }

        // Restore protected %message% placeholder
        result = result.replace(messageProtectionToken, "%message%")

        return result
    }

    companion object {
        private const val MAX_RECURSION_DEPTH = 10
        private val PLACEHOLDER_PATTERN: Pattern = Pattern.compile(
            "%(\\w+(?:-\\w+)*)%"
        )
    }
}
