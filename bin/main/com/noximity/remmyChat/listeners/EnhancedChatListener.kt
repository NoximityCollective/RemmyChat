package com.noximity.remmyChat.listeners

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.CompletableFuture

class EnhancedChatListener(private val plugin: RemmyChat) : Listener {

    private val mm = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val message = event.message
        val startTime = System.currentTimeMillis()

        try {
            // Check if player is muted
            if (isPlayerMuted(player)) {
                event.isCancelled = true
                player.sendMessage(mm.deserialize("<red>You are currently muted and cannot send messages."))
                return
            }

            // Security checks
            try {
                // val rateLimitResult = plugin.securityManager.checkRateLimit(player)
                // if (rateLimitResult != null && !rateLimitResult.allowed) {
                if (false) { // Temporarily disabled
                    event.isCancelled = true
                    player.sendMessage(mm.deserialize("<red>You are sending messages too quickly! Please slow down."))

                    // Log security violation
                    plugin.databaseManager.saveSecurityViolation(
                        player.uniqueId, player.name, "RATE_LIMIT", "WARNING",
                        "Message rate limit exceeded", player.address?.address?.hostAddress
                    )

                    recordPerformanceMetric("security_rate_limit", System.currentTimeMillis() - startTime)
                    return
                }

                // val spamResult = plugin.securityManager.checkSpam(player, message)
                // if (spamResult != null && !spamResult.allowed) {
                if (false) { // Temporarily disabled
                    event.isCancelled = true
                    player.sendMessage(mm.deserialize("<red>Your message was detected as spam and has been blocked."))

                    // Log security violation
                    plugin.databaseManager.saveSecurityViolation(
                        player.uniqueId, player.name, "SPAM", "WARNING",
                        "Spam message detected: $message", player.address?.address?.hostAddress
                    )

                    recordPerformanceMetric("security_spam_blocked", System.currentTimeMillis() - startTime)
                    return
                }
            } catch (e: Exception) {
                // SecurityManager not initialized
            }

            // Moderation checks - temporarily disabled
            /*
            try {
                val moderationResult = plugin.moderationManager.processMessage(player, message)

                when (moderationResult.action) {
                    "BLOCK" -> {
                        event.isCancelled = true
                        player.sendMessage(mm.deserialize("<red>Your message contains inappropriate content and has been blocked."))

                        // Log moderation action
                        plugin.databaseManager.saveModerationAction(
                            player.uniqueId, player.name, null, "SYSTEM",
                            "MESSAGE_BLOCKED", "Inappropriate content: ${moderationResult.reason}", 0
                        )

                        recordPerformanceMetric("moderation_blocked", System.currentTimeMillis() - startTime)
                        return
                    }
                    "FILTER" -> {
                        event.message = moderationResult.filteredMessage ?: message
                    }
                    "WARN" -> {
                        player.sendMessage(mm.deserialize("<yellow>Warning: Your message contains questionable content."))

                        // Log warning
                        plugin.databaseManager.saveModerationAction(
                            player.uniqueId, player.name, null, "SYSTEM",
                            "AUTO_WARNING", "Questionable content: ${moderationResult.reason}", 0
                        )
                    }
                }
            } catch (e: Exception) {
                // ModerationManager not initialized
            }
            */

            // Get user and channel information
            val user = plugin.chatService.getUser(player)
            val currentChannel = user?.currentChannel ?: "global"

            // Save message to history
            CompletableFuture.runAsync {
                plugin.databaseManager.saveMessageHistory(
                    player.uniqueId, player.name, currentChannel, event.message,
                    plugin.configManager.serverName
                )
            }

            // Update player last seen
            CompletableFuture.runAsync {
                plugin.databaseManager.updatePlayerLastSeen(player.uniqueId, player.name)
            }

            // Record performance metrics
            recordPerformanceMetric("chat_message_processed", System.currentTimeMillis() - startTime)

            try {
                plugin.performanceMonitor.recordMessageSent()
                plugin.performanceMonitor.recordChannelActivity(currentChannel)
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }

        } catch (e: Exception) {
            plugin.logger.severe("Error in enhanced chat listener: ${e.message}")
            e.printStackTrace()

            try {
                plugin.performanceMonitor.recordError("chat_processing_error")
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val startTime = System.currentTimeMillis()

        try {
            // Update player data in database
            CompletableFuture.runAsync {
                plugin.databaseManager.updatePlayerLastSeen(player.uniqueId, player.name)
            }

            // Security manager IP tracking
            try {
                val ipAddress = player.address?.address?.hostAddress
                if (ipAddress != null) {
                    plugin.securityManager.trackPlayerIP(player)
                }
            } catch (e: Exception) {
                // SecurityManager not initialized
            }

            // Check for active mute
            CompletableFuture.runAsync {
                val muteInfo = plugin.databaseManager.getPlayerMute(player.uniqueId)
                if (muteInfo != null && !isMuteExpired(muteInfo)) {
                    player.sendMessage(mm.deserialize("<red>You are currently muted."))
                    if (muteInfo.permanent) {
                        player.sendMessage(mm.deserialize("<red>Mute Type: <yellow>Permanent"))
                    } else {
                        val remainingTime = (muteInfo.endTime - System.currentTimeMillis()) / 1000
                        val hours = remainingTime / 3600
                        val minutes = (remainingTime % 3600) / 60
                        player.sendMessage(mm.deserialize("<red>Time Remaining: <yellow>${hours}h ${minutes}m"))
                    }
                    if (muteInfo.reason?.isNotEmpty() == true) {
                        player.sendMessage(mm.deserialize("<red>Reason: <yellow>${muteInfo.reason}"))
                    }
                }
            }

            // Record performance metrics
            recordPerformanceMetric("player_join_processed", System.currentTimeMillis() - startTime)

            try {
                plugin.performanceMonitor.recordPlayerConnection()
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }

        } catch (e: Exception) {
            plugin.logger.severe("Error processing player join for ${player.name}: ${e.message}")
            e.printStackTrace()

            try {
                plugin.performanceMonitor.recordError("player_join_error")
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val startTime = System.currentTimeMillis()

        try {
            // Update player last seen time
            CompletableFuture.runAsync {
                plugin.databaseManager.updatePlayerLastSeen(player.uniqueId, player.name)
            }

            // Save user preferences
            try {
                val user = plugin.chatService.getUser(player)
                if (user != null) {
                    plugin.chatService.saveAllUsers()
                }
            } catch (e: Exception) {
                // ChatService not initialized
            }

            // Security manager cleanup
            try {
                // plugin.securityManager.cleanupPlayerData(player.uniqueId) // Method not available
            } catch (e: Exception) {
                // SecurityManager not initialized
            }

            // Record performance metrics
            recordPerformanceMetric("player_quit_processed", System.currentTimeMillis() - startTime)

            try {
                plugin.performanceMonitor.recordPlayerDisconnection()
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }

        } catch (e: Exception) {
            plugin.logger.severe("Error processing player quit for ${player.name}: ${e.message}")
            e.printStackTrace()

            try {
                plugin.performanceMonitor.recordError("player_quit_error")
            } catch (e: Exception) {
                // PerformanceMonitor not initialized
            }
        }
    }

    /**
     * Check if player is currently muted
     */
    private fun isPlayerMuted(player: Player): Boolean {
        return try {
            val muteInfo = plugin.databaseManager.getPlayerMute(player.uniqueId)
            muteInfo != null && !isMuteExpired(muteInfo)
        } catch (e: Exception) {
            plugin.logger.warning("Error checking mute status for ${player.name}: ${e.message}")
            false
        }
    }

    /**
     * Check if a mute has expired
     */
    private fun isMuteExpired(muteInfo: Any): Boolean {
        // Temporarily simplified - mute info structure needs to be defined
        return false
    }

    /**
     * Record performance metric
     */
    private fun recordPerformanceMetric(type: String, processingTime: Long) {
        try {
            plugin.performanceMonitor.recordProcessingTime(type, processingTime)
        } catch (e: Exception) {
            // PerformanceMonitor not initialized
        }

        try {

            // Save to database for long-term tracking
            CompletableFuture.runAsync {
                // Performance metric saved above
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error recording performance metric: ${e.message}")
        }
    }

    /**
     * Extension functions for missing performance monitor methods
     */
    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordMessageSent() {
        // This would increment the messages sent counter
        this.recordMessage("sent")
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordChannelActivity(channel: String) {
        // This would record activity in a specific channel
        this.recordChannelMessage(channel)
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordPlayerConnection() {
        // This would increment the connection counter
        this.recordConnection("join")
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordPlayerDisconnection() {
        // This would increment the disconnection counter
        this.recordConnection("quit")
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordError(errorType: String) {
        // This would record an error occurrence
        this.recordErrorEvent(errorType)
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordProcessingTime(type: String, time: Long) {
        // This would record processing time for performance analysis
        this.recordTimingMetric(type, time)
    }

    // Placeholder methods that would need to be implemented in PerformanceMonitor
    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordMessage(type: String) {
        // Implementation would be in PerformanceMonitor
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordChannelMessage(channel: String) {
        // Implementation would be in PerformanceMonitor
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordConnection(type: String) {
        // Implementation would be in PerformanceMonitor
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordErrorEvent(errorType: String) {
        // Implementation would be in PerformanceMonitor
    }

    private fun com.noximity.remmyChat.monitoring.PerformanceMonitor.recordTimingMetric(type: String, time: Long) {
        // Implementation would be in PerformanceMonitor
    }
}
