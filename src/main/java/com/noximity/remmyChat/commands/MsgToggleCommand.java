package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.ChatUser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MsgToggleCommand implements CommandExecutor {

    private final RemmyChat plugin;

    public MsgToggleCommand(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"));
            return true;
        }

        ChatUser chatUser = plugin.getChatService().getChatUser(player.getUniqueId());
        boolean newState = !chatUser.isMsgToggle();
        chatUser.setMsgToggle(newState);

        // Save the new state to the database
        plugin.getDatabaseManager().saveUserPreferences(chatUser);

        if (newState) {
            Component message = plugin.getFormatService().formatSystemMessage("msgtoggle-enabled");
            if (message != null) {
                player.sendMessage(message);
            }
        } else {
            Component message = plugin.getFormatService().formatSystemMessage("msgtoggle-disabled");
            if (message != null) {
                player.sendMessage(message);
            }
        }

        return true;
    }
}
