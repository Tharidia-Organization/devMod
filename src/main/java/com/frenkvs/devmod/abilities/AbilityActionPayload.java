package com.frenkvs.devmod.abilities;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload for ability actions (dash, dodge) from client to server.
 */
public record AbilityActionPayload(AbilityType ability, int direction) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityActionPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "ability_action")));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActionPayload> STREAM_CODEC =
        StreamCodec.of(AbilityActionPayload::write, AbilityActionPayload::read);

    private static AbilityActionPayload read(RegistryFriendlyByteBuf buf) {
        return new AbilityActionPayload(
            AbilityType.values()[buf.readVarInt()],
            buf.readVarInt()
        );
    }

    private static void write(RegistryFriendlyByteBuf buf, AbilityActionPayload payload) {
        buf.writeVarInt(payload.ability.ordinal());
        buf.writeVarInt(payload.direction);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create a dash action payload.
     */
    public static AbilityActionPayload dash() {
        return new AbilityActionPayload(AbilityType.DASH, 0);
    }

    /**
     * Create a dodge action payload with direction.
     * @param direction 0=BACK, 1=LEFT, 2=RIGHT, 3=FORWARD
     */
    public static AbilityActionPayload dodge(DodgeAbilitySystem.DodgeDirection dir) {
        return new AbilityActionPayload(AbilityType.DODGE, dir != null ? dir.ordinal() : 0);
    }

    /**
     * Get dodge direction from the payload.
     */
    public DodgeAbilitySystem.DodgeDirection getDodgeDirection() {
        var values = DodgeAbilitySystem.DodgeDirection.values();
        if (direction >= 0 && direction < values.length) {
            return values[direction];
        }
        return DodgeAbilitySystem.DodgeDirection.BACK;
    }

    /**
     * Ability types that can be triggered by this payload.
     */
    public enum AbilityType {
        DASH,
        DODGE
    }
}
