package com.noximity.remmyChat.services;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.Channel;
import com.noximity.remmyChat.models.GroupFormat;
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

    public Component formatChatMessage(Player player, String channelName, String message) {
        String playerName = player.getName();
        String displayName = player.getDisplayName();

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            playerName = PlaceholderAPI.setPlaceholders(player, playerName);
            displayName = PlaceholderAPI.setPlaceholders(player, displayName);
        }

        Component messageComponent = formatMessageContent(player, message);

        Channel channel = plugin.getConfigManager().getChannel(channelName);
        if (channel == null) {
            channel = plugin.getConfigManager().getDefaultChannel();
        }

        String nameStyle = plugin.getConfigManager().getNameStyleTemplate("default");
        String channelPrefixRef = channel.getPrefix();
        String groupPrefixRef = "";
        String channelPrefix = "";
        String groupPrefix = "";

        if (plugin.getConfigManager().isUseGroupFormat() && plugin.getPermissionService().isLuckPermsHooked()) {
            GroupFormat groupFormat = plugin.getPermissionService().getHighestGroupFormat(player);
            if (groupFormat != null) {
                nameStyle = plugin.getConfigManager().getNameStyleTemplate(groupFormat.getNameStyle());
                groupPrefixRef = groupFormat.getPrefix();
            }
        }

        if (!channelPrefixRef.isEmpty()) {
            channelPrefix = plugin.getConfigManager().getChannelPrefixTemplate(channelPrefixRef);
            if (channelPrefix.isEmpty()) {
                channelPrefix = channelPrefixRef;
            }
            channelPrefix += " ";
        }

        if (!groupPrefixRef.isEmpty()) {
            groupPrefix = plugin.getConfigManager().getGroupPrefixTemplate(groupPrefixRef);
            if (groupPrefix.isEmpty()) {
                groupPrefix = groupPrefixRef;
            }
            groupPrefix += " ";
        }

        String formattedName = nameStyle.replace("%player_name%", displayName);

        String hoverText = plugin.getConfigManager().getHoverTemplate(channel.getHover());
        if (hoverText.isEmpty()) {
            hoverText = plugin.getConfigManager().getHoverTemplate("player-info");
        }

        hoverText = hoverText.replace("%player_name%", playerName);

        String chatFormat = plugin.getConfigManager().getChatFormat();
        String messageFormat = chatFormat
                .replace("%channel_prefix%", channelPrefix)
                .replace("%group_prefix%", groupPrefix)
                .replace("%name%", formattedName)
                .replace("%message%", "<message>");

        if (plugin.getConfigManager().isFormatHoverEnabled() && !hoverText.isEmpty()) {
            String nameWithHover = "<hover:show_text:'" + hoverText + "'><click:suggest_command:/msg " + playerName + " >" + formattedName + "</click></hover>";
            messageFormat = chatFormat
                    .replace("%channel_prefix%", channelPrefix)
                    .replace("%group_prefix%", groupPrefix)
                    .replace("%name%", nameWithHover)
                    .replace("%message%", "<message>");
        }

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            messageFormat = PlaceholderAPI.setPlaceholders(player, messageFormat);
        }

        try {
            return miniMessage.deserialize(messageFormat, TagResolver.builder()
                    .resolver(Placeholder.component("message", messageComponent))
                    .build());
        } catch (Exception e) {
            plugin.getLogger().warning("Error formatting message: " + e.getMessage());
            return Component.text("Error in formatting: " + PlainTextComponentSerializer.plainText().serialize(messageComponent));
        }
    }

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
            String hoverText = plugin.getConfig().getString("url-formatting.hover-text", "<#AAAAAA>Click to open");
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

    private String escapeMinimessage(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}

