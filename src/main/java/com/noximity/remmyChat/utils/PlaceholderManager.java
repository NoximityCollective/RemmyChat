package com.noximity.remmyChat.utils;

import com.noximity.remmyChat.RemmyChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderManager {

    private final RemmyChat plugin;
    private final Map<String, String> customPlaceholders = new HashMap<>();
    private static final int MAX_RECURSION_DEPTH = 10;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(\\w+(?:-\\w+)*)%");
    private boolean debugEnabled;
    private boolean debugPlaceholderResolution;

    public PlaceholderManager(RemmyChat plugin) {
        this.plugin = plugin;
        updateDebugSettings();
        loadCustomPlaceholders();
    }

    /**
     * Updates debug settings from config
     */
    private void updateDebugSettings() {
        this.debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        this.debugPlaceholderResolution = debugEnabled && plugin.getConfig().getBoolean("debug.placeholder-resolution", false);
    }

    /**
     * Loads custom placeholders from the configuration
     */
    public void loadCustomPlaceholders() {
        customPlaceholders.clear();
        updateDebugSettings();

        ConfigurationSection placeholdersSection = plugin.getConfig().getConfigurationSection("placeholders");
        if (placeholdersSection != null) {
            for (String key : placeholdersSection.getKeys(false)) {
                String value = placeholdersSection.getString(key);
                if (value != null) {
                    customPlaceholders.put(key, value);
                    if (debugPlaceholderResolution) {
                        plugin.getLogger().info("Loaded custom placeholder: %" + key + "%");
                    }
                }
            }
        }

        if (debugPlaceholderResolution) {
            plugin.getLogger().info("Total placeholders loaded: " + customPlaceholders.size());
        }
    }

    /**
     * Applies all custom placeholders to a string, resolving nested placeholders
     * @param text The text to process
     * @return The text with custom placeholders replaced
     */
    public String applyCustomPlaceholders(String text) {
        if (text == null) return "";
        return resolveAllPlaceholders(text);
    }

    /**
     * Resolves all placeholders in a string, handling recursive dependencies properly
     * @param text The text containing placeholders to resolve
     * @return The text with all placeholders replaced
     */
    private String resolveAllPlaceholders(String text) {
        // Get all placeholder keys mentioned in the text
        Set<String> mentionedPlaceholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            mentionedPlaceholders.add(placeholderKey);
        }

        if (mentionedPlaceholders.isEmpty()) {
            return text; // No placeholders to resolve
        }

        // Create a dependency graph and detect circular references
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String key : mentionedPlaceholders) {
            dependencies.put(key, new HashSet<>());

            String value = customPlaceholders.get(key);
            if (value != null) {
                Matcher depMatcher = PLACEHOLDER_PATTERN.matcher(value);
                while (depMatcher.find()) {
                    String depKey = depMatcher.group(1);
                    dependencies.get(key).add(depKey);
                }
            }
        }

        // Resolve placeholders in order (placeholders without dependencies first)
        Map<String, String> resolvedValues = new HashMap<>();
        Set<String> processingSet = new HashSet<>();

        for (String key : mentionedPlaceholders) {
            resolvePlaceholderValue(key, dependencies, resolvedValues, processingSet, 0);
        }

        // Apply all resolved placeholders to the text
        String result = text;
        for (Map.Entry<String, String> entry : resolvedValues.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return result;
    }

    /**
     * Recursively resolves a placeholder's value, handling dependencies
     */
    private String resolvePlaceholderValue(String key, Map<String, Set<String>> dependencies,
                                          Map<String, String> resolvedValues,
                                          Set<String> processingSet, int depth) {
        // Check for infinite recursion
        if (depth > MAX_RECURSION_DEPTH) {
            plugin.getLogger().warning("Maximum placeholder recursion depth exceeded for key: " + key);
            return "⚠️ Recursion limit";
        }

        // Check for circular dependencies
        if (processingSet.contains(key)) {
            plugin.getLogger().warning("Circular placeholder dependency detected for key: " + key);
            return "⚠️ Circular reference";
        }

        // Return cached resolved value if available
        if (resolvedValues.containsKey(key)) {
            return resolvedValues.get(key);
        }

        // Get the raw value
        String value = customPlaceholders.get(key);
        if (value == null) {
            return "%" + key + "%"; // Placeholder not found, return as is
        }

        // Debug log
        if (debugPlaceholderResolution && depth == 0) {
            plugin.getLogger().info("Resolving placeholder %" + key + "% = " + value);
        }

        // Mark as being processed (to detect cycles)
        processingSet.add(key);

        // Process dependencies first
        Set<String> deps = dependencies.getOrDefault(key, Collections.emptySet());
        for (String depKey : deps) {
            if (customPlaceholders.containsKey(depKey)) {
                String depValue = resolvePlaceholderValue(depKey, dependencies, resolvedValues, processingSet, depth + 1);

                if (debugPlaceholderResolution && depth == 0) {
                    plugin.getLogger().info(" - Dependency %" + depKey + "% = " + depValue);
                }

                value = value.replace("%" + depKey + "%", depValue);
            }
        }

        // Remove from processing set
        processingSet.remove(key);

        // Cache and return the resolved value
        resolvedValues.put(key, value);

        if (debugPlaceholderResolution && depth == 0) {
            plugin.getLogger().info("Final resolved value for %" + key + "% = " + value);
        }

        return value;
    }

    /**
     * Applies all placeholders (custom and PAPI) to a string
     * @param player The player context for PlaceholderAPI
     * @param text The text to process
     * @return The text with all placeholders replaced
     */
    public String applyAllPlaceholders(Player player, String text) {
        if (text == null) return "";

        // First apply our custom placeholders
        String result = applyCustomPlaceholders(text);

        // Then apply PAPI placeholders if available
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && player != null) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }

        return result;
    }
}
