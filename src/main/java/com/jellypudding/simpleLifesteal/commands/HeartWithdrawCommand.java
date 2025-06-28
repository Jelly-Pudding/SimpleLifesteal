package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class HeartWithdrawCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final PlayerDataManager playerDataManager;
    private final NamespacedKey heartKey;

    public HeartWithdrawCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.heartKey = new NamespacedKey(plugin, "lifesteal_heart");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        int heartsToWithdraw = 1;
        if (args.length > 0) {
            try {
                heartsToWithdraw = Integer.parseInt(args[0]);
                if (heartsToWithdraw <= 0) {
                    player.sendMessage(Component.text("You must withdraw at least 1 heart.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid number. Usage: /withdrawheart [integer]", NamedTextColor.RED));
                return true;
            }
        }

        int currentHearts = playerDataManager.getPlayerHearts(player.getUniqueId());

        // Check if player has enough hearts to withdraw
        if (currentHearts <= heartsToWithdraw) {
            player.sendMessage(Component.text("You cannot withdraw that many hearts. You need at least ", NamedTextColor.RED)
                    .append(Component.text(heartsToWithdraw + 1, NamedTextColor.YELLOW))
                    .append(Component.text(" hearts to withdraw ", NamedTextColor.RED))
                    .append(Component.text(heartsToWithdraw, NamedTextColor.YELLOW))
                    .append(Component.text(" heart" + (heartsToWithdraw == 1 ? "" : "s") + ".", NamedTextColor.RED)));
            return true;
        }

        // Check if player has inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Your inventory is full. Make some space first.", NamedTextColor.RED));
            return true;
        }

        // Remove hearts from player
        playerDataManager.removeHearts(player.getUniqueId(), heartsToWithdraw);
        
        // Create heart item
        ItemStack heartItem = createHeartItem(heartsToWithdraw);
        player.getInventory().addItem(heartItem);

        // Send success message
        int newHeartCount = currentHearts - heartsToWithdraw;
        player.sendMessage(Component.text("You withdrew ", NamedTextColor.GREEN)
                .append(Component.text(heartsToWithdraw, NamedTextColor.RED))
                .append(Component.text(" heart" + (heartsToWithdraw == 1 ? "" : "s") + "! You now have ", NamedTextColor.GREEN))
                .append(Component.text(newHeartCount, NamedTextColor.RED))
                .append(Component.text(" heart" + (newHeartCount == 1 ? "" : "s") + " remaining.", NamedTextColor.GREEN)));

        return true;
    }

    private ItemStack createHeartItem(int heartCount) {
        ItemStack item = new ItemStack(Material.APPLE, heartCount);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("Heart", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(List.of(
                Component.text("Consume to regain a heart.", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        
        // Add persistent data to identify this as a lifesteal heart.
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BOOLEAN, true);
        
        item.setItemMeta(meta);
        return item;
    }

    public NamespacedKey getHeartKey() {
        return heartKey;
    }
}