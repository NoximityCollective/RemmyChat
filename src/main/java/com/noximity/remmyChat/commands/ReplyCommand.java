package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private final RemmyChat plugin;

    public ReplyCommand(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.msg-usage"));
            return true;
        }

        ChatUser chatUser = plugin.getChatService().getChatUser(player.getUniqueId());
        UUID lastMessagedUUID = chatUser.getLastMessagedPlayer();

        if (lastMessagedUUID == null) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.nobody-to-reply"));
            return true;
        }

        Player target = Bukkit.getPlayer(lastMessagedUUID);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.player-not-online"));
            return true;
        }

        // Build message from args
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Format and send messages
        player.sendMessage(plugin.getFormatService().formatSystemMessage("msg-to-format",
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("message", message)));

        target.sendMessage(plugin.getFormatService().formatSystemMessage("msg-from-format",
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("message", message)));

        // Update last messaged player for the target
        ChatUser targetUser = plugin.getChatService().getChatUser(target.getUniqueId());
        targetUser.setLastMessagedPlayer(player.getUniqueId());

        return true;
    }
}