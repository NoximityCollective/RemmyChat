package com.noximity.remmyChat;

import com.noximity.remmyChat.commands.ChatCommand;
import com.noximity.remmyChat.commands.MessageCommand;
import com.noximity.remmyChat.commands.MsgToggleCommand;
import com.noximity.remmyChat.commands.ReplyCommand;
import com.noximity.remmyChat.commands.SocialSpyCommand;
import com.noximity.remmyChat.config.ConfigManager;
import com.noximity.remmyChat.config.Messages;
import com.noximity.remmyChat.database.DatabaseManager;
import com.noximity.remmyChat.listeners.ChatListener;
import com.noximity.remmyChat.services.ChatService;
import com.noximity.remmyChat.services.FormatService;
import com.noximity.remmyChat.services.PermissionService;
import com.noximity.remmyChat.utils.MessageUtils;
import com.noximity.remmyChat.utils.PlaceholderManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RemmyChat extends JavaPlugin {

    private static RemmyChat instance;
    private ConfigManager configManager;
    private Messages messages;
    private ChatService chatService;
    private FormatService formatService;
    private DatabaseManager databaseManager;
    private PermissionService permissionService;
    private PlaceholderManager placeholderManager;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.databaseManager = new DatabaseManager(this);

        this.permissionService = new PermissionService(this);
        this.formatService = new FormatService(this);
        this.chatService = new ChatService(this);
        this.placeholderManager = new PlaceholderManager(this);

        getCommand("remchat").setExecutor(new ChatCommand(this));
        getCommand("msg").setExecutor(new MessageCommand(this));
        getCommand("reply").setExecutor(new ReplyCommand(this));
        getCommand("msgtoggle").setExecutor(new MsgToggleCommand(this));
        getCommand("socialspy").setExecutor(new SocialSpyCommand(this));

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI found and hooked!");
            new RemmyChatPlaceholders(this).register();
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.");
        }

        getLogger().info("RemmyChat has been enabled!");
    }

    @Override
    public void onDisable() {
        if (chatService != null) {
            chatService.saveAllUsers();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("RemmyChat has been disabled!");
    }

    public static RemmyChat getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public ChatService getChatService() {
        return chatService;
    }

    public FormatService getFormatService() {
        return formatService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }
}
