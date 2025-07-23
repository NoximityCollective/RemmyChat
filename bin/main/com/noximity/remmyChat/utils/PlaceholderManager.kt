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
    private val customPlaceholders: MutableMap<String?, String?> = HashMap<String?, String?>()
    private var debugEnabled = false
    private var debugPlaceholderResolution = false

    init {
        updateDebugSettings()
        loadCustomPlaceholders()
    }

    /**
     * Updates debug settings from config
     */
    private fun updateDebugSettings() {
        this.debugEnabled = plugin
            .getConfig()
            .getBoolean("debug.enabled", false)
        this.debugPlaceholderResolution =
            debugEnabled &&
                    plugin
                        .getConfig()
                        .getBoolean("debug.placeholder-resolution", false)
    }

    /**
     * Loads custom placeholders from the configuration
     */
    fun loadCustomPlaceholders() {
        customPlaceholders.clear()
        updateDebugSettings()

        val placeholdersSection = plugin
            .getConfig()
            .getConfigurationSection("placeholders")
        if (placeholdersSection != null) {
            for (key in placeholdersSection.getKeys(false)) {
                val value = placeholdersSection.getString(key)
                if (value != null) {
                    customPlaceholders.put(key, value)
                    if (debugPlaceholderResolution) {
                        plugin
                            .getLogger()
                            .info("Loaded custom placeholder: %" + key + "%")
                    }
                }
            }
        }

        if (debugPlaceholderResolution) {
            plugin
                .getLogger()
                .info(
                    "Total placeholders loaded: " + customPlaceholders.size
                )
        }
    }

    /**
     * Applies all custom placeholders to a string, resolving nested placeholders
     * @param text The text to process
     * @return The text with custom placeholders replaced
     */
    fun applyCustomPlaceholders(text: String?): String {
        if (text == null) return ""
        return resolveAllPlaceholders(text)
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
        val dependencies: MutableMap<String?, MutableSet<String>> = HashMap<String?, MutableSet<String>>()
        for (key in mentionedPlaceholders) {
            dependencies.put(key, HashSet<String?>())

            val value = customPlaceholders.get(key)
            if (value != null) {
                val depMatcher: Matcher = PLACEHOLDER_PATTERN.matcher(value)
                while (depMatcher.find()) {
                    val depKey = depMatcher.group(1)
                    dependencies.get(key)!!.add(depKey!!)
                }
            }
        }

        // Resolve placeholders in order (placeholders without dependencies first)
        val resolvedValues: MutableMap<String?, String> = HashMap<String?, String>()
        val processingSet: MutableSet<String?> = HashSet<String?>()

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
        dependencies: MutableMap<String?, MutableSet<String>>,
        resolvedValues: MutableMap<String?, String>,
        processingSet: MutableSet<String?>,
        depth: Int
    ): String {
        // Check for infinite recursion
        if (depth > MAX_RECURSION_DEPTH) {
            plugin
                .getLogger()
                .warning(
                    "Maximum placeholder recursion depth exceeded for key: " +
                            key
                )
            return "⚠️ Recursion limit"
        }

        // Check for circular dependencies
        if (processingSet.contains(key)) {
            plugin
                .getLogger()
                .warning(
                    "Circular placeholder dependency detected for key: " + key
                )
            return "⚠️ Circular reference"
        }

        // Return cached resolved value if available
        if (resolvedValues.containsKey(key)) {
            return resolvedValues.get(key)!!
        }

        // Get the raw value
        var value = customPlaceholders.get(key)
        if (value == null) {
            return "%" + key + "%" // Placeholder not found, return as is
        }

        // Debug log
        if (debugPlaceholderResolution && depth == 0) {
            plugin
                .getLogger()
                .info("Resolving placeholder %" + key + "% = " + value)
        }

        // Mark as being processed (to detect cycles)
        processingSet.add(key)

        // Process dependencies first
        val deps = dependencies.getOrDefault(
            key,
            mutableSetOf<String?>()
        )
        for (depKey in deps) {
            if (customPlaceholders.containsKey(depKey)) {
                val depValue = resolvePlaceholderValue(
                    depKey,
                    dependencies,
                    resolvedValues,
                    processingSet,
                    depth + 1
                )

                if (debugPlaceholderResolution && depth == 0) {
                    plugin
                        .getLogger()
                        .info(" - Dependency %" + depKey + "% = " + depValue)
                }

                value = value.replace("%" + depKey + "%", depValue)
            }
        }

        // Remove from processing set
        processingSet.remove(key)

        // Cache and return the resolved value
        resolvedValues.put(key, value!!)

        if (debugPlaceholderResolution && depth == 0) {
            plugin
                .getLogger()
                .info("Final resolved value for %" + key + "% = " + value)
        }

        return value
    }

    /**
     * Applies only PlaceholderAPI placeholders to a string
     * @param player The player context for PlaceholderAPI
     * @param text The text to process
     * @return The text with PAPI placeholders replaced
     */
    fun applyPapiPlaceholders(player: Player?, text: String?): String {
        if (text == null) return ""

        // Apply PAPI placeholders if available
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") !=
            null &&
            player != null
        ) {
            return PlaceholderAPI.setPlaceholders(player, text)
        }

        return text
    }

    /**
     * Applies all placeholders (custom and PAPI) to a string
     * @param player The player context for PlaceholderAPI
     * @param text The text to process
     * @return The text with all placeholders replaced
     */
    fun applyAllPlaceholders(player: Player?, text: String?): String? {
        if (text == null) return ""

        // First apply our custom placeholders
        var result = applyCustomPlaceholders(text)

        // Then apply PAPI placeholders if available
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") !=
            null &&
            player != null
        ) {
            result = PlaceholderAPI.setPlaceholders(player, result)
        }

        return result
    }

    companion object {
        private const val MAX_RECURSION_DEPTH = 10
        private val PLACEHOLDER_PATTERN: Pattern = Pattern.compile(
            "%(\\w+(?:-\\w+)*)%"
        )
    }
}
