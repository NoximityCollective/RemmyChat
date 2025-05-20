package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final RemmyChat plugin;

    public MessageCommand(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.msg-usage"));
            return true;
        }

        // First try to get an exact match to prevent partial matches
        Player target = null;
        String targetName = args[0];

        // Check for exact match first
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().equalsIgnoreCase(targetName)) {
                target = onlinePlayer;
                break;
            }
        }

        // If no exact match was found, show error
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.player-not-found",
                    Placeholder.parsed("player", args[0])));
            return true;
        }

        if (target.equals(player)) {
            // Only block self-messaging if not allowed in config
            if (!plugin.getConfigManager().isAllowSelfMessaging()) {
                player.sendMessage(plugin.getFormatService().formatSystemMessage("error.cannot-message-self"));
                return true;
            }
        }

        ChatUser targetUser = plugin.getChatService().getChatUser(target.getUniqueId());
        if (!targetUser.isMsgToggle() && !player.hasPermission("remmychat.msgtoggle.bypass")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.player-messages-disabled",
                    Placeholder.parsed("player", target.getName())));
            return true;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();
        player.sendMessage(plugin.getFormatService().formatSystemMessage("msg-to-format",
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("message", message)));

        target.sendMessage(plugin.getFormatService().formatSystemMessage("msg-from-format",
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("message", message)));

        Component spyMessage = plugin.getFormatService().formatSystemMessage("socialspy-format",
                Placeholder.parsed("sender", player.getName()),
                Placeholder.parsed("receiver", target.getName()),
                Placeholder.parsed("message", message));

        for (ChatUser spyUser : plugin.getChatService().getSocialSpyUsers()) {
            Player spy = plugin.getServer().getPlayer(spyUser.getUuid());
            if (spy != null && spy.isOnline() && !spy.equals(player) && !spy.equals(target)) {
                spy.sendMessage(spyMessage);
            }
        }

        targetUser.setLastMessagedPlayer(player.getUniqueId());

        ChatUser senderUser = plugin.getChatService().getChatUser(player.getUniqueId());
        senderUser.setLastMessagedPlayer(target.getUniqueId());

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(player -> args[0].isEmpty() || player.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}

