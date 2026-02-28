package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.shrine.ShrineData;
import com.jellypudding.simpleLifesteal.shrine.ShrineManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShrineCommand implements CommandExecutor, TabCompleter {

    private final SimpleLifesteal plugin;
    private final ShrineManager shrineManager;

    public ShrineCommand(SimpleLifesteal plugin, ShrineManager shrineManager) {
        this.plugin = plugin;
        this.shrineManager = shrineManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unban"  -> handleUnban(sender, args);
            case "spawn"  -> handleSpawn(sender);
            case "cancel" -> handleCancel(sender);
            case "info"   -> handleInfo(sender);
            default       -> sendHelp(sender);
        }
        return true;
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can perform shrine unbans.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("simplelifesteal.shrine.unban")) {
            player.sendMessage(Component.text("You do not have permission to use this.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /shrine unban <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        // Minecraft names: 1-25 chars, alphanumeric + underscore; Bedrock names are prefixed with '.'
        if (!targetName.matches("\\.?[a-zA-Z0-9_]{1,25}")) {
            player.sendMessage(Component.text("Invalid player name.", NamedTextColor.RED));
            return;
        }

        shrineManager.performUnban(player, targetName);
    }

    private void handleSpawn(CommandSender sender) {
        if (!sender.hasPermission("simplelifesteal.shrine.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this.", NamedTextColor.RED));
            return;
        }

        if (shrineManager.getActiveShrine() != null) {
            sender.sendMessage(Component.text("A Blood Shrine is already active.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Forcing Blood Shrine spawn...", NamedTextColor.GRAY));
        shrineManager.spawnShrine();
    }

    private void handleCancel(CommandSender sender) {
        if (!sender.hasPermission("simplelifesteal.shrine.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this.", NamedTextColor.RED));
            return;
        }

        if (shrineManager.getActiveShrine() == null) {
            sender.sendMessage(Component.text("There is no active Blood Shrine to cancel.", NamedTextColor.RED));
            return;
        }

        shrineManager.detonateShrine();
        sender.sendMessage(Component.text("Blood Shrine detonated.", NamedTextColor.YELLOW));
    }

    private void handleInfo(CommandSender sender) {
        if (!sender.hasPermission("simplelifesteal.shrine.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this.", NamedTextColor.RED));
            return;
        }

        ShrineData shrine = shrineManager.getActiveShrine();
        if (shrine == null) {
            sender.sendMessage(Component.text("No Blood Shrine is currently active.", NamedTextColor.GRAY));
            return;
        }

        long secs = shrine.getRemainingSeconds();
        long mins = secs / 60;
        long rSec = secs % 60;

        sender.sendMessage(Component.text("Blood Shrine", NamedTextColor.DARK_RED));
        sender.sendMessage(Component.text("  Location: ", NamedTextColor.GRAY)
                .append(Component.text(formatLoc(shrine.getCenter()), NamedTextColor.RED)));
        sender.sendMessage(Component.text("  Heart cost: ", NamedTextColor.GRAY)
                .append(Component.text(shrine.getHeartsCost() + " hearts", NamedTextColor.RED)));
        sender.sendMessage(Component.text("  Unbans remaining: ", NamedTextColor.GRAY)
                .append(Component.text(shrine.getRemainingUnbans(), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  Expires in: ", NamedTextColor.GRAY)
                .append(Component.text(mins + "m " + String.format("%02d", rSec) + "s", NamedTextColor.YELLOW)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Collections.singletonList("unban"));
            if (sender.hasPermission("simplelifesteal.shrine.admin")) {
                subs.addAll(Arrays.asList("spawn", "cancel", "info"));
            }
            return filterStart(subs, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            if (!sender.hasPermission("simplelifesteal.shrine.unban")) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filterStart(names, args[1]);
        }

        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/shrine unban <player>", NamedTextColor.RED)
                .append(Component.text("  sacrifice heart items at a Blood Shrine to free a banned player", NamedTextColor.GRAY)));
        if (sender.hasPermission("simplelifesteal.shrine.admin")) {
            sender.sendMessage(Component.text("/shrine spawn", NamedTextColor.RED)
                    .append(Component.text("  force a shrine to spawn", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/shrine cancel", NamedTextColor.RED)
                    .append(Component.text("  detonate the active shrine", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/shrine info", NamedTextColor.RED)
                    .append(Component.text("  show active shrine details", NamedTextColor.GRAY)));
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private List<String> filterStart(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) result.add(s);
        }
        return result;
    }
}
