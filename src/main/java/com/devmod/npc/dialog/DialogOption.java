package com.devmod.npc.dialog;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Immutable record representing a dialog option that a player can select.
 * Each option has a label, optional icon, optional visibility condition, and an action.
 */
public record DialogOption(
    @Nonnull String id,
    @Nonnull String label,
    @Nonnull String icon,
    @Nullable DialogCondition showCondition,
    @Nonnull DialogAction action
) {
    public static final Codec<DialogOption> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(DialogOption::id),
            Codec.STRING.fieldOf("label").forGetter(DialogOption::label),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(DialogOption::icon),
            DialogCondition.CODEC.optionalFieldOf("showCondition").forGetter(o -> Optional.ofNullable(o.showCondition())),
            DialogAction.CODEC.fieldOf("action").forGetter(DialogOption::action)
        ).apply(instance, (id, label, icon, showCondition, action) ->
            new DialogOption(id, label, icon, showCondition.orElse(null), action))
    );

    public static final StreamCodec<FriendlyByteBuf, DialogOption> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeUtf(data.id);
            buf.writeUtf(data.label);
            buf.writeUtf(data.icon);
            // Note: Conditions are not sent to client, only evaluated server-side
            // Action type is sent for client display purposes
            buf.writeUtf(data.action.getType());
        },
        buf -> {
            String id = buf.readUtf();
            String label = buf.readUtf();
            String icon = buf.readUtf();
            buf.readUtf();
            // Client receives minimal info - actual action is executed server-side
            return new DialogOption(id, label, icon, null, new DialogAction.CloseDialog());
        }
    );

    /**
     * Creates a simple option that navigates to another node.
     */
    @Nonnull
    public static DialogOption goTo(@Nonnull String id, @Nonnull String label, @Nonnull String icon, @Nonnull String nodeId) {
        return new DialogOption(id, label, icon, null, new DialogAction.GoToNode(nodeId));
    }

    /**
     * Creates a simple option that closes the dialog.
     */
    @Nonnull
    public static DialogOption close(@Nonnull String id, @Nonnull String label, @Nonnull String icon) {
        return new DialogOption(id, label, icon, null, new DialogAction.CloseDialog());
    }

    /**
     * Creates an option with a visibility condition.
     */
    @Nonnull
    public DialogOption withCondition(@Nullable DialogCondition condition) {
        return new DialogOption(id, label, icon, condition, action);
    }

    /**
     * Creates a copy with updated label.
     */
    @Nonnull
    public DialogOption withLabel(@Nonnull String label) {
        return new DialogOption(id, label, icon, showCondition, action);
    }

    /**
     * Creates a copy with updated icon.
     */
    @Nonnull
    public DialogOption withIcon(@Nonnull String icon) {
        return new DialogOption(id, label, icon, showCondition, action);
    }

    /**
     * Creates a copy with updated action.
     */
    @Nonnull
    public DialogOption withAction(@Nonnull DialogAction action) {
        return new DialogOption(id, label, icon, showCondition, action);
    }

    /**
     * Returns true if this option has a visibility condition.
     */
    public boolean hasCondition() {
        return showCondition != null;
    }
}
