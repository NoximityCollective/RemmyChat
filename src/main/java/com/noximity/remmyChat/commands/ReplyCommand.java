package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import net.kyori.adventure.text.Component;
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
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.reply-usage"));
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

        ChatUser targetUser = plugin.getChatService().getChatUser(target.getUniqueId());
        if (!targetUser.isMsgToggle() && !player.hasPermission("remmychat.msgtoggle.bypass")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.player-messages-disabled",
                    Placeholder.parsed("player", target.getName())));
            return true;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
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

        return true;
    }
}