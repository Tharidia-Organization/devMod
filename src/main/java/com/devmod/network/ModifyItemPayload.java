package com.devmod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;

/**
 * Payload for comprehensive item modification.
 * NeoForge 1.21.1 compliant using StreamCodec.composite and ByteBufCodecs.collection.
 *
 * Supports:
 * - Durability changes
 * - Unbreakable flag
 * - Repair cost
 * - Enchantment modifications (add/remove/change level) - works with ALL modded enchantments
 * - Attribute modifier changes - works with ALL modded attributes
 *
 * Format for enchantments: "namespace:path:level" (e.g., "minecraft:sharpness:5")
 * Format for attributes: "namespace:path:value:operation" (e.g., "minecraft:generic.attack_damage:5.0:0")
 */
public record ModifyItemPayload(
    int durability,
    boolean unbreakable,
    int repairCost,
    List<String> enchantmentChanges,
    List<String> attributeChanges
) implements CustomPacketPayload {

    // Max limits for security
    private static final int MAX_ENCHANTMENTS = 64;
    private static final int MAX_ATTRIBUTES = 32;

    public static final Type<ModifyItemPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "modify_item"))
    );

    // NeoForge 1.21.1 compliant StreamCodec using composite pattern
    public static final StreamCodec<ByteBuf, ModifyItemPayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), ModifyItemPayload::durability,
        Objects.requireNonNull(ByteBufCodecs.BOOL), ModifyItemPayload::unbreakable,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), ModifyItemPayload::repairCost,
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.apply(Objects.requireNonNull(ByteBufCodecs.list(MAX_ENCHANTMENTS)))), ModifyItemPayload::enchantmentChanges,
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.apply(Objects.requireNonNull(ByteBufCodecs.list(MAX_ATTRIBUTES)))), ModifyItemPayload::attributeChanges,
        (durability, unbreakable, repairCost, enchantmentChanges, attributeChanges) ->
            new ModifyItemPayload(durability, unbreakable, repairCost, enchantmentChanges, attributeChanges)
    );

    public ModifyItemPayload {
        enchantmentChanges = enchantmentChanges != null ? enchantmentChanges : new ArrayList<>();
        attributeChanges = attributeChanges != null ? attributeChanges : new ArrayList<>();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Helper to create enchantment change string.
     * @param enchantmentId Full enchantment ID (e.g., "minecraft:sharpness")
     * @param level New level (0 to remove)
     */
    public static String enchantmentChange(String enchantmentId, int level) {
        return enchantmentId + ":" + level;
    }

    /**
     * Helper to create attribute change string.
     * @param attributeId Full attribute ID (e.g., "minecraft:generic.attack_damage")
     * @param value The value
     * @param operation 0=ADD, 1=MULTIPLY_BASE, 2=MULTIPLY_TOTAL
     */
    public static String attributeChange(String attributeId, double value, int operation) {
        return attributeId + ":" + value + ":" + operation;
    }
}
