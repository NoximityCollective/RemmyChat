package com.noximity.remmyChat.commands.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.noximity.remmyChat.RemmyChat
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Brigadier implementation of the reload command with modern Paper command system support
 */
class BrigadierReloadCommand(private val plugin: RemmyChat) : Command<CommandSourceStack>, SuggestionProvider<CommandSourceStack> {

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

    override fun run(context: CommandContext<CommandSourceStack>): Int {
        val stack = context.source
        val sender = stack.sender

        // Check permissions
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender)
            return 0
        }

        // Get component argument (optional)
        val component = try {
            context.getArgument("reload_component", String::class.java)?.lowercase() ?: "all"
        } catch (e: Exception) {
            "all"
        }

        val resolvedComponent = resolveComponent(component)

        if (resolvedComponent == null) {
            sendUnknownComponentMessage(sender, component)
            return 0
        }

        // Execute reload asynchronously to avoid blocking
        CompletableFuture.runAsync {
            executeReload(sender, resolvedComponent)
        }.exceptionally { throwable ->
            plugin.logger.severe("Error during reload: ${throwable.message}")
            sender.sendMessage(Component.text("An error occurred during reload. Check console for details.", NamedTextColor.RED))
            null
        }

        return 1
    }

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = context.source.sender

        if (!hasPermission(sender)) {
            return CompletableFuture.completedFuture(builder.build())
        }

        val input = builder.remaining.lowercase()

        // Add all available component aliases
        getAllAliases()
            .filter { it.startsWith(input) }
            .sorted()
            .forEach { builder.suggest(it) }

        return CompletableFuture.completedFuture(builder.build())
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
            ?: mm.deserialize("<gold>🔄 Reloading all configurations...</gold>")
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

            // Send individual status with enhanced formatting
            val statusMsg = if (result.success) {
                if (result.skipped) {
                    formatMessage("reload-${componentName}-skipped")
                        ?: mm.deserialize("<yellow>⏸ ${componentName.replaceFirstChar { it.uppercase() }} skipped (not available/enabled)</yellow>")
                } else {
                    formatMessage("reload-${componentName}-success")
                        ?: mm.deserialize("<green>✓ ${componentName.replaceFirstChar { it.uppercase() }} reloaded</green>")
                }
            } else {
                formatMessage("reload-${componentName}-failed")
                    ?: mm.deserialize("<red>✗ Failed to reload ${componentName}</red>")
            }
            sender.sendMessage(statusMsg)
        }

        // Send completion summary
        val successCount = results.values.count { it.success }
        val skippedCount = results.values.count { it.skipped }
        val totalCount = results.size
        val duration = System.currentTimeMillis() - startTime

        val completeMsg = formatMessage("reload-all-complete",
            Placeholder.parsed("success", successCount.toString()),
            Placeholder.parsed("skipped", skippedCount.toString()),
            Placeholder.parsed("total", totalCount.toString()),
            Placeholder.parsed("duration", "${duration}ms")
        ) ?: mm.deserialize("<aqua>🎉 Reload complete: <green><success></green>/<yellow><skipped></yellow>/<blue><total></blue> components (success/skipped/total) in <white><duration></white></aqua>")

        sender.sendMessage(completeMsg)

        // Log failures for debugging
        results.filter { !it.value.success && !it.value.skipped }.forEach { (component, result) ->
            plugin.logger.warning("Failed to reload $component: ${result.error}")
        }
    }

    private fun reloadComponent(sender: CommandSender, component: String) {
        val startTime = System.currentTimeMillis()

        // Send start message with component name
        val componentDisplay = component.replaceFirstChar { it.uppercase() }
        val startMsg = mm.deserialize("<gold>🔄 Reloading <yellow>$componentDisplay</yellow>...</gold>")
        sender.sendMessage(startMsg)

        // Check and display dependencies
        val deps = dependencies[component] ?: emptyList()
        if (deps.isNotEmpty()) {
            val depsMsg = mm.deserialize("<gray>📋 Dependencies: <white>${deps.joinToString(", ")}</white></gray>")
            sender.sendMessage(depsMsg)
        }

        // Execute reload
        val result = executeComponentReload(component)
        val duration = System.currentTimeMillis() - startTime

        // Send result with enhanced formatting
        val resultMsg = when {
            result.success && result.skipped -> {
                formatMessage("reload-${component}-skipped")
                    ?: mm.deserialize("<yellow>⏸ $componentDisplay skipped (not available/enabled)</yellow>")
            }
            result.success -> {
                formatMessage("reload-${component}-success")
                    ?: mm.deserialize("<green>✓ $componentDisplay reloaded successfully!</green> <gray>(<duration>ms)</gray>")
                        .let { mm.deserialize(it.toString().replace("<duration>", duration.toString())) }
            }
            else -> {
                formatMessage("reload-${component}-failed")
                    ?: mm.deserialize("<red>✗ Failed to reload $componentDisplay!</red> <dark_gray>(<duration>ms)</dark_gray>")
                        .let { mm.deserialize(it.toString().replace("<duration>", duration.toString())) }
            }
        }
        sender.sendMessage(resultMsg)

        // Show detailed error for debugging
        if (!result.success && !result.skipped && result.error != null) {
            plugin.logger.warning("Failed to reload $component: ${result.error}")
            if (sender.hasPermission("remmychat.admin.debug")) {
                val errorMsg = mm.deserialize("<dark_red>🔍 Debug: <white>${result.error}</white></dark_red>")
                sender.sendMessage(errorMsg)
            }
        }
    }

    private fun executeComponentReload(component: String): ReloadResult {
        return try {
            when (component) {
                "config" -> ReloadResult(reloadMainConfig())
                "messages" -> ReloadResult(reloadMessages())
                "channels" -> ReloadResult(reloadChannels())
                "groups" -> ReloadResult(reloadGroups())
                "discord" -> reloadDiscordWithAvailabilityCheck()
                "crossserver" -> reloadCrossServerWithAvailabilityCheck()
                "maintenance" -> ReloadResult(reloadMaintenance())
                "placeholders" -> ReloadResult(reloadPlaceholders())
                "database" -> ReloadResult(reloadDatabase())
                "templates" -> ReloadResult(reloadTemplates())
                "symbols" -> ReloadResult(reloadSymbols())
                "fix-placeholders" -> ReloadResult(fixPlaceholders())
                else -> ReloadResult(false, "Unknown component: $component")
            }
        } catch (e: Exception) {
            ReloadResult(false, e.message ?: "Unknown error")
        }
    }

    // Individual reload implementations with better error handling
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

    private fun reloadDiscordWithAvailabilityCheck(): ReloadResult {
        // Check if DiscordSRV is available
        if (plugin.server.pluginManager.getPlugin("DiscordSRV") == null) {
            return ReloadResult(true, null, true) // Skipped, not an error
        }

        return try {
            val configReloaded = plugin.configManager.reloadDiscordConfig()
            if (configReloaded) {
                plugin.discordSRVIntegration.reloadChannelMappings()
                ReloadResult(true)
            } else {
                ReloadResult(false, "Failed to reload Discord configuration")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload Discord integration: ${e.message}")
            ReloadResult(false, e.message)
        }
    }

    private fun reloadCrossServerWithAvailabilityCheck(): ReloadResult {
        // Check if cross-server is enabled
        val config = plugin.configManager.getMainConfig()
        val redisEnabled = config.getBoolean("cross-server.redis.enabled", false)
        val dbEnabled = config.getBoolean("cross-server.database.enabled", false)
        val hybridEnabled = config.getBoolean("cross-server.hybrid.enabled", false)

        if (!redisEnabled && !dbEnabled && !hybridEnabled) {
            return ReloadResult(true, null, true) // Skipped, not enabled
        }

        return try {
            // Reload main config for cross-server settings
            val success = plugin.configManager.reloadMainConfigOnly()
            ReloadResult(success)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload cross-server: ${e.message}")
            ReloadResult(false, e.message)
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
            ?: mm.deserialize("<red>You don't have permission to use this command!</red>")
        sender.sendMessage(message)
    }

    private fun sendUnknownComponentMessage(sender: CommandSender, component: String) {
        val errorMsg = formatMessage("reload-component-unknown",
            Placeholder.parsed("component", component))
            ?: mm.deserialize("<red>Unknown config component: <white>$component</white></red>")
        sender.sendMessage(errorMsg)

        val availableMsg = formatMessage("reload-available-components")
            ?: mm.deserialize("<gray>Available components: <white>${components.keys.joinToString(", ")}</white></gray>")
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
     * Data class to hold reload operation results with additional metadata
     */
    private data class ReloadResult(
        val success: Boolean,
        val error: String? = null,
        val skipped: Boolean = false
    )
}
