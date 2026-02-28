package com.jellypudding.simpleLifesteal.shrine;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ShrineData {

    private final Location center;
    private int remainingUnbans;
    private final long createdTimeMillis;
    private final long expiryTimeMillis;
    private final int heartsCost;
    private final List<Location> placedBlocks = new ArrayList<>();

    public ShrineData(Location center, int remainingUnbans, long expiryTimeMillis, int heartsCost) {
        this.center = center.clone();
        this.remainingUnbans = remainingUnbans;
        this.createdTimeMillis = System.currentTimeMillis();
        this.expiryTimeMillis = expiryTimeMillis;
        this.heartsCost = heartsCost;
    }

    public Location getCenter() {
        return center.clone();
    }

    public int getRemainingUnbans() {
        return remainingUnbans;
    }

    public void decrementUnbans() {
        remainingUnbans--;
    }

    public long getCreatedTimeMillis() {
        return createdTimeMillis;
    }

    public long getExpiryTimeMillis() {
        return expiryTimeMillis;
    }

    public int getHeartsCost() {
        return heartsCost;
    }

    public List<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public long getRemainingSeconds() {
        return Math.max(0, (expiryTimeMillis - System.currentTimeMillis()) / 1000);
    }

    public float getTimeProgress() {
        long total = expiryTimeMillis - createdTimeMillis;
        long remaining = expiryTimeMillis - System.currentTimeMillis();
        if (total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) remaining / total));
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTimeMillis;
    }

    public boolean isExhausted() {
        return remainingUnbans <= 0;
    }

    public boolean isNearby(Location loc, double radius) {
        if (loc.getWorld() == null || !loc.getWorld().equals(center.getWorld())) return false;
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= radius;
    }

    public boolean removeShrineBlock(Location blockLoc) {
        Iterator<Location> iter = placedBlocks.iterator();
        while (iter.hasNext()) {
            Location loc = iter.next();
            if (loc.getWorld() != null && loc.getWorld().equals(blockLoc.getWorld())
                    && loc.getBlockX() == blockLoc.getBlockX()
                    && loc.getBlockY() == blockLoc.getBlockY()
                    && loc.getBlockZ() == blockLoc.getBlockZ()) {
                iter.remove();
                return true;
            }
        }
        return false;
    }

    public void purgeDestroyedBlocks() {
        placedBlocks.removeIf(loc -> {
            if (loc.getWorld() == null) return true;
            Material type = loc.getWorld().getBlockAt(loc).getType();
            return type == Material.AIR || type == Material.CAVE_AIR;
        });
    }

    public boolean isFullyDestroyed() {
        return placedBlocks.isEmpty();
    }
}
