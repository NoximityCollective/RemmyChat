package com.noximity.remmyChat.models;

import java.util.UUID;

public class ChatUser {

    private final UUID uuid;
    private String currentChannel;
    private UUID lastMessagedPlayer;
    private boolean msgToggle;
    private boolean socialSpy;

    public ChatUser(UUID uuid, String defaultChannel) {
        this.uuid = uuid;
        this.currentChannel = defaultChannel;
        this.msgToggle = true;
        this.socialSpy = false;
    }

    public ChatUser(UUID uuid, String defaultChannel, boolean msgToggle, boolean socialSpy) {
        this.uuid = uuid;
        this.currentChannel = defaultChannel;
        this.msgToggle = msgToggle;
        this.socialSpy = socialSpy;
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

    public boolean isMsgToggle() {
        return msgToggle;
    }

    public void setMsgToggle(boolean msgToggle) {
        this.msgToggle = msgToggle;
    }

    public boolean isSocialSpy() {
        return socialSpy;
    }

    public void setSocialSpy(boolean socialSpy) {
        this.socialSpy = socialSpy;
    }
}