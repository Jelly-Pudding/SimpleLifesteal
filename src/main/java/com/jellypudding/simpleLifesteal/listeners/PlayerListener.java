package com.jellypudding.simpleLifesteal.listeners;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;

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

        // Load player data asynchronously and pass a callback for post-load actions.
        playerDataManager.loadPlayerData(player, loadedHearts -> {
            // This code runs on the main thread AFTER data is loaded/cached.
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
            banPlayer(victim); // Use single ban method
        }

        // Killer gains a heart
        if (killer != null && !killer.equals(victim)) {
            playerDataManager.addHearts(killer.getUniqueId(), 1);
            int killerNewHearts = playerDataManager.getPlayerHearts(killer.getUniqueId());
            killer.sendMessage(Component.text("You stole a heart! You now have " + killerNewHearts + (killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        Player killer = deadEntity.getKiller();

        // Check if it's a BattleLock NPC.
        if (deadEntity instanceof Villager npc) {
            List<MetadataValue> metadata = npc.getMetadata(battleLockMetaKey);
            if (!metadata.isEmpty()) {
                String playerUuidString = metadata.get(0).asString();
                try {
                    UUID combatLoggerUuid = UUID.fromString(playerUuidString);

                    // Give the killer a heart.
                    if (killer != null) {
                        playerDataManager.addHearts(killer.getUniqueId(), 1);
                        int killerNewHearts = playerDataManager.getPlayerHearts(killer.getUniqueId());
                        killer.sendMessage(Component.text("You killed a combat logger's NPC and stole a heart! You now have " + killerNewHearts + (killerNewHearts == 1 ? " heart." : " hearts."), NamedTextColor.GREEN));
                    }

                    // Penalise the combat logger.
                    int originalPlayerCurrentHearts = playerDataManager.getPlayerHearts(combatLoggerUuid);
                    if (originalPlayerCurrentHearts > 0) {
                        int newLoggerHearts = Math.max(0, originalPlayerCurrentHearts - 1);
                        playerDataManager.removeHearts(combatLoggerUuid, 1);

                        // Ban combat logger if they hit 0 hearts
                        if (newLoggerHearts <= 0) {
                            OfflinePlayer offlineLogger = Bukkit.getOfflinePlayer(combatLoggerUuid);
                            if (offlineLogger != null) {
                                banOfflinePlayer(offlineLogger);
                            }
                        }
                    }

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error parsing BattleLock NPC metadata UUID: " + playerUuidString, e);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred processing BattleLock NPC death for UUID: " + playerUuidString, e);
                }
            }
        }
    }

    private void banPlayer(Player player) {
        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();
        String finalBanMessage = plugin.getBanMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, "SimpleLifesteal", true);
            plugin.getLogger().info("Banned player " + playerName + " (" + playerUUID + ") for running out of hearts.");
        });
    }

    private void banOfflinePlayer(OfflinePlayer player) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName() != null ? player.getName() : playerUUID.toString();
        String finalBanMessage = plugin.getBanMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(finalBanMessage, (Date) null, "SimpleLifesteal"); // Ban
            plugin.getLogger().info("Banned offline player " + playerName + " (" + playerUUID + ") for running out of hearts.");
        });
    }
}
