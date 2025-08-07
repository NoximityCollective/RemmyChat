package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Enhanced reload command with support for individual component reloading
 * and comprehensive error handling with detailed status reporting.
 */
class ReloadCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

    // Available reload components with their aliases
    private val components = mapOf(
        "all" to setOf("all"),
        "config" to setOf("config", "main", "cfg"),
        "messages" to setOf("messages", "msg", "lang"),
        "channels" to setOf("channels", "ch", "channel"),
        "groups" to setOf("groups", "group", "grp"),
        "discord" to setOf("discord", "discordsrv"),
        "crossserver" to setOf("crossserver", "cross-server", "cs", "cross"),
        "maintenance" to setOf("maintenance", "maint"),
        "placeholders" to setOf("placeholders", "papi", "placeholder"),
        "database" to setOf("database", "db", "data"),
        "templates" to setOf("templates", "temp", "template"),
        "symbols" to setOf("symbols", "sym", "symbol"),
        "fix-placeholders" to setOf("fix-placeholders", "fix-placeholder", "placeholder-fix")
    )

    // Component dependencies
    private val dependencies = mapOf(
        "channels" to listOf("config"),
        "groups" to listOf("config"),
        "discord" to listOf("config"),
        "crossserver" to listOf("config"),
        "templates" to listOf("config"),
        "symbols" to listOf("config")
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        // Check permissions
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender)
            return true
        }

        // Determine component to reload
        val component = if (args.isEmpty()) "all" else args[0].lowercase()
        val resolvedComponent = resolveComponent(component)

        if (resolvedComponent == null) {
            sendUnknownComponentMessage(sender, component)
            return true
        }

        // Execute reload
        executeReload(sender, resolvedComponent)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        if (!hasPermission(sender) || args.size > 1) {
            return emptyList()
        }

        val input = if (args.isNotEmpty()) args[0].lowercase() else ""
        return getAllAliases()
            .filter { it.startsWith(input) }
            .sorted()
    }

    private fun hasPermission(sender: CommandSender): Boolean {
        return sender !is Player || sender.hasPermission("remmychat.admin")
    }

    private fun resolveComponent(input: String): String? {
        return components.entries.find { (_, aliases) ->
            aliases.any { it.equals(input, ignoreCase = true) }
        }?.key
    }

    private fun getAllAliases(): List<String> {
        return components.values.flatten()
    }

    private fun executeReload(sender: CommandSender, component: String) {
        when (component) {
            "all" -> reloadAll(sender)
            else -> reloadComponent(sender, component)
        }
    }

    private fun reloadAll(sender: CommandSender) {
        val startTime = System.currentTimeMillis()

        // Send start message
        val startMsg = formatMessage("reload-all-start")
            ?: Component.text("🔄 Reloading all configurations...", NamedTextColor.GOLD)
        sender.sendMessage(startMsg)

        val results = mutableMapOf<String, ReloadResult>()

        // Reload in dependency order
        val reloadOrder = listOf(
            "config", "messages", "channels", "groups",
            "discord", "crossserver", "maintenance",
            "database", "templates", "symbols", "placeholders"
        )

        // Execute reloads
        for (componentName in reloadOrder) {
            val result = executeComponentReload(componentName)
            results[componentName] = result

            // Send individual status
            val statusMsg = if (result.success) {
                formatMessage("reload-${componentName}-success")
                    ?: Component.text("✓ ${componentName.replaceFirstChar { it.uppercase() }} reloaded", NamedTextColor.GREEN)
            } else {
                formatMessage("reload-${componentName}-failed")
                    ?: Component.text("✗ Failed to reload ${componentName}", NamedTextColor.RED)
            }
            sender.sendMessage(statusMsg)
        }

        // Send completion summary
        val successCount = results.values.count { it.success }
        val totalCount = results.size
        val duration = System.currentTimeMillis() - startTime

        val completeMsg = formatMessage("reload-all-complete",
            Placeholder.parsed("success", successCount.toString()),
            Placeholder.parsed("total", totalCount.toString()),
            Placeholder.parsed("duration", "${duration}ms")
        ) ?: Component.text("🎉 Reload complete: $successCount/$totalCount components successfully reloaded (${duration}ms)", NamedTextColor.AQUA)

        sender.sendMessage(completeMsg)

        // Log failures
        results.filter { !it.value.success }.forEach { (component, result) ->
            plugin.logger.warning("Failed to reload $component: ${result.error}")
        }
    }

    private fun reloadComponent(sender: CommandSender, component: String) {
        val startTime = System.currentTimeMillis()

        // Send start message
        val startMsg = Component.text("🔄 Reloading $component...", NamedTextColor.GOLD)
        sender.sendMessage(startMsg)

        // Check dependencies
        val deps = dependencies[component] ?: emptyList()
        if (deps.isNotEmpty()) {
            sender.sendMessage(Component.text("📋 Checking dependencies: ${deps.joinToString(", ")}", NamedTextColor.GRAY))
        }

        // Execute reload
        val result = executeComponentReload(component)
        val duration = System.currentTimeMillis() - startTime

        // Send result
        val resultMsg = if (result.success) {
            formatMessage("reload-${component}-success")
                ?: Component.text("✓ ${component.replaceFirstChar { it.uppercase() }} reloaded successfully! (${duration}ms)", NamedTextColor.GREEN)
        } else {
            formatMessage("reload-${component}-failed")
                ?: Component.text("✗ Failed to reload ${component}! (${duration}ms)", NamedTextColor.RED)
        }
        sender.sendMessage(resultMsg)

        // Log detailed error if failed
        if (!result.success && result.error != null) {
            plugin.logger.warning("Failed to reload $component: ${result.error}")
            if (sender.hasPermission("remmychat.admin.debug")) {
                sender.sendMessage(Component.text("Error: ${result.error}", NamedTextColor.DARK_RED))
            }
        }
    }

    private fun executeComponentReload(component: String): ReloadResult {
        return try {
            val success = when (component) {
                "config" -> reloadMainConfig()
                "messages" -> reloadMessages()
                "channels" -> reloadChannels()
                "groups" -> reloadGroups()
                "discord" -> reloadDiscord()
                "crossserver" -> reloadCrossServer()
                "maintenance" -> reloadMaintenance()
                "placeholders" -> reloadPlaceholders()
                "database" -> reloadDatabase()
                "templates" -> reloadTemplates()
                "symbols" -> reloadSymbols()
                "fix-placeholders" -> fixPlaceholders()
                else -> false
            }
            ReloadResult(success)
        } catch (e: Exception) {
            ReloadResult(false, e.message ?: "Unknown error")
        }
    }

    // Individual reload implementations
    private fun reloadMainConfig(): Boolean {
        return try {
            plugin.configManager.reloadMainConfigOnly()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload main config: ${e.message}")
            false
        }
    }

    private fun reloadMessages(): Boolean {
        return try {
            plugin.messages.reloadMessages()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload messages: ${e.message}")
            false
        }
    }

    private fun reloadChannels(): Boolean {
        return try {
            plugin.configManager.reloadChannels()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload channels: ${e.message}")
            false
        }
    }

    private fun reloadGroups(): Boolean {
        return try {
            plugin.configManager.reloadGroups()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload groups: ${e.message}")
            false
        }
    }

    private fun reloadDiscord(): Boolean {
        // Check if DiscordSRV is available
        if (plugin.server.pluginManager.getPlugin("DiscordSRV") == null) {
            return false // Not an error, just not available
        }

        return try {
            val configReloaded = plugin.configManager.reloadDiscordConfig()
            if (configReloaded) {
                plugin.discordSRVIntegration.reloadChannelMappings()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload Discord integration: ${e.message}")
            false
        }
    }

    private fun reloadCrossServer(): Boolean {
        // Check if cross-server is enabled
        val config = plugin.configManager.getMainConfig()
        val redisEnabled = config.getBoolean("cross-server.redis.enabled", false)
        val dbEnabled = config.getBoolean("cross-server.database.enabled", false)
        val hybridEnabled = config.getBoolean("cross-server.hybrid.enabled", false)

        if (!redisEnabled && !dbEnabled && !hybridEnabled) {
            return false // Not enabled, not an error
        }

        return try {
            // Reload main config for cross-server settings
            plugin.configManager.reloadMainConfigOnly()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload cross-server: ${e.message}")
            false
        }
    }

    private fun fixPlaceholders(): Boolean {
        return try {
            plugin.configMigration.fixPlaceholderIssues()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to fix placeholders: ${e.message}")
            false
        }
    }

    private fun reloadMaintenance(): Boolean {
        return try {
            plugin.configManager.reloadMainConfigOnly()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload maintenance: ${e.message}")
            false
        }
    }

    private fun reloadPlaceholders(): Boolean {
        return try {
            plugin.placeholderManager.loadCustomPlaceholders()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload placeholders: ${e.message}")
            false
        }
    }

    private fun reloadDatabase(): Boolean {
        return try {
            plugin.configManager.reloadDatabaseConfig()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload database: ${e.message}")
            false
        }
    }

    private fun reloadTemplates(): Boolean {
        return try {
            plugin.configManager.reloadTemplates()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload templates: ${e.message}")
            false
        }
    }

    private fun reloadSymbols(): Boolean {
        return try {
            plugin.configManager.reloadSymbols()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload symbols: ${e.message}")
            false
        }
    }

    // Helper methods
    private fun sendNoPermissionMessage(sender: CommandSender) {
        val message = formatMessage("error.no-permission")
            ?: Component.text("You don't have permission to use this command!", NamedTextColor.RED)
        sender.sendMessage(message)
    }

    private fun sendUnknownComponentMessage(sender: CommandSender, component: String) {
        val errorMsg = formatMessage("reload-component-unknown",
            Placeholder.parsed("component", component))
            ?: Component.text("Unknown config component: $component", NamedTextColor.RED)
        sender.sendMessage(errorMsg)

        val availableMsg = formatMessage("reload-available-components")
            ?: Component.text("Available components: ${components.keys.joinToString(", ")}", NamedTextColor.GRAY)
        sender.sendMessage(availableMsg)
    }

    private fun formatMessage(key: String, vararg placeholders: TagResolver): Component? {
        return try {
            plugin.formatService.formatSystemMessage(key, *placeholders)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Data class to hold reload operation results
     */
    private data class ReloadResult(
        val success: Boolean,
        val error: String? = null
    )
}
