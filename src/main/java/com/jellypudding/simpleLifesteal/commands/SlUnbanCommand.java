package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SlUnbanCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final DatabaseManager databaseManager;

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

        String playerName = args[0];
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
