package com.noximity.remmyChat.config;

import com.noximity.remmyChat.RemmyChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Messages {

    private final RemmyChat plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public Messages(RemmyChat plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (InputStream defaultStream = plugin.getResource("messages.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defaultConfig);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load default messages.yml: " + e.getMessage());
        }
    }

    public void reloadMessages() {
        loadMessages();
    }

    public String getMessage(String path) {
        return messagesConfig.getString(path, "Message not found: " + path);
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
}