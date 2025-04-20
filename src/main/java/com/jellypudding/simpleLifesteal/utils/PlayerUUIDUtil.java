package com.jellypudding.simpleLifesteal.utils;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerUUIDUtil {

    private static final String bedrockPrefix = ".";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Pattern uuidPattern = Pattern.compile("\"id\"\s*:\s*\"([0-9a-fA-F-]+)\"");

    public static void fetchBedrockUUIDAsync(SimpleLifesteal plugin, String playerName, Consumer<UUID> callback) {
        if (playerName == null || callback == null || plugin == null) {
            return;
        }

        if (!playerName.startsWith(bedrockPrefix)) {
             plugin.getLogger().warning("fetchBedrockUUIDAsync called with non-prefixed name: " + playerName);
             Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
             return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = null;
            String errorMessage = null;
            int responseCode = -1;
            String responseBodyForDebug = "<API Call Not Made>";

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
                responseBodyForDebug = response.body();

                if (responseCode == 200) {
                    Matcher matcher = uuidPattern.matcher(responseBodyForDebug);
                    if (matcher.find()) {
                        String uuidString = matcher.group(1);
                        if (uuidString != null && uuidString.length() == 32) {
                            String uuidWithHyphens = uuidString.substring(0, 8) + "-" +
                                    uuidString.substring(8, 12) + "-" +
                                    uuidString.substring(12, 16) + "-" +
                                    uuidString.substring(16, 20) + "-" +
                                    uuidString.substring(20);
                            try {
                                uuid = UUID.fromString(uuidWithHyphens);
                            } catch (IllegalArgumentException e) {
                                errorMessage = "API returned invalid UUID format.";
                            }
                        } else {
                            errorMessage = "API returned unexpected UUID string format/length.";
                        }
                    } else {
                        errorMessage = "Could not parse UUID from API response.";
                    }
                } else if (responseCode == 204 || responseCode == 302) {
                    errorMessage = "Player not found or is Java account.";
                } else {
                    errorMessage = "Geyser API request failed with status code: " + responseCode;
                }
            } catch (Exception e) {
                errorMessage = "An error occurred while contacting the API.";
                plugin.getLogger().log(Level.WARNING, "Error fetching UUID via Geyser API for " + playerName + ": " + e.getMessage());
            }

            if (uuid == null && errorMessage != null) {
                plugin.getLogger().warning("Failed to fetch UUID for '" + playerName + "'. Reason: " + errorMessage + ". API Response: " + responseBodyForDebug);
            }

            final UUID finalUuid = uuid;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalUuid));
        });
    }
}
