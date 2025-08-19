package com.noximity.remmyChat.services

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized service for handling all reload operations with advanced features:
 * - Component dependency management
 * - Async reload support
 * - Detailed progress tracking
 * - Error recovery mechanisms
 * - Performance monitoring
 */
class ReloadService(private val plugin: RemmyChat) {

    private val mm = MiniMessage.miniMessage()
    private val isReloading = AtomicBoolean(false)
    private val reloadHistory = ConcurrentHashMap<String, ReloadHistoryEntry>()

    // Component definitions with metadata
    private val componentRegistry = mapOf(
        "config" to ComponentDefinition(
            name = "config",
            aliases = setOf("config", "main", "cfg"),
            dependencies = emptyList(),
            priority = 1,
            critical = true,
            description = "Main plugin configuration"
        ),
        "messages" to ComponentDefinition(
            name = "messages",
            aliases = setOf("messages", "msg", "lang"),
            dependencies = listOf("config"),
            priority = 2,
            critical = true,
            description = "Message templates and localization"
        ),
        "channels" to ComponentDefinition(
            name = "channels",
            aliases = setOf("channels", "ch", "channel"),
            dependencies = listOf("config"),
            priority = 3,
            critical = true,
            description = "Channel configurations"
        ),
        "groups" to ComponentDefinition(
            name = "groups",
            aliases = setOf("groups", "group", "grp"),
            dependencies = listOf("config"),
            priority = 4,
            critical = true,
            description = "Group formats and permissions"
        ),
        "discord" to ComponentDefinition(
            name = "discord",
            aliases = setOf("discord", "discordsrv"),
            dependencies = listOf("config"),
            priority = 5,
            critical = false,
            description = "Discord integration settings",
            availabilityCheck = { plugin.server.pluginManager.getPlugin("DiscordSRV") != null }
        ),
        "crossserver" to ComponentDefinition(
            name = "crossserver",
            aliases = setOf("crossserver", "cross-server", "cs", "cross"),
            dependencies = listOf("config"),
            priority = 6,
            critical = false,
            description = "Cross-server configuration",
            availabilityCheck = {
                try {
                    val config = plugin.configManager.getMainConfig()
                    config.getBoolean("cross-server.redis.enabled", false) ||
                    config.getBoolean("cross-server.database.enabled", false) ||
                    config.getBoolean("cross-server.hybrid.enabled", false)
                } catch (e: Exception) {
                    false
                }
            }
        ),
        "maintenance" to ComponentDefinition(
            name = "maintenance",
            aliases = setOf("maintenance", "maint"),
            dependencies = listOf("config"),
            priority = 7,
            critical = false,
            description = "Maintenance mode settings"
        ),
        "database" to ComponentDefinition(
            name = "database",
            aliases = setOf("database", "db", "data"),
            dependencies = listOf("config"),
            priority = 8,
            critical = false,
            description = "Database connection and sync settings"
        ),
        "templates" to ComponentDefinition(
            name = "templates",
            aliases = setOf("templates", "temp", "template"),
            dependencies = listOf("config"),
            priority = 9,
            critical = false,
            description = "Template definitions and formatting"
        ),
        "symbols" to ComponentDefinition(
            name = "symbols",
            aliases = setOf("symbols", "sym", "symbol"),
            dependencies = listOf("config"),
            priority = 10,
            critical = false,
            description = "Symbol and emoji mappings"
        ),
        "placeholders" to ComponentDefinition(
            name = "placeholders",
            aliases = setOf("placeholders", "papi", "placeholder"),
            dependencies = listOf("config", "messages"),
            priority = 11,
            critical = false,
            description = "Custom placeholder definitions"
        )
    )

    /**
     * Reload all components in dependency order
     */
    fun reloadAll(sender: CommandSender): CompletableFuture<ReloadResult> {
        return CompletableFuture.supplyAsync {
            if (!isReloading.compareAndSet(false, true)) {
                return@supplyAsync ReloadResult(
                    success = false,
                    message = "Reload already in progress",
                    componentsProcessed = 0,
                    errors = listOf("Another reload operation is currently running")
                )
            }

            try {
                performFullReload(sender)
            } finally {
                isReloading.set(false)
            }
        }
    }

    /**
     * Reload a specific component and its dependencies
     */
    fun reloadComponent(sender: CommandSender, componentName: String): CompletableFuture<ReloadResult> {
        return CompletableFuture.supplyAsync {
            if (!isReloading.compareAndSet(false, true)) {
                return@supplyAsync ReloadResult(
                    success = false,
                    message = "Reload already in progress",
                    componentsProcessed = 0,
                    errors = listOf("Another reload operation is currently running")
                )
            }

            try {
                performComponentReload(sender, componentName)
            } finally {
                isReloading.set(false)
            }
        }
    }

