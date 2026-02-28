package com.jellypudding.simpleLifesteal.listeners;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.shrine.ShrineData;
import com.jellypudding.simpleLifesteal.shrine.ShrineManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShrineListener implements Listener {

    private final SimpleLifesteal plugin;
    private final ShrineManager shrineManager;

    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Set<UUID> nearbyPlayers = new HashSet<>();

    private BukkitTask proximityTask;
    private BukkitTask soundTask;
    private BukkitTask particleTask;

    private static final Sound[] AMBIENT_SOUNDS = {
            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
            Sound.ENTITY_WITHER_AMBIENT,
            Sound.ENTITY_ENDERMAN_STARE,
            Sound.AMBIENT_NETHER_WASTES_MOOD,
            Sound.ENTITY_WITHER_SKELETON_AMBIENT
    };

    public ShrineListener(SimpleLifesteal plugin, ShrineManager shrineManager) {
        this.plugin = plugin;
        this.shrineManager = shrineManager;

        shrineManager.setOnShrineRemoved(this::clearAllBossBars);

        proximityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkProximity, 40L, 40L);   // every 2 seconds
        soundTask     = Bukkit.getScheduler().runTaskTimer(plugin, this::playAmbientSounds, 140L, 140L); // every 7 seconds
        particleTask  = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnShrineParticles, 20L, 20L);  // every second
    }

    private void checkProximity() {
        ShrineData shrine = shrineManager.getActiveShrine();

        if (shrine == null) {
            clearAllBossBars();
            return;
        }

        double radius = plugin.getConfig().getDouble("shrine.proximity-radius", 30);
        Set<UUID> nowNearby = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shrine.isNearby(player.getLocation(), radius)) {
                nowNearby.add(player.getUniqueId());
                updateBossBar(player, shrine);
            }
        }

        Set<UUID> leftArea = new HashSet<>(nearbyPlayers);
        leftArea.removeAll(nowNearby);
        for (UUID uuid : leftArea) {
            removeBossBar(uuid);
        }

        nearbyPlayers.clear();
        nearbyPlayers.addAll(nowNearby);
    }

    private void updateBossBar(Player player, ShrineData shrine) {
        long secs   = shrine.getRemainingSeconds();
        long mins   = secs / 60;
        long remSec = secs % 60;
        String timeStr = mins + "m " + String.format("%02d", remSec) + "s";

        Component title = Component.text()
                .append(Component.text("Blood Shrine", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Cost: ", NamedTextColor.GRAY))
                .append(Component.text(shrine.getHeartsCost() + " hearts", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Time: ", NamedTextColor.GRAY))
                .append(Component.text(timeStr, NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Unbans left: ", NamedTextColor.GRAY))
                .append(Component.text(shrine.getRemainingUnbans(), NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();

        float progress = shrine.getTimeProgress();

        BossBar bar = activeBossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            activeBossBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(Math.max(0f, Math.min(1f, progress)));
        }
    }

    private void removeBossBar(UUID uuid) {
        BossBar bar = activeBossBars.remove(uuid);
        if (bar == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.hideBossBar(bar);
        }
    }

    public void clearAllBossBars() {
        for (UUID uuid : new HashSet<>(activeBossBars.keySet())) {
            removeBossBar(uuid);
        }
        nearbyPlayers.clear();
    }

    private void playAmbientSounds() {
        ShrineData shrine = shrineManager.getActiveShrine();
        if (shrine == null || nearbyPlayers.isEmpty()) return;

        double radius = plugin.getConfig().getDouble("shrine.proximity-radius", 30);
        Location shrineCenter = shrine.getCenter();

        Sound chosen = AMBIENT_SOUNDS[(int) (Math.random() * AMBIENT_SOUNDS.length)];

        for (UUID uuid : nearbyPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (!shrine.isNearby(player.getLocation(), radius)) continue;
            player.playSound(shrineCenter, chosen, 0.6f, 0.85f);
        }
    }

    private void spawnShrineParticles() {
        ShrineData shrine = shrineManager.getActiveShrine();
        if (shrine == null) return;

        Location base = shrine.getCenter();
        if (base.getWorld() == null) return;

        base.getWorld().spawnParticle(Particle.SOUL,        base.clone().add(0, 2, 0),    3, 0.4, 0.5, 0.4, 0.02);
        base.getWorld().spawnParticle(Particle.ASH,         base.clone().add(0, 1, 0),    5, 1.0, 0.5, 1.0, 0.05);
        base.getWorld().spawnParticle(Particle.LARGE_SMOKE, base.clone().add(-1, 4, -1),  1, 0.1, 0.1, 0.1, 0.01);
        base.getWorld().spawnParticle(Particle.LARGE_SMOKE, base.clone().add( 1, 4, -1),  1, 0.1, 0.1, 0.1, 0.01);
        base.getWorld().spawnParticle(Particle.LARGE_SMOKE, base.clone().add(-1, 4,  1),  1, 0.1, 0.1, 0.1, 0.01);
        base.getWorld().spawnParticle(Particle.LARGE_SMOKE, base.clone().add( 1, 4,  1),  1, 0.1, 0.1, 0.1, 0.01);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ShrineData shrine = shrineManager.getActiveShrine();
        if (shrine == null) return;
        if (!shrine.removeShrineBlock(event.getBlock().getLocation())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ShrineData s = shrineManager.getActiveShrine();
            if (s == null) return;
            s.purgeDestroyedBlocks();
            if (s.isFullyDestroyed()) shrineManager.detonateShrine();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeBossBar(uuid);
        nearbyPlayers.remove(uuid);
    }

    public void shutdown() {
        if (proximityTask != null) proximityTask.cancel();
        if (soundTask      != null) soundTask.cancel();
        if (particleTask   != null) particleTask.cancel();
        clearAllBossBars();
    }
}
