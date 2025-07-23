package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.ChatUser
import java.util.*

class ChatService(private val plugin: RemmyChat) {
    private val chatUsers: MutableMap<UUID?, ChatUser> = HashMap<UUID?, ChatUser>()

    fun getChatUser(uuid: UUID?): ChatUser? {
        return chatUsers.computeIfAbsent(uuid) { id: UUID? ->
            val defaultChannel = plugin.getConfigManager().getDefaultChannel().getName()
            plugin.getDatabaseManager().loadUserPreferences(id, defaultChannel)
        }
    }

    fun createChatUser(uuid: UUID) {
        if (!chatUsers.containsKey(uuid)) {
            val defaultChannel = plugin.getConfigManager().getDefaultChannel().getName()
            val user = plugin.getDatabaseManager().loadUserPreferences(uuid, defaultChannel)
            chatUsers.put(uuid, user!!)
        }
    }

    fun removeChatUser(uuid: UUID?) {
        if (chatUsers.containsKey(uuid)) {
            plugin.getDatabaseManager().saveUserPreferences(chatUsers.get(uuid))
            chatUsers.remove(uuid)
        }
    }

    fun setChannel(uuid: UUID?, channel: String?): Boolean {
        if (plugin.getConfigManager().getChannel(channel) == null) {
            return false
        }

        getChatUser(uuid)!!.setCurrentChannel(channel)
        return true
    }

    fun saveAllUsers() {
        val users: MutableList<ChatUser?> = ArrayList<ChatUser?>(chatUsers.values)
        for (user in users) {
            plugin.getDatabaseManager().saveUserPreferences(user)
        }
    }

    val socialSpyUsers: MutableList<ChatUser?>
        get() {
            val spyUsers: MutableList<ChatUser?> = ArrayList<ChatUser?>()
            for (user in chatUsers.values) {
                if (user.isSocialSpy()) {
                    spyUsers.add(user)
                }
            }
            return spyUsers
        }
}