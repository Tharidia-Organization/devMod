package com.devmod.telemetry.spatial;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
public class LandmarkService {
    public static final LandmarkService INSTANCE = new LandmarkService();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Landmarks defined in config
    private final Map<String, LandmarkDefinition> landmarks = new ConcurrentHashMap<>();

    // Player observations: playerId -> (landmarkId -> first observation time)
    private final Map<UUID, Map<String, Long>> playerObservations = new ConcurrentHashMap<>();

    // Observation counts per landmark (for analytics)
    private final Map<String, Integer> observationCounts = new ConcurrentHashMap<>();

    private LandmarkService() {}

    /**
     * Definition of a landmark point of interest.
     */
    public record LandmarkDefinition(
            String id,
            BlockPos position,
            int observeRadius,
            double viewAngle,
            String description,
            String category
    ) {
        public LandmarkDefinition(String id, BlockPos position, int observeRadius, String description) {
            this(id, position, observeRadius, 0.8, description, "default");
        }
    }

    /**
     * Result of a landmark observation check.
     */
    public record ObservationResult(
            boolean observed,
            String landmarkId,
            String description,
            boolean firstTime,
            double distance,
            double dotProduct
    ) {
        public static final ObservationResult NONE = new ObservationResult(false, "", "", false, 0, 0);
    }

    /**
     * Check if player is observing any landmarks.
     *
     * @param player The server player to check
     * @return ObservationResult if player is looking at a landmark
     */
    public ObservationResult checkLandmarkObservation(ServerPlayer player) {
        if (landmarks.isEmpty()) {
            return ObservationResult.NONE;
        }

        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        UUID playerId = player.getUUID();

        for (LandmarkDefinition landmark : landmarks.values()) {
            Vec3 landmarkPos = Vec3.atCenterOf(Objects.requireNonNull(landmark.position()));
            Vec3 toLandmark = landmarkPos.subtract(Objects.requireNonNull(eyePos));
            double distance = toLandmark.length();

            // Check if within observation radius
            if (distance > landmark.observeRadius()) {
                continue;
            }

            // Check if player is looking at landmark
            double dot = Objects.requireNonNull(lookVec.normalize()).dot(Objects.requireNonNull(toLandmark.normalize()));
            if (dot > landmark.viewAngle()) {
                // Player is looking at this landmark
                boolean firstTime = recordObservation(playerId, landmark.id());

                return new ObservationResult(
                        true,
                        landmark.id(),
                        landmark.description(),
                        firstTime,
                        distance,
                        dot
                );
            }
        }

        return ObservationResult.NONE;
    }

    /**
     * Record that a player observed a landmark.
     *
     * @param playerId Player UUID
     * @param landmarkId Landmark ID
     * @return true if this is the first observation
     */
    private boolean recordObservation(UUID playerId, String landmarkId) {
        Map<String, Long> playerObs = playerObservations.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        boolean firstTime = !playerObs.containsKey(landmarkId);
        if (firstTime) {
            playerObs.put(landmarkId, System.currentTimeMillis());
            observationCounts.merge(landmarkId, 1, (a, b) -> a + b);
        }

        return firstTime;
    }

    /**
     * Get landmarks observed by a player.
     *
     * @param playerId Player UUID
     * @return Set of observed landmark IDs
     */
    public Set<String> getObservedLandmarks(UUID playerId) {
        Map<String, Long> playerObs = playerObservations.get(playerId);
        if (playerObs == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(playerObs.keySet());
    }

    /**
     * Get observation percentage for a player.
     *
     * @param playerId Player UUID
     * @return Percentage of landmarks observed (0.0 - 1.0)
     */
    public float getObservationPercentage(UUID playerId) {
        if (landmarks.isEmpty()) {
            return 0;
        }
        Set<String> observed = getObservedLandmarks(playerId);
        return (float) observed.size() / landmarks.size();
    }

    /**
     * Get total observation count for a landmark.
     *
     * @param landmarkId Landmark ID
     * @return Number of unique players who observed this landmark
     */
    public int getObservationCount(String landmarkId) {
        return observationCounts.getOrDefault(landmarkId, 0);
    }

    /**
     * Get all landmarks.
     *
     * @return Collection of all landmark definitions
     */
    public Collection<LandmarkDefinition> getAllLandmarks() {
        return Collections.unmodifiableCollection(landmarks.values());
    }

    /**
     * Add a landmark definition.
     *
     * @param landmark The landmark to add
     */
    public void addLandmark(LandmarkDefinition landmark) {
        landmarks.put(landmark.id(), landmark);
        LOGGER.debug("[LandmarkService] Added landmark: {}", landmark.id());
    }

    /**
     * Remove a landmark definition.
     *
     * @param landmarkId The landmark ID to remove
     */
    public void removeLandmark(String landmarkId) {
        landmarks.remove(landmarkId);
    }

    /**
     * Load landmarks from a JSON file.
     *
     * @param path Path to the landmarks JSON file
     */
    public void loadFromFile(Path path) {
        if (!Files.exists(path)) {
            LOGGER.info("[LandmarkService] No landmarks file found at {}", path);
            return;
        }

        try {
            String json = Files.readString(path);
            Type listType = new TypeToken<List<LandmarkDefinition>>() {}.getType();
            List<LandmarkDefinition> loaded = GSON.fromJson(json, listType);

            if (loaded != null) {
                landmarks.clear();
                for (LandmarkDefinition def : loaded) {
                    landmarks.put(def.id(), def);
                }
                LOGGER.info("[LandmarkService] Loaded {} landmarks from {}", landmarks.size(), path);
            }
        } catch (IOException e) {
            LOGGER.error("[LandmarkService] Failed to load landmarks: {}", e.getMessage());
        }
    }

    /**
     * Save landmarks to a JSON file.
     *
     * @param path Path to save the landmarks JSON file
     */
    public void saveToFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(new ArrayList<>(landmarks.values()));
            Files.writeString(path, json);
            LOGGER.info("[LandmarkService] Saved {} landmarks to {}", landmarks.size(), path);
        } catch (IOException e) {
            LOGGER.error("[LandmarkService] Failed to save landmarks: {}", e.getMessage());
        }
    }

    /**
     * Cleanup observations for a player.
     *
     * @param playerId Player UUID
     */
    public void cleanupPlayer(UUID playerId) {
        playerObservations.remove(playerId);
    }

    /**
     * Clear all data.
     */
    public void clear() {
        landmarks.clear();
        playerObservations.clear();
        observationCounts.clear();
    }

    /**
     * Get statistics for all landmarks.
     *
     * @return Map of landmark ID to observation count
     */
    public Map<String, Integer> getStatistics() {
        return new HashMap<>(observationCounts);
    }
}
