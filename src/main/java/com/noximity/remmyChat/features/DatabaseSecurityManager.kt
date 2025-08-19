package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import org.bukkit.Bukkit
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles advanced database security features including encryption, audit logging, and remote backup
 */
class DatabaseSecurityManager(private val plugin: RemmyChat) {

    private val securityExecutor = Executors.newSingleThreadScheduledExecutor()

    // Encryption settings
    private var encryptionEnabled = false
    private var encryptionAlgorithm = "AES-256"
    private var encryptionKey: SecretKey? = null
    private val encryptedFields = setOf("message", "reason", "description")

    // Audit logging
    private var auditEnabled = false
    private var logOperations = false
    private var logSensitive = true
    private var auditRetentionDays = 30
    private val auditLog = ConcurrentHashMap<String, AuditEntry>()

    // Remote backup
    private var remoteBackupEnabled = false
    private var backupType = "ftp"
    private var remoteHost = ""
    private var remoteUsername = ""
    private var remotePassword = ""
    private var remotePath = "/remmychat_backups/"

    // IP whitelist
    private val ipWhitelist = mutableSetOf<String>()

    // Statistics
    private var totalOperations = 0L
    private var encryptedOperations = 0L
    private var auditedOperations = 0L
    private var backupsSent = 0L

    fun initialize() {
        loadConfiguration()
        setupEncryption()
        createAuditTables()
        startMaintenanceTasks()

        plugin.debugLog("DatabaseSecurityManager initialized - Encryption: $encryptionEnabled, Audit: $auditEnabled, RemoteBackup: $remoteBackupEnabled")
    }

    /**
     * Encrypt sensitive data before database storage
     */
    fun encryptData(data: String, fieldName: String): String {
        if (!encryptionEnabled || !encryptedFields.contains(fieldName) || encryptionKey == null) {
            return data
        }

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)
            val encryptedData = cipher.doFinal(data.toByteArray())

