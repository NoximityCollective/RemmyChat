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
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        this.databaseFile = File(plugin.dataFolder, dbName)

        try {
            Class.forName("org.sqlite.JDBC")
            val url = "jdbc:sqlite:" + databaseFile!!.absolutePath

            connection = DriverManager.getConnection(url)
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = DELETE;")
                stmt.execute("PRAGMA foreign_keys = ON;")
            }
            createTables()
            plugin.logger.info("SQLite database connection established at: " + databaseFile!!.absolutePath)
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize SQLite database: ${e.message}", e)
        } catch (e: ClassNotFoundException) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize SQLite database: ${e.message}", e)
        }
    }

    private fun createTables() {
        try {
            connection!!.createStatement().use { statement ->
                // Users table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(16), " +
                            "msg_toggle BOOLEAN DEFAULT 1, " +
                            "social_spy BOOLEAN DEFAULT 0, " +
                            "current_channel VARCHAR(32) DEFAULT 'global', " +
                            "last_seen BIGINT DEFAULT 0, " +
                            "join_date BIGINT DEFAULT 0)"
                )

                // Security violations table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS security_violations (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16), " +
                            "violation_type VARCHAR(32) NOT NULL, " +
                            "severity VARCHAR(16) NOT NULL, " +
                            "message TEXT, " +
                            "timestamp BIGINT NOT NULL, " +
                            "ip_address VARCHAR(45), " +
                            "resolved BOOLEAN DEFAULT 0)"
                )

                // Moderation logs table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS moderation_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "target_uuid VARCHAR(36) NOT NULL, " +
                            "target_name VARCHAR(16), " +
                            "moderator_uuid VARCHAR(36), " +
                            "moderator_name VARCHAR(16), " +
                            "action_type VARCHAR(32) NOT NULL, " +
                            "reason TEXT, " +
                            "duration BIGINT DEFAULT 0, " +
                            "timestamp BIGINT NOT NULL, " +
                            "active BOOLEAN DEFAULT 1)"
                )

                // Message history table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS message_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16), " +
                            "channel VARCHAR(32) NOT NULL, " +
                            "message TEXT NOT NULL, " +
                            "timestamp BIGINT NOT NULL, " +
                            "server_name VARCHAR(32))"
                )

                // Performance metrics table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS performance_metrics (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "metric_type VARCHAR(32) NOT NULL, " +
                            "metric_value REAL NOT NULL, " +
                            "timestamp BIGINT NOT NULL, " +
                            "server_name VARCHAR(32))"
                )

                // Maintenance logs table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS maintenance_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "operation_type VARCHAR(32) NOT NULL, " +
                            "description TEXT, " +
                            "status VARCHAR(16) NOT NULL, " +
                            "start_time BIGINT NOT NULL, " +
                            "end_time BIGINT, " +
                            "duration BIGINT, " +
                            "records_affected INTEGER DEFAULT 0)"
                )

                // Player mutes table
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_mutes (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(16), " +
                            "muted_by VARCHAR(36), " +
                            "reason TEXT, " +
                            "start_time BIGINT NOT NULL, " +
                            "end_time BIGINT, " +
                            "permanent BOOLEAN DEFAULT 0, " +
                            "active BOOLEAN DEFAULT 1)"
                )

                // Add missing columns to existing users table
                try {
                    statement.execute("ALTER TABLE users ADD COLUMN player_name VARCHAR(16)")
                } catch (e: SQLException) {
                    // Column already exists, ignore
                }
                try {
                    statement.execute("ALTER TABLE users ADD COLUMN last_seen BIGINT DEFAULT 0")
                } catch (e: SQLException) {
                    // Column already exists, ignore
                }
                try {
                    statement.execute("ALTER TABLE users ADD COLUMN join_date BIGINT DEFAULT 0")
                } catch (e: SQLException) {
                    // Column already exists, ignore
                }

                // Create indexes for better performance
                statement.execute("CREATE INDEX IF NOT EXISTS idx_security_violations_uuid ON security_violations(uuid)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_security_violations_timestamp ON security_violations(timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_moderation_logs_target ON moderation_logs(target_uuid)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_moderation_logs_timestamp ON moderation_logs(timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_message_history_uuid ON message_history(uuid)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_message_history_timestamp ON message_history(timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_performance_metrics_timestamp ON performance_metrics(timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(last_seen)")

                plugin.logger.info("Database tables created or already exist")
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to create database tables", e)
        }
    }

    private fun ensureConnection(): Boolean {
        try {
            if (connection == null || connection!!.isClosed) {
                val url = "jdbc:sqlite:" + databaseFile!!.absolutePath
                connection = DriverManager.getConnection(url)
                plugin.logger.info("Reconnected to database")
                return true
            }
            return true
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to reconnect to database", e)
            return false
        }
    }

    fun getConnection(): Connection? {
        return if (ensureConnection()) connection else null
    }

    fun close() {
        try {
            if (connection != null && !connection!!.isClosed) {
                connection!!.close()
                plugin.logger.info("Database connection closed")
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to close database connection", e)
        }
    }

    fun saveUserPreferences(user: ChatUser) {
        if (plugin.isEnabled) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                saveUserPreferencesSync(user)
            })
        } else {
            try {
                if (connection != null && !connection!!.isClosed) {
                    connection!!.close()
                }
                ensureConnection()
                saveUserPreferencesSync(user)
            } catch (e: SQLException) {
                plugin.logger.log(Level.WARNING, "Could not refresh connection during shutdown: ${e.message}")
            }
        }
    }

    private fun saveUserPreferencesSync(user: ChatUser) {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot save user preferences - no database connection")
            return
        }

        try {
            // Get player name if available
            val playerName = Bukkit.getPlayer(user.uuid)?.name

            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO users (uuid, player_name, msg_toggle, social_spy, current_channel) VALUES (?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, user.uuid.toString())
                ps.setString(2, playerName)
                ps.setBoolean(3, user.isMsgToggle)
                ps.setBoolean(4, user.isSocialSpy)
                ps.setString(5, user.currentChannel)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save user preferences: ${e.message}", e)
        }
    }

    fun loadUserPreferences(uuid: UUID, defaultChannel: String): ChatUser? {
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
                            if (savedChannel != null && savedChannel.isNotEmpty()) savedChannel else defaultChannel

                        return ChatUser(uuid, channelToUse, msgToggle, socialSpy)
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to load user preferences", e)
        }

        return ChatUser(uuid, defaultChannel)
    }

    /**
     * Get user by player name (for cross-server messaging)
     * Returns ChatUser with UUID if found, null otherwise
     */
    fun getUserByName(playerName: String): ChatUser? {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot get user by name - no database connection")
            return null
        }

        try {
            connection!!.prepareStatement(
                "SELECT uuid, msg_toggle, social_spy, current_channel FROM users WHERE LOWER(player_name) = LOWER(?) LIMIT 1"
            ).use { ps ->
                ps.setString(1, playerName)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val uuid = UUID.fromString(rs.getString("uuid"))
                        val msgToggle = rs.getBoolean("msg_toggle")
                        val socialSpy = rs.getBoolean("social_spy")
                        val currentChannel = rs.getString("current_channel") ?: "global"

                        return ChatUser(uuid, currentChannel, msgToggle, socialSpy)
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to get user by name: ${e.message}", e)
        }

        return null
    }

    /**
     * Check if database connection is active
     */
    fun isConnected(): Boolean {
        return try {
            connection != null && !connection!!.isClosed
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Sync data to database
     */
    fun syncData() {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot sync data - no database connection")
            return
        }

        try {
            connection!!.createStatement().use { statement ->
                statement.execute("PRAGMA synchronous = FULL")
                statement.execute("PRAGMA optimize")
            }
            plugin.debugLog("Database sync completed")
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to sync database: ${e.message}", e)
        }
    }

    /**
     * Clean up old message history
     */
    fun cleanupOldMessages(retentionDays: Int): Int {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot cleanup messages - no database connection")
            return 0
        }

        try {
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            connection!!.prepareStatement(
                "DELETE FROM message_history WHERE timestamp < ?"
            ).use { ps ->
                ps.setLong(1, cutoffTime)
                val deletedCount = ps.executeUpdate()

                // Also cleanup old performance metrics
                connection!!.prepareStatement(
                    "DELETE FROM performance_metrics WHERE timestamp < ?"
                ).use { ps2 ->
                    ps2.setLong(1, cutoffTime)
                    ps2.executeUpdate()
                }

                return deletedCount
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to cleanup old messages: ${e.message}", e)
            return 0
        }
    }

    /**
     * Clean up old player data for inactive players
     */
    fun cleanupOldPlayerData(retentionDays: Int): Int {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot cleanup player data - no database connection")
            return 0
        }

        try {
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)

            // Get list of inactive players
            val inactiveUUIDs = mutableListOf<String>()
            connection!!.prepareStatement(
                "SELECT uuid FROM users WHERE last_seen < ? AND last_seen > 0"
            ).use { ps ->
                ps.setLong(1, cutoffTime)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        inactiveUUIDs.add(rs.getString("uuid"))
                    }
                }
            }

            var totalDeleted = 0
            for (uuid in inactiveUUIDs) {
                // Delete from security violations
                connection!!.prepareStatement(
                    "DELETE FROM security_violations WHERE uuid = ?"
                ).use { ps ->
                    ps.setString(1, uuid)
                    totalDeleted += ps.executeUpdate()
                }

                // Delete from message history
                connection!!.prepareStatement(
                    "DELETE FROM message_history WHERE uuid = ?"
                ).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeUpdate()
                }

                // Delete from moderation logs (as target)
                connection!!.prepareStatement(
                    "DELETE FROM moderation_logs WHERE target_uuid = ?"
                ).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeUpdate()
                }

                // Delete from users table
                connection!!.prepareStatement(
                    "DELETE FROM users WHERE uuid = ?"
                ).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeUpdate()
                }
            }

            return inactiveUUIDs.size
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to cleanup player data: ${e.message}", e)
            return 0
        }
    }

    /**
     * Perform database maintenance operations
     */
    fun performMaintenance() {
        if (!ensureConnection()) {
            plugin.logger.warning("Cannot perform maintenance - no database connection")
            return
        }

        try {
            val startTime = System.currentTimeMillis()

            connection!!.createStatement().use { statement ->
                // Analyze tables for query optimization
                statement.execute("ANALYZE")

                // Vacuum database to reclaim space
                statement.execute("VACUUM")

                // Update table statistics
                statement.execute("PRAGMA optimize")

                // Integrity check
                val result = statement.executeQuery("PRAGMA integrity_check")
                if (result.next() && result.getString(1) != "ok") {
                    plugin.logger.warning("Database integrity check failed: ${result.getString(1)}")
                }
            }

            val duration = System.currentTimeMillis() - startTime

            // Log maintenance operation
            logMaintenanceOperation("DATABASE_MAINTENANCE", "Full database maintenance",
                "SUCCESS", startTime, duration, 0)

            plugin.debugLog("Database maintenance completed in ${duration}ms")
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to perform database maintenance: ${e.message}", e)
        }
    }

    /**
     * Save security violation
     */
    fun saveSecurityViolation(uuid: UUID, playerName: String?, violationType: String,
                            severity: String, message: String?, ipAddress: String?) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT INTO security_violations (uuid, player_name, violation_type, severity, message, timestamp, ip_address) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, playerName)
                ps.setString(3, violationType)
                ps.setString(4, severity)
                ps.setString(5, message)
                ps.setLong(6, System.currentTimeMillis())
                ps.setString(7, ipAddress)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save security violation: ${e.message}", e)
        }
    }

    /**
     * Save moderation action
     */
    fun saveModerationAction(targetUuid: UUID, targetName: String?, moderatorUuid: UUID?,
                           moderatorName: String?, actionType: String, reason: String?, duration: Long) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT INTO moderation_logs (target_uuid, target_name, moderator_uuid, moderator_name, " +
                "action_type, reason, duration, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, targetUuid.toString())
                ps.setString(2, targetName)
                ps.setString(3, moderatorUuid?.toString())
                ps.setString(4, moderatorName)
                ps.setString(5, actionType)
                ps.setString(6, reason)
                ps.setLong(7, duration)
                ps.setLong(8, System.currentTimeMillis())
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save moderation action: ${e.message}", e)
        }
    }

    /**
     * Save message to history
     */
    fun saveMessageHistory(uuid: UUID, playerName: String?, channel: String, message: String, serverName: String?) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT INTO message_history (uuid, player_name, channel, message, timestamp, server_name) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, playerName)
                ps.setString(3, channel)
                ps.setString(4, message)
                ps.setLong(5, System.currentTimeMillis())
                ps.setString(6, serverName)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save message history: ${e.message}", e)
        }
    }

    /**
     * Save performance metric
     */
    fun savePerformanceMetric(metricType: String, value: Double, serverName: String?) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT INTO performance_metrics (metric_type, metric_value, timestamp, server_name) " +
                "VALUES (?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, metricType)
                ps.setDouble(2, value)
                ps.setLong(3, System.currentTimeMillis())
                ps.setString(4, serverName)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save performance metric: ${e.message}", e)
        }
    }

    /**
     * Log maintenance operation
     */
    fun logMaintenanceOperation(operationType: String, description: String, status: String,
                              startTime: Long, duration: Long, recordsAffected: Int) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT INTO maintenance_logs (operation_type, description, status, start_time, end_time, duration, records_affected) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, operationType)
                ps.setString(2, description)
                ps.setString(3, status)
                ps.setLong(4, startTime)
                ps.setLong(5, startTime + duration)
                ps.setLong(6, duration)
                ps.setInt(7, recordsAffected)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to log maintenance operation: ${e.message}", e)
        }
    }

    /**
     * Update player last seen time
     */
    fun updatePlayerLastSeen(uuid: UUID, playerName: String) {
        if (!ensureConnection()) return

        try {
            val currentTime = System.currentTimeMillis()
            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO users (uuid, player_name, last_seen, join_date, msg_toggle, social_spy, current_channel) " +
                "VALUES (?, ?, ?, COALESCE((SELECT join_date FROM users WHERE uuid = ?), ?), " +
                "COALESCE((SELECT msg_toggle FROM users WHERE uuid = ?), 1), " +
                "COALESCE((SELECT social_spy FROM users WHERE uuid = ?), 0), " +
                "COALESCE((SELECT current_channel FROM users WHERE uuid = ?), 'global'))"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, playerName)
                ps.setLong(3, currentTime)
                ps.setString(4, uuid.toString())
                ps.setLong(5, currentTime)
                ps.setString(6, uuid.toString())
                ps.setString(7, uuid.toString())
                ps.setString(8, uuid.toString())
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to update player last seen: ${e.message}", e)
        }
    }

    /**
     * Get player mute status
     */
    fun getPlayerMute(uuid: UUID): MuteInfo? {
        if (!ensureConnection()) return null

        try {
            connection!!.prepareStatement(
                "SELECT * FROM player_mutes WHERE uuid = ? AND active = 1 AND (permanent = 1 OR end_time > ?)"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setLong(2, System.currentTimeMillis())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return MuteInfo(
                            uuid = uuid,
                            playerName = rs.getString("player_name"),
                            mutedBy = rs.getString("muted_by")?.let { UUID.fromString(it) },
                            reason = rs.getString("reason"),
                            startTime = rs.getLong("start_time"),
                            endTime = if (rs.getBoolean("permanent")) -1 else rs.getLong("end_time"),
                            permanent = rs.getBoolean("permanent")
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to get player mute: ${e.message}", e)
        }
        return null
    }

    /**
     * Save player mute
     */
    fun savePlayerMute(muteInfo: MuteInfo) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO player_mutes (uuid, player_name, muted_by, reason, start_time, end_time, permanent, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
            ).use { ps ->
                ps.setString(1, muteInfo.uuid.toString())
                ps.setString(2, muteInfo.playerName)
                ps.setString(3, muteInfo.mutedBy?.toString())
                ps.setString(4, muteInfo.reason)
                ps.setLong(5, muteInfo.startTime)
                ps.setLong(6, if (muteInfo.permanent) -1 else muteInfo.endTime)
                ps.setBoolean(7, muteInfo.permanent)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save player mute: ${e.message}", e)
        }
    }

    /**
     * Remove player mute
     */
    fun removePlayerMute(uuid: UUID) {
        if (!ensureConnection()) return

        try {
            connection!!.prepareStatement(
                "UPDATE player_mutes SET active = 0 WHERE uuid = ?"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to remove player mute: ${e.message}", e)
        }
    }

    /**
     * Data class for mute information
     */
    data class MuteInfo(
        val uuid: UUID,
        val playerName: String?,
        val mutedBy: UUID?,
        val reason: String?,
        val startTime: Long,
        val endTime: Long,
        val permanent: Boolean
    )

}
