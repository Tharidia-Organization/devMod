package com.devmod.actions.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages generation snapshots for deterministic comparison between code generation runs.
 *
 * <p>Snapshot format is sorted JSON-like text for deterministic comparison.
 * Each snapshot records the list of action IDs and a hash of the generated source,
 * enabling detection of added/removed/modified actions between runs.
 */
public final class SnapshotManager {

    private SnapshotManager() {}

    /**
     * Diff between two snapshots.
     *
     * @param added     action IDs present in current but not in baseline
     * @param removed   action IDs present in baseline but not in current
     * @param sourceChanged true if the generated source text differs
     */
    public record SnapshotDiff(
        List<String> added,
        List<String> removed,
        boolean sourceChanged
    ) {
        public SnapshotDiff {
            Objects.requireNonNull(added, "added");
            Objects.requireNonNull(removed, "removed");
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }

        /**
         * Returns true if the snapshots are identical.
         */
        public boolean isIdentical() {
            return added.isEmpty() && removed.isEmpty() && !sourceChanged;
        }

        /**
         * Returns a human-readable summary of the diff.
         */
        public String summary() {
            if (isIdentical()) {
                return "No changes detected.";
            }
            StringBuilder sb = new StringBuilder();
            if (!added.isEmpty()) {
                sb.append("Added (").append(added.size()).append("): ")
                    .append(added).append('\n');
            }
            if (!removed.isEmpty()) {
                sb.append("Removed (").append(removed.size()).append("): ")
                    .append(removed).append('\n');
            }
            if (sourceChanged) {
                sb.append("Generated source has changed.\n");
            }
            return sb.toString().trim();
        }
    }

    /**
     * Internal representation of a stored snapshot.
     */
    record Snapshot(
        Instant timestamp,
        List<String> actionIds,
        int sourceHash
    ) {}

    /**
     * Takes a snapshot of the current generation output and writes it to disk.
     *
     * @param output   the generation output to snapshot
     * @param filePath the path to write the snapshot file
     * @throws IOException if writing fails
     */
    public static void takeSnapshot(RegistrationOutput output, Path filePath) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(filePath, "filePath");

        String content = serializeSnapshot(output);
        Files.writeString(filePath, content);
    }

    /**
     * Compares the current generation output against a stored snapshot.
     *
     * @param current      the current generation output
     * @param snapshotPath the path to the stored snapshot file
     * @return diff between current and stored snapshot
     * @throws IOException if reading the snapshot fails
     */
    public static SnapshotDiff compareWithSnapshot(
            RegistrationOutput current, Path snapshotPath) throws IOException {

        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(snapshotPath, "snapshotPath");

        if (!Files.exists(snapshotPath)) {
            // No previous snapshot - everything is "added"
            return new SnapshotDiff(
                List.copyOf(current.actionIds()),
                List.of(),
                true
            );
        }

        String storedContent = Files.readString(snapshotPath);
        Snapshot stored = deserializeSnapshot(storedContent);

        return compareSnapshots(current, stored);
    }

    /**
     * Compares two generation outputs without involving disk files.
     * Useful for in-memory testing.
     *
     * @param current  the current generation output
     * @param previous the previous generation output
     * @return diff between current and previous
     */
    public static SnapshotDiff compare(RegistrationOutput current, RegistrationOutput previous) {
        Snapshot prevSnapshot = new Snapshot(
            previous.timestamp(),
            previous.actionIds(),
            previous.generatedSource().hashCode()
        );
        return compareSnapshots(current, prevSnapshot);
    }

    // =========================================================================
    // Serialization
    // =========================================================================

    private static String serializeSnapshot(RegistrationOutput output) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ActionRegistrationGenerator Snapshot\n");
        sb.append("# timestamp: ").append(output.timestamp()).append('\n');
        sb.append("# sourceHash: ").append(output.generatedSource().hashCode()).append('\n');
        sb.append("# actionCount: ").append(output.actionCount()).append('\n');
        sb.append("#\n");

        // Write action IDs sorted (they already are, but enforce)
        List<String> sorted = new ArrayList<>(output.actionIds());
        sorted.sort(String::compareTo);
        for (String id : sorted) {
            sb.append(id).append('\n');
        }

        return sb.toString();
    }

    /** Pattern to extract sourceHash from snapshot header */
    private static final Pattern SOURCE_HASH_PATTERN = Pattern.compile(
        "^# sourceHash:\\s*(-?\\d+)$", Pattern.MULTILINE);

    /** Pattern to extract timestamp from snapshot header */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "^# timestamp:\\s*(.+)$", Pattern.MULTILINE);

    private static Snapshot deserializeSnapshot(String content) {
        Instant timestamp = Instant.EPOCH;
        Matcher tsMatcher = TIMESTAMP_PATTERN.matcher(content);
        if (tsMatcher.find()) {
            try {
                timestamp = Instant.parse(tsMatcher.group(1).trim());
            } catch (Exception ignored) {
                // Use epoch as fallback
            }
        }

        int sourceHash = 0;
        Matcher hashMatcher = SOURCE_HASH_PATTERN.matcher(content);
        if (hashMatcher.find()) {
            sourceHash = Integer.parseInt(hashMatcher.group(1));
        }

        List<String> actionIds = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                actionIds.add(trimmed);
            }
        }

        return new Snapshot(timestamp, actionIds, sourceHash);
    }

    private static SnapshotDiff compareSnapshots(
            RegistrationOutput current, Snapshot stored) {

        Set<String> currentSet = new LinkedHashSet<>(current.actionIds());
        Set<String> storedSet = new LinkedHashSet<>(stored.actionIds());

        List<String> added = new ArrayList<>();
        for (String id : currentSet) {
            if (!storedSet.contains(id)) {
                added.add(id);
            }
        }
        added.sort(String::compareTo);

        List<String> removed = new ArrayList<>();
        for (String id : storedSet) {
            if (!currentSet.contains(id)) {
                removed.add(id);
            }
        }
        removed.sort(String::compareTo);

        boolean sourceChanged = current.generatedSource().hashCode() != stored.sourceHash();

        return new SnapshotDiff(added, removed, sourceChanged);
    }
}
