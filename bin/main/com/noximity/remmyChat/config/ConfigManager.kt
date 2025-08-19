package com.noximity.remmyChat.config

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import com.noximity.remmyChat.models.GroupFormat
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConfigManager(private val plugin: RemmyChat) {

    // Configuration files
    private lateinit var mainConfig: FileConfiguration
    private lateinit var channelsConfig: FileConfiguration
    private lateinit var groupsConfig: FileConfiguration
    private lateinit var templatesConfig: FileConfiguration
    private lateinit var databaseConfig: FileConfiguration
    private lateinit var discordConfig: FileConfiguration

    // Configuration file objects
    private lateinit var mainConfigFile: File
    private lateinit var channelsConfigFile: File
    private lateinit var groupsConfigFile: File
    private lateinit var templatesConfigFile: File
    private lateinit var databaseConfigFile: File
    private lateinit var discordConfigFile: File

    // Cached data
    val channels = ConcurrentHashMap<String, Channel>()
    val groups = ConcurrentHashMap<String, GroupFormat>()
    val templates = ConcurrentHashMap<String, String>()

    // Configuration properties
    var defaultChannel: Channel? = null
        private set

    var chatCooldown: Int = 3
        private set

    var debugEnabled: Boolean = false
        private set

    var verboseStartup: Boolean = false
        private set



    // Server identification
    var serverName: String = ""
        private set

    var serverDisplayName: String = ""
        private set

    // Chat configuration properties
    var cooldown: Int = 3
        private set

    var isAllowSelfMessaging: Boolean = false
        private set

    var isUseGroupFormat: Boolean = true
        private set

    var chatFormat: String? = null
        private set

    var isParsePlaceholdersInMessages: Boolean = true
        private set

    var isParsePapiPlaceholdersInMessages: Boolean = true
        private set

    var symbolMappings: Map<String, String> = emptyMap()
        private set

    var isLinkClickEnabled: Boolean = true
        private set

    var isPlayerFormattingAllowed: Boolean = false
        private set

    // Template caches
    private val hoverTemplates = mutableMapOf<String, String>()
    private val channelPrefixTemplates = mutableMapOf<String, String>()
    private val groupPrefixTemplates = mutableMapOf<String, String>()
    private val nameStyleTemplates = mutableMapOf<String, String>()

    fun initialize() {
        plugin.debugLog("Initializing ConfigManager...")

        // Create plugin data folder if it doesn't exist
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        try {
            // Initialize configuration files
            initializeConfigFiles()

            // Load all configurations
            loadAllConfigs()

            // Process configurations
            processConfigurations()

            plugin.logger.info("Configuration loaded successfully!")

        } catch (e: Exception) {
            plugin.logger.severe("Failed to initialize configuration: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initializeConfigFiles() {
        // Main config (config.yml)
        mainConfigFile = File(plugin.dataFolder, "config.yml")
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false)
        }

        // Channels config
        channelsConfigFile = File(plugin.dataFolder, "channels.yml")
        if (!channelsConfigFile.exists()) {
            plugin.saveResource("channels.yml", false)
        }

        // Groups config
        groupsConfigFile = File(plugin.dataFolder, "groups.yml")
        if (!groupsConfigFile.exists()) {
            plugin.saveResource("groups.yml", false)
        }

        // Templates config
        templatesConfigFile = File(plugin.dataFolder, "templates.yml")
        if (!templatesConfigFile.exists()) {
            plugin.saveResource("templates.yml", false)
        }



        // Database config
        databaseConfigFile = File(plugin.dataFolder, "database.yml")
        if (!databaseConfigFile.exists()) {
            plugin.saveResource("database.yml", false)
        }

        // Discord config
        discordConfigFile = File(plugin.dataFolder, "discord.yml")
        if (!discordConfigFile.exists()) {
            plugin.saveResource("discord.yml", false)
        }
    }

    private fun loadAllConfigs() {
        plugin.debugLog("Loading configuration files...")

        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile)
        channelsConfig = YamlConfiguration.loadConfiguration(channelsConfigFile)
        groupsConfig = YamlConfiguration.loadConfiguration(groupsConfigFile)
        templatesConfig = YamlConfiguration.loadConfiguration(templatesConfigFile)

        databaseConfig = YamlConfiguration.loadConfiguration(databaseConfigFile)
        discordConfig = YamlConfiguration.loadConfiguration(discordConfigFile)

        plugin.debugLog("All configuration files loaded")
    }

    private fun processConfigurations() {
        plugin.debugLog("Processing configurations...")

        // Load main config settings
        loadMainConfigSettings()

        // Load templates first (needed for other configs)
        loadTemplates()

        // Load channels
        loadChannels()

        // Load groups
        loadGroups()

        // Load server identification
        loadServerSettings()

        plugin.debugLog("Configuration processing completed")
    }

    private fun loadMainConfigSettings() {
        chatCooldown = mainConfig.getInt("chat-cooldown", 3)
        cooldown = chatCooldown
        debugEnabled = mainConfig.getBoolean("debug.enabled", false)
        verboseStartup = mainConfig.getBoolean("debug.verbose-startup", false)


        isAllowSelfMessaging = mainConfig.getBoolean("allow-self-messaging", false)
        isUseGroupFormat = mainConfig.getBoolean("features.use-group-format", true)
        chatFormat = mainConfig.getString("chat-format", "%channel_prefix% %group_prefix%%name%: %message%")
        isParsePlaceholdersInMessages = mainConfig.getBoolean("features.parse-placeholders-in-messages", true)
        isParsePapiPlaceholdersInMessages = mainConfig.getBoolean("features.parse-papi-placeholders-in-messages", true)
        isLinkClickEnabled = mainConfig.getBoolean("features.clickable-links", true)
        isPlayerFormattingAllowed = mainConfig.getBoolean("features.player-formatting", false)

        // Load symbol mappings
        val symbolSection = mainConfig.getConfigurationSection("symbol-mappings")
        val mutableSymbols = mutableMapOf<String, String>()
        symbolSection?.getKeys(false)?.forEach { key ->
            val value = symbolSection.getString(key)
            if (!value.isNullOrEmpty()) {
                mutableSymbols[key] = value
            }
        }
        symbolMappings = mutableSymbols

        plugin.debugLog("Main config settings loaded: cooldown=$chatCooldown, debug=$debugEnabled")
    }

    private fun loadTemplates() {
        templates.clear()
        hoverTemplates.clear()
        channelPrefixTemplates.clear()
        groupPrefixTemplates.clear()
        nameStyleTemplates.clear()

        // Load custom placeholders
        val templatesSection = templatesConfig.getConfigurationSection("placeholders")
        templatesSection?.getKeys(false)?.forEach { key ->
            val value = templatesSection.getString(key) ?: ""
            templates[key] = value
            plugin.debugLog("Loaded template: $key = $value")
        }

        // Load hover templates
        val hoverSection = templatesConfig.getConfigurationSection("hover-templates")
        hoverSection?.getKeys(false)?.forEach { key ->
            val value = hoverSection.getString(key) ?: ""
            hoverTemplates[key] = value
            templates["hover-$key"] = value
            plugin.debugLog("Loaded hover template: hover-$key")
        }

        // Load channel prefix templates
        val channelPrefixSection = templatesConfig.getConfigurationSection("channel-prefix-templates")
        channelPrefixSection?.getKeys(false)?.forEach { key ->
            val value = channelPrefixSection.getString(key) ?: ""
            channelPrefixTemplates[key] = value
            plugin.debugLog("Loaded channel prefix template: $key")
        }

        // Load group prefix templates
        val groupPrefixSection = templatesConfig.getConfigurationSection("group-prefix-templates")
        groupPrefixSection?.getKeys(false)?.forEach { key ->
            val value = groupPrefixSection.getString(key) ?: ""
            groupPrefixTemplates[key] = value
            plugin.debugLog("Loaded group prefix template: $key")
        }

        // Load name style templates
        val nameStyleSection = templatesConfig.getConfigurationSection("name-style-templates")
        nameStyleSection?.getKeys(false)?.forEach { key ->
            val value = nameStyleSection.getString(key) ?: ""
            nameStyleTemplates[key] = value
            plugin.debugLog("Loaded name style template: $key")
        }

        // Load message format templates
        val messageFormats = templatesConfig.getConfigurationSection("message-formats")
        messageFormats?.getKeys(false)?.forEach { key ->
            val value = messageFormats.getString(key) ?: ""
            templates["format-$key"] = value
            plugin.debugLog("Loaded format template: format-$key")
        }

        plugin.debugLog("Loaded ${templates.size} templates")
    }

    private fun loadChannels() {
        channels.clear()

        val channelsSection = channelsConfig.getConfigurationSection("channels")
        channelsSection?.getKeys(false)?.forEach { channelName ->
            val channelSection = channelsSection.getConfigurationSection(channelName)
            if (channelSection != null) {
                try {
                    val channel = Channel(
                        name = channelName,
                        permission = channelSection.getString("permission", "") ?: "",
                        radius = channelSection.getInt("radius", -1),
                        prefix = channelSection.getString("prefix", "") ?: "",
                        displayName = channelSection.getString("display-name", "") ?: "",
                        description = channelSection.getString("description", "") ?: "",
                        format = channelSection.getString("format", "%player_display_name% <dark_gray>»</dark_gray> %message%") ?: "%player_display_name% <dark_gray>»</dark_gray> %message%",
                        hoverTemplate = channelSection.getString("hover-template", "player-info") ?: "player-info",
                        crossServer = false,
                        localOnly = channelSection.getBoolean("local-only", false),
                        enabled = channelSection.getBoolean("enabled", true)
                    )

                    // Load moderation settings
                    val moderationSection = channelSection.getConfigurationSection("moderation")
                    if (moderationSection != null) {
                        channel.rateLimit = moderationSection.getInt("rate-limit", 30)
                        channel.cooldown = moderationSection.getInt("cooldown", 3)
                        channel.maxLength = moderationSection.getInt("max-length", 256)
                        channel.spamProtection = moderationSection.getBoolean("spam-protection", true)
                    }

                    // Load features
                    val featuresSection = channelSection.getConfigurationSection("features")
                    if (featuresSection != null) {
                        channel.urlDetection = featuresSection.getBoolean("url-detection", true)
                        channel.mentionSystem = featuresSection.getBoolean("mention-system", true)
                        channel.emojiSupport = featuresSection.getBoolean("emoji-support", true)
                        channel.placeholderParsing = featuresSection.getBoolean("placeholder-parsing", true)
                    }

                    channels[channelName] = channel
                    plugin.debugLog("Loaded channel: $channelName (${if (channel.enabled) "enabled" else "disabled"})")

                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load channel '$channelName': ${e.message}")
                }
            }
        }

        // Set default channel
        val defaultChannelName = mainConfig.getString("default-channel", "global") ?: "global"
        defaultChannel = channels[defaultChannelName]

        if (defaultChannel == null) {
            plugin.logger.warning("Default channel '$defaultChannelName' not found! Using first available channel.")
            defaultChannel = channels.values.firstOrNull()
        }

        plugin.debugLog("Loaded ${channels.size} channels, default: ${defaultChannel?.name}")
    }

    private fun loadGroups() {
        groups.clear()

        val groupsSection = groupsConfig.getConfigurationSection("groups")
        groupsSection?.getKeys(false)?.forEach { groupName ->
            val groupSection = groupsSection.getConfigurationSection(groupName)
            if (groupSection != null) {
                try {
                    val group = GroupFormat(
                        name = groupName,
                        priority = groupSection.getInt("priority", 100),
                        displayName = groupSection.getString("display-name", groupName) ?: groupName,
                        description = groupSection.getString("description", "") ?: "",
                        permissions = groupSection.getStringList("permissions"),
                        prefix = groupSection.getString("formatting.prefix", "") ?: "",
                        suffix = groupSection.getString("formatting.suffix", "") ?: "",
                        nameStyle = groupSection.getString("formatting.name-style", "%player_name%") ?: "%player_name%",
                        chatFormat = groupSection.getString("formatting.chat-format", "%name-style% <dark_gray>»</dark_gray> %message%") ?: "%name-style% <dark_gray>»</dark_gray> %message%",
                        hoverTemplate = groupSection.getString("interactions.hover-template", "player-info") ?: "player-info",
                        clickAction = groupSection.getString("interactions.click-action", "suggest") ?: "suggest",
                        clickCommand = groupSection.getString("interactions.click-command", "/msg %player_name% ") ?: "/msg %player_name% "
                    )

                    // Load features
                    val featuresSection = groupSection.getConfigurationSection("features")
                    if (featuresSection != null) {
                        group.bypassCooldown = featuresSection.getBoolean("bypass-cooldown", false)
                        group.bypassFilters = featuresSection.getBoolean("bypass-filters", false)
                        group.useColors = featuresSection.getBoolean("use-colors", false)
                        group.useFormatting = featuresSection.getBoolean("use-formatting", false)
                        group.mentionEveryone = featuresSection.getBoolean("mention-everyone", false)
                        group.priorityMessages = featuresSection.getBoolean("priority-messages", false)
                        group.socialSpyAccess = featuresSection.getBoolean("social-spy-access", false)
                        group.moderateChat = featuresSection.getBoolean("moderate-chat", false)
                        group.staffChannels = featuresSection.getBoolean("staff-channels", false)
                    }

                    groups[groupName] = group
                    plugin.debugLog("Loaded group: $groupName (priority: ${group.priority})")

                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load group '$groupName': ${e.message}")
                }
            }
        }

        plugin.debugLog("Loaded ${groups.size} groups")
    }

    private fun loadServerSettings() {
        serverName = "local"
        serverDisplayName = "Local Server"
    }

    private fun detectServerName(): String {
        // Try to detect server name from system properties or environment
        val systemName = System.getProperty("remmychat.server.name")
        if (!systemName.isNullOrEmpty()) {
            return systemName
        }

        val envName = System.getenv("REMMYCHAT_SERVER_NAME")
        if (!envName.isNullOrEmpty()) {
            return envName
        }

        // Fallback to a default name
        return "server-${System.currentTimeMillis() % 10000}"
    }

    fun reload(): Boolean {
        return try {
            plugin.debugLog("Reloading configuration...")

            // Reload all config files
            mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile)
            channelsConfig = YamlConfiguration.loadConfiguration(channelsConfigFile)
            groupsConfig = YamlConfiguration.loadConfiguration(groupsConfigFile)
            templatesConfig = YamlConfiguration.loadConfiguration(templatesConfigFile)
            databaseConfig = YamlConfiguration.loadConfiguration(databaseConfigFile)
            discordConfig = YamlConfiguration.loadConfiguration(discordConfigFile)

            // Reprocess configurations
            processConfigurations()

            plugin.logger.info("Configuration reloaded successfully!")
            true

        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload configuration: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Getter methods for configuration access
    fun getChannel(name: String): Channel? = channels[name]

    fun getGroup(name: String): GroupFormat? = groups[name]

    fun getTemplate(name: String): String? = templates[name]

    fun getMainConfig(): FileConfiguration = mainConfig

    fun getChannelsConfig(): FileConfiguration = channelsConfig

    fun getGroupsConfig(): FileConfiguration = groupsConfig

    fun getTemplatesConfig(): FileConfiguration = templatesConfig



    fun getDatabaseConfig(): FileConfiguration = databaseConfig

    fun getDiscordConfig(): FileConfiguration = discordConfig

    // Feature flags from main config
    fun isFormatHoverEnabled(): Boolean = mainConfig.getBoolean("features.format-hover", true)

    fun isClickableLinksEnabled(): Boolean = mainConfig.getBoolean("features.clickable-links", true)

    fun isPlayerFormattingEnabled(): Boolean = mainConfig.getBoolean("features.player-formatting", false)

    fun isUseGroupFormatEnabled(): Boolean = mainConfig.getBoolean("features.use-group-format", true)

    // Template getters
    fun getHoverTemplate(name: String): String = hoverTemplates[name] ?: ""

    fun getChannelPrefixTemplate(name: String): String = channelPrefixTemplates[name] ?: ""

    fun getGroupPrefixTemplate(name: String): String = groupPrefixTemplates[name] ?: ""

    fun getNameStyleTemplate(name: String): String = nameStyleTemplates[name] ?: ""

    fun isAllowSelfMessagingEnabled(): Boolean = mainConfig.getBoolean("features.allow-self-messaging", false)

    // Group format methods
    fun getGroupFormat(name: String): GroupFormat? = groups[name]

    val groupFormats: Map<String, GroupFormat>
        get() = groups

    fun isParsePlaceholdersInMessagesEnabled(): Boolean = mainConfig.getBoolean("features.parse-placeholders-in-messages", false)

    fun isParsePapiPlaceholdersInMessagesEnabled(): Boolean = mainConfig.getBoolean("features.parse-papi-placeholders-in-messages", false)

    // URL formatting settings
    fun isUrlFormattingEnabled(): Boolean = mainConfig.getBoolean("url-formatting.enabled", true)

    fun getUrlColor(): String = mainConfig.getString("url-formatting.color", "#3498DB") ?: "#3498DB"

    fun isUrlUnderlineEnabled(): Boolean = mainConfig.getBoolean("url-formatting.underline", true)

    fun getUrlHoverText(): String = mainConfig.getString("url-formatting.hover-text", "<#AAAAAA>Click to open") ?: "<#AAAAAA>Click to open"

    // Performance settings
    fun getMessageQueueSize(): Int = mainConfig.getInt("performance.message-queue-size", 1000)

    fun isBatchProcessingEnabled(): Boolean = mainConfig.getBoolean("performance.batch-processing", true)

    fun getBatchSize(): Int = mainConfig.getInt("performance.batch-size", 50)

    fun getPlayerCacheSize(): Int = mainConfig.getInt("performance.player-cache-size", 500)

    fun getChannelCacheSize(): Int = mainConfig.getInt("performance.channel-cache-size", 100)

    fun getCacheCleanupInterval(): Int = mainConfig.getInt("performance.cache-cleanup-interval", 300)

    fun isAsyncProcessingEnabled(): Boolean = mainConfig.getBoolean("performance.async-processing", true)

    fun getThreadPoolSize(): Int = mainConfig.getInt("performance.thread-pool-size", 4)



    // Database settings
    fun getDatabaseType(): String = databaseConfig.getString("primary.type", "mysql") ?: "mysql"

    fun getDatabaseHost(): String = databaseConfig.getString("primary.connection.host", "localhost") ?: "localhost"

    fun getDatabasePort(): Int = databaseConfig.getInt("primary.connection.port", 3306)

    fun getDatabaseName(): String = databaseConfig.getString("primary.connection.database", "remmychat") ?: "remmychat"

    fun getDatabaseUsername(): String = databaseConfig.getString("primary.connection.username", "remmychat") ?: "remmychat"

    fun getDatabasePassword(): String = databaseConfig.getString("primary.connection.password", "") ?: ""

    fun getDatabaseTablePrefix(): String = databaseConfig.getString("primary.tables.prefix", "rc_") ?: "rc_"

    // Discord settings
    fun isDiscordEnabled(): Boolean = discordConfig.getBoolean("integration.enabled", false)

    fun getDiscordMode(): String = discordConfig.getString("integration.mode", "bidirectional") ?: "bidirectional"

    // Component-specific reload methods
    fun reloadChannels(): Boolean {
        return try {
            plugin.debugLog("Reloading channels configuration...")
            loadChannels()
            // Also reload the ChannelManager to pick up changes
            plugin.channelManager.reload()
            plugin.debugLog("Channels reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload channels: ${e.message}")
            false
        }
    }

    fun reloadGroups(): Boolean {
        return try {
            plugin.debugLog("Reloading groups configuration...")
            loadGroups()
            // Also reload the GroupManager to pick up changes
            plugin.groupManager.reload()
            plugin.debugLog("Groups reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload groups: ${e.message}")
            false
        }
    }

    fun reloadTemplates(): Boolean {
        return try {
            plugin.debugLog("Reloading templates configuration...")
            loadTemplates()
            // Also reload the TemplateManager to pick up changes
            plugin.templateManager.reload()
            plugin.debugLog("Templates reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload templates: ${e.message}")
            false
        }
    }

    fun reloadSymbols(): Boolean {
        return try {
            plugin.debugLog("Reloading symbols configuration...")
            // Reload the main config to get fresh symbol data
            mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile)
            // Reload symbols from the fresh config
            symbolMappings = mainConfig.getConfigurationSection("symbols")?.let { section ->
                section.getKeys(false).associate { key ->
                    key to (section.getString(key) ?: "")
                }
            } ?: emptyMap()
            plugin.debugLog("Symbols reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload symbols: ${e.message}")
            false
        }
    }

    fun reloadMainConfigOnly(): Boolean {
        return try {
            plugin.debugLog("Reloading main configuration file...")
            mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile)
            loadMainConfigSettings()
            plugin.debugLog("Main configuration reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload main configuration: ${e.message}")
            false
        }
    }

    fun reloadDatabaseConfig(): Boolean {
        return try {
            plugin.debugLog("Reloading database configuration...")
            databaseConfig = YamlConfiguration.loadConfiguration(databaseConfigFile)
            plugin.debugLog("Database configuration reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload database configuration: ${e.message}")
            false
        }
    }

    fun reloadDiscordConfig(): Boolean {
        return try {
            plugin.debugLog("Reloading Discord configuration...")
            discordConfig = YamlConfiguration.loadConfiguration(discordConfigFile)
            plugin.debugLog("Discord configuration reloaded successfully")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload Discord configuration: ${e.message}")
            false
        }
    }
}