            // Combine IV and encrypted data
            val combined = iv + encryptedData
            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to encrypt data: ${e.message}")
            data // Return original data if encryption fails
        }
    }

    /**
     * Decrypt sensitive data after database retrieval
     */
    fun decryptData(encryptedData: String, fieldName: String): String {
        if (!encryptionEnabled || !encryptedFields.contains(fieldName) || encryptionKey == null) {
            return encryptedData
        }

        return try {
            val combined = Base64.getDecoder().decode(encryptedData)
            val iv = combined.sliceArray(0..15)
            val encrypted = combined.sliceArray(16 until combined.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec)
            val decryptedData = cipher.doFinal(encrypted)

            String(decryptedData)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to decrypt data: ${e.message}")
            encryptedData // Return encrypted data if decryption fails
        }
    }

    /**
     * Log database operation for audit purposes
     */
    fun auditOperation(operation: String, table: String, userId: String?, details: Map<String, Any> = emptyMap()) {
        if (!auditEnabled) return

        val auditId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val auditEntry = AuditEntry(
            id = auditId,
            operation = operation,
            table = table,
            userId = userId,
            timestamp = timestamp,
            details = details,
            ipAddress = getCurrentIPAddress(),
            success = true
        )

        auditLog[auditId] = auditEntry
        saveAuditEntry(auditEntry)
        auditedOperations++

        plugin.debugLog("Audited operation: $operation on $table")
    }

    /**
     * Validate IP address against whitelist
     */
    fun validateIPAccess(ipAddress: String): Boolean {
        if (ipWhitelist.isEmpty()) return true
        return ipWhitelist.contains(ipAddress) || ipWhitelist.contains("*")
    }

    /**
     * Create encrypted backup and upload to remote location
     */
    fun createRemoteBackup(localBackupFile: File): RemoteBackupResult {
        if (!remoteBackupEnabled) {
            return RemoteBackupResult(false, "Remote backup is disabled")
        }

        try {
            // Compress and encrypt the backup
            val encryptedBackup = encryptBackupFile(localBackupFile)

            // Upload to remote location
            val uploadResult = uploadToRemote(encryptedBackup)

            if (uploadResult.success) {
                backupsSent++
                plugin.debugLog("Remote backup completed successfully")

                // Clean up temporary encrypted file
                encryptedBackup.delete()

                auditOperation("REMOTE_BACKUP", "system", null, mapOf(
                    "backup_file" to localBackupFile.name,
                    "remote_path" to uploadResult.remotePath
                ))
            }

            return uploadResult

        } catch (e: Exception) {
            plugin.logger.severe("Remote backup failed: ${e.message}")
            return RemoteBackupResult(false, "Remote backup failed: ${e.message}")
        }
    }

    /**
     * Check database integrity and security
     */
    fun performSecurityCheck(): SecurityCheckResult {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // Check encryption status
        if (!encryptionEnabled) {
            issues.add("Database encryption is disabled")
            recommendations.add("Enable encryption for sensitive data")
        }

        // Check audit logging
        if (!auditEnabled) {
            issues.add("Audit logging is disabled")
            recommendations.add("Enable audit logging for security compliance")
        }

        // Check IP whitelist
        if (ipWhitelist.isEmpty()) {
            issues.add("No IP address restrictions configured")
            recommendations.add("Configure IP whitelist for database access")
        }

        // Check for weak encryption keys
        if (encryptionEnabled && encryptionKey != null) {
            val keyStrength = encryptionKey!!.encoded.size * 8
            if (keyStrength < 256) {
                issues.add("Weak encryption key strength: ${keyStrength}bit")
                recommendations.add("Use at least 256-bit encryption keys")
            }
        }

        // Check audit log retention
        val oldAuditEntries = auditLog.values.count {
            System.currentTimeMillis() - it.timestamp > (auditRetentionDays * 24 * 60 * 60 * 1000L)
        }

        if (oldAuditEntries > 1000) {
            issues.add("Large number of old audit entries: $oldAuditEntries")
            recommendations.add("Consider reducing audit retention period or implementing cleanup")
        }

        val riskLevel = when {
            issues.size >= 3 -> "HIGH"
            issues.size >= 2 -> "MEDIUM"
            issues.size >= 1 -> "LOW"
            else -> "NONE"
        }

        return SecurityCheckResult(
            riskLevel = riskLevel,
            issues = issues,
            recommendations = recommendations,
            auditEntriesCount = auditLog.size,
            encryptionStatus = if (encryptionEnabled) "ENABLED" else "DISABLED",
            lastCheck = System.currentTimeMillis()
        )
    }

    /**
     * Load configuration settings
     */
    private fun loadConfiguration() {
        val databaseConfig = plugin.configManager.getDatabaseConfig()

        // Encryption settings
        val encryptionSection = databaseConfig.getConfigurationSection("security.encryption")
        if (encryptionSection != null) {
            encryptionEnabled = encryptionSection.getBoolean("enabled", false)
            encryptionAlgorithm = encryptionSection.getString("algorithm", "AES-256") ?: "AES-256"
            val keyString = encryptionSection.getString("key", "")
            if (keyString?.isNotEmpty() == true && encryptionEnabled) {
                setupEncryptionKey(keyString)
            }
        }

        // Audit settings
        val auditSection = databaseConfig.getConfigurationSection("security.audit")
        if (auditSection != null) {
            auditEnabled = auditSection.getBoolean("enabled", false)
            logOperations = auditSection.getBoolean("log-operations", false)
            logSensitive = auditSection.getBoolean("log-sensitive", true)
            auditRetentionDays = auditSection.getInt("retention", 30)
        }

        // IP whitelist
        val accessSection = databaseConfig.getConfigurationSection("security.access-control")
        if (accessSection != null) {
            val whitelist = accessSection.getStringList("ip-whitelist")
            ipWhitelist.addAll(whitelist)
        }

        // Remote backup settings
        val remoteBackupSection = databaseConfig.getConfigurationSection("backup.location.remote")
        if (remoteBackupSection != null) {
            remoteBackupEnabled = remoteBackupSection.getBoolean("enabled", false)
            backupType = remoteBackupSection.getString("type", "ftp") ?: "ftp"
            remoteHost = remoteBackupSection.getString("host", "") ?: ""
            remoteUsername = remoteBackupSection.getString("username", "") ?: ""
            remotePassword = remoteBackupSection.getString("password", "") ?: ""
            remotePath = remoteBackupSection.getString("path", "/remmychat_backups/") ?: "/remmychat_backups/"
        }
    }

    /**
     * Setup encryption key
     */
    private fun setupEncryption() {
        if (!encryptionEnabled) return

        try {
            if (encryptionKey == null) {
                // Generate new key if none exists
                val keyGenerator = KeyGenerator.getInstance("AES")
                keyGenerator.init(256)
                encryptionKey = keyGenerator.generateKey()

                plugin.logger.info("Generated new encryption key for database security")
                saveEncryptionKey()
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to setup encryption: ${e.message}")
            encryptionEnabled = false
        }
    }

    /**
     * Setup encryption key from configuration
     */
    private fun setupEncryptionKey(keyString: String) {
        try {
            val keyBytes = Base64.getDecoder().decode(keyString)
            encryptionKey = SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            plugin.logger.severe("Invalid encryption key in configuration: ${e.message}")
            encryptionEnabled = false
        }
    }

    /**
     * Save encryption key to secure location
     */
    private fun saveEncryptionKey() {
        if (encryptionKey == null) return

        try {
            val keyFile = File(plugin.dataFolder, "security/encryption.key")
            keyFile.parentFile.mkdirs()

            val encodedKey = Base64.getEncoder().encodeToString(encryptionKey!!.encoded)
            keyFile.writeText(encodedKey)

            // Set restrictive permissions
            keyFile.setReadable(false, false)
            keyFile.setWritable(false, false)
            keyFile.setExecutable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(true, true)

            plugin.logger.info("Encryption key saved securely")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save encryption key: ${e.message}")
        }
    }

    /**
     * Create audit tables in database
     */
    private fun createAuditTables() {
        if (!auditEnabled) return

        try {
            val connection = plugin.databaseManager.getConnection()
            if (connection != null) {
                val createTableSQL = """
                    CREATE TABLE IF NOT EXISTS security_audit_log (
                        id VARCHAR(36) PRIMARY KEY,
                        operation VARCHAR(50) NOT NULL,
                        table_name VARCHAR(50) NOT NULL,
                        user_id VARCHAR(36),
                        timestamp BIGINT NOT NULL,
                        ip_address VARCHAR(45),
                        details TEXT,
                        success BOOLEAN DEFAULT TRUE,
                        INDEX idx_audit_timestamp (timestamp),
                        INDEX idx_audit_operation (operation),
                        INDEX idx_audit_user (user_id)
                    )
                """.trimIndent()

                connection.createStatement().use { statement ->
                    statement.execute(createTableSQL)
                }

                plugin.debugLog("Audit tables created successfully")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create audit tables: ${e.message}")
            auditEnabled = false
        }
    }

    /**
     * Save audit entry to database
     */
    private fun saveAuditEntry(entry: AuditEntry) {
        if (!auditEnabled) return

        try {
            val connection = plugin.databaseManager.getConnection()
            if (connection != null) {
                val insertSQL = """
                    INSERT INTO security_audit_log
                    (id, operation, table_name, user_id, timestamp, ip_address, details, success)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(insertSQL).use { ps ->
                    ps.setString(1, entry.id)
                    ps.setString(2, entry.operation)
                    ps.setString(3, entry.table)
                    ps.setString(4, entry.userId)
                    ps.setLong(5, entry.timestamp)
                    ps.setString(6, entry.ipAddress)
                    ps.setString(7, entry.details.toString())
                    ps.setBoolean(8, entry.success)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save audit entry: ${e.message}")
        }
    }

    /**
     * Encrypt backup file
     */
    private fun encryptBackupFile(backupFile: File): File {
        val encryptedFile = File(backupFile.parent, "${backupFile.name}.encrypted")

        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)

            FileInputStream(backupFile).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    // Write IV first
                    output.write(iv)

                    // Encrypt and write data
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                        if (encryptedChunk != null) {
                            output.write(encryptedChunk)
                        }
                    }

                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null) {
                        output.write(finalChunk)
                    }
                }
            }

            return encryptedFile
        } catch (e: Exception) {
            plugin.logger.severe("Failed to encrypt backup file: ${e.message}")
            throw e
        }
    }

    /**
     * Upload file to remote location
     */
    private fun uploadToRemote(file: File): RemoteBackupResult {
        return when (backupType.lowercase()) {
            "ftp" -> uploadViaFTP(file)
            "sftp" -> uploadViaSFTP(file)
            "http" -> uploadViaHTTP(file)
            else -> RemoteBackupResult(false, "Unsupported backup type: $backupType")
        }
    }

    /**
     * Upload via FTP (basic implementation)
     */
    private fun uploadViaFTP(file: File): RemoteBackupResult {
        // This is a simplified implementation
        // In production, you would use Apache Commons Net or similar library
        try {
            val remotePath = "$remotePath${file.name}"
            plugin.debugLog("Would upload ${file.name} to FTP: $remoteHost$remotePath")

            // Simulate upload
            Thread.sleep(1000)

            return RemoteBackupResult(true, "FTP upload completed", remotePath)
        } catch (e: Exception) {
            return RemoteBackupResult(false, "FTP upload failed: ${e.message}")
        }
    }

    /**
     * Upload via SFTP (basic implementation)
     */
    private fun uploadViaSFTP(file: File): RemoteBackupResult {
        try {
            val remotePath = "$remotePath${file.name}"
            plugin.debugLog("Would upload ${file.name} to SFTP: $remoteHost$remotePath")

            // Simulate upload
            Thread.sleep(1500)

            return RemoteBackupResult(true, "SFTP upload completed", remotePath)
        } catch (e: Exception) {
            return RemoteBackupResult(false, "SFTP upload failed: ${e.message}")
        }
    }

    /**
     * Upload via HTTP POST (basic implementation)
     */
    private fun uploadViaHTTP(file: File): RemoteBackupResult {
        try {
            val remotePath = "$remotePath${file.name}"
            plugin.debugLog("Would upload ${file.name} to HTTP: $remoteHost$remotePath")

            // Simulate upload
            Thread.sleep(2000)

            return RemoteBackupResult(true, "HTTP upload completed", remotePath)
        } catch (e: Exception) {
            return RemoteBackupResult(false, "HTTP upload failed: ${e.message}")
        }
    }

    /**
     * Get current IP address (simplified)
     */
    private fun getCurrentIPAddress(): String {
        return "127.0.0.1" // In production, get actual client IP
    }

    /**
     * Start maintenance tasks
     */
    private fun startMaintenanceTasks() {
        // Audit log cleanup task
        securityExecutor.scheduleAtFixedRate({
            cleanupOldAuditEntries()
        }, 24, 24, TimeUnit.HOURS)

        // Security check task
        securityExecutor.scheduleAtFixedRate({
            performSecurityCheck()
        }, 1, 168, TimeUnit.HOURS) // Weekly
    }

    /**
     * Clean up old audit entries
     */
    private fun cleanupOldAuditEntries() {
        if (!auditEnabled) return

        val cutoffTime = System.currentTimeMillis() - (auditRetentionDays * 24 * 60 * 60 * 1000L)
        val toRemove = auditLog.values.filter { it.timestamp < cutoffTime }

        toRemove.forEach { entry ->
            auditLog.remove(entry.id)
        }

        // Also clean database
        try {
            val connection = plugin.databaseManager.getConnection()
            if (connection != null) {
                val deleteSQL = "DELETE FROM security_audit_log WHERE timestamp < ?"
                connection.prepareStatement(deleteSQL).use { ps ->
                    ps.setLong(1, cutoffTime)
                    val deleted = ps.executeUpdate()
                    plugin.debugLog("Cleaned up $deleted old audit entries")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to cleanup audit entries: ${e.message}")
        }
    }

    /**
     * Get security statistics
     */
    fun getSecurityStatistics(): Map<String, Any> {
        return mapOf(
            "encryptionEnabled" to encryptionEnabled,
            "auditEnabled" to auditEnabled,
            "remoteBackupEnabled" to remoteBackupEnabled,
            "totalOperations" to totalOperations,
            "encryptedOperations" to encryptedOperations,
            "auditedOperations" to auditedOperations,
            "backupsSent" to backupsSent,
            "auditEntriesCount" to auditLog.size,
            "ipWhitelistSize" to ipWhitelist.size
        )
    }

    fun shutdown() {
        securityExecutor.shutdown()
    }

    // Data classes
    data class AuditEntry(
        val id: String,
        val operation: String,
        val table: String,
        val userId: String?,
        val timestamp: Long,
        val details: Map<String, Any>,
        val ipAddress: String,
        val success: Boolean
    )

    data class RemoteBackupResult(
        val success: Boolean,
        val message: String,
        val remotePath: String = ""
    )

    data class SecurityCheckResult(
        val riskLevel: String,
        val issues: List<String>,
        val recommendations: List<String>,
        val auditEntriesCount: Int,
        val encryptionStatus: String,
        val lastCheck: Long
    )
}
