package com.devmod.arena.snapshot;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages arena snapshots for recovery and drift detection.
 *
 * <p>Implements:
 * <ul>
 *   <li>Session state snapshots</li>
 *   <li>Version drift detection (template changed during session)</li>
 *   <li>Crash recovery from persisted snapshots</li>
 *   <li>Snapshot cleanup based on retention policy</li>
 * </ul>
 */
public class ArenaSnapshotManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaSnapshotManager.class);

    private static final String SNAPSHOT_EXTENSION = ".snapshot.json";
    private static final int DEFAULT_RETENTION_DAYS = 7;

    private final Path snapshotDirectory;
    private final ArenaTelemetry telemetry;
    private final int retentionDays;

    // In-memory snapshot cache for active sessions
    private final ConcurrentHashMap<UUID, ArenaSnapshot> activeSnapshots = new ConcurrentHashMap<>();

    // Stats
    private final AtomicInteger snapshotsTaken = new AtomicInteger(0);
    private final AtomicInteger snapshotsRecovered = new AtomicInteger(0);
    private final AtomicInteger driftDetections = new AtomicInteger(0);

    public ArenaSnapshotManager(Path snapshotDirectory, ArenaTelemetry telemetry) {
        this(snapshotDirectory, telemetry, DEFAULT_RETENTION_DAYS);
    }

    public ArenaSnapshotManager(Path snapshotDirectory, ArenaTelemetry telemetry, int retentionDays) {
        this.snapshotDirectory = Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.retentionDays = retentionDays;

        // Ensure directory exists
        try {
            Files.createDirectories(snapshotDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to create snapshot directory: {}", snapshotDirectory, e);
        }

        LOGGER.info("ArenaSnapshotManager initialized: dir={}, retention={}d",
            snapshotDirectory, retentionDays);
    }

    /**
     * Takes a snapshot of an arena session.
     *
     * @param handle      The arena handle
     * @param sessionId   The session ID
     * @param sessionData Additional session data
     * @return The created snapshot
     */
    public ArenaSnapshot takeSnapshot(ArenaHandle handle, UUID sessionId, Map<String, Object> sessionData) {
        Instant now = Instant.now();

        ArenaSnapshot snapshot = new ArenaSnapshot(
            UUID.randomUUID(),
            sessionId,
            handle.arenaId(),
            handle.templateId(),
            handle.templateVersion(),
            handle.policyId(),
            handle.policyVersion(),
            now,
            SnapshotState.ACTIVE,
            new HashMap<>(sessionData),
            null // No end time yet
        );

        // Store in memory
        activeSnapshots.put(sessionId, snapshot);

        // Persist to disk
        persistSnapshot(snapshot);

        snapshotsTaken.incrementAndGet();

        telemetry.emit("arena.snapshot.taken", Map.of(
            "snapshotId", snapshot.snapshotId().toString(),
            "sessionId", sessionId.toString(),
            "templateId", handle.templateId(),
            "templateVersion", handle.templateVersion()
        ));

        LOGGER.debug("Snapshot taken: sessionId={}, snapshotId={}", sessionId, snapshot.snapshotId());

        return snapshot;
    }

    /**
     * Updates a snapshot with new session data.
     *
     * @param sessionId   The session ID
     * @param sessionData Updated session data
     * @return Updated snapshot or null if not found
     */
    @Nullable
    public ArenaSnapshot updateSnapshot(UUID sessionId, Map<String, Object> sessionData) {
        ArenaSnapshot existing = activeSnapshots.get(sessionId);
        if (existing == null) {
            LOGGER.warn("Cannot update snapshot: session {} not found", sessionId);
            return null;
        }

        Map<String, Object> mergedData = new HashMap<>(existing.sessionData());
        mergedData.putAll(sessionData);

        ArenaSnapshot updated = new ArenaSnapshot(
            existing.snapshotId(),
            existing.sessionId(),
            existing.arenaId(),
            existing.templateId(),
            existing.templateVersion(),
            existing.policyId(),
            existing.policyVersion(),
            existing.createdAt(),
            existing.state(),
            mergedData,
            existing.endedAt()
        );

        activeSnapshots.put(sessionId, updated);
        persistSnapshot(updated);

        return updated;
    }

    /**
     * Marks a session as ended and finalizes its snapshot.
     *
     * @param sessionId The session ID
     * @param outcome   The session outcome
     * @return Finalized snapshot or null if not found
     */
    @Nullable
    public ArenaSnapshot finalizeSnapshot(UUID sessionId, String outcome) {
        ArenaSnapshot existing = activeSnapshots.remove(sessionId);
        if (existing == null) {
            LOGGER.warn("Cannot finalize snapshot: session {} not found", sessionId);
            return null;
        }

        Map<String, Object> finalData = new HashMap<>(existing.sessionData());
        finalData.put("outcome", outcome);

        ArenaSnapshot finalized = new ArenaSnapshot(
            existing.snapshotId(),
            existing.sessionId(),
            existing.arenaId(),
            existing.templateId(),
            existing.templateVersion(),
            existing.policyId(),
            existing.policyVersion(),
            existing.createdAt(),
            SnapshotState.COMPLETED,
            finalData,
            Instant.now()
        );

        persistSnapshot(finalized);

        telemetry.emit("arena.snapshot.finalized", Map.of(
            "snapshotId", finalized.snapshotId().toString(),
            "sessionId", sessionId.toString(),
            "outcome", outcome,
            "durationMs", Duration.between(finalized.createdAt(), finalized.endedAt()).toMillis()
        ));

        LOGGER.debug("Snapshot finalized: sessionId={}, outcome={}", sessionId, outcome);

        return finalized;
    }

    /**
     * Checks for version drift (template/policy changed during session).
     *
     * @param sessionId          The session ID
     * @param currentTemplateVer Current template version
     * @param currentPolicyVer   Current policy version
     * @return Drift result
     */
    public DriftResult checkVersionDrift(UUID sessionId, int currentTemplateVer, int currentPolicyVer) {
        ArenaSnapshot snapshot = activeSnapshots.get(sessionId);
        if (snapshot == null) {
            return DriftResult.noSnapshot(sessionId);
        }

        boolean templateDrift = snapshot.templateVersion() != currentTemplateVer;
        boolean policyDrift = snapshot.policyVersion() != currentPolicyVer;

        if (templateDrift || policyDrift) {
            driftDetections.incrementAndGet();

            telemetry.emit("arena.snapshot.version_drift", Map.of(
                "sessionId", sessionId.toString(),
                "templateId", snapshot.templateId(),
                "snapshotTemplateVersion", snapshot.templateVersion(),
                "currentTemplateVersion", currentTemplateVer,
                "snapshotPolicyVersion", snapshot.policyVersion(),
                "currentPolicyVersion", currentPolicyVer,
                "templateDrift", templateDrift,
                "policyDrift", policyDrift
            ));

            LOGGER.warn("Version drift detected for session {}: template {}→{}, policy {}→{}",
                sessionId,
                snapshot.templateVersion(), currentTemplateVer,
                snapshot.policyVersion(), currentPolicyVer);
        }

        return new DriftResult(
            sessionId,
            templateDrift,
            policyDrift,
            snapshot.templateVersion(),
            currentTemplateVer,
            snapshot.policyVersion(),
            currentPolicyVer
        );
    }

    /**
     * Recovers sessions from persisted snapshots (crash recovery).
     *
     * @return List of recovered snapshots
     */
    public List<ArenaSnapshot> recoverSnapshots() {
        List<ArenaSnapshot> recovered = new ArrayList<>();

        if (!Files.isDirectory(snapshotDirectory)) {
            return recovered;
        }

        try (var stream = Files.list(snapshotDirectory)) {
            stream.filter(p -> p.toString().endsWith(SNAPSHOT_EXTENSION))
                .forEach(path -> {
                    ArenaSnapshot snapshot = loadSnapshot(path);
                    if (snapshot != null && snapshot.state() == SnapshotState.ACTIVE) {
                        // This was an active session that didn't complete
                        activeSnapshots.put(snapshot.sessionId(), snapshot);
                        recovered.add(snapshot);
                        snapshotsRecovered.incrementAndGet();

                        LOGGER.info("Recovered snapshot: sessionId={}, templateId={}",
                            snapshot.sessionId(), snapshot.templateId());
                    }
                });
        } catch (IOException e) {
            LOGGER.error("Failed to recover snapshots", e);
        }

        if (!recovered.isEmpty()) {
            telemetry.emit("arena.snapshot.recovery", Map.of(
                "recoveredCount", recovered.size()
            ));
        }

        return recovered;
    }

    /**
     * Gets an active snapshot by session ID.
     */
    public Optional<ArenaSnapshot> getSnapshot(UUID sessionId) {
        return Optional.ofNullable(activeSnapshots.get(sessionId));
    }

    /**
     * Gets all active snapshots.
     */
    public Collection<ArenaSnapshot> getActiveSnapshots() {
        return Collections.unmodifiableCollection(activeSnapshots.values());
    }

    /**
     * Cleans up old snapshots based on retention policy.
     *
     * @return Number of snapshots cleaned up
     */
    public int cleanup() {
        return cleanup(retentionDays);
    }

    /**
     * Cleans up snapshots older than the specified days.
     *
     * @param days Maximum age in days
     * @return Number of snapshots cleaned up
     */
    public int cleanup(int days) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int deleted = 0;

        if (!Files.isDirectory(snapshotDirectory)) {
            return 0;
        }

        try (var stream = Files.list(snapshotDirectory)) {
            List<Path> files = stream
                .filter(p -> p.toString().endsWith(SNAPSHOT_EXTENSION))
                .toList();

            for (Path file : files) {
                ArenaSnapshot snapshot = loadSnapshot(file);
                if (snapshot != null) {
                    Instant endedAt = snapshot.endedAt();
                    Instant snapshotTime = endedAt != null ? endedAt : snapshot.createdAt();

                    if (snapshotTime != null && snapshotTime.isBefore(cutoff)) {
                        Files.delete(file);
                        deleted++;
                        LOGGER.debug("Deleted old snapshot: {}", file);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to cleanup snapshots", e);
        }

        if (deleted > 0) {
            telemetry.emit("arena.snapshot.cleanup", Map.of(
                "deletedCount", deleted,
                "retentionDays", days
            ));
            LOGGER.info("Cleaned up {} old snapshots", deleted);
        }

        return deleted;
    }

    /**
     * Gets manager statistics.
     */
    public SnapshotStats getStats() {
        return new SnapshotStats(
            activeSnapshots.size(),
            snapshotsTaken.get(),
            snapshotsRecovered.get(),
            driftDetections.get()
        );
    }

    // ========== Persistence ==========

    private void persistSnapshot(ArenaSnapshot snapshot) {
        Path file = snapshotDirectory.resolve(snapshot.sessionId() + SNAPSHOT_EXTENSION);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(serializeSnapshot(snapshot));
        } catch (IOException e) {
            LOGGER.error("Failed to persist snapshot: {}", snapshot.snapshotId(), e);
        }
    }

    @Nullable
    private ArenaSnapshot loadSnapshot(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return deserializeSnapshot(json);
        } catch (Exception e) {
            LOGGER.debug("Failed to load snapshot: {}", file, e);
            return null;
        }
    }

    private String serializeSnapshot(ArenaSnapshot snapshot) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");
        sb.append("\"snapshotId\":\"").append(snapshot.snapshotId()).append("\"");
        sb.append(",\"sessionId\":\"").append(snapshot.sessionId()).append("\"");
        sb.append(",\"arenaId\":\"").append(snapshot.arenaId()).append("\"");
        sb.append(",\"templateId\":\"").append(escape(snapshot.templateId())).append("\"");
        sb.append(",\"templateVersion\":").append(snapshot.templateVersion());
        sb.append(",\"policyId\":\"").append(escape(snapshot.policyId())).append("\"");
        sb.append(",\"policyVersion\":").append(snapshot.policyVersion());
        sb.append(",\"createdAt\":\"").append(snapshot.createdAt()).append("\"");
        sb.append(",\"state\":\"").append(snapshot.state().name()).append("\"");
        if (snapshot.endedAt() != null) {
            sb.append(",\"endedAt\":\"").append(snapshot.endedAt()).append("\"");
        }
        sb.append(",\"sessionData\":");
        serializeMap(sb, snapshot.sessionData());
        sb.append("}");
        return sb.toString();
    }

    private void serializeMap(StringBuilder sb, Map<String, Object> map) {
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s) {
                sb.append("\"").append(escape(s)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escape(value.toString())).append("\"");
            }
        }
        sb.append("}");
    }

    @Nullable
    private ArenaSnapshot deserializeSnapshot(String json) {
        try {
            UUID snapshotId = UUID.fromString(extractString(json, "snapshotId"));
            UUID sessionId = UUID.fromString(extractString(json, "sessionId"));
            UUID arenaId = UUID.fromString(extractString(json, "arenaId"));
            String templateId = extractString(json, "templateId");
            int templateVersion = extractInt(json, "templateVersion");
            String policyId = extractString(json, "policyId");
            int policyVersion = extractInt(json, "policyVersion");
            Instant createdAt = Instant.parse(extractString(json, "createdAt"));
            SnapshotState state = SnapshotState.valueOf(extractString(json, "state"));

            String endedAtStr = extractString(json, "endedAt");
            Instant endedAt = endedAtStr != null && !endedAtStr.isEmpty()
                ? Instant.parse(endedAtStr) : null;

            // Simplified: sessionData not fully parsed
            Map<String, Object> sessionData = new HashMap<>();

            return new ArenaSnapshot(
                snapshotId, sessionId, arenaId,
                templateId, templateVersion,
                policyId, policyVersion,
                createdAt, state, sessionData, endedAt
            );
        } catch (Exception e) {
            LOGGER.debug("Failed to deserialize snapshot: {}", e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private int extractInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return 0;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public void close() {
        LOGGER.info("ArenaSnapshotManager closed: {} active snapshots", activeSnapshots.size());
    }

    // ========== Records ==========

    /**
     * Snapshot state enum.
     */
    public enum SnapshotState {
        ACTIVE,
        COMPLETED,
        RECOVERED,
        FAILED
    }

    /**
     * Arena session snapshot.
     */
    public record ArenaSnapshot(
        UUID snapshotId,
        UUID sessionId,
        UUID arenaId,
        String templateId,
        int templateVersion,
        String policyId,
        int policyVersion,
        Instant createdAt,
        SnapshotState state,
        Map<String, Object> sessionData,
        @Nullable Instant endedAt
    ) {}

    /**
     * Version drift detection result.
     */
    public record DriftResult(
        UUID sessionId,
        boolean templateDrift,
        boolean policyDrift,
        int snapshotTemplateVersion,
        int currentTemplateVersion,
        int snapshotPolicyVersion,
        int currentPolicyVersion
    ) {
        public boolean hasDrift() {
            return templateDrift || policyDrift;
        }

        public static DriftResult noSnapshot(UUID sessionId) {
            return new DriftResult(sessionId, false, false, 0, 0, 0, 0);
        }
    }

    /**
     * Snapshot manager statistics.
     */
    public record SnapshotStats(
        int activeCount,
        int totalTaken,
        int totalRecovered,
        int driftDetections
    ) {}
}
