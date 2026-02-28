package com.jellypudding.simpleLifesteal.shrine;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.utils.PlayerNameUtil;
import com.jellypudding.simpleLifesteal.utils.UnbanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ShrineManager {

    private final SimpleLifesteal plugin;
    private ShrineData activeShrine;
    private BukkitTask spawnTask;
    private BukkitTask expiryTask;
    private final Random random = new Random();

    private Runnable onShrineRemoved;

    public ShrineManager(SimpleLifesteal plugin) {
        this.plugin = plugin;
    }

    public void setOnShrineRemoved(Runnable callback) {
        this.onShrineRemoved = callback;
    }

    public void scheduleNextSpawn() {
        if (!plugin.getConfig().getBoolean("shrine.enabled", true)) return;

        int minMinutes = plugin.getConfig().getInt("shrine.spawn-interval-min", 420);
        int maxMinutes = plugin.getConfig().getInt("shrine.spawn-interval-max", 720);
        if (minMinutes >= maxMinutes) maxMinutes = minMinutes + 1;

        int delayMinutes = minMinutes + random.nextInt(maxMinutes - minMinutes);
        long delayTicks = delayMinutes * 60L * 20L;

        spawnTask = Bukkit.getScheduler().runTaskLater(plugin, this::spawnShrine, delayTicks);
        plugin.getLogger().info("Next Blood Shrine will appear in " + delayMinutes + " minutes.");
    }

    public void spawnShrine() {
        if (!plugin.getConfig().getBoolean("shrine.enabled", true)) return;

        if (activeShrine != null) {
            plugin.getLogger().warning("Attempted to spawn a Blood Shrine while one is already active.");
            scheduleNextSpawn();
            return;
        }

        String worldName = plugin.getConfig().getString("shrine.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Shrine world '" + worldName + "' not found! Check shrine.world in config.");
            scheduleNextSpawn();
            return;
        }

        int radius = plugin.getConfig().getInt("shrine.spawn-radius", 4500);

        // Load candidate chunks asynchronously so the main thread is never blocked waiting
        // for chunk generation or disk reads.
        findValidLocationAsync(world, radius, 0, spawnLoc -> {
            if (spawnLoc == null) {
                plugin.getLogger().warning("Could not find a valid Blood Shrine location after 30 attempts. Retrying in 5 minutes.");
                Bukkit.getScheduler().runTaskLater(plugin, this::spawnShrine, 20L * 60L * 5L);
                return;
            }
            finishSpawn(spawnLoc);
        });
    }

    /**
     * Recursively searches for a valid shrine location, loading each candidate chunk
     * asynchronously. The callback is always called on the main thread.
     */
    private void findValidLocationAsync(World world, int radius, int attempt, Consumer<Location> callback) {
        if (attempt >= 30) {
            callback.accept(null);
            return;
        }

        double angle = random.nextDouble() * 2 * Math.PI;
        double r     = random.nextDouble() * radius;
        int x = (int) Math.round(r * Math.cos(angle));
        int z = (int) Math.round(r * Math.sin(angle));

        // getChunkAtAsync returns a CompletableFuture whose callback runs on the main thread
        // once the chunk is fully loaded/generated — no blocking.
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int surfaceY = world.getHighestBlockYAt(x, z);
            if (surfaceY < 60) {
                findValidLocationAsync(world, radius, attempt + 1, callback);
                return;
            }

            Block ground = world.getBlockAt(x, surfaceY, z);
            Material mat = ground.getType();

            if (!mat.isSolid() || mat == Material.WATER || mat == Material.LAVA
                    || mat.name().contains("LEAVES")
                    || mat == Material.ICE || mat == Material.FROSTED_ICE
                    || mat == Material.PACKED_ICE || mat == Material.BLUE_ICE) {
                findValidLocationAsync(world, radius, attempt + 1, callback);
                return;
            }

            // Need 4 clear blocks above for the shrine structure
            for (int dy = 1; dy <= 4; dy++) {
                Material above = world.getBlockAt(x, surfaceY + dy, z).getType();
                if (above != Material.AIR && above != Material.CAVE_AIR) {
                    findValidLocationAsync(world, radius, attempt + 1, callback);
                    return;
                }
            }

            callback.accept(new Location(world, x, surfaceY + 1, z));
        });
    }

    /** Called on the main thread once a valid spawn location has been found. */
    private void finishSpawn(Location spawnLoc) {
        int costMin   = plugin.getConfig().getInt("shrine.hearts-cost-min", 5);
        int costMax   = plugin.getConfig().getInt("shrine.hearts-cost-max", 100);
        int unbanMin  = plugin.getConfig().getInt("shrine.unbans-min", 1);
        int unbanMax  = plugin.getConfig().getInt("shrine.unbans-max", 5);
        int durMin    = plugin.getConfig().getInt("shrine.duration-min", 20);
        int durMax    = plugin.getConfig().getInt("shrine.duration-max", 50);

        if (costMin  > costMax)  costMax  = costMin;
        if (unbanMin > unbanMax) unbanMax = unbanMin;
        if (durMin   > durMax)   durMax   = durMin;

        int heartsCost   = costMin  + (costMin  == costMax  ? 0 : random.nextInt(costMax  - costMin  + 1));
        int unbans       = unbanMin + (unbanMin == unbanMax ? 0 : random.nextInt(unbanMax - unbanMin + 1));
        int durationMins = durMin   + (durMin   == durMax   ? 0 : random.nextInt(durMax   - durMin   + 1));
        long expiryMs    = System.currentTimeMillis() + durationMins * 60L * 1000L;

        activeShrine = new ShrineData(spawnLoc, unbans, expiryMs, heartsCost);
        buildShrine(spawnLoc, activeShrine);

        int bx = spawnLoc.getBlockX();
        int bz = spawnLoc.getBlockZ();

        Component announcement = Component.text()
                .append(Component.text("A ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Blood Shrine", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text(" has appeared at (" + bx + ", " + bz + ").", NamedTextColor.DARK_GRAY))
                .build();

        Bukkit.broadcast(announcement);

        if (plugin.isDiscordRelayAPIReady()) {
            try {
                com.jellypudding.discordRelay.DiscordRelayAPI.sendFormattedMessage(
                        "Blood Shrine",
                        "A Blood Shrine has appeared at (" + bx + ", " + bz + ").",
                        new Color(139, 0, 0)
                );
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Blood Shrine Discord notification.", e);
            }
        }

        expiryTask = Bukkit.getScheduler().runTaskLater(plugin, this::detonateShrine, durationMins * 60L * 20L);

        plugin.getLogger().info("Blood Shrine spawned at (" + bx + ", " + spawnLoc.getBlockY() + ", " + bz + ") — "
                + unbans + " unban(s), cost " + heartsCost + " hearts, duration " + durationMins + " min.");
    }

    private void buildShrine(Location base, ShrineData data) {
        World world = base.getWorld();
        int cx = base.getBlockX();
        int cy = base.getBlockY();
        int cz = base.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material mat = (dx == 0 && dz == 0) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN;
                place(world, data, cx + dx, cy, cz + dz, mat);
            }
        }

        place(world, data, cx,     cy + 1, cz,     Material.SOUL_SAND);
        place(world, data, cx - 1, cy + 1, cz - 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx + 1, cy + 1, cz - 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx - 1, cy + 1, cz + 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx + 1, cy + 1, cz + 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx - 1, cy + 1, cz,     Material.SOUL_LANTERN);
        place(world, data, cx + 1, cy + 1, cz,     Material.SOUL_LANTERN);
        place(world, data, cx,     cy + 1, cz - 1, Material.SOUL_LANTERN);
        place(world, data, cx,     cy + 1, cz + 1, Material.SOUL_LANTERN);

        place(world, data, cx,     cy + 2, cz,     Material.SOUL_FIRE);
        place(world, data, cx - 1, cy + 2, cz - 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx + 1, cy + 2, cz - 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx - 1, cy + 2, cz + 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx + 1, cy + 2, cz + 1, Material.POLISHED_BLACKSTONE);
        place(world, data, cx,     cy + 2, cz - 1, Material.IRON_BARS);
        place(world, data, cx,     cy + 2, cz + 1, Material.IRON_BARS);
        place(world, data, cx - 1, cy + 2, cz,     Material.IRON_BARS);
        place(world, data, cx + 1, cy + 2, cz,     Material.IRON_BARS);

        // Skulls face inward toward the shrine centre
        placeSkull(world, data, cx - 1, cy + 3, cz - 1, BlockFace.SOUTH_EAST);
        placeSkull(world, data, cx + 1, cy + 3, cz - 1, BlockFace.SOUTH_WEST);
        placeSkull(world, data, cx - 1, cy + 3, cz + 1, BlockFace.NORTH_EAST);
        placeSkull(world, data, cx + 1, cy + 3, cz + 1, BlockFace.NORTH_WEST);
    }

    private void place(World world, ShrineData data, int x, int y, int z, Material mat) {
        Location loc = new Location(world, x, y, z);
        data.getPlacedBlocks().add(loc);
        world.getBlockAt(loc).setType(mat, false);
    }

    private void placeSkull(World world, ShrineData data, int x, int y, int z, BlockFace facing) {
        Location loc = new Location(world, x, y, z);
        data.getPlacedBlocks().add(loc);
        Block block = world.getBlockAt(loc);
        block.setType(Material.WITHER_SKELETON_SKULL, false);
        Rotatable rotatable = (Rotatable) block.getBlockData();
        rotatable.setRotation(facing);
        block.setBlockData(rotatable, false);
    }

    public void detonateShrine() {
        if (activeShrine == null) return;

        Location explosionCenter = activeShrine.getCenter().add(0, 2, 0);
        demolishBlocks();

        explosionCenter.getWorld().strikeLightningEffect(explosionCenter);
        explosionCenter.getWorld().createExplosion(explosionCenter, 3.0f, false, false);

        Bukkit.broadcast(Component.text()
                .append(Component.text("The ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Blood Shrine", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text(" has exploded.", NamedTextColor.DARK_GRAY))
                .build());

        if (plugin.isDiscordRelayAPIReady()) {
            try {
                com.jellypudding.discordRelay.DiscordRelayAPI.sendFormattedMessage(
                        "Blood Shrine",
                        "The Blood Shrine has exploded.",
                        new Color(139, 0, 0)
                );
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Blood Shrine detonation Discord notification.", e);
            }
        }

        scheduleNextSpawn();
    }

    private void demolishBlocks() {
        if (activeShrine == null) return;

        for (Location loc : activeShrine.getPlacedBlocks()) {
            Block block = loc.getBlock();
            if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR) {
                block.setType(Material.AIR, false);
            }
        }

        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }

        activeShrine = null;

        if (onShrineRemoved != null) {
            onShrineRemoved.run();
        }
    }

    public void performUnban(Player performer, String targetName) {
        if (activeShrine == null) {
            performer.sendMessage(Component.text("There is no active Blood Shrine.", NamedTextColor.RED));
            return;
        }

        double useRadius = plugin.getConfig().getDouble("shrine.use-radius", 10);
        if (!activeShrine.isNearby(performer.getLocation(), useRadius)) {
            performer.sendMessage(Component.text("You need to be closer to the Blood Shrine.", NamedTextColor.RED));
            return;
        }

        // Check heart items in inventory before the (potentially async) lookup
        int cost = activeShrine.getHeartsCost();
        int itemCount = countHeartItems(performer.getInventory());
        if (itemCount < cost) {
            performer.sendMessage(Component.text("You need " + cost + " heart" + (cost == 1 ? "" : "s")
                    + " in your inventory. You have " + itemCount + ".", NamedTextColor.RED));
            return;
        }

        if (targetName.startsWith(".")) {
            performer.sendMessage(Component.text("Looking up Bedrock player...", NamedTextColor.GRAY));
        }

        UnbanService.unban(plugin, targetName, (uuid, unbanned) -> {
            // Always process the result on the main thread (the lookup may be async for Bedrock players)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (activeShrine == null) {
                    performer.sendMessage(Component.text("The Blood Shrine has disappeared.", NamedTextColor.RED));
                    return;
                }

                if (uuid == null) {
                    performer.sendMessage(Component.text("Player '" + targetName + "' not found.", NamedTextColor.RED));
                    return;
                }

                OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(uuid);
                String resolvedName = targetOffline.getName() != null ? targetOffline.getName() : targetName;
                Component targetComponent = PlayerNameUtil.getFormattedPlayerName(plugin.getChromaTagAPI(), uuid, resolvedName);

                if (!unbanned) {
                    performer.sendMessage(Component.text()
                            .append(targetComponent)
                            .append(Component.text(" is not banned by the Lifesteal system.", NamedTextColor.RED))
                            .build());
                    return;
                }

                // Consume heart items from inventory
                removeHeartItems(performer.getInventory(), cost);

                activeShrine.decrementUnbans();

                plugin.getLogger().info(performer.getName() + " used the Blood Shrine to unban " + resolvedName
                        + " (UUID: " + uuid + "), sacrificing " + cost + " heart(s).");

                plugin.getDatabaseManager().recordShrineUnban(
                        uuid, resolvedName,
                        performer.getUniqueId(), performer.getName()
                );

                performer.sendMessage(Component.text()
                        .append(Component.text("You sacrificed " + cost + " heart" + (cost == 1 ? "" : "s") + " to free ", NamedTextColor.DARK_GRAY))
                        .append(targetComponent)
                        .build());

                UnbanService.removeGameBan(targetOffline);

                Bukkit.broadcast(Component.text()
                        .append(performer.displayName())
                        .append(Component.text(" sacrificed " + cost + " heart" + (cost == 1 ? "" : "s") + " at the ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Blood Shrine", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                        .append(Component.text(" to free ", NamedTextColor.DARK_GRAY))
                        .append(targetComponent)
                        .build());

                if (plugin.isDiscordRelayAPIReady()) {
                    try {
                        com.jellypudding.discordRelay.DiscordRelayAPI.sendFormattedMessage(
                                "Blood Shrine Unban",
                                performer.getName() + " sacrificed " + cost + " heart" + (cost == 1 ? "" : "s") + " at the Blood Shrine to unban " + resolvedName + ".",
                                new Color(139, 0, 0)
                        );
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to send Blood Shrine unban Discord notification.", e);
                    }
                }

                if (activeShrine.isExhausted()) {
                    detonateShrine();
                }
            });
        });
    }

    private int countHeartItems(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && plugin.getHeartItemUtil().isHeartItem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeHeartItems(Inventory inv, int amount) {
        int toRemove = amount;
        for (int i = 0; i < inv.getSize() && toRemove > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && plugin.getHeartItemUtil().isHeartItem(item)) {
                if (item.getAmount() <= toRemove) {
                    toRemove -= item.getAmount();
                    inv.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                    toRemove = 0;
                }
            }
        }
    }

    public ShrineData getActiveShrine() {
        return activeShrine;
    }

    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (activeShrine != null) {
            demolishBlocks();
        }
    }
}
