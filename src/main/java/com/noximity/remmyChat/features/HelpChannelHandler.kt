package com.noximity.remmyChat.features

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.models.Channel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Handles help channel specific features including ticket system and FAQ integration
 */
class HelpChannelHandler(private val plugin: RemmyChat) : Listener {

    private val ticketExecutor = Executors.newSingleThreadScheduledExecutor()

    // Ticket system
    private val activeTickets = ConcurrentHashMap<String, HelpTicket>()
    private val playerTickets = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val staffMembers = ConcurrentHashMap<UUID, Long>() // UUID to last seen time

    // FAQ system
    private val faqEntries = ConcurrentHashMap<String, FAQEntry>()
    private val faqKeywords = ConcurrentHashMap<String, String>() // keyword -> faq id

    // Configuration
    private var ticketSystemEnabled = false
    private var faqIntegrationEnabled = true
    private var autoNotifyStaff = true
    private var maxTicketsPerPlayer = 3
    private var ticketAutoCloseHours = 48
    private var helpKeywords = setOf("help", "support", "issue", "problem", "bug", "stuck", "how")

    // Patterns for automatic detection
    private val questionPattern = Pattern.compile("\\b(?:how|what|where|when|why|can|could|would|should)\\b.*\\?", Pattern.CASE_INSENSITIVE)
    private val helpRequestPattern = Pattern.compile("\\b(?:help|support|assist|stuck|problem|issue|bug)\\b", Pattern.CASE_INSENSITIVE)

    fun initialize() {
        loadConfiguration()
        loadFAQEntries()
        updateStaffList()

        // Register as listener
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Start periodic tasks
        startTicketCleanupTask()
        startStaffUpdateTask()

        plugin.debugLog("HelpChannelHandler initialized - Tickets: $ticketSystemEnabled, FAQ: $faqIntegrationEnabled")
    }

    /**
     * Process a help channel message
     */
    fun processHelpMessage(player: Player, channel: Channel, message: String): HelpMessageResult {
        val result = HelpMessageResult()

        // Check for FAQ triggers first
        if (faqIntegrationEnabled) {
            val faqMatch = findFAQMatch(message)
            if (faqMatch != null) {
                result.faqSuggestion = faqMatch
                result.suggestedResponse = createFAQResponse(faqMatch)
            }
        }

        // Check if this looks like a help request
        if (looksLikeHelpRequest(message)) {
            result.isHelpRequest = true

            // Auto-notify staff if enabled
            if (autoNotifyStaff) {
                notifyOnlineStaff(player, message, channel)
                result.staffNotified = true
            }

            // Suggest creating a ticket if system is enabled
            if (ticketSystemEnabled && !hasActiveTicket(player)) {
                result.ticketSuggested = true
                result.ticketCreateMessage = createTicketSuggestion(player)
            }
        }

        // Add helpful formatting
        result.processedMessage = enhanceHelpMessage(message, result)

        return result
    }

    /**
     * Create a new help ticket
     */
    fun createTicket(player: Player, title: String, description: String): TicketResult {
        if (!ticketSystemEnabled) {
            return TicketResult(false, "Ticket system is disabled")
        }

        // Check if player has too many active tickets
        val playerActiveTickets = getPlayerActiveTickets(player.uniqueId)
        if (playerActiveTickets.size >= maxTicketsPerPlayer) {
            return TicketResult(false, "You have too many active tickets (${playerActiveTickets.size}/$maxTicketsPerPlayer)")
        }

        val ticketId = generateTicketId()
        val ticket = HelpTicket(
            id = ticketId,
            playerUuid = player.uniqueId,
            playerName = player.name,
            title = title,
            description = description,
            status = TicketStatus.OPEN,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            assignedStaff = null,
            responses = mutableListOf()
        )

        activeTickets[ticketId] = ticket
        playerTickets.computeIfAbsent(player.uniqueId) { mutableSetOf() }.add(ticketId)

        // Notify staff
        notifyStaffOfNewTicket(ticket)

        // Schedule auto-close
        scheduleTicketAutoClose(ticketId)

        return TicketResult(true, "Ticket #$ticketId created successfully", ticketId)
    }

    /**
     * Load configuration settings
     */
    private fun loadConfiguration() {
        val channelsConfig = plugin.configManager.getChannelsConfig()
        val helpSection = channelsConfig.getConfigurationSection("channels.help.help-features")

        if (helpSection != null) {
            ticketSystemEnabled = helpSection.getBoolean("ticket-system", false)
            faqIntegrationEnabled = helpSection.getBoolean("faq-integration", true)
            autoNotifyStaff = helpSection.getBoolean("auto-notify-staff", true)
            maxTicketsPerPlayer = helpSection.getInt("max-tickets-per-player", 3)
            ticketAutoCloseHours = helpSection.getInt("auto-close-hours", 48)
        }

        // Load help keywords
        val mainConfig = plugin.config
        val keywordsList = mainConfig.getStringList("help.keywords")
        if (keywordsList.isNotEmpty()) {
            helpKeywords = keywordsList.toSet()
        }
    }

