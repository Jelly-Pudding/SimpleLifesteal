package com.jellypudding.simpleLifesteal.utils;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class HeartItemUtil {

    private final NamespacedKey heartKey;

    public HeartItemUtil(SimpleLifesteal plugin) {
        this.heartKey = new NamespacedKey(plugin, "lifesteal_heart");
    }

    public ItemStack createHeartItem(int heartCount) {
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

    public boolean isHeartItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(heartKey, PersistentDataType.BOOLEAN);
    }

    public NamespacedKey getHeartKey() {
        return heartKey;
    }
}

