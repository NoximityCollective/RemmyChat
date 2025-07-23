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
    var configManager: ConfigManager? = null
        private set
    var messages: Messages? = null
        private set
    var chatService: ChatService? = null
        private set
    var formatService: FormatService? = null
        private set
    var databaseManager: DatabaseManager? = null
        private set
    var permissionService: PermissionService? = null
        private set
    var placeholderManager: PlaceholderManager? = null
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
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.isProtocolLibEnabled = true
            debugLog("ProtocolLib found. Advanced message deletion enabled.")
        } else {
            this.isProtocolLibEnabled = false
            debugLog("ProtocolLib not found. Advanced message deletion disabled.")
        }

        getCommand("remchat")!!.setExecutor(ChatCommand(this))
        getCommand("msg")!!.setExecutor(MessageCommand(this))
        getCommand("reply")!!.setExecutor(ReplyCommand(this))
        getCommand("msgtoggle")!!.setExecutor(MsgToggleCommand(this))
        getCommand("socialspy")!!.setExecutor(SocialSpyCommand(this))

        getServer().getPluginManager().registerEvents(ChatListener(this), this)

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI found and hooked!")
            RemmyChatPlaceholders(this).register()
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.")
        }

        getLogger().info("RemmyChat has been enabled!")
    }

    override fun onDisable() {
        if (chatService != null) {
            chatService!!.saveAllUsers()
        }

        if (databaseManager != null) {
            databaseManager!!.close()
        }

        getLogger().info("RemmyChat has been disabled!")
    }

    fun debugLog(message: String?) {
        if (getConfig().getBoolean("debug.enabled", false)) {
            getLogger().info("[DEBUG] " + message)
        }
    }

    companion object {
        var instance: RemmyChat? = null
            private set
    }
}
