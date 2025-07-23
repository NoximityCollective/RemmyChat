package com.noximity.remmyChat.models

import java.util.*

class ChatUser {
    val uuid: UUID
    var currentChannel: String
    var lastMessagedPlayer: UUID? = null
    var isMsgToggle: Boolean
    var isSocialSpy: Boolean

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


}
