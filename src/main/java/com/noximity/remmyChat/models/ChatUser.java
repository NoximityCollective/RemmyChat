package com.noximity.remmyChat.models;

import java.util.UUID;

public class ChatUser {

    private final UUID uuid;
    private String currentChannel;
    private UUID lastMessagedPlayer;

    public ChatUser(UUID uuid, String defaultChannel) {
        this.uuid = uuid;
        this.currentChannel = defaultChannel;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getCurrentChannel() {
        return currentChannel;
    }

    public void setCurrentChannel(String currentChannel) {
        this.currentChannel = currentChannel;
    }

    public UUID getLastMessagedPlayer() {
        return lastMessagedPlayer;
    }

    public void setLastMessagedPlayer(UUID lastMessagedPlayer) {
        this.lastMessagedPlayer = lastMessagedPlayer;
    }
}