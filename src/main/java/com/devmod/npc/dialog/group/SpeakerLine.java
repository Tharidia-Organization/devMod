package com.devmod.npc.dialog.group;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.devmod.npc.NpcEmotion;
import com.devmod.npc.dialog.DialogLimits;

/**
 * A single line of dialog with speaker information.
 * Used in multi-NPC conversations where different NPCs speak in turn.
 *
 * <p>Special speaker IDs:
 * <ul>
 *   <li>{@link #PLAYER_SPEAKER_ID} (all zeros) - The player is speaking</li>
 *   <li>{@link #NARRATOR_SPEAKER_ID} (all ones) - Narrator/system message</li>
 * </ul>
 */
public record SpeakerLine(
    @Nonnull UUID speakerId,
    @Nonnull String text,
    @Nullable NpcEmotion emotion,
    @Nullable String speakerNameOverride
) {
    /**
     * Compact constructor with null validation.
     * Ensures speakerId and text are never null to prevent NullPointerExceptions
     * in isPlayer() and isNarrator() methods.
     */
    public SpeakerLine {
        java.util.Objects.requireNonNull(speakerId, "speakerId cannot be null");
        java.util.Objects.requireNonNull(text, "text cannot be null");
    }

    /** UUID representing the player as speaker */
    public static final UUID PLAYER_SPEAKER_ID = new UUID(0L, 0L);

    /** UUID representing a narrator (no speaker shown) */
    public static final UUID NARRATOR_SPEAKER_ID = new UUID(-1L, -1L);

    public static final Codec<SpeakerLine> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("speakerId").forGetter(SpeakerLine::speakerId),
            Codec.STRING.fieldOf("text").forGetter(SpeakerLine::text),
            NpcEmotion.CODEC.optionalFieldOf("emotion").forGetter(l -> Optional.ofNullable(l.emotion())),
            Codec.STRING.optionalFieldOf("speakerNameOverride").forGetter(l -> Optional.ofNullable(l.speakerNameOverride()))
        ).apply(instance, (speakerId, text, emotion, nameOverride) ->
            new SpeakerLine(speakerId, text, emotion.orElse(null), nameOverride.orElse(null)))
    );

    public static final StreamCodec<FriendlyByteBuf, SpeakerLine> STREAM_CODEC = StreamCodec.of(
        (buf, line) -> {
            buf.writeUUID(line.speakerId);
            buf.writeUtf(DialogLimits.truncate(line.text, DialogLimits.MAX_LINE_LENGTH), DialogLimits.MAX_LINE_LENGTH);
            buf.writeBoolean(line.emotion != null);
            if (line.emotion != null) {
                buf.writeUtf(
                    DialogLimits.truncate(line.emotion.getSerializedName(), DialogLimits.MAX_ACTION_TYPE_LENGTH),
                    DialogLimits.MAX_ACTION_TYPE_LENGTH
                );
            }
            buf.writeBoolean(line.speakerNameOverride != null);
            if (line.speakerNameOverride != null) {
                buf.writeUtf(
                    DialogLimits.truncate(line.speakerNameOverride, DialogLimits.MAX_DIALOG_NAME_LENGTH),
                    DialogLimits.MAX_DIALOG_NAME_LENGTH
                );
            }
        },
        buf -> {
            UUID speakerId = buf.readUUID();
            String text = buf.readUtf(DialogLimits.MAX_LINE_LENGTH);
            NpcEmotion emotion = buf.readBoolean()
                ? NpcEmotion.fromName(buf.readUtf(DialogLimits.MAX_ACTION_TYPE_LENGTH))
                : null;
            String nameOverride = buf.readBoolean()
                ? buf.readUtf(DialogLimits.MAX_DIALOG_NAME_LENGTH)
                : null;
            return new SpeakerLine(speakerId, text, emotion, nameOverride);
        }
    );

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Creates a line spoken by a specific NPC.
     */
    @Nonnull
    public static SpeakerLine npc(@Nonnull UUID npcId, @Nonnull String text) {
        return new SpeakerLine(npcId, text, null, null);
    }

    /**
     * Creates a line spoken by a specific NPC with an emotion.
     */
    @Nonnull
    public static SpeakerLine npc(@Nonnull UUID npcId, @Nonnull String text, @Nonnull NpcEmotion emotion) {
        return new SpeakerLine(npcId, text, emotion, null);
    }

    /**
     * Creates a line spoken by the player.
     */
    @Nonnull
    public static SpeakerLine player(@Nonnull String text) {
        return new SpeakerLine(PLAYER_SPEAKER_ID, text, null, null);
    }

    /**
     * Creates a narrator/system line (no speaker shown).
     */
    @Nonnull
    public static SpeakerLine narrator(@Nonnull String text) {
        return new SpeakerLine(NARRATOR_SPEAKER_ID, text, null, null);
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Returns true if this line is spoken by the player.
     */
    public boolean isPlayer() {
        return speakerId.equals(PLAYER_SPEAKER_ID);
    }

    /**
     * Returns true if this line is a narrator/system message.
     */
    public boolean isNarrator() {
        return speakerId.equals(NARRATOR_SPEAKER_ID);
    }

    /**
     * Returns true if this line is spoken by an NPC.
     */
    public boolean isNpc() {
        return !isPlayer() && !isNarrator();
    }

    /**
     * Returns true if this line has an emotion set.
     */
    public boolean hasEmotion() {
        return emotion != null;
    }

    /**
     * Returns true if this line has a speaker name override.
     */
    public boolean hasNameOverride() {
        return speakerNameOverride != null && !speakerNameOverride.isEmpty();
    }

    // ========================================================================
    // Builder-style Methods
    // ========================================================================

    /**
     * Returns a copy with the specified emotion.
     */
    @Nonnull
    public SpeakerLine withEmotion(@Nullable NpcEmotion emotion) {
        return new SpeakerLine(speakerId, text, emotion, speakerNameOverride);
    }

    /**
     * Returns a copy with the specified speaker name override.
     */
    @Nonnull
    public SpeakerLine withNameOverride(@Nullable String nameOverride) {
        return new SpeakerLine(speakerId, text, emotion, nameOverride);
    }

    /**
     * Returns a copy with different text.
     */
    @Nonnull
    public SpeakerLine withText(@Nonnull String text) {
        return new SpeakerLine(speakerId, text, emotion, speakerNameOverride);
    }
}
