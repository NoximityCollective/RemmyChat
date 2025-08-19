package com.noximity.remmyChat.discord

import com.noximity.remmyChat.RemmyChat
import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Helper class for Discord configuration management and validation
 * Provides utilities to help users set up Discord integration correctly
 */
class DiscordConfigHelper(private val plugin: RemmyChat) {

    /**
     * Generate a sample discord.yml configuration based on actual Discord channels
     */
    fun generateSampleConfig(): String {
        val builder = StringBuilder()

        builder.appendLine("# ========================================")
        builder.appendLine("# RemmyChat Discord Configuration Helper")
        builder.appendLine("# ========================================")
        builder.appendLine()

        if (!plugin.isDiscordSRVEnabled) {
            builder.appendLine("# ERROR: DiscordSRV plugin not found!")
            builder.appendLine("# Please install DiscordSRV first before configuring Discord integration")
            return builder.toString()
        }

        try {
            val guild = DiscordSRV.getPlugin().mainGuild
            if (guild == null) {
                builder.appendLine("# ERROR: DiscordSRV guild not configured!")
                builder.appendLine("# Please configure DiscordSRV first")
                return builder.toString()
            }

            builder.appendLine("# Discord Server: ${guild.name}")
            builder.appendLine("# Available Discord Channels:")
            guild.textChannels.sortedBy { it.name }.forEach { channel ->
                builder.appendLine("#   - ${channel.name}")
            }
            builder.appendLine()

            builder.appendLine("# ========================================")
            builder.appendLine("# SUGGESTED CONFIGURATION")
            builder.appendLine("# ========================================")
            builder.appendLine()

            // Generate integration section
            builder.appendLine("integration:")
            builder.appendLine("  enabled: true")
            builder.appendLine("  auto-detect: true")
            builder.appendLine("  mode: \"bidirectional\"")
            builder.appendLine("  validate-on-startup: true")
            builder.appendLine("  require-discordsrv: true")
            builder.appendLine()

            // Generate channels section based on available channels and RemmyChat channels
            builder.appendLine("channels:")

            val remmyChannels = plugin.configManager.channels.keys
            val discordChannelNames = guild.textChannels.map { it.name }.toSet()

            remmyChannels.forEach { remmyChannel ->
                builder.appendLine("  $remmyChannel:")
                builder.appendLine("    enabled: true")

                // Suggest best matching Discord channel
                val suggestedDiscordChannel = findBestDiscordChannelMatch(remmyChannel, discordChannelNames)
                builder.appendLine("    discord-channel: \"$suggestedDiscordChannel\"")

                // Set appropriate direction based on channel type
                val direction = when (remmyChannel) {
                    "event", "announcements" -> "mc-to-discord"
                    "admin", "staff" -> "both"
                    else -> "both"
                }
                builder.appendLine("    direction: \"$direction\"")

                // Add role restrictions for staff channels
                if (remmyChannel in listOf("admin", "staff")) {
                    builder.appendLine("    allowed-roles:")
                    when (remmyChannel) {
                        "admin" -> {
                            builder.appendLine("      - \"Admin\"")
                            builder.appendLine("      - \"Owner\"")
                        }
                        "staff" -> {
                            builder.appendLine("      - \"Staff\"")
                            builder.appendLine("      - \"Admin\"")
                            builder.appendLine("      - \"Moderator\"")
                        }
                    }
                }

                // Add special settings for help channel
                if (remmyChannel == "help") {
                    builder.appendLine("    auto-ping-staff: true")
                    builder.appendLine("    staff-role: \"Staff\"")
                }

                builder.appendLine()
            }

            // Add formatting section
            builder.appendLine("formatting:")
            builder.appendLine("  minecraft-to-discord:")
            builder.appendLine("    format: \"**%player_name%** (%server%): %message%\"")
            builder.appendLine("    channel-formats:")
            remmyChannels.forEach { channel ->
                val format = when (channel) {
                    "global" -> "**%player_name%**: %message%"
                    "staff" -> "**[Staff]** %player_name%: %message%"
                    "admin" -> "**[Admin]** %player_name%: %message%"
                    "trade" -> "**[Trade]** %player_name%: %message%"
                    "help" -> "**[Help]** %player_name%: %message%"
                    "event" -> "📢 **[Event]** %message%"
                    else -> "**[${channel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}]** %player_name%: %message%"
                }
                builder.appendLine("      $channel: \"$format\"")
            }
            builder.appendLine()

            builder.appendLine("  discord-to-minecraft:")
            builder.appendLine("    format: \"<blue>[Discord]</blue> <gray>%username%</gray>: %message%\"")
            builder.appendLine("    channel-formats:")
            remmyChannels.forEach { channel ->
                val format = when (channel) {
                    "global" -> "<blue>[Discord]</blue> <gray>%username%</gray>: %message%"
                    "staff" -> "<gold>[Discord-Staff]</gold> <yellow>%username%</yellow>: %message%"
                    "admin" -> "<red>[Discord-Admin]</red> <red>%username%</red>: %message%"
                    "trade" -> "<green>[Discord-Trade]</green> <gray>%username%</gray>: %message%"
                    "help" -> "<aqua>[Discord-Help]</aqua> <gray>%username%</gray>: %message%"
                    else -> "<blue>[Discord-${channel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}]</blue> <gray>%username%</gray>: %message%"
                }
                builder.appendLine("      $channel: \"$format\"")
            }

        } catch (e: Exception) {
            builder.appendLine("# ERROR: Failed to generate configuration: ${e.message}")
        }

        return builder.toString()
    }

