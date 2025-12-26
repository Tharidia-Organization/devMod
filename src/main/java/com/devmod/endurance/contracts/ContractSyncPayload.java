package com.devmod.endurance.contracts;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

import io.netty.buffer.ByteBuf;

public record ContractSyncPayload(
    @Nonnull List<ContractData> contracts,
    float totalMultiplier
) implements CustomPacketPayload {

    public static final Type<ContractSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "contract_sync"));

    public static final StreamCodec<ByteBuf, ContractSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ContractData.LIST_CODEC, ContractSyncPayload::contracts,
            ByteBufCodecs.FLOAT, ContractSyncPayload::totalMultiplier,
            ContractSyncPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Data for a single active contract.
     */
    public record ContractData(
        @Nonnull String id,
        @Nonnull String nameKey,
        int tierOrdinal,
        int color,
        float multiplier,
        boolean violated
    ) {
        public static final StreamCodec<ByteBuf, ContractData> CODEC = StreamCodec.of(
            (buf, data) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, data.nameKey);
                ByteBufCodecs.VAR_INT.encode(buf, data.tierOrdinal);
                ByteBufCodecs.VAR_INT.encode(buf, data.color);
                ByteBufCodecs.FLOAT.encode(buf, data.multiplier);
                ByteBufCodecs.BOOL.encode(buf, data.violated);
            },
            buf -> new ContractData(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf)
            )
        );

        public static final StreamCodec<ByteBuf, List<ContractData>> LIST_CODEC =
            ContractData.CODEC.apply(ByteBufCodecs.list());

        public BloodContract.ContractTier getTier() {
            return BloodContract.ContractTier.values()[tierOrdinal];
        }
    }

    /**
     * Create sync payload for a player's active contracts.
     */
    public static ContractSyncPayload forSession(ActiveContractManager.ContractSession session) {
        List<ContractData> data = session.getActiveContracts().stream()
            .map(contract -> new ContractData(
                contract.getId().toString(),
                contract.getNameKey(),
                contract.getTier().ordinal(),
                contract.getColor(),
                contract.getTotalMultiplier(),
                session.getViolatedContracts().contains(contract)
            ))
            .toList();

        return new ContractSyncPayload(data, session.getRewardMultiplier());
    }
}
