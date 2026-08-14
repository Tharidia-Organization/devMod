package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: the AI goals of every nearby mob, from both selectors.
 * <p>
 * Carries only the part the client cannot see for itself: {@code goalSelector} and
 * {@code targetSelector} are plain server-side fields, and a goal's running state exists
 * nowhere else. The mob is resolved client-side by id, so the labels follow its interpolated
 * position rather than the position it had when the packet was built.
 * <p>
 * Both selectors travel in one entry per mob, so one snapshot wholly replaces the previous
 * one. Which selector a goal came from is a flag on {@link GoalInfo} rather than a prefix on
 * the name, so the renderer can colour it without parsing text back apart.
 */
public record EntityGoalsPayload(List<MobGoals> mobs) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * Maximum mobs per payload to prevent DoS via unbounded allocation.
     * <p>
     * Deliberately lower than the 128 of {@link BrainsPayload}/{@link BeesPayload}: an entry
     * there is two var-ints, an entry here is a whole goal list with class names (~500 bytes),
     * so 128 mobs would put a realistic snapshot past the 32 KB the handler validates against
     * and it would be dropped. 32 mobs lands near 16 KB, and more than 32 stacks of goal text
     * on screen is unreadable anyway.
     */
    private static final int MAX_MOBS = 32;
    /** Maximum goals per mob; both selectors together stay far under this even for boss-like mobs. */
    private static final int MAX_GOALS_PER_MOB = 32;
    /** Maximum goal name length; names are goal class simple names. */
    private static final int MAX_NAME_LENGTH = 64;

    public static final CustomPacketPayload.Type<EntityGoalsPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_goals")));

    public static final StreamCodec<FriendlyByteBuf, EntityGoalsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityGoalsPayload decode(@Nonnull FriendlyByteBuf buf) {
            int mobCount = Math.min(buf.readVarInt(), MAX_MOBS);
            if (mobCount < 0) mobCount = 0;
            List<MobGoals> mobs = new ArrayList<>(mobCount);
            for (int i = 0; i < mobCount; i++) {
                int entityId = buf.readVarInt();
                int goalCount = Math.min(buf.readVarInt(), MAX_GOALS_PER_MOB);
                if (goalCount < 0) goalCount = 0;
                List<GoalInfo> goals = new ArrayList<>(goalCount);
                for (int g = 0; g < goalCount; g++) {
                    goals.add(new GoalInfo(
                        buf.readVarInt(),  // priority
                        buf.readBoolean(), // running
                        buf.readBoolean(), // targetSelector
                        Objects.requireNonNull(buf.readUtf(MAX_NAME_LENGTH)) // name
                    ));
                }
                mobs.add(new MobGoals(entityId, goals));
            }
            return new EntityGoalsPayload(mobs);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull EntityGoalsPayload payload) {
            buf.writeVarInt(payload.mobs.size());
            for (MobGoals mob : payload.mobs) {
                buf.writeVarInt(mob.entityId);
                buf.writeVarInt(mob.goals.size());
                for (GoalInfo goal : mob.goals) {
                    buf.writeVarInt(goal.priority);
                    buf.writeBoolean(goal.running);
                    buf.writeBoolean(goal.targetSelector);
                    buf.writeUtf(Objects.requireNonNull(goal.name));
                }
            }
        }
    };

    /** Caps the sender so a crowded search box cannot exceed what the codec will decode. */
    public static int maxMobs() {
        return MAX_MOBS;
    }

    /** Caps the sender so a goal-heavy mob cannot exceed what the codec will decode. */
    public static int maxGoalsPerMob() {
        return MAX_GOALS_PER_MOB;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(mobs.size());
        for (MobGoals mob : mobs) {
            size += PayloadSizeUtil.varIntSize(mob.entityId);
            size += PayloadSizeUtil.varIntSize(mob.goals.size());
            for (GoalInfo goal : mob.goals) {
                size += PayloadSizeUtil.varIntSize(goal.priority);
                size += 1; // running
                size += 1; // targetSelector
                size += PayloadSizeUtil.estimatedUtfSize(goal.name);
            }
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record MobGoals(
        int entityId,
        List<GoalInfo> goals
    ) {}

    public record GoalInfo(
        int priority,
        boolean running,
        boolean targetSelector,
        String name
    ) {}
}
