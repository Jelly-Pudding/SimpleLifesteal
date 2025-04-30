package com.jellypudding.simpleLifesteal;

import com.jellypudding.simpleLifesteal.commands.HeartsCommand;
import com.jellypudding.simpleLifesteal.commands.IsBannedCommand;
import com.jellypudding.simpleLifesteal.commands.SlUnbanCommand;
import com.jellypudding.simpleLifesteal.commands.CheckBanResultCommand;
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
            return false; // Indicate failure
        }
        // Delegate to the method in PlayerDataManager
        return playerDataManager.givePlayerHearts(playerUuid, amount);
    }
}
