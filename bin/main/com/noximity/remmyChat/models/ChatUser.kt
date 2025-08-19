package com.noximity.remmyChat.models

import java.util.*

class ChatUser {
    val uuid: UUID
    var currentChannel: String
    var lastMessagedPlayer: UUID? = null
    var lastMessageSender: UUID? = null
    var isMsgToggle: Boolean
    var isSocialSpy: Boolean
    var isMessagesEnabled: Boolean
        get() = isMsgToggle
        set(value) { isMsgToggle = value }

    // Track last message times per channel
    private val lastMessageTimes = mutableMapOf<String, Long>()

    constructor(uuid: UUID, defaultChannel: String) {
        this.uuid = uuid
        this.currentChannel = defaultChannel
        this.isMsgToggle = true
        this.isSocialSpy = false
    }

    constructor(uuid: UUID, defaultChannel: String, msgToggle: Boolean, socialSpy: Boolean) {
        this.uuid = uuid
        this.currentChannel = defaultChannel
        this.isMsgToggle = msgToggle
        this.isSocialSpy = socialSpy
    }


    /**
     * Get the last message time for a specific channel
     */
    fun getLastMessageTime(channelName: String): Long {
        return lastMessageTimes[channelName] ?: 0L
    }

    /**
     * Update the last message time for a specific channel
     */
    fun updateLastMessageTime(channelName: String, time: Long = System.currentTimeMillis()) {
        lastMessageTimes[channelName] = time
    }

    /**
     * Clear message times for all channels
     */
    fun clearMessageTimes() {
        lastMessageTimes.clear()
    }
}
