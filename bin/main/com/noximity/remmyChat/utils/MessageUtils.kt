package com.noximity.remmyChat.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

object MessageUtils {
    fun componentToString(component: Component): String {
        return PlainTextComponentSerializer.plainText().serialize(component)
    }

    fun createClickableCommand(text: String, command: String, hoverText: String, color: TextColor?): Component {
        return Component.text(text)
            .color(color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
    }

    fun createClickableSuggest(text: String, suggestion: String, hoverText: String, color: TextColor?): Component {
        return Component.text(text)
            .color(color)
            .clickEvent(ClickEvent.suggestCommand(suggestion))
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
    }

    fun newline(): TextComponent {
        return Component.text("\n")
    }
}