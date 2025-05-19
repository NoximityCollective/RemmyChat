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
    private File databaseFile;

    public DatabaseManager(RemmyChat plugin) {
        this.plugin = plugin;
        this.initialize();
    }

    private void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.databaseFile = new File(plugin.getDataFolder(), dbName);

        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = DELETE;");
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            createTables();
            plugin.getLogger().info("SQLite database connection established at: " + databaseFile.getAbsolutePath());
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database: " + e.getMessage(), e);
        }
    }

    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "msg_toggle BOOLEAN DEFAULT 1, " +
                    "social_spy BOOLEAN DEFAULT 0, " +
                    "current_channel VARCHAR(32) DEFAULT 'global')");

            plugin.getLogger().info("Database tables created or already exist");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    private boolean ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                plugin.getLogger().info("Reconnected to database");
                return true;
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reconnect to database", e);
            return false;
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
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                saveUserPreferencesSync(user);
            });
        } else {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                ensureConnection();
                saveUserPreferencesSync(user);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh connection during shutdown: " + e.getMessage());
            }
        }
    }

    private void saveUserPreferencesSync(ChatUser user) {
        if (!ensureConnection()) {
            plugin.getLogger().warning("Cannot save user preferences - no database connection");
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO users (uuid, msg_toggle, social_spy, current_channel) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, user.getUuid().toString());
            ps.setBoolean(2, user.isMsgToggle());
            ps.setBoolean(3, user.isSocialSpy());
            ps.setString(4, user.getCurrentChannel());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save user preferences: " + e.getMessage(), e);
        }
    }

    public ChatUser loadUserPreferences(UUID uuid, String defaultChannel) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT msg_toggle, social_spy, current_channel FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean msgToggle = rs.getBoolean("msg_toggle");
                    boolean socialSpy = rs.getBoolean("social_spy");
                    String savedChannel = rs.getString("current_channel");

                    String channelToUse = savedChannel != null && !savedChannel.isEmpty() ?
                            savedChannel : defaultChannel;

                    return new ChatUser(uuid, channelToUse, msgToggle, socialSpy);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load user preferences", e);
        }

        return new ChatUser(uuid, defaultChannel);
    }
}
