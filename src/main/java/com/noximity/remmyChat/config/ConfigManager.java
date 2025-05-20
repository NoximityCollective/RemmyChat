package com.noximity.remmyChat.config;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.Channel;
import com.noximity.remmyChat.models.GroupFormat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final RemmyChat plugin;
    private FileConfiguration config;
    private final Map<String, Channel> channels = new HashMap<>();
    private final Map<String, GroupFormat> groupFormats = new HashMap<>();
    private final Map<String, String> hoverTemplates = new HashMap<>();
    private final Map<String, String> channelPrefixTemplates = new HashMap<>();
    private final Map<String, String> groupPrefixTemplates = new HashMap<>();
    private final Map<String, String> nameStyleTemplates = new HashMap<>();
    private boolean urlFormattingEnabled;
    private boolean useGroupFormat;
    private boolean allowSelfMessaging;
    private String chatFormat;
    private boolean debugEnabled;
    private boolean verboseStartup;

    public ConfigManager(RemmyChat plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        // Load debug settings first
        this.debugEnabled = config.getBoolean("debug.enabled", false);
        this.verboseStartup = !config.isSet("debug.verbose-startup") || config.getBoolean("debug.verbose-startup", true);

        loadTemplates();
        loadChannels();
        loadGroupFormats();
        loadUrlFormatting();
        this.useGroupFormat = config.getBoolean("features.use-group-format", true);
        this.allowSelfMessaging = config.getBoolean("features.allow-self-messaging", false);
        this.chatFormat = config.getString("chat-format", "%channel_prefix% %group_prefix%%name%: %message%");
    }

    private void loadTemplates() {
        // Load hover templates
        ConfigurationSection hoversSection = config.getConfigurationSection("templates.hovers");
        if (hoversSection != null) {
            for (String key : hoversSection.getKeys(false)) {
                String template = hoversSection.getString(key);
                if (template != null) {
                    hoverTemplates.put(key, template);
                    if (verboseStartup) {
                        plugin.getLogger().info("Loaded hover template: " + key);
                    }
                }
            }
        }

        // Load channel prefix templates
        ConfigurationSection channelPrefixesSection = config.getConfigurationSection("templates.channel-prefixes");
        if (channelPrefixesSection != null) {
            for (String key : channelPrefixesSection.getKeys(false)) {
                String template = channelPrefixesSection.getString(key);
                if (template != null) {
                    channelPrefixTemplates.put(key, template);
                    if (verboseStartup) {
                        plugin.getLogger().info("Loaded channel prefix template: " + key);
                    }
                }
            }
        }

        // Load group prefix templates
        ConfigurationSection groupPrefixesSection = config.getConfigurationSection("templates.group-prefixes");
        if (groupPrefixesSection != null) {
            for (String key : groupPrefixesSection.getKeys(false)) {
                String template = groupPrefixesSection.getString(key);
                if (template != null) {
                    groupPrefixTemplates.put(key, template);
                    if (verboseStartup) {
                        plugin.getLogger().info("Loaded group prefix template: " + key);
                    }
                }
            }
        }

        // Load name style templates
        ConfigurationSection nameStylesSection = config.getConfigurationSection("templates.name-styles");
        if (nameStylesSection != null) {
            for (String key : nameStylesSection.getKeys(false)) {
                String template = nameStylesSection.getString(key);
                if (template != null) {
                    nameStyleTemplates.put(key, template);
                    if (verboseStartup) {
                        plugin.getLogger().info("Loaded name style template: " + key);
                    }
                }
            }
        }
    }

    private void loadChannels() {
        ConfigurationSection channelsSection = config.getConfigurationSection("channels");
        if (channelsSection == null) {
            plugin.getLogger().warning("No channels configured!");
            return;
        }

        for (String key : channelsSection.getKeys(false)) {
            String permission = channelsSection.getString(key + ".permission", "");
            double radius = channelsSection.getDouble(key + ".radius", -1);
            String prefix = channelsSection.getString(key + ".prefix", "");
            String hover = channelsSection.getString(key + ".hover", "player-info");
            String displayName = channelsSection.getString(key + ".display-name", "");

            Channel channel = new Channel(key, permission, radius, prefix, hover, displayName);
            channels.put(key, channel);

            if (verboseStartup) {
                plugin.getLogger().info("Loaded channel: " + key + (displayName.isEmpty() ? "" : " with display name: " + displayName));
            }
        }
    }

    private void loadGroupFormats() {
        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        if (groupsSection == null) {
            plugin.getLogger().info("No group formats configured, using default name styles only.");
            return;
        }

        for (String key : groupsSection.getKeys(false)) {
            String nameStyle = groupsSection.getString(key + ".name-style", "default");
            String prefix = groupsSection.getString(key + ".prefix", "");
            String format = groupsSection.getString(key + ".format", "");

            // Debug information
            if (debugEnabled || verboseStartup) {
                plugin.getLogger().info("Loading group format for " + key + ":");
                plugin.getLogger().info("  - name-style: " + nameStyle);
                plugin.getLogger().info("  - prefix: '" + prefix + "'");
                plugin.getLogger().info("  - format: '" + format + "'");
            }

            GroupFormat groupFormat = new GroupFormat(key, nameStyle, prefix, format);
            groupFormats.put(key, groupFormat);

            if (verboseStartup) {
                plugin.getLogger().info("Loaded group format: " + key);
            }
        }
    }

    private void loadUrlFormatting() {
        this.urlFormattingEnabled = config.getBoolean("url-formatting.enabled", true);
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Reload debug settings first
        this.debugEnabled = config.getBoolean("debug.enabled", false);
        this.verboseStartup = !config.isSet("debug.verbose-startup") || config.getBoolean("debug.verbose-startup", true);

        channels.clear();
        groupFormats.clear();
        hoverTemplates.clear();
        channelPrefixTemplates.clear();
        groupPrefixTemplates.clear();
        nameStyleTemplates.clear();

        loadTemplates();
        loadChannels();
        loadGroupFormats();
        loadUrlFormatting();
        this.useGroupFormat = config.getBoolean("features.use-group-format", true);
        this.allowSelfMessaging = config.getBoolean("features.allow-self-messaging", false);
        this.chatFormat = config.getString("chat-format", "%channel_prefix% %group_prefix%%name%: %message%");

        // Reload placeholders
        plugin.getPlaceholderManager().loadCustomPlaceholders();
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

    public GroupFormat getGroupFormat(String name) {
        return groupFormats.get(name);
    }

    public Map<String, GroupFormat> getGroupFormats() {
        return groupFormats;
    }

    public String getHoverTemplate(String name) {
        return hoverTemplates.getOrDefault(name, "");
    }

    public String getChannelPrefixTemplate(String name) {
        return channelPrefixTemplates.getOrDefault(name, "");
    }

    public String getGroupPrefixTemplate(String name) {
        return groupPrefixTemplates.getOrDefault(name, "");
    }

    public String getNameStyleTemplate(String name) {
        return nameStyleTemplates.getOrDefault(name, nameStyleTemplates.getOrDefault("default", "<#4A90E2>%player_name%"));
    }

    public String getChatFormat() {
        return chatFormat;
    }

    public boolean isUseGroupFormat() {
        return useGroupFormat;
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

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isAllowSelfMessaging() {
        return allowSelfMessaging;
    }
}
