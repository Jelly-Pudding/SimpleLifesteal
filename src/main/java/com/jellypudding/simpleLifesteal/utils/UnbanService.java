package com.jellypudding.simpleLifesteal.utils;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class UnbanService {

    public interface Callback {
        // uuid is null if the player could not be found by name.
        // unbanned is true only if a ban record was actually removed from the database.
        void done(@Nullable UUID uuid, boolean unbanned);
    }

    private UnbanService() {}

    public static void unban(SimpleLifesteal plugin, String playerName, Callback callback) {
        if (playerName.startsWith(".")) {
            PlayerUUIDUtil.fetchBedrockUUIDAsync(plugin, playerName, uuid -> {
                if (uuid == null) {
                    callback.done(null, false);
                    return;
                }
                boolean removed = plugin.getDatabaseManager().removePluginBan(uuid);
                callback.done(uuid, removed);
            });
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            callback.done(null, false);
            return;
        }

        boolean removed = plugin.getDatabaseManager().removePluginBan(target.getUniqueId());
        callback.done(target.getUniqueId(), removed);
    }

    public static void removeGameBan(OfflinePlayer player) {
        PlayerProfile profile = Bukkit.getServer().createProfile(player.getUniqueId(), player.getName());
        BanList<PlayerProfile> banList = Bukkit.getBanList(BanListType.PROFILE);
        BanEntry<PlayerProfile> entry = banList.getBanEntry(profile);
        if (entry != null) entry.remove();
    }
}
