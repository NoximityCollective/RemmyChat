package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import com.noximity.remmyChat.compatibility.VersionCompatibility
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class MessageCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.players-only")
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("This command is for players only!")
            }
            return true
        }

        if (args.size < 2) {
            val usageMsg = plugin.formatService.formatSystemMessage("error.msg-usage")
            if (usageMsg != null) {
                sender.sendMessage(usageMsg)
            } else {
                sender.sendMessage("Usage: /msg <player> <message>")
            }
            return true
        }

        val targetName = args[0]
        var target: Player? = plugin.server.getPlayer(targetName)
        var targetUuid: UUID? = null
        var isLocalPlayer = true

        // Check for exact online player match first
        if (target == null) {
            // Try partial name matching
            val matches = plugin.server.onlinePlayers.filter { it.name.lowercase().startsWith(targetName.lowercase()) }
            if (matches.size == 1) {
                target = matches[0]
            }
        }



        // If still no target found
        if (target == null && targetUuid == null) {
            val errorMsg = plugin.formatService.formatSystemMessage(
                "error.player-not-found",
                Placeholder.parsed("player", args[0])
            )
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("Player ${args[0]} not found!")
            }
            return true
        }

        // Check for self-messaging
        if (target?.uniqueId == sender.uniqueId || targetUuid == sender.uniqueId) {
            val errorMsg = plugin.formatService.formatSystemMessage("error.self-message")
            if (errorMsg != null) {
                VersionCompatibility.sendMessage(sender, errorMsg)
            } else {
                VersionCompatibility.sendMessage(sender, "You cannot send a message to yourself!")
            }
            return true
        }

        // Check if target has messages disabled (for local players)
        if (isLocalPlayer && target != null) {
            val targetUser = plugin.chatService.getChatUser(target.uniqueId)
            if (targetUser != null && !targetUser.isMsgToggle && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
                val errorMsg = plugin.formatService.formatSystemMessage(
                    "error.player-messages-disabled",
                    Placeholder.parsed("player", target.name)
                )
                if (errorMsg != null) {
                    sender.sendMessage(errorMsg)
                } else {
                    sender.sendMessage("${target.name} has messages disabled!")
                }
                return true
            }
        }

        // Build the message
        val messageBuilder = StringBuilder()
        for (i in 1..<args.size) {
            messageBuilder.append(args[i]).append(" ")
        }
        val message: String = messageBuilder.toString().trim()

        // Send confirmation to sender
        val toMsg = plugin.formatService.formatSystemMessage(
            "msg-to-format",
            Placeholder.parsed("player", targetName),
            Placeholder.parsed("message", message)
        )
        if (toMsg != null) {
            sender.sendMessage(toMsg)
        }

        if (isLocalPlayer && target != null) {
            // Send to local player
            val fromMsg = plugin.formatService.formatSystemMessage(
                "msg-from-format",
                Placeholder.parsed("player", sender.name),
                Placeholder.parsed("message", message)
            )
            if (fromMsg != null) {
                target.sendMessage(fromMsg)
            }

            // Update last messaged player for local recipient
            val targetUser = plugin.chatService.getChatUser(target.uniqueId)
            targetUser?.lastMessagedPlayer = sender.uniqueId
        } else {
            // Player not found locally
            val errorMsg = plugin.formatService.formatSystemMessage("error.player-not-found",
                Placeholder.parsed("player", targetName))
            if (errorMsg != null) {
                sender.sendMessage(errorMsg)
            } else {
                sender.sendMessage("Player $targetName not found!")
            }
            return true
        }

        // Send to social spy users on local server
        val spyMessage = plugin.formatService.formatSystemMessage(
            "socialspy-format",
            Placeholder.parsed("sender", sender.name),
            Placeholder.parsed("receiver", targetName),
            Placeholder.parsed("message", message)
        )

        for (spyUser in plugin.chatService.getSocialSpyUsers()) {
            val spy = plugin.server.getPlayer(spyUser.uuid)
            if (spy != null && spy.isOnline && spy.uniqueId != sender.uniqueId && spy.uniqueId != targetUuid) {
                if (spyMessage != null) {
                    spy.sendMessage(spyMessage)
                }
            }
        }

        // Update last messaged player for sender
        val senderUser = plugin.chatService.getChatUser(sender.uniqueId)
        senderUser?.lastMessagedPlayer = targetUuid

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        if (args.size == 1) {
            val completions = mutableListOf<String>()
            val partial = args[0].lowercase()

            // Add online players on current server
            for (player in plugin.server.onlinePlayers) {
                if (player.name.lowercase().startsWith(partial) && player != sender) {
                    completions.add(player.name)
                }
            }

            // Add recently messaged players (if available)
            if (sender is Player) {
                val chatUser = plugin.chatService.getChatUser(sender.uniqueId)
                chatUser?.let { user ->
                    // Add last messaged player if not already in list
                    user.lastMessagedPlayer?.let { lastUuid ->
                        val lastPlayer = plugin.server.getPlayer(lastUuid)
                        if (lastPlayer != null && lastPlayer != sender &&
                            lastPlayer.name.lowercase().startsWith(partial) &&
                            !completions.contains(lastPlayer.name)) {
                            completions.add(0, lastPlayer.name) // Add at beginning for priority
                        }
                    }
                }
            }

            // Sort completions alphabetically, but keep priority players at top
            val sortedCompletions = completions.drop(if (completions.isNotEmpty() && sender is Player) 1 else 0).sorted()
            val priorityCompletions = if (completions.isNotEmpty() && sender is Player) listOf(completions[0]) else emptyList()

            return (priorityCompletions + sortedCompletions).toMutableList()
        }

        // For args.size > 1, no tab completion needed (message content)
        return mutableListOf()
    }
}
