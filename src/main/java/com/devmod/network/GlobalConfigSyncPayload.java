package com.devmod.network;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;

public record GlobalConfigSyncPayload(
    CompoundTag armorConfigs,
    CompoundTag weaponConfigs
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<GlobalConfigSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "global_config_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GlobalConfigSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, Objects.requireNonNull(val.armorConfigs()));
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, Objects.requireNonNull(val.weaponConfigs()));
        },
        buffer -> new GlobalConfigSyncPayload(
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer))
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += armorConfigs != null ? armorConfigs.sizeInBytes() : 0;
        size += weaponConfigs != null ? weaponConfigs.sizeInBytes() : 0;
        return size;
    }

    /**
     * Creates a payload from the current global configs.
     */
    public static GlobalConfigSyncPayload fromCurrentConfigs() {
        CompoundTag armorTag = new CompoundTag();
        CompoundTag weaponTag = new CompoundTag();

        // Serialize armor configs
        com.devmod.config.ArmorConfigManager.getAllGlobalStats().forEach((item, stats) -> {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
            CompoundTag statsTag = new CompoundTag();
            stats.save(statsTag);
            armorTag.put(Objects.requireNonNull(id.toString()), statsTag);
        });

        // Serialize weapon configs
        com.devmod.config.WeaponConfigManager.getAllGlobalStats().forEach((item, stats) -> {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
            CompoundTag statsTag = new CompoundTag();
            stats.save(statsTag);
            weaponTag.put(Objects.requireNonNull(id.toString()), statsTag);
        });

        return new GlobalConfigSyncPayload(armorTag, weaponTag);
    }

    /**
     * Applies the received configs on the client side.
     */
    public void applyToClientConfigs() {
        // Apply armor configs
        for (String key : armorConfigs.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(key));
            if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                CompoundTag statsTag = armorConfigs.getCompound(Objects.requireNonNull(key));
                ArmorStats stats = ArmorStats.load(statsTag);
                com.devmod.config.ArmorConfigManager.setGlobalStatsClientOnly(item, stats);
            }
        }

        // Apply weapon configs
        for (String key : weaponConfigs.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(key));
            if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                CompoundTag statsTag = weaponConfigs.getCompound(Objects.requireNonNull(key));
                WeaponStats stats = WeaponStats.load(statsTag);
                com.devmod.config.WeaponConfigManager.setGlobalStatsClientOnly(item, stats);
            }
        }
    }
}
