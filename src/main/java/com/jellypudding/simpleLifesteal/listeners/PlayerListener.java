package com.jellypudding.simpleLifesteal.listeners;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        playerDataManager.loadPlayerData(player, loadedHearts -> {
            if (!player.isOnline()) return;
            // They will have 0 hearts if they have been unbanned. Set them to the starting hearts.
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
            killer.sendMessage(Component.text("You stole a heart! You now have " + killerNewHearts + (killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN));
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
                plugin.getLogger().info("Combat logger " + loggerName + " already had 0 or fewer hearts (" + originalLoggerHearts + "). No action taken.");
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
                    killer.sendMessage(Component.text("You killed " + loggerName + "'s combat log NPC and stole a heart! You now have "
                            + killerNewHearts + (killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN));
                } else {
                     plugin.getLogger().info("[DEBUG] Killer was the same as the combat logger. No heart added.");
                }
            }

        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing BattleLock NPC metadata UUID from damage event: " + playerUuidString, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred processing BattleLock NPC death from damage event: " + playerUuidString, e);
        }
    }

    private void banPlayer(Player player) {
        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();
        String rawBanMessage = plugin.getBanMessage();
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawBanMessage);
        String finalBanMessage = LegacyComponentSerializer.legacySection().serialize(componentMessage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, "SimpleLifesteal", true);
            plugin.getLogger().info("Banned player " + playerName + " (" + playerUUID + ") for running out of hearts.");
        });
    }

    private void banOfflinePlayer(OfflinePlayer player) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName() != null ? player.getName() : playerUUID.toString();
        String rawBanMessage = plugin.getBanMessage();
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawBanMessage);
        String finalBanMessage = LegacyComponentSerializer.legacySection().serialize(componentMessage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, "SimpleLifesteal");
            plugin.getLogger().info("Banned offline player " + playerName + " (" + playerUUID + ") for running out of hearts.");
        });
    }
}
