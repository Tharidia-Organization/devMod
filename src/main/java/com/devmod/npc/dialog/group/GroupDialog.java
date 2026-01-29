package com.devmod.npc.dialog.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.devmod.npc.dialog.DialogLimits;
/**
 * A dialog involving multiple NPCs that speak in sequence.
 * Each node contains lines with speaker attribution.
 *
 * <p>Group dialogs support:
 * <ul>
 *   <li>Multiple NPC speakers</li>
 *   <li>Player participation</li>
 *   <li>Narrator/system messages</li>
 *   <li>Per-line emotions</li>
 *   <li>Branching via standard dialog options</li>
 * </ul>
 */
public record GroupDialog(
    @Nonnull String id,
    @Nonnull String name,
    @Nonnull List<UUID> participants,
    @Nonnull Map<String, GroupDialogNode> nodes,
    @Nonnull String entryNodeId,
    long updatedAt,
    int revision
) {
    public static final Codec<GroupDialog> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(GroupDialog::id),
            Codec.STRING.fieldOf("name").forGetter(GroupDialog::name),
            UUIDUtil.CODEC.listOf().fieldOf("participants").forGetter(GroupDialog::participants),
            Codec.unboundedMap(Codec.STRING, GroupDialogNode.CODEC).fieldOf("nodes").forGetter(GroupDialog::nodes),
            Codec.STRING.fieldOf("entryNodeId").forGetter(GroupDialog::entryNodeId),
            Codec.LONG.optionalFieldOf("updatedAt", 0L).forGetter(GroupDialog::updatedAt),
            Codec.INT.optionalFieldOf("revision", 0).forGetter(GroupDialog::revision)
        ).apply(instance, GroupDialog::new)
    );

    public static final StreamCodec<FriendlyByteBuf, GroupDialog> STREAM_CODEC = StreamCodec.of(
        (buf, dialog) -> {
            buf.writeUtf(DialogLimits.truncate(dialog.id, DialogLimits.MAX_DIALOG_ID_LENGTH), DialogLimits.MAX_DIALOG_ID_LENGTH);
            buf.writeUtf(DialogLimits.truncate(dialog.name, DialogLimits.MAX_DIALOG_NAME_LENGTH), DialogLimits.MAX_DIALOG_NAME_LENGTH);
            int participantCount = Math.min(dialog.participants.size(), DialogLimits.MAX_NODES);
            buf.writeVarInt(participantCount);
            int writtenParticipants = 0;
            for (UUID participant : dialog.participants) {
                if (writtenParticipants >= participantCount) {
                    break;
                }
                buf.writeUUID(participant);
                writtenParticipants++;
            }
            int nodeCount = Math.min(dialog.nodes.size(), DialogLimits.MAX_NODES);
            buf.writeVarInt(nodeCount);
            int writtenNodes = 0;
            for (var entry : dialog.nodes.entrySet()) {
                if (writtenNodes >= nodeCount) {
                    break;
                }
                buf.writeUtf(DialogLimits.truncate(entry.getKey(), DialogLimits.MAX_NODE_ID_LENGTH), DialogLimits.MAX_NODE_ID_LENGTH);
                GroupDialogNode.STREAM_CODEC.encode(buf, entry.getValue());
                writtenNodes++;
            }
            buf.writeUtf(DialogLimits.truncate(dialog.entryNodeId, DialogLimits.MAX_NODE_ID_LENGTH), DialogLimits.MAX_NODE_ID_LENGTH);
            buf.writeLong(dialog.updatedAt);
            buf.writeVarInt(dialog.revision);
        },
        buf -> {
            String id = buf.readUtf(DialogLimits.MAX_DIALOG_ID_LENGTH);
            String name = buf.readUtf(DialogLimits.MAX_DIALOG_NAME_LENGTH);
            int participantCount = buf.readVarInt();
            if (participantCount < 0 || participantCount > DialogLimits.MAX_NODES) {
                throw new IllegalArgumentException("Invalid group dialog participant count: " + participantCount);
            }
            List<UUID> participants = new ArrayList<>();
            for (int i = 0; i < participantCount; i++) {
                participants.add(buf.readUUID());
            }
            int nodeCount = buf.readVarInt();
            if (nodeCount < 0 || nodeCount > DialogLimits.MAX_NODES) {
                throw new IllegalArgumentException("Invalid group dialog node count: " + nodeCount);
            }
            Map<String, GroupDialogNode> nodes = new HashMap<>();
            for (int i = 0; i < nodeCount; i++) {
                String nodeId = buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH);
                GroupDialogNode node = GroupDialogNode.STREAM_CODEC.decode(buf);
                nodes.put(nodeId, node);
            }
            String entryNodeId = buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH);
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            return new GroupDialog(id, name, participants, nodes, entryNodeId, updatedAt, revision);
        }
    );

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Gets the entry node of this group dialog.
     */
    @Nonnull
    public Optional<GroupDialogNode> getEntryNode() {
        return Optional.ofNullable(nodes.get(entryNodeId));
    }

    /**
     * Gets a node by its ID.
     */
    @Nonnull
    public Optional<GroupDialogNode> getNode(@Nonnull String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Returns the number of participants.
     */
    public int participantCount() {
        return participants.size();
    }

    /**
     * Returns true if the given UUID is a participant.
     */
    public boolean hasParticipant(@Nonnull UUID npcId) {
        return participants.contains(npcId);
    }

    /**
     * Returns the number of nodes.
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns all node IDs.
     */
    @Nonnull
    public Set<String> getNodeIds() {
        return Set.copyOf(nodes.keySet());
    }

    /**
     * Collects all unique speaker IDs used across all nodes.
     */
    @Nonnull
    public Set<UUID> getAllSpeakers() {
        return nodes.values().stream()
            .flatMap(node -> node.lines().stream())
            .map(SpeakerLine::speakerId)
            .collect(Collectors.toSet());
    }

    // ========================================================================
    // Builder-style Methods
    // ========================================================================

    /**
     * Returns a copy with an added participant.
     */
    @Nonnull
    public GroupDialog withParticipant(@Nonnull UUID npcId) {
        if (participants.contains(npcId)) return this;
        List<UUID> newParticipants = new ArrayList<>(participants);
        newParticipants.add(npcId);
        return new GroupDialog(id, name, List.copyOf(newParticipants), nodes, entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy without a participant.
     */
    @Nonnull
    public GroupDialog withoutParticipant(@Nonnull UUID npcId) {
        if (!participants.contains(npcId)) return this;
        List<UUID> newParticipants = new ArrayList<>(participants);
        newParticipants.remove(npcId);
        return new GroupDialog(id, name, List.copyOf(newParticipants), nodes, entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy with an added or updated node.
     */
    @Nonnull
    public GroupDialog withNode(@Nonnull GroupDialogNode node) {
        Map<String, GroupDialogNode> newNodes = new HashMap<>(nodes);
        newNodes.put(node.id(), node);
        return new GroupDialog(id, name, participants, Map.copyOf(newNodes), entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy without a node.
     */
    @Nonnull
    public GroupDialog withoutNode(@Nonnull String nodeId) {
        Map<String, GroupDialogNode> newNodes = new HashMap<>(nodes);
        newNodes.remove(nodeId);
        return new GroupDialog(id, name, participants, Map.copyOf(newNodes), entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy with a different entry node.
     */
    @Nonnull
    public GroupDialog withEntryNode(@Nonnull String entryNodeId) {
        return new GroupDialog(id, name, participants, nodes, entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy with a different name.
     */
    @Nonnull
    public GroupDialog withName(@Nonnull String name) {
        return new GroupDialog(id, name, participants, nodes, entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns a copy with updated timestamp and revision.
     */
    @Nonnull
    public GroupDialog touch() {
        return new GroupDialog(id, name, participants, nodes, entryNodeId,
            System.currentTimeMillis(), revision + 1);
    }

    // ========================================================================
    // Validation
    // ========================================================================

    /**
     * Validates the group dialog structure.
     *
     * @return List of validation errors, empty if valid
     */
    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (id.length() > DialogLimits.MAX_DIALOG_ID_LENGTH) {
            errors.add("Dialog ID too long (max " + DialogLimits.MAX_DIALOG_ID_LENGTH + " chars)");
        }
        if (name.length() > DialogLimits.MAX_DIALOG_NAME_LENGTH) {
            errors.add("Dialog name too long (max " + DialogLimits.MAX_DIALOG_NAME_LENGTH + " chars)");
        }
        if (participants.size() > DialogLimits.MAX_NODES) {
            errors.add("Too many participants (max " + DialogLimits.MAX_NODES + ")");
        }
        if (nodes.size() > DialogLimits.MAX_NODES) {
            errors.add("Too many dialog nodes (max " + DialogLimits.MAX_NODES + ")");
        }
        if (entryNodeId.length() > DialogLimits.MAX_NODE_ID_LENGTH) {
            errors.add("Entry node ID too long (max " + DialogLimits.MAX_NODE_ID_LENGTH + " chars)");
        }

        if (id.isEmpty()) {
            errors.add("Dialog ID is empty");
        }

        if (name.isEmpty()) {
            errors.add("Dialog name is empty");
        }

        if (participants.isEmpty()) {
            errors.add("No participants defined");
        }

        if (entryNodeId.isEmpty()) {
            errors.add("Entry node ID is not set");
        } else if (!nodes.containsKey(entryNodeId)) {
            errors.add("Entry node '" + entryNodeId + "' does not exist");
        }

        // Check that all speakers in lines are participants
        Set<UUID> allSpeakers = getAllSpeakers();
        for (UUID speaker : allSpeakers) {
            if (!speaker.equals(SpeakerLine.PLAYER_SPEAKER_ID)
                && !speaker.equals(SpeakerLine.NARRATOR_SPEAKER_ID)
                && !participants.contains(speaker)) {
                errors.add("Speaker " + speaker + " is not a participant");
            }
        }

        // Check for orphan goto actions
        for (var entry : nodes.entrySet()) {
            String nodeKey = entry.getKey();
            GroupDialogNode node = entry.getValue();
            if (node.id().length() > DialogLimits.MAX_NODE_ID_LENGTH) {
                errors.add("Node ID too long: " + node.id());
            }
            if (!node.id().equals(nodeKey)) {
                errors.add("Node key '" + nodeKey + "' does not match node ID '" + node.id() + "'");
            }
            if (node.lines().size() > DialogLimits.MAX_LINES_PER_NODE) {
                errors.add("Node '" + node.id() + "' has too many lines (max " + DialogLimits.MAX_LINES_PER_NODE + ")");
            }
            for (SpeakerLine line : node.lines()) {
                if (line.text().length() > DialogLimits.MAX_LINE_LENGTH) {
                    errors.add("Line too long in node '" + node.id() + "' (max " + DialogLimits.MAX_LINE_LENGTH + " chars)");
                }
                if (line.speakerNameOverride() != null
                    && line.speakerNameOverride().length() > DialogLimits.MAX_DIALOG_NAME_LENGTH) {
                    errors.add("Speaker name override too long in node '" + node.id() + "'");
                }
            }
            if (node.options().size() > DialogLimits.MAX_OPTIONS_PER_NODE) {
                errors.add("Node '" + node.id() + "' has too many options (max " + DialogLimits.MAX_OPTIONS_PER_NODE + ")");
            }
            for (var option : node.options()) {
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
                        errors.add("Node '" + node.id() + "' references non-existent node '" + goTo.nodeId() + "'");
                    }
                }
            }
        }

        return errors;
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Creates an empty group dialog.
     */
    @Nonnull
    public static GroupDialog createEmpty(@Nonnull String id, @Nonnull String name) {
        return new GroupDialog(id, name, List.of(), Map.of(), "", System.currentTimeMillis(), 0);
    }

    /**
     * Creates a group dialog with initial participants.
     */
    @Nonnull
    public static GroupDialog create(
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull List<UUID> participants
    ) {
        return new GroupDialog(id, name, List.copyOf(participants), Map.of(), "",
            System.currentTimeMillis(), 0);
    }
}
