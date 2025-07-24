package com.jellypudding.simpleLifesteal;

import com.jellypudding.simpleLifesteal.commands.HeartsCommand;
import com.jellypudding.simpleLifesteal.commands.IsBannedCommand;
import com.jellypudding.simpleLifesteal.commands.SlUnbanCommand;
import com.jellypudding.simpleLifesteal.commands.CheckBanResultCommand;
import com.jellypudding.simpleLifesteal.commands.HeartWithdrawCommand;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import com.jellypudding.simpleLifesteal.listeners.PlayerListener;
import com.jellypudding.simpleLifesteal.managers.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.jellypudding.simpleLifesteal.utils.BanCheckResult;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class SimpleLifesteal extends JavaPlugin {

    private int startingHearts;
    private int maxHearts;
    private String banMessage;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    // Map to store results of async ban checks (PlayerName -> BanCheckResult).
    private final Map<String, BanCheckResult> pendingBanResults = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        startingHearts = getConfig().getInt("starting-hearts", 10);
        maxHearts = getConfig().getInt("maximum-hearts", 20);
        banMessage = getConfig().getString("ban-message", "You ran out of hearts!");

        getLogger().info("SimpleLifesteal enabled!");

        // Initialise database.
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.getConnection();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialise database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialise player data manager.
        playerDataManager = new PlayerDataManager(this);

        // Register Commands.
        getCommand("hearts").setExecutor(new HeartsCommand(this));
        getCommand("withdrawheart").setExecutor(new HeartWithdrawCommand(this));
        getCommand("isbanned").setExecutor(new IsBannedCommand(this));
        getCommand("slunban").setExecutor(new SlUnbanCommand(this));
        getCommand("checkbanresult").setExecutor(new CheckBanResultCommand(this));

        // Register Event Listeners.
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("SimpleLifesteal fully enabled.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }

        getLogger().info("SimpleLifesteal disabled.");
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
    }

    public int getStartingHearts() {
        return startingHearts;
    }

    public int getMaxHearts() {
        return maxHearts;
    }

    public String getBanMessage() {
        return banMessage;
    }

    public Map<String, BanCheckResult> getPendingBanResults() {
        return pendingBanResults;
    }

    // --- Manager Getters ---
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    // --- Public API Methods ---

    /**
     * Public API method to give a player hearts.
     * Handles clamping hearts between min (0) and max configured hearts.
     * Saves the data to the database.
     *
     * @param playerUuid The UUID of the player.
     * @param amount The number of hearts to add (must be positive).
     * @return true if hearts were successfully added, false otherwise (invalid amount or player already at max hearts).
     */
    public boolean addHearts(UUID playerUuid, int amount) {
        if (playerDataManager == null) {
            getLogger().severe("Attempted to call addHearts API before PlayerDataManager was initialised!");
            return false;
        }
        return playerDataManager.givePlayerHearts(playerUuid, amount);
    }

    /**
     * Public API method to get a player's current heart count.
     *
     * @param playerUuid The UUID of the player.
     * @return The player's current heart count, or starting hearts if player not found.
     */
    public int getPlayerHearts(UUID playerUuid) {
        if (playerDataManager == null) {
            getLogger().severe("Attempted to call getPlayerHearts API before PlayerDataManager was initialised!");
            return getStartingHearts();
        }
        return playerDataManager.getPlayerHearts(playerUuid);
    }

    /**
     * Public API method to get a player's maximum heart limit.
     * Returns the individual player's limit if set, otherwise the global maximum.
     *
     * @param playerUuid The UUID of the player.
     * @return The player's maximum heart limit.
     */
    public int getPlayerMaxHearts(UUID playerUuid) {
        if (playerDataManager == null) {
            getLogger().severe("Attempted to call getPlayerMaxHearts API before PlayerDataManager was initialised!");
            return getMaxHearts();
        }
        return playerDataManager.getPlayerMaxHearts(playerUuid);
    }

    /**
     * Public API method to set a player's maximum heart limit.
     * This allows the player to exceed the global maximum-hearts configuration.
     *
     * @param playerUuid The UUID of the player.
     * @param maxHearts The new maximum heart limit (must be at least 1).
     * @return true if the limit was successfully set, false otherwise.
     */
    public boolean setPlayerMaxHearts(UUID playerUuid, int maxHearts) {
        if (playerDataManager == null) {
            getLogger().severe("Attempted to call setPlayerMaxHearts API before PlayerDataManager was initialised!");
            return false;
        }
        if (maxHearts < 1) {
            return false;
        }
        playerDataManager.setPlayerMaxHearts(playerUuid, maxHearts);
        return true;
    }

    /**
     * Public API method to increase a player's maximum heart limit.
     * This allows the player to exceed the global maximum-hearts configuration.
     *
     * @param playerUuid The UUID of the player.
     * @param amount The amount to increase the limit by (must be positive).
     * @return true if the limit was successfully increased, false otherwise.
     */
    public boolean increasePlayerMaxHearts(UUID playerUuid, int amount) {
        if (playerDataManager == null) {
            getLogger().severe("Attempted to call increasePlayerMaxHearts API before PlayerDataManager was initialised!");
            return false;
        }
        return playerDataManager.increasePlayerMaxHearts(playerUuid, amount);
    }
}
