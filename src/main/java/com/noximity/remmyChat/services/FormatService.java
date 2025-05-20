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
        Component messageComponent = formatMessageContent(player, message);

        // Debug settings
        boolean debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        boolean debugFormatProcessing = debugEnabled && plugin.getConfig().getBoolean("debug.format-processing", false);
        boolean debugGroupSelection = debugEnabled && plugin.getConfig().getBoolean("debug.group-selection", false);

        // Get the channel
        Channel channel = plugin.getConfigManager().getChannel(channelName);
        if (channel == null) {
            channel = plugin.getConfigManager().getDefaultChannel();
        }

        // Get channel display name if it exists
        String channelDisplayName = "";
        if (channel != null && channel.hasDisplayName()) {
            channelDisplayName = channel.getDisplayName() + " ";
        }

        // The default formatting approach using templates
        String channelPrefixRef = channel.getPrefix();
        String channelPrefix = "";

        // If using group formats with LuckPerms
        if (plugin.getConfigManager().isUseGroupFormat() && plugin.getPermissionService().isLuckPermsHooked()) {
            GroupFormat groupFormat = plugin.getPermissionService().getHighestGroupFormat(player);

            // Debug info for group selection
            if (debugGroupSelection) {
                if (groupFormat != null) {
                    plugin.getLogger().info("Using group format for player " + playerName + ": " + groupFormat.getName());
                    plugin.getLogger().info("Format string: " + groupFormat.getFormat());
                } else {
                    plugin.getLogger().info("No group format found for player " + playerName);
                }
            }

            // If we have a custom format for this group, use it directly
            if (groupFormat != null && !groupFormat.getFormat().isEmpty()) {
                // Process custom format with our placeholders system
                String customFormat = groupFormat.getFormat();

                if (debugFormatProcessing) {
                    plugin.getLogger().info("Original format: " + customFormat);
                }

                // Important: First apply custom placeholders
                customFormat = plugin.getPlaceholderManager().applyCustomPlaceholders(customFormat);

                if (debugFormatProcessing) {
                    plugin.getLogger().info("After custom placeholder processing: " + customFormat);
                }

                // Add the channel display name if it exists
                if (!channelDisplayName.isEmpty()) {
                    customFormat = channelDisplayName + customFormat;
                }

                // Then replace the special placeholders that need direct substitution
                customFormat = customFormat.replace("%player_name%", playerName);
                customFormat = customFormat.replace("%display_name%", displayName);
                customFormat = customFormat.replace("%channel_name%", channelDisplayName.trim());

                // Replace %message% with component placeholder - must be done after other placeholder processing
                // but before MiniMessage deserialization
                customFormat = customFormat.replace("%message%", "<message>");

                if (debugFormatProcessing) {
                    plugin.getLogger().info("Final format before deserialization: " + customFormat);
                }

                // Apply PAPI placeholders if available
                if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    customFormat = PlaceholderAPI.setPlaceholders(player, customFormat);
                }

                try {
                    return miniMessage.deserialize(customFormat, TagResolver.builder()
                            .resolver(Placeholder.component("message", messageComponent))
                            .build());
                } catch (Exception e) {
                    plugin.getLogger().warning("Error formatting custom message: " + e.getMessage());
                    if (debugFormatProcessing) {
                        plugin.getLogger().warning("Failed format: " + customFormat);
                    }
                    return Component.text("Error in formatting: " + PlainTextComponentSerializer.plainText().serialize(messageComponent));
                }
            }

            // Otherwise fallback to the old template system
            String nameStyle = "default";
            String groupPrefixRef = "";

            if (groupFormat != null) {
                nameStyle = groupFormat.getNameStyle();
                groupPrefixRef = groupFormat.getPrefix();
            }

            String formattedName = plugin.getConfigManager().getNameStyleTemplate(nameStyle).replace("%player_name%", displayName);
            String groupPrefix = "";

            if (!groupPrefixRef.isEmpty()) {
                groupPrefix = plugin.getConfigManager().getGroupPrefixTemplate(groupPrefixRef);
                if (!groupPrefix.isEmpty()) {
                    groupPrefix += " ";
                }
            }

            if (!channelPrefixRef.isEmpty()) {
                channelPrefix = plugin.getConfigManager().getChannelPrefixTemplate(channelPrefixRef);
                if (!channelPrefix.isEmpty()) {
                    channelPrefix += " ";
                }
            }

            String hoverText = plugin.getConfigManager().getHoverTemplate(channel.getHover());
            if (hoverText.isEmpty()) {
                hoverText = plugin.getConfigManager().getHoverTemplate("player-info");
            }

            // Apply placeholders to hover text
            hoverText = hoverText.replace("%player_name%", playerName);
            hoverText = plugin.getPlaceholderManager().applyAllPlaceholders(player, hoverText);

            String chatFormat = plugin.getConfigManager().getChatFormat();
            String messageFormat;

            if (plugin.getConfigManager().isFormatHoverEnabled() && !hoverText.isEmpty()) {
                String nameWithHover = "<hover:show_text:'" + hoverText + "'><click:suggest_command:/msg " + playerName + " >" + formattedName + "</click></hover>";
                messageFormat = chatFormat
                        .replace("%channel_prefix%", channelPrefix)
                        .replace("%group_prefix%", groupPrefix)
                        .replace("%name%", nameWithHover)
                        .replace("%message%", "<message>");
            } else {
                messageFormat = chatFormat
                        .replace("%channel_prefix%", channelPrefix)
                        .replace("%group_prefix%", groupPrefix)
                        .replace("%name%", formattedName)
                        .replace("%message%", "<message>");
            }

            // Add the channel display name if it exists
            if (!channelDisplayName.isEmpty()) {
                messageFormat = channelDisplayName + messageFormat;
            }

            // Process all custom placeholders in the final message format
            messageFormat = plugin.getPlaceholderManager().applyAllPlaceholders(player, messageFormat);

            // Replace %channel_name% after applying custom placeholders
            messageFormat = messageFormat.replace("%channel_name%", channelDisplayName.trim());

            try {
                return miniMessage.deserialize(messageFormat, TagResolver.builder()
                        .resolver(Placeholder.component("message", messageComponent))
                        .build());
            } catch (Exception e) {
                plugin.getLogger().warning("Error formatting message: " + e.getMessage());
                return Component.text("Error in formatting: " + PlainTextComponentSerializer.plainText().serialize(messageComponent));
            }
        }

        // Fallback to original implementation
        String formattedName = plugin.getConfigManager().getNameStyleTemplate("default").replace("%player_name%", displayName);

        if (!channelPrefixRef.isEmpty()) {
            channelPrefix = plugin.getConfigManager().getChannelPrefixTemplate(channelPrefixRef);
            if (!channelPrefix.isEmpty()) {
                channelPrefix += " ";
            }
        }

        String hoverText = plugin.getConfigManager().getHoverTemplate(channel.getHover());
        if (hoverText.isEmpty()) {
            hoverText = plugin.getConfigManager().getHoverTemplate("player-info");
        }

        // Apply placeholders to hover text
        hoverText = hoverText.replace("%player_name%", playerName);
        hoverText = plugin.getPlaceholderManager().applyAllPlaceholders(player, hoverText);

        String chatFormat = plugin.getConfigManager().getChatFormat();
        String messageFormat;

        if (plugin.getConfigManager().isFormatHoverEnabled() && !hoverText.isEmpty()) {
            String nameWithHover = "<hover:show_text:'" + hoverText + "'><click:suggest_command:/msg " + playerName + " >" + formattedName + "</click></hover>";
            messageFormat = chatFormat
                    .replace("%channel_prefix%", channelPrefix)
                    .replace("%group_prefix%", "")
                    .replace("%name%", nameWithHover)
                    .replace("%message%", "<message>");
        } else {
            messageFormat = chatFormat
                    .replace("%channel_prefix%", channelPrefix)
                    .replace("%group_prefix%", "")
                    .replace("%name%", formattedName)
                    .replace("%message%", "<message>");
        }

        // Add the channel display name if it exists
        if (!channelDisplayName.isEmpty()) {
            messageFormat = channelDisplayName + messageFormat;
        }

        // Process all custom placeholders in the final message format
        messageFormat = plugin.getPlaceholderManager().applyAllPlaceholders(player, messageFormat);

        // Replace %channel_name% after applying custom placeholders
        messageFormat = messageFormat.replace("%channel_name%", channelDisplayName.trim());

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

