package com.devmod.npc.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Immutable record representing a dialog node containing text and options.
 * Nodes can have visibility conditions and multiple response options.
 */
public record DialogNode(
    @Nonnull String id,
    @Nonnull List<String> lines,
    @Nonnull List<DialogOption> options,
    @Nullable DialogCondition showCondition
) {
    public static final Codec<DialogNode> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(DialogNode::id),
            Codec.STRING.listOf().fieldOf("lines").forGetter(DialogNode::lines),
            DialogOption.CODEC.listOf().optionalFieldOf("options", List.of()).forGetter(DialogNode::options),
            DialogCondition.CODEC.optionalFieldOf("showCondition").forGetter(n -> Optional.ofNullable(n.showCondition()))
        ).apply(instance, (id, lines, options, showCondition) ->
            new DialogNode(id, lines, options, showCondition.orElse(null)))
    );

    /**
     * Creates a simple node with text and no options (terminal node).
     */
    @Nonnull
    public static DialogNode terminal(@Nonnull String id, @Nonnull String... lines) {
        return new DialogNode(id, List.of(lines), List.of(), null);
    }

    /**
     * Creates a node with text and options.
     */
    @Nonnull
    public static DialogNode withOptions(@Nonnull String id, @Nonnull List<String> lines, @Nonnull List<DialogOption> options) {
        return new DialogNode(id, lines, options, null);
    }

    /**
     * Creates a copy with updated lines.
     */
    @Nonnull
    public DialogNode withLines(@Nonnull List<String> lines) {
        return new DialogNode(id, lines, options, showCondition);
    }

    /**
     * Creates a copy with an added line.
     */
    @Nonnull
    public DialogNode withAddedLine(@Nonnull String line) {
        List<String> newLines = new ArrayList<>(lines);
        newLines.add(line);
        return new DialogNode(id, List.copyOf(newLines), options, showCondition);
    }

    /**
     * Creates a copy with updated options.
     */
    @Nonnull
    public DialogNode withOptions(@Nonnull List<DialogOption> options) {
        return new DialogNode(id, lines, options, showCondition);
    }

    /**
     * Creates a copy with an added option.
     */
    @Nonnull
    public DialogNode withAddedOption(@Nonnull DialogOption option) {
        List<DialogOption> newOptions = new ArrayList<>(options);
        newOptions.add(option);
        return new DialogNode(id, lines, List.copyOf(newOptions), showCondition);
    }

    /**
     * Creates a copy with a visibility condition.
     */
    @Nonnull
    public DialogNode withCondition(@Nullable DialogCondition condition) {
        return new DialogNode(id, lines, options, condition);
    }

    /**
     * Returns true if this node has a visibility condition.
     */
    public boolean hasCondition() {
        return showCondition != null;
    }

    /**
     * Returns true if this node has no options (terminal node).
     */
    public boolean isTerminal() {
        return options.isEmpty();
    }

    /**
     * Returns the combined text as a single string with newlines.
     */
    @Nonnull
    public String getCombinedText() {
        return String.join("\n", lines);
    }

    /**
     * Gets an option by its ID.
     */
    @Nonnull
    public Optional<DialogOption> getOption(@Nonnull String optionId) {
        return options.stream()
            .filter(o -> o.id().equals(optionId))
            .findFirst();
    }
}
