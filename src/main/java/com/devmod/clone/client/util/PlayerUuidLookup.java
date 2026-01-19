package com.devmod.clone.client.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.devmod.DevMod;

/**
 * Utility for looking up player UUIDs from their username.
 * Uses Mojang's API with caching to minimize requests.
 */
public final class PlayerUuidLookup {

    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Cache of username -> UUID lookups */
    private static final Map<String, UUID> CACHE = new ConcurrentHashMap<>();

    /** HTTP client for API requests */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    private PlayerUuidLookup() {}

    /**
     * Look up a player's UUID from their username.
     * Uses Mojang's API with caching.
     *
     * @param username The player's username
     * @return CompletableFuture with the UUID, or null if not found
     */
    @Nonnull
    public static CompletableFuture<UUID> lookup(@Nonnull String username) {
        String normalizedName = Objects.requireNonNull(username).toLowerCase(Locale.ROOT);

        // Check cache first
        UUID cached = CACHE.get(normalizedName);
        if (cached != null) {
            return Objects.requireNonNull(CompletableFuture.completedFuture(cached));
        }

        // Make async API request
        return Objects.requireNonNull(CompletableFuture.supplyAsync(() -> {
            try {
                return lookupSync(username);
            } catch (Exception e) {
                DevMod.LOGGER.warn("[Clone] Failed to lookup player UUID for {}: {}", username, e.getMessage());
                return null;
            }
        }));
    }

    /**
     * Synchronous lookup (called from async context).
     */
    @Nullable
    private static UUID lookupSync(@Nonnull String username) throws Exception {
        String url = MOJANG_API_URL + username;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.statusCode() == 404) {
            // Player not found
            return null;
        }

        if (response.statusCode() != 200) {
            DevMod.LOGGER.warn("[Clone] Mojang API returned status {}", response.statusCode());
            return null;
        }

        String body = response.body();
        if (body == null || body.isEmpty()) {
            return null;
        }

        // Parse JSON response
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String idStr = json.get("id").getAsString();

        // Convert to UUID format (Mojang returns without dashes)
        UUID uuid = parseUuid(Objects.requireNonNull(idStr));

        // Cache the result
        if (uuid != null) {
            CACHE.put(username.toLowerCase(Locale.ROOT), uuid);
        }

        return uuid;
    }

    /**
     * Parse a UUID from Mojang's format (no dashes).
     */
    @Nullable
    private static UUID parseUuid(@Nonnull String idStr) {
        if (idStr.length() != 32) {
            return null;
        }

        try {
            // Insert dashes: 8-4-4-4-12
            String formatted = idStr.substring(0, 8) + "-"
                + idStr.substring(8, 12) + "-"
                + idStr.substring(12, 16) + "-"
                + idStr.substring(16, 20) + "-"
                + idStr.substring(20, 32);
            return UUID.fromString(formatted);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Clear the lookup cache.
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
