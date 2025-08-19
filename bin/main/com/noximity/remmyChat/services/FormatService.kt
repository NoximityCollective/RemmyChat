package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.compatibility.VersionCompatibility
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

    /**
     * Format a system message from the messages.yml file
     */
    fun formatSystemMessage(key: String, vararg placeholders: TagResolver): Component? {
        val message = plugin.messages.getMessage(key) ?: return null
        return miniMessage.deserialize(message, *placeholders)
    }

    /**
     * Format a system message from the messages.yml file with simple string replacement
     */
    fun formatSystemMessage(key: String): Component? {
        val message = plugin.messages.getMessage(key) ?: return null
        return miniMessage.deserialize(message)
    }

    /**
     * Format a message string into a Component
     */
    fun formatMessage(message: String): Component {
        return miniMessage.deserialize(message)
    }

    fun formatChatMessage(
        player: Player,
        channelName: String?,
        message: String
    ): Component {
        val playerName = player.name
        val displayName = VersionCompatibility.getPlayerDisplayName(player)
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

        // Check if channel has a custom format first
        if (channel != null && channel.format.isNotEmpty() && channel.format != "%player_display_name% <dark_gray>»</dark_gray> %message%") {
            // Use channel-specific format
            var channelFormat = channel.format

            if (debugFormatProcessing) {
                plugin.debugLog("Using channel-specific format for channel '${channel.name}': $channelFormat")
            }

            // Replace basic placeholders
            channelFormat = channelFormat.replace("%player_name%", playerName)
            channelFormat = channelFormat.replace("%player_display_name%", displayName)
            channelFormat = channelFormat.replace("%display_name%", displayName)

            // Apply custom placeholders
            channelFormat = plugin.placeholderManager.applyCustomPlaceholders(channelFormat)

            // Handle default-message template
            channelFormat = channelFormat.replace("%default-message%", "<white>%message%</white>")

            // Apply all placeholders
            channelFormat = plugin.placeholderManager.applyAllPlaceholders(player, channelFormat)

            // Replace %message% with component placeholder
            channelFormat = channelFormat.replace("%message%", "<message>")

            try {
                return miniMessage.deserialize(
                    channelFormat,
                    TagResolver.builder()
                        .resolver(
                            Placeholder.component("message", messageComponent)
                        )
                        .build()
                )
            } catch (e: Exception) {
                plugin.logger.warning("Error formatting channel message: ${e.message}")
                if (debugFormatProcessing) {
                    plugin.logger.warning("Failed channel format: $channelFormat")
                }
                // Fall through to group/default formatting
            }
        }

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
                        "Format string: " + groupFormat.chatFormat
                    )
                } else {
                    plugin.debugLog(
                        "No group format found for player " + playerName
                    )
                    plugin.debugLog(
                        "LuckPerms hooked: " + plugin.permissionService.isLuckPermsHooked
                    )
                    plugin.debugLog(
                        "Use group format enabled: " + plugin.configManager.isUseGroupFormat
                    )
                }
            }

            // If we have a custom format for this group, use it directly
            if (groupFormat != null && groupFormat.chatFormat.isNotEmpty()) {
                // Process custom format with our placeholders system
                var customFormat = groupFormat.chatFormat

                if (debugFormatProcessing) {
                    plugin.debugLog("Original format: " + customFormat)
                    plugin.debugLog("Group name: " + groupFormat.name)
                    plugin.debugLog("Group prefix: '" + groupFormat.prefix + "'")
                    plugin.debugLog("Group name-style: '" + groupFormat.nameStyle + "'")
                }

                // First handle group-specific placeholders
                customFormat = customFormat.replace("%prefix%", groupFormat.prefix)
                customFormat = customFormat.replace("%suffix%", groupFormat.suffix)

                // Process the name-style placeholder
                var nameStyleFormatted = groupFormat.nameStyle
                nameStyleFormatted = nameStyleFormatted.replace("%player_name%", playerName)
                nameStyleFormatted = nameStyleFormatted.replace("%display_name%", displayName)
                customFormat = customFormat.replace("%name-style%", nameStyleFormatted)

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "After group placeholder processing: " + customFormat
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

                // Apply custom placeholders after group placeholders
                customFormat = plugin.placeholderManager.applyCustomPlaceholders(customFormat)

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "After custom placeholder processing: " + customFormat
                    )
                }

                // Replace %message% with component placeholder - must be done after other placeholder processing
                // but before MiniMessage deserialization
                customFormat = customFormat.replace("%message%", "<message>")

                if (debugFormatProcessing) {
                    plugin.debugLog(
                        "Final format before deserialization: " + customFormat
                    )
                }

                // Apply PAPI placeholders if available
                try {
                    if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                        customFormat = PlaceholderAPI.setPlaceholders(player, customFormat)
                    }
                } catch (e: NoClassDefFoundError) {
                    // PlaceholderAPI classes not available, skip PAPI processing
                } catch (e: Exception) {
                    // Other PlaceholderAPI error, skip PAPI processing
                    plugin.logger.warning("Error applying PlaceholderAPI placeholders: ${e.message}")
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
                if (debugFormatProcessing) {
                    plugin.debugLog("Using fallback system with group format: " + groupFormat.name)
                }
            } else {
                if (debugFormatProcessing) {
                    plugin.debugLog("Using fallback system with no group format")
                }
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

            var hoverText = plugin.configManager.getHoverTemplate(channel?.hoverTemplate ?: "player-info")
            if (hoverText.isEmpty()) {
                hoverText = plugin.configManager.getHoverTemplate("player-info")
            }

            // Apply placeholders to hover text
            hoverText = hoverText.replace("%player_name%", playerName)
            hoverText = plugin.placeholderManager.applyAllPlaceholders(player, hoverText)

            val chatFormat = plugin.configManager.chatFormat ?: "%channel_prefix% %group_prefix%%name%: %message%"
            var messageFormat: String

            if (plugin.configManager.isFormatHoverEnabled() &&
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

            // Handle default-message template first before processing other placeholders
            messageFormat = messageFormat.replace("%default-message%", "<white>%message%</white>")

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

        var hoverText = plugin.configManager.getHoverTemplate(channel?.hoverTemplate ?: "player-info")
        if (hoverText.isEmpty()) {
            hoverText = plugin.configManager.getHoverTemplate("player-info")
        }

        // Apply placeholders to hover text
        hoverText = hoverText.replace("%player_name%", playerName)
        hoverText = plugin.placeholderManager.applyAllPlaceholders(player, hoverText)

        val chatFormat = plugin.configManager.chatFormat ?: "%channel_prefix% %group_prefix%%name%: %message%"
        var messageFormat: String

        if (plugin.configManager.isFormatHoverEnabled() &&
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

        // Handle default-message template first before processing other placeholders
        messageFormat = messageFormat.replace("%default-message%", "<white>%message%</white>")

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

        if (plugin.configManager.isUrlFormattingEnabled() && plugin.config.getBoolean("url-formatting.hover", true)) {
            val hoverText: String = plugin.configManager.getUrlHoverText()
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

    fun formatSystemMessageFromPath(
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
