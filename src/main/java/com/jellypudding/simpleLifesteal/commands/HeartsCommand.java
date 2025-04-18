package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HeartsCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final PlayerDataManager playerDataManager;

    public HeartsCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        int currentHearts = playerDataManager.getPlayerHearts(player.getUniqueId());

        // Build the message using Components
        Component message = Component.text("You currently have ", NamedTextColor.GREEN)
                .append(Component.text(currentHearts, NamedTextColor.RED))
                .append(Component.text(" heart" + (currentHearts == 1 ? "." : "s."), NamedTextColor.GREEN));

        player.sendMessage(message);
        return true;
    }
} 