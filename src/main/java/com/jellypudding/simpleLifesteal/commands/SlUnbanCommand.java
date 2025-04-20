package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import com.jellypudding.simpleLifesteal.utils.PlayerUUIDUtil;

public class SlUnbanCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final DatabaseManager databaseManager;
    private final String bedrockPrefix = ".";

    public SlUnbanCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /slunban <player>", NamedTextColor.RED));
            return true;
        }

        final String playerName = args[0];

        if (playerName.startsWith(bedrockPrefix)) {
            sender.sendMessage(Component.text("Attempting to unban Bedrock player via API lookup...", NamedTextColor.GRAY));
            PlayerUUIDUtil.fetchBedrockUUIDAsync(plugin, playerName, uuid -> {
                if (uuid != null) {
                    boolean removed = databaseManager.removePluginBan(uuid);
                    if (removed) {
                        sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                            .append(Component.text(" has been unbanned from the SimpleLifesteal database.", NamedTextColor.GREEN)));
                        plugin.getLogger().info("Admin " + sender.getName() + " unbanned " + playerName + " from the SimpleLifesteal database (via API lookup).");
                    } else {
                        sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                            .append(Component.text(" was not found in the SimpleLifesteal ban database.", NamedTextColor.RED)));
                    }
                } else {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found via Geyser API lookup. Cannot unban.", NamedTextColor.RED));
                }
            });
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found.", NamedTextColor.RED));
            return true;
        }

        boolean removed = databaseManager.removePluginBan(targetPlayer.getUniqueId());
        if (removed) {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                .append(Component.text(" has been unbanned from the SimpleLifesteal database.", NamedTextColor.GREEN)));
            plugin.getLogger().info("Admin " + sender.getName() + " unbanned " + playerName + " from the SimpleLifesteal database.");
        } else {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                .append(Component.text(" was not found in the SimpleLifesteal ban database.", NamedTextColor.RED)));
        }

        return true;
    }
}
