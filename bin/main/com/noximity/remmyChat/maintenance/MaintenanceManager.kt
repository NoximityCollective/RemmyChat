package com.noximity.remmyChat.maintenance

import com.noximity.remmyChat.RemmyChat
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream

class MaintenanceManager(private val plugin: RemmyChat) {

    private val maintenanceExecutor = Executors.newSingleThreadScheduledExecutor()

    // Configuration
    private var autoSaveInterval = 300L // 5 minutes
    private var cleanupOldData = true
    private var dataRetentionDays = 30
    private var backupConfigs = true
    private var compressionEnabled = true

    // Backup settings
    private var backupEnabled = true
    private var backupInterval = 24L // 24 hours
    private var maxBackups = 7
    private var backupLocation = "backups"

    // Cleanup settings
    private var messageHistoryRetentionDays = 7
    private var playerDataRetentionDays = 90
    private var logFileRetentionDays = 14
    private var tempFileCleanupEnabled = true

    fun initialize() {
        plugin.debugLog("Initializing MaintenanceManager...")
        loadConfiguration()
        startMaintenanceTasks()
        plugin.debugLog("MaintenanceManager initialized")
    }

    private fun loadConfiguration() {
        val config = plugin.configManager
        val mainConfig = plugin.config

        // Load basic maintenance settings
        autoSaveInterval = mainConfig.getLong("maintenance.auto-save-interval", 300L)
        cleanupOldData = mainConfig.getBoolean("maintenance.cleanup-old-data", true)
        dataRetentionDays = mainConfig.getInt("maintenance.data-retention-days", 30)
        backupConfigs = mainConfig.getBoolean("maintenance.backup-configs", true)

        // Load backup settings
        backupEnabled = mainConfig.getBoolean("backup.enabled", true)
        backupInterval = mainConfig.getLong("backup.schedule.full-backup", 24L)
        maxBackups = mainConfig.getInt("backup.retention.daily", 7)
        backupLocation = mainConfig.getString("backup.location.local-path", "backups") ?: "backups"
        compressionEnabled = mainConfig.getBoolean("backup.compression.enabled", true)

        // Load cleanup settings
        messageHistoryRetentionDays = mainConfig.getInt("maintenance.cleanup.message-history-retention", 7)
        playerDataRetentionDays = mainConfig.getInt("maintenance.cleanup.player-data-retention", 90)
        logFileRetentionDays = mainConfig.getInt("maintenance.cleanup.log-file-retention", 14)
        tempFileCleanupEnabled = mainConfig.getBoolean("maintenance.cleanup.temp-files", true)

        plugin.debugLog("Maintenance configuration loaded")
    }

    private fun startMaintenanceTasks() {
        // Auto-save task
        if (autoSaveInterval > 0) {
            maintenanceExecutor.scheduleAtFixedRate({
                try {
                    performAutoSave()
                } catch (e: Exception) {
                    plugin.logger.warning("Error during auto-save: ${e.message}")
                }
            }, autoSaveInterval, autoSaveInterval, TimeUnit.SECONDS)
        }

        // Daily cleanup task
        if (cleanupOldData) {
            maintenanceExecutor.scheduleAtFixedRate({
                try {
                    performDailyCleanup()
                } catch (e: Exception) {
                    plugin.logger.warning("Error during daily cleanup: ${e.message}")
                }
            }, 1L, 24L, TimeUnit.HOURS) // Run daily
        }

        // Backup task
        if (backupEnabled) {
            maintenanceExecutor.scheduleAtFixedRate({
                try {
                    performBackup()
                } catch (e: Exception) {
                    plugin.logger.warning("Error during backup: ${e.message}")
                }
            }, backupInterval, backupInterval, TimeUnit.HOURS)
        }

        // Hourly maintenance task
        maintenanceExecutor.scheduleAtFixedRate({
            try {
                performHourlyMaintenance()
            } catch (e: Exception) {
                plugin.logger.warning("Error during hourly maintenance: ${e.message}")
            }
        }, 1L, 1L, TimeUnit.HOURS)

        plugin.debugLog("Maintenance tasks scheduled")
    }

