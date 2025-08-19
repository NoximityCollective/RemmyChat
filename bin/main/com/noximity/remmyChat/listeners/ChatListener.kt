package com.noximity.remmyChat.listeners

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.compatibility.VersionCompatibility
import com.noximity.remmyChat.events.RemmyChatMessageEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class ChatListener(private val plugin: RemmyChat) : Listener {
    private val cooldowns: MutableMap<UUID, Long> = HashMap()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        event.isCancelled = true

        val player = event.player
        val rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message())

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
                    player.sendMessage(cooldownMsg)
                } else {
                    player.sendMessage("Please wait $remainingSeconds seconds before sending another message.")
                }
                return
            }

            cooldowns[player.uniqueId] = currentTime
        }

        val chatUser = plugin.chatService.getChatUser(player.uniqueId)
        if (chatUser == null) {
            player.sendMessage("Error: Could not load user data!")
            return
        }

        var currentChannel = plugin.configManager.getChannel(chatUser.currentChannel)

        if (currentChannel == null) {
            currentChannel = plugin.configManager.defaultChannel
            if (currentChannel == null) {
                val errorMsg = plugin.formatService.formatSystemMessage("error.no-default-channel")
                if (errorMsg != null) {
                    player.sendMessage(errorMsg)
                } else {
                    player.sendMessage("No default channel configured!")
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
                player.sendMessage(errorMsg)
            } else {
                player.sendMessage("You don't have permission to use this channel!")
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
        if (currentChannel.radius > 0) {
            // Local channel with radius
            for (recipient in plugin.server.onlinePlayers) {
                if (player.world == recipient.world &&
                    player.location.distance(recipient.location) <= currentChannel.radius
                ) {
                    recipient.sendMessage(formattedMessage)
                }
            }
        } else {
            // Global channel - send to all players in the same channel on this server
            for (recipient in plugin.server.onlinePlayers) {
                val recipientUser = plugin.chatService.getChatUser(recipient.uniqueId)
                if (recipientUser?.currentChannel == currentChannel.name) {
                    recipient.sendMessage(formattedMessage)
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
