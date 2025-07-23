package com.noximity.remmyChat.database

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.ChatUser
import org.bukkit.Bukkit
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*
import java.util.logging.Level

class DatabaseManager(private val plugin: RemmyChat) {
    private var connection: Connection? = null
    private val dbName = "remmychat.db"
    private var databaseFile: File? = null

    init {
        this.initialize()
    }

    private fun initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs()
        }

        this.databaseFile = File(plugin.getDataFolder(), dbName)

        try {
            Class.forName("org.sqlite.JDBC")
            val url = "jdbc:sqlite:" + databaseFile!!.getAbsolutePath()

            connection = DriverManager.getConnection(url)
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = DELETE;")
                stmt.execute("PRAGMA foreign_keys = ON;")
            }
            createTables()
            plugin.getLogger().info("SQLite database connection established at: " + databaseFile!!.getAbsolutePath())
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database: " + e.message, e)
        } catch (e: ClassNotFoundException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database: " + e.message, e)
        }
    }

    private fun createTables() {
        try {
            connection!!.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "msg_toggle BOOLEAN DEFAULT 1, " +
                            "social_spy BOOLEAN DEFAULT 0, " +
                            "current_channel VARCHAR(32) DEFAULT 'global')"
                )
                plugin.getLogger().info("Database tables created or already exist")
            }
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e)
        }
    }

    private fun ensureConnection(): Boolean {
        try {
            if (connection == null || connection!!.isClosed()) {
                val url = "jdbc:sqlite:" + databaseFile!!.getAbsolutePath()
                connection = DriverManager.getConnection(url)
                plugin.getLogger().info("Reconnected to database")
                return true
            }
            return true
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reconnect to database", e)
            return false
        }
    }

    fun close() {
        try {
            if (connection != null && !connection!!.isClosed()) {
                connection!!.close()
                plugin.getLogger().info("Database connection closed")
            }
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e)
        }
    }

    fun saveUserPreferences(user: ChatUser) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                saveUserPreferencesSync(user)
            })
        } else {
            try {
                if (connection != null && !connection!!.isClosed()) {
                    connection!!.close()
                }
                ensureConnection()
                saveUserPreferencesSync(user)
            } catch (e: SQLException) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh connection during shutdown: " + e.message)
            }
        }
    }

    private fun saveUserPreferencesSync(user: ChatUser) {
        if (!ensureConnection()) {
            plugin.getLogger().warning("Cannot save user preferences - no database connection")
            return
        }

        try {
            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO users (uuid, msg_toggle, social_spy, current_channel) VALUES (?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, user.getUuid().toString())
                ps.setBoolean(2, user.isMsgToggle())
                ps.setBoolean(3, user.isSocialSpy())
                ps.setString(4, user.getCurrentChannel())
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save user preferences: " + e.message, e)
        }
    }

    fun loadUserPreferences(uuid: UUID, defaultChannel: String?): ChatUser {
        try {
            connection!!.prepareStatement(
                "SELECT msg_toggle, social_spy, current_channel FROM users WHERE uuid = ?"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val msgToggle: Boolean = rs.getBoolean("msg_toggle")
                        val socialSpy: Boolean = rs.getBoolean("social_spy")
                        val savedChannel: String? = rs.getString("current_channel")

                        val channelToUse =
                            if (savedChannel != null && !savedChannel.isEmpty()) savedChannel else defaultChannel

                        return ChatUser(uuid, channelToUse, msgToggle, socialSpy)
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load user preferences", e)
        }

        return ChatUser(uuid, defaultChannel)
    }
}
