package com.noximity.remmyChat.compatibility

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.lang.reflect.Method
import java.util.logging.Logger

/**
 * Utility class to handle version compatibility between Paper 1.21.x versions
 * Uses reflection to gracefully handle API differences
 */
object VersionCompatibility {

    private val logger: Logger = Bukkit.getLogger()
    private val serverVersion: String = Bukkit.getVersion()
    private val bukkitVersion: String = Bukkit.getBukkitVersion()

    // Cache reflection results to avoid repeated lookups
    private var hasAsyncChatEvent: Boolean? = null
    private var hasDisplayNameMethod: Boolean? = null
    private var hasPlayerListNameMethod: Boolean? = null

    /**
     * Gets the server version information
     */
    fun getServerInfo(): String {
        return "Server: $serverVersion, Bukkit: $bukkitVersion"
    }

    /**
     * Checks if the server supports the modern AsyncChatEvent (Paper 1.19+)
     * Falls back to legacy AsyncPlayerChatEvent if not available
     */
    fun supportsAsyncChatEvent(): Boolean {
        if (hasAsyncChatEvent == null) {
            hasAsyncChatEvent = try {
                Class.forName("io.papermc.paper.event.player.AsyncChatEvent")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
        return hasAsyncChatEvent!!
    }

    /**
     * Safely gets the display name from a player
     * Handles potential API changes in display name methods
     */
    fun getPlayerDisplayName(player: Player): String {
        return try {
            // Try modern method first
            when {
                hasDisplayNameComponent(player) -> {
                    val displayName = player.displayName()
                    PlainTextComponentSerializer.plainText().serialize(displayName)
                }
                else -> {
                    // Fallback to legacy method
                    @Suppress("DEPRECATION")
                    player.displayName ?: player.name
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get display name for ${player.name}: ${e.message}")
            player.name
        }
    }

    /**
     * Safely gets the player list name from a player
     */
    fun getPlayerListName(player: Player): String {
        return try {
            when {
                hasPlayerListNameComponent(player) -> {
                    val listName = player.playerListName()
                    if (listName != null) {
                        PlainTextComponentSerializer.plainText().serialize(listName)
                    } else {
                        player.name
                    }
                }
                else -> {
                    // Fallback to legacy method
                    @Suppress("DEPRECATION")
                    player.playerListName ?: player.name
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get player list name for ${player.name}: ${e.message}")
            player.name
        }
    }

    /**
     * Safely sends a message to a player using the best available method
     */
    fun sendMessage(player: Player, message: Component) {
        try {
            player.sendMessage(message)
        } catch (e: Exception) {
            logger.warning("Failed to send Component message to ${player.name}: ${e.message}")
            try {
                // Fallback to legacy string message
                val legacyMessage = LegacyComponentSerializer.legacySection().serialize(message)
                @Suppress("DEPRECATION")
                player.sendMessage(legacyMessage)
            } catch (e2: Exception) {
                logger.warning("Failed to send legacy message to ${player.name}: ${e2.message}")
            }
        }
    }

    /**
     * Safely sends a message to a player using string
     */
    fun sendMessage(player: Player, message: String) {
        try {
            player.sendMessage(message)
        } catch (e: Exception) {
            logger.warning("Failed to send string message to ${player.name}: ${e.message}")
        }
    }

    /**
     * Extracts message content from AsyncChatEvent or AsyncPlayerChatEvent
     */
    fun extractMessageFromChatEvent(event: Any): String? {
        return when (event) {
            is AsyncChatEvent -> {
                try {
                    PlainTextComponentSerializer.plainText().serialize(event.message())
                } catch (e: Exception) {
                    logger.warning("Failed to extract message from AsyncChatEvent: ${e.message}")
                    null
                }
            }
            is AsyncPlayerChatEvent -> {
                try {
                    @Suppress("DEPRECATION")
                    event.message
                } catch (e: Exception) {
                    logger.warning("Failed to extract message from AsyncPlayerChatEvent: ${e.message}")
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Gets the player from a chat event
     */
    fun getPlayerFromChatEvent(event: Any): Player? {
        return when (event) {
            is AsyncChatEvent -> event.player
            is AsyncPlayerChatEvent -> event.player
            else -> null
        }
    }

    /**
     * Cancels a chat event safely
     */
    fun cancelChatEvent(event: Any) {
        when (event) {
            is AsyncChatEvent -> event.isCancelled = true
            is AsyncPlayerChatEvent -> event.isCancelled = true
        }
    }

    /**
     * Checks if player has modern displayName() method returning Component
     */
    private fun hasDisplayNameComponent(player: Player): Boolean {
        if (hasDisplayNameMethod == null) {
            hasDisplayNameMethod = try {
                val method = player.javaClass.getMethod("displayName")
                method.returnType == Component::class.java
            } catch (e: Exception) {
                false
            }
        }
        return hasDisplayNameMethod!!
    }

    /**
     * Checks if player has modern playerListName() method returning Component
     */
    private fun hasPlayerListNameComponent(player: Player): Boolean {
        if (hasPlayerListNameMethod == null) {
            hasPlayerListNameMethod = try {
                val method = player.javaClass.getMethod("playerListName")
                method.returnType == Component::class.java
            } catch (e: Exception) {
                false
            }
        }
        return hasPlayerListNameMethod!!
    }

    /**
     * Safely calls a method using reflection with error handling
     */
    private fun <T> safeReflectionCall(
        obj: Any,
        methodName: String,
        returnType: Class<T>,
        vararg args: Any
    ): T? {
        return try {
            val method = if (args.isEmpty()) {
                obj.javaClass.getMethod(methodName)
            } else {
                val paramTypes = args.map { it.javaClass }.toTypedArray()
                obj.javaClass.getMethod(methodName, *paramTypes)
            }

            val result = method.invoke(obj, *args)
            if (returnType.isInstance(result)) {
                returnType.cast(result)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.fine("Reflection call failed for $methodName: ${e.message}")
            null
        }
    }

    /**
     * Checks if a method exists on a class
     */
    fun hasMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Boolean {
        return try {
            clazz.getMethod(methodName, *paramTypes)
            true
        } catch (e: NoSuchMethodException) {
            false
        }
    }

    /**
     * Gets the Minecraft version from the server
     */
    fun getMinecraftVersion(): String {
        return try {
            val version = Bukkit.getMinecraftVersion()
            version
        } catch (e: Exception) {
            // Fallback parsing from bukkit version
            val bukkitVer = Bukkit.getBukkitVersion()
            val versionPart = bukkitVer.split("-")[0]
            versionPart
        }
    }

    /**
     * Checks if the current server version is at least the specified version
     */
    fun isVersionAtLeast(major: Int, minor: Int, patch: Int = 0): Boolean {
        return try {
            val currentVersion = getMinecraftVersion()
            val parts = currentVersion.split(".")

            val currentMajor = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val currentMinor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val currentPatch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            when {
                currentMajor > major -> true
                currentMajor < major -> false
                currentMinor > minor -> true
                currentMinor < minor -> false
                currentPatch >= patch -> true
                else -> false
            }
        } catch (e: Exception) {
            logger.warning("Failed to parse version for comparison: ${e.message}")
            true // Assume newer version if parsing fails
        }
    }

    /**
     * Safely fires an event, ensuring it's called from the main thread
     */
    fun <T : org.bukkit.event.Event> safeFireEvent(plugin: org.bukkit.plugin.Plugin, event: T) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.getPluginManager().callEvent(event)
            })
        }
    }

    /**
     * Logs version compatibility information
     */
    fun logCompatibilityInfo(logger: Logger) {
        logger.info("RemmyChat Version Compatibility:")
        logger.info("  Server: $serverVersion")
        logger.info("  Bukkit: $bukkitVersion")
        logger.info("  Minecraft: ${getMinecraftVersion()}")
        logger.info("  AsyncChatEvent supported: ${supportsAsyncChatEvent()}")
        logger.info("  Version 1.21+: ${isVersionAtLeast(1, 21)}")
    }
}
