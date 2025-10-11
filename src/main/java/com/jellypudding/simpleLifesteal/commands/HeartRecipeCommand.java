package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class HeartRecipeCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;

    public HeartRecipeCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("heart-crafting.enabled", false)) {
            sender.sendMessage(Component.text("Heart crafting is currently disabled.", NamedTextColor.RED));
            return true;
        }

        try {
            List<List<String>> recipeConfig = (List<List<String>>) plugin.getConfig().getList("heart-crafting.recipe");

            if (recipeConfig == null || recipeConfig.size() != 3) {
                sender.sendMessage(Component.text("Heart crafting recipe is not configured correctly.", NamedTextColor.RED));
                return true;
            }

            sender.sendMessage(Component.text("══════════════", NamedTextColor.GOLD, TextDecoration.BOLD));
            sender.sendMessage(Component.text(" Heart Crafting Recipe", NamedTextColor.RED, TextDecoration.BOLD));
            sender.sendMessage(Component.text("══════════════", NamedTextColor.GOLD, TextDecoration.BOLD));

            for (int row = 0; row < 3; row++) {
                List<String> rowItems = recipeConfig.get(row);
                Component rowComponent = Component.text("");

                for (int col = 0; col < 3; col++) {
                    String materialName = rowItems.get(col);
                    Material material = parseMaterial(materialName);

                    if (material != null && material != Material.AIR) {
                        String displayName = formatMaterialName(materialName);
                        rowComponent = rowComponent.append(Component.text("[" + displayName + "]", NamedTextColor.YELLOW));
                    } else {
                        rowComponent = rowComponent.append(Component.text("[Empty]", NamedTextColor.GRAY));
                    }

                    if (col < 2) {
                        rowComponent = rowComponent.append(Component.text(" "));
                    }
                }

                sender.sendMessage(rowComponent);
            }

            sender.sendMessage(Component.text("══════════════", NamedTextColor.GOLD, TextDecoration.BOLD));

        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to display heart recipe. Contact an administrator.", NamedTextColor.RED));
            plugin.getLogger().severe("Error displaying heart recipe: " + e.getMessage());
        }

        return true;
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.trim().isEmpty() || materialName.equalsIgnoreCase("AIR")) {
            return Material.AIR;
        }

        try {
            return Material.valueOf(materialName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatMaterialName(String materialName) {
        if (materialName == null) {
            return "Empty";
        }

        String[] words = materialName.split("_");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            formatted.append(word.charAt(0)).append(word.substring(1).toLowerCase());
            if (i < words.length - 1) {
                formatted.append(" ");
            }
        }

        return formatted.toString();
    }
}

