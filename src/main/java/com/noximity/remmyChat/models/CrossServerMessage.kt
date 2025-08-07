package com.noximity.remmyChat.models

import java.util.*

/**
 * Represents a message sent between servers in a cross-server network
 */
data class CrossServerMessage(
    // Message identification
    var id: String = "",
    val type: String,
    val subType: String? = null,

    // Source information
    var sourceServer: String = "",
    var timestamp: Long = 0L,

    // Target information
    val targetServers: List<String> = emptyList(),
    val targetPlayer: String? = null,

    // Content
    val content: String = "",
    val formattedContent: String? = null,

    // Message metadata
    val channel: String? = null,
    val sender: String = "",
    val senderUuid: String = "",

    // Additional data
    val data: Map<String, Any> = emptyMap(),

    // Priority and routing
    val priority: Int = 5, // 1-10, higher = more important
    val ttl: Long = 300000L, // Time to live in milliseconds (5 minutes default)

    // Processing flags
    val requiresAck: Boolean = false,
    val compressed: Boolean = false
) {

    /**
     * Check if this message is expired
     */
    fun isExpired(): Boolean {
        return timestamp > 0 && (System.currentTimeMillis() - timestamp) > ttl
    }

    /**
     * Check if this message should be sent to a specific server
     */
    fun shouldSendToServer(serverName: String): Boolean {
        if (targetServers.contains("*")) return true
        return targetServers.contains(serverName)
    }

    /**
     * Check if this message is targeted at a specific player
     */
    fun isTargetedMessage(): Boolean {
        return !targetPlayer.isNullOrEmpty()
    }

    /**
     * Check if this is a high priority message
     */
    fun isHighPriority(): Boolean {
        return priority >= 8
    }

    /**
     * Check if this message is for a specific channel
     */
    fun isChannelMessage(): Boolean {
        return !channel.isNullOrEmpty()
    }

    /**
     * Get message size estimate in bytes
     */
    fun getEstimatedSize(): Int {
        var size = 0
        size += id.length * 2
        size += type.length * 2
        size += subType?.length?.times(2) ?: 0
        size += sourceServer.length * 2
        size += content.length * 2
        size += formattedContent?.length?.times(2) ?: 0
        size += channel?.length?.times(2) ?: 0
        size += sender.length * 2
        size += senderUuid.length * 2
        size += targetPlayer?.length?.times(2) ?: 0
        size += targetServers.sumOf { it.length * 2 }
        size += data.toString().length * 2 // Rough estimate
        return size
    }

    /**
     * Create a copy with updated timestamp
     */
    fun withCurrentTimestamp(): CrossServerMessage {
        return this.copy(timestamp = System.currentTimeMillis())
    }

    /**
     * Create a copy with a new ID
     */
    fun withNewId(newId: String): CrossServerMessage {
        return this.copy(id = newId)
    }

    /**
     * Create an acknowledgment message for this message
     */
    fun createAck(receiverServer: String): CrossServerMessage {
        return CrossServerMessage(
            type = "ack",
            sourceServer = receiverServer,
            targetServers = listOf(sourceServer),
            data = mapOf(
                "originalId" to id,
                "originalType" to type
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Convert to a map for serialization
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["type"] = type
        if (subType != null) map["subType"] = subType
        map["sourceServer"] = sourceServer
        map["timestamp"] = timestamp
        map["targetServers"] = targetServers
        if (targetPlayer != null) map["targetPlayer"] = targetPlayer
        map["content"] = content
        if (formattedContent != null) map["formattedContent"] = formattedContent
        if (channel != null) map["channel"] = channel
        map["sender"] = sender
        map["senderUuid"] = senderUuid
        map["data"] = data
        map["priority"] = priority
        map["ttl"] = ttl
        map["requiresAck"] = requiresAck
        map["compressed"] = compressed
        return map
    }

    override fun toString(): String {
        return "CrossServerMessage(id='$id', type='$type', source='$sourceServer', targets=$targetServers)"
    }

    companion object {
        /**
         * Create a CrossServerMessage from a map (for deserialization)
         */
        fun fromMap(map: Map<String, Any>): CrossServerMessage {
            return CrossServerMessage(
                id = map["id"] as? String ?: "",
                type = map["type"] as? String ?: "",
                subType = map["subType"] as? String,
                sourceServer = map["sourceServer"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                targetServers = (map["targetServers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                targetPlayer = map["targetPlayer"] as? String,
                content = map["content"] as? String ?: "",
                formattedContent = map["formattedContent"] as? String,
                channel = map["channel"] as? String,
                sender = map["sender"] as? String ?: "",
                senderUuid = map["senderUuid"] as? String ?: "",
                data = (map["data"] as? Map<String, Any>) ?: emptyMap(),
                priority = (map["priority"] as? Number)?.toInt() ?: 5,
                ttl = (map["ttl"] as? Number)?.toLong() ?: 300000L,
                requiresAck = map["requiresAck"] as? Boolean ?: false,
                compressed = map["compressed"] as? Boolean ?: false
            )
        }

        /**
         * Create a chat message
         */
        fun createChatMessage(
            channel: String,
            sender: String,
            senderUuid: String,
            content: String,
            formattedContent: String,
            targetServers: List<String>
        ): CrossServerMessage {
            return CrossServerMessage(
                type = "chat",
                channel = channel,
                sender = sender,
                senderUuid = senderUuid,
                content = content,
                formattedContent = formattedContent,
                targetServers = targetServers,
                priority = 5
            )
        }

        /**
         * Create a private message
         */
        fun createPrivateMessage(
            sender: String,
            senderUuid: String,
            recipient: String,
            content: String,
            formattedContent: String
        ): CrossServerMessage {
            return CrossServerMessage(
                type = "private_message",
                sender = sender,
                senderUuid = senderUuid,
                targetPlayer = recipient,
                content = content,
                formattedContent = formattedContent,
                targetServers = listOf("*"),
                priority = 7
            )
        }

        /**
         * Create a server announcement
         */
        fun createAnnouncement(
            content: String,
            formattedContent: String,
            targetServers: List<String> = listOf("*")
        ): CrossServerMessage {
            return CrossServerMessage(
                type = "announcement",
                content = content,
                formattedContent = formattedContent,
                targetServers = targetServers,
                priority = 9
            )
        }

        /**
         * Create a heartbeat message
         */
        fun createHeartbeat(
            serverName: String,
            playerCount: Int,
            additionalData: Map<String, Any> = emptyMap()
        ): CrossServerMessage {
            val data = mutableMapOf<String, Any>()
            data["playerCount"] = playerCount
            data.putAll(additionalData)

            return CrossServerMessage(
                type = "heartbeat",
                sourceServer = serverName,
                data = data,
                targetServers = listOf("*"),
                priority = 2
            )
        }

        /**
         * Create a player sync message
         */
        fun createPlayerSync(
            syncType: String,
            playerName: String,
            playerUuid: String,
            syncData: Map<String, Any>,
            targetServers: List<String> = listOf("*")
        ): CrossServerMessage {
            return CrossServerMessage(
                type = "player_sync",
                subType = syncType,
                sender = playerName,
                senderUuid = playerUuid,
                data = syncData,
                targetServers = targetServers,
                priority = 6
            )
        }

        /**
         * Create an admin command message
         */
        fun createAdminCommand(
            command: String,
            args: Map<String, Any>,
            targetServers: List<String>
        ): CrossServerMessage {
            return CrossServerMessage(
                type = "admin_command",
                content = command,
                data = args,
                targetServers = targetServers,
                priority = 10,
                requiresAck = true
            )
        }
    }
}
