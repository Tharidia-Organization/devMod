package com.devmod.area.data;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent audit log for area operations.
 * Tracks who performed what actions on areas.
 */
public class AreaAuditLog extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaAuditLog.class);
    private static final String DATA_NAME = "devmod_area_audit_log";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_ENTRIES = "entries";

    /** Maximum audit entries to retain */
    public static final int MAX_ENTRIES = 1000;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    /**
     * Types of actions that can be audited.
     */
    public enum ActionType implements StringRepresentable {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        BUILD_START("build_start"),
        BUILD_COMPLETE("build_complete"),
        BUILD_CANCEL("build_cancel"),
        TEMPLATE_SAVE("template_save"),
        TEMPLATE_DELETE("template_delete"),
        SET_MAIN_HUB("set_main_hub"),
        CLONE("clone"),
        SNAPSHOT_CAPTURE("snapshot_capture"),
        SNAPSHOT_RESTORE("snapshot_restore"),
        SNAPSHOT_DELETE("snapshot_delete");

        public static final Codec<ActionType> CODEC = StringRepresentable.fromEnum(ActionType::values);

        private final String name;

        ActionType(String name) {
            this.name = name;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return Objects.requireNonNull(name);
        }

        @Nonnull
        public static ActionType byName(@Nonnull String name) {
            for (ActionType type : values()) {
                if (type.name.equals(name)) {
                    return type;
                }
            }
            return CREATE;
        }
    }

    /**
     * A single audit log entry.
     */
    public record AuditEntry(
        long timestamp,
        ActionType action,
        UUID targetId,
        String targetName,
        @Nullable UUID actorId,
        @Nullable String actorName,
        @Nullable String details
    ) {
        public static final Codec<AuditEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.LONG.fieldOf("timestamp").forGetter(AuditEntry::timestamp),
                ActionType.CODEC.fieldOf("action").forGetter(AuditEntry::action),
                UUIDUtil.CODEC.fieldOf("targetId").forGetter(AuditEntry::targetId),
                Codec.STRING.fieldOf("targetName").forGetter(AuditEntry::targetName),
                UUIDUtil.CODEC.optionalFieldOf("actorId")
                    .forGetter(e -> Optional.ofNullable(e.actorId())),
                Codec.STRING.optionalFieldOf("actorName", "")
                    .forGetter(e -> e.actorName() != null ? e.actorName() : ""),
                Codec.STRING.optionalFieldOf("details", "")
                    .forGetter(e -> e.details() != null ? e.details() : "")
            ).apply(instance, (ts, action, targetId, targetName, actorIdOpt, actorName, details) ->
                new AuditEntry(ts, action, targetId, targetName,
                    actorIdOpt.orElse(null),
                    actorName.isEmpty() ? null : actorName,
                    details.isEmpty() ? null : details))
        );

        /**
         * Creates a formatted description of this entry.
         */
        @Nonnull
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(action.getSerializedName()).append("] ");
            if (actorName != null) {
                sb.append(actorName).append(" ");
            }
            sb.append("on '").append(targetName).append("'");
            if (details != null && !details.isEmpty()) {
                sb.append(" (").append(details).append(")");
            }
            return Objects.requireNonNull(sb.toString());
        }
    }

    /**
     * Gets the AreaAuditLog instance for the server.
     */
    @Nonnull
    public static AreaAuditLog get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        // I-02 fix: Check overworld availability before accessing data storage
        var overworld = server.overworld();
        if (overworld == null) {
            throw new IllegalStateException("Cannot access AreaAuditLog: overworld not loaded");
        }
        return Objects.requireNonNull(
            overworld.getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
    }

    private static Factory<AreaAuditLog> factory() {
        return new Factory<>(
            AreaAuditLog::new,
            AreaAuditLog::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    // ========================================================================
    // Logging Methods
    // ========================================================================

    /**
     * Logs an action to the audit log.
     *
     * @param action     The type of action
     * @param targetId   The ID of the area/template being acted on
     * @param targetName The name of the area/template
     * @param actor      The player performing the action (may be null for server actions)
     * @param details    Optional additional details
     */
    public void log(
        @Nonnull ActionType action,
        @Nonnull UUID targetId,
        @Nonnull String targetName,
        @Nullable ServerPlayer actor,
        @Nullable String details
    ) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(targetName);

        AuditEntry entry = new AuditEntry(
            System.currentTimeMillis(),
            action,
            targetId,
            targetName,
            actor != null ? actor.getUUID() : null,
            actor != null ? actor.getName().getString() : null,
            details
        );

        entries.addFirst(entry);

        // Trim old entries
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }

        setDirty();
        LOGGER.debug("[AuditLog] {}", entry.getDescription());
    }

    /**
     * Logs an action without a player actor.
     */
    public void logServer(
        @Nonnull ActionType action,
        @Nonnull UUID targetId,
        @Nonnull String targetName,
        @Nullable String details
    ) {
        log(action, targetId, targetName, null, details);
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Gets the most recent audit entries.
     * M-16 fix: Limit is capped to MAX_ENTRIES to prevent unbounded queries.
     *
     * @param limit Maximum number of entries to return (capped to MAX_ENTRIES)
     * @return List of recent entries (newest first)
     */
    @Nonnull
    public List<AuditEntry> getRecent(int limit) {
        // M-16 fix: Clamp limit to valid range
        int safeLimit = Math.clamp(limit, 0, MAX_ENTRIES);
        return Objects.requireNonNull(entries.stream()
            .limit(safeLimit)
            .toList());
    }

    /**
     * Gets audit entries for a specific area/template.
     * M-16 fix: Limit is capped to MAX_ENTRIES.
     *
     * @param targetId The area/template ID
     * @param limit    Maximum entries to return (capped to MAX_ENTRIES)
     * @return List of entries for that target (newest first)
     */
    @Nonnull
    public List<AuditEntry> getByTarget(@Nonnull UUID targetId, int limit) {
        Objects.requireNonNull(targetId);
        // M-16 fix: Clamp limit to valid range
        int safeLimit = Math.clamp(limit, 0, MAX_ENTRIES);
        return Objects.requireNonNull(entries.stream()
            .filter(e -> e.targetId().equals(targetId))
            .limit(safeLimit)
            .toList());
    }

    /**
     * Gets audit entries by a specific actor.
     * M-16 fix: Limit is capped to MAX_ENTRIES.
     *
     * @param actorId The player's UUID
     * @param limit   Maximum entries to return (capped to MAX_ENTRIES)
     * @return List of entries by that actor (newest first)
     */
    @Nonnull
    public List<AuditEntry> getByActor(@Nonnull UUID actorId, int limit) {
        Objects.requireNonNull(actorId);
        // M-16 fix: Clamp limit to valid range
        int safeLimit = Math.clamp(limit, 0, MAX_ENTRIES);
        return Objects.requireNonNull(entries.stream()
            .filter(e -> actorId.equals(e.actorId()))
            .limit(safeLimit)
            .toList());
    }

    /**
     * Gets audit entries by action type.
     * M-16 fix: Limit is capped to MAX_ENTRIES.
     *
     * @param action The action type
     * @param limit  Maximum entries to return (capped to MAX_ENTRIES)
     * @return List of entries of that type (newest first)
     */
    @Nonnull
    public List<AuditEntry> getByAction(@Nonnull ActionType action, int limit) {
        Objects.requireNonNull(action);
        // M-16 fix: Clamp limit to valid range
        int safeLimit = Math.clamp(limit, 0, MAX_ENTRIES);
        return Objects.requireNonNull(entries.stream()
            .filter(e -> e.action() == action)
            .limit(safeLimit)
            .toList());
    }

    /**
     * Gets the total number of entries in the log.
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Clears all entries (use with caution).
     */
    public void clear() {
        entries.clear();
        setDirty();
        LOGGER.info("[AuditLog] Audit log cleared");
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        ListTag entriesList = new ListTag();
        for (AuditEntry entry : entries) {
            AuditEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry)
                .result()
                .ifPresent(entriesList::add);
        }
        tag.put(TAG_ENTRIES, entriesList);

        return tag;
    }

    @Nonnull
    public static AreaAuditLog load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AreaAuditLog log = new AreaAuditLog();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[AuditLog] Loading data from newer version {} (current: {})", version, DATA_VERSION);
        }

        ListTag entriesList = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        List<AuditEntry> loadedEntries = new ArrayList<>();

        for (int i = 0; i < entriesList.size(); i++) {
            CompoundTag entryTag = entriesList.getCompound(i);
            AuditEntry.CODEC.parse(NbtOps.INSTANCE, entryTag)
                .result()
                .ifPresent(loadedEntries::add);
        }

        // Add in order (oldest to newest, so newest end up at front of deque)
        for (int i = loadedEntries.size() - 1; i >= 0; i--) {
            log.entries.addFirst(loadedEntries.get(i));
        }

        LOGGER.info("[AuditLog] Loaded {} audit entries", log.entries.size());
        return log;
    }
}
