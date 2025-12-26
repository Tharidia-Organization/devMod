package com.devmod.endurance;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuestActionPayload(
    Action action
) implements CustomPacketPayload {

    public static final Type<QuestActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "quest_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionPayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.VAR_INT.map(i -> Action.fromId(Objects.requireNonNull(i)), Action::getId)), QuestActionPayload::action,
        QuestActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Available quest actions.
     */
    public enum Action {
        CONTINUE_AFTER_DEATH(0),      // Player chooses to respawn and continue
        GIVE_UP_AFTER_DEATH(1),       // Player chooses to end quest after death
        CONTINUE_TO_NEXT_WAVE(2),     // Player continues to next wave at checkpoint
        EXIT_AT_CHECKPOINT(3),        // Player exits at checkpoint keeping rewards
        ABANDON_QUEST(4);             // Player abandons quest mid-wave

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static Action fromId(int id) {
            for (Action action : values()) {
                if (action.id == id) return action;
            }
            return ABANDON_QUEST;
        }
    }
}
