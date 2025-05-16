package com.noximity.remmyChat.database;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final RemmyChat plugin;
    private Connection connection;
    private final String dbName = "remmychat.db";

    public DatabaseManager(RemmyChat plugin) {
        this.plugin = plugin;
        this.initialize();
    }

    private void initialize() {
        File dataFolder = new File(plugin.getDataFolder(), dbName);
        if (!dataFolder.exists()) {
            try {
                plugin.getDataFolder().mkdir();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create plugin data folder", e);
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);

            createTables();
            plugin.getLogger().info("SQLite database connection established!");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
        }
    }

    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "msg_toggle BOOLEAN DEFAULT 1, " +
                    "social_spy BOOLEAN DEFAULT 0)");

            plugin.getLogger().info("Database tables created or already exist");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }

    public void saveUserPreferences(ChatUser user) {
        // Run asynchronously if the plugin is enabled, otherwise run synchronously
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                saveUserPreferencesSync(user);
            });
        } else {
            saveUserPreferencesSync(user);
        }
    }

    private void saveUserPreferencesSync(ChatUser user) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO users (uuid, msg_toggle, social_spy) VALUES (?, ?, ?)")) {
            ps.setString(1, user.getUuid().toString());
            ps.setBoolean(2, user.isMsgToggle());
            ps.setBoolean(3, user.isSocialSpy());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save user preferences", e);
        }
    }

    public ChatUser loadUserPreferences(UUID uuid, String defaultChannel) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT msg_toggle, social_spy FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean msgToggle = rs.getBoolean("msg_toggle");
                    boolean socialSpy = rs.getBoolean("social_spy");
                    return new ChatUser(uuid, defaultChannel, msgToggle, socialSpy);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load user preferences", e);
        }

        // Return default user if not found in database
        return new ChatUser(uuid, defaultChannel);
    }
}
