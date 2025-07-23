package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class FormatService(private val plugin: RemmyChat) {
    private val miniMessage: MiniMessage
    private val urlPattern: Pattern = Pattern.compile(
        "(https?://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?)"
    )

    init {
        this.miniMessage = MiniMessage.miniMessage()
    }

    fun formatChatMessage(
        player: Player,
        channelName: String?,
        message: String
    ): Component {
        val playerName = player.getName()
        val displayName = player.getDisplayName()
        val messageComponent = formatMessageContent(player, message)

        // Debug settings
        val debugEnabled = plugin
            .getConfig()
            .getBoolean("debug.enabled", false)
        val debugFormatProcessing =
            debugEnabled &&
                    plugin.getConfig().getBoolean("debug.format-processing", false)
        val debugGroupSelection =
            debugEnabled &&
                    plugin.getConfig().getBoolean("debug.group-selection", false)

        // Get the channel
        var channel = plugin.getConfigManager().getChannel(channelName)
        if (channel == null) {
            channel = plugin.getConfigManager().getDefaultChannel()
        }

        // Get channel display name if it exists
        var channelDisplayName = ""
        if (channel != null && channel.hasDisplayName()) {
            channelDisplayName = channel.getDisplayName() + " "
        }

        // The default formatting approach using templates
        val channelPrefixRef = channel.getPrefix()
        var channelPrefix = ""

        // If using group formats with LuckPerms
        if (plugin.getConfigManager().isUseGroupFormat() &&
            plugin.getPermissionService().isLuckPermsHooked()
        ) {
            val groupFormat = plugin
                .getPermissionService()
                .getHighestGroupFormat(player)

            // Debug info for group selection
            if (debugGroupSelection) {
                if (groupFormat != null) {
                    plugin.debugLog(
                        "Using group format for player " +
                                playerName +
                                ": " +
                                groupFormat.getName()
                    )
                    plugin.debugLog(
                        "Format string: " + groupFormat.getFormat()
                    )
                } else {
                    plugin.debugLog(
                        "No group format found for player " + playerName
                    )
                }
            }

            // If we have a custom format for this group, use it directly
            if (groupFormat != null && !groupFormat.getFormat().isEmpty()) {
                // Process custom format with our placeholders system
                var customFormat = groupFormat.getFormat()

                if (debugFormatProcessing) {
                    plugin.debugLog("Original format: " + customFormat)
                }

                // Important: First apply custom placeholders
                customFormat = plugin
                    .getPlaceholderManager()
                    .applyCustomPlaceholders(customFormat)

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "After custom placeholder processing: " + customFormat
                    )
                }

                // Add the channel display name if it exists
                if (!channelDisplayName.isEmpty()) {
                    customFormat = channelDisplayName + customFormat
                }

                // Then replace the special placeholders that need direct substitution
                customFormat = customFormat.replace(
                    "%player_name%",
                    playerName
                )
                customFormat = customFormat.replace(
                    "%display_name%",
                    displayName
                )
                customFormat = customFormat.replace(
                    "%channel_name%",
                    channelDisplayName.trim { it <= ' ' }
                )

                // Replace %message% with component placeholder - must be done after other placeholder processing
                // but before MiniMessage deserialization
                customFormat = customFormat.replace("%message%", "<message>")

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "Final format before deserialization: " + customFormat
                    )
                }

                // Apply PAPI placeholders if available
                if (plugin
                        .getServer()
                        .getPluginManager()
                        .getPlugin("PlaceholderAPI") !=
                    null
                ) {
                    customFormat = PlaceholderAPI.setPlaceholders(
                        player,
                        customFormat
                    )
                }

                try {
                    return miniMessage.deserialize(
                        customFormat,
                        TagResolver.builder()
                            .resolver(
                                Placeholder.component(
                                    "message",
                                    messageComponent
                                )
                            )
                            .build()
                    )
                } catch (e: Exception) {
                    plugin
                        .getLogger()
                        .warning(
                            "Error formatting custom message: " + e.message
                        )
                    if (debugFormatProcessing) {
                        plugin
                            .getLogger()
                            .warning("Failed format: " + customFormat)
                    }
                    return Component.text(
                        "Error in formatting: " +
                                PlainTextComponentSerializer.plainText().serialize(
                                    messageComponent
                                )
                    )
                }
            }

            // Otherwise fallback to the old template system
            var nameStyle: String? = "default"
            var groupPrefixRef = ""

            if (groupFormat != null) {
                nameStyle = groupFormat.getNameStyle()
                groupPrefixRef = groupFormat.getPrefix()
            }

            val formattedName: String = plugin
                .getConfigManager()
                .getNameStyleTemplate(nameStyle)
                .replace("%player_name%", displayName)
            var groupPrefix = ""

            if (!groupPrefixRef.isEmpty()) {
                groupPrefix = plugin
                    .getConfigManager()
                    .getGroupPrefixTemplate(groupPrefixRef)
                if (!groupPrefix.isEmpty()) {
                    groupPrefix += " "
                }
            }

            if (!channelPrefixRef.isEmpty()) {
                channelPrefix = plugin
                    .getConfigManager()
                    .getChannelPrefixTemplate(channelPrefixRef)
                if (!channelPrefix.isEmpty()) {
                    channelPrefix += " "
                }
            }

            var hoverText = plugin
                .getConfigManager()
                .getHoverTemplate(channel.getHover())
            if (hoverText.isEmpty()) {
                hoverText = plugin
                    .getConfigManager()
                    .getHoverTemplate("player-info")
            }

            // Apply placeholders to hover text
            hoverText = hoverText.replace("%player_name%", playerName)
            hoverText = plugin
                .getPlaceholderManager()
                .applyAllPlaceholders(player, hoverText)

            val chatFormat = plugin.getConfigManager().getChatFormat()
            var messageFormat: String

            if (plugin.getConfigManager().isFormatHoverEnabled() &&
                !hoverText.isEmpty()
            ) {
                val nameWithHover =
                    "<hover:show_text:'" +
                            hoverText +
                            "'><click:suggest_command:/msg " +
                            playerName +
                            " >" +
                            formattedName +
                            "</click></hover>"
                messageFormat = chatFormat
                    .replace("%channel_prefix%", channelPrefix)
                    .replace("%group_prefix%", groupPrefix)
                    .replace("%name%", nameWithHover)
                    .replace("%message%", "<message>")
            } else {
                messageFormat = chatFormat
                    .replace("%channel_prefix%", channelPrefix)
                    .replace("%group_prefix%", groupPrefix)
                    .replace("%name%", formattedName)
                    .replace("%message%", "<message>")
            }

            // Add the channel display name if it exists
            if (!channelDisplayName.isEmpty()) {
                messageFormat = channelDisplayName + messageFormat
            }

            // Process all custom placeholders in the final message format
            messageFormat = plugin
                .getPlaceholderManager()
                .applyAllPlaceholders(player, messageFormat)

            // Replace %channel_name% after applying custom placeholders
            messageFormat = messageFormat.replace(
                "%channel_name%",
                channelDisplayName.trim { it <= ' ' }
            )

            try {
                return miniMessage.deserialize(
                    messageFormat,
                    TagResolver.builder()
                        .resolver(
                            Placeholder.component("message", messageComponent)
                        )
                        .build()
                )
            } catch (e: Exception) {
                plugin
                    .getLogger()
                    .warning("Error formatting message: " + e.message)
                return Component.text(
                    "Error in formatting: " +
                            PlainTextComponentSerializer.plainText().serialize(
                                messageComponent
                            )
                )
            }
        }

        // Fallback to original implementation
        val formattedName: String = plugin
            .getConfigManager()
            .getNameStyleTemplate("default")
            .replace("%player_name%", displayName)

        if (!channelPrefixRef.isEmpty()) {
            channelPrefix = plugin
                .getConfigManager()
                .getChannelPrefixTemplate(channelPrefixRef)
            if (!channelPrefix.isEmpty()) {
                channelPrefix += " "
            }
        }

        var hoverText = plugin
            .getConfigManager()
            .getHoverTemplate(channel.getHover())
        if (hoverText.isEmpty()) {
            hoverText = plugin
                .getConfigManager()
                .getHoverTemplate("player-info")
        }

        // Apply placeholders to hover text
        hoverText = hoverText.replace("%player_name%", playerName)
        hoverText = plugin
            .getPlaceholderManager()
            .applyAllPlaceholders(player, hoverText)

        val chatFormat = plugin.getConfigManager().getChatFormat()
        var messageFormat: String

        if (plugin.getConfigManager().isFormatHoverEnabled() &&
            !hoverText.isEmpty()
        ) {
            val nameWithHover =
                "<hover:show_text:'" +
                        hoverText +
                        "'><click:suggest_command:/msg " +
                        playerName +
                        " >" +
                        formattedName +
                        "</click></hover>"
            messageFormat = chatFormat
                .replace("%channel_prefix%", channelPrefix)
                .replace("%group_prefix%", "")
                .replace("%name%", nameWithHover)
                .replace("%message%", "<message>")
        } else {
            messageFormat = chatFormat
                .replace("%channel_prefix%", channelPrefix)
                .replace("%group_prefix%", "")
                .replace("%name%", formattedName)
                .replace("%message%", "<message>")
        }

        // Add the channel display name if it exists
        if (!channelDisplayName.isEmpty()) {
            messageFormat = channelDisplayName + messageFormat
        }

        // Process all custom placeholders in the final message format
        messageFormat = plugin
            .getPlaceholderManager()
            .applyAllPlaceholders(player, messageFormat)

        // Replace %channel_name% after applying custom placeholders
        messageFormat = messageFormat.replace(
            "%channel_name%",
            channelDisplayName.trim { it <= ' ' }
        )

        try {
            return miniMessage.deserialize(
                messageFormat,
                TagResolver.builder()
                    .resolver(
                        Placeholder.component("message", messageComponent)
                    )
                    .build()
            )
        } catch (e: Exception) {
            plugin
                .getLogger()
                .warning("Error formatting message: " + e.message)
            return Component.text(
                "Error in formatting: " +
                        PlainTextComponentSerializer.plainText().serialize(
                            messageComponent
                        )
            )
        }
    }

    private fun formatMessageContent(player: Player, message: String): Component {
        // Parse custom placeholders in message content if enabled
        var message = message
        if (plugin.getConfigManager().isParsePlaceholdersInMessages()) {
            message = plugin
                .getPlaceholderManager()
                .applyCustomPlaceholders(message)
        }

        // Parse PAPI placeholders in message content if enabled
        if (plugin.getConfigManager().isParsePapiPlaceholdersInMessages()) {
            message = plugin
                .getPlaceholderManager()
                .applyPapiPlaceholders(player, message)
        }

        // Symbol replacement using regex (case-insensitive, dashes/numbers allowed)
        val symbols = plugin
            .getConfigManager()
            .getSymbolMappings()
        if (symbols != null && !symbols.isEmpty()) {
            val matcher: Matcher = SYMBOL_PATTERN.matcher(message)
            val sb = StringBuffer()
            while (matcher.find()) {
                val code = matcher.group()
                val codeKey: String = code.lowercase(Locale.getDefault())
                val replacement = symbols.get(codeKey)
                if (replacement != null) {
                    matcher.appendReplacement(
                        sb,
                        Matcher.quoteReplacement(replacement)
                    )
                } else {
                    matcher.appendReplacement(
                        sb,
                        Matcher.quoteReplacement(code)
                    )
                }
            }
            matcher.appendTail(sb)
            message = sb.toString()
        }

        val urls: MutableList<String> = ArrayList<String>()
        val startPositions: MutableList<Int?> = ArrayList<Int?>()
        val endPositions: MutableList<Int?> = ArrayList<Int?>()

        val matcher = urlPattern.matcher(message)
        while (matcher.find()) {
            urls.add(matcher.group())
            startPositions.add(matcher.start())
            endPositions.add(matcher.end())
        }

        if (urls.isEmpty() || !plugin.getConfigManager().isLinkClickEnabled()) {
            return formatPlainMessage(message, player)
        }

        val builder = Component.text()
        var lastEnd = 0

        for (i in urls.indices) {
            if (startPositions.get(i)!! > lastEnd) {
                val beforeUrl: String = message.substring(
                    lastEnd,
                    startPositions.get(i)
                )
                builder.append(formatPlainMessage(beforeUrl, player))
            }

            val url = urls.get(i)
            val urlComponent = formatUrl(url)
            builder.append(urlComponent)

            lastEnd = endPositions.get(i)!!
        }

        if (lastEnd < message.length) {
            val afterUrls: String = message.substring(lastEnd)
            builder.append(formatPlainMessage(afterUrls, player))
        }

        return builder.build()
    }

    private fun formatUrl(url: String): Component {
        val colorHex: String = plugin
            .getConfig()
            .getString("url-formatting.color", "#3498DB")!!

        val urlBuilder = Component.text()
            .content(url)
            .clickEvent(ClickEvent.openUrl(url))
        if (!colorHex.isEmpty()) {
            try {
                urlBuilder.color(
                    TextColor.fromHexString(
                        colorHex
                    )
                )
            } catch (e: Exception) {
                plugin
                    .getLogger()
                    .warning("Invalid color in URL format: " + colorHex)
            }
        }

        if (plugin.getConfig().getBoolean("url-formatting.underline", true)) {
            urlBuilder.decoration(TextDecoration.UNDERLINED, true)
        }

        if (plugin.getConfig().getBoolean("url-formatting.hover", true)) {
            val hoverText: String = plugin
                .getConfig()
                .getString(
                    "url-formatting.hover-text",
                    "<#AAAAAA>Click to open"
                )!!
            val hoverComponent = miniMessage.deserialize(hoverText)
            urlBuilder.hoverEvent(HoverEvent.showText(hoverComponent))
        }

        return urlBuilder.build()
    }

    private fun formatPlainMessage(text: String, player: Player): Component {
        var text = text
        if (!plugin.getConfigManager().isPlayerFormattingAllowed() &&
            !(player.hasPermission("remmychat.format.color"))
        ) {
            text = escapeMinimessage(text)
        }

        return miniMessage.deserialize(text)
    }

    fun formatSystemMessage(
        path: String?,
        vararg placeholders: TagResolver?
    ): Component? {
        val message = plugin.getMessages().getMessage(path)

        // Skip empty messages completely by returning null
        if (message == null || message.trim { it <= ' ' }.isEmpty()) {
            return null
        }

        try {
            return miniMessage.deserialize(
                message,
                TagResolver.resolver(*placeholders)
            )
        } catch (e: Exception) {
            plugin
                .getLogger()
                .warning("Error formatting system message: " + e.message)
            return Component.text("Error in formatting system message")
        }
    }

    private fun escapeMinimessage(input: String): String {
        return input.replace("<", "\\<").replace(">", "\\>")
    }

    companion object {
        private val SYMBOL_PATTERN: Pattern = Pattern.compile(
            "(?i):([a-z0-9_-]+):"
        )
    }
}
