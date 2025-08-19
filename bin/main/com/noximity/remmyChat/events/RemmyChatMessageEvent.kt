package com.noximity.remmyChat.events

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a RemmyChat message is sent.
 * This event is primarily used for integrations like DiscordSRV.
 */
class RemmyChatMessageEvent(
    val player: Player,
    val channel: String,
    val rawMessage: String,
    val formattedMessage: Component
) : Event() {

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }

    override fun getHandlers(): HandlerList {
        return Companion.handlers
    }
}
