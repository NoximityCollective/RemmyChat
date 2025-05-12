package com.noximity.remmyChat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class MessageUtils {

    private MessageUtils() {
    }

    public static String componentToString(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static Component createClickableCommand(String text, String command, String hoverText, TextColor color) {
        return Component.text(text)
                .color(color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText)));
    }

    public static Component createClickableSuggest(String text, String suggestion, String hoverText, TextColor color) {
        return Component.text(text)
                .color(color)
                .clickEvent(ClickEvent.suggestCommand(suggestion))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText)));
    }

    public static TextComponent newline() {
        return Component.text("\n");
    }
}