package com.noximity.remmyChat


import com.noximity.remmyChat.commands.brigadier.*
import com.noximity.remmyChat.config.ConfigManager
import com.noximity.remmyChat.config.Messages
import com.noximity.remmyChat.database.DatabaseManager
import com.noximity.remmyChat.integrations.DiscordSRVIntegration
import com.noximity.remmyChat.integrations.DiscordSRVUtil
import com.noximity.remmyChat.compatibility.CompatibleChatListener
import com.noximity.remmyChat.compatibility.VersionCompatibility
import com.noximity.remmyChat.services.ChatService
import com.noximity.remmyChat.services.FormatService
import com.noximity.remmyChat.services.PermissionService
import com.noximity.remmyChat.services.ReloadService
import com.noximity.remmyChat.utils.PlaceholderManager
import com.noximity.remmyChat.templates.TemplateManager
import com.noximity.remmyChat.channels.ChannelManager
import com.noximity.remmyChat.commands.ChannelCommand
import com.noximity.remmyChat.groups.GroupManager
import com.noximity.remmyChat.migration.ConfigMigration
import com.noximity.remmyChat.security.SecurityManager
import com.noximity.remmyChat.monitoring.PerformanceMonitor
import com.noximity.remmyChat.maintenance.MaintenanceManager
import com.noximity.remmyChat.discord.AdvancedDiscordIntegration
import com.noximity.remmyChat.discord.DiscordConfigHelper
import com.noximity.remmyChat.templates.AdvancedTemplateProcessor
import com.noximity.remmyChat.moderation.ModerationManager
import com.noximity.remmyChat.listeners.EnhancedChatListener
import com.noximity.remmyChat.features.TradeChannelHandler
import com.noximity.remmyChat.features.HelpChannelHandler
import com.noximity.remmyChat.features.GroupBehaviorManager
import com.noximity.remmyChat.features.AdvancedDiscordFeatures
import com.noximity.remmyChat.features.DatabaseSecurityManager
import com.noximity.remmyChat.features.EventChannelHandler
import com.noximity.remmyChat.features.FeatureManager
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin

