package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.utils.BanCheckResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;

public class CheckBanResultCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final long resultTimeoutMillis = 15000;

    public CheckBanResultCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /checkbanresult <player>", NamedTextColor.RED));
            return true;
        }

        String playerName = args[0];
        Map<String, BanCheckResult> pendingResults = plugin.getPendingBanResults();

        // Cleanup expired entries.
        Iterator<Map.Entry<String, BanCheckResult>> iterator = pendingResults.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BanCheckResult> entry = iterator.next();
            if (entry.getValue().isExpired(resultTimeoutMillis)) {
                iterator.remove();
            }
        }

        BanCheckResult result = pendingResults.get(playerName);

        if (result != null) {
            sender.sendMessage(Component.text(result.getStatus()));
        } else {
            sender.sendMessage(Component.text("RESULT_PENDING")); 
        }

        return true;
    }
}
