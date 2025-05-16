package com.noximity.remmyChat.services;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatService {

    private final RemmyChat plugin;
    private final Map<UUID, ChatUser> chatUsers = new HashMap<>();

    public ChatService(RemmyChat plugin) {
        this.plugin = plugin;
    }

    public ChatUser getChatUser(UUID uuid) {
        return chatUsers.computeIfAbsent(uuid, id -> {
            String defaultChannel = plugin.getConfigManager().getDefaultChannel().getName();
            return plugin.getDatabaseManager().loadUserPreferences(id, defaultChannel);
        });
    }

    public void createChatUser(UUID uuid) {
        if (!chatUsers.containsKey(uuid)) {
            String defaultChannel = plugin.getConfigManager().getDefaultChannel().getName();
            ChatUser user = plugin.getDatabaseManager().loadUserPreferences(uuid, defaultChannel);
            chatUsers.put(uuid, user);
        }
    }

    public void removeChatUser(UUID uuid) {
        if (chatUsers.containsKey(uuid)) {
            plugin.getDatabaseManager().saveUserPreferences(chatUsers.get(uuid));
            chatUsers.remove(uuid);
        }
    }

    public boolean setChannel(UUID uuid, String channel) {
        if (plugin.getConfigManager().getChannel(channel) == null) {
            return false;
        }

        getChatUser(uuid).setCurrentChannel(channel);
        return true;
    }

    public void saveAllUsers() {
        List<ChatUser> users = new ArrayList<>(chatUsers.values());
        for (ChatUser user : users) {
            plugin.getDatabaseManager().saveUserPreferences(user);
        }
    }

    public List<ChatUser> getSocialSpyUsers() {
        List<ChatUser> spyUsers = new ArrayList<>();
        for (ChatUser user : chatUsers.values()) {
            if (user.isSocialSpy()) {
                spyUsers.add(user);
            }
        }
        return spyUsers;
    }
}