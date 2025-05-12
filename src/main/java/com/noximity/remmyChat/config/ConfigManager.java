package com.noximity.remmyChat.config;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.Channel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final RemmyChat plugin;
    private FileConfiguration config;
    private final Map<String, Channel> channels = new HashMap<>();
    private boolean urlFormattingEnabled;

    public ConfigManager(RemmyChat plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        loadChannels();
        loadUrlFormatting();
    }

    private void loadChannels() {
        ConfigurationSection channelsSection = config.getConfigurationSection("channels");
        if (channelsSection == null) {
            plugin.getLogger().warning("No channels configured!");
            return;
        }

        for (String key : channelsSection.getKeys(false)) {
            String format = channelsSection.getString(key + ".format");
            String permission = channelsSection.getString(key + ".permission");
            double radius = channelsSection.getDouble(key + ".radius", -1);

            Channel channel = new Channel(key, format, permission, radius);
            channels.put(key, channel);

            plugin.getLogger().info("Loaded channel: " + key);
        }
    }

    private void loadUrlFormatting() {
        this.urlFormattingEnabled = config.getBoolean("url-formatting.enabled", true);

        if (!config.isSet("url-formatting.color")) {
            config.set("url-formatting.color", "#3498DB");
        }

        if (!config.isSet("url-formatting.underline")) {
            config.set("url-formatting.underline", true);
        }

        if (!config.isSet("url-formatting.hover")) {
            config.set("url-formatting.hover", true);
        }

        if (!config.isSet("url-formatting.hover-text")) {
            config.set("url-formatting.hover-text", "<#AAAAAA>Click to open</hover-text>");
        }

        plugin.saveConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        channels.clear();
        loadChannels();
        loadUrlFormatting();
    }

    public boolean isPlayerFormattingAllowed() {
        return config.getBoolean("features.player-formatting", false);
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public Channel getChannel(String name) {
        return channels.get(name);
    }

    public Channel getDefaultChannel() {
        String defaultChannel = config.getString("default-channel");
        return channels.getOrDefault(defaultChannel, null);
    }

    public boolean isFormatHoverEnabled() {
        return config.getBoolean("features.format-hover", true);
    }

    public boolean isLinkClickEnabled() {
        return urlFormattingEnabled;
    }

    public int getCooldown() {
        return config.getInt("chat-cooldown", 0);
    }
}