    /**
     * Perform auto-save operations
     */
    private fun performAutoSave() {
        plugin.debugLog("Performing auto-save...")

        // Save chat service data
        try {
            plugin.chatService?.saveAllUsers()
        } catch (e: Exception) {
            plugin.logger.warning("Error saving chat service data: ${e.message}")
        }

        // Save channel data
        try {
            plugin.channelManager?.saveChannelData()
        } catch (e: Exception) {
            plugin.logger.warning("Error saving channel data: ${e.message}")
        }

        // Force database sync if enabled
        try {
            if (plugin.databaseManager?.isConnected() == true) {
                plugin.databaseManager.syncData()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error syncing database: ${e.message}")
        }

        plugin.debugLog("Auto-save completed")
    }

    /**
     * Perform daily cleanup operations
     */
    fun performDailyCleanup() {
        plugin.logger.info("Starting daily cleanup operations...")

        // Clean old message history
        cleanupMessageHistory()

        // Clean old player data
        cleanupPlayerData()

        // Clean log files
        cleanupLogFiles()

        // Clean temporary files
        if (tempFileCleanupEnabled) {
            cleanupTempFiles()
        }

        // Clean old backup files
        cleanupOldBackups()

        // Database maintenance
        performDatabaseMaintenance()

        plugin.logger.info("Daily cleanup operations completed")
    }

    /**
     * Perform hourly maintenance
     */
    private fun performHourlyMaintenance() {
        plugin.debugLog("Performing hourly maintenance...")

        // Clean cache
        cleanupCache()

        // Optimize memory usage
        optimizeMemory()

        // Check disk space
        checkDiskSpace()

        plugin.debugLog("Hourly maintenance completed")
    }

    /**
     * Create backup of configuration and data
     */
    fun performBackup(): BackupResult {
        plugin.logger.info("Starting backup operation...")

        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupDir = File(plugin.dataFolder, backupLocation)

            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val backupFileName = if (compressionEnabled) {
                "remmychat_backup_$timestamp.zip"
            } else {
                "remmychat_backup_$timestamp"
            }

            val backupFile = File(backupDir, backupFileName)

            if (compressionEnabled) {
                return createCompressedBackup(backupFile, timestamp)
            } else {
                return createDirectoryBackup(backupFile, timestamp)
            }

        } catch (e: Exception) {
            plugin.logger.severe("Backup failed: ${e.message}")
            e.printStackTrace()
            return BackupResult(false, "Backup failed: ${e.message}", null)
        }
    }

