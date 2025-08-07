package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.ChatUser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatService(private val plugin: RemmyChat) {
    private val chatUsers = ConcurrentHashMap<UUID, ChatUser>()

    fun getChatUser(uuid: UUID): ChatUser? {
        return chatUsers[uuid] ?: run {
            val defaultChannel = plugin.configManager.defaultChannel?.name ?: "global"
            val user = plugin.databaseManager.loadUserPreferences(uuid, defaultChannel) ?: ChatUser(uuid, defaultChannel)
            chatUsers[uuid] = user
            user
        }
    }

    fun createChatUser(uuid: UUID) {
        if (!chatUsers.containsKey(uuid)) {
            val defaultChannel = plugin.configManager.defaultChannel?.name ?: "global"
            val user = plugin.databaseManager.loadUserPreferences(uuid, defaultChannel)
            if (user != null) {
                chatUsers[uuid] = user
            }
        }
    }

    fun removeChatUser(uuid: UUID) {
        chatUsers[uuid]?.let { user ->
            plugin.databaseManager.saveUserPreferences(user)
            chatUsers.remove(uuid)
        }
    }

    fun setChannel(uuid: UUID, channel: String): Boolean {
        if (plugin.configManager.getChannel(channel) == null) {
            return false
        }

        getChatUser(uuid)?.let { it.currentChannel = channel }
        return true
    }

    fun saveAllUsers() {
        val users = java.util.ArrayList(chatUsers.values)
        for (user in users) {
            plugin.databaseManager.saveUserPreferences(user)
        }
    }

    fun getSocialSpyUsers(): List<ChatUser> {
        val spyUsers = java.util.ArrayList<ChatUser>()
        for (user in chatUsers.values) {
            if (user.isSocialSpy) {
                spyUsers.add(user)
            }
        }
        return spyUsers
    }

    fun getChannels(): Map<String, com.noximity.remmyChat.models.Channel> {
        return plugin.configManager.channels
    }

    fun getUser(uuid: UUID): ChatUser? {
        return getChatUser(uuid)
    }

    fun getUser(player: org.bukkit.entity.Player): ChatUser? {
        return getChatUser(player.uniqueId)
    }

    fun getServerName(): String {
        return plugin.configManager.serverName.ifEmpty { "local" }
    }

    fun clearOldCachedData() {
        // Clear old cached data - implementation can be expanded as needed
        plugin.debugLog("Clearing old cached data from ChatService")
    }
}
