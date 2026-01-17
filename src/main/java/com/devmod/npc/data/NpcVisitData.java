package com.devmod.npc.data;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Immutable record tracking player visits to NPCs.
 * Used for "first visit" conditions and visit count placeholders.
 */
public record NpcVisitData(
    @Nonnull UUID playerId,
    @Nonnull UUID npcId,
    long firstVisitedAt,
    long lastVisitedAt,
    int visitCount
) {
    public static final Codec<NpcVisitData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("playerId").forGetter(NpcVisitData::playerId),
            UUIDUtil.CODEC.fieldOf("npcId").forGetter(NpcVisitData::npcId),
            Codec.LONG.fieldOf("firstVisitedAt").forGetter(NpcVisitData::firstVisitedAt),
            Codec.LONG.fieldOf("lastVisitedAt").forGetter(NpcVisitData::lastVisitedAt),
            Codec.INT.optionalFieldOf("visitCount", 1).forGetter(NpcVisitData::visitCount)
        ).apply(instance, NpcVisitData::new)
    );

    /**
     * Creates a new visit record for the first visit.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return A new NpcVisitData with current timestamp for both first and last visit
     */
    @Nonnull
    public static NpcVisitData createFirstVisit(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        long now = System.currentTimeMillis();
        return new NpcVisitData(playerId, npcId, now, now, 1);
    }

    /**
     * Creates a copy with updated last visit timestamp and incremented visit count.
     */
    @Nonnull
    public NpcVisitData touch() {
        return new NpcVisitData(playerId, npcId, firstVisitedAt, System.currentTimeMillis(), visitCount + 1);
    }

    /**
     * Returns the time since the first visit in milliseconds.
     */
    public long timeSinceFirstVisit() {
        return System.currentTimeMillis() - firstVisitedAt;
    }

    /**
     * Returns the time since the last visit in milliseconds.
     */
    public long timeSinceLastVisit() {
        return System.currentTimeMillis() - lastVisitedAt;
    }
}