    /**
     * Load FAQ entries from configuration
     */
    private fun loadFAQEntries() {
        faqEntries.clear()
        faqKeywords.clear()

        val mainConfig = plugin.config
        val faqSection = mainConfig.getConfigurationSection("help.faq")

        faqSection?.getKeys(false)?.forEach { faqId ->
            val entrySection = faqSection.getConfigurationSection(faqId)
            if (entrySection != null) {
                val entry = FAQEntry(
                    id = faqId,
                    question = entrySection.getString("question", ""),
                    answer = entrySection.getString("answer", ""),
                    keywords = entrySection.getStringList("keywords"),
                    category = entrySection.getString("category", "general")
                )

                faqEntries[faqId] = entry

                // Map keywords to FAQ ID
                entry.keywords.forEach { keyword ->
                    faqKeywords[keyword.lowercase()] = faqId
                }
            }
        }

        plugin.debugLog("Loaded ${faqEntries.size} FAQ entries")
    }

    /**
     * Update list of online staff members
     */
    private fun updateStaffList() {
        staffMembers.clear()
        val currentTime = System.currentTimeMillis()

        Bukkit.getOnlinePlayers().forEach { player ->
            if (isStaffMember(player)) {
                staffMembers[player.uniqueId] = currentTime
            }
        }
    }

    /**
     * Check if a player is a staff member
     */
    private fun isStaffMember(player: Player): Boolean {
        return player.hasPermission("remmychat.staff") ||
               player.hasPermission("remmychat.channel.staff") ||
               player.hasPermission("remmychat.channel.admin") ||
               player.hasPermission("remmychat.admin")
    }

    /**
     * Find matching FAQ entry for a message
     */
    private fun findFAQMatch(message: String): FAQEntry? {
        val messageLower = message.lowercase()

        // First try exact keyword matches
        for ((keyword, faqId) in faqKeywords) {
            if (messageLower.contains(keyword)) {
                return faqEntries[faqId]
            }
        }

        // Then try fuzzy matching on questions
        return faqEntries.values.find { faq ->
            val questionWords = faq.question.lowercase().split(" ")
            val messageWords = messageLower.split(" ")

            val matchingWords = questionWords.intersect(messageWords.toSet())
            matchingWords.size >= 2 // At least 2 words match
        }
    }

    /**
     * Check if message looks like a help request
     */
    private fun looksLikeHelpRequest(message: String): Boolean {
        return questionPattern.matcher(message).find() ||
               helpRequestPattern.matcher(message).find() ||
               helpKeywords.any { keyword -> message.contains(keyword, ignoreCase = true) }
    }

    /**
     * Check if player has an active ticket
     */
    private fun hasActiveTicket(player: Player): Boolean {
        return getPlayerActiveTickets(player.uniqueId).isNotEmpty()
    }

    /**
     * Get player's active tickets
     */
    private fun getPlayerActiveTickets(playerUuid: UUID): List<HelpTicket> {
        val ticketIds = playerTickets[playerUuid] ?: return emptyList()
        return ticketIds.mapNotNull { activeTickets[it] }.filter { it.status == TicketStatus.OPEN }
    }

