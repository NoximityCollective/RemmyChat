package com.noximity.remmyChat.services;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatService {

    private final RemmyChat plugin;
    private final Map<UUID, ChatUser> chatUsers = new HashMap<>();

    public ChatService(RemmyChat plugin) {
        this.plugin = plugin;
    }

    public ChatUser getChatUser(UUID uuid) {
        return chatUsers.computeIfAbsent(uuid, id ->
                new ChatUser(id, plugin.getConfigManager().getDefaultChannel().getName()));
    }

    public void createChatUser(UUID uuid) {
        if (!chatUsers.containsKey(uuid)) {
            chatUsers.put(uuid, new ChatUser(uuid, plugin.getConfigManager().getDefaultChannel().getName()));
        }
    }

    public void removeChatUser(UUID uuid) {
        chatUsers.remove(uuid);
    }

    public boolean setChannel(UUID uuid, String channel) {
        if (plugin.getConfigManager().getChannel(channel) == null) {
            return false;
        }

        getChatUser(uuid).setCurrentChannel(channel);
        return true;
    }
}