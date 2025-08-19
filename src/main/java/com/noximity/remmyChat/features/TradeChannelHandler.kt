package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.UUID

/**
 * Handles trade channel specific features like price detection, item linking, and keyword requirements
 */
class TradeChannelHandler(private val plugin: RemmyChat) {

    private val tradePostExecutor = Executors.newSingleThreadScheduledExecutor()

    // Trade post tracking
    private val activeTradePosts = ConcurrentHashMap<String, TradePost>()
    private val playerLastTradePost = ConcurrentHashMap<UUID, Long>()

    // Price detection patterns
    private val pricePatterns = listOf(
        Pattern.compile("\\$([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:coins?|dollars?|\\$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:emeralds?|diamonds?)", Pattern.CASE_INSENSITIVE)
    )

    // Keyword patterns
    private val requiredKeywords = setOf("WTS", "WTB", "WTT", "SELLING", "BUYING", "TRADING")
    private val keywordPattern = Pattern.compile("\\b(?:WTS|WTB|WTT|SELLING|BUYING|TRADING)\\b", Pattern.CASE_INSENSITIVE)

    // Item linking pattern
    private val itemLinkPattern = Pattern.compile("\\[([^\\]]+)\\]")

    fun initialize() {
        // Start auto-expire cleanup task
        tradePostExecutor.scheduleAtFixedRate({
            cleanupExpiredTradePosts()
        }, 1, 1, TimeUnit.MINUTES)

        plugin.debugLog("TradeChannelHandler initialized")
    }

    /**
     * Process a trade channel message
     */
    fun processTradeMessage(player: Player, channel: Channel, message: String): TradeMessageResult {
        val result = TradeMessageResult()

        // Check if channel requires keywords
        if (channel.requireKeywords && !hasRequiredKeywords(message)) {
            result.isValid = false
            result.errorMessage = "Trade messages must include WTS (Want to Sell), WTB (Want to Buy), or WTT (Want to Trade)"
            return result
        }

        // Detect prices if enabled
        if (channel.priceDetection) {
            val detectedPrices = detectPrices(message)
            result.detectedPrices = detectedPrices

            if (detectedPrices.isNotEmpty()) {
                result.processedMessage = highlightPrices(message, detectedPrices)
            }
        }

        // Process item links if enabled
        if (channel.itemLinking) {
            val linkedMessage = processItemLinks(player, message)
            result.processedMessage = linkedMessage.ifEmpty { result.processedMessage }
            result.hasItemLinks = linkedMessage != message
        }

        // Handle auto-expire if enabled
        if (channel.autoExpire > 0) {
            val postId = createTradePost(player, channel, message, channel.autoExpire.toLong())
            result.tradePostId = postId
        }

        // Track player's last trade post
        playerLastTradePost[player.uniqueId] = System.currentTimeMillis()

        result.isValid = true
        if (result.processedMessage.isEmpty()) {
            result.processedMessage = message
        }

        return result
    }

    /**
     * Check if message contains required keywords
     */
    private fun hasRequiredKeywords(message: String): Boolean {
        return keywordPattern.matcher(message).find()
    }

    /**
     * Detect prices in the message
     */
    private fun detectPrices(message: String): List<DetectedPrice> {
        val prices = mutableListOf<DetectedPrice>()

        for (pattern in pricePatterns) {
            val matcher = pattern.matcher(message)
            while (matcher.find()) {
                val priceText = matcher.group()
                val priceValue = extractPriceValue(matcher.group(1) ?: matcher.group())

                if (priceValue > 0) {
                    prices.add(DetectedPrice(
                        originalText = priceText,
                        value = priceValue,
                        startIndex = matcher.start(),
                        endIndex = matcher.end()
                    ))
                }
            }
        }

        return prices
    }

    /**
     * Extract numeric value from price string
     */
    private fun extractPriceValue(priceString: String): Double {
        return try {
            priceString.replace(",", "").replace("$", "").toDouble()
        } catch (e: NumberFormatException) {
            0.0
        }
    }

    /**
     * Highlight detected prices in the message
     */
    private fun highlightPrices(message: String, prices: List<DetectedPrice>): String {
        var result = message

        // Sort by start index in reverse order to avoid index shifting
        val sortedPrices = prices.sortedByDescending { it.startIndex }

        for (price in sortedPrices) {
            val highlightedPrice = "<hover:show_text:'Detected price: ${price.value}'><color:#00FF00>${price.originalText}</color></hover>"
            result = result.substring(0, price.startIndex) + highlightedPrice + result.substring(price.endIndex)
        }

        return result
    }

    /**
     * Process item links in the message
     */
    private fun processItemLinks(player: Player, message: String): String {
        val matcher = itemLinkPattern.matcher(message)
        val result = StringBuffer()

        while (matcher.find()) {
            val itemName = matcher.group(1)
            val linkedItem = createItemLink(player, itemName)
            matcher.appendReplacement(result, linkedItem)
        }

        matcher.appendTail(result)
        return result.toString()
    }

