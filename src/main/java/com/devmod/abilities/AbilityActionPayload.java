package com.devmod.abilities;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;

public record AbilityActionPayload(@Nullable AbilityType ability, int direction) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final CustomPacketPayload.Type<AbilityActionPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "ability_action")));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActionPayload> STREAM_CODEC =
        StreamCodec.of(AbilityActionPayload::write, AbilityActionPayload::read);

    private static AbilityActionPayload read(RegistryFriendlyByteBuf buf) {
        int abilityOrdinal = buf.readVarInt();
        AbilityType[] values = AbilityType.values();
        AbilityType ability = (abilityOrdinal >= 0 && abilityOrdinal < values.length)
            ? values[abilityOrdinal]
            : null;
        int direction = buf.readVarInt();
        return new AbilityActionPayload(ability, direction);
    }

    private static void write(RegistryFriendlyByteBuf buf, AbilityActionPayload payload) {
        buf.writeVarInt(payload.ability != null ? payload.ability.ordinal() : -1);
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
     * @param dir Direction to dodge (BACK, LEFT, RIGHT, FORWARD)
     */
    public static AbilityActionPayload dodge(DodgeAbilitySystem.DodgeDirection dir) {
        int dirOrdinal = dir != null ? dir.ordinal() : DodgeAbilitySystem.DodgeDirection.BACK.ordinal();
        return new AbilityActionPayload(AbilityType.DODGE, dirOrdinal);
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

    @Override
    public int estimatedSize() {
        // Two VarInts: ability ordinal + direction (size depends on value)
        int abilityOrdinal = ability != null ? ability.ordinal() : -1;
        return PayloadSizeUtil.varIntSize(abilityOrdinal) + PayloadSizeUtil.varIntSize(direction);
    }
}