    /**
     * Get all available component aliases for tab completion
     */
    fun getAllAliases(): List<String> {
        return componentRegistry.values
            .flatMap { it.aliases }
            .plus("all")
            .sorted()
    }

    /**
     * Resolve component name from alias
     */
    fun resolveComponent(alias: String): String? {
        if (alias.equals("all", ignoreCase = true)) return "all"

        return componentRegistry.values
            .find { component ->
                component.aliases.any { it.equals(alias, ignoreCase = true) }
            }?.name
    }

    /**
     * Get component information
     */
    fun getComponentInfo(componentName: String): ComponentDefinition? {
        return componentRegistry[componentName]
    }

    /**
     * Get reload history for debugging
     */
    fun getReloadHistory(): Map<String, ReloadHistoryEntry> {
        return reloadHistory.toMap()
    }

    private fun performFullReload(sender: CommandSender): ReloadResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val results = mutableMapOf<String, ComponentReloadResult>()

        // Send start message
        sendMessage(sender, "reload-all-start",
            mm.deserialize("<gold>🔄 Reloading all configurations...</gold>"))

        // Get components in dependency order
        val orderedComponents = getComponentsInDependencyOrder()

        // Process each component
        for (component in orderedComponents) {
            try {
                val result = reloadSingleComponent(component)
                results[component.name] = result

                // Send status message
                val statusMsg = when {
                    result.success && result.skipped -> {
                        mm.deserialize("<yellow>⏸ ${component.name.replaceFirstChar { it.uppercase() }} skipped (${result.skipReason})</yellow>")
                    }
                    result.success -> {
                        mm.deserialize("<green>✓ ${component.name.replaceFirstChar { it.uppercase() }} reloaded</green>")
                    }
                    else -> {
                        errors.add("${component.name}: ${result.error}")
                        mm.deserialize("<red>✗ Failed to reload ${component.name}</red>")
                    }
                }
                sender.sendMessage(statusMsg)

            } catch (e: Exception) {
                val error = "Unexpected error reloading ${component.name}: ${e.message}"
                errors.add(error)
                plugin.logger.severe(error)
                sender.sendMessage(mm.deserialize("<red>✗ ${component.name.replaceFirstChar { it.uppercase() }}: Unexpected error</red>"))
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val successCount = results.values.count { it.success }
        val skippedCount = results.values.count { it.skipped }
        val totalCount = results.size

        // Send completion message
        val completeMsg = sendMessage(sender, "reload-all-complete",
            mm.deserialize("<aqua>🎉 Reload complete: <green>$successCount</green>/<yellow>$skippedCount</yellow>/<blue>$totalCount</blue> (success/skipped/total) in <white>${duration}ms</white></aqua>"),
            Placeholder.parsed("success", successCount.toString()),
            Placeholder.parsed("skipped", skippedCount.toString()),
            Placeholder.parsed("total", totalCount.toString()),
            Placeholder.parsed("duration", "${duration}ms")
        )

        // Record history
        recordReloadHistory("all", errors.isEmpty(), errors.firstOrNull(), duration)

        return ReloadResult(
            success = errors.isEmpty(),
            message = if (errors.isEmpty()) "All components reloaded successfully" else "Some components failed to reload",
            componentsProcessed = totalCount,
            errors = errors,
            duration = duration
        )
    }

    private fun performComponentReload(sender: CommandSender, componentName: String): ReloadResult {
        val startTime = System.currentTimeMillis()

        val component = componentRegistry[componentName]
            ?: return ReloadResult(
                success = false,
                message = "Unknown component: $componentName",
                componentsProcessed = 0,
                errors = listOf("Component '$componentName' not found")
            )

        // Send start message
        val componentDisplay = component.name.replaceFirstChar { it.uppercase() }
        sender.sendMessage(mm.deserialize("<gold>🔄 Reloading <yellow>$componentDisplay</yellow>...</gold>"))

        // Show dependencies if any
        if (component.dependencies.isNotEmpty()) {
            val depsMsg = mm.deserialize("<gray>📋 Dependencies: <white>${component.dependencies.joinToString(", ")}</white></gray>")
            sender.sendMessage(depsMsg)
        }

        try {
            val result = reloadSingleComponent(component)
            val duration = System.currentTimeMillis() - startTime

            // Send result message
            val resultMsg = when {
                result.success && result.skipped -> {
                    mm.deserialize("<yellow>⏸ $componentDisplay skipped (${result.skipReason})</yellow>")
                }
                result.success -> {
                    mm.deserialize("<green>✓ $componentDisplay reloaded successfully!</green> <gray>(${duration}ms)</gray>")
                }
                else -> {
                    mm.deserialize("<red>✗ Failed to reload $componentDisplay!</red> <dark_gray>(${duration}ms)</dark_gray>")
                }
            }
            sender.sendMessage(resultMsg)

            // Show debug info if available and user has permission
            if (!result.success && result.error != null && sender.hasPermission("remmychat.admin.debug")) {
                val debugMsg = mm.deserialize("<dark_red>🔍 Debug: <white>${result.error}</white></dark_red>")
                sender.sendMessage(debugMsg)
            }

            // Record history
            recordReloadHistory(componentName, result.success, result.error, duration)

            return ReloadResult(
                success = result.success,
                message = if (result.success) "Component reloaded successfully" else "Component reload failed",
                componentsProcessed = 1,
                errors = if (result.error != null) listOf(result.error) else emptyList(),
                duration = duration
            )

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val error = "Unexpected error: ${e.message}"

            plugin.logger.severe("Failed to reload $componentName: $error")
            sender.sendMessage(mm.deserialize("<red>✗ $componentDisplay: Unexpected error!</red>"))

            recordReloadHistory(componentName, false, error, duration)

            return ReloadResult(
                success = false,
                message = error,
                componentsProcessed = 1,
                errors = listOf(error),
                duration = duration
            )
        }
    }

    private fun reloadSingleComponent(component: ComponentDefinition): ComponentReloadResult {
        // Check availability
        if (component.availabilityCheck != null && !component.availabilityCheck.invoke()) {
            return ComponentReloadResult(
                success = true,
                skipped = true,
                skipReason = "not available/enabled"
            )
        }

        // Perform the actual reload
        return try {
            val success = when (component.name) {
                "config" -> {
                    plugin.configManager.reloadMainConfigOnly()
                }
                "messages" -> {
                    plugin.messages.reloadMessages()
                    true
                }
                "channels" -> {
                    plugin.configManager.reloadChannels()
                }
                "groups" -> {
                    plugin.configManager.reloadGroups()
                }
                "discord" -> {
                    // First reload discord config, then reload integration
                    val configReloaded = plugin.configManager.reloadDiscordConfig()
                    if (configReloaded) {
                        plugin.discordSRVIntegration.reloadChannelMappings()
                        true
                    } else {
                        false
                    }
                }
                "crossserver" -> {
                    // Reload main config for cross-server settings
                    plugin.configManager.reloadMainConfigOnly()
                }
                "maintenance" -> {
                    // Reload main config for maintenance settings
                    plugin.configManager.reloadMainConfigOnly()
                }
                "database" -> {
                    plugin.configManager.reloadDatabaseConfig()
                }
                "templates" -> {
                    plugin.configManager.reloadTemplates()
                }
                "symbols" -> {
                    plugin.configManager.reloadSymbols()
                }
                "placeholders" -> {
                    plugin.placeholderManager.loadCustomPlaceholders()
                    true
                }
                else -> false
            }

            ComponentReloadResult(success = success)

        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload ${component.name}: ${e.message}")
            ComponentReloadResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun getComponentsInDependencyOrder(): List<ComponentDefinition> {
        return componentRegistry.values.sortedBy { it.priority }
    }

    private fun sendMessage(sender: CommandSender, key: String, fallback: Component, vararg placeholders: TagResolver): Component {
        val message = try {
            plugin.formatService.formatSystemMessage(key, *placeholders) ?: fallback
        } catch (e: Exception) {
            fallback
        }
        sender.sendMessage(message)
        return message
    }

    private fun recordReloadHistory(component: String, success: Boolean, error: String?, duration: Long) {
        reloadHistory[component] = ReloadHistoryEntry(
            timestamp = System.currentTimeMillis(),
            success = success,
            error = error,
            duration = duration
        )
    }

    // Data classes
    data class ComponentDefinition(
        val name: String,
        val aliases: Set<String>,
        val dependencies: List<String>,
        val priority: Int,
        val critical: Boolean,
        val description: String,
        val availabilityCheck: (() -> Boolean)? = null
    )

    data class ComponentReloadResult(
        val success: Boolean,
        val error: String? = null,
        val skipped: Boolean = false,
        val skipReason: String? = null
    )

    data class ReloadResult(
        val success: Boolean,
        val message: String,
        val componentsProcessed: Int,
        val errors: List<String>,
        val duration: Long = 0L
    )

    data class ReloadHistoryEntry(
        val timestamp: Long,
        val success: Boolean,
        val error: String?,
        val duration: Long
    )
}
