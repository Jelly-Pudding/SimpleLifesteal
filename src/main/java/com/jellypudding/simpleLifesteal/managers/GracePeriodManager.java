package com.jellypudding.simpleLifesteal.managers;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GracePeriodManager {

    private final SimpleLifesteal plugin;
    private com.jellypudding.offlineStats.api.OfflineStatsAPI offlineStatsAPI;
    private final boolean enabled;
    private final long gracePeriodMillis;

    private final Map<UUID, Long> activeGracePeriodPlayers = new ConcurrentHashMap<>();

    public GracePeriodManager(SimpleLifesteal plugin, com.jellypudding.offlineStats.api.OfflineStatsAPI offlineStatsAPI) {
        this.plugin = plugin;
        this.offlineStatsAPI = offlineStatsAPI;
        this.enabled = plugin.getConfig().getBoolean("grace-period.enabled", false);
        int gracePeriodHours = plugin.getConfig().getInt("grace-period.duration-hours", 1);
        this.gracePeriodMillis = gracePeriodHours * 60 * 60 * 1000L;

        if (enabled && offlineStatsAPI != null) {
            startMonitoringTask();
        }
    }

    public long checkPlaytimeAndGetRemaining(UUID playerUuid) {
        if (!enabled || offlineStatsAPI == null) {
            return -1;
        }

        try {
            long timePlayed = offlineStatsAPI.getPlayerTimePlayed(playerUuid);
            if (timePlayed < 0 || timePlayed >= gracePeriodMillis) {
                return -1;
            }
            return gracePeriodMillis - timePlayed;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error checking playtime for UUID " + playerUuid, e);
            return -1;
        }
    }

    public boolean isPlayerInGracePeriod(UUID playerUuid) {
        return activeGracePeriodPlayers.containsKey(playerUuid);
    }

    public boolean checkGracePeriodEligibility(UUID playerUuid) {
        if (!enabled || offlineStatsAPI == null) {
            return false;
        }

        int existingHearts = plugin.getDatabaseManager().getPlayerHearts(playerUuid);
        if (existingHearts != -1) {
            return false;
        }

        long remainingMs = checkPlaytimeAndGetRemaining(playerUuid);
        return remainingMs > 0;
    }

    public boolean handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        int existingHearts = plugin.getDatabaseManager().getPlayerHearts(uuid);
        if (existingHearts == -1) {
            long remainingMs = checkPlaytimeAndGetRemaining(uuid);
            if (remainingMs > 0) {
                activeGracePeriodPlayers.put(uuid, System.currentTimeMillis() + remainingMs);
                showGracePeriodTitle(player);
                return true;
            }
        }
        return false;
    }

    public boolean handlePlayerQuit(UUID playerUuid) {
        if (activeGracePeriodPlayers.containsKey(playerUuid)) {
            activeGracePeriodPlayers.remove(playerUuid);
            return true;
        }
        return false;
    }

    private void showGracePeriodTitle(Player player) {
        Long expiryTime = activeGracePeriodPlayers.get(player.getUniqueId());
        if (expiryTime == null) return;

        long remainingMs = expiryTime - System.currentTimeMillis();
        int remainingMinutes = (int) (remainingMs / 60000);

        Title graceTitle = Title.title(
            Component.text("Grace Period Active", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("Lifesteal disabled for " + remainingMinutes + " more minutes", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
        );
        player.showTitle(graceTitle);
    }

    private void startMonitoringTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            for (Map.Entry<UUID, Long> entry : activeGracePeriodPlayers.entrySet()) {
                UUID uuid = entry.getKey();
                long expiryTime = entry.getValue();

                if (currentTime >= expiryTime) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        plugin.getLogger().info("Grace period ended for " + player.getName() + ". Adding to Lifesteal database.");

                        plugin.getPlayerDataManager().loadPlayerData(player, loadedHearts -> {
                            if (!player.isOnline()) return;

                            Title endTitle = Title.title(
                                Component.text("Grace Period Ended", NamedTextColor.RED, TextDecoration.BOLD),
                                Component.text("Lifesteal is now active", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
                            );
                            player.showTitle(endTitle);
                            player.sendMessage(Component.text("Your grace period has ended. Lifesteal is now active.", NamedTextColor.RED));
                        });
                    }
                    activeGracePeriodPlayers.remove(uuid);
                }
            }
        }, 600L, 600L); // Check every 30 seconds (600 ticks)
    }
}

