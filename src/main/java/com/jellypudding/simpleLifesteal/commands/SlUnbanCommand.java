package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.utils.UnbanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SlUnbanCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final String bedrockPrefix = ".";

    public SlUnbanCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
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
        }

        UnbanService.unban(plugin, playerName, (uuid, unbanned) -> {
            if (uuid == null) {
                if (playerName.startsWith(bedrockPrefix)) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found via Geyser API lookup. Cannot unban.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found.", NamedTextColor.RED));
                }
                return;
            }

            if (unbanned) {
                sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                    .append(Component.text(" has been unbanned from the SimpleLifesteal database.", NamedTextColor.GREEN)));
                plugin.getLogger().info("Admin " + sender.getName() + " unbanned " + playerName + " from the SimpleLifesteal database.");
            } else {
                sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                    .append(Component.text(" was not found in the SimpleLifesteal ban database.", NamedTextColor.RED)));
            }
        });

        return true;
    }
}
