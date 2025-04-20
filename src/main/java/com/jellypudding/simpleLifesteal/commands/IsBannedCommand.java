package com.jellypudding.simpleLifesteal.commands;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import com.jellypudding.simpleLifesteal.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class IsBannedCommand implements CommandExecutor {

    private final SimpleLifesteal plugin;
    private final DatabaseManager databaseManager;
    private final String bedrockPrefix = ".";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Pattern uuidPattern = Pattern.compile("\"id\"\s*:\s*\"([0-9a-fA-F-]+)\"");

    public IsBannedCommand(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    private void checkBedrockPlayerBanAsync(CommandSender sender, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = null;
            String errorMessage = null;
            int responseCode = -1;
            String responseBodyForDebug = "<No response body received>";

            try {
                String encodedPlayerName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
                String apiUrl = "https://api.geysermc.org/v2/utils/uuid/bedrock_or_java/" + encodedPlayerName + "?prefix=" + URLEncoder.encode(bedrockPrefix, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                responseCode = response.statusCode();

                if (responseCode == 200) {
                    String responseBody = response.body();
                    responseBodyForDebug = responseBody;
                    Matcher matcher = uuidPattern.matcher(responseBody);
                    if (matcher.find()) {
                        String uuidString = matcher.group(1);
                        if (uuidString != null && uuidString.length() == 32) {
                             String uuidWithHyphens = uuidString.substring(0, 8) + "-" +
                                                      uuidString.substring(8, 12) + "-" +
                                                      uuidString.substring(12, 16) + "-" +
                                                      uuidString.substring(16, 20) + "-" +
                                                      uuidString.substring(20);
                            //plugin.getLogger().info("[DEBUG] Formatted UUID string: " + uuidWithHyphens);
                            try {
                                 uuid = UUID.fromString(uuidWithHyphens);
                            } catch (IllegalArgumentException e) {
                                errorMessage = "Failed to parse formatted UUID string.";
                                plugin.getLogger().warning("Failed to parse formatted UUID: " + uuidWithHyphens + " from API Response: " + responseBodyForDebug);
                            }
                        } else {
                             errorMessage = "API returned unexpected UUID string format/length.";
                             plugin.getLogger().warning("Unexpected UUID string format/length from API: '" + uuidString + "' Response: " + responseBodyForDebug);
                        }
                    } else {
                         errorMessage = "Could not parse UUID from API response.";
                         plugin.getLogger().warning("Could not find UUID pattern in API response. Response: " + responseBodyForDebug);
                    }
                } else if (responseCode == 204 || responseCode == 302) {
                     errorMessage = "Player not found or is Java account.";
                 } else {
                    errorMessage = "Geyser API request failed with status code: " + responseCode;
                 }
                responseBodyForDebug = response.body();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error checking Bedrock player ban via API for " + playerName, e);
                errorMessage = "An error occurred while contacting the API.";
            }

            final UUID finalUuid = uuid;
            final String finalErrorMessage = errorMessage;
            final String finalResponseBody = responseBodyForDebug;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalUuid != null) {
                    boolean bannedByPluginDB = databaseManager.isPlayerBannedByPlugin(finalUuid);
                    if (bannedByPluginDB) {
                        sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                           .append(Component.text(" is banned.", NamedTextColor.RED)));
                   } else {
                        sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                           .append(Component.text(" is not banned.", NamedTextColor.GREEN)));
                   }
                } else {
                    String logMsg = "Could not determine ban status for '" + playerName + "'.";
                    if (finalErrorMessage != null) {
                        logMsg += " Reason: " + finalErrorMessage;
                    } else {
                        logMsg += " Reason: Unknown (check API response).";
                    }
                    plugin.getLogger().log(Level.WARNING, logMsg + " API Response Body: " + finalResponseBody);
                    sender.sendMessage(Component.text("Could not determine ban status for '" + playerName + ".", NamedTextColor.RED));
                }
            });
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /isbanned <player>", NamedTextColor.RED));
            return true;
        }

        String playerName = args[0];

        if (playerName.startsWith(bedrockPrefix)) {
            checkBedrockPlayerBanAsync(sender, playerName);
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found.", NamedTextColor.RED));
            return true;
        }

        if (targetPlayer.isOnline()) {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
                .append(Component.text(" is not banned.", NamedTextColor.GREEN)));
            return true;
        }

        boolean bannedByPluginDB = databaseManager.isPlayerBannedByPlugin(targetPlayer.getUniqueId());

        if (bannedByPluginDB) {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
               .append(Component.text(" is banned.", NamedTextColor.RED)));
       } else {
            sender.sendMessage(Component.text(playerName, NamedTextColor.YELLOW)
               .append(Component.text(" is not banned.", NamedTextColor.GREEN)));
       }

        return true;
    }
}
