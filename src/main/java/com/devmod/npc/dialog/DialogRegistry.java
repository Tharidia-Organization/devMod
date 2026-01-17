package com.devmod.npc.dialog;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.devmod.DevMod;
import com.devmod.npc.io.DialogSetIO;

/**
 * Registry for custom dialog sets loaded from disk and saved at runtime.
 * Presets are provided by {@link DialogPresets} and are never overwritten.
 */
public final class DialogRegistry {
    private static final Map<String, DialogSet> CUSTOM = new ConcurrentHashMap<>();
    private static final Object LOAD_LOCK = new Object();
    private static volatile boolean loaded = false;

    private DialogRegistry() {}

    /**
     * Gets a dialog set by ID, preferring custom sets over presets.
     */
    @Nonnull
    public static Optional<DialogSet> getDialogSet(@Nonnull String id) {
        Objects.requireNonNull(id, "id");
        ensureLoaded();
        DialogSet custom = CUSTOM.get(id);
        if (custom != null) {
            return Optional.of(custom);
        }
        return DialogPresets.getPreset(id);
    }

    /**
     * Returns true if a dialog set exists (custom or preset).
     */
    public static boolean hasDialog(@Nonnull String id) {
        Objects.requireNonNull(id, "id");
        ensureLoaded();
        return CUSTOM.containsKey(id) || DialogPresets.hasPreset(id);
    }

    /**
     * Returns all dialog sets (presets + custom).
     */
    @Nonnull
    public static Map<String, DialogSet> getAllDialogSets() {
        ensureLoaded();
        Map<String, DialogSet> result = new LinkedHashMap<>(DialogPresets.getAllPresets());
        result.putAll(CUSTOM);
        return Map.copyOf(result);
    }

    /**
     * Returns all dialog IDs (presets + custom).
     */
    @Nonnull
    public static Set<String> getAllDialogIds() {
        ensureLoaded();
        Set<String> ids = new LinkedHashSet<>(DialogPresets.getAllPresetIds());
        ids.addAll(CUSTOM.keySet());
        return Set.copyOf(ids);
    }

    /**
     * Saves a custom dialog set to disk and registers it in memory.
     */
    @Nonnull
    public static SaveResult saveDialogSet(
        @Nonnull DialogSet incoming,
        int expectedRevision,
        @Nullable UUID playerUuid,
        boolean canOverrideOwner
    ) {
        Objects.requireNonNull(incoming, "incoming");
        ensureLoaded();

        if (incoming.id().isEmpty()) {
            return SaveResult.failure("Dialog ID is empty", List.of("Provide a valid dialog set ID"));
        }

        if (incoming.isPreset()) {
            return SaveResult.failure("Preset dialog sets cannot be modified", List.of());
        }

        if (DialogPresets.hasPreset(incoming.id())) {
            return SaveResult.failure("Dialog ID conflicts with a preset", List.of(incoming.id()));
        }

        List<String> validationErrors = incoming.validate();
        if (!validationErrors.isEmpty()) {
            return SaveResult.failure("Dialog validation failed", validationErrors);
        }

        DialogSet existing = CUSTOM.get(incoming.id());
        if (existing != null) {
            if (existing.revision() != expectedRevision) {
                return SaveResult.failure("Dialog revision mismatch", List.of(
                    "Expected " + expectedRevision + ", found " + existing.revision()
                ));
            }
            if (existing.ownerUUID() != null && playerUuid != null
                && !existing.ownerUUID().equals(playerUuid) && !canOverrideOwner) {
                return SaveResult.failure("You do not own this dialog set", List.of(existing.id()));
            }
        }

        int nextRevision = existing != null ? existing.revision() + 1 : 1;
        UUID owner = existing != null ? existing.ownerUUID() : playerUuid;

        DialogSet normalized = new DialogSet(
            incoming.id(),
            incoming.name(),
            incoming.nodes(),
            incoming.entryNodeId(),
            System.currentTimeMillis(),
            nextRevision,
            false,
            owner,
            incoming.schedule()
        );

        try {
            Path path = DialogSetIO.getDefaultExportPath(incoming.id());
            DialogSetIO.exportToFile(normalized, path);
        } catch (Exception e) {
            DevMod.LOGGER.error("Failed to save dialog set {}: {}", incoming.id(), e.getMessage());
            return SaveResult.failure("Failed to save dialog to disk", List.of(String.valueOf(e.getMessage())));
        }

        CUSTOM.put(normalized.id(), normalized);
        return SaveResult.success("Dialog saved: " + normalized.id());
    }

    /**
     * Registers a custom dialog set already loaded from disk (import/reload).
     */
    @Nonnull
    public static SaveResult registerCustomDialog(@Nonnull DialogSet incoming) {
        Objects.requireNonNull(incoming, "incoming");
        ensureLoaded();
        return registerCustomInternal(incoming);
    }

    /**
     * Reloads all dialog files from disk into the custom registry.
     *
     * @return number of dialogs loaded
     */
    public static int reloadFromDisk() {
        synchronized (LOAD_LOCK) {
            CUSTOM.clear();
            int loadedCount = 0;

            Path[] files = DialogSetIO.listExportedDialogs();
            for (Path file : files) {
                Optional<DialogSet> dialogSet = DialogSetIO.importFromFile(file);
                if (dialogSet.isEmpty()) {
                    continue;
                }
                SaveResult result = registerCustomInternal(dialogSet.get());
                if (result.success()) {
                    loadedCount++;
                } else {
                    DevMod.LOGGER.warn("Skipping dialog {} from {}: {}",
                        dialogSet.get().id(), file, result.message());
                }
            }

            loaded = true;
            DevMod.LOGGER.info("Loaded {} custom dialog set(s)", loadedCount);
            return loadedCount;
        }
    }

    private static SaveResult registerCustomInternal(@Nonnull DialogSet incoming) {
        if (incoming.id().isEmpty()) {
            return SaveResult.failure("Dialog ID is empty", List.of());
        }
        if (DialogPresets.hasPreset(incoming.id())) {
            return SaveResult.failure("Dialog ID conflicts with a preset", List.of(incoming.id()));
        }

        List<String> validationErrors = incoming.validate();
        if (!validationErrors.isEmpty()) {
            return SaveResult.failure("Dialog validation failed", validationErrors);
        }

        DialogSet normalized = new DialogSet(
            incoming.id(),
            incoming.name(),
            incoming.nodes(),
            incoming.entryNodeId(),
            incoming.updatedAt(),
            incoming.revision(),
            false,
            incoming.ownerUUID(),
            incoming.schedule()
        );

        CUSTOM.put(normalized.id(), normalized);
        return SaveResult.success("Dialog registered: " + normalized.id());
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (!loaded) {
                reloadFromDisk();
            }
        }
    }

    public record SaveResult(boolean success, @Nonnull String message, @Nonnull List<String> errors) {
        public static SaveResult success(@Nonnull String message) {
            return new SaveResult(true, message, List.of());
        }

        public static SaveResult failure(@Nonnull String message, @Nonnull List<String> errors) {
            return new SaveResult(false, message, List.copyOf(errors));
        }
    }
}
