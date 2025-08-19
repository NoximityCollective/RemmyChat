package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Command to show status and information about all implemented features
 */
class FeaturesCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("remmychat.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "status" -> showFeatureStatus(sender)
            "metrics" -> showFeatureMetrics(sender)
            "security" -> showSecurityStatus(sender)
            "trade" -> showTradeFeatures(sender)
            "help" -> showHelpFeatures(sender)
            "event" -> showEventFeatures(sender)
            "discord" -> showDiscordFeatures(sender)
            "database" -> showDatabaseFeatures(sender)
            "groups" -> showGroupFeatures(sender)
            else -> showMainHelp(sender)
        }

        return true
    }

    private fun showMainHelp(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("RemmyChat Features", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        sender.sendMessage(Component.text()
            .append(Component.text("• ", NamedTextColor.GRAY))
            .append(createClickableCommand("/remmychat features status", "Show overall feature status"))
            .build())

        sender.sendMessage(Component.text()
            .append(Component.text("• ", NamedTextColor.GRAY))
            .append(createClickableCommand("/remmychat features metrics", "Show feature performance metrics"))
            .build())

        sender.sendMessage(Component.text()
            .append(Component.text("• ", NamedTextColor.GRAY))
            .append(createClickableCommand("/remmychat features security", "Show database security status"))
            .build())

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Feature Categories:", NamedTextColor.AQUA, TextDecoration.BOLD))

        val categories = mapOf(
            "trade" to "Trade Channel Features",
            "help" to "Help Channel Features",
            "event" to "Event Channel Features",
            "discord" to "Advanced Discord Integration",
            "database" to "Database Security Features",
            "groups" to "Group Behavior Management"
        )

        categories.forEach { (category, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("  • ", NamedTextColor.GRAY))
                .append(createClickableCommand("/remmychat features $category", description))
                .build())
        }
    }

    private fun showFeatureStatus(sender: CommandSender) {
        val report = plugin.featureManager.getFeatureReport()

        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Feature Status Overview", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        sender.sendMessage(Component.text()
            .append(Component.text("Total Features Enabled: ", NamedTextColor.GRAY))
            .append(Component.text(report.enabledFeatures.size.toString(), NamedTextColor.GREEN, TextDecoration.BOLD))
            .build())

        sender.sendMessage(Component.empty())

        val featureDescriptions = mapOf(
            "trade_channel" to "Trade Channel (Price detection, item linking, auto-expire)",
            "help_channel" to "Help Channel (Ticket system, FAQ integration)",
            "event_channel" to "Event Channel (Announcements, auto-broadcast, scheduled messages)",
            "group_behavior" to "Group Behavior (Channel access, message limits, mention restrictions)",
            "advanced_discord" to "Advanced Discord (Webhooks, role sync, statistics)",
            "database_security" to "Database Security (Encryption, audit logging, remote backup)"
        )

        featureDescriptions.forEach { (feature, description) ->
            val isEnabled = report.enabledFeatures.contains(feature)
            val status = if (isEnabled) "✓" else "✗"
            val color = if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED

            sender.sendMessage(Component.text()
                .append(Component.text("$status ", color, TextDecoration.BOLD))
                .append(Component.text(description, if (isEnabled) NamedTextColor.WHITE else NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text()
            .append(Component.text("Last Updated: ", NamedTextColor.GRAY))
            .append(Component.text(formatTimestamp(report.lastUpdated), NamedTextColor.WHITE))
            .build())
    }

    private fun showFeatureMetrics(sender: CommandSender) {
        val metrics = plugin.featureManager.getFeatureMetrics()

        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Feature Performance Metrics", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        if (metrics.isEmpty()) {
            sender.sendMessage(Component.text("No metrics available. Features may be disabled.", NamedTextColor.GRAY))
            return
        }

        metrics.forEach { (feature, data) ->
            sender.sendMessage(Component.text()
                .append(Component.text("${feature.replace('_', ' ').uppercase()}:", NamedTextColor.AQUA, TextDecoration.BOLD))
                .build())

            data.forEach { (key, value) ->
                sender.sendMessage(Component.text()
                    .append(Component.text("  • ", NamedTextColor.GRAY))
                    .append(Component.text("${key.replace('_', ' ')}: ", NamedTextColor.WHITE))
                    .append(Component.text(value.toString(), NamedTextColor.GREEN))
                    .build())
            }
            sender.sendMessage(Component.empty())
        }
    }

    private fun showSecurityStatus(sender: CommandSender) {
        val securityCheck = plugin.featureManager.performSecurityCheck()

        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Database Security Status", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val riskColor = when (securityCheck.riskLevel) {
            "NONE" -> NamedTextColor.GREEN
            "LOW" -> NamedTextColor.YELLOW
            "MEDIUM" -> NamedTextColor.GOLD
            "HIGH" -> NamedTextColor.RED
            else -> NamedTextColor.GRAY
        }

        sender.sendMessage(Component.text()
            .append(Component.text("Security Status: ", NamedTextColor.GRAY))
            .append(Component.text(if (securityCheck.enabled) "ENABLED" else "DISABLED",
                if (securityCheck.enabled) NamedTextColor.GREEN else NamedTextColor.RED, TextDecoration.BOLD))
            .build())

        sender.sendMessage(Component.text()
            .append(Component.text("Risk Level: ", NamedTextColor.GRAY))
            .append(Component.text(securityCheck.riskLevel, riskColor, TextDecoration.BOLD))
            .build())

        if (securityCheck.issues.isNotEmpty()) {
            sender.sendMessage(Component.empty())
            sender.sendMessage(Component.text("Security Issues:", NamedTextColor.RED, TextDecoration.BOLD))
            securityCheck.issues.forEach { issue ->
                sender.sendMessage(Component.text()
                    .append(Component.text("  ⚠ ", NamedTextColor.YELLOW))
                    .append(Component.text(issue, NamedTextColor.WHITE))
                    .build())
            }
        }

        if (securityCheck.recommendations.isNotEmpty()) {
            sender.sendMessage(Component.empty())
            sender.sendMessage(Component.text("Recommendations:", NamedTextColor.BLUE, TextDecoration.BOLD))
            securityCheck.recommendations.forEach { recommendation ->
                sender.sendMessage(Component.text()
                    .append(Component.text("  💡 ", NamedTextColor.YELLOW))
                    .append(Component.text(recommendation, NamedTextColor.WHITE))
                    .build())
            }
        }
    }

    private fun showTradeFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Trade Channel Features", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val tradeStats = plugin.tradeChannelHandler.getTradeStatistics()

        val features = listOf(
            "Price Detection" to "Automatically detects and highlights prices in trade messages",
            "Item Linking" to "Convert [item] text into clickable item displays",
            "Auto-Expire Posts" to "Trade posts automatically expire after configured time",
            "Keyword Requirements" to "Require WTS/WTB/WTT keywords in trade messages"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Current Statistics:", NamedTextColor.AQUA, TextDecoration.BOLD))
        sender.sendMessage(Component.text()
            .append(Component.text("  Active Posts: ", NamedTextColor.GRAY))
            .append(Component.text(tradeStats.activePosts.toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Recent Posts: ", NamedTextColor.GRAY))
            .append(Component.text(tradeStats.recentPosts.toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Trading Players: ", NamedTextColor.GRAY))
            .append(Component.text(tradeStats.totalPlayersTrading.toString(), NamedTextColor.GREEN))
            .build())
    }

    private fun showHelpFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Help Channel Features", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val features = listOf(
            "Ticket System" to "Players can create support tickets for help",
            "FAQ Integration" to "Automatic FAQ suggestions based on message content",
            "Auto Staff Notification" to "Automatically notify online staff of help requests",
            "Help Request Detection" to "Smart detection of questions and help requests"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text()
            .append(Component.text("Commands: ", NamedTextColor.AQUA, TextDecoration.BOLD))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  • ", NamedTextColor.GRAY))
            .append(createClickableCommand("/ticket create <title> <description>", "Create a support ticket"))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  • ", NamedTextColor.GRAY))
            .append(createClickableCommand("/faq search <keyword>", "Search FAQ entries"))
            .build())
    }

    private fun showEventFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Event Channel Features", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val eventStats = plugin.eventChannelHandler.getEventStatistics()

        val features = listOf(
            "Announcement Mode" to "Restrict posting to designated announcers only",
            "Auto-Broadcast" to "Automatically send rotating broadcast messages",
            "Scheduled Messages" to "Schedule messages for future delivery",
            "Event Formatting" to "Special formatting for announcements and events"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Statistics:", NamedTextColor.AQUA, TextDecoration.BOLD))
        sender.sendMessage(Component.text()
            .append(Component.text("  Announcements Sent: ", NamedTextColor.GRAY))
            .append(Component.text(eventStats.announcementsSent.toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Broadcasts Sent: ", NamedTextColor.GRAY))
            .append(Component.text(eventStats.broadcastsSent.toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Scheduled Messages: ", NamedTextColor.GRAY))
            .append(Component.text(eventStats.activeScheduledMessages.toString(), NamedTextColor.GREEN))
            .build())
    }

    private fun showDiscordFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Advanced Discord Integration", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val discordStats = plugin.advancedDiscordFeatures.getStatistics()

        val features = listOf(
            "Custom Webhooks" to "Send messages via Discord webhooks with player avatars",
            "Role Synchronization" to "Sync Minecraft groups with Discord roles",
            "Periodic Statistics" to "Automatically send server statistics to Discord",
            "Moderation Alerts" to "Send moderation actions to Discord channels",
            "Chat Violation Logging" to "Log chat violations to Discord for review"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Integration Statistics:", NamedTextColor.AQUA, TextDecoration.BOLD))
        sender.sendMessage(Component.text()
            .append(Component.text("  Messages Sent: ", NamedTextColor.GRAY))
            .append(Component.text(discordStats["messagesSent"].toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Webhooks Sent: ", NamedTextColor.GRAY))
            .append(Component.text(discordStats["webhooksSent"].toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Error Count: ", NamedTextColor.GRAY))
            .append(Component.text(discordStats["errorCount"].toString(), NamedTextColor.RED))
            .build())
    }

    private fun showDatabaseFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Database Security Features", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val dbStats = plugin.databaseSecurityManager.getSecurityStatistics()

        val features = listOf(
            "Data Encryption" to "Encrypt sensitive data in the database using AES-256",
            "Audit Logging" to "Log all database operations for security compliance",
            "Remote Backup" to "Automatically upload encrypted backups to remote locations",
            "IP Whitelisting" to "Restrict database access to specific IP addresses",
            "Security Monitoring" to "Monitor and alert on security issues"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Security Statistics:", NamedTextColor.AQUA, TextDecoration.BOLD))
        sender.sendMessage(Component.text()
            .append(Component.text("  Encryption Enabled: ", NamedTextColor.GRAY))
            .append(Component.text(dbStats["encryptionEnabled"].toString(),
                if (dbStats["encryptionEnabled"] == true) NamedTextColor.GREEN else NamedTextColor.RED))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Audit Entries: ", NamedTextColor.GRAY))
            .append(Component.text(dbStats["auditEntriesCount"].toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Remote Backups Sent: ", NamedTextColor.GRAY))
            .append(Component.text(dbStats["backupsSent"].toString(), NamedTextColor.GREEN))
            .build())
    }

    private fun showGroupFeatures(sender: CommandSender) {
        sender.sendMessage(Component.text()
            .append(Component.text("═══ ", NamedTextColor.GOLD))
            .append(Component.text("Group Behavior Management", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ═══", NamedTextColor.GOLD))
            .build())

        val groupStats = plugin.groupBehaviorManager.getChannelAccessStats()

        val features = listOf(
            "Channel Access Control" to "Restrict channel access based on player groups",
            "Message Length Limits" to "Different message length limits per group",
            "Mention Restrictions" to "Control who can mention @everyone, @staff, etc.",
            "Mention Cooldowns" to "Group-based cooldowns for mentions",
            "Interactive Mentions" to "Clickable mentions with hover information"
        )

        features.forEach { (name, description) ->
            sender.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
                .build())
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Behavior Statistics:", NamedTextColor.AQUA, TextDecoration.BOLD))
        sender.sendMessage(Component.text()
            .append(Component.text("  Channel Rules: ", NamedTextColor.GRAY))
            .append(Component.text(groupStats["channelRulesCount"].toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Group Limits: ", NamedTextColor.GRAY))
            .append(Component.text(groupStats["groupLimitsCount"].toString(), NamedTextColor.GREEN))
            .build())
        sender.sendMessage(Component.text()
            .append(Component.text("  Mention Rules: ", NamedTextColor.GRAY))
            .append(Component.text(groupStats["mentionRulesCount"].toString(), NamedTextColor.GREEN))
            .build())
    }

    private fun createClickableCommand(command: String, description: String): Component {
        return Component.text(description, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text("Click to run: $command", NamedTextColor.GRAY)))
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("status", "metrics", "security", "trade", "help", "event", "discord", "database", "groups")
                .filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
}
