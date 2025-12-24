package com.devmod.network;

import com.devmod.stats.ArmorStats;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Network payload for updating armor statistics.
 * Sent from ItemEditorScreen (Armor module) to server.
 *
 * Supports both global (per-item-type) and specific (per-item-instance) configurations.
 */
public record UpdateArmorPayload(
    boolean isGlobal,           // true = global config, false = specific item
    int slot,                   // Equipment slot index (0=HEAD, 1=CHEST, 2=LEGS, 3=FEET)
    float physicalReduction,    // Physical damage reduction (0.0 - 1.0)
    float fireReduction,        // Fire damage reduction (0.0 - 1.0)
    float magicReduction,       // Magic damage reduction (0.0 - 1.0)
    float explosionReduction,   // Explosion damage reduction (0.0 - 1.0)
    float projectileReduction,  // Projectile damage reduction (0.0 - 1.0)
    float armorBonus,           // Bonus armor value
    float toughnessBonus,       // Bonus toughness
    float knockbackResistance,  // Knockback resistance (0.0 - 1.0)
    boolean thornsEnabled,      // Enable thorns reflection
    float thornsPercent,        // Thorns reflection percentage (0.0 - 0.5)
    boolean shieldReflect,      // Shield reflect projectiles
    float shieldBlockStrength,  // Shield block strength (0.0 - 1.0)
    float shieldRecovery,       // Shield recovery speed multiplier
    String itemName             // Item registry name (for global config key)
) implements CustomPacketPayload {

    public static final Type<UpdateArmorPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "update_armor"))
    );

    // StreamCodec using RegistryFriendlyByteBuf for NeoForge 1.21
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateArmorPayload> STREAM_CODEC =
        StreamCodec.of(UpdateArmorPayload::encode, UpdateArmorPayload::decode);

    private static void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull UpdateArmorPayload payload) {
        buf.writeBoolean(payload.isGlobal());
        buf.writeVarInt(payload.slot());
        buf.writeFloat(payload.physicalReduction());
        buf.writeFloat(payload.fireReduction());
        buf.writeFloat(payload.magicReduction());
        buf.writeFloat(payload.explosionReduction());
        buf.writeFloat(payload.projectileReduction());
        buf.writeFloat(payload.armorBonus());
        buf.writeFloat(payload.toughnessBonus());
        buf.writeFloat(payload.knockbackResistance());
        buf.writeBoolean(payload.thornsEnabled());
        buf.writeFloat(payload.thornsPercent());
        buf.writeBoolean(payload.shieldReflect());
        buf.writeFloat(payload.shieldBlockStrength());
        buf.writeFloat(payload.shieldRecovery());
        buf.writeUtf(Objects.requireNonNull(payload.itemName()), 256);
    }

    @Nonnull
    private static UpdateArmorPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
        return new UpdateArmorPayload(
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readFloat(),
            buf.readFloat(),
            Objects.requireNonNull(buf.readUtf(256))
        );
    }

    // Canonical constructor with null safety
    public UpdateArmorPayload {
        itemName = itemName == null ? "" : itemName;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create an ArmorStats instance from this payload.
     */
    public ArmorStats toArmorStats() {
        ArmorStats stats = new ArmorStats();
        stats.physicalReduction = physicalReduction;
        stats.fireReduction = fireReduction;
        stats.magicReduction = magicReduction;
        stats.explosionReduction = explosionReduction;
        stats.projectileReduction = projectileReduction;
        stats.armorBonus = armorBonus;
        stats.toughnessBonus = toughnessBonus;
        stats.knockbackResistance = knockbackResistance;
        stats.thornsReflect = thornsEnabled;
        stats.thornsPercent = thornsPercent;
        stats.shieldReflectProjectiles = shieldReflect;
        stats.shieldBlockStrength = shieldBlockStrength;
        stats.shieldRecoverySpeed = shieldRecovery;
        return stats;
    }

    /**
     * Create a payload from ArmorStats.
     */
    public static UpdateArmorPayload fromArmorStats(boolean isGlobal, int slot, ArmorStats stats, String itemName) {
        return new UpdateArmorPayload(
            isGlobal,
            slot,
            stats.physicalReduction,
            stats.fireReduction,
            stats.magicReduction,
            stats.explosionReduction,
            stats.projectileReduction,
            stats.armorBonus,
            stats.toughnessBonus,
            stats.knockbackResistance,
            stats.thornsReflect,
            stats.thornsPercent,
            stats.shieldReflectProjectiles,
            stats.shieldBlockStrength,
            stats.shieldRecoverySpeed,
            itemName
        );
    }
}