    /**
     * Create a clickable item link
     */
    private fun createItemLink(player: Player, itemName: String): String {
        // Try to find matching item in player's inventory
        val matchingItem = findItemInInventory(player, itemName)

        return if (matchingItem != null) {
            val itemDisplayName = getItemDisplayName(matchingItem)
            val itemLore = getItemLore(matchingItem)

            "<hover:show_text:'$itemDisplayName\\n$itemLore'><click:run_command:'/iteminfo ${matchingItem.type.name}'><color:#FFD700>[$itemDisplayName]</color></click></hover>"
        } else {
            // Fallback to basic item lookup
            val material = Material.values().find { it.name.equals(itemName, ignoreCase = true) }
            if (material != null) {
                "<hover:show_text:'${material.name.lowercase().replace('_', ' ')}'><color:#FFD700>[$itemName]</color></hover>"
            } else {
                "[$itemName]" // No change if item not found
            }
        }
    }

    /**
     * Find item in player's inventory that matches the name
     */
    private fun findItemInInventory(player: Player, itemName: String): ItemStack? {
        return player.inventory.contents.find { item ->
            item != null && (
                item.type.name.equals(itemName, ignoreCase = true) ||
                (item.itemMeta?.displayName?.contains(itemName, ignoreCase = true) == true)
            )
        }
    }

    /**
     * Get display name for an item
     */
    private fun getItemDisplayName(item: ItemStack): String {
        return item.itemMeta?.displayName ?: item.type.name.lowercase().replace('_', ' ')
    }

    /**
     * Get lore for an item
     */
    private fun getItemLore(item: ItemStack): String {
        val lore = item.itemMeta?.lore
        return if (lore != null && lore.isNotEmpty()) {
            lore.joinToString("\\n") { it }
        } else {
            "No description"
        }
    }

    /**
     * Create a new trade post with auto-expire
     */
    private fun createTradePost(player: Player, channel: Channel, message: String, expireAfterSeconds: Long): String {
        val postId = "${player.uniqueId}-${System.currentTimeMillis()}"
        val expiryTime = System.currentTimeMillis() + (expireAfterSeconds * 1000)

        val tradePost = TradePost(
            id = postId,
            playerUuid = player.uniqueId,
            playerName = player.name,
            channelName = channel.name,
            message = message,
            createdAt = System.currentTimeMillis(),
            expiresAt = expiryTime
        )

        activeTradePosts[postId] = tradePost

        // Schedule individual expiry
        tradePostExecutor.schedule({
            expireTradePost(postId)
        }, expireAfterSeconds, TimeUnit.SECONDS)

        return postId
    }

    /**
     * Expire a specific trade post
     */
    private fun expireTradePost(postId: String) {
        val tradePost = activeTradePosts.remove(postId)
        if (tradePost != null) {
            val player = plugin.server.getPlayer(tradePost.playerUuid)
            if (player != null && player.isOnline) {
                player.sendMessage(
                    Component.text("Your trade post has expired: ", NamedTextColor.YELLOW)
                        .append(Component.text(tradePost.message.take(50) + "...", NamedTextColor.GRAY))
                )
            }

            plugin.debugLog("Trade post expired: $postId")
        }
    }

    /**
     * Clean up expired trade posts
     */
    private fun cleanupExpiredTradePosts() {
        val currentTime = System.currentTimeMillis()
        val expiredPosts = activeTradePosts.values.filter { it.expiresAt <= currentTime }

        for (post in expiredPosts) {
            expireTradePost(post.id)
        }

        if (expiredPosts.isNotEmpty()) {
            plugin.debugLog("Cleaned up ${expiredPosts.size} expired trade posts")
        }
    }

    /**
     * Get active trade posts for a player
     */
    fun getPlayerTradePosts(playerUuid: UUID): List<TradePost> {
        return activeTradePosts.values.filter { it.playerUuid == playerUuid }
    }

    /**
     * Cancel a trade post
     */
    fun cancelTradePost(playerUuid: UUID, postId: String): Boolean {
        val post = activeTradePosts[postId]
        return if (post != null && post.playerUuid == playerUuid) {
            activeTradePosts.remove(postId)
            true
        } else {
            false
        }
    }

    /**
     * Get trade statistics
     */
    fun getTradeStatistics(): TradeStatistics {
        val currentTime = System.currentTimeMillis()
        val recentPosts = activeTradePosts.values.filter { currentTime - it.createdAt < 3600000 } // Last hour

        return TradeStatistics(
            activePosts = activeTradePosts.size,
            recentPosts = recentPosts.size,
            totalPlayersTrading = activeTradePosts.values.map { it.playerUuid }.toSet().size
        )
    }

    fun shutdown() {
        tradePostExecutor.shutdown()
    }

    // Data classes
    data class TradeMessageResult(
        var isValid: Boolean = false,
        var errorMessage: String = "",
        var processedMessage: String = "",
        var detectedPrices: List<DetectedPrice> = emptyList(),
        var hasItemLinks: Boolean = false,
        var tradePostId: String? = null
    )

    data class DetectedPrice(
        val originalText: String,
        val value: Double,
        val startIndex: Int,
        val endIndex: Int
    )

    data class TradePost(
        val id: String,
        val playerUuid: UUID,
        val playerName: String,
        val channelName: String,
        val message: String,
        val createdAt: Long,
        val expiresAt: Long
    )

    data class TradeStatistics(
        val activePosts: Int,
        val recentPosts: Int,
        val totalPlayersTrading: Int
    )
}
