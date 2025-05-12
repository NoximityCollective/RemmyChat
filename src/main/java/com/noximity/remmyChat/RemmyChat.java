package com.noximity.remmyChat;

import com.noximity.remmyChat.commands.ChatCommand;
import com.noximity.remmyChat.commands.MessageCommand;
import com.noximity.remmyChat.commands.ReplyCommand;
import com.noximity.remmyChat.config.ConfigManager;
import com.noximity.remmyChat.config.Messages;
import com.noximity.remmyChat.listeners.ChatListener;
import com.noximity.remmyChat.services.ChatService;
import com.noximity.remmyChat.services.FormatService;
import com.noximity.remmyChat.utils.MessageUtils;
import org.bukkit.plugin.java.JavaPlugin;

public final class RemmyChat extends JavaPlugin {

    private static RemmyChat instance;
    private ConfigManager configManager;
    private Messages messages;
    private ChatService chatService;
    private FormatService formatService;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);

        this.formatService = new FormatService(this);
        this.chatService = new ChatService(this);

        getCommand("remchat").setExecutor(new ChatCommand(this));
        getCommand("msg").setExecutor(new MessageCommand(this));
        getCommand("reply").setExecutor(new ReplyCommand(this));

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI found and hooked!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.");
        }

        getLogger().info("RemmyChat has been enabled!");
    }

    @Override
    public void onDisable() {
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
}