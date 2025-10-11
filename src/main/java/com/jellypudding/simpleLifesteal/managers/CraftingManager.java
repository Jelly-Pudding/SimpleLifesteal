package com.jellypudding.simpleLifesteal.managers;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.utils.HeartItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;

public class CraftingManager {

    private final SimpleLifesteal plugin;
    private final HeartItemUtil heartItemUtil;
    private NamespacedKey recipeKey;

    public CraftingManager(SimpleLifesteal plugin, HeartItemUtil heartItemUtil) {
        this.plugin = plugin;
        this.heartItemUtil = heartItemUtil;
    }

    public void registerHeartRecipe() {
        if (!plugin.getConfig().getBoolean("heart-crafting.enabled", false)) {
            plugin.getLogger().info("Heart crafting is disabled.");
            return;
        }

        try {
            // Read recipe from config
            List<List<String>> recipeConfig = (List<List<String>>) plugin.getConfig().getList("heart-crafting.recipe");

            if (recipeConfig == null || recipeConfig.size() != 3) {
                plugin.getLogger().warning("Invalid heart crafting recipe configuration. Recipe must have exactly 3 rows.");
                return;
            }

            for (int i = 0; i < recipeConfig.size(); i++) {
                List<String> row = recipeConfig.get(i);
                if (row.size() != 3) {
                    plugin.getLogger().warning("Invalid heart crafting recipe configuration. Row " + (i + 1) + " must have exactly 3 items.");
                    return;
                }
            }

            // Create the recipe
            recipeKey = new NamespacedKey(plugin, "heart_recipe");
            ItemStack heartItem = heartItemUtil.createHeartItem(1);
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, heartItem);

            recipe.shape("ABC", "DEF", "GHI");
            String[] positions = {"A", "B", "C", "D", "E", "F", "G", "H", "I"};
            int posIndex = 0;
            for (List<String> row : recipeConfig) {
                for (String materialName : row) {
                    String position = positions[posIndex];
                    Material material = parseMaterial(materialName);
                    
                    if (material != null && material != Material.AIR) {
                        recipe.setIngredient(position.charAt(0), material);
                    } else {
                        recipe.setIngredient(position.charAt(0), Material.AIR);
                    }
                    posIndex++;
                }
            }

            plugin.getServer().addRecipe(recipe);
            plugin.getLogger().info("Heart crafting recipe registered successfully!");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register heart crafting recipe: " + e.getMessage());
        }
    }

    public void unregisterHeartRecipe() {
        if (recipeKey != null) {
            plugin.getServer().removeRecipe(recipeKey);
            plugin.getLogger().info("Heart crafting recipe unregistered.");
        }
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.trim().isEmpty() || materialName.equalsIgnoreCase("AIR")) {
            return Material.AIR;
        }

        try {
            return Material.valueOf(materialName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in heart recipe: " + materialName);
            return null;
        }
    }
}

