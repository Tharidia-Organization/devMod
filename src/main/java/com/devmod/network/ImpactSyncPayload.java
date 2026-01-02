package com.devmod.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload for synchronizing impact data in multiplayer.
 * Sent when a player deals damage to update their Impact HUD.
 */
public record ImpactSyncPayload(
    int victimEntityId,
    int bodyPartOrdinal,  // HitHelper.BodyPart ordinal
    float multiplier,
    // Damage breakdown
    float baseWeaponDamage,
    float enchantBonus,
    float pehkuiBonus,
    float bodyPartMultiplier,
    float armorPenBonus,
    float finalDamage,
    // Attack info
    String attackSource,
    boolean isRanged,
    // Hit position (nullable)
    boolean hasHitPoint,
    double hitX,
    double hitY,
    double hitZ,
    // Slash direction (nullable)
    boolean hasSlashDir,
    double slashX,
    double slashY,
    double slashZ,
    // Actual damage dealt
    float actualDamage
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ImpactSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "impact_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            buffer.writeVarInt(val.victimEntityId());
            buffer.writeVarInt(val.bodyPartOrdinal());
            buffer.writeFloat(val.multiplier());
            // Damage breakdown
            buffer.writeFloat(val.baseWeaponDamage());
            buffer.writeFloat(val.enchantBonus());
            buffer.writeFloat(val.pehkuiBonus());
            buffer.writeFloat(val.bodyPartMultiplier());
            buffer.writeFloat(val.armorPenBonus());
            buffer.writeFloat(val.finalDamage());
            // Attack info
            buffer.writeUtf(Objects.requireNonNull(val.attackSource()));
            buffer.writeBoolean(val.isRanged());
            // Hit point
            buffer.writeBoolean(val.hasHitPoint());
            if (val.hasHitPoint()) {
                buffer.writeDouble(val.hitX());
                buffer.writeDouble(val.hitY());
                buffer.writeDouble(val.hitZ());
            }
            // Slash direction
            buffer.writeBoolean(val.hasSlashDir());
            if (val.hasSlashDir()) {
                buffer.writeDouble(val.slashX());
                buffer.writeDouble(val.slashY());
                buffer.writeDouble(val.slashZ());
            }
            buffer.writeFloat(val.actualDamage());
        },
        buffer -> {
            int victimId = buffer.readVarInt();
            int bodyPart = buffer.readVarInt();
            float mult = buffer.readFloat();
            float baseDmg = buffer.readFloat();
            float enchant = buffer.readFloat();
            float pehkui = buffer.readFloat();
            float bpMult = buffer.readFloat();
            float armorPen = buffer.readFloat();
            float finalDmg = buffer.readFloat();
            String src = buffer.readUtf();
            boolean ranged = buffer.readBoolean();
            boolean hasHit = buffer.readBoolean();
            double hx = 0, hy = 0, hz = 0;
            if (hasHit) {
                hx = buffer.readDouble();
                hy = buffer.readDouble();
                hz = buffer.readDouble();
            }
            boolean hasSlash = buffer.readBoolean();
            double sx = 0, sy = 0, sz = 0;
            if (hasSlash) {
                sx = buffer.readDouble();
                sy = buffer.readDouble();
                sz = buffer.readDouble();
            }
            float actual = buffer.readFloat();
            return new ImpactSyncPayload(
                victimId, bodyPart, mult,
                baseDmg, enchant, pehkui, bpMult, armorPen, finalDmg,
                src, ranged,
                hasHit, hx, hy, hz,
                hasSlash, sx, sy, sz,
                actual
            );
        }
    );

    @Override
    @Nonnull
    public Type<ImpactSyncPayload> type() {
        return Objects.requireNonNull(TYPE, "payload type");
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += varIntSize(victimEntityId);
        size += varIntSize(bodyPartOrdinal);
        size += 4; // multiplier
        size += 4; // baseWeaponDamage
        size += 4; // enchantBonus
        size += 4; // pehkuiBonus
        size += 4; // bodyPartMultiplier
        size += 4; // armorPenBonus
        size += 4; // finalDamage
        size += estimatedUtfSize(attackSource);
        size += 1; // isRanged
        size += 1; // hasHitPoint
        if (hasHitPoint) {
            size += 8 * 3;
        }
        size += 1; // hasSlashDir
        if (hasSlashDir) {
            size += 8 * 3;
        }
        size += 4; // actualDamage
        return size;
    }

    /**
     * Creates an ImpactSyncPayload from damage event data.
     */
    public static ImpactSyncPayload create(
            int victimEntityId,
            int bodyPartOrdinal,
            float multiplier,
            float baseWeaponDamage,
            float enchantBonus,
            float pehkuiBonus,
            float bodyPartMultiplier,
            float armorPenBonus,
            float finalDamage,
            String attackSource,
            boolean isRanged,
            @Nullable net.minecraft.world.phys.Vec3 hitPoint,
            @Nullable net.minecraft.world.phys.Vec3 slashDirection,
            float actualDamage
    ) {
        boolean hasHit = hitPoint != null;
        boolean hasSlash = slashDirection != null;
        double hx = 0, hy = 0, hz = 0;
        if (hitPoint != null) {
            hx = hitPoint.x;
            hy = hitPoint.y;
            hz = hitPoint.z;
        }
        double sx = 0, sy = 0, sz = 0;
        if (slashDirection != null) {
            sx = slashDirection.x;
            sy = slashDirection.y;
            sz = slashDirection.z;
        }
        return new ImpactSyncPayload(
            victimEntityId, bodyPartOrdinal, multiplier,
            baseWeaponDamage, enchantBonus, pehkuiBonus, bodyPartMultiplier, armorPenBonus, finalDamage,
            attackSource, isRanged,
            hasHit, hx, hy, hz,
            hasSlash, sx, sy, sz,
            actualDamage
        );
    }

    /**
     * Gets the hit point as a Vec3, or null if not present.
     */
    @Nullable
    public net.minecraft.world.phys.Vec3 getHitPoint() {
        return hasHitPoint ? new net.minecraft.world.phys.Vec3(hitX, hitY, hitZ) : null;
    }

    /**
     * Gets the slash direction as a Vec3, or null if not present.
     */
    @Nullable
    public net.minecraft.world.phys.Vec3 getSlashDirection() {
        return hasSlashDir ? new net.minecraft.world.phys.Vec3(slashX, slashY, slashZ) : null;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
