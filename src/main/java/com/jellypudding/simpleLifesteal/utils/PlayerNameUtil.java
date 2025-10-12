package com.jellypudding.simpleLifesteal.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.UUID;

public class PlayerNameUtil {

    public static Component getFormattedPlayerName(com.jellypudding.chromaTag.ChromaTag chromaTagAPI, UUID playerUuid, String fallbackName) {
        if (chromaTagAPI != null) {
            TextColor color = chromaTagAPI.getPlayerColor(playerUuid);
            if (color != null) {
                return Component.text(fallbackName, color);
            }
        }
        return Component.text(fallbackName);
    }
}
