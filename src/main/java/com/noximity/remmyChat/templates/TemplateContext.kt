package com.noximity.remmyChat.templates

import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.models.GroupFormat
import org.bukkit.entity.Player

/**
 * Context data container for template processing
 * Contains all the information needed to resolve placeholders and templates
 */
data class TemplateContext(
    val message: String = "",
    val player: Player? = null,
    val channel: Channel? = null,
    val group: GroupFormat? = null,
    val targetPlayer: Player? = null,
    val sourceServer: String = "",
    val customData: Map<String, Any> = emptyMap()
) {

    /**
     * Create a copy of this context with a new message
     */
    fun withMessage(newMessage: String): TemplateContext {
        return copy(message = newMessage)
    }

    /**
     * Create a copy of this context with a new player
     */
    fun withPlayer(newPlayer: Player): TemplateContext {
        return copy(player = newPlayer)
    }

    /**
     * Create a copy of this context with a new channel
     */
    fun withChannel(newChannel: Channel): TemplateContext {
        return copy(channel = newChannel)
    }

    /**
     * Create a copy of this context with a new group
     */
    fun withGroup(newGroup: GroupFormat): TemplateContext {
        return copy(group = newGroup)
    }

    /**
     * Create a copy of this context with additional custom data
     */
    fun withCustomData(key: String, value: Any): TemplateContext {
        val newData = customData.toMutableMap()
        newData[key] = value
        return copy(customData = newData)
    }

    /**
     * Create a copy of this context with multiple custom data entries
     */
    fun withCustomData(data: Map<String, Any>): TemplateContext {
        val newData = customData.toMutableMap()
        newData.putAll(data)
        return copy(customData = newData)
    }

    /**
     * Create a copy of this context with a target player (for private messages)
     */
    fun withTargetPlayer(target: Player): TemplateContext {
        return copy(targetPlayer = target)
    }

    /**
     * Create a copy of this context with a source server (for cross-server messages)
     */
    fun withSourceServer(server: String): TemplateContext {
        return copy(sourceServer = server)
    }

    /**
     * Check if this context has a valid player
     */
    fun hasPlayer(): Boolean = player != null

    /**
     * Check if this context has a valid channel
     */
    fun hasChannel(): Boolean = channel != null

    /**
     * Check if this context has a valid group
     */
    fun hasGroup(): Boolean = group != null

    /**
     * Check if this context has a target player
     */
    fun hasTargetPlayer(): Boolean = targetPlayer != null

    /**
     * Check if this context is for a cross-server message
     */
    fun isCrossServer(): Boolean = sourceServer.isNotEmpty()

    /**
     * Get a custom data value as a specific type
     */
    inline fun <reified T> getCustomData(key: String): T? {
        return customData[key] as? T
    }

    /**
     * Get a custom data value as a string, with fallback
     */
    fun getCustomDataString(key: String, fallback: String = ""): String {
        return customData[key]?.toString() ?: fallback
    }

    /**
     * Get a custom data value as an integer, with fallback
     */
    fun getCustomDataInt(key: String, fallback: Int = 0): Int {
        return when (val value = customData[key]) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }
    }

    /**
     * Get a custom data value as a boolean, with fallback
     */
    fun getCustomDataBoolean(key: String, fallback: Boolean = false): Boolean {
        return when (val value = customData[key]) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> fallback
        }
    }

    /**
     * Create a builder for this context
     */
    fun toBuilder(): Builder {
        return Builder()
            .message(message)
            .player(player)
            .channel(channel)
            .group(group)
            .targetPlayer(targetPlayer)
            .sourceServer(sourceServer)
            .customData(customData)
    }

    /**
     * Builder pattern for creating TemplateContext instances
     */
    class Builder {
        private var message: String = ""
        private var player: Player? = null
        private var channel: Channel? = null
        private var group: GroupFormat? = null
        private var targetPlayer: Player? = null
        private var sourceServer: String = ""
        private var customData: MutableMap<String, Any> = mutableMapOf()

        fun message(message: String) = apply { this.message = message }
        fun player(player: Player?) = apply { this.player = player }
        fun channel(channel: Channel?) = apply { this.channel = channel }
        fun group(group: GroupFormat?) = apply { this.group = group }
        fun targetPlayer(targetPlayer: Player?) = apply { this.targetPlayer = targetPlayer }
        fun sourceServer(sourceServer: String) = apply { this.sourceServer = sourceServer }

        fun customData(data: Map<String, Any>) = apply {
            this.customData.clear()
            this.customData.putAll(data)
        }

        fun addCustomData(key: String, value: Any) = apply {
            this.customData[key] = value
        }

        fun build(): TemplateContext {
            return TemplateContext(
                message = message,
                player = player,
                channel = channel,
                group = group,
                targetPlayer = targetPlayer,
                sourceServer = sourceServer,
                customData = customData.toMap()
            )
        }
    }

    companion object {
        /**
         * Create a new builder
         */
        fun builder(): Builder = Builder()

        /**
         * Create a simple context with just a message
         */
        fun message(message: String): TemplateContext {
            return TemplateContext(message = message)
        }

        /**
         * Create a context for a player chat message
         */
        fun playerMessage(player: Player, message: String, channel: Channel? = null, group: GroupFormat? = null): TemplateContext {
            return TemplateContext(
                message = message,
                player = player,
                channel = channel,
                group = group
            )
        }

        /**
         * Create a context for a cross-server message
         */
        fun crossServerMessage(player: Player, message: String, channel: Channel, sourceServer: String): TemplateContext {
            return TemplateContext(
                message = message,
                player = player,
                channel = channel,
                sourceServer = sourceServer
            )
        }

        /**
         * Create a context for a private message
         */
        fun privateMessage(sender: Player, target: Player, message: String): TemplateContext {
            return TemplateContext(
                message = message,
                player = sender,
                targetPlayer = target
            )
        }
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + (player?.uniqueId?.hashCode() ?: 0)
        result = 31 * result + (channel?.name?.hashCode() ?: 0)
        result = 31 * result + (group?.name?.hashCode() ?: 0)
        result = 31 * result + (targetPlayer?.uniqueId?.hashCode() ?: 0)
        result = 31 * result + sourceServer.hashCode()
        result = 31 * result + customData.hashCode()
        return result
    }
}
