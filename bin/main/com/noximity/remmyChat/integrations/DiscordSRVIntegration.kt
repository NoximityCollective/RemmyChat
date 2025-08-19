package com.noximity.remmyChat.integrations

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.compatibility.VersionCompatibility
import com.noximity.remmyChat.events.RemmyChatMessageEvent
import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.api.ListenerPriority
import github.scarsz.discordsrv.api.Subscribe
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild
import github.scarsz.discordsrv.util.DiscordUtil
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class DiscordSRVIntegration(private val plugin: RemmyChat) : Listener {
    private var isDiscordSRVEnabled = false
    private val channelMappings = mutableMapOf<String, String>()

    fun initialize() {
        plugin.debugLog("Initializing DiscordSRV integration...")

        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) {
            plugin.logger.info("DiscordSRV plugin not found - Discord integration disabled")
            plugin.debugLog("DiscordSRV not found. Discord integration disabled.")
            return
        }

        // Check if Discord integration is enabled in config
        if (!plugin.configManager.isDiscordEnabled()) {
            plugin.logger.info("Discord integration disabled in config (set integration.enabled: true in discord.yml to enable)")
            plugin.debugLog("Discord integration disabled in config (integration.enabled: false)")
            return
        }

        try {
            // Load channel mappings from config
            loadChannelMappings()

            // Register DiscordSRV listener
            DiscordSRV.api.subscribe(this)

            isDiscordSRVEnabled = true
            plugin.logger.info("✅ DiscordSRV integration enabled successfully!")
            plugin.logger.info("📋 Loaded ${channelMappings.size} channel mappings")
            plugin.debugLog("Channel mappings loaded: $channelMappings")

            // Validate channels after a short delay to allow DiscordSRV to fully initialize
            plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
                validateAndReportChannels()
            }, 60L) // 3 second delay
        } catch (e: Exception) {
            plugin.logger.warning("❌ Failed to initialize DiscordSRV integration: ${e.message}")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        if (isDiscordSRVEnabled) {
            try {
                DiscordSRV.api.unsubscribe(this)
                isDiscordSRVEnabled = false
                channelMappings.clear()
                plugin.logger.info("🔌 DiscordSRV integration shutdown")
                plugin.debugLog("DiscordSRV integration shutdown.")
            } catch (e: Exception) {
                plugin.logger.warning("Error during DiscordSRV integration shutdown: ${e.message}")
            }
        }
    }

    private fun loadChannelMappings() {
        channelMappings.clear()

        // Load from channels section in discord config
        val channelsConfig = plugin.configManager.getDiscordConfig().getConfigurationSection("channels")
        channelsConfig?.getKeys(false)?.forEach { remmyChannel ->
            val channelSection = channelsConfig.getConfigurationSection(remmyChannel)
            if (channelSection != null) {
                val enabled = channelSection.getBoolean("enabled", true)
                if (enabled) {
                    val discordChannel = channelSection.getString("discord-channel")
                    if (discordChannel != null) {
                        channelMappings[remmyChannel] = discordChannel
                        plugin.debugLog("Mapped RemmyChat channel '$remmyChannel' to Discord channel '$discordChannel'")
                    }
                }
            }
        }

        // Add fallback mappings for channels that don't have explicit mappings
        plugin.configManager.channels.keys.forEach { channelName ->
            if (!channelMappings.containsKey(channelName)) {
                // Use channel name as fallback (but only if discord integration is configured)
                val hasDiscordConfig = plugin.configManager.getDiscordConfig().contains("channels.$channelName")
                if (hasDiscordConfig) {
                    channelMappings[channelName] = channelName
                    plugin.debugLog("Using fallback mapping for channel '$channelName'")
                } else {
                    plugin.debugLog("Skipping channel '$channelName' - no Discord configuration found")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRemmyChatMessage(event: RemmyChatMessageEvent) {
        if (!isDiscordSRVEnabled || !plugin.configManager.isDiscordEnabled()) return

        try {
            val channelName = event.channel
            val discordChannelName = getDiscordChannelForRemmyChannel(channelName)

            if (discordChannelName == null) {
                plugin.debugLog("No Discord mapping found for RemmyChat channel: $channelName")
                return
            }

            // Check direction setting for this channel
            val channelDirection = getChannelDirection(channelName)
            if (channelDirection == "discord-to-minecraft") {
                plugin.debugLog("Channel '$channelName' is configured for Discord-to-Minecraft only, skipping Minecraft message")
                return
            }

            // Get the Discord channel using JDA directly
            var discordChannel = getDiscordTextChannel(discordChannelName)
            if (discordChannel == null) {
                plugin.debugLog("Discord channel '$discordChannelName' not found in Discord server")

                // Fallback to general channel if configured
                val fallbackChannel = channelMappings["global"] ?: "general"
                if (discordChannelName != fallbackChannel) {
                    plugin.debugLog("Attempting fallback to '$fallbackChannel' channel")
                    discordChannel = getDiscordTextChannel(fallbackChannel)

                    if (discordChannel != null) {
                        plugin.logger.warning("⚠️  Channel '$discordChannelName' not found, using fallback '$fallbackChannel'")
                    } else {
                        plugin.logger.warning("❌ Both '$discordChannelName' and fallback '$fallbackChannel' channels not found")
                        return
                    }
                } else {
                    return
                }
            }

            // Format the message for Discord
            val plainMessage = PlainTextComponentSerializer.plainText().serialize(event.formattedMessage)
            val discordMessage = formatMessageForDiscord(event.player, plainMessage, channelName)

            // Send to Discord
            DiscordUtil.sendMessage(discordChannel, discordMessage)
            plugin.debugLog("Sent message from ${event.player.name} in channel '$channelName' to Discord channel '$discordChannelName'")

        } catch (e: Exception) {
            plugin.logger.warning("Error sending message to Discord: ${e.message}")
            e.printStackTrace()
        }
    }

    @Subscribe(priority = ListenerPriority.NORMAL)
    fun onDiscordMessage(event: DiscordGuildMessageReceivedEvent) {
        if (!isDiscordSRVEnabled || !plugin.configManager.isDiscordEnabled()) return
        if (event.author.isBot) return

        try {
            val discordChannel = event.channel
            val remmyChannel = getRemmyChannelForDiscordChannel(discordChannel.name)

            if (remmyChannel == null) {
                plugin.debugLog("No RemmyChat mapping found for Discord channel: ${discordChannel.name}")
                return
            }

            // Check direction setting for this channel
            val channelDirection = getChannelDirection(remmyChannel)
            if (channelDirection == "minecraft-to-discord" || channelDirection == "mc-to-discord") {
                plugin.debugLog("Channel '$remmyChannel' is configured for Minecraft-to-Discord only, skipping Discord message")
                return
            }

            // Check if the RemmyChat channel exists
            val channelConfig = plugin.configManager.getChannel(remmyChannel)
            if (channelConfig == null) {
                plugin.debugLog("RemmyChat channel '$remmyChannel' does not exist")
                return
            }

            // Format message for Minecraft
            val minecraftMessage = formatDiscordMessageForMinecraft(event.author.effectiveName, event.message.contentDisplay, remmyChannel)

            // Send to all players in the channel (or with permission for the channel)
            Bukkit.getScheduler().runTask(plugin) { _ ->
                sendDiscordMessageToMinecraft(minecraftMessage, remmyChannel, channelConfig)
            }

            plugin.debugLog("Sent message from Discord user '${event.author.effectiveName}' to RemmyChat channel '$remmyChannel'")

        } catch (e: Exception) {
            plugin.logger.warning("Error processing Discord message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getDiscordChannelForRemmyChannel(remmyChannel: String): String? {
        return channelMappings[remmyChannel]
    }

    private fun getRemmyChannelForDiscordChannel(discordChannel: String): String? {
        return channelMappings.entries.find { it.value == discordChannel }?.key
    }

    /**
     * Get the direction setting for a channel from discord.yml
     */
    private fun getChannelDirection(channelName: String): String {
        val channelSection = plugin.configManager.getDiscordConfig().getConfigurationSection("channels.$channelName")
        return channelSection?.getString("direction", "both") ?: "both"
    }

    private fun formatMessageForDiscord(player: Player, message: String, channel: String): String {
        // First try to get channel-specific format
        val channelSpecificTemplate = plugin.configManager.getDiscordConfig().getString("formatting.minecraft-to-discord.channel-formats.$channel")
        val template = channelSpecificTemplate ?: plugin.configManager.getDiscordConfig().getString("formatting.minecraft-to-discord.format", "**%player_name%**: %message%")

        // Get server name from plugin
        val serverName = "local"

        // Get display name
        val displayName = try {
            VersionCompatibility.getPlayerDisplayName(player)
        } catch (e: Exception) {
            player.name
        }

        return template?.replace("%player_name%", player.name)
            ?.replace("%display_name%", displayName)
            ?.replace("%server%", serverName)
            ?.replace("%channel%", channel)
            ?.replace("%message%", message) ?: "**${player.name}**: $message"
    }

    private fun formatDiscordMessageForMinecraft(username: String, message: String, channel: String): String {
        // First try to get channel-specific format
        val channelSpecificTemplate = plugin.configManager.getDiscordConfig().getString("formatting.discord-to-minecraft.channel-formats.$channel")
        val template = channelSpecificTemplate ?: plugin.configManager.getDiscordConfig().getString("formatting.discord-to-minecraft.format", "<blue>[Discord]</blue> <gray>%username%</gray>: %message%")

        // Get server name for consistency
        val serverName = "local"

        return template?.replace("%username%", username)
            ?.replace("%user%", username)
            ?.replace("%name%", username)
            ?.replace("%message%", message)
            ?.replace("%channel%", channel)
            ?.replace("%server%", serverName) ?: "<blue>[Discord]</blue> <gray>$username</gray>: $message"
    }

    private fun sendDiscordMessageToMinecraft(formattedMessage: String, channel: String, channelConfig: com.noximity.remmyChat.models.Channel) {
        val component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(formattedMessage)

        // Send to all online players who have access to this channel
        for (player in Bukkit.getOnlinePlayers()) {
            val chatUser = plugin.chatService.getChatUser(player.uniqueId)

            // Check if player has permission for this channel
            val permission = channelConfig.permission
            if (permission.isNotEmpty() && !player.hasPermission(permission)) {
                continue
            }

            // For global channels, send only to players currently in this channel (who also have permission)
            // For local channels, we can't determine location from Discord, so treat as global
            if (channelConfig.radius <= 0) {
                // Global channel - send only to players currently in this channel
                if (chatUser?.currentChannel == channel) {
                    player.sendMessage(component)
                }
            } else {
                // Local channel - send only to players currently in this channel
                if (chatUser?.currentChannel == channel) {
                    player.sendMessage(component)
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        return isDiscordSRVEnabled && plugin.configManager.isDiscordEnabled()
    }

    fun getChannelMappings(): Map<String, String> {
        return channelMappings.toMap()
    }

    fun reloadChannelMappings() {
        if (isDiscordSRVEnabled && plugin.configManager.isDiscordEnabled()) {
            val oldMappingCount = channelMappings.size
            loadChannelMappings()
            plugin.logger.info("🔄 Reloaded Discord channel mappings (${channelMappings.size} channels)")
            plugin.debugLog("Reloaded DiscordSRV channel mappings: old=$oldMappingCount, new=${channelMappings.size}")
        } else {
            plugin.debugLog("Cannot reload channel mappings: discordSRV=${isDiscordSRVEnabled}, configEnabled=${plugin.configManager.isDiscordEnabled()}")
        }
    }

    /**
     * Validate and report channel status during initialization
     */
    private fun validateAndReportChannels() {
        plugin.logger.info("🔍 Validating Discord channel mappings...")

        val validationResults = validateDiscordChannels()
        val validChannels = validationResults.count { it.value }
        val invalidChannels = validationResults.count { !it.value }

        if (invalidChannels == 0) {
            plugin.logger.info("✅ All ${validChannels} Discord channels validated successfully!")
        } else {
            plugin.logger.warning("⚠️  Discord channel validation: ${validChannels} valid, ${invalidChannels} invalid")
            plugin.logger.warning("Run '/remmychat discord validate' for detailed channel validation")
        }
    }

    /**
     * Validate all configured Discord channels exist in the Discord server
     */
    fun validateDiscordChannels(): Map<String, Boolean> {
        val validationResults = mutableMapOf<String, Boolean>()

        if (!isDiscordSRVEnabled) {
            plugin.debugLog("Cannot validate Discord channels: DiscordSRV not enabled")
            return validationResults
        }

        channelMappings.forEach { (remmyChannel, discordChannelName) ->
            val discordChannel = getDiscordTextChannel(discordChannelName)
            val isValid = discordChannel != null
            validationResults[remmyChannel] = isValid

            if (isValid) {
                plugin.debugLog("✅ Channel validation: '$remmyChannel' -> '$discordChannelName' (FOUND)")
            } else {
                plugin.debugLog("❌ Channel validation: '$remmyChannel' -> '$discordChannelName' (NOT FOUND)")

                // Suggest similar channel names
                val suggestions = findSimilarChannelNames(discordChannelName)
                if (suggestions.isNotEmpty()) {
                    plugin.debugLog("   💡 Suggested alternatives: ${suggestions.joinToString(", ")}")
                }
            }
        }

        return validationResults
    }

    /**
     * Get diagnostic information about Discord integration
     */
    fun getDiagnosticInfo(): String {
        val info = StringBuilder()
        info.appendLine("=== DiscordSRV Integration Diagnostics ===")
        info.appendLine("DiscordSRV Enabled: $isDiscordSRVEnabled")
        info.appendLine("Config Enabled: ${plugin.configManager.isDiscordEnabled()}")
        info.appendLine("Channel Mappings: ${channelMappings.size}")
        info.appendLine("Integration Mode: ${plugin.configManager.getDiscordConfig().getString("integration.mode", "bidirectional")}")
        info.appendLine("Auto-detect: ${plugin.configManager.getDiscordConfig().getBoolean("integration.auto-detect", true)}")

        if (isDiscordSRVEnabled) {
            try {
                val jda = DiscordSRV.getPlugin().jda
                val guild = DiscordSRV.getPlugin().mainGuild

                info.appendLine("JDA Available: ${jda != null}")
                info.appendLine("Guild Available: ${guild != null}")

                if (guild != null) {
                    info.appendLine("Guild Name: ${guild.name}")
                    info.appendLine("Guild ID: ${guild.id}")
                    info.appendLine("Available Discord Channels:")
                    guild.textChannels.sortedBy { it.name }.forEach { channel ->
                        info.appendLine("  - ${channel.name} (ID: ${channel.id})")
                    }
                }
            } catch (e: Exception) {
                info.appendLine("Error getting Discord info: ${e.message}")
            }
        }

        info.appendLine("\n=== Channel Mapping Configuration ===")
        channelMappings.forEach { (remmyChannel, discordChannel) ->
            val direction = getChannelDirection(remmyChannel)
            val enabled = plugin.configManager.getDiscordConfig().getBoolean("channels.$remmyChannel.enabled", true)
            info.appendLine("$remmyChannel -> $discordChannel (direction: $direction, enabled: $enabled)")
        }

        info.appendLine("\n=== Channel Mapping Validation ===")
        val validationResults = validateDiscordChannels()
        validationResults.forEach { (remmyChannel, isValid) ->
            val status = if (isValid) "✅" else "❌"
            val discordChannel = channelMappings[remmyChannel]
            info.appendLine("$status $remmyChannel -> $discordChannel")
        }

        return info.toString()
    }

    /**
     * Find similar Discord channel names for suggestions
     */
    private fun findSimilarChannelNames(targetName: String): List<String> {
        return try {
            val guild = DiscordSRV.getPlugin().mainGuild ?: return emptyList()

            guild.textChannels
                .map { it.name }
                .filter { channelName ->
                    // Check for partial matches, case insensitive
                    channelName.contains(targetName, ignoreCase = true) ||
                    targetName.contains(channelName, ignoreCase = true) ||
                    // Check for similar words (basic similarity)
                    levenshteinDistance(channelName.lowercase(), targetName.lowercase()) <= 2
                }
                .take(3) // Limit to 3 suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Calculate Levenshtein distance for basic string similarity
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * Get Discord text channel by name using JDA directly
     * This bypasses DiscordSRV's channel mapping system and looks for channels by name in the Discord server
     */
    private fun getDiscordTextChannel(channelName: String): TextChannel? {
        return try {
            // Get DiscordSRV's JDA instance and main guild
            val jda = DiscordSRV.getPlugin().jda
            if (jda == null) {
                plugin.debugLog("DiscordSRV JDA instance not available")
                return null
            }

            val guild = DiscordSRV.getPlugin().mainGuild
            if (guild == null) {
                plugin.debugLog("DiscordSRV main guild not available")
                return null
            }

            // First try to find channel by exact name
            val channels = guild.getTextChannelsByName(channelName, true)
            if (channels.isNotEmpty()) {
                plugin.debugLog("Found Discord channel '$channelName' by exact name match")
                return channels[0]
            }

            // If not found by exact name, try case-insensitive search
            val channelsIgnoreCase = guild.getTextChannelsByName(channelName, false)
            if (channelsIgnoreCase.isNotEmpty()) {
                plugin.debugLog("Found Discord channel '$channelName' by case-insensitive name match")
                return channelsIgnoreCase[0]
            }

            plugin.debugLog("Discord channel '$channelName' not found in guild '${guild.name}'")

            // Log available channels for debugging
            val availableChannels = guild.textChannels.map { it.name }
            plugin.debugLog("Available Discord channels: ${availableChannels.joinToString(", ")}")

            // Try to suggest alternatives
            val suggestions = findSimilarChannelNames(channelName)
            if (suggestions.isNotEmpty()) {
                plugin.debugLog("💡 Similar channels found: ${suggestions.joinToString(", ")}")
            }

            null
        } catch (e: Exception) {
            plugin.logger.warning("Error finding Discord channel '$channelName': ${e.message}")
            null
        }
    }

    /**
     * Test sending a message to a specific Discord channel
     */
    fun testChannelMessage(remmyChannelName: String, testMessage: String = "🔧 Test message from RemmyChat"): Boolean {
        if (!isDiscordSRVEnabled) {
            plugin.debugLog("Cannot test channel: DiscordSRV not enabled")
            return false
        }

        val discordChannelName = getDiscordChannelForRemmyChannel(remmyChannelName)
        if (discordChannelName == null) {
            plugin.debugLog("Cannot test channel '$remmyChannelName': no Discord mapping found")
            return false
        }

        val discordChannel = getDiscordTextChannel(discordChannelName)
        if (discordChannel == null) {
            plugin.debugLog("Cannot test channel '$remmyChannelName': Discord channel '$discordChannelName' not found")
            return false
        }

        return try {
            DiscordUtil.sendMessage(discordChannel, testMessage)
            plugin.debugLog("✅ Test message sent successfully to '$discordChannelName'")
            true
        } catch (e: Exception) {
            plugin.logger.warning("❌ Failed to send test message to '$discordChannelName': ${e.message}")
            false
        }
    }
}
