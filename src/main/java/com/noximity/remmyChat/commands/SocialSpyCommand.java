package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SocialSpyCommand implements CommandExecutor {

    private final RemmyChat plugin;

    public SocialSpyCommand(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"));
            return true;
        }

        if (!player.hasPermission("remmychat.socialspy")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"));
            return true;
        }

        ChatUser chatUser = plugin.getChatService().getChatUser(player.getUniqueId());
        boolean newState = !chatUser.isSocialSpy();
        chatUser.setSocialSpy(newState);

        // Save the new state to the database
        plugin.getDatabaseManager().saveUserPreferences(chatUser);

        if (newState) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("socialspy-enabled"));
        } else {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("socialspy-disabled"));
        }

        return true;
    }
}
