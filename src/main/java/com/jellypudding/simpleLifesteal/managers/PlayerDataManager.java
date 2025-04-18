package com.jellypudding.simpleLifesteal.managers;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import org.bukkit.Bukkit;
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
                // Execute the callback with the loaded heart count.
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
        plugin.getLogger().info("Saving all player heart data...");
        for (UUID uuid : heartCache.keySet()) {
            savePlayerData(uuid, false);
        }
        plugin.getLogger().info("Finished saving player heart data.");
    }

    public int getPlayerHearts(UUID uuid) {
        return heartCache.getOrDefault(uuid, startingHearts);
    }

    private void setHeartsInternal(UUID uuid, int hearts) {
        // Clamp hearts (0 to maxHearts).
        hearts = Math.max(minHearts, Math.min(hearts, maxHearts));
        heartCache.put(uuid, hearts);
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
        int maxHearts = plugin.getMaxHearts();

        if (currentHearts >= maxHearts) {
            return false;
        }

        int newHearts = currentHearts + amount;

        if (newHearts > maxHearts) {
            newHearts = maxHearts;
        }

        setPlayerHearts(playerUuid, newHearts);
        plugin.getLogger().info("API: Added " + (newHearts - currentHearts) + " heart(s) to " + playerUuid + ". New total: " + newHearts);
        return true;
    }

} 