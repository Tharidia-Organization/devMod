package com.devmod.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.stats.ArmorStats;

/**
 * Network payload for updating armor stats from the editor UI.
 * Sent from client to server when a player modifies armor attributes.
 *
 * <p><b>CRITICAL:</b> The record field order MUST match the encode/decode order in STREAM_CODEC.
 * If fields are reordered, added, or removed, update the codec accordingly to prevent
 * network deserialization errors.</p>
 *
 * <p>Field order: isGlobal, slot, physicalReduction, fireReduction, magicReduction,
 * explosionReduction, projectileReduction, armorBonus, toughnessBonus, knockbackResistance,
 * thornsEnabled, thornsPercent, shieldReflect, shieldBlockStrength, shieldRecovery, itemName</p>
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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

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
        stats.setPhysicalReduction(physicalReduction);
        stats.setFireReduction(fireReduction);
        stats.setMagicReduction(magicReduction);
        stats.setExplosionReduction(explosionReduction);
        stats.setProjectileReduction(projectileReduction);
        stats.setArmorBonus(armorBonus);
        stats.setToughnessBonus(toughnessBonus);
        stats.setKnockbackResistance(knockbackResistance);
        stats.setThornsReflect(thornsEnabled);
        stats.setThornsPercent(thornsPercent);
        stats.setShieldReflectProjectiles(shieldReflect);
        stats.setShieldBlockStrength(shieldBlockStrength);
        stats.setShieldRecoverySpeed(shieldRecovery);
        return stats;
    }

    /**
     * Create a payload from ArmorStats.
     */
    public static UpdateArmorPayload fromArmorStats(boolean isGlobal, int slot, ArmorStats stats, String itemName) {
        return new UpdateArmorPayload(
            isGlobal,
            slot,
            stats.getPhysicalReduction(),
            stats.getFireReduction(),
            stats.getMagicReduction(),
            stats.getExplosionReduction(),
            stats.getProjectileReduction(),
            stats.getArmorBonus(),
            stats.getToughnessBonus(),
            stats.getKnockbackResistance(),
            stats.isThornsReflect(),
            stats.getThornsPercent(),
            stats.isShieldReflectProjectiles(),
            stats.getShieldBlockStrength(),
            stats.getShieldRecoverySpeed(),
            itemName
        );
    }

    @Override
    public int estimatedSize() {
        // 3 bools + 1 VarInt + 11 floats + string (max 256 chars)
        // = 3 + 5 + 44 + itemName length = ~52 + itemName
        return 52 + (itemName != null ? itemName.length() : 0);
    }
}
