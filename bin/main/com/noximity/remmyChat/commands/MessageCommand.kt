package com.noximity.remmyChat.commands

import com.noximity.remmyChat.RemmyChat
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*
import java.util.stream.Collectors

class MessageCommand(private val plugin: RemmyChat) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"))
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.msg-usage"))
            return true
        }

        // First try to get an exact match to prevent partial matches
        var target: Player? = null
        val targetName = args[0]

        // Check for exact match first
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().equals(targetName, ignoreCase = true)) {
                target = onlinePlayer
                break
            }
        }

        // If no exact match was found, show error
        if (target == null || !target.isOnline()) {
            sender.sendMessage(
                plugin.getFormatService().formatSystemMessage(
                    "error.player-not-found",
                    Placeholder.parsed("player", args[0])
                )
            )
            return true
        }

        if (target == sender) {
            // Only block self-messaging if not allowed in config
            if (!plugin.getConfigManager().isAllowSelfMessaging()) {
                sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.cannot-message-self"))
                return true
            }
        }

        val targetUser = plugin.getChatService().getChatUser(target.getUniqueId())
        if (!targetUser.isMsgToggle() && !sender.hasPermission("remmychat.msgtoggle.bypass")) {
            sender.sendMessage(
                plugin.getFormatService().formatSystemMessage(
                    "error.player-messages-disabled",
                    Placeholder.parsed("player", target.getName())
                )
            )
            return true
        }

        val messageBuilder = StringBuilder()
        for (i in 1..<args.size) {
            messageBuilder.append(args[i]).append(" ")
        }
        val message: String = messageBuilder.toString().trim { it <= ' ' }
        sender.sendMessage(
            plugin.getFormatService().formatSystemMessage(
                "msg-to-format",
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("message", message)
            )
        )

        target.sendMessage(
            plugin.getFormatService().formatSystemMessage(
                "msg-from-format",
                Placeholder.parsed("player", sender.getName()),
                Placeholder.parsed("message", message)
            )
        )

        val spyMessage = plugin.getFormatService().formatSystemMessage(
            "socialspy-format",
            Placeholder.parsed("sender", sender.getName()),
            Placeholder.parsed("receiver", target.getName()),
            Placeholder.parsed("message", message)
        )

        for (spyUser in plugin.getChatService().getSocialSpyUsers()) {
            val spy = plugin.getServer().getPlayer(spyUser.getUuid())
            if (spy != null && spy.isOnline() && (spy != sender) && (spy != target)) {
                spy.sendMessage(spyMessage)
            }
        }

        targetUser.setLastMessagedPlayer(sender.getUniqueId())

        val senderUser = plugin.getChatService().getChatUser(sender.getUniqueId())
        senderUser.setLastMessagedPlayer(target.getUniqueId())

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): MutableList<String?>? {
        if (args.size == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .filter { player: Player? ->
                    args[0].isEmpty() || player!!.getName().lowercase(Locale.getDefault())
                        .startsWith(args[0].lowercase(Locale.getDefault()))
                }
                .map<String?> { obj: Player? -> obj!!.getName() }
                .collect(Collectors.toList())
        }
        return ArrayList<String?>()
    }
}