class RemmyChat : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set
    lateinit var messages: Messages
        private set
    lateinit var chatService: ChatService
        private set
    lateinit var formatService: FormatService
        private set
    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var permissionService: PermissionService
        private set
    lateinit var placeholderManager: PlaceholderManager
        private set
    lateinit var discordSRVIntegration: DiscordSRVIntegration
        private set
    lateinit var templateManager: TemplateManager
        private set
    lateinit var channelManager: ChannelManager
        private set
    lateinit var groupManager: GroupManager
        private set

    // New advanced features
    lateinit var configMigration: ConfigMigration
        private set
    lateinit var securityManager: SecurityManager
        private set
    lateinit var performanceMonitor: PerformanceMonitor
        private set
    lateinit var maintenanceManager: MaintenanceManager
        private set
    lateinit var advancedDiscordIntegration: AdvancedDiscordIntegration
        private set
    lateinit var advancedTemplateProcessor: AdvancedTemplateProcessor
        private set
    lateinit var moderationManager: ModerationManager
        private set
    lateinit var reloadService: ReloadService
        private set

    // New feature handlers
    lateinit var tradeChannelHandler: TradeChannelHandler
        private set
    lateinit var helpChannelHandler: HelpChannelHandler
        private set
    lateinit var groupBehaviorManager: GroupBehaviorManager
        private set
    lateinit var advancedDiscordFeatures: AdvancedDiscordFeatures
        private set
    lateinit var databaseSecurityManager: DatabaseSecurityManager
        private set
    lateinit var eventChannelHandler: EventChannelHandler
        private set
    lateinit var featureManager: FeatureManager
        private set

    var isProtocolLibEnabled: Boolean = false
        private set
    var isDiscordSRVEnabled: Boolean = false
        private set


    override fun onEnable() {
        instance = this

        // Log version compatibility information
        logger.info("Starting RemmyChat with version compatibility checks...")
        VersionCompatibility.logCompatibilityInfo(logger)

        // Initialize migration system first
        this.configMigration = ConfigMigration(this)

        // Check and perform migration if needed
        if (!configMigration.migrateToOrganizedStructure()) {
            logger.severe("Failed to migrate configuration to organized structure!")
            logger.severe("Please check the error messages above and fix any issues.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Fix placeholder issues in existing configurations
        if (!configMigration.fixPlaceholderIssues()) {
            logger.warning("Failed to fix placeholder issues, but continuing startup...")
        }

        this.configManager = ConfigManager(this)
        this.configManager.initialize()
        this.messages = Messages(this)
        this.databaseManager = DatabaseManager(this)

        this.permissionService = PermissionService(this)

        // Initialize core managers
        this.templateManager = TemplateManager(this)
        this.groupManager = GroupManager(this)
        this.channelManager = ChannelManager(this)

        // Initialize advanced features
        this.securityManager = SecurityManager(this)
        this.performanceMonitor = PerformanceMonitor(this)
        this.maintenanceManager = MaintenanceManager(this)
        this.advancedTemplateProcessor = AdvancedTemplateProcessor(this)
        this.moderationManager = ModerationManager(this)

        // Initialize new feature handlers
        this.tradeChannelHandler = TradeChannelHandler(this)
        this.helpChannelHandler = HelpChannelHandler(this)
        this.groupBehaviorManager = GroupBehaviorManager(this)
        this.advancedDiscordFeatures = AdvancedDiscordFeatures(this)
        this.databaseSecurityManager = DatabaseSecurityManager(this)
        this.eventChannelHandler = EventChannelHandler(this)
        this.featureManager = FeatureManager(this)

        this.formatService = FormatService(this)
        this.chatService = ChatService(this)
        this.placeholderManager = PlaceholderManager(this)
        this.discordSRVIntegration = DiscordSRVIntegration(this)
        this.reloadService = ReloadService(this)


        // Initialize the managers
        this.templateManager.initialize()
        this.groupManager.initialize()
        this.channelManager.initialize()
        this.placeholderManager.initialize()

        // Refresh placeholders from templates after initialization
        this.placeholderManager.refreshFromTemplateManager()

        // Initialize advanced features
        this.securityManager.initialize()
        this.performanceMonitor.initialize()
        this.maintenanceManager.initialize()
        this.advancedTemplateProcessor.initialize()
        this.moderationManager.initialize()

        // Initialize new feature handlers
        this.tradeChannelHandler.initialize()
        this.helpChannelHandler.initialize()
        this.groupBehaviorManager.initialize()
        this.advancedDiscordFeatures.initialize()
        this.databaseSecurityManager.initialize()
        this.eventChannelHandler.initialize()

        // Initialize feature manager last to coordinate all features
        this.featureManager.initialize()

        // ProtocolLib detection
        if (server.pluginManager.getPlugin("ProtocolLib") != null) {
            this.isProtocolLibEnabled = true
            debugLog("ProtocolLib found. Advanced message deletion enabled.")
        } else {
            this.isProtocolLibEnabled = false
            debugLog("ProtocolLib not found. Advanced message deletion disabled.")
        }

        // Register new Brigadier commands
        registerBrigadierCommands()

        // Register fallback commands in case Brigadier fails
        registerFallbackCommands()

        // Register non-Brigadier commands using Paper API
        // Note: Paper plugins don't support YAML-based command declarations
        // Commands are registered through the Brigadier system above

        server.pluginManager.registerEvents(CompatibleChatListener(this), this)
        server.pluginManager.registerEvents(EnhancedChatListener(this), this)

        // Initialize PlaceholderAPI integration with delayed retry
        initializePlaceholderAPI()

        // Initialize DiscordSRV integration with delayed retry
        initializeDiscordSRV()

        logger.info("RemmyChat initialized successfully - running in local-only mode")

        // Initialize bStats metrics
        try {
            val pluginId = 26691
            logger.info("Initializing bStats metrics with plugin ID: $pluginId")

            // Check if bStats is enabled in server config
            val bStatsEnabled = config.getBoolean("bStats.enabled", true)
            logger.info("bStats enabled in server config: $bStatsEnabled")

            val metrics = Metrics(this, pluginId)
            logger.info("✅ bStats metrics initialized successfully!")
            logger.info("📊 Plugin should now appear on bStats.org with ID: $pluginId")

            debugLog("bStats metrics object created: $metrics")
        } catch (e: Exception) {
            logger.severe("❌ Failed to initialize bStats metrics: ${e.message}")
            e.printStackTrace()
        }

        logger.info("RemmyChat has been enabled!")
        logger.info("Compatible with Paper ${VersionCompatibility.getMinecraftVersion()} (${VersionCompatibility.getServerInfo()})")
    }

    override fun onDisable() {
        logger.info("Shutting down RemmyChat...")

        // Shutdown feature manager first to coordinate all feature shutdowns
        if (::featureManager.isInitialized) {
            featureManager.shutdown()
        }

        // Shutdown advanced features first
        if (::maintenanceManager.isInitialized) {
            maintenanceManager.shutdown()
        }

        if (::advancedDiscordIntegration.isInitialized) {
            advancedDiscordIntegration.shutdown()
        }

        if (::advancedTemplateProcessor.isInitialized) {
            try {
                advancedTemplateProcessor.shutdown()
            } catch (e: Exception) {
                logger.warning("Error shutting down AdvancedTemplateProcessor: ${e.message}")
            }
        }

        if (::performanceMonitor.isInitialized) {
            performanceMonitor.shutdown()
        }

        // Shutdown core services
        if (::discordSRVIntegration.isInitialized) {
            discordSRVIntegration.shutdown()
        }

        DiscordSRVUtil.shutdown()

        if (::chatService.isInitialized) {
            chatService.saveAllUsers()
        }

        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }

        logger.info("RemmyChat has been disabled!")
    }

    private fun initializePlaceholderAPI() {
        val papiPlugin = server.pluginManager.getPlugin("PlaceholderAPI")
        if (papiPlugin != null) {
            logger.info("PlaceholderAPI detected - Version: ${papiPlugin.description.version}")

            // Check if PlaceholderAPI is properly enabled
            if (!papiPlugin.isEnabled) {
                logger.warning("PlaceholderAPI plugin found but not enabled - will retry in 1 second")
                server.scheduler.runTaskLater(this, Runnable {
                    initializePlaceholderAPI()
                }, 20L)
                return
            }

            try {
                // Use PlaceholderAPI's class loader to find the expansion class
                val papiClassLoader = papiPlugin.javaClass.classLoader
                val expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion", true, papiClassLoader)

                // Verify we can create our expansion
                val expansion = RemmyChatPlaceholders(this)

                // Try to register the expansion
                val registered = expansion.register()
                if (registered) {
                    logger.info("PlaceholderAPI integration enabled successfully!")
                } else {
                    logger.warning("Failed to register PlaceholderAPI expansion - expansion already exists or registration failed")
                }
            } catch (e: ClassNotFoundException) {
                logger.warning("PlaceholderAPI expansion classes not found: ${e.message}")
                // Retry after a delay in case PlaceholderAPI is still loading
                server.scheduler.runTaskLater(this, Runnable {
                    retryPlaceholderAPIInitialization()
                }, 40L) // Wait 2 seconds
            } catch (e: NoClassDefFoundError) {
                logger.warning("PlaceholderAPI classes could not be loaded: ${e.message}")
                server.scheduler.runTaskLater(this, Runnable {
                    retryPlaceholderAPIInitialization()
                }, 40L)
            } catch (e: Exception) {
                logger.warning("Failed to initialize PlaceholderAPI expansion: ${e.javaClass.simpleName} - ${e.message}")
                server.scheduler.runTaskLater(this, Runnable {
                    retryPlaceholderAPIInitialization()
                }, 40L)
            }
        } else {
            logger.info("PlaceholderAPI not found - placeholders will use basic replacements only")
        }
    }

    private fun retryPlaceholderAPIInitialization() {
        val papiPlugin = server.pluginManager.getPlugin("PlaceholderAPI")
        if (papiPlugin != null && papiPlugin.isEnabled) {
            try {
                val papiClassLoader = papiPlugin.javaClass.classLoader
                Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion", true, papiClassLoader)

                val expansion = RemmyChatPlaceholders(this)
                val registered = expansion.register()
                if (registered) {
                    logger.info("PlaceholderAPI integration enabled successfully on retry!")
                } else {
                    logger.warning("PlaceholderAPI expansion registration failed on retry")
                }
            } catch (e: Exception) {
                logger.warning("PlaceholderAPI integration failed on retry: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }

    fun debugLog(message: String) {
        try {
            if (::configManager.isInitialized && configManager.getMainConfig().getBoolean("debug.enabled", false)) {
                logger.info("[DEBUG] $message")
            }
        } catch (e: Exception) {
            // Config not ready yet, just log without debug check
            logger.info("[DEBUG] $message")
        }
    }



    override fun reloadConfig() {
        super.reloadConfig()
        if (::configManager.isInitialized) {
            configManager.reload()

            // Reload channels
            if (::channelManager.isInitialized) {
                channelManager.reload()
            }

            // Reload messages
            if (::messages.isInitialized) {
                messages.reloadMessages()
            }

            // Reload groups
            if (::groupManager.isInitialized) {
                groupManager.reload()
            }

            // Reload templates
            if (::templateManager.isInitialized) {
                templateManager.reload()
                // Refresh placeholders after template reload
                if (::placeholderManager.isInitialized) {
                    placeholderManager.refreshFromTemplateManager()
                }
            }

            logger.info("RemmyChat configuration reloaded successfully!")
        }
    }

    private fun initializeDiscordSRV() {
        val discordSRVPlugin = server.pluginManager.getPlugin("DiscordSRV")
        if (discordSRVPlugin != null) {
            logger.info("DiscordSRV detected - Version: ${discordSRVPlugin.description.version}")

            // Check if DiscordSRV is properly enabled
            if (!discordSRVPlugin.isEnabled) {
                logger.warning("DiscordSRV plugin found but not enabled - will retry in 2 seconds")
                server.scheduler.runTaskLater(this, Runnable {
                    initializeDiscordSRV()
                }, 40L)
                return
            }

            try {
                // Use DiscordSRV's class loader to find classes
                val discordSRVClassLoader = discordSRVPlugin.javaClass.classLoader

                // Test if we can access basic DiscordSRV classes
                Class.forName("github.scarsz.discordsrv.DiscordSRV", true, discordSRVClassLoader)
                Class.forName("github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent", true, discordSRVClassLoader)

                this.isDiscordSRVEnabled = true
                DiscordSRVUtil.initialize(this)
                discordSRVIntegration.initialize()
                server.pluginManager.registerEvents(discordSRVIntegration, this)

                // Initialize advanced Discord integration
                this.advancedDiscordIntegration = AdvancedDiscordIntegration(this)
                this.advancedDiscordIntegration.initialize()

                logger.info("DiscordSRV integration enabled successfully!")
                debugLog("DiscordSRV found and integrated with advanced features!")
            } catch (e: ClassNotFoundException) {
                logger.warning("DiscordSRV classes not found: ${e.message}")
                initializeBasicDiscordIntegration()
                // Retry after a delay in case DiscordSRV is still loading
                server.scheduler.runTaskLater(this, Runnable {
                    retryDiscordSRVInitialization()
                }, 60L) // Wait 3 seconds
            } catch (e: NoClassDefFoundError) {
                logger.warning("DiscordSRV classes not available: ${e.message}")
                initializeBasicDiscordIntegration()
                server.scheduler.runTaskLater(this, Runnable {
                    retryDiscordSRVInitialization()
                }, 60L)
            } catch (e: Exception) {
                logger.warning("Failed to initialize DiscordSRV: ${e.javaClass.simpleName} - ${e.message}")
                initializeBasicDiscordIntegration()
                server.scheduler.runTaskLater(this, Runnable {
                    retryDiscordSRVInitialization()
                }, 60L)
            }
        } else {
            logger.info("DiscordSRV not found - basic Discord integration enabled")
            initializeBasicDiscordIntegration()
        }
    }

    private fun initializeBasicDiscordIntegration() {
        this.isDiscordSRVEnabled = false
        // Initialize basic Discord integration without DiscordSRV
        this.advancedDiscordIntegration = AdvancedDiscordIntegration(this)
        this.advancedDiscordIntegration.initialize()
        debugLog("Basic Discord integration enabled without DiscordSRV.")
    }

    private fun retryDiscordSRVInitialization() {
        val discordSRVPlugin = server.pluginManager.getPlugin("DiscordSRV")
        if (discordSRVPlugin != null && discordSRVPlugin.isEnabled && !isDiscordSRVEnabled) {
            try {
                val discordSRVClassLoader = discordSRVPlugin.javaClass.classLoader
                Class.forName("github.scarsz.discordsrv.DiscordSRV", true, discordSRVClassLoader)
                Class.forName("github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent", true, discordSRVClassLoader)

                this.isDiscordSRVEnabled = true
                DiscordSRVUtil.initialize(this)
                discordSRVIntegration.initialize()
                server.pluginManager.registerEvents(discordSRVIntegration, this)

                // Re-initialize advanced Discord integration with DiscordSRV
                if (::advancedDiscordIntegration.isInitialized) {
                    advancedDiscordIntegration.shutdown()
                }
                this.advancedDiscordIntegration = AdvancedDiscordIntegration(this)
                this.advancedDiscordIntegration.initialize()

                logger.info("DiscordSRV integration enabled successfully on retry!")
            } catch (e: Exception) {
                logger.warning("DiscordSRV integration failed on retry: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }





    private fun registerBrigadierCommands() {
        val manager: LifecycleEventManager<*> = this.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            // Register /msg command
            val msgCommand = BrigadierMessageCommand(this)
            commands.register(
                Commands.literal("msg")
                    .then(
                        Commands.argument("player", ArgumentTypes.player())
                            .then(
                                Commands.argument("message", StringArgumentType.greedyString())
                                    .suggests(msgCommand)
                                    .executes(msgCommand)
                            )
                    )
                    .build(),
                "Send a private message to another player",
                listOf("message", "tell", "whisper")
            )

            // Register /reply command
            val replyCommand = BrigadierReplyCommand(this)
            commands.register(
                Commands.literal("reply")
                    .then(
                        Commands.argument("message", StringArgumentType.greedyString())
                            .executes(replyCommand)
                    )
                    .build(),
                "Reply to the last player who messaged you",
                listOf("r")
            )

            // Register /msgtoggle command
            val msgToggleCommand = BrigadierMsgToggleCommand(this)
            commands.register(
                Commands.literal("msgtoggle")
                    .executes(msgToggleCommand)
                    .build(),
                "Toggle private messages on/off",
                listOf("togglemsg")
            )

            // Register /socialspy command
            val socialSpyCommand = BrigadierSocialSpyCommand(this)
            commands.register(
                Commands.literal("socialspy")
                    .executes(socialSpyCommand)
                    .build(),
                "Toggle social spy mode",
                listOf("sspy")
            )

            // Register /ch command (channel)
            val channelCommand = BrigadierChannelCommand(this)
            commands.register(
                Commands.literal("ch")
                    .executes(channelCommand) // /ch (show current channel)
                    .then(
                        Commands.argument("subcommand", StringArgumentType.word())
                            .suggests(channelCommand)
                            .executes(channelCommand) // /ch <subcommand>
                            .then(
                                Commands.argument("channel", StringArgumentType.word())
                                    .suggests(channelCommand)
                                    .executes(channelCommand) // /ch <subcommand> <channel>
                            )
                    )
                    .build(),
                "Channel management commands",
                listOf("channel", "c")
            )

            // Register /remmychat command (main plugin command)
            val chatCommand = BrigadierChatCommand(this)
            commands.register(
                Commands.literal("remmychat")
                    .executes(chatCommand) // /remmychat (show help)
                    .then(
                        Commands.argument("subcommand", StringArgumentType.word())
                            .suggests(chatCommand)
                            .executes(chatCommand) // /remmychat <subcommand>
                            .then(
                                Commands.argument("channel", StringArgumentType.word())
                                    .suggests(chatCommand)
                                    .executes(chatCommand) // /remmychat channel <channel>
                            )
                            .then(
                                Commands.argument("discord_subcommand", StringArgumentType.word())
                                    .suggests(chatCommand)
                                    .executes(chatCommand) // /remmychat discord <subcommand>
                                    .then(
                                        Commands.argument("test_target", StringArgumentType.word())
                                            .suggests(chatCommand)
                                            .executes(chatCommand) // /remmychat discord test <channel>
                                    )
                            )
                            .then(
                                Commands.argument("reload_component", StringArgumentType.word())
                                    .suggests(chatCommand)
                                    .executes(chatCommand) // /remmychat reload <component>
                            )
                    )
                    .build(),
                "Main RemmyChat command",
                listOf("rc")
            )
        }
    }

    /**
     * Register fallback commands using traditional command system
     */
    private fun registerFallbackCommands() {
        try {
            // Register traditional channel command as fallback
            val channelCommandFallback = ChannelCommand(this)
            getCommand("ch")?.setExecutor(channelCommandFallback)
            getCommand("ch")?.tabCompleter = channelCommandFallback

            // Register features command
            val featuresCommand = com.noximity.remmyChat.commands.FeaturesCommand(this)
            getCommand("remmychat")?.setExecutor(featuresCommand)
            getCommand("remmychat")?.tabCompleter = featuresCommand

            debugLog("Fallback commands registered successfully")
        } catch (e: Exception) {
            logger.warning("Failed to register fallback commands: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: RemmyChat
            private set
    }


}