    /**
     * Create compressed backup
     */
    private fun createCompressedBackup(backupFile: File, timestamp: String): BackupResult {
        try {
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->

                // Backup configuration files
                val configFiles = listOf(
                    "config.yml", "channels.yml", "groups.yml", "templates.yml",
                    "cross-server.yml", "database.yml", "discord.yml"
                )

                for (configFile in configFiles) {
                    val file = File(plugin.dataFolder, configFile)
                    if (file.exists()) {
                        addFileToZip(zipOut, file, configFile)
                    }
                }

                // Backup player data
                val playerDataDir = File(plugin.dataFolder, "playerdata")
                if (playerDataDir.exists()) {
                    addDirectoryToZip(zipOut, playerDataDir, "playerdata/")
                }

                // Backup message history (if exists)
                val messageHistoryFile = File(plugin.dataFolder, "message_history.yml")
                if (messageHistoryFile.exists()) {
                    addFileToZip(zipOut, messageHistoryFile, "message_history.yml")
                }

                // Backup user preferences
                val userPrefsFile = File(plugin.dataFolder, "user_preferences.yml")
                if (userPrefsFile.exists()) {
                    addFileToZip(zipOut, userPrefsFile, "user_preferences.yml")
                }
            }

            val fileSizeMB = backupFile.length() / (1024.0 * 1024.0)
            plugin.logger.info("Backup created successfully: ${backupFile.name} (${String.format("%.2f", fileSizeMB)} MB)")

            return BackupResult(true, "Backup created successfully", backupFile)

        } catch (e: Exception) {
            plugin.logger.severe("Failed to create compressed backup: ${e.message}")
            return BackupResult(false, "Failed to create compressed backup: ${e.message}", null)
        }
    }

    /**
     * Create directory-based backup
     */
    private fun createDirectoryBackup(backupDir: File, timestamp: String): BackupResult {
        try {
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // Copy configuration files
            val configFiles = listOf(
                "config.yml", "channels.yml", "groups.yml", "templates.yml",
                "cross-server.yml", "database.yml", "discord.yml"
            )

            for (configFile in configFiles) {
                val sourceFile = File(plugin.dataFolder, configFile)
                val destFile = File(backupDir, configFile)
                if (sourceFile.exists()) {
                    Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Copy player data directory
            val sourcePlayerData = File(plugin.dataFolder, "playerdata")
            val destPlayerData = File(backupDir, "playerdata")
            if (sourcePlayerData.exists()) {
                copyDirectory(sourcePlayerData, destPlayerData)
            }

            plugin.logger.info("Directory backup created successfully: ${backupDir.name}")
            return BackupResult(true, "Directory backup created successfully", backupDir)

        } catch (e: Exception) {
            plugin.logger.severe("Failed to create directory backup: ${e.message}")
            return BackupResult(false, "Failed to create directory backup: ${e.message}", null)
        }
    }

    /**
     * Add file to ZIP archive
     */
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)

            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } >= 0) {
                zipOut.write(buffer, 0, length)
            }

            zipOut.closeEntry()
        }
    }

    /**
     * Add directory to ZIP archive
     */
    private fun addDirectoryToZip(zipOut: ZipOutputStream, dir: File, basePath: String) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addDirectoryToZip(zipOut, file, "$basePath${file.name}/")
            } else {
                addFileToZip(zipOut, file, "$basePath${file.name}")
            }
        }
    }

    /**
     * Copy directory recursively
     */
    private fun copyDirectory(source: File, dest: File) {
        if (!dest.exists()) {
            dest.mkdirs()
        }

        val files = source.listFiles() ?: return

        for (file in files) {
            val destFile = File(dest, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /**
     * Clean up old message history
     */
    private fun cleanupMessageHistory() {
        if (plugin.databaseManager?.isConnected() == true) {
            try {
                val deletedCount = plugin.databaseManager.cleanupOldMessages(messageHistoryRetentionDays)
                if (deletedCount > 0) {
                    plugin.logger.info("Cleaned up $deletedCount old message history entries")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning up message history: ${e.message}")
            }
        }
    }

    /**
     * Clean up old player data
     */
    private fun cleanupPlayerData() {
        if (plugin.databaseManager?.isConnected() == true) {
            try {
                val deletedCount = plugin.databaseManager.cleanupOldPlayerData(playerDataRetentionDays)
                if (deletedCount > 0) {
                    plugin.logger.info("Cleaned up data for $deletedCount inactive players")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning up player data: ${e.message}")
            }
        }
    }

    /**
     * Clean up old log files
     */
    private fun cleanupLogFiles() {
        try {
            val logsDir = File(plugin.dataFolder, "logs")
            if (!logsDir.exists()) return

            val cutoffTime = System.currentTimeMillis() - (logFileRetentionDays * 24 * 60 * 60 * 1000L)
            var deletedCount = 0

            logsDir.listFiles()?.forEach { logFile ->
                if (logFile.isFile && logFile.lastModified() < cutoffTime) {
                    if (logFile.delete()) {
                        deletedCount++
                    }
                }
            }

            if (deletedCount > 0) {
                plugin.logger.info("Cleaned up $deletedCount old log files")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error cleaning up log files: ${e.message}")
        }
    }

    /**
     * Clean up temporary files
     */
    private fun cleanupTempFiles() {
        try {
            val tempDir = File(plugin.dataFolder, "temp")
            if (!tempDir.exists()) return

            var deletedCount = 0
            tempDir.listFiles()?.forEach { tempFile ->
                if (tempFile.delete()) {
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                plugin.logger.info("Cleaned up $deletedCount temporary files")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error cleaning up temporary files: ${e.message}")
        }
    }

    /**
     * Clean up old backup files
     */
    private fun cleanupOldBackups() {
        try {
            val backupDir = File(plugin.dataFolder, backupLocation)
            if (!backupDir.exists()) return

            val backupFiles = backupDir.listFiles { file ->
                file.isFile && (file.name.startsWith("remmychat_backup_") || file.name.startsWith("backup_"))
            }?.sortedByDescending { it.lastModified() } ?: return

            // Keep only the most recent backups
            val filesToDelete = if (backupFiles.size > maxBackups) {
                backupFiles.drop(maxBackups)
            } else {
                emptyList()
            }

            var deletedCount = 0
            filesToDelete.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                plugin.logger.info("Cleaned up $deletedCount old backup files")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error cleaning up old backups: ${e.message}")
        }
    }

    /**
     * Perform database maintenance
     */
    private fun performDatabaseMaintenance() {
        if (plugin.databaseManager?.isConnected() == true) {
            try {
                plugin.databaseManager.performMaintenance()
                plugin.debugLog("Database maintenance completed")
            } catch (e: Exception) {
                plugin.logger.warning("Error during database maintenance: ${e.message}")
            }
        }
    }

    /**
     * Clean up cache
     */
    private fun cleanupCache() {
        try {
            // Clean template cache
            try {
                plugin.templateManager?.cleanupCache()
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning template cache: ${e.message}")
            }

            // Clean group manager cache
            try {
                plugin.groupManager?.cleanupCache()
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning group cache: ${e.message}")
            }

            // Clean channel manager cache
            try {
                plugin.channelManager?.cleanupCache()
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning channel cache: ${e.message}")
            }

            plugin.debugLog("Cache cleanup completed")
        } catch (e: Exception) {
            plugin.logger.warning("Error during cache cleanup: ${e.message}")
        }
    }

    /**
     * Optimize memory usage
     */
    private fun optimizeMemory() {
        try {
            // Suggest garbage collection
            System.gc()

            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()

            val usedPercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100

            if (usedPercent > 80) {
                plugin.logger.warning("High memory usage detected: ${String.format("%.1f%%", usedPercent)}")

                // Perform additional cleanup
                cleanupCache()

                // Clear some non-essential data
                try {
                    plugin.chatService?.clearOldCachedData()
                } catch (e: Exception) {
                    plugin.logger.warning("Error clearing cached data: ${e.message}")
                }
            }

            plugin.debugLog("Memory optimization completed (${String.format("%.1f%%", usedPercent)} used)")
        } catch (e: Exception) {
            plugin.logger.warning("Error during memory optimization: ${e.message}")
        }
    }

    /**
     * Check disk space
     */
    private fun checkDiskSpace() {
        try {
            val dataFolder = plugin.dataFolder
            val usableSpace = dataFolder.usableSpace
            val totalSpace = dataFolder.totalSpace

            val usedPercent = ((totalSpace - usableSpace).toDouble() / totalSpace.toDouble()) * 100

            if (usedPercent > 90) {
                plugin.logger.warning("Low disk space detected: ${String.format("%.1f%%", usedPercent)} used")

                // Trigger additional cleanup
                if (cleanupOldData) {
                    performDailyCleanup()
                }
            }

            plugin.debugLog("Disk space check completed (${String.format("%.1f%%", usedPercent)} used)")
        } catch (e: Exception) {
            plugin.logger.warning("Error checking disk space: ${e.message}")
        }
    }

    /**
     * Get maintenance statistics
     */
    fun getMaintenanceStatistics(): MaintenanceStatistics {
        val backupDir = File(plugin.dataFolder, backupLocation)
        val backupCount = if (backupDir.exists()) {
            backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("remmychat_backup_")
            }?.size ?: 0
        } else 0

        val totalBackupSize = if (backupDir.exists()) {
            backupDir.listFiles()?.sumOf { it.length() } ?: 0L
        } else 0L

        val lastBackupTime = if (backupDir.exists()) {
            backupDir.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L
        } else 0L

        return MaintenanceStatistics(
            backupCount = backupCount,
            totalBackupSizeMB = totalBackupSize / (1024.0 * 1024.0),
            lastBackupTime = lastBackupTime,
            autoSaveInterval = autoSaveInterval,
            cleanupEnabled = cleanupOldData,
            dataRetentionDays = dataRetentionDays
        )
    }

    /**
     * Force manual backup
     */
    fun forceBackup(): BackupResult {
        return performBackup()
    }

    /**
     * Force manual cleanup
     */
    fun forceCleanup() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            performDailyCleanup()
        })
    }

    /**
     * Shutdown maintenance manager
     */
    fun shutdown() {
        plugin.logger.info("Shutting down maintenance manager...")

        // Perform final auto-save
        try {
            performAutoSave()
        } catch (e: Exception) {
            plugin.logger.warning("Error during final auto-save: ${e.message}")
        }

        // Shutdown executor
        maintenanceExecutor.shutdown()
        try {
            if (!maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            maintenanceExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        plugin.logger.info("Maintenance manager shutdown completed")
    }

    /**
     * Reload maintenance configuration
     */
    fun reload() {
        loadConfiguration()
        plugin.logger.info("Maintenance configuration reloaded")
    }



    /**
     * List available backups for admin interface
     */
    fun listBackups(): List<BackupInfo> {
        val backups = mutableListOf<BackupInfo>()
        val backupDir = File(plugin.dataFolder, backupLocation)

        if (!backupDir.exists()) {
            return backups
        }

        backupDir.listFiles { file ->
            file.name.endsWith(".zip") || file.name.endsWith(".tar.gz")
        }?.forEach { file ->
            backups.add(
                BackupInfo(
                    name = file.name,
                    size = file.length(),
                    timestamp = file.lastModified()
                )
            )
        }

        return backups.sortedByDescending { it.timestamp }
    }

    // Data classes
    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backupFile: File?
    )

    data class MaintenanceStatistics(
        val backupCount: Int,
        val totalBackupSizeMB: Double,
        val lastBackupTime: Long,
        val autoSaveInterval: Long,
        val cleanupEnabled: Boolean,
        val dataRetentionDays: Int
    )

    data class BackupInfo(
        val name: String,
        val size: Long,
        val timestamp: Long
    )
}
