package com.noximity.remmyChat.models

/**
 * Represents information about a server in the cross-server network
 */
data class ServerInfo(
    val name: String,
    var displayName: String = "",
    var region: String = "",
    var online: Boolean = true,
    var playerCount: Int = 0,
    var lastHeartbeat: Long = System.currentTimeMillis()
) {
    // Server capabilities
    var supportedChannels: List<String> = emptyList()
    var version: String = ""
    var maxPlayers: Int = 0
    var motd: String = ""

    // Performance metrics
    var averageLatency: Long = 0L
    var tps: Double = 20.0
    var memoryUsage: Double = 0.0
    var cpuUsage: Double = 0.0

    // Network statistics
    var messagesSent: Long = 0L
    var messagesReceived: Long = 0L
    var bytesTransferred: Long = 0L
    var lastMessageTime: Long = 0L

    // Connection status
    var connectionAttempts: Int = 0
    var lastConnectionAttempt: Long = 0L
    var consecutiveFailures: Int = 0
    var lastError: String = ""

    /**
     * Check if the server is considered online based on heartbeat timeout
     */
    fun isOnline(timeoutMs: Long = 90000L): Boolean {
        return online && (System.currentTimeMillis() - lastHeartbeat) < timeoutMs
    }

    /**
     * Check if the server is responding quickly
     */
    fun isResponsive(maxLatencyMs: Long = 5000L): Boolean {
        return isOnline() && averageLatency < maxLatencyMs
    }

    /**
     * Check if the server has players online
     */
    fun hasPlayers(): Boolean = playerCount > 0

    /**
     * Check if the server is at capacity
     */
    fun isAtCapacity(): Boolean = maxPlayers > 0 && playerCount >= maxPlayers

    /**
     * Get the player capacity percentage (0.0 to 1.0)
     */
    fun getCapacityPercentage(): Double {
        return if (maxPlayers > 0) playerCount.toDouble() / maxPlayers.toDouble() else 0.0
    }

    /**
     * Update heartbeat with current timestamp
     */
    fun updateHeartbeat() {
        lastHeartbeat = System.currentTimeMillis()
        online = true
        consecutiveFailures = 0
    }

    /**
     * Mark server as offline
     */
    fun markOffline(reason: String = "") {
        online = false
        lastError = reason
        consecutiveFailures++
    }

    /**
     * Record a successful connection
     */
    fun recordConnection() {
        connectionAttempts++
        lastConnectionAttempt = System.currentTimeMillis()
        consecutiveFailures = 0
        online = true
    }

    /**
     * Record a failed connection attempt
     */
    fun recordConnectionFailure(error: String) {
        connectionAttempts++
        lastConnectionAttempt = System.currentTimeMillis()
        consecutiveFailures++
        lastError = error
        if (consecutiveFailures >= 3) {
            online = false
        }
    }

    /**
     * Update performance metrics
     */
    fun updatePerformance(latency: Long, tps: Double, memory: Double, cpu: Double) {
        // Calculate rolling average for latency
        averageLatency = if (averageLatency == 0L) {
            latency
        } else {
            (averageLatency * 0.7 + latency * 0.3).toLong()
        }

        this.tps = tps
        this.memoryUsage = memory
        this.cpuUsage = cpu
    }

    /**
     * Record a message being sent to this server
     */
    fun recordMessageSent(bytes: Int = 0) {
        messagesSent++
        lastMessageTime = System.currentTimeMillis()
        if (bytes > 0) {
            bytesTransferred += bytes
        }
    }

    /**
     * Record a message being received from this server
     */
    fun recordMessageReceived(bytes: Int = 0) {
        messagesReceived++
        lastMessageTime = System.currentTimeMillis()
        if (bytes > 0) {
            bytesTransferred += bytes
        }
    }

    /**
     * Get health status of the server
     */
    fun getHealthStatus(): ServerHealth {
        return when {
            !isOnline() -> ServerHealth.OFFLINE
            consecutiveFailures > 0 -> ServerHealth.DEGRADED
            averageLatency > 10000L -> ServerHealth.SLOW
            cpuUsage > 90.0 || memoryUsage > 90.0 -> ServerHealth.OVERLOADED
            else -> ServerHealth.HEALTHY
        }
    }

    /**
     * Get time since last heartbeat in seconds
     */
    fun getTimeSinceHeartbeat(): Long {
        return (System.currentTimeMillis() - lastHeartbeat) / 1000
    }

    /**
     * Get time since last message in seconds
     */
    fun getTimeSinceLastMessage(): Long {
        return if (lastMessageTime > 0) {
            (System.currentTimeMillis() - lastMessageTime) / 1000
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Check if server supports a specific channel
     */
    fun supportsChannel(channelName: String): Boolean {
        return supportedChannels.isEmpty() || supportedChannels.contains(channelName)
    }

    /**
     * Get server statistics as a map
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "displayName" to displayName,
            "region" to region,
            "online" to online,
            "playerCount" to playerCount,
            "maxPlayers" to maxPlayers,
            "capacityPercentage" to getCapacityPercentage(),
            "health" to getHealthStatus().name,
            "timeSinceHeartbeat" to getTimeSinceHeartbeat(),
            "timeSinceLastMessage" to getTimeSinceLastMessage(),
            "averageLatency" to averageLatency,
            "tps" to tps,
            "memoryUsage" to memoryUsage,
            "cpuUsage" to cpuUsage,
            "messagesSent" to messagesSent,
            "messagesReceived" to messagesReceived,
            "bytesTransferred" to bytesTransferred,
            "connectionAttempts" to connectionAttempts,
            "consecutiveFailures" to consecutiveFailures,
            "supportedChannels" to supportedChannels.size,
            "version" to version
        )
    }

    /**
     * Get a human-readable status string
     */
    fun getStatusString(): String {
        val health = getHealthStatus()
        val timeSince = getTimeSinceHeartbeat()

        return when (health) {
            ServerHealth.HEALTHY -> "Online ($playerCount players)"
            ServerHealth.DEGRADED -> "Online with issues ($playerCount players, ${consecutiveFailures} failures)"
            ServerHealth.SLOW -> "Online but slow ($playerCount players, ${averageLatency}ms latency)"
            ServerHealth.OVERLOADED -> "Online but overloaded ($playerCount players, CPU: ${cpuUsage}%, Memory: ${memoryUsage}%)"
            ServerHealth.OFFLINE -> "Offline (${timeSince}s ago)"
        }
    }

    /**
     * Create a compact representation for network transmission
     */
    fun toCompactMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "displayName" to displayName,
            "region" to region,
            "playerCount" to playerCount,
            "maxPlayers" to maxPlayers,
            "version" to version,
            "supportedChannels" to supportedChannels,
            "tps" to tps,
            "memoryUsage" to memoryUsage,
            "cpuUsage" to cpuUsage
        )
    }

    override fun toString(): String {
        return "ServerInfo(name='$name', ${getStatusString()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerInfo) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        /**
         * Create ServerInfo from a compact map representation
         */
        fun fromCompactMap(map: Map<String, Any>): ServerInfo {
            val server = ServerInfo(
                name = map["name"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                region = map["region"] as? String ?: ""
            )

            server.playerCount = (map["playerCount"] as? Number)?.toInt() ?: 0
            server.maxPlayers = (map["maxPlayers"] as? Number)?.toInt() ?: 0
            server.version = map["version"] as? String ?: ""
            server.supportedChannels = (map["supportedChannels"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            server.tps = (map["tps"] as? Number)?.toDouble() ?: 20.0
            server.memoryUsage = (map["memoryUsage"] as? Number)?.toDouble() ?: 0.0
            server.cpuUsage = (map["cpuUsage"] as? Number)?.toDouble() ?: 0.0

            return server
        }

        /**
         * Create a local server info instance
         */
        fun createLocal(
            serverName: String,
            displayName: String = serverName,
            region: String = "local"
        ): ServerInfo {
            return ServerInfo(
                name = serverName,
                displayName = displayName,
                region = region,
                online = true,
                playerCount = 0,
                lastHeartbeat = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Enum representing the health status of a server
 */
enum class ServerHealth {
    HEALTHY,    // Server is online and performing well
    DEGRADED,   // Server is online but has some issues
    SLOW,       // Server is online but responding slowly
    OVERLOADED, // Server is online but under heavy load
    OFFLINE     // Server is offline or unreachable
}
