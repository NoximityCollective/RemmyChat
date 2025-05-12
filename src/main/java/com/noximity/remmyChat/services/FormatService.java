package com.noximity.remmyChat.services;

import com.noximity.remmyChat.RemmyChat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatService {

    private final RemmyChat plugin;
    private final MiniMessage miniMessage;
    private final Pattern urlPattern = Pattern.compile("(https?://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?)");

    public FormatService(RemmyChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component formatChatMessage(Player player, String channel, String message) {
        String playerName = player.getName();
        String displayName = player.getDisplayName();

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            playerName = PlaceholderAPI.setPlaceholders(player, playerName);
            displayName = PlaceholderAPI.setPlaceholders(player, displayName);
        }

        Component messageComponent = formatMessageContent(player, message);

        String format = plugin.getConfigManager().getChannel(channel).getFormat();
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        TagResolver.Builder tagResolverBuilder = TagResolver.builder()
                .resolver(Placeholder.parsed("player", playerName))
                .resolver(Placeholder.component("message", messageComponent))
                .resolver(Placeholder.parsed("displayname", displayName));
        try {
            return miniMessage.deserialize(format, tagResolverBuilder.build());
        } catch (Exception e) {
            plugin.getLogger().warning("Error formatting message: " + e.getMessage());
            return Component.text("Error in formatting: " + PlainTextComponentSerializer.plainText().serialize(messageComponent));
        }
    }

    /**
     * Formats the message content, handling URLs and player formatting
     *
     * @param player The player sending the message
     * @param message The raw message text
     * @return A formatted Component with URLs properly handled
     */
    private Component formatMessageContent(Player player, String message) {
        List<String> urls = new ArrayList<>();
        List<Integer> startPositions = new ArrayList<>();
        List<Integer> endPositions = new ArrayList<>();

        Matcher matcher = urlPattern.matcher(message);
        while (matcher.find()) {
            urls.add(matcher.group());
            startPositions.add(matcher.start());
            endPositions.add(matcher.end());
        }

        if (urls.isEmpty() || !plugin.getConfigManager().isLinkClickEnabled()) {
            return formatPlainMessage(message, player);
        }

        TextComponent.Builder builder = Component.text();
        int lastEnd = 0;

        for (int i = 0; i < urls.size(); i++) {
            if (startPositions.get(i) > lastEnd) {
                String beforeUrl = message.substring(lastEnd, startPositions.get(i));
                builder.append(formatPlainMessage(beforeUrl, player));
            }

            String url = urls.get(i);
            Component urlComponent = formatUrl(url);
            builder.append(urlComponent);

            lastEnd = endPositions.get(i);
        }

        if (lastEnd < message.length()) {
            String afterUrls = message.substring(lastEnd);
            builder.append(formatPlainMessage(afterUrls, player));
        }

        return builder.build();
    }

    private Component formatUrl(String url) {
        String colorHex = plugin.getConfig().getString("url-formatting.color", "#3498DB");

        TextComponent.Builder urlBuilder = Component.text()
                .content(url)
                .clickEvent(ClickEvent.openUrl(url));
        if (!colorHex.isEmpty()) {
            try {
                urlBuilder.color(net.kyori.adventure.text.format.TextColor.fromHexString(colorHex));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid color in URL format: " + colorHex);
            }
        }

        if (plugin.getConfig().getBoolean("url-formatting.underline", true)) {
            urlBuilder.decoration(TextDecoration.UNDERLINED, true);
        }

        if (plugin.getConfig().getBoolean("url-formatting.hover", true)) {
            String hoverText = plugin.getConfig().getString("url-formatting.hover-text", "<#AAAAAA>Click to open</hover-text>");
            Component hoverComponent = miniMessage.deserialize(hoverText);
            urlBuilder.hoverEvent(HoverEvent.showText(hoverComponent));
        }

        return urlBuilder.build();
    }

    private Component formatPlainMessage(String text, Player player) {
        if (!plugin.getConfigManager().isPlayerFormattingAllowed() &&
                !(player.hasPermission("remmychat.format.color"))) {
            text = escapeMinimessage(text);
        }

        return miniMessage.deserialize(text);
    }

    public Component formatSystemMessage(String path, TagResolver... placeholders) {
        String message = plugin.getMessages().getMessage(path);
        try {
            return miniMessage.deserialize(message, TagResolver.resolver(placeholders));
        } catch (Exception e) {
            plugin.getLogger().warning("Error formatting system message: " + e.getMessage());
            return Component.text("Error in formatting system message");
        }
    }

    /**
     * Manually escape MiniMessage format characters to prevent players from using formatting
     * when they don't have permission.
     *
     * @param input The input string to escape
     * @return The escaped string
     */
    private String escapeMinimessage(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}