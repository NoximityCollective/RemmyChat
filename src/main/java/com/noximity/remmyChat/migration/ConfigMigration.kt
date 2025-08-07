package com.noximity.remmyChat.migration

import com.noximity.remmyChat.RemmyChat
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConfigMigration(private val plugin: RemmyChat) {

    private val dataFolder = plugin.dataFolder
    private val backupFolder = File(dataFolder, "migration-backups")

    fun migrateToOrganizedStructure(): Boolean {
        plugin.logger.info("Starting configuration migration to organized structure...")

        try {
            // Check if migration is needed
            if (!needsMigration()) {
                plugin.logger.info("Configuration is already in organized format. No migration needed.")
                return true
            }

            // Create backup
            createBackup()

            // Migrate configurations
            migrateMainConfig()
            migrateChannelsConfig()
            migrateGroupsConfig()
            migrateTemplatesConfig()
            migrateCrossServerConfig()
            migrateDatabaseConfig()
            migrateDiscordConfig()
            migrateMessagesConfig()
            migrateSymbolsConfig()

            // Mark migration as complete
            createMigrationMarker()

            plugin.logger.info("Configuration migration completed successfully!")
            plugin.logger.info("Old configuration files have been backed up to: ${backupFolder.path}")

            return true

        } catch (e: Exception) {
            plugin.logger.severe("Configuration migration failed: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Fix existing configurations that may have problematic placeholder references
     */
    fun fixPlaceholderIssues(): Boolean {
        plugin.logger.info("Checking for placeholder configuration issues...")

        try {
            var fixesApplied = false

            // Fix main config chat-format
            val configFile = File(dataFolder, "config.yml")
            if (configFile.exists()) {
                val config = YamlConfiguration.loadConfiguration(configFile)
                var chatFormat = config.getString("chat-format")

                if (chatFormat != null && chatFormat.contains("%default-message%")) {
                    chatFormat = chatFormat.replace("%default-message%", "%message%")
                    config.set("chat-format", chatFormat)
                    config.save(configFile)
                    plugin.logger.info("Fixed chat-format in config.yml: replaced %default-message% with %message%")
                    fixesApplied = true
                }
            }

            // Fix group formats that use %default-message%
            val groupsFile = File(dataFolder, "groups.yml")
            if (groupsFile.exists()) {
                val groupsConfig = YamlConfiguration.loadConfiguration(groupsFile)
                val groupsSection = groupsConfig.getConfigurationSection("groups")

                if (groupsSection != null) {
                    for (groupName in groupsSection.getKeys(false)) {
                        val groupSection = groupsSection.getConfigurationSection(groupName)
                        val formattingSection = groupSection?.getConfigurationSection("formatting")

                        if (formattingSection != null) {
                            var chatFormat = formattingSection.getString("chat-format")
                            if (chatFormat != null && chatFormat.contains("%default-message%")) {
                                chatFormat = chatFormat.replace("%default-message%", "%message%")
                                formattingSection.set("chat-format", chatFormat)
                                plugin.logger.info("Fixed chat-format for group '$groupName': replaced %default-message% with %message%")
                                fixesApplied = true
                            }
                        }
                    }

                    if (fixesApplied) {
                        groupsConfig.save(groupsFile)
                    }
                }
            }

            if (fixesApplied) {
                plugin.logger.info("Placeholder configuration fixes applied successfully!")
            } else {
                plugin.logger.info("No placeholder issues found.")
            }

            return true

        } catch (e: Exception) {
            plugin.logger.severe("Failed to fix placeholder issues: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun needsMigration(): Boolean {
        val migrationMarker = File(dataFolder, ".migrated-to-organized")

        // If marker exists, no migration needed
        if (migrationMarker.exists()) {
            return false
        }

        // If organized config files already exist, assume they're correct
        val organizedFiles = listOf(
            "config.yml", "channels.yml", "groups.yml", "templates.yml",
            "cross-server.yml", "database.yml", "discord.yml"
        )

        val hasOrganizedConfig = organizedFiles.all { File(dataFolder, it).exists() }
        if (hasOrganizedConfig) {
            // Check if these are organized format by looking for specific keys
            val configFile = File(dataFolder, "config.yml")
            if (configFile.exists()) {
                val config = YamlConfiguration.loadConfiguration(configFile)
                // Check for organized format indicators
                if (config.contains("config-files") || config.contains("performance")) {
                    createMigrationMarker()
                    return false
                }
            }
        }

        // Check if old format config exists
        val oldConfigFile = File(dataFolder, "config.yml")
        return oldConfigFile.exists()
    }

    private fun createBackup() {
        if (!backupFolder.exists()) {
            backupFolder.mkdirs()
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val specificBackupFolder = File(backupFolder, "backup_$timestamp")
        specificBackupFolder.mkdirs()

        // Backup all existing config files
        val filesToBackup = listOf("config.yml", "messages.yml", "symbols.yml", "channels.yml", "groups.yml")

        filesToBackup.forEach { fileName ->
            val originalFile = File(dataFolder, fileName)
            if (originalFile.exists()) {
                val backupFile = File(specificBackupFolder, fileName)
                originalFile.copyTo(backupFile, overwrite = true)
                plugin.logger.info("Backed up: $fileName")
            }
        }
    }

    private fun migrateMainConfig() {
        val oldConfigFile = File(dataFolder, "config.yml")
        if (!oldConfigFile.exists()) return

        val oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile)
        val newConfig = YamlConfiguration()

        // Copy basic settings
        newConfig.set("default-channel", oldConfig.getString("default-channel", "global"))
        newConfig.set("chat-cooldown", oldConfig.getInt("chat-cooldown", 3))

        // Migrate debug settings
        val debugSection = newConfig.createSection("debug")
        val oldDebugSection = oldConfig.getConfigurationSection("debug")
        if (oldDebugSection != null) {
            debugSection.set("enabled", oldDebugSection.getBoolean("enabled", false))
            debugSection.set("verbose-startup", oldDebugSection.getBoolean("verbose-startup", false))
            debugSection.set("format-processing", oldDebugSection.getBoolean("format-processing", false))
            debugSection.set("cross-server", false)
        } else {
            debugSection.set("enabled", false)
            debugSection.set("verbose-startup", false)
            debugSection.set("format-processing", false)
            debugSection.set("cross-server", false)
        }

        // Migrate features
        val featuresSection = newConfig.createSection("features")
        val oldFeaturesSection = oldConfig.getConfigurationSection("features")
        if (oldFeaturesSection != null) {
            featuresSection.set("format-hover", oldFeaturesSection.getBoolean("format-hover", true))
            featuresSection.set("clickable-links", oldFeaturesSection.getBoolean("clickable-links", true))
            featuresSection.set("player-formatting", oldFeaturesSection.getBoolean("player-formatting", false))
            featuresSection.set("use-group-format", oldFeaturesSection.getBoolean("use-group-format", true))
            featuresSection.set("allow-self-messaging", oldFeaturesSection.getBoolean("allow-self-messaging", false))
        } else {
            featuresSection.set("format-hover", true)
            featuresSection.set("clickable-links", true)
            featuresSection.set("player-formatting", false)
            featuresSection.set("use-group-format", true)
            featuresSection.set("allow-self-messaging", false)
        }
        featuresSection.set("parse-placeholders-in-messages", false)
        featuresSection.set("parse-papi-placeholders-in-messages", false)

        // Migrate URL formatting
        val urlSection = newConfig.createSection("url-formatting")
        val oldUrlSection = oldConfig.getConfigurationSection("url-formatting")
        if (oldUrlSection != null) {
            urlSection.set("enabled", oldUrlSection.getBoolean("enabled", true))
            urlSection.set("color", oldUrlSection.getString("color", "#3498DB"))
            urlSection.set("underline", oldUrlSection.getBoolean("underline", true))
            urlSection.set("hover-text", oldUrlSection.getString("hover-text", "<#AAAAAA>Click to open"))
        } else {
            urlSection.set("enabled", true)
            urlSection.set("color", "#3498DB")
            urlSection.set("underline", true)
            urlSection.set("hover-text", "<#AAAAAA>Click to open")
        }

        // Add new organized structure
        val configFilesSection = newConfig.createSection("config-files")
        configFilesSection.set("channels", "channels.yml")
        configFilesSection.set("groups", "groups.yml")
        configFilesSection.set("templates", "templates.yml")
        configFilesSection.set("cross-server", "cross-server.yml")
        configFilesSection.set("database", "database.yml")
        configFilesSection.set("discord", "discord.yml")

        // Add performance settings
        val performanceSection = newConfig.createSection("performance")
        performanceSection.set("message-queue-size", 1000)
        performanceSection.set("batch-processing", true)
        performanceSection.set("batch-size", 50)
        performanceSection.set("player-cache-size", 500)
        performanceSection.set("channel-cache-size", 100)
        performanceSection.set("cache-cleanup-interval", 300)
        performanceSection.set("async-processing", true)
        performanceSection.set("thread-pool-size", 4)

        // Add integrations
        val integrationsSection = newConfig.createSection("integrations")
        integrationsSection.set("placeholder-api", true)
        integrationsSection.set("discord-srv", true)
        integrationsSection.set("vault", true)
        integrationsSection.set("luckperms", true)

        // Add security settings
        val securitySection = newConfig.createSection("security")
        val rateLimitSection = securitySection.createSection("rate-limit")
        rateLimitSection.set("enabled", true)
        rateLimitSection.set("messages-per-minute", 30)
        rateLimitSection.set("burst-limit", 5)

        val spamSection = securitySection.createSection("spam-protection")
        spamSection.set("enabled", true)
        spamSection.set("duplicate-threshold", 3)
        spamSection.set("similarity-threshold", 0.8)

        val contentFilterSection = securitySection.createSection("content-filter")
        contentFilterSection.set("enabled", false)
        contentFilterSection.set("blocked-words", listOf<String>())
        contentFilterSection.set("replacement-char", "*")

        // Add maintenance
        val maintenanceSection = newConfig.createSection("maintenance")
        maintenanceSection.set("auto-save-interval", 300)
        maintenanceSection.set("cleanup-old-data", true)
        maintenanceSection.set("data-retention-days", 30)
        maintenanceSection.set("backup-configs", true)

        // Add legacy systems (disabled)
        val legacySection = newConfig.createSection("legacy")
        val bungeeSection = legacySection.createSection("bungeecord-messaging")
        bungeeSection.set("enabled", false)
        bungeeSection.set("note", "Legacy BungeeCord messaging disabled. Use modern cross-server system.")

        // Migrate chat format and fix %default-message% issue
        var chatFormat = oldConfig.getString("chat-format")
        if (chatFormat != null) {
            // Fix problematic %default-message% placeholder
            if (chatFormat.contains("%default-message%")) {
                chatFormat = chatFormat.replace("%default-message%", "%message%")
                plugin.logger.info("Fixed chat-format: replaced %default-message% with %message%")
            }
        } else {
            // Set default format that doesn't use problematic placeholders
            chatFormat = "%group_prefix% %player_name%: %message%"
        }
        newConfig.set("chat-format", chatFormat)

        // Save new config
        val newConfigFile = File(dataFolder, "config.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated main configuration")
    }

    private fun migrateChannelsConfig() {
        val oldConfigFile = File(dataFolder, "config.yml")
        if (!oldConfigFile.exists()) return

        val oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile)
        val channelsSection = oldConfig.getConfigurationSection("channels")

        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Channels Configuration
            Define all available chat channels, their permissions, and behaviors
        """.trimIndent())

        val newChannelsSection = newConfig.createSection("channels")

        // Default global channel configuration
        val globalSection = newChannelsSection.createSection("global")
        globalSection.set("enabled", true)
        globalSection.set("permission", "")
        globalSection.set("radius", -1)
        globalSection.set("display-name", "")
        globalSection.set("description", "Main chat channel for all players")
        globalSection.set("cross-server", true)
        globalSection.set("local-only", false)
        globalSection.set("format", "%player_display_name% <dark_gray>»</dark_gray> %message%")
        globalSection.set("hover-template", "player-info")

        val globalFeaturesSection = globalSection.createSection("features")
        globalFeaturesSection.set("url-detection", true)
        globalFeaturesSection.set("mention-system", true)
        globalFeaturesSection.set("emoji-support", true)
        globalFeaturesSection.set("placeholder-parsing", true)

        val globalModerationSection = globalSection.createSection("moderation")
        globalModerationSection.set("rate-limit", 30)
        globalModerationSection.set("cooldown", 3)
        globalModerationSection.set("max-length", 256)
        globalModerationSection.set("spam-protection", true)

        // Migrate existing channels if they exist
        if (channelsSection != null) {
            for (channelName in channelsSection.getKeys(false)) {
                val oldChannel = channelsSection.getConfigurationSection(channelName)
                if (oldChannel != null && channelName != "global") {
                    val newChannelSection = newChannelsSection.createSection(channelName)

                    newChannelSection.set("enabled", true)
                    newChannelSection.set("permission", oldChannel.getString("permission", ""))
                    newChannelSection.set("radius", oldChannel.getInt("radius", -1))
                    newChannelSection.set("display-name", oldChannel.getString("display-name", ""))
                    newChannelSection.set("description", "Migrated channel: $channelName")
                    newChannelSection.set("cross-server", oldChannel.getInt("radius") == -1)
                    newChannelSection.set("local-only", oldChannel.getInt("radius") != -1)
                    newChannelSection.set("format", "%player_display_name% <dark_gray>»</dark_gray> %message%")
                    newChannelSection.set("hover-template", oldChannel.getString("hover", "player-info"))

                    val featuresSection = newChannelSection.createSection("features")
                    featuresSection.set("url-detection", true)
                    featuresSection.set("mention-system", true)
                    featuresSection.set("emoji-support", true)
                    featuresSection.set("placeholder-parsing", true)

                    val moderationSection = newChannelSection.createSection("moderation")
                    moderationSection.set("rate-limit", 30)
                    moderationSection.set("cooldown", 3)
                    moderationSection.set("max-length", 256)
                    moderationSection.set("spam-protection", true)
                }
            }
        }

        // Add standard channels if they don't exist
        val standardChannels = mapOf(
            "local" to mapOf(
                "permission" to "remmychat.channel.local",
                "radius" to 100,
                "display-name" to "<gray>[Local]</gray>",
                "description" to "Chat with nearby players (100 blocks)",
                "cross-server" to false,
                "local-only" to true
            ),
            "staff" to mapOf(
                "permission" to "remmychat.channel.staff",
                "radius" to -1,
                "display-name" to "<gold>[Staff]</gold>",
                "description" to "Private staff communication channel",
                "cross-server" to true,
                "local-only" to false
            ),
            "trade" to mapOf(
                "permission" to "remmychat.channel.trade",
                "radius" to -1,
                "display-name" to "<green>[Trade]</green>",
                "description" to "Buy and sell items with other players",
                "cross-server" to true,
                "local-only" to false
            )
        )

        for ((channelName, settings) in standardChannels) {
            if (!newChannelsSection.contains(channelName)) {
                val channelSection = newChannelsSection.createSection(channelName)
                channelSection.set("enabled", true)
                settings.forEach { (key, value) -> channelSection.set(key, value) }
                channelSection.set("format", "%player_display_name% <dark_gray>»</dark_gray> %message%")
                channelSection.set("hover-template", "player-info")

                val featuresSection = channelSection.createSection("features")
                featuresSection.set("url-detection", true)
                featuresSection.set("mention-system", channelName != "local")
                featuresSection.set("emoji-support", true)
                featuresSection.set("placeholder-parsing", true)

                val moderationSection = channelSection.createSection("moderation")
                moderationSection.set("rate-limit", if (channelName == "trade") 10 else 30)
                moderationSection.set("cooldown", if (channelName == "local") 1 else 3)
                moderationSection.set("max-length", 256)
                moderationSection.set("spam-protection", true)
            }
        }

        // Add channel groups
        val groupsSection = newConfig.createSection("channel-groups")
        val publicGroup = groupsSection.createSection("public")
        publicGroup.set("channels", listOf("global", "local", "trade"))
        publicGroup.set("description", "Public channels available to regular players")

        val staffGroup = groupsSection.createSection("staff")
        staffGroup.set("channels", listOf("staff"))
        staffGroup.set("description", "Staff-only communication channels")

        // Add auto-join settings
        val autoJoinSection = newConfig.createSection("auto-join")
        autoJoinSection.set("new-players", listOf("global", "local"))

        val permissionBasedSection = autoJoinSection.createSection("permission-based")
        permissionBasedSection.set("remmychat.channel.staff", listOf("staff"))
        permissionBasedSection.set("remmychat.channel.trade", listOf("trade"))

        // Add switching settings
        val switchingSection = newConfig.createSection("switching")
        switchingSection.set("enabled", true)
        switchingSection.set("command", "/ch")
        switchingSection.set("aliases", listOf("/channel", "/c"))
        switchingSection.set("remember-channel", true)
        switchingSection.set("show-list", true)

        val quickSwitchSection = switchingSection.createSection("quick-switch")
        quickSwitchSection.set("enabled", true)
        quickSwitchSection.set("1", "global")
        quickSwitchSection.set("2", "local")
        quickSwitchSection.set("3", "staff")
        quickSwitchSection.set("4", "trade")

        // Save new channels config
        val newConfigFile = File(dataFolder, "channels.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated channels configuration")
    }

    private fun migrateGroupsConfig() {
        val oldConfigFile = File(dataFolder, "config.yml")
        if (!oldConfigFile.exists()) return

        val oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile)
        val groupsSection = oldConfig.getConfigurationSection("groups")

        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Groups Configuration
            Define permission-based formatting rules and group behaviors
        """.trimIndent())

        val newGroupsSection = newConfig.createSection("groups")

        // Define default groups with priorities
        val defaultGroups = mapOf(
            "owner" to mapOf(
                "priority" to 1000,
                "display-name" to "Owner",
                "description" to "Server owner with full administrative privileges",
                "permissions" to listOf("remmychat.group.owner", "group.owner", "*"),
                "name-style" to "<bold><gradient:#FF0000:#FFAA00>%player_name%</gradient></bold>",
                "prefix" to "<red>[OWNER]</red>",
                "bypass-cooldown" to true,
                "bypass-filters" to true,
                "use-colors" to true,
                "use-formatting" to true
            ),
            "admin" to mapOf(
                "priority" to 900,
                "display-name" to "Administrator",
                "description" to "Server administrator with extensive privileges",
                "permissions" to listOf("remmychat.group.admin", "group.admin"),
                "name-style" to "<italic><color:#CC44FF>%player_name%</color></italic>",
                "prefix" to "<red>[ADMIN]</red>",
                "bypass-cooldown" to true,
                "bypass-filters" to true,
                "use-colors" to true,
                "use-formatting" to true
            ),
            "moderator" to mapOf(
                "priority" to 700,
                "display-name" to "Moderator",
                "description" to "Staff member responsible for maintaining order",
                "permissions" to listOf("remmychat.group.moderator", "group.mod"),
                "name-style" to "<underlined><color:#33CCFF>%player_name%</underlined>",
                "prefix" to "<blue>[MOD]</blue>",
                "bypass-cooldown" to true,
                "staff-channels" to true
            ),
            "vip" to mapOf(
                "priority" to 500,
                "display-name" to "VIP",
                "description" to "VIP member with special privileges",
                "permissions" to listOf("remmychat.group.vip", "group.vip"),
                "name-style" to "<color:#FFCC00>%player_name%</color>",
                "prefix" to "<yellow>[VIP]</yellow>",
                "custom-join-message" to true
            ),
            "default" to mapOf(
                "priority" to 100,
                "display-name" to "Player",
                "description" to "Default player group",
                "permissions" to listOf<String>(),
                "name-style" to "<white>%player_name%</white>",
                "prefix" to ""
            )
        )

        // Migrate existing groups or create defaults
        if (groupsSection != null) {
            for (groupName in groupsSection.getKeys(false)) {
                val oldGroup = groupsSection.getConfigurationSection(groupName)
                if (oldGroup != null) {
                    val newGroupSection = newGroupsSection.createSection(groupName)

                    // Set priority based on group name or use default
                    val priority = when (groupName) {
                        "owner" -> 1000
                        "admin" -> 900
                        "mod", "moderator" -> 700
                        "vip" -> 500
                        else -> 300
                    }
                    newGroupSection.set("priority", priority)
                    newGroupSection.set("display-name", groupName.replaceFirstChar { it.uppercase() })
                    newGroupSection.set("description", "Migrated group: $groupName")

                    // Migrate format to new structure
                    val oldFormat = oldGroup.getString("format", "")
                    val formatting = newGroupSection.createSection("formatting")
                    formatting.set("name-style", oldGroup.getString("name-style", "<white>%player_name%</white>"))
                    formatting.set("prefix", oldGroup.getString("prefix", ""))
                    formatting.set("suffix", "")
                    formatting.set("chat-format", if (!oldFormat.isNullOrEmpty()) oldFormat else "%prefix% %name-style% <dark_gray>»</dark_gray> %message%")

                    val interactions = newGroupSection.createSection("interactions")
                    interactions.set("hover-template", "player-info")
                    interactions.set("click-action", "suggest")
                    interactions.set("click-command", "/msg %player_name% ")

                    val features = newGroupSection.createSection("features")
                    features.set("bypass-cooldown", priority >= 700)
                    features.set("bypass-filters", priority >= 900)
                    features.set("use-colors", priority >= 500)
                    features.set("use-formatting", priority >= 500)
                }
            }
        } else {
            // Create default groups
            for ((groupName, settings) in defaultGroups) {
                val groupSection = newGroupsSection.createSection(groupName)
                settings.forEach { (key, value) ->
                    if (key !in listOf("name-style", "prefix", "bypass-cooldown", "bypass-filters", "use-colors", "use-formatting", "staff-channels", "custom-join-message")) {
                        groupSection.set(key, value)
                    }
                }

                val formatting = groupSection.createSection("formatting")
                formatting.set("name-style", settings["name-style"] as? String ?: "<white>%player_name%</white>")
                formatting.set("prefix", settings["prefix"] as? String ?: "")
                formatting.set("suffix", "")
                formatting.set("chat-format", "%prefix% %name-style% <dark_gray>»</dark_gray> %message%")

                val interactions = groupSection.createSection("interactions")
                interactions.set("hover-template", "player-info")
                interactions.set("click-action", "suggest")
                interactions.set("click-command", "/msg %player_name% ")

                val features = groupSection.createSection("features")
                settings.forEach { (key, value) ->
                    if (key in listOf("bypass-cooldown", "bypass-filters", "use-colors", "use-formatting", "staff-channels", "custom-join-message")) {
                        features.set(key.replace("-", "_"), value)
                    }
                }
            }
        }

        // Add group detection settings
        val detectionSection = newConfig.createSection("detection")
        detectionSection.set("method", "permission")

        val permissionDetection = detectionSection.createSection("permission-detection")
        permissionDetection.set("check-all", true)
        permissionDetection.set("cache-duration", 300)
        permissionDetection.set("auto-update", true)

        val vaultIntegration = detectionSection.createSection("vault-integration")
        vaultIntegration.set("enabled", true)
        vaultIntegration.set("use-primary-group", true)

        // Save new groups config
        val newConfigFile = File(dataFolder, "groups.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated groups configuration")
    }

    private fun migrateTemplatesConfig() {
        val oldConfigFile = File(dataFolder, "config.yml")
        val oldMessagesFile = File(dataFolder, "messages.yml")
        val oldSymbolsFile = File(dataFolder, "symbols.yml")

        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Templates Configuration
            Define reusable templates, placeholders, and formatting elements
        """.trimIndent())

        // Migrate placeholders from old config
        val placeholdersSection = newConfig.createSection("placeholders")

        if (oldConfigFile.exists()) {
            val oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile)
            val oldPlaceholders = oldConfig.getConfigurationSection("placeholders")

            if (oldPlaceholders != null) {
                for (key in oldPlaceholders.getKeys(false)) {
                    placeholdersSection.set(key, oldPlaceholders.getString(key))
                }
            }
        }

        // Add default placeholders if not present
        val defaultPlaceholders = mapOf(
            "default-message" to "<white>%message%</white>",
            "player-name" to "<#4A90E2>%player_name%</#4A90E2>",
            "server-name" to "<yellow>%server%</yellow>",
            "timestamp" to "<gray>[%time%]</gray>",
            "separator" to "<dark_gray>»</dark_gray>",
            "heart" to "<red>❤</red>",
            "star" to "<yellow>⭐</yellow>",
            "diamond" to "<aqua>💎</aqua>"
        )

        for ((key, value) in defaultPlaceholders) {
            if (!placeholdersSection.contains(key)) {
                placeholdersSection.set(key, value)
            }
        }

        // Migrate hover templates from old config
        val hoverTemplatesSection = newConfig.createSection("hover-templates")

        if (oldConfigFile.exists()) {
            val oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile)
            val oldTemplates = oldConfig.getConfigurationSection("templates.hovers")

            if (oldTemplates != null) {
                for (key in oldTemplates.getKeys(false)) {
                    hoverTemplatesSection.set(key, oldTemplates.getString(key))
                }
            }
        }

        // Add default hover templates
        val defaultHoverTemplates = mapOf(
            "player-info" to """
                <#778899>Player Information</color>
                <#F8F9FA>Name: <#E8E8E8>%player_name%</color></color>
                <#F8F9FA>Rank: <#E8E8E8>%player_group%</color></color>
                <#F8F9FA>Online: <#E8E8E8>%player_online_time%</color></color>

                <#AAAAAA>Click to send a message</color>
            """.trimIndent(),
            "local-chat" to """
                <#778899>Local Chat</color>
                <#F8F9FA>Range: <#E8E8E8>100 blocks</color></color>
                <#F8F9FA>Players nearby: <#E8E8E8>%nearby_players%</color></color>

                <#AAAAAA>Messages only visible to nearby players</color>
            """.trimIndent(),
            "staff-chat" to """
                <#778899>Staff Communication</color>
                <#F8F9FA>Private staff channel</color>
                <#F8F9FA>Cross-server: <#E8E8E8>Yes</color></color>

                <#AAAAAA>Staff-only communication channel</color>
            """.trimIndent()
        )

        for ((key, value) in defaultHoverTemplates) {
            if (!hoverTemplatesSection.contains(key)) {
                hoverTemplatesSection.set(key, value)
            }
        }

        // Migrate symbols if they exist
        if (oldSymbolsFile.exists()) {
            val oldSymbols = YamlConfiguration.loadConfiguration(oldSymbolsFile)
            val symbolsSection = oldSymbols.getConfigurationSection("symbols")

            if (symbolsSection != null) {
                for (key in symbolsSection.getKeys(false)) {
                    val symbolKey = key.replace(":", "").replace("_", "-")
                    placeholdersSection.set(symbolKey, symbolsSection.getString(key))
                }
            }
        }

        // Add message format templates
        val messageFormatsSection = newConfig.createSection("message-formats")
        messageFormatsSection.set("default-chat", "%player_display_name% %separator% %formatted-message%")
        messageFormatsSection.set("global-chat", "%player_display_name% %separator% %formatted-message%")
        messageFormatsSection.set("local-chat", "<gray>[Local]</gray> %player_display_name% %separator% %formatted-message%")
        messageFormatsSection.set("staff-chat", "<gold>[Staff]</gold> %player_display_name% %separator% <gold>%message%</gold>")
        messageFormatsSection.set("trade-chat", "<green>[Trade]</green> %player_display_name% %separator% %formatted-message%")

        // Add template settings
        val settingsSection = newConfig.createSection("settings")
        val processingSection = settingsSection.createSection("processing")
        processingSection.set("cache-compiled", true)
        processingSection.set("cache-size", 200)
        processingSection.set("cache-ttl", 3600)

        val validationSection = settingsSection.createSection("validation")
        validationSection.set("validate-on-load", true)
        validationSection.set("check-circular-refs", true)
        validationSection.set("warn-missing-placeholders", true)

        val performanceSection = settingsSection.createSection("performance")
        performanceSection.set("async-processing", true)
        performanceSection.set("max-processing-time", 100)
        performanceSection.set("fallback-on-timeout", true)

        // Save new templates config
        val newConfigFile = File(dataFolder, "templates.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated templates configuration")
    }

    private fun migrateCrossServerConfig() {
        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Cross-Server Configuration
            Configure cross-server messaging using Redis or Database methods
        """.trimIndent())

        // Server identification
        val serverSection = newConfig.createSection("server")
        serverSection.set("name", "server1")
        serverSection.set("display-name", "<yellow>Server1</yellow>")
        serverSection.set("description", "Main server")
        serverSection.set("region", "us-east")

        // Redis configuration (disabled by default)
        val redisSection = newConfig.createSection("redis")
        redisSection.set("enabled", false)

        val connectionSection = redisSection.createSection("connection")
        connectionSection.set("host", "localhost")
        connectionSection.set("port", 6379)
        connectionSection.set("password", "")
        connectionSection.set("database", 0)
        connectionSection.set("timeout", 5000)

        val poolSection = redisSection.createSection("pool")
        poolSection.set("max-connections", 10)
        poolSection.set("min-idle-connections", 2)
        poolSection.set("max-idle-connections", 8)
        poolSection.set("max-wait-time", 3000)

        val channelsSection = redisSection.createSection("channels")
        channelsSection.set("chat", "remmychat:chat")
        channelsSection.set("heartbeat", "remmychat:heartbeat")
        channelsSection.set("player-sync", "remmychat:player_sync")
        channelsSection.set("admin", "remmychat:admin")

        // Database configuration
        val databaseSection = newConfig.createSection("database")
        databaseSection.set("enabled", false)
        databaseSection.set("use-main-connection", true)

        val pollingSection = databaseSection.createSection("polling")
        pollingSection.set("interval", 2)
        pollingSection.set("batch-size", 50)
        pollingSection.set("message-ttl", 300)

        // Network channels
        val networkChannelsSection = newConfig.createSection("network-channels")

        val globalChannel = networkChannelsSection.createSection("global")
        globalChannel.set("enabled", true)
        globalChannel.set("format", "<gray>[%server%]</gray> %original_message%")
        globalChannel.set("allowed-servers", listOf("*"))
        globalChannel.set("priority", 5)

        val staffChannel = networkChannelsSection.createSection("staff")
        staffChannel.set("enabled", true)
        staffChannel.set("format", "<yellow>[%server%]</yellow> %original_message%")
        staffChannel.set("allowed-servers", listOf("*"))
        staffChannel.set("priority", 10)

        // Performance settings
        val performanceSection = newConfig.createSection("performance")
        val queueSection = performanceSection.createSection("queue")
        queueSection.set("max-size", 1000)
        queueSection.set("batch-processing", true)
        queueSection.set("batch-size", 20)
        queueSection.set("process-interval", 50)

        // Save cross-server config
        val newConfigFile = File(dataFolder, "cross-server.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated cross-server configuration")
    }

    private fun migrateDatabaseConfig() {
        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Database Configuration
            Configure database connections, sync settings, and data management
        """.trimIndent())

        // Primary database connection
        val primarySection = newConfig.createSection("primary")
        primarySection.set("type", "mysql")

        val connectionSection = primarySection.createSection("connection")
        connectionSection.set("host", "localhost")
        connectionSection.set("port", 3306)
        connectionSection.set("database", "remmychat")
        connectionSection.set("username", "remmychat")
        connectionSection.set("password", "your_secure_password")

        val propertiesSection = connectionSection.createSection("properties")
        propertiesSection.set("useSSL", false)
        propertiesSection.set("allowPublicKeyRetrieval", true)
        propertiesSection.set("serverTimezone", "UTC")
        propertiesSection.set("characterEncoding", "utf8mb4")
        propertiesSection.set("useUnicode", true)

        val poolSection = primarySection.createSection("pool")
        poolSection.set("maximum-pool-size", 10)
        poolSection.set("minimum-idle", 2)
        poolSection.set("connection-timeout", 30000)
        poolSection.set("idle-timeout", 600000)
        poolSection.set("max-lifetime", 1800000)
        poolSection.set("connection-test-query", "SELECT 1")

        val tablesSection = primarySection.createSection("tables")
        tablesSection.set("prefix", "rc_")
        tablesSection.set("users", "users")
        tablesSection.set("user_preferences", "user_preferences")
        tablesSection.set("message_history", "message_history")
        tablesSection.set("muted_players", "muted_players")

        // Sync settings
        val syncSection = newConfig.createSection("sync")
        syncSection.set("enabled", true)

        val playerDataSection = syncSection.createSection("player-data")
        val syncItemsSection = playerDataSection.createSection("sync-items")
        syncItemsSection.set("channel-preferences", true)
        syncItemsSection.set("message-settings", true)
        syncItemsSection.set("muted-players", true)
        syncItemsSection.set("last-seen", true)

        val frequencySection = playerDataSection.createSection("frequency")
        frequencySection.set("interval", 60)

        val immediateSyncSection = frequencySection.createSection("immediate-sync")
        immediateSyncSection.set("channel-change", true)
        immediateSyncSection.set("login", true)
        immediateSyncSection.set("logout", true)

        // Caching
        val cachingSection = newConfig.createSection("caching")
        cachingSection.set("enabled", true)

        val userDataCache = cachingSection.createSection("cache-types.user-data")
        userDataCache.set("enabled", true)
        userDataCache.set("ttl", 300)
        userDataCache.set("max-size", 1000)

        // Maintenance
        val maintenanceSection = newConfig.createSection("maintenance")
        maintenanceSection.set("enabled", true)

        val cleanupSection = maintenanceSection.createSection("cleanup")
        val oldMessagesSection = cleanupSection.createSection("old-messages")
        oldMessagesSection.set("enabled", true)
        oldMessagesSection.set("max-age", 30)
        oldMessagesSection.set("interval", 24)

        // Save database config
        val newConfigFile = File(dataFolder, "database.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated database configuration")
    }

    private fun migrateDiscordConfig() {
        val newConfig = YamlConfiguration()

        // Add header comment
        newConfig.options().header("""
            RemmyChat Discord Integration Configuration
            Requires DiscordSRV plugin to be installed and configured
        """.trimIndent())

        // Integration settings
        val integrationSection = newConfig.createSection("integration")
        integrationSection.set("enabled", false)
        integrationSection.set("auto-detect", true)
        integrationSection.set("mode", "bidirectional")
        integrationSection.set("validate-on-startup", true)
        integrationSection.set("require-discordsrv", true)

        // Channel mappings
        val channelsSection = newConfig.createSection("channels")

        val globalChannel = channelsSection.createSection("global")
        globalChannel.set("enabled", true)
        globalChannel.set("discord-channel", "general")
        globalChannel.set("direction", "both")

        val staffChannel = channelsSection.createSection("staff")
        staffChannel.set("enabled", true)
        staffChannel.set("discord-channel", "staff")
        staffChannel.set("direction", "both")
        staffChannel.set("allowed-roles", listOf("Staff", "Admin", "Moderator"))

        val tradeChannel = channelsSection.createSection("trade")
        tradeChannel.set("enabled", true)
        tradeChannel.set("discord-channel", "marketplace")
        tradeChannel.set("direction", "both")

        // Message formatting
        val formattingSection = newConfig.createSection("formatting")
        val mcToDiscordSection = formattingSection.createSection("minecraft-to-discord")
        mcToDiscordSection.set("format", "**%player_name%** (%server%): %message%")

        val channelFormatsSection = mcToDiscordSection.createSection("channel-formats")
        channelFormatsSection.set("global", "**%player_name%**: %message%")
        channelFormatsSection.set("staff", "**[Staff]** %player_name%: %message%")
        channelFormatsSection.set("trade", "**[Trade]** %player_name%: %message%")

        mcToDiscordSection.set("show-server-name", true)
        mcToDiscordSection.set("server-format", "**[%server%]** %player_name%: %message%")

        val discordToMcSection = formattingSection.createSection("discord-to-minecraft")
        discordToMcSection.set("format", "<blue>[Discord]</blue> <gray>%username%</gray>: %message%")

        val discordChannelFormatsSection = discordToMcSection.createSection("channel-formats")
        discordChannelFormatsSection.set("general", "<blue>[Discord]</blue> <gray>%username%</gray>: %message%")
        discordChannelFormatsSection.set("staff", "<gold>[Discord-Staff]</gold> <yellow>%username%</yellow>: %message%")

        // Content processing
        val contentSection = newConfig.createSection("content")
        val processingSection = contentSection.createSection("processing")
        processingSection.set("strip-minecraft-formatting", true)
        processingSection.set("strip-discord-formatting", false)
        processingSection.set("convert-emojis", true)
        processingSection.set("process-mentions", true)

        val filteringSection = contentSection.createSection("filtering")
        filteringSection.set("filter-bots", true)
        filteringSection.set("filter-webhooks", false)
        filteringSection.set("filter-system-messages", true)

        val maxLengthSection = contentSection.createSection("max-length")
        maxLengthSection.set("minecraft-to-discord", 2000)
        maxLengthSection.set("discord-to-minecraft", 256)

        // Save discord config
        val newConfigFile = File(dataFolder, "discord.yml")
        newConfig.save(newConfigFile)
        plugin.logger.info("Migrated Discord configuration")
    }

    private fun migrateMessagesConfig() {
        val oldMessagesFile = File(dataFolder, "messages.yml")
        if (!oldMessagesFile.exists()) return

        val oldMessages = YamlConfiguration.loadConfiguration(oldMessagesFile)

        // Messages are now integrated into templates.yml, so we'll add them there
        val templatesFile = File(dataFolder, "templates.yml")
        if (!templatesFile.exists()) return

        val templatesConfig = YamlConfiguration.loadConfiguration(templatesFile)
        val messagesSection = templatesConfig.createSection("system-messages")

        // Migrate common messages
        val messageKeys = mapOf(
            "plugin-reloaded" to "plugin-reloaded",
            "current-channel" to "current-channel",
            "channel-changed" to "channel-changed",
            "cooldown" to "cooldown-message",
            "msg-to-format" to "private-message-to",
            "msg-from-format" to "private-message-from",
            "socialspy-format" to "social-spy-format"
        )

        for ((oldKey, newKey) in messageKeys) {
            val message = oldMessages.getString(oldKey)
            if (message != null) {
                messagesSection.set(newKey, message)
            }
        }

        // Migrate error messages
        val errorSection = oldMessages.getConfigurationSection("error")
        if (errorSection != null) {
            val newErrorSection = messagesSection.createSection("errors")
            for (key in errorSection.getKeys(false)) {
                newErrorSection.set(key.replace("-", "_"), errorSection.getString(key))
            }
        }

        templatesConfig.save(templatesFile)
        plugin.logger.info("Migrated messages to templates configuration")
    }

    private fun migrateSymbolsConfig() {
        val oldSymbolsFile = File(dataFolder, "symbols.yml")
        if (!oldSymbolsFile.exists()) return

        // Symbols have already been migrated to templates.yml in migrateTemplatesConfig()
        plugin.logger.info("Symbols migrated to templates configuration")
    }

    private fun createMigrationMarker() {
        val marker = File(dataFolder, ".migrated-to-organized")
        marker.writeText("Migration completed at: ${LocalDateTime.now()}")
    }
}
