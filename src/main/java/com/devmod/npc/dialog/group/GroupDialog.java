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
            buf.writeUtf(dialog.id);
            buf.writeUtf(dialog.name);
            buf.writeVarInt(dialog.participants.size());
            for (UUID participant : dialog.participants) {
                buf.writeUUID(participant);
            }
            buf.writeVarInt(dialog.nodes.size());
            for (var entry : dialog.nodes.entrySet()) {
                buf.writeUtf(entry.getKey());
                GroupDialogNode.STREAM_CODEC.encode(buf, entry.getValue());
            }
            buf.writeUtf(dialog.entryNodeId);
            buf.writeLong(dialog.updatedAt);
            buf.writeVarInt(dialog.revision);
        },
        buf -> {
            String id = buf.readUtf();
            String name = buf.readUtf();
            int participantCount = buf.readVarInt();
            List<UUID> participants = new ArrayList<>();
            for (int i = 0; i < participantCount; i++) {
                participants.add(buf.readUUID());
            }
            int nodeCount = buf.readVarInt();
            Map<String, GroupDialogNode> nodes = new HashMap<>();
            for (int i = 0; i < nodeCount; i++) {
                String nodeId = buf.readUtf();
                GroupDialogNode node = GroupDialogNode.STREAM_CODEC.decode(buf);
                nodes.put(nodeId, node);
            }
            String entryNodeId = buf.readUtf();
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
        for (GroupDialogNode node : nodes.values()) {
            for (var option : node.options()) {
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
