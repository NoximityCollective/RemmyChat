package com.noximity.remmyChat.integrations

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.compatibility.VersionCompatibility
import com.noximity.remmyChat.events.RemmyChatMessageEvent
import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.api.ListenerPriority
import github.scarsz.discordsrv.api.Subscribe
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel
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
                // Use channel name as fallback
                channelMappings[channelName] = channelName
                plugin.debugLog("Using fallback mapping for channel '$channelName'")
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

            // Get the Discord channel
            val discordChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(discordChannelName)
            if (discordChannel == null) {
                plugin.debugLog("Discord channel '$discordChannelName' not found in DiscordSRV configuration")
                return
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
}
