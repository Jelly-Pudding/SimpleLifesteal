package com.jellypudding.simpleLifesteal.managers;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PlayerDataManager {

    private final SimpleLifesteal plugin;
    private final DatabaseManager databaseManager;
    // Cache: UUID -> current_hearts
    private final ConcurrentHashMap<UUID, Integer> heartCache;

    private final int startingHearts;
    private final int maxHearts;
    private final int minHearts = 0;

    public PlayerDataManager(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.heartCache = new ConcurrentHashMap<>();
        this.startingHearts = plugin.getStartingHearts();
        this.maxHearts = plugin.getMaxHearts();
    }

    /**
     * Asynchronously loads player data and executes a callback on the main thread
     * once the data is loaded and cached.
     *
     * @param player The player whose data to load.
     * @param callback A Consumer that accepts the loaded heart count.
     */
    public void loadPlayerData(Player player, Consumer<Integer> callback) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int hearts = databaseManager.getPlayerHearts(uuid);
            // Player not found in DB.
            if (hearts == -1) {
                hearts = startingHearts;
                databaseManager.setPlayerHearts(uuid, hearts);
            }

            final int finalHearts = hearts;

            // Update cache and execute callback synchronously.
            Bukkit.getScheduler().runTask(plugin, () -> {
                heartCache.put(uuid, finalHearts);

                if (player != null && player.isOnline()) {
                    updatePlayerMaxHealth(player, finalHearts);
                }

                // Execute the original callback if provided.
                if (callback != null) {
                    callback.accept(finalHearts);
                }
            });
        });
    }

    public void savePlayerData(UUID uuid, boolean removeFromCache) {
        if (heartCache.containsKey(uuid)) {
            int hearts = heartCache.get(uuid);
            // Save to database asynchronously.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                databaseManager.setPlayerHearts(uuid, hearts);
                if (removeFromCache) {
                    heartCache.remove(uuid);
                }
            });
        } else {
            // Should never happen.
            plugin.getLogger().warning("Attempted to save data for UUID " + uuid + " which was not in cache.");
        }
    }

    public void saveAllPlayerData() {
        plugin.getLogger().info("Saving all player heart data synchronously...");
        for (UUID uuid : heartCache.keySet()) {
            if (heartCache.containsKey(uuid)) {
                 int hearts = heartCache.get(uuid);
                 databaseManager.setPlayerHearts(uuid, hearts);
             } else {
                 // Should never happen.
                 plugin.getLogger().warning("Attempted to save data for UUID " + uuid + " which was not in cache during shutdown.");
             }
        }
        plugin.getLogger().info("Finished saving player heart data synchronously.");
    }

    public int getPlayerHearts(UUID uuid) {
        if (heartCache.containsKey(uuid)) {
            return heartCache.get(uuid);
        } else {
            int dbHearts = databaseManager.getPlayerHearts(uuid);
            return (dbHearts == -1) ? startingHearts : dbHearts;
        }
    }

    public int getPlayerMaxHearts(UUID uuid) {
        Integer individualMax = databaseManager.getPlayerMaxHearts(uuid);
        return (individualMax != null) ? individualMax : maxHearts;
    }

    public void setPlayerMaxHearts(UUID uuid, int newMaxHearts) {
        if (newMaxHearts < 1) {
            plugin.getLogger().warning("Attempted to set max hearts to " + newMaxHearts + " for UUID " + uuid + " (minimum is 1)");
            return;
        }

        databaseManager.setPlayerMaxHearts(uuid, newMaxHearts);

        int currentHearts = getPlayerHearts(uuid);
        if (currentHearts > newMaxHearts) {
            setPlayerHearts(uuid, newMaxHearts);
        }

        plugin.getLogger().info("Set max hearts to " + newMaxHearts + " for UUID " + uuid);
    }

    public boolean increasePlayerMaxHearts(UUID uuid, int amount) {
        if (amount <= 0) {
            return false;
        }

        int currentMax = getPlayerMaxHearts(uuid);
        int newMax = currentMax + amount;
        setPlayerMaxHearts(uuid, newMax);

        plugin.getLogger().info("Increased max hearts by " + amount + " for UUID " + uuid + " (new max: " + newMax + ")");
        return true;
    }

    private void setHeartsInternal(UUID uuid, int hearts) {
        // Get individual player's max hearts instead of global max
        int playerMaxHearts = getPlayerMaxHearts(uuid);
        // Clamp hearts (0 to playerMaxHearts).
        hearts = Math.max(minHearts, Math.min(hearts, playerMaxHearts));
        heartCache.put(uuid, hearts);

        // Update player's actual max health if they are online
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updatePlayerMaxHealth(player, hearts);
        }
    }

    private void updatePlayerMaxHealth(Player player, int hearts) {
        // Ensure hearts is at least 1 (representing half a heart in game)
        double newMaxHealth = Math.max(1.0, hearts * 2.0); 
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMaxHealth);
            if (player.getHealth() > newMaxHealth) {
                player.setHealth(newMaxHealth);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update max health for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void setPlayerHearts(UUID uuid, int hearts) {
        setHeartsInternal(uuid, hearts);
        savePlayerData(uuid, false);
    }

    public void addHearts(UUID uuid, int amount) {
        int currentHearts = getPlayerHearts(uuid);
        int newHearts = currentHearts + amount;
        setPlayerHearts(uuid, newHearts);
    }

    public void removeHearts(UUID uuid, int amount) {
        int currentHearts = getPlayerHearts(uuid);
        int newHearts = currentHearts - amount;
        setPlayerHearts(uuid, newHearts);
    }

    /**
     * Intended for API usage (shop plugins).
     *
     * @param playerUuid The UUID of the player.
     * @param amount The number of hearts to add.
     * @return true if hearts were successfully added, false otherwise (invalid amount or player already at max hearts).
     */
    public boolean givePlayerHearts(UUID playerUuid, int amount) {
        if (amount <= 0) {
            return false;
        }

        int currentHearts = getPlayerHearts(playerUuid);
        int playerMaxHearts = getPlayerMaxHearts(playerUuid);

        if (currentHearts >= playerMaxHearts) {
            return false;
        }

        int newHearts = currentHearts + amount;

        if (newHearts > playerMaxHearts) {
            newHearts = playerMaxHearts;
        }

        setPlayerHearts(playerUuid, newHearts);
        plugin.getLogger().info("API: Added " + (newHearts - currentHearts) + " heart(s) to " + playerUuid + ". New total: " + newHearts);
        return true;
    }

}