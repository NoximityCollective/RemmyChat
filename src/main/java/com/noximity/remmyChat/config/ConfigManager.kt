package com.noximity.remmyChat.config

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.models.GroupFormat
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.collections.HashMap
import kotlin.collections.MutableMap

class ConfigManager(private val plugin: RemmyChat) {
    private lateinit var config: FileConfiguration
    val channels: MutableMap<String, Channel> = HashMap<String, Channel>()
    val groupFormats: MutableMap<String, GroupFormat> = HashMap<String, GroupFormat>()
    private val hoverTemplates: MutableMap<String, String> = HashMap<String, String>()
    private val channelPrefixTemplates: MutableMap<String, String> = HashMap<String, String>()
    private val groupPrefixTemplates: MutableMap<String, String> = HashMap<String, String>()
    private val nameStyleTemplates: MutableMap<String, String> = HashMap<String, String>()
    var isLinkClickEnabled: Boolean = false
        private set
    var isUseGroupFormat: Boolean = false
        private set
    var isAllowSelfMessaging: Boolean = false
        private set
    var chatFormat: String? = null
        private set
    var isDebugEnabled: Boolean = false
        private set
    private var verboseStartup = false
    val symbolMappings: MutableMap<String, String> = HashMap<String, String>()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        plugin.saveDefaultConfig()
        this.config = plugin.config

        // Load debug settings first
        this.isDebugEnabled = config.getBoolean("debug.enabled", false)
        this.verboseStartup =
            !config.isSet("debug.verbose-startup") ||
                    config.getBoolean("debug.verbose-startup", true)

