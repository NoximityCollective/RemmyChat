package com.noximity.remmyChat.monitoring

import com.noximity.remmyChat.RemmyChat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PerformanceMonitor(private val plugin: RemmyChat) {

    // Metrics tracking
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val crossServerMessages = AtomicLong(0)
    private val discordMessages = AtomicLong(0)
    private val databaseQueries = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    // Performance metrics
    private val messageProcessingTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val databaseQueryTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val averageResponseTimes = ConcurrentHashMap<String, Double>()

    // System metrics
    private val memoryUsage = mutableListOf<Long>()
    private val cpuUsage = mutableListOf<Double>()
    private val threadCount = AtomicInteger(0)
    private val activeConnections = AtomicInteger(0)

    // Channel-specific metrics
    private val channelMessageCounts = ConcurrentHashMap<String, AtomicLong>()
    private val channelUserCounts = ConcurrentHashMap<String, AtomicInteger>()

    // Alert thresholds
    private var highMemoryThreshold = 0.8 // 80%
    private var slowQueryThreshold = 1000L // 1 second
    private var highErrorRateThreshold = 0.05 // 5%
    private var maxResponseTimeThreshold = 500L // 500ms

    // Monitoring state
    private var monitoringEnabled = true
    private var alertsEnabled = true
    private var statisticsLoggingEnabled = false
    private var metricsCollectionInterval = 60L // 1 minute
    private var statisticsRetentionHours = 24L

    // Scheduled executor for monitoring tasks
    private val monitoringExecutor = Executors.newScheduledThreadPool(2)

    fun initialize() {
        plugin.debugLog("Initializing PerformanceMonitor...")
        loadConfiguration()
        startMonitoringTasks()
        plugin.debugLog("PerformanceMonitor initialized")
    }

    private fun loadConfiguration() {
        val config = plugin.configManager
        val mainConfig = config.getMainConfig()

        monitoringEnabled = mainConfig.getBoolean("monitoring.enabled", true)
        alertsEnabled = mainConfig.getBoolean("monitoring.alerts.enabled", true)
        statisticsLoggingEnabled = mainConfig.getBoolean("monitoring.statistics-logging", false)
        metricsCollectionInterval = mainConfig.getLong("monitoring.metrics-collection-interval", 60L)
        statisticsRetentionHours = mainConfig.getLong("monitoring.statistics-retention-hours", 24L)

        // Load alert thresholds
        highMemoryThreshold = mainConfig.getDouble("monitoring.alerts.high-memory-threshold", 0.8)
        slowQueryThreshold = mainConfig.getLong("monitoring.alerts.slow-query-threshold", 1000L)
        highErrorRateThreshold = mainConfig.getDouble("monitoring.alerts.high-error-rate-threshold", 0.05)
        maxResponseTimeThreshold = mainConfig.getLong("monitoring.alerts.max-response-time-threshold", 500L)

        plugin.debugLog("Performance monitoring configuration loaded")
    }

    private fun startMonitoringTasks() {
        if (!monitoringEnabled) return

        // Start metrics collection task
        monitoringExecutor.scheduleAtFixedRate({
            try {
                collectSystemMetrics()
                checkAlerts()
                cleanupOldMetrics()
            } catch (e: Exception) {
                plugin.logger.warning("Error in monitoring task: ${e.message}")
                recordError("monitoring_task_error")
            }
        }, metricsCollectionInterval, metricsCollectionInterval, TimeUnit.SECONDS)

        // Start statistics logging task (only if enabled)
        if (statisticsLoggingEnabled) {
            monitoringExecutor.scheduleAtFixedRate({
                try {
                    logStatistics()
                } catch (e: Exception) {
                    plugin.logger.warning("Error in statistics logging: ${e.message}")
                }
            }, 300L, 300L, TimeUnit.SECONDS) // Every 5 minutes
        }

        plugin.debugLog("Monitoring tasks started")
    }

    /**
     * Record a message being sent
     */
    fun recordMessageSent(channel: String, processingTime: Long) {
        if (!monitoringEnabled) return

        messagesSent.incrementAndGet()
        recordChannelMessage(channel)
        recordProcessingTime("message_sent", processingTime)

        // Check for slow message processing
        if (alertsEnabled && processingTime > maxResponseTimeThreshold) {
            alertSlowMessageProcessing(channel, processingTime)
        }
    }

    /**
     * Record a message being received
     */
    fun recordMessageReceived(channel: String) {
        if (!monitoringEnabled) return

        messagesReceived.incrementAndGet()
        recordChannelMessage(channel)
    }

    /**
     * Record a cross-server message
     */
    fun recordCrossServerMessage(direction: String, processingTime: Long) {
        if (!monitoringEnabled) return

        crossServerMessages.incrementAndGet()
        recordProcessingTime("cross_server_$direction", processingTime)
    }

    /**
     * Record a Discord message
     */
    fun recordDiscordMessage(direction: String) {
        if (!monitoringEnabled) return

        discordMessages.incrementAndGet()
    }

    /**
     * Record a database query
     */
    fun recordDatabaseQuery(queryType: String, executionTime: Long) {
        if (!monitoringEnabled) return

        databaseQueries.incrementAndGet()
        recordQueryTime(queryType, executionTime)

        // Check for slow queries
        if (alertsEnabled && executionTime > slowQueryThreshold) {
            alertSlowQuery(queryType, executionTime)
        }
    }

    /**
     * Record cache hit
     */
    fun recordCacheHit(cacheType: String) {
        if (!monitoringEnabled) return
        cacheHits.incrementAndGet()
    }

    /**
     * Record cache miss
     */
    fun recordCacheMiss(cacheType: String) {
        if (!monitoringEnabled) return
        cacheMisses.incrementAndGet()
    }

    /**
     * Record an error
     */
    fun recordError(errorType: String) {
        errorCount.incrementAndGet()
        plugin.debugLog("Error recorded: $errorType")
    }

    /**
     * Record processing time for a specific operation
     */
    private fun recordProcessingTime(operation: String, time: Long) {
        val times = messageProcessingTimes.getOrPut(operation) { mutableListOf() }
        synchronized(times) {
            times.add(time)
            // Keep only last 100 measurements
            if (times.size > 100) {
                times.removeAt(0)
            }
        }

        // Calculate average
        val average = times.average()
        averageResponseTimes[operation] = average
    }

    /**
     * Record database query time
     */
    private fun recordQueryTime(queryType: String, time: Long) {
        val times = databaseQueryTimes.getOrPut(queryType) { mutableListOf() }
        synchronized(times) {
            times.add(time)
            if (times.size > 100) {
                times.removeAt(0)
            }
        }
    }

    /**
     * Record channel message
     */
    private fun recordChannelMessage(channel: String) {
        channelMessageCounts.getOrPut(channel) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * Update channel user count
     */
    fun updateChannelUserCount(channel: String, count: Int) {
        if (!monitoringEnabled) return
        channelUserCounts.getOrPut(channel) { AtomicInteger(0) }.set(count)
    }

    /**
     * Update active connections count
     */
    fun updateActiveConnections(count: Int) {
        if (!monitoringEnabled) return
        activeConnections.set(count)
    }

    /**
     * Collect system metrics
     */
    private fun collectSystemMetrics() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        // Record memory usage
        memoryUsage.add(usedMemory)
        if (memoryUsage.size > 100) {
            memoryUsage.removeAt(0)
        }

        // Record thread count
        threadCount.set(Thread.activeCount())

        // Check memory usage alert
        val memoryUsagePercent = usedMemory.toDouble() / maxMemory.toDouble()
        if (alertsEnabled && memoryUsagePercent > highMemoryThreshold) {
            alertHighMemoryUsage(memoryUsagePercent)
        }
    }

    /**
     * Check for alert conditions
     */
    private fun checkAlerts() {
        if (!alertsEnabled) return

        // Check error rate
        val totalMessages = messagesSent.get() + messagesReceived.get()
        if (totalMessages > 0) {
            val errorRate = errorCount.get().toDouble() / totalMessages.toDouble()
            if (errorRate > highErrorRateThreshold) {
                alertHighErrorRate(errorRate)
            }
        }

        // Check cache hit rate
        val totalCacheRequests = cacheHits.get() + cacheMisses.get()
        if (totalCacheRequests > 100) {
            val hitRate = cacheHits.get().toDouble() / totalCacheRequests.toDouble()
            if (hitRate < 0.7) { // Less than 70% hit rate
                alertLowCacheHitRate(hitRate)
            }
        }
    }

    /**
     * Clean up old metrics data
     */
    private fun cleanupOldMetrics() {
        val cutoffTime = Instant.now().minus(statisticsRetentionHours, ChronoUnit.HOURS).toEpochMilli()

        // Cleanup is handled by keeping only recent measurements in lists
        // This is already implemented in recordProcessingTime and other methods
    }

    /**
     * Log performance statistics
     */
    private fun logStatistics() {
        if (!statisticsLoggingEnabled) return

        val stats = getPerformanceStatistics()

        plugin.logger.info("=== RemmyChat Performance Statistics ===")
        plugin.logger.info("Messages Sent: ${stats.messagesSent}")
        plugin.logger.info("Messages Received: ${stats.messagesReceived}")
        plugin.logger.info("Cross-Server Messages: ${stats.crossServerMessages}")
        plugin.logger.info("Discord Messages: ${stats.discordMessages}")
        plugin.logger.info("Database Queries: ${stats.databaseQueries}")
        plugin.logger.info("Cache Hit Rate: ${String.format("%.2f%%", stats.cacheHitRate * 100)}")
        plugin.logger.info("Average Message Processing Time: ${String.format("%.2f ms", stats.averageMessageProcessingTime)}")
        plugin.logger.info("Error Count: ${stats.errorCount}")
        plugin.logger.info("Active Connections: ${stats.activeConnections}")
        plugin.logger.info("Memory Usage: ${String.format("%.2f MB", stats.memoryUsageMB)}")
        plugin.logger.info("==========================================")
    }

    /**
     * Get current performance statistics
     */
    fun getPerformanceStatistics(): PerformanceStatistics {
        val totalCacheRequests = cacheHits.get() + cacheMisses.get()
        val cacheHitRate = if (totalCacheRequests > 0) {
            cacheHits.get().toDouble() / totalCacheRequests.toDouble()
        } else 0.0

        val avgMessageProcessingTime = averageResponseTimes["message_sent"] ?: 0.0

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsageMB = usedMemory / (1024.0 * 1024.0)

        return PerformanceStatistics(
            messagesSent = messagesSent.get(),
            messagesReceived = messagesReceived.get(),
            crossServerMessages = crossServerMessages.get(),
            discordMessages = discordMessages.get(),
            databaseQueries = databaseQueries.get(),
            cacheHitRate = cacheHitRate,
            averageMessageProcessingTime = avgMessageProcessingTime,
            errorCount = errorCount.get(),
            activeConnections = activeConnections.get(),
            memoryUsageMB = memoryUsageMB,
            threadCount = threadCount.get(),
            channelStatistics = getChannelStatistics()
        )
    }

    /**
     * Get channel-specific statistics
     */
    private fun getChannelStatistics(): Map<String, ChannelStatistics> {
        return channelMessageCounts.mapValues { (channel, messageCount) ->
            ChannelStatistics(
                messageCount = messageCount.get(),
                userCount = channelUserCounts[channel]?.get() ?: 0
            )
        }
    }

    /**
     * Reset all statistics
     */
    fun resetStatistics() {
        messagesSent.set(0)
        messagesReceived.set(0)
        crossServerMessages.set(0)
        discordMessages.set(0)
        databaseQueries.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        errorCount.set(0)

        messageProcessingTimes.clear()
        databaseQueryTimes.clear()
        averageResponseTimes.clear()
        channelMessageCounts.clear()

        memoryUsage.clear()
        cpuUsage.clear()

        plugin.logger.info("Performance statistics reset")
    }

    /**
     * Export statistics to JSON format
     */
    fun exportStatistics(): String {
        val stats = getPerformanceStatistics()
        return """
            {
                "timestamp": "${Instant.now()}",
                "messagesSent": ${stats.messagesSent},
                "messagesReceived": ${stats.messagesReceived},
                "crossServerMessages": ${stats.crossServerMessages},
                "discordMessages": ${stats.discordMessages},
                "databaseQueries": ${stats.databaseQueries},
                "cacheHitRate": ${stats.cacheHitRate},
                "averageMessageProcessingTime": ${stats.averageMessageProcessingTime},
                "errorCount": ${stats.errorCount},
                "activeConnections": ${stats.activeConnections},
                "memoryUsageMB": ${stats.memoryUsageMB},
                "threadCount": ${stats.threadCount},
                "channelStatistics": ${stats.channelStatistics}
            }
        """.trimIndent()
    }

    // Alert methods
    private fun alertSlowMessageProcessing(channel: String, processingTime: Long) {
        plugin.logger.warning("Slow message processing detected in channel '$channel': ${processingTime}ms")
    }

    private fun alertSlowQuery(queryType: String, executionTime: Long) {
        plugin.logger.warning("Slow database query detected: $queryType took ${executionTime}ms")
    }

    private fun alertHighMemoryUsage(usagePercent: Double) {
        plugin.logger.warning("High memory usage detected: ${String.format("%.2f%%", usagePercent * 100)}")
    }

    private fun alertHighErrorRate(errorRate: Double) {
        plugin.logger.warning("High error rate detected: ${String.format("%.2f%%", errorRate * 100)}")
    }

    private fun alertLowCacheHitRate(hitRate: Double) {
        plugin.logger.warning("Low cache hit rate detected: ${String.format("%.2f%%", hitRate * 100)}")
    }

    /**
     * Shutdown monitoring
     */
    fun shutdown() {
        monitoringExecutor.shutdown()
        try {
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            monitoringExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Reload monitoring configuration
     */
    fun reload() {
        loadConfiguration()
        plugin.logger.info("Performance monitoring configuration reloaded")
    }

    /**
     * Get performance statistics for admin interface
     */
    fun getDetailedPerformanceStatistics(): PerformanceStatistics {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val memoryUsageMB = usedMemory / 1024.0 / 1024.0

        val totalMessages = messagesSent.get() + messagesReceived.get()
        val cacheTotal = cacheHits.get() + cacheMisses.get()
        val hitRate = if (cacheTotal > 0) cacheHits.get().toDouble() / cacheTotal.toDouble() else 0.0

        val avgProcessingTime = if (messageProcessingTimes.isNotEmpty()) {
            messageProcessingTimes.values.flatten().average()
        } else 0.0

        val channelStats = channelMessageCounts.mapValues { (channel, count) ->
            ChannelStatistics(
                messageCount = count.get(),
                userCount = channelUserCounts[channel]?.get() ?: 0
            )
        }

        return PerformanceStatistics(
            messagesSent = messagesSent.get(),
            messagesReceived = messagesReceived.get(),
            crossServerMessages = crossServerMessages.get(),
            discordMessages = discordMessages.get(),
            databaseQueries = databaseQueries.get(),
            cacheHitRate = hitRate,
            averageMessageProcessingTime = avgProcessingTime,
            errorCount = errorCount.get(),
            activeConnections = activeConnections.get(),
            memoryUsageMB = memoryUsageMB,
            threadCount = Thread.activeCount(),
            channelStatistics = channelStats
        )
    }

    /**
     * Reset performance counters
     */
    fun resetCounters() {
        messagesSent.set(0)
        messagesReceived.set(0)
        crossServerMessages.set(0)
        discordMessages.set(0)
        databaseQueries.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        errorCount.set(0)
        activeConnections.set(0)

        messageProcessingTimes.clear()
        databaseQueryTimes.clear()
        averageResponseTimes.clear()
        memoryUsage.clear()
        cpuUsage.clear()

        channelMessageCounts.clear()
        channelUserCounts.clear()

        plugin.logger.info("Performance counters have been reset")
    }

    // Data classes
    data class PerformanceStatistics(
        val messagesSent: Long,
        val messagesReceived: Long,
        val crossServerMessages: Long,
        val discordMessages: Long,
        val databaseQueries: Long,
        val cacheHitRate: Double,
        val averageMessageProcessingTime: Double,
        val errorCount: Long,
        val activeConnections: Int,
        val memoryUsageMB: Double,
        val threadCount: Int,
        val channelStatistics: Map<String, ChannelStatistics>
    )

    data class ChannelStatistics(
        val messageCount: Long,
        val userCount: Int
    )
}
