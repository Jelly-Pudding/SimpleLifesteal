package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import com.jellypudding.simpleLifesteal.utils.BanCheckResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import com.jellypudding.simpleLifesteal.utils.PlayerUUIDUtil;


public class IsBannedCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final DatabaseManager databaseManager;
    private final String bedrockPrefix = ".";

    public IsBannedCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /isbanned <player>", NamedTextColor.RED));
            return true;
        }

        final String playerName = args[0];

        if (playerName.startsWith(bedrockPrefix)) {
            sender.sendMessage(Component.text("CHECK_PENDING " + playerName)); 
            PlayerUUIDUtil.fetchBedrockUUIDAsync(plugin, playerName, uuid -> {
                String status;
                if (uuid != null) {
                    boolean banned = databaseManager.isPlayerBannedByPlugin(uuid);
                    status = banned ? "BANNED" : "NOT_BANNED";
                } else {
                    status = "ERROR";
                }
                plugin.getPendingBanResults().put(playerName, new BanCheckResult(status));
            });
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found.", NamedTextColor.RED));
            return true;
        }

        if (targetPlayer.isOnline()) {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                .append(Component.text(" is not banned.", NamedTextColor.GREEN)));
            return true;
        }

        boolean bannedByPluginDB = databaseManager.isPlayerBannedByPlugin(targetPlayer.getUniqueId());
        if (bannedByPluginDB) {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
               .append(Component.text(" is banned.", NamedTextColor.RED)));
       } else {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
               .append(Component.text(" is not banned.", NamedTextColor.GREEN)));
       }

        return true;
    }
}
