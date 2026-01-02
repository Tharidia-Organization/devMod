package com.devmod.telemetry.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Network payload for syncing LVC (Last Value Cache) telemetry data to clients.
 *
 * <p>Sent periodically (every 1-2 seconds) to update client-side HUDs with
 * real-time combat statistics from the server's LVC.
 *
 * <p>This is separate from QuestSyncPayload to allow independent sync rates
 * and avoid bloating the quest sync packet.
 */
public record LVCSyncPayload(
    // DPS metrics
    double currentDPS,
    double peakDPS,

    // Accuracy
    int hitCount,
    int missCount,
    int criticalHitCount,

    // Combat totals
    double totalDamage,
    int killCount,
    int deathCount,

    // Combo
    int currentCombo,
    int maxCombo,

    // Wave progress
    int highestWave,
    int wavesCompleted,

    // Ability usage
    int dashCount,
    int dodgeCount,
    int perfectDodgeCount,
    double totalDamageNegated,
    double totalStaminaSpent,

    // Top weapons (name:kills pairs, comma-separated)
    String topWeapons
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<LVCSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "lvc_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LVCSyncPayload> STREAM_CODEC = StreamCodec.of(
        LVCSyncPayload::encode,
        LVCSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, LVCSyncPayload payload) {
        buf.writeDouble(payload.currentDPS);
        buf.writeDouble(payload.peakDPS);
        buf.writeVarInt(payload.hitCount);
        buf.writeVarInt(payload.missCount);
        buf.writeVarInt(payload.criticalHitCount);
        buf.writeDouble(payload.totalDamage);
        buf.writeVarInt(payload.killCount);
        buf.writeVarInt(payload.deathCount);
        buf.writeVarInt(payload.currentCombo);
        buf.writeVarInt(payload.maxCombo);
        buf.writeVarInt(payload.highestWave);
        buf.writeVarInt(payload.wavesCompleted);
        buf.writeVarInt(payload.dashCount);
        buf.writeVarInt(payload.dodgeCount);
        buf.writeVarInt(payload.perfectDodgeCount);
        buf.writeDouble(payload.totalDamageNegated);
        buf.writeDouble(payload.totalStaminaSpent);
        buf.writeUtf(Objects.requireNonNull(payload.topWeapons));
    }

    private static final int MAX_STRING_LENGTH = 512;

    private static LVCSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new LVCSyncPayload(
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readUtf(MAX_STRING_LENGTH)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 8; // currentDPS
        size += 8; // peakDPS
        size += varIntSize(hitCount);
        size += varIntSize(missCount);
        size += varIntSize(criticalHitCount);
        size += 8; // totalDamage
        size += varIntSize(killCount);
        size += varIntSize(deathCount);
        size += varIntSize(currentCombo);
        size += varIntSize(maxCombo);
        size += varIntSize(highestWave);
        size += varIntSize(wavesCompleted);
        size += varIntSize(dashCount);
        size += varIntSize(dodgeCount);
        size += varIntSize(perfectDodgeCount);
        size += 8; // totalDamageNegated
        size += 8; // totalStaminaSpent
        size += estimatedUtfSize(topWeapons);
        return size;
    }

    /**
     * Create an empty payload (no data).
     */
    public static LVCSyncPayload empty() {
        return new LVCSyncPayload(
            0.0, 0.0,
            0, 0, 0,
            0.0, 0, 0,
            0, 0,
            0, 0,
            0, 0, 0, 0.0, 0.0,
            ""
        );
    }

    /**
     * Calculate accuracy from hit/miss counts.
     * @return accuracy between 0.0 and 1.0
     */
    public double getAccuracy() {
        int total = hitCount + missCount;
        return total > 0 ? (double) hitCount / total : 0.0;
    }

    /**
     * Calculate K/D ratio.
     * @return kills per death, or killCount if no deaths
     */
    public double getKDRatio() {
        return deathCount > 0 ? (double) killCount / deathCount : killCount;
    }

    /**
     * Check if any data is present.
     */
    public boolean hasData() {
        return hitCount > 0 || killCount > 0 || totalDamage > 0;
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