        loadTemplates()
        loadChannels()
        loadGroupFormats()
        loadUrlFormatting()
        loadSymbols()
        this.isUseGroupFormat = config.getBoolean(
            "features.use-group-format",
            true
        )
        this.isAllowSelfMessaging = config.getBoolean(
            "features.allow-self-messaging",
            false
        )
        this.chatFormat = config.getString(
            "chat-format",
            "%channel_prefix% %group_prefix%%name%: %message%"
        )
    }

    private fun loadTemplates() {
        // Load hover templates
        val hoversSection = config.getConfigurationSection(
            "templates.hovers"
        )
        hoversSection?.let { section ->
            for (key in section.getKeys(false)) {
                val template = section.getString(key)
                if (template != null) {
                    hoverTemplates[key] = template
                    if (verboseStartup) {
                        plugin.debugLog("Loaded hover template: " + key)
                    }
                }
            }
        }

        // Load channel prefix templates
        val channelPrefixesSection =
            config.getConfigurationSection("templates.channel-prefixes")
        channelPrefixesSection?.let { section ->
            for (key in section.getKeys(false)) {
                val template = section.getString(key)
                if (template != null) {
                    channelPrefixTemplates[key] = template
                    if (verboseStartup) {
                        plugin.debugLog(
                            "Loaded channel prefix template: " + key
                        )
                    }
                }
            }
        }

        // Load group prefix templates
        val groupPrefixesSection =
            config.getConfigurationSection("templates.group-prefixes")
        groupPrefixesSection?.let { section ->
            for (key in section.getKeys(false)) {
                val template = section.getString(key)
                if (template != null) {
                    groupPrefixTemplates[key] = template
                    if (verboseStartup) {
                        plugin.debugLog("Loaded group prefix template: " + key)
                    }
                }
            }
        }

        // Load name style templates
        val nameStylesSection = config.getConfigurationSection(
            "templates.name-styles"
        )
        nameStylesSection?.let { section ->
            for (key in section.getKeys(false)) {
                val template = section.getString(key)
                if (template != null) {
                    nameStyleTemplates[key] = template
                    if (verboseStartup) {
                        plugin.debugLog("Loaded name style template: " + key)
                    }
                }
            }
        }
    }

    private fun loadChannels() {
        val channelsSection = config.getConfigurationSection(
            "channels"
        )
        if (channelsSection == null) {
            plugin.logger.warning("No channels configured!")
            return
        }

        for (key in channelsSection.getKeys(false)) {
            val permission = channelsSection.getString(
                key + ".permission",
                ""
            ) ?: ""
            val radius = channelsSection.getDouble(key + ".radius", -1.0)
            val prefix = channelsSection.getString(key + ".prefix", "") ?: ""
            val hover = channelsSection.getString(
                key + ".hover",
                "player-info"
            ) ?: "player-info"
            val displayName = channelsSection.getString(
                key + ".display-name",
                ""
            ) ?: ""

            val channel = Channel(
                key,
                permission,
                radius,
                prefix,
                hover,
                displayName
            )
            channels[key] = channel

            if (verboseStartup) {
                plugin.logger.info(
                    "Loaded channel: " +
                            key +
                            (if (displayName.isEmpty())
                                ""
                            else
                                " with display name: " + displayName)
                )
            }
        }
    }

    private fun loadGroupFormats() {
        val groupsSection = config.getConfigurationSection(
            "groups"
        )
        if (groupsSection == null) {
            plugin.logger.info(
                "No group formats configured, using default name styles only."
            )
            return
        }

        for (key in groupsSection.getKeys(false)) {
            val nameStyle = groupsSection.getString(
                key + ".name-style",
                "default"
            ) ?: "default"
            val prefix = groupsSection.getString(key + ".prefix", "") ?: ""
            val format = groupsSection.getString(key + ".format", "") ?: ""

            // Debug information
            if (this.isDebugEnabled || verboseStartup) {
                plugin.debugLog("Loading group format for " + key + ":")
                plugin.debugLog("  - name-style: " + nameStyle)
                plugin.debugLog("  - prefix: '" + prefix + "'")
                plugin.debugLog("  - format: '" + format + "'")
            }

            val groupFormat = GroupFormat(
                key,
                nameStyle,
                prefix,
                format
            )
            groupFormats[key] = groupFormat

            if (verboseStartup) {
                plugin.debugLog("Loaded group format: " + key)
            }
        }
    }

    private fun loadUrlFormatting() {
        this.isLinkClickEnabled = config.getBoolean(
            "url-formatting.enabled",
            true
        )
    }

    private fun loadSymbols() {
        symbolMappings.clear()
        val symbolsFile = File(plugin.dataFolder, "symbols.yml")
        if (!symbolsFile.exists()) {
            plugin.saveResource("symbols.yml", false)
        }
        val symbolsConfig: FileConfiguration = YamlConfiguration.loadConfiguration(
            symbolsFile
        )
        symbolsConfig.getConfigurationSection("symbols")?.let { section ->
            for (key in section.getKeys(false)) {
                val value = section.getString(key)
                if (value != null) {
                    symbolMappings[key] = value
                }
            }
        }
    }

    fun reloadConfig() {
        plugin.reloadConfig()
        this.config = plugin.config

        // Reload debug settings first
        this.isDebugEnabled = config.getBoolean("debug.enabled", false)
        this.verboseStartup =
            !config.isSet("debug.verbose-startup") ||
                    config.getBoolean("debug.verbose-startup", true)

        channels.clear()
        groupFormats.clear()
        hoverTemplates.clear()
        channelPrefixTemplates.clear()
        groupPrefixTemplates.clear()
        nameStyleTemplates.clear()
        symbolMappings.clear()

        loadTemplates()
        loadChannels()
        loadGroupFormats()
        loadUrlFormatting()
        loadSymbols()
        this.isUseGroupFormat = config.getBoolean(
            "features.use-group-format",
            true
        )
        this.isAllowSelfMessaging = config.getBoolean(
            "features.allow-self-messaging",
            false
        )
        this.chatFormat = config.getString(
            "chat-format",
            "%channel_prefix% %group_prefix%%name%: %message%"
        )

        // Reload placeholders
        plugin.placeholderManager.loadCustomPlaceholders()
    }

    val isPlayerFormattingAllowed: Boolean
        get() = config.getBoolean("features.player-formatting", false)

    fun getChannel(name: String): Channel? {
        return channels[name]
    }

    fun getGroupFormat(name: String): GroupFormat? {
        return groupFormats[name]
    }

    fun getHoverTemplate(name: String): String {
        return hoverTemplates.getOrDefault(name, "")
    }

    fun getChannelPrefixTemplate(name: String): String {
        return channelPrefixTemplates.getOrDefault(name, "")
    }

    fun getGroupPrefixTemplate(name: String): String {
        return groupPrefixTemplates.getOrDefault(name, "")
    }

    fun getNameStyleTemplate(name: String): String {
        return nameStyleTemplates.getOrDefault(
            name,
            nameStyleTemplates.getOrDefault("default", "<#4A90E2>%player_name%")
        )
    }

    val defaultChannel: Channel?
        get() {
            val defaultChannelName = config.getString("default-channel")
            return if (defaultChannelName != null) channels[defaultChannelName] else null
        }



    val isFormatHoverEnabled: Boolean
        get() = config.getBoolean("features.format-hover", true)

    val cooldown: Int
        get() = config.getInt("chat-cooldown", 0)

    val isParsePlaceholdersInMessages: Boolean
        get() = config.getBoolean(
            "features.parse-placeholders-in-messages",
            false
        )

    val isParsePapiPlaceholdersInMessages: Boolean
        get() = config.getBoolean(
            "features.parse-papi-placeholders-in-messages",
            false
        )

    val deleteButtonText: String
        get() = config.getString("delete-button.text", "<red>❌</red>") ?: "<red>❌</red>"

    val deleteButtonHover: String
        get() = config.getString(
            "delete-button.hover",
            "<gray>Delete this message</gray>"
        ) ?: "<gray>Delete this message</gray>"

    val deleteButtonClickMessage: String
        get() = config.getString(
            "delete-button.click-message",
            "<green>Message deleted!</green>"
        ) ?: "<green>Message deleted!</green>"

    val deleteButtonSound: String
        get() = config.getString("delete-button.sound", "") ?: ""
}