    /**
     * Find the best matching Discord channel for a RemmyChat channel
     */
    private fun findBestDiscordChannelMatch(remmyChannel: String, discordChannels: Set<String>): String {
        // Direct match
        if (discordChannels.contains(remmyChannel)) {
            return remmyChannel
        }

        // Common mappings
        val commonMappings = mapOf(
            "global" to listOf("general", "chat", "main"),
            "trade" to listOf("marketplace", "trading", "trade", "market"),
            "help" to listOf("support", "help", "assistance"),
            "staff" to listOf("staff", "moderator", "mod"),
            "admin" to listOf("admin", "administrator"),
            "event" to listOf("announcements", "events", "news"),
            "local" to listOf("local", "general")
        )

        // Try common mappings
        commonMappings[remmyChannel]?.forEach { suggestion ->
            if (discordChannels.contains(suggestion)) {
                return suggestion
            }
        }

        // Try case-insensitive match
        discordChannels.find { it.equals(remmyChannel, ignoreCase = true) }?.let {
            return it
        }

        // Try partial matches
        discordChannels.find { channel ->
            channel.contains(remmyChannel, ignoreCase = true) ||
            remmyChannel.contains(channel, ignoreCase = true)
        }?.let {
            return it
        }

        // Fallback to general or first available channel
        return when {
            discordChannels.contains("general") -> "general"
            discordChannels.contains("chat") -> "chat"
            discordChannels.isNotEmpty() -> discordChannels.first()
            else -> "general"
        }
    }

