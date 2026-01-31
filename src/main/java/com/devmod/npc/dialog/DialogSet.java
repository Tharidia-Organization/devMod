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

import com.devmod.npc.dialog.condition.DialogCondition;
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
            buf.writeUtf(DialogLimits.truncate(data.id, DialogLimits.MAX_DIALOG_ID_LENGTH), DialogLimits.MAX_DIALOG_ID_LENGTH);
            buf.writeUtf(DialogLimits.truncate(data.name, DialogLimits.MAX_DIALOG_NAME_LENGTH), DialogLimits.MAX_DIALOG_NAME_LENGTH);
            int nodeCount = Math.min(data.nodes.size(), DialogLimits.MAX_NODES);
            buf.writeVarInt(nodeCount);
            int written = 0;
            for (var entry : data.nodes.entrySet()) {
                if (written >= nodeCount) {
                    break;
                }
                buf.writeUtf(DialogLimits.truncate(entry.getKey(), DialogLimits.MAX_NODE_ID_LENGTH), DialogLimits.MAX_NODE_ID_LENGTH);
                writeNode(buf, entry.getValue());
                written++;
            }
            buf.writeUtf(DialogLimits.truncate(data.entryNodeId, DialogLimits.MAX_NODE_ID_LENGTH), DialogLimits.MAX_NODE_ID_LENGTH);
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
            String id = buf.readUtf(DialogLimits.MAX_DIALOG_ID_LENGTH);
            String name = buf.readUtf(DialogLimits.MAX_DIALOG_NAME_LENGTH);
            int nodeCount = buf.readVarInt();
            if (nodeCount < 0 || nodeCount > DialogLimits.MAX_NODES) {
                throw new IllegalArgumentException("Invalid dialog node count: " + nodeCount);
            }
            Map<String, DialogNode> nodes = new HashMap<>();
            for (int i = 0; i < nodeCount; i++) {
                String nodeId = buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH);
                DialogNode node = readNode(buf, nodeId);
                nodes.put(nodeId, node);
            }
            String entryNodeId = buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH);
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            boolean isPreset = buf.readBoolean();
            UUID ownerUUID = buf.readBoolean() ? buf.readUUID() : null;
            DialogSchedule schedule = buf.readBoolean() ? DialogSchedule.STREAM_CODEC.decode(buf) : null;
            return new DialogSet(id, name, nodes, entryNodeId, updatedAt, revision, isPreset, ownerUUID, schedule);
        }
    );

    private static void writeNode(FriendlyByteBuf buf, DialogNode node) {
        int lineCount = Math.min(node.lines().size(), DialogLimits.MAX_LINES_PER_NODE);
        buf.writeVarInt(lineCount);
        int writtenLines = 0;
        for (String line : node.lines()) {
            if (writtenLines >= lineCount) {
                break;
            }
            buf.writeUtf(DialogLimits.truncate(line, DialogLimits.MAX_LINE_LENGTH), DialogLimits.MAX_LINE_LENGTH);
            writtenLines++;
        }
        int optionCount = Math.min(node.options().size(), DialogLimits.MAX_OPTIONS_PER_NODE);
        buf.writeVarInt(optionCount);
        int writtenOptions = 0;
        for (DialogOption option : node.options()) {
            if (writtenOptions >= optionCount) {
                break;
            }
            DialogOption.STREAM_CODEC.encode(buf, option);
            writtenOptions++;
        }
        buf.writeBoolean(node.showCondition() != null);
        if (node.showCondition() != null) {
            DialogCondition.STREAM_CODEC.encode(buf, node.showCondition());
        }
    }

    private static DialogNode readNode(FriendlyByteBuf buf, String id) {
        int lineCount = buf.readVarInt();
        if (lineCount < 0 || lineCount > DialogLimits.MAX_LINES_PER_NODE) {
            throw new IllegalArgumentException("Invalid dialog line count: " + lineCount);
        }
        var lines = new java.util.ArrayList<String>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(buf.readUtf(DialogLimits.MAX_LINE_LENGTH));
        }
        int optionCount = buf.readVarInt();
        if (optionCount < 0 || optionCount > DialogLimits.MAX_OPTIONS_PER_NODE) {
            throw new IllegalArgumentException("Invalid dialog option count: " + optionCount);
        }
        var options = new java.util.ArrayList<DialogOption>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            options.add(DialogOption.STREAM_CODEC.decode(buf));
        }
        DialogCondition condition = buf.readBoolean() ? DialogCondition.STREAM_CODEC.decode(buf) : null;
        return new DialogNode(id, lines, options, condition);
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

        if (id.length() > DialogLimits.MAX_DIALOG_ID_LENGTH) {
            errors.add("Dialog ID too long (max " + DialogLimits.MAX_DIALOG_ID_LENGTH + " chars)");
        }
        if (name.length() > DialogLimits.MAX_DIALOG_NAME_LENGTH) {
            errors.add("Dialog name too long (max " + DialogLimits.MAX_DIALOG_NAME_LENGTH + " chars)");
        }
        if (nodes.size() > DialogLimits.MAX_NODES) {
            errors.add("Too many dialog nodes (max " + DialogLimits.MAX_NODES + ")");
        }
        if (entryNodeId.length() > DialogLimits.MAX_NODE_ID_LENGTH) {
            errors.add("Entry node ID too long (max " + DialogLimits.MAX_NODE_ID_LENGTH + " chars)");
        }

        if (entryNodeId.isEmpty()) {
            errors.add("Entry node ID is not set");
        } else if (!nodes.containsKey(entryNodeId)) {
            errors.add("Entry node '" + entryNodeId + "' does not exist");
        }

        // Check for orphan goto actions
        for (DialogNode node : nodes.values()) {
            if (node.id().length() > DialogLimits.MAX_NODE_ID_LENGTH) {
                errors.add("Node ID too long: " + node.id());
            }
            if (node.lines().size() > DialogLimits.MAX_LINES_PER_NODE) {
                errors.add("Node '" + node.id() + "' has too many lines (max " + DialogLimits.MAX_LINES_PER_NODE + ")");
            }
            for (String line : node.lines()) {
                if (line.length() > DialogLimits.MAX_LINE_LENGTH) {
                    errors.add("Line too long in node '" + node.id() + "' (max " + DialogLimits.MAX_LINE_LENGTH + " chars)");
                }
            }
            if (node.options().size() > DialogLimits.MAX_OPTIONS_PER_NODE) {
                errors.add("Node '" + node.id() + "' has too many options (max " + DialogLimits.MAX_OPTIONS_PER_NODE + ")");
            }
            for (DialogOption option : node.options()) {
                if (option.id().length() > DialogLimits.MAX_OPTION_ID_LENGTH) {
                    errors.add("Option ID too long in node '" + node.id() + "'");
                }
                if (option.label().length() > DialogLimits.MAX_OPTION_LABEL_LENGTH) {
                    errors.add("Option label too long in node '" + node.id() + "'");
                }
                if (option.icon().length() > DialogLimits.MAX_OPTION_ICON_LENGTH) {
                    errors.add("Option icon too long in node '" + node.id() + "'");
                }
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