    /**
     * Create FAQ response message
     */
    private fun createFAQResponse(faq: FAQEntry): Component {
        return Component.text()
            .append(Component.text("💡 ", NamedTextColor.YELLOW))
            .append(Component.text("FAQ: ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(faq.question, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("📝 ", NamedTextColor.GREEN))
            .append(Component.text(faq.answer, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(
                Component.text("[More Help]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/help faq ${faq.category}"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to see more FAQs in ${faq.category}")))
            )
            .build()
    }

    /**
     * Create ticket suggestion message
     */
    private fun createTicketSuggestion(player: Player): Component {
        return Component.text()
            .append(Component.text("🎫 ", NamedTextColor.BLUE))
            .append(Component.text("Need more help? ", NamedTextColor.GRAY))
            .append(
                Component.text("[Create Ticket]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand("/ticket create "))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to create a support ticket")))
            )
            .build()
    }

    /**
     * Notify online staff members of help request
     */
    private fun notifyOnlineStaff(player: Player, message: String, channel: Channel) {
        val notification = Component.text()
            .append(Component.text("🔔 ", NamedTextColor.YELLOW))
            .append(Component.text("Help request in ", NamedTextColor.GOLD))
            .append(Component.text(channel.getEffectiveDisplayName(), NamedTextColor.AQUA))
            .append(Component.text(" by ", NamedTextColor.GOLD))
            .append(Component.text(player.name, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Message: ", NamedTextColor.GRAY))
            .append(Component.text(message.take(100), NamedTextColor.WHITE))
            .append(if (message.length > 100) Component.text("...", NamedTextColor.GRAY) else Component.empty())
            .build()

        staffMembers.keys.forEach { staffUuid ->
            val staffPlayer = Bukkit.getPlayer(staffUuid)
            if (staffPlayer != null && staffPlayer.isOnline && staffPlayer.uniqueId != player.uniqueId) {
                staffPlayer.sendMessage(notification)
            }
        }
    }

    /**
     * Notify staff of new ticket
     */
    private fun notifyStaffOfNewTicket(ticket: HelpTicket) {
        val notification = Component.text()
            .append(Component.text("🎫 ", NamedTextColor.BLUE))
            .append(Component.text("New support ticket #${ticket.id}", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("Player: ", NamedTextColor.GRAY))
            .append(Component.text(ticket.playerName, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Title: ", NamedTextColor.GRAY))
            .append(Component.text(ticket.title, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(
                Component.text("[View Ticket]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/ticket view ${ticket.id}"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to view ticket details")))
            )
            .build()

        staffMembers.keys.forEach { staffUuid ->
            val staffPlayer = Bukkit.getPlayer(staffUuid)
            if (staffPlayer != null && staffPlayer.isOnline) {
                staffPlayer.sendMessage(notification)
            }
        }
    }

    /**
     * Enhance help message with helpful formatting
     */
    private fun enhanceHelpMessage(message: String, result: HelpMessageResult): String {
        var enhanced = message

        // Add help emoji for questions
        if (result.isHelpRequest && message.contains("?")) {
            enhanced = "❓ $enhanced"
        }

        return enhanced
    }

    /**
     * Generate unique ticket ID
     */
    private fun generateTicketId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (1000..9999).random()
        return "$timestamp$random"
    }

    /**
     * Schedule automatic ticket closure
     */
    private fun scheduleTicketAutoClose(ticketId: String) {
        ticketExecutor.schedule({
            val ticket = activeTickets[ticketId]
            if (ticket != null && ticket.status == TicketStatus.OPEN) {
                ticket.status = TicketStatus.AUTO_CLOSED
                ticket.updatedAt = System.currentTimeMillis()

                val player = Bukkit.getPlayer(ticket.playerUuid)
                if (player != null && player.isOnline) {
                    player.sendMessage(
                        Component.text("🎫 Your ticket #${ticket.id} has been automatically closed due to inactivity.", NamedTextColor.YELLOW)
                    )
                }

                plugin.debugLog("Auto-closed ticket: $ticketId")
            }
        }, ticketAutoCloseHours.toLong(), TimeUnit.HOURS)
    }

    /**
     * Start ticket cleanup task
     */
    private fun startTicketCleanupTask() {
        ticketExecutor.scheduleAtFixedRate({
            cleanupOldTickets()
        }, 1, 24, TimeUnit.HOURS) // Daily cleanup
    }

    /**
     * Start staff update task
     */
    private fun startStaffUpdateTask() {
        ticketExecutor.scheduleAtFixedRate({
            updateStaffList()
        }, 0, 5, TimeUnit.MINUTES) // Update every 5 minutes
    }

    /**
     * Clean up old tickets
     */
    private fun cleanupOldTickets() {
        val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days
        val toRemove = mutableListOf<String>()

        activeTickets.values.forEach { ticket ->
            if (ticket.status != TicketStatus.OPEN && ticket.updatedAt < cutoffTime) {
                toRemove.add(ticket.id)
            }
        }

        toRemove.forEach { ticketId ->
            val ticket = activeTickets.remove(ticketId)
            if (ticket != null) {
                playerTickets[ticket.playerUuid]?.remove(ticketId)
            }
        }

        if (toRemove.isNotEmpty()) {
            plugin.debugLog("Cleaned up ${toRemove.size} old tickets")
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (isStaffMember(event.player)) {
            staffMembers[event.player.uniqueId] = System.currentTimeMillis()
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        staffMembers.remove(event.player.uniqueId)
    }

    fun shutdown() {
        ticketExecutor.shutdown()
    }

    // Data classes
    data class HelpMessageResult(
        var isHelpRequest: Boolean = false,
        var faqSuggestion: FAQEntry? = null,
        var suggestedResponse: Component? = null,
        var staffNotified: Boolean = false,
        var ticketSuggested: Boolean = false,
        var ticketCreateMessage: Component? = null,
        var processedMessage: String = ""
    )

    data class TicketResult(
        val success: Boolean,
        val message: String,
        val ticketId: String? = null
    )

    data class HelpTicket(
        val id: String,
        val playerUuid: UUID,
        val playerName: String,
        val title: String,
        val description: String,
        var status: TicketStatus,
        val createdAt: Long,
        var updatedAt: Long,
        var assignedStaff: UUID? = null,
        val responses: MutableList<TicketResponse> = mutableListOf()
    )

    data class TicketResponse(
        val id: String,
        val ticketId: String,
        val authorUuid: UUID,
        val authorName: String,
        val message: String,
        val timestamp: Long,
        val isStaffResponse: Boolean
    )

    data class FAQEntry(
        val id: String,
        val question: String,
        val answer: String,
        val keywords: List<String>,
        val category: String
    )

    enum class TicketStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED,
        AUTO_CLOSED
    }
}
