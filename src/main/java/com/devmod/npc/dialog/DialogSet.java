package com.devmod.npc.dialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable record representing a complete dialog set with multiple nodes.
 * Dialog sets can be presets (built-in, non-modifiable) or custom (user-created).
 *
 * <p>Dialog sets can optionally have a {@link DialogSchedule} that restricts
 * when the dialog is available (by game time, day of week, or date range).
 */
public record DialogSet(
    @Nonnull String id,
    @Nonnull String name,
    @Nonnull Map<String, DialogNode> nodes,
    @Nonnull String entryNodeId,
    long updatedAt,
    int revision,
    boolean isPreset,
    @Nullable UUID ownerUUID,
    @Nullable DialogSchedule schedule
) {
    public static final Codec<DialogSet> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(DialogSet::id),
            Codec.STRING.fieldOf("name").forGetter(DialogSet::name),
            Codec.unboundedMap(Codec.STRING, DialogNode.CODEC).fieldOf("nodes").forGetter(DialogSet::nodes),
            Codec.STRING.fieldOf("entryNodeId").forGetter(DialogSet::entryNodeId),
            Codec.LONG.optionalFieldOf("updatedAt", 0L).forGetter(DialogSet::updatedAt),
            Codec.INT.optionalFieldOf("revision", 0).forGetter(DialogSet::revision),
            Codec.BOOL.optionalFieldOf("isPreset", false).forGetter(DialogSet::isPreset),
            UUIDUtil.CODEC.optionalFieldOf("ownerUUID").forGetter(s -> Optional.ofNullable(s.ownerUUID())),
            DialogSchedule.CODEC.optionalFieldOf("schedule").forGetter(s -> Optional.ofNullable(s.schedule()))
        ).apply(instance, (id, name, nodes, entryNodeId, updatedAt, revision, isPreset, ownerUUID, schedule) ->
            new DialogSet(id, name, nodes, entryNodeId, updatedAt, revision, isPreset, ownerUUID.orElse(null), schedule.orElse(null)))
    );

    public static final StreamCodec<FriendlyByteBuf, DialogSet> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeUtf(data.id);
            buf.writeUtf(data.name);
            buf.writeVarInt(data.nodes.size());
            for (var entry : data.nodes.entrySet()) {
                buf.writeUtf(entry.getKey());
                writeNode(buf, entry.getValue());
            }
            buf.writeUtf(data.entryNodeId);
            buf.writeLong(data.updatedAt);
            buf.writeVarInt(data.revision);
            buf.writeBoolean(data.isPreset);
            buf.writeBoolean(data.ownerUUID != null);
            if (data.ownerUUID != null) {
                buf.writeUUID(data.ownerUUID);
            }
            buf.writeBoolean(data.schedule != null);
            if (data.schedule != null) {
                DialogSchedule.STREAM_CODEC.encode(buf, data.schedule);
            }
        },
        buf -> {
            String id = buf.readUtf();
            String name = buf.readUtf();
            int nodeCount = buf.readVarInt();
            Map<String, DialogNode> nodes = new HashMap<>();
            for (int i = 0; i < nodeCount; i++) {
                String nodeId = buf.readUtf();
                DialogNode node = readNode(buf, nodeId);
                nodes.put(nodeId, node);
            }
            String entryNodeId = buf.readUtf();
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            boolean isPreset = buf.readBoolean();
            UUID ownerUUID = buf.readBoolean() ? buf.readUUID() : null;
            DialogSchedule schedule = buf.readBoolean() ? DialogSchedule.STREAM_CODEC.decode(buf) : null;
            return new DialogSet(id, name, nodes, entryNodeId, updatedAt, revision, isPreset, ownerUUID, schedule);
        }
    );

    private static void writeNode(FriendlyByteBuf buf, DialogNode node) {
        buf.writeVarInt(node.lines().size());
        for (String line : node.lines()) {
            buf.writeUtf(line);
        }
        buf.writeVarInt(node.options().size());
        for (DialogOption option : node.options()) {
            DialogOption.STREAM_CODEC.encode(buf, option);
        }
    }

    private static DialogNode readNode(FriendlyByteBuf buf, String id) {
        int lineCount = buf.readVarInt();
        var lines = new java.util.ArrayList<String>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(buf.readUtf());
        }
        int optionCount = buf.readVarInt();
        var options = new java.util.ArrayList<DialogOption>();
        for (int i = 0; i < optionCount; i++) {
            options.add(DialogOption.STREAM_CODEC.decode(buf));
        }
        return new DialogNode(id, lines, options, null);
    }

    /**
     * Creates a new empty dialog set.
     */
    @Nonnull
    public static DialogSet createEmpty(@Nonnull String id, @Nonnull String name, @Nullable UUID ownerUUID) {
        return new DialogSet(
            id,
            name,
            Map.of(),
            "",
            System.currentTimeMillis(),
            0,
            false,
            ownerUUID,
            null
        );
    }

    /**
     * Creates a new preset dialog set.
     */
    @Nonnull
    public static DialogSet createPreset(@Nonnull String id, @Nonnull String name, @Nonnull Map<String, DialogNode> nodes, @Nonnull String entryNodeId) {
        return new DialogSet(
            id,
            name,
            nodes,
            entryNodeId,
            0,
            0,
            true,
            null,
            null
        );
    }

    /**
     * Creates a new preset dialog set with a schedule.
     */
    @Nonnull
    public static DialogSet createPresetWithSchedule(
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull Map<String, DialogNode> nodes,
        @Nonnull String entryNodeId,
        @Nullable DialogSchedule schedule
    ) {
        return new DialogSet(
            id,
            name,
            nodes,
            entryNodeId,
            0,
            0,
            true,
            null,
            schedule
        );
    }

    /**
     * Gets the entry node of this dialog set.
     */
    @Nonnull
    public Optional<DialogNode> getEntryNode() {
        return Optional.ofNullable(nodes.get(entryNodeId));
    }

    /**
     * Gets a node by its ID.
     */
    @Nonnull
    public Optional<DialogNode> getNode(@Nonnull String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Creates a copy with an added or updated node.
     */
    @Nonnull
    public DialogSet withNode(@Nonnull DialogNode node) {
        Map<String, DialogNode> newNodes = new HashMap<>(nodes);
        newNodes.put(node.id(), node);
        return new DialogSet(id, name, Map.copyOf(newNodes), entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Creates a copy with a node removed.
     */
    @Nonnull
    public DialogSet withoutNode(@Nonnull String nodeId) {
        Map<String, DialogNode> newNodes = new HashMap<>(nodes);
        newNodes.remove(nodeId);
        return new DialogSet(id, name, Map.copyOf(newNodes), entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Creates a copy with updated entry node ID.
     */
    @Nonnull
    public DialogSet withEntryNode(@Nonnull String entryNodeId) {
        return new DialogSet(id, name, nodes, entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Creates a copy with updated name.
     */
    @Nonnull
    public DialogSet withName(@Nonnull String name) {
        return new DialogSet(id, name, nodes, entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Creates a copy with updated schedule.
     */
    @Nonnull
    public DialogSet withSchedule(@Nullable DialogSchedule schedule) {
        return new DialogSet(id, name, nodes, entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Creates a copy with incremented revision and updated timestamp.
     */
    @Nonnull
    public DialogSet touch() {
        return new DialogSet(id, name, nodes, entryNodeId, System.currentTimeMillis(), revision + 1, isPreset, ownerUUID, schedule);
    }

    /**
     * Returns true if this dialog set is modifiable (not a preset).
     */
    public boolean isModifiable() {
        return !isPreset;
    }

    /**
     * Returns true if this dialog set has a schedule.
     */
    public boolean hasSchedule() {
        return schedule != null && !schedule.isAlwaysAvailable();
    }

    /**
     * Checks if the dialog is available based on its schedule.
     * Returns true if no schedule is set or if the schedule allows access.
     *
     * @param level The Minecraft level (for game time)
     * @return true if the dialog is available
     */
    public boolean isScheduleAvailable(@Nonnull net.minecraft.world.level.Level level) {
        return schedule == null || schedule.isAvailable(level);
    }

    /**
     * Gets the reason why the dialog is unavailable, or null if available.
     *
     * @param level The Minecraft level (for game time)
     * @return Unavailability reason, or null if available
     */
    @Nullable
    public String getUnavailabilityReason(@Nonnull net.minecraft.world.level.Level level) {
        if (schedule == null || schedule.isAlwaysAvailable()) {
            return null;
        }

        if (!schedule.checkGameTime(level.getDayTime())) {
            String range = schedule.getGameTimeDescription();
            return "Disponibile solo dalle " + (range != null ? range : "?");
        }

        if (!schedule.checkRealWorldDay()) {
            String days = schedule.getDayOfWeekDescription();
            return "Disponibile solo: " + (days != null ? days : "?");
        }

        if (!schedule.checkDateRange()) {
            String range = schedule.getDateRangeDescription();
            return "Periodo: " + (range != null ? range : "?");
        }

        return null;
    }

    /**
     * Returns true if the given player UUID is the owner of this dialog set.
     */
    public boolean isOwnedBy(@Nonnull UUID playerUUID) {
        return ownerUUID != null && ownerUUID.equals(playerUUID);
    }

    /**
     * Returns the number of nodes in this dialog set.
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Validates the dialog set structure.
     * Returns a list of validation errors, empty if valid.
     */
    @Nonnull
    public java.util.List<String> validate() {
        var errors = new java.util.ArrayList<String>();

        if (entryNodeId.isEmpty()) {
            errors.add("Entry node ID is not set");
        } else if (!nodes.containsKey(entryNodeId)) {
            errors.add("Entry node '" + entryNodeId + "' does not exist");
        }

        // Check for orphan goto actions
        for (DialogNode node : nodes.values()) {
            for (DialogOption option : node.options()) {
                if (option.action() instanceof com.devmod.npc.dialog.action.DialogAction.GoToNode goTo) {
                    if (!nodes.containsKey(goTo.nodeId())) {
                        errors.add("Node '" + node.id() + "' option '" + option.id() + "' references non-existent node '" + goTo.nodeId() + "'");
                    }
                }
            }
        }

        return errors;
    }
}
