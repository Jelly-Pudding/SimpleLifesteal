package com.jellypudding.simpleLifesteal.listeners;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final SimpleLifesteal plugin;
    private final PlayerDataManager playerDataManager;
    private final String battleLockMetaKey = "BattleLock_CombatLog";

    public PlayerListener(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        playerDataManager.loadPlayerData(player, loadedHearts -> {
            if (!player.isOnline()) return;
            if (loadedHearts <= 0) {
                int startingHearts = plugin.getStartingHearts();
                plugin.getLogger().info(player.getName() + " joined with 0 hearts. Resetting to " + startingHearts + ".");
                playerDataManager.setPlayerHearts(playerUuid, startingHearts);
                player.sendMessage(Component.text("Your hearts have been reset!", NamedTextColor.GOLD));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerDataManager.savePlayerData(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        int currentHearts = playerDataManager.getPlayerHearts(victim.getUniqueId());

        int newHearts = Math.max(0, currentHearts - 1);
        playerDataManager.removeHearts(victim.getUniqueId(), 1);

        if (newHearts > 0) {
            victim.sendMessage(Component.text("You lost a heart! You now have " + newHearts + (newHearts == 1 ? " heart." : " hearts."), NamedTextColor.RED));
        } else {
            victim.sendMessage(Component.text("You lost your final heart!", NamedTextColor.RED));
            banPlayer(victim);
        }

        if (killer != null && !killer.equals(victim)) {
            playerDataManager.addHearts(killer.getUniqueId(), 1);
            int killerNewHearts = playerDataManager.getPlayerHearts(killer.getUniqueId());
            killer.sendMessage(Component.text("You stole a heart! You now have ", NamedTextColor.GREEN)
                    .append(Component.text(killerNewHearts, NamedTextColor.RED))
                    .append(Component.text((killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager npc) || event.getFinalDamage() < npc.getHealth()) {
            return;
        }

        List<MetadataValue> metadata = npc.getMetadata(battleLockMetaKey);
        if (metadata.isEmpty()) {
            return;
        }

        String playerUuidString = metadata.get(0).asString();
        try {
            UUID combatLoggerUuid = UUID.fromString(playerUuidString);
            OfflinePlayer combatLogger = Bukkit.getOfflinePlayer(combatLoggerUuid);
            String loggerName = combatLogger.getName() != null ? combatLogger.getName() : combatLoggerUuid.toString();

            int originalLoggerHearts = playerDataManager.getPlayerHearts(combatLoggerUuid);
            if (originalLoggerHearts == -1) {
                plugin.getLogger().warning("Combat logger " + loggerName + " (UUID: " + combatLoggerUuid + ") not found in database during NPC kill processing.");
                return; 
            }

            if (originalLoggerHearts > 0) {
                int newLoggerHearts = Math.max(0, originalLoggerHearts - 1);
                playerDataManager.removeHearts(combatLoggerUuid, 1);

                if (newLoggerHearts <= 0) {
                    if (combatLogger != null) {
                        plugin.getLogger().info("Combat logger " + loggerName + " reached 0 hearts. Banning...");
                        banOfflinePlayer(combatLogger);
                    }
                }
            } else {
                // Should never happen.
                plugin.getLogger().warning("Combat logger " + loggerName + " already had 0 or fewer hearts (" + originalLoggerHearts + "). No action taken.");
            }

            Player killer = null;
            Entity damager = event.getDamager();

            if (damager instanceof Player) {
                killer = (Player) damager;
            } else if (damager instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player shooter) {
                    killer = shooter;
                }
            }

            if (killer != null) {
                if (!killer.getUniqueId().equals(combatLoggerUuid)) {
                    playerDataManager.addHearts(killer.getUniqueId(), 1);
                    int killerNewHearts = playerDataManager.getPlayerHearts(killer.getUniqueId());
                    killer.sendMessage(Component.text("You killed " + loggerName + "'s combat log NPC and stole a heart! You now have ", NamedTextColor.GREEN)
                            .append(Component.text(killerNewHearts, NamedTextColor.RED))
                            .append(Component.text((killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN)));
                } else {
                     plugin.getLogger().warning("Killer was the same as the combat logger. No heart added.");
                }
            }

        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing BattleLock NPC metadata UUID from damage event: " + playerUuidString, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred processing BattleLock NPC death from damage event: " + playerUuidString, e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager npc)) {
            return;
        }

        List<MetadataValue> metadata = npc.getMetadata(battleLockMetaKey);
        if (metadata.isEmpty()) {
            return;
        }

        String playerUuidString = metadata.get(0).asString();
        try {
            UUID combatLoggerUuid = UUID.fromString(playerUuidString);
            OfflinePlayer combatLogger = Bukkit.getOfflinePlayer(combatLoggerUuid);
            String loggerName = combatLogger.getName() != null ? combatLogger.getName() : combatLoggerUuid.toString();

            int originalLoggerHearts = playerDataManager.getPlayerHearts(combatLoggerUuid);
            if (originalLoggerHearts == -1) {
                plugin.getLogger().warning("Combat logger " + loggerName + " (UUID: " + combatLoggerUuid + ") not found in database during NPC death processing.");
                return; 
            }

            if (originalLoggerHearts > 0) {
                int newLoggerHearts = Math.max(0, originalLoggerHearts - 1);
                playerDataManager.removeHearts(combatLoggerUuid, 1);

                plugin.getLogger().info("Combat logger " + loggerName + " lost a heart due to environmental NPC death. Hearts: " + originalLoggerHearts + " -> " + newLoggerHearts);

                if (newLoggerHearts <= 0) {
                    if (combatLogger != null) {
                        plugin.getLogger().info("Combat logger " + loggerName + " reached 0 hearts from environmental death. Banning...");
                        banOfflinePlayer(combatLogger);
                    }
                }
            } else {
                plugin.getLogger().warning("Combat logger " + loggerName + " already had 0 or fewer hearts (" + originalLoggerHearts + "). No action taken.");
            }

        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing BattleLock NPC metadata UUID from death event: " + playerUuidString, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred processing BattleLock NPC death from death event: " + playerUuidString, e);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHeartConsume(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Allow right-click in air or on blocks
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // If right-clicking on a block with interactions, don't consume the heart
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && hasBlockInteraction(clickedBlock.getType())) {
                return;
            }
        }

        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey heartKey = new NamespacedKey(plugin, "lifesteal_heart");

        if (meta.getPersistentDataContainer().has(heartKey, PersistentDataType.BOOLEAN)) {
            // Cancel the event to prevent normal item interaction
            event.setCancelled(true);

            // Check if player is already at max hearts
            int currentHearts = playerDataManager.getPlayerHearts(player.getUniqueId());
            int maxHearts = plugin.getPlayerMaxHearts(player.getUniqueId());
            
            if (currentHearts >= maxHearts) {
                player.sendMessage(Component.text("You are already at maximum hearts (", NamedTextColor.RED)
                        .append(Component.text(maxHearts, NamedTextColor.YELLOW))
                        .append(Component.text(")", NamedTextColor.RED)));
                return;
            }

            // Consume the item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }

            // Add heart to player
            playerDataManager.addHearts(player.getUniqueId(), 1);
            int newHeartCount = playerDataManager.getPlayerHearts(player.getUniqueId());

            player.sendMessage(Component.text("You consumed a heart. You now have ", NamedTextColor.GREEN)
                    .append(Component.text(newHeartCount, NamedTextColor.RED))
                    .append(Component.text(" heart" + (newHeartCount == 1 ? "" : "s") + ".", NamedTextColor.GREEN)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeartItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey heartKey = new NamespacedKey(plugin, "lifesteal_heart");

        // Prevent normal consumption of heart apples - they should only be consumed via right-click.
        if (meta.getPersistentDataContainer().has(heartKey, PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
        }
    }

    private boolean hasBlockInteraction(Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND,
                 DISPENSER, DROPPER, HOPPER,
                 ACACIA_DOOR, BIRCH_DOOR, DARK_OAK_DOOR, JUNGLE_DOOR, OAK_DOOR, SPRUCE_DOOR, MANGROVE_DOOR, CHERRY_DOOR, BAMBOO_DOOR, CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR,
                 ACACIA_TRAPDOOR, BIRCH_TRAPDOOR, DARK_OAK_TRAPDOOR, JUNGLE_TRAPDOOR, OAK_TRAPDOOR, SPRUCE_TRAPDOOR, MANGROVE_TRAPDOOR, CHERRY_TRAPDOOR, BAMBOO_TRAPDOOR, CRIMSON_TRAPDOOR, WARPED_TRAPDOOR, IRON_TRAPDOOR,
                 LEVER, ACACIA_BUTTON, BIRCH_BUTTON, DARK_OAK_BUTTON, JUNGLE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON, BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON, STONE_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 ACACIA_FENCE_GATE, BIRCH_FENCE_GATE, DARK_OAK_FENCE_GATE, JUNGLE_FENCE_GATE, OAK_FENCE_GATE, SPRUCE_FENCE_GATE, MANGROVE_FENCE_GATE, CHERRY_FENCE_GATE, BAMBOO_FENCE_GATE, CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    private void banPlayer(Player player) {
        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();
        String rawBanMessage = plugin.getBanMessage();
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawBanMessage);
        String finalBanMessage = LegacyComponentSerializer.legacySection().serialize(componentMessage);
        String banSource = plugin.getName();

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, banSource, true);
            plugin.getDatabaseManager().addPluginBan(playerUUID, finalBanMessage);
            plugin.getLogger().info("Banned player " + playerName + " (" + playerUUID + ") for running out of hearts.");

            int banCount = plugin.getDatabaseManager().getTotalHeartBans();
            Component broadcastMessage = player.displayName()
                    .append(Component.text(" has run out of hearts and was banned! ", NamedTextColor.GRAY))
                    .append(Component.text("(", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Total bans: ", NamedTextColor.GRAY))
                    .append(Component.text(banCount, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY));
            Bukkit.broadcast(broadcastMessage);
        });
    }

    private void banOfflinePlayer(OfflinePlayer player) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName() != null ? player.getName() : playerUUID.toString();
        String rawBanMessage = plugin.getBanMessage();
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawBanMessage);
        String finalBanMessage = LegacyComponentSerializer.legacySection().serialize(componentMessage);
        String banSource = plugin.getName();

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, banSource);
            plugin.getDatabaseManager().addPluginBan(playerUUID, finalBanMessage);
            plugin.getLogger().info("Banned offline player " + playerName + " (" + playerUUID + ") for running out of hearts.");

            int banCount = plugin.getDatabaseManager().getTotalHeartBans();
            Component broadcastMessage = Component.text(playerName, NamedTextColor.YELLOW)
                    .append(Component.text(" has run out of hearts and was banned (whilst offline)! ", NamedTextColor.GRAY))
                    .append(Component.text("(", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Total bans: ", NamedTextColor.GRAY))
                    .append(Component.text(banCount, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY));
            Bukkit.broadcast(broadcastMessage);
        });
    }
}
