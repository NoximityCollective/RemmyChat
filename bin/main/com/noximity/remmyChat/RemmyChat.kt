package com.noximity.remmyChat

import com.noximity.remmyChat.commands.*
import com.noximity.remmyChat.config.ConfigManager
import com.noximity.remmyChat.config.Messages
import com.noximity.remmyChat.database.DatabaseManager
import com.noximity.remmyChat.listeners.ChatListener
import com.noximity.remmyChat.services.ChatService
import com.noximity.remmyChat.services.FormatService
import com.noximity.remmyChat.services.PermissionService
import com.noximity.remmyChat.utils.PlaceholderManager
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
    var isProtocolLibEnabled: Boolean = false
        private set

    override fun onEnable() {
        instance = this

        this.configManager = ConfigManager(this)
        this.messages = Messages(this)
        this.databaseManager = DatabaseManager(this)

        this.permissionService = PermissionService(this)
        this.formatService = FormatService(this)
        this.chatService = ChatService(this)
        this.placeholderManager = PlaceholderManager(this)

        // ProtocolLib detection
        if (server.pluginManager.getPlugin("ProtocolLib") != null) {
            this.isProtocolLibEnabled = true
            debugLog("ProtocolLib found. Advanced message deletion enabled.")
        } else {
            this.isProtocolLibEnabled = false
            debugLog("ProtocolLib not found. Advanced message deletion disabled.")
        }

        getCommand("remchat")?.setExecutor(ChatCommand(this))
        getCommand("msg")?.setExecutor(MessageCommand(this))
        getCommand("reply")?.setExecutor(ReplyCommand(this))
        getCommand("msgtoggle")?.setExecutor(MsgToggleCommand(this))
        getCommand("socialspy")?.setExecutor(SocialSpyCommand(this))

        server.pluginManager.registerEvents(ChatListener(this), this)

        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            logger.info("PlaceholderAPI found and hooked!")
            RemmyChatPlaceholders(this).register()
        } else {
            logger.warning("PlaceholderAPI not found! Placeholders will not work.")
        }

        logger.info("RemmyChat has been enabled!")
    }

    override fun onDisable() {
        if (::chatService.isInitialized) {
            chatService.saveAllUsers()
        }

        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }

        logger.info("RemmyChat has been disabled!")
    }

    fun debugLog(message: String) {
        if (config.getBoolean("debug.enabled", false)) {
            logger.info("[DEBUG] $message")
        }
    }



    companion object {
        lateinit var instance: RemmyChat
            private set
    }
}
