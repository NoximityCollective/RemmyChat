package com.noximity.remmyChat.listeners;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.Channel;
import com.noximity.remmyChat.models.ChatUser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final RemmyChat plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ChatListener(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (rawMessage.trim().isEmpty()) {
            return;
        }

        // Check cooldown
        int cooldownTime = plugin.getConfigManager().getCooldown();
        if (cooldownTime > 0) {
            long lastMessageTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastMessageTime < cooldownTime * 1000L) {
                long remainingSeconds = (cooldownTime * 1000L - (currentTime - lastMessageTime)) / 1000;
                player.sendMessage(plugin.getFormatService().formatSystemMessage("cooldown",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("seconds", String.valueOf(remainingSeconds))));
                return;
            }

            cooldowns.put(player.getUniqueId(), currentTime);
        }

        ChatUser chatUser = plugin.getChatService().getChatUser(player.getUniqueId());
        Channel currentChannel = plugin.getConfigManager().getChannel(chatUser.getCurrentChannel());

        if (currentChannel == null) {
            currentChannel = plugin.getConfigManager().getDefaultChannel();
            if (currentChannel == null) {
                player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-default-channel"));
                return;
            }
            chatUser.setCurrentChannel(currentChannel.getName());
        }

        // Check permission for the channel
        if (currentChannel.getPermission() != null && !currentChannel.getPermission().isEmpty()
                && !player.hasPermission(currentChannel.getPermission())) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"));
            return;
        }

        // Format the message
        Component formattedMessage = plugin.getFormatService().formatChatMessage(player, currentChannel.getName(), rawMessage);

        // Determine who should receive the message
        if (currentChannel.getRadius() > 0) {
            // Local radius-based chat - only players within radius receive the message
            for (Player recipient : plugin.getServer().getOnlinePlayers()) {
                if (player.getWorld().equals(recipient.getWorld()) &&
                        player.getLocation().distance(recipient.getLocation()) <= currentChannel.getRadius()) {
                    recipient.sendMessage(formattedMessage);
                }
            }
        } else {
            // Global chat - only send to players in the same channel
            for (Player recipient : plugin.getServer().getOnlinePlayers()) {
                ChatUser recipientUser = plugin.getChatService().getChatUser(recipient.getUniqueId());
                // Only send message if recipient is in the same channel
                if (recipientUser.getCurrentChannel().equals(currentChannel.getName())) {
                    recipient.sendMessage(formattedMessage);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // This will now load the saved channel from the database
        plugin.getChatService().createChatUser(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Save user preferences including channel before removing from cache
        ChatUser user = plugin.getChatService().getChatUser(player.getUniqueId());
        if (user != null) {
            plugin.getDatabaseManager().saveUserPreferences(user);
        }
        plugin.getChatService().removeChatUser(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }
}

