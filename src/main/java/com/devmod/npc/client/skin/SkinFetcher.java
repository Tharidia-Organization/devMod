package com.devmod.npc.client.skin;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;

/**
 * Fetches and caches player skins from the Mojang API.
 * Allows displaying skins for offline players.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkinFetcher.class);

    /** Mojang API endpoints. */
    private static final String MOJANG_UUID_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_PROFILE_API = "https://sessionserver.mojang.com/session/minecraft/profile/";

    /** Default Steve skin. */
    private static final ResourceLocation STEVE_SKIN =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    /** Cache of fetched skins: playerName -> ResourceLocation. */
    private static final Map<String, ResourceLocation> skinCache = new ConcurrentHashMap<>();

    /** Cache of pending fetches to avoid duplicate requests. */
    private static final Map<String, CompletableFuture<ResourceLocation>> pendingFetches = new ConcurrentHashMap<>();

    /** Cache of skin models: playerName -> isSlim. */
    private static final Map<String, Boolean> modelCache = new ConcurrentHashMap<>();

    /** HTTP client for API requests. */
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /** Executor for async operations. */
    private static final Executor executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SkinFetcher");
        t.setDaemon(true);
        return t;
    });

    private SkinFetcher() {}

    /**
     * Gets the skin for a player, fetching from Mojang API if necessary.
     * Returns Steve skin immediately while fetching in background.
     *
     * @param playerName The player's username
     * @return The skin ResourceLocation (may be Steve while loading)
     */
    @Nonnull
    public static ResourceLocation getSkin(@Nullable String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return STEVE_SKIN;
        }

        String nameLower = playerName.toLowerCase();

        // Check cache first
        ResourceLocation cached = skinCache.get(nameLower);
        if (cached != null) {
            return cached;
        }

        // Start async fetch if not already pending (atomic check-and-set)
        pendingFetches.computeIfAbsent(nameLower, key -> {
            CompletableFuture<ResourceLocation> future = fetchSkinAsync(playerName);

            future.thenAccept(skin -> {
                skinCache.put(key, skin);
                pendingFetches.remove(key);
                LOGGER.debug("Cached skin for player: {}", playerName);
            }).exceptionally(e -> {
                LOGGER.debug("Failed to fetch skin for {}: {}", playerName, e.getMessage());
                skinCache.put(key, STEVE_SKIN);
                pendingFetches.remove(key);
                return null;
            });

            return future;
        });

        // Return Steve while loading
        return STEVE_SKIN;
    }

    /**
     * Checks if the player has a slim (Alex) model.
     *
     * @param playerName The player's username
     * @return true if slim model, false for wide (Steve) model
     */
    public static boolean isSlimModel(@Nullable String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        return modelCache.getOrDefault(playerName.toLowerCase(), false);
    }

    /**
     * Clears all cached skins.
     */
    public static void clearCache() {
        skinCache.clear();
        modelCache.clear();
        pendingFetches.clear();
        LOGGER.info("Skin cache cleared");
    }

    /**
     * Fetches a player's skin asynchronously from Mojang API.
     */
    private static CompletableFuture<ResourceLocation> fetchSkinAsync(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Get UUID from username
                UUID uuid = fetchUUID(playerName);
                if (uuid == null) {
                    LOGGER.debug("Player not found: {}", playerName);
                    return STEVE_SKIN;
                }

                // Step 2: Get profile with skin URL
                String skinUrl = fetchSkinUrl(uuid, playerName);
                if (skinUrl == null) {
                    LOGGER.debug("No skin URL for player: {}", playerName);
                    return STEVE_SKIN;
                }

                // Step 3: Download and register skin texture
                return downloadAndRegisterSkin(playerName, skinUrl);

            } catch (Exception e) {
                LOGGER.warn("Error fetching skin for {}: {}", playerName, e.getMessage());
                return STEVE_SKIN;
            }
        }, executor);
    }

    /**
     * Fetches a player's UUID from their username.
     */
    @Nullable
    private static UUID fetchUUID(String playerName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MOJANG_UUID_API + playerName))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return null; // Player not found
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned status " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = json.get("id").getAsString();

        // Convert to UUID format (add dashes)
        return UUID.fromString(
            id.substring(0, 8) + "-" +
            id.substring(8, 12) + "-" +
            id.substring(12, 16) + "-" +
            id.substring(16, 20) + "-" +
            id.substring(20)
        );
    }

    /**
     * Fetches the skin URL from a player's profile.
     */
    @Nullable
    private static String fetchSkinUrl(UUID uuid, String playerName) throws Exception {
        String uuidString = uuid.toString().replace("-", "");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MOJANG_PROFILE_API + uuidString))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        var properties = json.getAsJsonArray("properties");

        for (var prop : properties) {
            JsonObject propObj = prop.getAsJsonObject();
            if ("textures".equals(propObj.get("name").getAsString())) {
                String value = propObj.get("value").getAsString();
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();

                JsonObject texturesObj = textures.getAsJsonObject("textures");
                if (texturesObj != null && texturesObj.has("SKIN")) {
                    JsonObject skinObj = texturesObj.getAsJsonObject("SKIN");
                    String url = skinObj.get("url").getAsString();

                    // Check for slim model
                    if (skinObj.has("metadata")) {
                        JsonObject metadata = skinObj.getAsJsonObject("metadata");
                        if (metadata.has("model") && "slim".equals(metadata.get("model").getAsString())) {
                            modelCache.put(playerName.toLowerCase(), true);
                        }
                    }

                    return url;
                }
            }
        }

        return null;
    }

    /**
     * Downloads a skin texture and registers it with Minecraft.
     */
    @Nonnull
    private static ResourceLocation downloadAndRegisterSkin(String playerName, String skinUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(skinUrl))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download skin: " + response.statusCode());
        }

        // Load the image
        NativeImage image = NativeImage.read(response.body());

        // Register texture on main thread
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID,
            "skins/" + playerName.toLowerCase()
        );

        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture texture = new DynamicTexture(image);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                LOGGER.debug("Registered skin texture for: {}", playerName);
            } catch (Exception e) {
                image.close();
                LOGGER.error("Failed to register skin texture for {}: {}", playerName, e.getMessage());
            }
        });

        return location;
    }
}
