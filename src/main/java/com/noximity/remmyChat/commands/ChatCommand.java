package com.noximity.remmyChat.commands;

import com.noximity.remmyChat.RemmyChat;
import com.noximity.remmyChat.models.Channel;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatCommand implements CommandExecutor, TabCompleter {

    private final RemmyChat plugin;

    public ChatCommand(RemmyChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getFormatService().formatSystemMessage("error.players-only"));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "channel", "ch" -> handleChannelCommand(player, args);
            case "reload" -> handleReloadCommand(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void handleChannelCommand(Player player, String[] args) {
        if (args.length < 2) {
            // Show current channel
            player.sendMessage(plugin.getFormatService().formatSystemMessage("current-channel",
                    Placeholder.parsed("channel", plugin.getChatService().getChatUser(player.getUniqueId()).getCurrentChannel())));
            return;
        }

        String channelName = args[1].toLowerCase();
        Channel channel = plugin.getConfigManager().getChannel(channelName);

        if (channel == null) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.channel-not-found",
                    Placeholder.parsed("channel", channelName)));
            return;
        }

        // Check permission
        if (channel.getPermission() != null && !channel.getPermission().isEmpty()
                && !player.hasPermission(channel.getPermission())) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"));
            return;
        }

        // Set channel
        plugin.getChatService().setChannel(player.getUniqueId(), channelName);
        player.sendMessage(plugin.getFormatService().formatSystemMessage("channel-changed",
                Placeholder.parsed("channel", channelName)));
    }

    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("remmychat.admin")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("error.no-permission"));
            return;
        }

        plugin.getConfigManager().reloadConfig();
        plugin.getMessages().reloadMessages();
        player.sendMessage(plugin.getFormatService().formatSystemMessage("plugin-reloaded"));
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-header"));
        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-channel"));

        if (player.hasPermission("remmychat.admin")) {
            player.sendMessage(plugin.getFormatService().formatSystemMessage("help-reload"));
        }

        player.sendMessage(plugin.getFormatService().formatSystemMessage("help-footer"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("channel");
            if (sender.hasPermission("remmychat.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("channel")) {
            Map<String, Channel> channels = plugin.getConfigManager().getChannels();
            completions.addAll(channels.keySet().stream()
                    .filter(channel -> {
                        Channel ch = channels.get(channel);
                        return ch.getPermission() == null || ch.getPermission().isEmpty()
                                || sender.hasPermission(ch.getPermission());
                    })
                    .collect(Collectors.toList()));
        }

        return completions;
    }
}