    /**
     * Validate current Discord configuration and provide suggestions
     */
    fun validateConfiguration(): DiscordConfigValidation {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if DiscordSRV is available
        if (!plugin.isDiscordSRVEnabled) {
            issues.add("DiscordSRV plugin not found or not enabled")
            suggestions.add("Install and configure DiscordSRV plugin first")
            return DiscordConfigValidation(false, issues, suggestions, warnings)
        }

        // Check if Discord integration is enabled
        if (!plugin.configManager.isDiscordEnabled()) {
            issues.add("Discord integration is disabled in discord.yml")
            suggestions.add("Set 'integration.enabled: true' in discord.yml")
        }

        // Check if discord.yml exists
        val discordFile = File(plugin.dataFolder, "discord.yml")
        if (!discordFile.exists()) {
            issues.add("discord.yml configuration file not found")
            suggestions.add("Create discord.yml configuration file")
            return DiscordConfigValidation(false, issues, suggestions, warnings)
        }

        try {
            val guild = DiscordSRV.getPlugin().mainGuild
            if (guild == null) {
                issues.add("DiscordSRV guild not configured")
                suggestions.add("Configure DiscordSRV's main guild setting")
                return DiscordConfigValidation(false, issues, suggestions, warnings)
            }

            val discordChannelNames = guild.textChannels.map { it.name }.toSet()
            val channelMappings = plugin.discordSRVIntegration.getChannelMappings()

            // Validate each channel mapping
            channelMappings.forEach { (remmyChannel, discordChannel) ->
                if (!discordChannelNames.contains(discordChannel)) {
                    issues.add("Discord channel '$discordChannel' (mapped from '$remmyChannel') not found in server")

                    val bestMatch = findBestDiscordChannelMatch(remmyChannel, discordChannelNames)
                    suggestions.add("Consider changing '$remmyChannel.discord-channel' to '$bestMatch'")
                }

                // Check direction setting
                val direction = getChannelDirection(remmyChannel)
                if (direction !in listOf("both", "bidirectional", "minecraft-to-discord", "mc-to-discord", "discord-to-minecraft")) {
                    warnings.add("Channel '$remmyChannel' has invalid direction '$direction'")
                    suggestions.add("Set '$remmyChannel.direction' to one of: both, minecraft-to-discord, discord-to-minecraft")
                }
            }

            // Check for missing channel mappings
            plugin.configManager.channels.keys.forEach { remmyChannel ->
                if (!channelMappings.containsKey(remmyChannel)) {
                    warnings.add("RemmyChat channel '$remmyChannel' has no Discord mapping")

                    val bestMatch = findBestDiscordChannelMatch(remmyChannel, discordChannelNames)
                    suggestions.add("Add Discord mapping for '$remmyChannel' -> '$bestMatch' in discord.yml")
                }
            }

        } catch (e: Exception) {
            issues.add("Error validating Discord configuration: ${e.message}")
        }

        val isValid = issues.isEmpty()
        return DiscordConfigValidation(isValid, issues, suggestions, warnings)
    }

