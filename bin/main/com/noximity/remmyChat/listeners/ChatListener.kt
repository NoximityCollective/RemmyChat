package com.noximity.remmyChat.listeners

import com.noximity.remmyChat.RemmyChat
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class ChatListener(private val plugin: RemmyChat) : Listener {
    private val cooldowns: MutableMap<UUID?, Long?> = HashMap<UUID?, Long?>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        event.setCancelled(true)

        val player = event.getPlayer()

        val rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message())

        if (rawMessage.trim { it <= ' ' }.isEmpty()) {
            return
        }

        // Check cooldown
        val cooldownTime = plugin.getConfigManager().getCooldown()
        if (cooldownTime > 0) {
            val lastMessageTime = cooldowns.getOrDefault(player.getUniqueId(), 0L)!!
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastMessageTime < cooldownTime * 1000L) {
                val remainingSeconds = (cooldownTime * 1000L - (currentTime - lastMessageTime)) / 1000
                player.sendMessage(
                    plugin.getFormatService().formatSystemMessage(
                        "cooldown",
                        Placeholder.parsed("seconds", remainingSeconds.toString())
                    )
                )
                return
            }

            cooldowns.put(player.getUniqueId(), currentTime)
        }

        val chatUser = plugin.getChatService().getChatUser(player.getUniqueId())
        var currentChannel = plugin.getConfigManager().getChannel(chatUser.getCurrentChannel())

        if (currentChannel == null) {
            currentChannel = plugin.getConfigManager().getDefaultChannel()
            if (currentChannel == null) {
                player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-default-channel"))
                return
            }
            chatUser.setCurrentChannel(currentChannel.getName())
        }

        // Check permission for the channel
        if (currentChannel.getPermission() != null && !currentChannel.getPermission()
                .isEmpty() && !player.hasPermission(currentChannel.getPermission())
        ) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"))
            return
        }

        // Format the message
        val formattedMessage = plugin.getFormatService().formatChatMessage(player, currentChannel.getName(), rawMessage)
        // Log the message to console
        val plainMessage = PlainTextComponentSerializer.plainText().serialize(formattedMessage)
        plugin.getLogger().info(plainMessage)
        if (currentChannel.getRadius() > 0) {
            for (recipient in plugin.getServer().getOnlinePlayers()) {
                if (player.getWorld() == recipient.getWorld() &&
                    player.getLocation().distance(recipient.getLocation()) <= currentChannel.getRadius()
                ) {
                    recipient.sendMessage(formattedMessage)
                }
            }
        } else {
            for (recipient in plugin.getServer().getOnlinePlayers()) {
                val recipientUser = plugin.getChatService().getChatUser(recipient.getUniqueId())
                if (recipientUser.getCurrentChannel() == currentChannel.getName()) {
                    recipient.sendMessage(formattedMessage)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.getPlayer()
        // This will now load the saved channel from the database
        plugin.getChatService().createChatUser(player.getUniqueId())
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.getPlayer()
        // Save user preferences including channel before removing from cache
        val user = plugin.getChatService().getChatUser(player.getUniqueId())
        if (user != null) {
            plugin.getDatabaseManager().saveUserPreferences(user)
        }
        plugin.getChatService().removeChatUser(player.getUniqueId())
        cooldowns.remove(player.getUniqueId())
    }
}

