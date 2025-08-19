package com.noximity.remmyChat.compatibility

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.events.RemmyChatMessageEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player
import java.util.*

/**
 * Compatible chat listener that handles both modern AsyncChatEvent and legacy AsyncPlayerChatEvent
 * Provides backward compatibility for Paper 1.21.x versions
 */
class CompatibleChatListener(private val plugin: RemmyChat) : Listener {
    private val cooldowns: MutableMap<UUID, Long> = HashMap()

    init {
        // Log compatibility information on initialization
        VersionCompatibility.logCompatibilityInfo(plugin.logger)
    }

    /**
     * Modern AsyncChatEvent handler for Paper 1.19+ with Component support
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onModernChat(event: AsyncChatEvent) {
        if (!VersionCompatibility.supportsAsyncChatEvent()) {
            return // Skip if not supported
        }

        val player = event.player
        val rawMessage = try {
            PlainTextComponentSerializer.plainText().serialize(event.message())
        } catch (e: Exception) {
            plugin.logger.warning("Failed to extract message from AsyncChatEvent: ${e.message}")
            return
        }

        // Cancel the original event
        event.isCancelled = true

        // Process the chat message
        processChatMessage(player, rawMessage)
    }

    /**
     * Legacy AsyncPlayerChatEvent handler for compatibility with older Paper versions
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLegacyChat(event: AsyncPlayerChatEvent) {
        // Only handle if modern AsyncChatEvent is not available
        if (VersionCompatibility.supportsAsyncChatEvent()) {
            return
        }

        val player = event.player
        val rawMessage = event.message

        // Cancel the original event
        event.isCancelled = true

        // Process the chat message
        processChatMessage(player, rawMessage)
    }

    /**
     * Common chat message processing logic
     */
    private fun processChatMessage(player: Player, rawMessage: String) {
        if (rawMessage.trim().isEmpty()) {
            return
        }

        // Check cooldown
        val cooldownTime = plugin.configManager.cooldown
        if (cooldownTime > 0) {
            val lastMessageTime = cooldowns.getOrDefault(player.uniqueId, 0L)
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastMessageTime < cooldownTime * 1000L) {
                val remainingSeconds = (cooldownTime * 1000L - (currentTime - lastMessageTime)) / 1000
                val cooldownMsg = plugin.formatService.formatSystemMessage(
                    "cooldown",
                    Placeholder.parsed("seconds", remainingSeconds.toString())
                )

                if (cooldownMsg != null) {
                    VersionCompatibility.sendMessage(player, cooldownMsg)
                } else {
                    VersionCompatibility.sendMessage(player, "Please wait $remainingSeconds seconds before sending another message.")
                }
                return
            }

            cooldowns[player.uniqueId] = currentTime
        }

        val chatUser = plugin.chatService.getChatUser(player.uniqueId)
        if (chatUser == null) {
            VersionCompatibility.sendMessage(player, "Error: Could not load user data!")
            return
        }

        var currentChannel = plugin.configManager.getChannel(chatUser.currentChannel)

        if (currentChannel == null) {
            currentChannel = plugin.configManager.defaultChannel
            if (currentChannel == null) {
                val errorMsg = plugin.formatService.formatSystemMessage("error.no-default-channel")
                if (errorMsg != null) {
                    VersionCompatibility.sendMessage(player, errorMsg)
                } else {
                    VersionCompatibility.sendMessage(player, "No default channel configured!")
                }
                return
            }
            chatUser.currentChannel = currentChannel.name ?: "global"
        }

        // Check permission for the channel
        val permission = currentChannel.permission
        if (permission != null && permission.isNotEmpty() && !player.hasPermission(permission)) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.no-permission")
            if (errorMsg != null) {
                VersionCompatibility.sendMessage(player, errorMsg)
            } else {
                VersionCompatibility.sendMessage(player, "You don't have permission to use this channel!")
            }
            return
        }

        // Format the message
        val formattedMessage = plugin.formatService.formatChatMessage(player, currentChannel.name, rawMessage)

        // Log the message to console
        val plainMessage = PlainTextComponentSerializer.plainText().serialize(formattedMessage)
        plugin.logger.info(plainMessage)

        // Fire RemmyChatMessageEvent for integrations (like DiscordSRV) - must be synchronous
        val remmyChatEvent = RemmyChatMessageEvent(player, currentChannel.name ?: "global", rawMessage, formattedMessage)
        VersionCompatibility.safeFireEvent(plugin, remmyChatEvent)

        // Send to local server players
        sendToLocalPlayers(player, currentChannel, formattedMessage)
    }

    /**
     * Sends the formatted message to local players based on channel settings
     */
    private fun sendToLocalPlayers(sender: Player, channel: com.noximity.remmyChat.models.Channel, formattedMessage: Component) {
        if (channel.radius > 0) {
            // Local channel with radius
            for (recipient in plugin.server.onlinePlayers) {
                if (sender.world == recipient.world &&
                    sender.location.distance(recipient.location) <= channel.radius
                ) {
                    VersionCompatibility.sendMessage(recipient, formattedMessage)
                }
            }
        } else {
            // Global channel - send to all players in the same channel on this server
            for (recipient in plugin.server.onlinePlayers) {
                val recipientUser = plugin.chatService.getChatUser(recipient.uniqueId)
                if (recipientUser?.currentChannel == channel.name) {
                    VersionCompatibility.sendMessage(recipient, formattedMessage)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // This will now load the saved channel from the database
        plugin.chatService.createChatUser(player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // Save user preferences including channel before removing from cache
        val user = plugin.chatService.getChatUser(player.uniqueId)
        if (user != null) {
            plugin.databaseManager.saveUserPreferences(user)
        }
        plugin.chatService.removeChatUser(player.uniqueId)
        cooldowns.remove(player.uniqueId)
    }
}