    /**
     * Generate a corrected discord.yml configuration
     */
    fun generateCorrectedConfig(): String {
        val builder = StringBuilder()

        builder.appendLine("# ========================================")
        builder.appendLine("# AUTO-CORRECTED DISCORD CONFIGURATION")
        builder.appendLine("# Generated by RemmyChat Discord Config Helper")
        builder.appendLine("# ========================================")
        builder.appendLine()

        try {
            // Copy the existing configuration structure but fix channel mappings
            val currentConfig = plugin.configManager.getDiscordConfig()

            // Integration section
            builder.appendLine("integration:")
            builder.appendLine("  enabled: ${currentConfig.getBoolean("integration.enabled", true)}")
            builder.appendLine("  auto-detect: ${currentConfig.getBoolean("integration.auto-detect", true)}")
            builder.appendLine("  mode: \"${currentConfig.getString("integration.mode", "bidirectional")}\"")
            builder.appendLine("  validate-on-startup: ${currentConfig.getBoolean("integration.validate-on-startup", true)}")
            builder.appendLine("  require-discordsrv: ${currentConfig.getBoolean("integration.require-discordsrv", true)}")
            builder.appendLine()

            // Bot section
            builder.appendLine("bot:")
            val botSection = currentConfig.getConfigurationSection("bot")
            if (botSection != null) {
                builder.appendLine("  presence:")
                builder.appendLine("    enabled: ${botSection.getBoolean("presence.enabled", true)}")
                builder.appendLine("    update-interval: ${botSection.getInt("presence.update-interval", 300)}")
                builder.appendLine("    activity-type: \"${botSection.getString("presence.activity-type", "playing")}\"")
                builder.appendLine("    activity-message: \"${botSection.getString("presence.activity-message", "with %online_players% players")}\"")
                builder.appendLine("  commands:")
                builder.appendLine("    enabled: ${botSection.getBoolean("commands.enabled", false)}")
                builder.appendLine("    prefix: \"${botSection.getString("commands.prefix", "!")}\"")
            }
            builder.appendLine()

            // Channels section with corrected mappings
            builder.appendLine("channels:")

            if (plugin.isDiscordSRVEnabled) {
                val guild = DiscordSRV.getPlugin().mainGuild
                if (guild != null) {
                    val discordChannelNames = guild.textChannels.map { it.name }.toSet()

                    plugin.configManager.channels.keys.forEach { remmyChannel ->
                        val currentChannelSection = currentConfig.getConfigurationSection("channels.$remmyChannel")
                        val isEnabled = currentChannelSection?.getBoolean("enabled", true) ?: true
                        val currentDirection = currentChannelSection?.getString("direction", "both") ?: "both"
                        val currentDiscordChannel = currentChannelSection?.getString("discord-channel", remmyChannel) ?: remmyChannel

                        builder.appendLine("  $remmyChannel:")
                        builder.appendLine("    enabled: $isEnabled")

                        // Find best matching Discord channel
                        val bestMatch = if (discordChannelNames.contains(currentDiscordChannel)) {
                            currentDiscordChannel
                        } else {
                            findBestDiscordChannelMatch(remmyChannel, discordChannelNames)
                        }

                        builder.appendLine("    discord-channel: \"$bestMatch\"")

                        if (bestMatch != currentDiscordChannel) {
                            builder.appendLine("    # CORRECTED: was '$currentDiscordChannel', suggested '$bestMatch'")
                        }

                        builder.appendLine("    direction: \"$currentDirection\"")

                        // Add role restrictions if they exist
                        val allowedRoles = currentChannelSection?.getStringList("allowed-roles")
                        if (!allowedRoles.isNullOrEmpty()) {
                            builder.appendLine("    allowed-roles:")
                            allowedRoles.forEach { role ->
                                builder.appendLine("      - \"$role\"")
                            }
                        }

                        // Add special settings
                        if (currentChannelSection?.getBoolean("auto-ping-staff") == true) {
                            builder.appendLine("    auto-ping-staff: true")
                            val staffRole = currentChannelSection.getString("staff-role", "Staff")
                            builder.appendLine("    staff-role: \"$staffRole\"")
                        }

                        builder.appendLine()
                    }
                }
            }

            // Copy formatting section
            val formattingSection = currentConfig.getConfigurationSection("formatting")
            if (formattingSection != null) {
                builder.appendLine("formatting:")
                copyConfigSection(formattingSection, builder, 1)
                builder.appendLine()
            }

            // Copy other sections
            listOf("content", "events", "advanced", "moderation", "performance", "debug", "security").forEach { sectionName ->
                val section = currentConfig.getConfigurationSection(sectionName)
                if (section != null) {
                    builder.appendLine("$sectionName:")
                    copyConfigSection(section, builder, 1)
                    builder.appendLine()
                }
            }

        } catch (e: Exception) {
            builder.appendLine("# ERROR generating corrected configuration: ${e.message}")
        }

        return builder.toString()
    }

    /**
     * Copy a configuration section to the builder with proper indentation
     */
    private fun copyConfigSection(section: org.bukkit.configuration.ConfigurationSection, builder: StringBuilder, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)

