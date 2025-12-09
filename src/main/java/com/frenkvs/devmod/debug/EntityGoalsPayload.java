package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for entity AI goals debug data (server to client).
 * Shows active and available goals with priorities.
 */
public record EntityGoalsPayload(
    int entityId,
    String entityName,
    double posX,
    double posY,
    double posZ,
    List<GoalInfo> goals,
    List<GoalInfo> targetGoals
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityGoalsPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_goals"));

    public static final StreamCodec<FriendlyByteBuf, EntityGoalsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityGoalsPayload decode(FriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            String entityName = buf.readUtf();
            double posX = buf.readDouble();
            double posY = buf.readDouble();
            double posZ = buf.readDouble();

            int goalCount = buf.readVarInt();
            List<GoalInfo> goals = new ArrayList<>(goalCount);
            for (int i = 0; i < goalCount; i++) {
                goals.add(new GoalInfo(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf()
                ));
            }

            int targetGoalCount = buf.readVarInt();
            List<GoalInfo> targetGoals = new ArrayList<>(targetGoalCount);
            for (int i = 0; i < targetGoalCount; i++) {
                targetGoals.add(new GoalInfo(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf()
                ));
            }

            return new EntityGoalsPayload(entityId, entityName, posX, posY, posZ, goals, targetGoals);
        }

        @Override
        public void encode(FriendlyByteBuf buf, EntityGoalsPayload payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeUtf(payload.entityName);
            buf.writeDouble(payload.posX);
            buf.writeDouble(payload.posY);
            buf.writeDouble(payload.posZ);

            buf.writeVarInt(payload.goals.size());
            for (GoalInfo goal : payload.goals) {
                buf.writeVarInt(goal.priority);
                buf.writeBoolean(goal.isRunning);
                buf.writeUtf(goal.name);
            }

            buf.writeVarInt(payload.targetGoals.size());
            for (GoalInfo goal : payload.targetGoals) {
                buf.writeVarInt(goal.priority);
                buf.writeBoolean(goal.isRunning);
                buf.writeUtf(goal.name);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Information about a single AI goal.
     */
    public record GoalInfo(
        int priority,
        boolean isRunning,
        String name
    ) {}

    /**
     * Create an empty payload to clear debug for an entity.
     */
    public static EntityGoalsPayload clear(int entityId) {
        return new EntityGoalsPayload(entityId, "", 0, 0, 0, List.of(), List.of());
    }
}
