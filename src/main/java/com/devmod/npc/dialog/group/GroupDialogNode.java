package com.devmod.npc.dialog.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * A node in a group dialog containing multiple speaker lines.
 * Similar to {@link com.devmod.npc.dialog.DialogNode} but with speaker attribution.
 */
public record GroupDialogNode(
    @Nonnull String id,
    @Nonnull List<SpeakerLine> lines,
    @Nonnull List<DialogOption> options,
    @Nullable DialogCondition showCondition
) {
    public static final Codec<GroupDialogNode> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(GroupDialogNode::id),
            SpeakerLine.CODEC.listOf().fieldOf("lines").forGetter(GroupDialogNode::lines),
            DialogOption.CODEC.listOf().fieldOf("options").forGetter(GroupDialogNode::options),
            DialogCondition.CODEC.optionalFieldOf("showCondition").forGetter(n -> Optional.ofNullable(n.showCondition()))
        ).apply(instance, (id, lines, options, condition) ->
            new GroupDialogNode(id, lines, options, condition.orElse(null)))
    );

    public static final StreamCodec<FriendlyByteBuf, GroupDialogNode> STREAM_CODEC = StreamCodec.of(
        (buf, node) -> {
            buf.writeUtf(node.id);
            buf.writeVarInt(node.lines.size());
            for (SpeakerLine line : node.lines) {
                SpeakerLine.STREAM_CODEC.encode(buf, line);
            }
            buf.writeVarInt(node.options.size());
            for (DialogOption option : node.options) {
                DialogOption.STREAM_CODEC.encode(buf, option);
            }
            // Note: showCondition is not sent to client (evaluated server-side)
        },
        buf -> {
            String id = buf.readUtf();
            int lineCount = buf.readVarInt();
            List<SpeakerLine> lines = new ArrayList<>();
            for (int i = 0; i < lineCount; i++) {
                lines.add(SpeakerLine.STREAM_CODEC.decode(buf));
            }
            int optionCount = buf.readVarInt();
            List<DialogOption> options = new ArrayList<>();
            for (int i = 0; i < optionCount; i++) {
                options.add(DialogOption.STREAM_CODEC.decode(buf));
            }
            return new GroupDialogNode(id, lines, options, null);
        }
    );

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Gets an option by its ID.
     */
    @Nonnull
    public Optional<DialogOption> getOption(@Nonnull String optionId) {
        return options.stream()
            .filter(opt -> opt.id().equals(optionId))
            .findFirst();
    }

    /**
     * Returns true if this node has a show condition.
     */
    public boolean hasCondition() {
        return showCondition != null;
    }

    /**
     * Returns the number of lines in this node.
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Returns the number of options in this node.
     */
    public int optionCount() {
        return options.size();
    }

    /**
     * Gets a line by index.
     */
    @Nonnull
    public Optional<SpeakerLine> getLine(int index) {
        if (index < 0 || index >= lines.size()) {
            return Optional.empty();
        }
        return Optional.of(lines.get(index));
    }

    // ========================================================================
    // Builder-style Methods
    // ========================================================================

    /**
     * Returns a copy with an added line.
     */
    @Nonnull
    public GroupDialogNode withLine(@Nonnull SpeakerLine line) {
        List<SpeakerLine> newLines = new ArrayList<>(lines);
        newLines.add(line);
        return new GroupDialogNode(id, List.copyOf(newLines), options, showCondition);
    }

    /**
     * Returns a copy with a line replaced.
     */
    @Nonnull
    public GroupDialogNode withLineAt(int index, @Nonnull SpeakerLine line) {
        List<SpeakerLine> newLines = new ArrayList<>(lines);
        if (index >= 0 && index < newLines.size()) {
            newLines.set(index, line);
        }
        return new GroupDialogNode(id, List.copyOf(newLines), options, showCondition);
    }

    /**
     * Returns a copy with a line removed.
     */
    @Nonnull
    public GroupDialogNode withoutLine(int index) {
        if (index < 0 || index >= lines.size()) {
            return this;
        }
        List<SpeakerLine> newLines = new ArrayList<>(lines);
        newLines.remove(index);
        return new GroupDialogNode(id, List.copyOf(newLines), options, showCondition);
    }

    /**
     * Returns a copy with an added option.
     */
    @Nonnull
    public GroupDialogNode withOption(@Nonnull DialogOption option) {
        List<DialogOption> newOptions = new ArrayList<>(options);
        newOptions.add(option);
        return new GroupDialogNode(id, lines, List.copyOf(newOptions), showCondition);
    }

    /**
     * Returns a copy with a different show condition.
     */
    @Nonnull
    public GroupDialogNode withCondition(@Nullable DialogCondition condition) {
        return new GroupDialogNode(id, lines, options, condition);
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Creates an empty node with the given ID.
     */
    @Nonnull
    public static GroupDialogNode empty(@Nonnull String id) {
        return new GroupDialogNode(id, List.of(), List.of(), null);
    }

    /**
     * Creates a node with a single speaker line.
     */
    @Nonnull
    public static GroupDialogNode single(@Nonnull String id, @Nonnull SpeakerLine line) {
        return new GroupDialogNode(id, List.of(line), List.of(), null);
    }
}