        section.getKeys(false).forEach { key ->
            val value = section.get(key)
            when (value) {
                is org.bukkit.configuration.ConfigurationSection -> {
                    builder.appendLine("$indent$key:")
                    copyConfigSection(value, builder, indentLevel + 1)
                }
                is List<*> -> {
                    builder.appendLine("$indent$key:")
                    value.forEach { item ->
                        builder.appendLine("$indent  - \"$item\"")
                    }
                }
                is String -> {
                    builder.appendLine("$indent$key: \"$value\"")
                }
                else -> {
                    builder.appendLine("$indent$key: $value")
                }
            }
        }
    }

    /**
     * Save the corrected configuration to a backup file
     */
    fun saveCorrectedConfigToFile(): File {
        val correctedConfig = generateCorrectedConfig()
        val backupFile = File(plugin.dataFolder, "discord-corrected.yml")

        backupFile.writeText(correctedConfig)

        plugin.logger.info("✅ Corrected Discord configuration saved to: ${backupFile.name}")
        return backupFile
    }

    /**
     * Create a simple quick-fix configuration for common setups
     */
    fun createQuickFixConfig(): String {
        val builder = StringBuilder()

        builder.appendLine("# ========================================")
        builder.appendLine("# QUICK FIX - Basic Discord Configuration")
        builder.appendLine("# ========================================")
        builder.appendLine()
        builder.appendLine("integration:")
        builder.appendLine("  enabled: true")
        builder.appendLine("  mode: \"bidirectional\"")
        builder.appendLine()
        builder.appendLine("channels:")
        builder.appendLine("  global:")
        builder.appendLine("    enabled: true")
        builder.appendLine("    discord-channel: \"general\"")
        builder.appendLine("    direction: \"both\"")
        builder.appendLine()
        builder.appendLine("  staff:")
        builder.appendLine("    enabled: true")
        builder.appendLine("    discord-channel: \"staff\"")
        builder.appendLine("    direction: \"both\"")
        builder.appendLine()
        builder.appendLine("  trade:")
        builder.appendLine("    enabled: true")
        builder.appendLine("    discord-channel: \"trade\"  # Change to your actual channel name")
        builder.appendLine("    direction: \"both\"")
        builder.appendLine()
        builder.appendLine("formatting:")
        builder.appendLine("  minecraft-to-discord:")
        builder.appendLine("    format: \"**%player_name%**: %message%\"")
        builder.appendLine("  discord-to-minecraft:")
        builder.appendLine("    format: \"<blue>[Discord]</blue> <gray>%username%</gray>: %message%\"")

        return builder.toString()
    }

    /**
     * Check for common configuration issues
     */
    fun diagnoseIssues(): List<String> {
        val issues = mutableListOf<String>()

        // Check basic setup
        if (!plugin.isDiscordSRVEnabled) {
            issues.add("❌ DiscordSRV plugin not found")
            return issues
        }

        if (!plugin.configManager.isDiscordEnabled()) {
            issues.add("❌ Discord integration disabled in config")
            return issues
        }

        try {
            val guild = DiscordSRV.getPlugin().mainGuild
            if (guild == null) {
                issues.add("❌ DiscordSRV main guild not configured")
                return issues
            }

            val jda = DiscordSRV.getPlugin().jda
            if (jda == null) {
                issues.add("❌ DiscordSRV JDA instance not available")
                return issues
            }

            // Check channel mappings
            val mappings = plugin.discordSRVIntegration.getChannelMappings()
            if (mappings.isEmpty()) {
                issues.add("⚠️  No Discord channel mappings configured")
            }

            val discordChannels = guild.textChannels.map { it.name }.toSet()
            mappings.forEach { (remmyChannel, discordChannel) ->
                if (!discordChannels.contains(discordChannel)) {
                    issues.add("❌ Discord channel '$discordChannel' (for '$remmyChannel') not found")
                }
            }

            // Note: Permission checks removed to avoid import complexity
            // Bot permissions should be configured properly in Discord server settings

        } catch (e: Exception) {
            issues.add("❌ Error during diagnosis: ${e.message}")
        }

        return issues
    }

    /**
     * Get the direction setting for a channel from discord.yml
     */
    private fun getChannelDirection(channelName: String): String {
        val channelSection = plugin.configManager.getDiscordConfig().getConfigurationSection("channels.$channelName")
        return channelSection?.getString("direction", "both") ?: "both"
    }

    /**
     * Data class for validation results
     */
    data class DiscordConfigValidation(
        val isValid: Boolean,
        val issues: List<String>,
        val suggestions: List<String>,
        val warnings: List<String>
    )
}
