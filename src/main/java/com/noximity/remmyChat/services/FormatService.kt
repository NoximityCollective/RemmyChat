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
        val playerName = player.name
        val displayName = player.name
        val messageComponent = formatMessageContent(player, message)

        // Debug settings
        val debugEnabled = plugin.config.getBoolean("debug.enabled", false)
        val debugFormatProcessing =
            debugEnabled &&
                    plugin.config.getBoolean("debug.format-processing", false)
        val debugGroupSelection =
            debugEnabled &&
                    plugin.config.getBoolean("debug.group-selection", false)

        // Get the channel
        var channel = if (channelName != null) plugin.configManager.getChannel(channelName) else null
        if (channel == null) {
            channel = plugin.configManager.defaultChannel
        }

        // Get channel display name if it exists
        var channelDisplayName = ""
        if (channel != null && channel.hasDisplayName()) {
            channelDisplayName = (channel.displayName ?: "") + " "
        }

        // The default formatting approach using templates
        val channelPrefixRef = channel?.prefix
        var channelPrefix = ""

        // If using group formats with LuckPerms
        if (plugin.configManager.isUseGroupFormat &&
            plugin.permissionService.isLuckPermsHooked
        ) {
            val groupFormat = plugin
                .permissionService
                .getHighestGroupFormat(player)

            // Debug info for group selection
            if (debugGroupSelection) {
                if (groupFormat != null) {
                    plugin.debugLog(
                        "Using group format for player " +
                                playerName +
                                ": " +
                                groupFormat.name
                    )
                    plugin.debugLog(
                        "Format string: " + groupFormat.format
                    )
                } else {
                    plugin.debugLog(
                        "No group format found for player " + playerName
                    )
                }
            }

            // If we have a custom format for this group, use it directly
            if (groupFormat != null && groupFormat.format?.isNotEmpty() == true) {
                // Process custom format with our placeholders system
                var customFormat = groupFormat.format ?: ""

                if (debugFormatProcessing) {
                    plugin.debugLog("Original format: " + customFormat)
                }

                // Important: First apply custom placeholders
                customFormat = plugin.placeholderManager.applyCustomPlaceholders(customFormat)

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "After custom placeholder processing: " + customFormat
                    )
                }

                // Add the channel display name if it exists
                if (channelDisplayName.isNotEmpty()) {
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
                if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                    customFormat = PlaceholderAPI.setPlaceholders(player, customFormat)
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
                    plugin.logger.warning("Error formatting custom message: ${e.message}")
                    if (debugFormatProcessing) {
                        plugin.logger.warning("Failed format: $customFormat")
                    }
                    return Component.text(
                        "Error in formatting: " +
                                PlainTextComponentSerializer.plainText().serialize(messageComponent)
                    )
                }
            }

            // Otherwise fallback to the old template system
            var nameStyle = "default"
            var groupPrefixRef = ""

            if (groupFormat != null) {
                nameStyle = groupFormat.nameStyle ?: "default"
                groupPrefixRef = groupFormat.prefix ?: ""
            }

            val formattedName: String = plugin.configManager
                .getNameStyleTemplate(nameStyle ?: "default")
                .replace("%player_name%", displayName)
            var groupPrefix = ""

            if (groupPrefixRef.isNotEmpty()) {
                groupPrefix = plugin.configManager.getGroupPrefixTemplate(groupPrefixRef)
                if (groupPrefix.isNotEmpty()) {
                    groupPrefix += " "
                }
            }

            if (channelPrefixRef != null && channelPrefixRef.isNotEmpty()) {
                channelPrefix = plugin.configManager.getChannelPrefixTemplate(channelPrefixRef)
                if (channelPrefix.isNotEmpty()) {
                    channelPrefix += " "
                }
            }

            var hoverText = plugin.configManager.getHoverTemplate(channel?.hover ?: "player-info")
            if (hoverText.isEmpty()) {
                hoverText = plugin.configManager.getHoverTemplate("player-info")
            }

            // Apply placeholders to hover text
            hoverText = hoverText.replace("%player_name%", playerName)
            hoverText = plugin.placeholderManager.applyAllPlaceholders(player, hoverText)

            val chatFormat = plugin.configManager.chatFormat ?: "%channel_prefix% %group_prefix%%name%: %message%"
            var messageFormat: String

            if (plugin.configManager.isFormatHoverEnabled &&
                hoverText.isNotEmpty()
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
            if (channelDisplayName.isNotEmpty()) {
                messageFormat = channelDisplayName + messageFormat
            }

            // Process all custom placeholders in the final message format
            messageFormat = plugin.placeholderManager.applyAllPlaceholders(player, messageFormat)

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
                plugin.logger.warning("Error formatting message: ${e.message}")
                return Component.text(
                    "Error in formatting: " +
                            PlainTextComponentSerializer.plainText().serialize(messageComponent)
                )
            }
        }

        // Fallback to original implementation
        val formattedName: String = plugin.configManager
            .getNameStyleTemplate("default")
            .replace("%player_name%", displayName)

        if (channelPrefixRef != null && channelPrefixRef.isNotEmpty()) {
            channelPrefix = plugin.configManager.getChannelPrefixTemplate(channelPrefixRef)
            if (channelPrefix.isNotEmpty()) {
                channelPrefix += " "
            }
        }

        var hoverText = plugin.configManager.getHoverTemplate(channel?.hover ?: "player-info")
        if (hoverText.isEmpty()) {
            hoverText = plugin.configManager.getHoverTemplate("player-info")
        }

        // Apply placeholders to hover text
        hoverText = hoverText.replace("%player_name%", playerName)
        hoverText = plugin.placeholderManager.applyAllPlaceholders(player, hoverText)

        val chatFormat = plugin.configManager.chatFormat ?: "%channel_prefix% %group_prefix%%name%: %message%"
        var messageFormat: String

        if (plugin.configManager.isFormatHoverEnabled &&
            hoverText.isNotEmpty()
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
        if (channelDisplayName.isNotEmpty()) {
            messageFormat = channelDisplayName + messageFormat
        }

        // Process all custom placeholders in the final message format
        messageFormat = plugin.placeholderManager.applyAllPlaceholders(player, messageFormat)

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
            plugin.logger.warning("Error formatting message: ${e.message}")
            return Component.text(
                "Error in formatting: " +
                        PlainTextComponentSerializer.plainText().serialize(messageComponent)
            )
        }
    }

    private fun formatMessageContent(player: Player, message: String): Component {
        // Parse custom placeholders in message content if enabled
        var processedMessage = message
        if (plugin.configManager.isParsePlaceholdersInMessages) {
            processedMessage = plugin.placeholderManager.applyCustomPlaceholders(processedMessage)
        }

        // Parse PAPI placeholders in message content if enabled
        if (plugin.configManager.isParsePapiPlaceholdersInMessages) {
            processedMessage = plugin.placeholderManager.applyPapiPlaceholders(player, processedMessage)
        }

        // Symbol replacement using regex (case-insensitive, dashes/numbers allowed)
        val symbols = plugin.configManager.symbolMappings
        if (symbols.isNotEmpty()) {
            val matcher: Matcher = SYMBOL_PATTERN.matcher(processedMessage)
            val sb = StringBuffer()
            while (matcher.find()) {
                val code = matcher.group()
                val codeKey: String = code.lowercase(Locale.getDefault())
                val replacement = symbols[codeKey]
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
            processedMessage = sb.toString()
        }

        val urls: MutableList<String> = ArrayList()
        val startPositions: MutableList<Int> = ArrayList()
        val endPositions: MutableList<Int> = ArrayList()

        val matcher = urlPattern.matcher(processedMessage)
        while (matcher.find()) {
            urls.add(matcher.group())
            startPositions.add(matcher.start())
            endPositions.add(matcher.end())
        }

        if (urls.isEmpty() || !plugin.configManager.isLinkClickEnabled) {
            return formatPlainMessage(processedMessage, player)
        }

        val builder = Component.text()
        var lastEnd = 0

        for (i in urls.indices) {
            if (startPositions[i] > lastEnd) {
                val beforeUrl: String = processedMessage.substring(lastEnd, startPositions[i])
                builder.append(formatPlainMessage(beforeUrl, player))
            }

            val url = urls[i]
            val urlComponent = formatUrl(url)
            builder.append(urlComponent)

            lastEnd = endPositions[i]
        }

        if (lastEnd < processedMessage.length) {
            val afterUrls: String = processedMessage.substring(lastEnd)
            builder.append(formatPlainMessage(afterUrls, player))
        }

        return builder.build()
    }

    private fun formatUrl(url: String): Component {
        val colorHex: String = plugin.config.getString("url-formatting.color", "#3498DB") ?: "#3498DB"

        val urlBuilder = Component.text()
            .content(url)
            .clickEvent(ClickEvent.openUrl(url))
        if (colorHex.isNotEmpty()) {
            try {
                urlBuilder.color(TextColor.fromHexString(colorHex))
            } catch (e: Exception) {
                plugin.logger.warning("Invalid color in URL format: $colorHex")
            }
        }

        if (plugin.config.getBoolean("url-formatting.underline", true)) {
            urlBuilder.decoration(TextDecoration.UNDERLINED, true)
        }

        if (plugin.config.getBoolean("url-formatting.hover", true)) {
            val hoverText: String = plugin.config.getString(
                "url-formatting.hover-text",
                "<#AAAAAA>Click to open"
            ) ?: "<#AAAAAA>Click to open"
            val hoverComponent = miniMessage.deserialize(hoverText)
            urlBuilder.hoverEvent(HoverEvent.showText(hoverComponent))
        }

        return urlBuilder.build()
    }

    private fun formatPlainMessage(text: String, player: Player): Component {
        var processedText = text
        if (!plugin.configManager.isPlayerFormattingAllowed &&
            !player.hasPermission("remmychat.format.color")
        ) {
            processedText = escapeMinimessage(processedText)
        }

        return miniMessage.deserialize(processedText)
    }

    fun formatSystemMessage(
        path: String?,
        vararg placeholders: TagResolver
    ): Component? {
        val message = plugin.messages.getMessage(path) ?: return null

        // Skip empty messages completely by returning null
        if (message.trim().isEmpty()) {
            return null
        }

        try {
            return miniMessage.deserialize(
                message,
                TagResolver.resolver(*placeholders)
            )
        } catch (e: Exception) {
            plugin.logger.warning("Error formatting system message: ${e.message}")
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
