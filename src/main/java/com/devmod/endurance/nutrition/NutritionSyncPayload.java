package com.devmod.endurance.nutrition;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Syncs nutrition state from server to client for HUD display.
 *
 * <p>Sent when:
 * <ul>
 *   <li>Player joins an Endurance quest</li>
 *   <li>Nutrition values change significantly</li>
 *   <li>Player eats food during quest</li>
 * </ul>
 *
 * @author DevMod Team
 */
public record NutritionSyncPayload(
    float[] values,           // 6 nutrition category values (0.0-1.0)
    float balance,            // Overall nutrition balance (0.0-1.0)
    float damageMultiplier,   // Current damage multiplier
    float speedMultiplier,    // Current speed multiplier
    float damageReduction,    // Current damage reduction
    byte flags                // Bit flags: 0=wellFed, 1=malnourished, 2=critical, 3=healingAllowed
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    // FIX AUDIT #6: Logger for clamp warnings
    private static final Logger LOGGER = LoggerFactory.getLogger(NutritionSyncPayload.class);

    /** Number of nutrition categories. */
    public static final int CATEGORY_COUNT = 6;

    // FIX AUDIT2 #6: Pre-computed field names to avoid string concatenation in hot path
    private static final String[] CATEGORY_FIELD_NAMES = {
        "category[0]", "category[1]", "category[2]",
        "category[3]", "category[4]", "category[5]"
    };

    // FIX AUDIT2 #3: Rate limiting for clamp warnings to prevent log spam
    // Only log once per field per 5-second window
    private static final long WARN_COOLDOWN_MS = 5000;
    private static final long[] lastWarnTime = new long[10];  // 6 categories + 4 other fields

    public static final CustomPacketPayload.Type<NutritionSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nutrition_sync")));

    public static final StreamCodec<FriendlyByteBuf, NutritionSyncPayload> STREAM_CODEC =
        StreamCodec.of(NutritionSyncPayload::write, NutritionSyncPayload::read);

    /**
     * Create payload from a NutritionSession.
     */
    public static NutritionSyncPayload fromSession(NutritionSession session) {
        float[] values = new float[CATEGORY_COUNT];
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            values[i] = session.getValue(i);
        }

        byte flags = 0;
        if (session.isWellFed()) flags |= 0x01;
        if (session.isMalnourished()) flags |= 0x02;
        if (session.isCritical()) flags |= 0x04;
        if (session.isHealingAllowed()) flags |= 0x08;

        return new NutritionSyncPayload(
            values,
            session.getBalance(),
            session.getDamageMultiplier(),
            session.getSpeedMultiplier(),
            session.getDamageReduction(),
            flags
        );
    }

    /**
     * Create an empty/neutral payload for players without nutrition tracking.
     * Uses 0.5 for neutral state (no buff/debuff).
     */
    public static NutritionSyncPayload empty() {
        float[] values = new float[CATEGORY_COUNT];
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            values[i] = 0.5f; // Neutral value (no buff/debuff)
        }
        return new NutritionSyncPayload(
            values,
            0.5f,  // balance (neutral)
            1.0f,  // damageMultiplier (neutral)
            1.0f,  // speedMultiplier (neutral)
            0.0f,  // damageReduction (none)
            (byte) 0x08 // healingAllowed = true
        );
    }

    private static NutritionSyncPayload read(FriendlyByteBuf buf) {
        float[] values = new float[CATEGORY_COUNT];
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            // FIX AUDIT2 #6: Use pre-computed field names to avoid string concatenation
            values[i] = clampWithWarning(buf.readFloat(), 0f, 1f, CATEGORY_FIELD_NAMES[i], i);
        }
        // Field indices 6-9 for non-category fields
        float balance = clampWithWarning(buf.readFloat(), 0f, 1f, "balance", 6);
        float damageMultiplier = clampWithWarning(buf.readFloat(), 0f, 3f, "damageMultiplier", 7);
        float speedMultiplier = clampWithWarning(buf.readFloat(), 0f, 3f, "speedMultiplier", 8);
        float damageReduction = clampWithWarning(buf.readFloat(), -1f, 1f, "damageReduction", 9);
        byte flags = buf.readByte();

        return new NutritionSyncPayload(values, balance, damageMultiplier, speedMultiplier, damageReduction, flags);
    }

    /**
     * FIX AUDIT #6: Clamp a value to a range with warning log if clamping occurs.
     * FIX AUDIT2 #3: Rate-limited to prevent log spam from buggy servers.
     *
     * @param value the value to clamp
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @param fieldName field name for logging
     * @param fieldIndex index into lastWarnTime array for rate limiting
     * @return clamped value
     */
    private static float clampWithWarning(float value, float min, float max, String fieldName, int fieldIndex) {
        if (value < min || value > max) {
            // FIX AUDIT2 #3: Rate limit warnings to prevent log spam
            long now = System.currentTimeMillis();
            if (now - lastWarnTime[fieldIndex] >= WARN_COOLDOWN_MS) {
                lastWarnTime[fieldIndex] = now;
                LOGGER.warn("[NutritionSync] Clamping {} from {} to [{}, {}] - possible server bug",
                    fieldName, value, min, max);
            }
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }

    private static void write(FriendlyByteBuf buf, NutritionSyncPayload payload) {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            buf.writeFloat(payload.values[i]);
        }
        buf.writeFloat(payload.balance);
        buf.writeFloat(payload.damageMultiplier);
        buf.writeFloat(payload.speedMultiplier);
        buf.writeFloat(payload.damageReduction);
        buf.writeByte(payload.flags);
    }

    // =========================================================================
    // FLAG ACCESSORS
    // =========================================================================

    public boolean isWellFed() {
        return (flags & 0x01) != 0;
    }

    public boolean isMalnourished() {
        return (flags & 0x02) != 0;
    }

    public boolean isCritical() {
        return (flags & 0x04) != 0;
    }

    public boolean isHealingAllowed() {
        return (flags & 0x08) != 0;
    }

    // =========================================================================
    // CATEGORY ACCESSORS
    // =========================================================================

    /**
     * Get value for a specific category.
     * @param index category index (0-5)
     * @return value 0.0-1.0
     */
    public float getValue(int index) {
        if (index < 0 || index >= CATEGORY_COUNT) {
            return 0f;
        }
        return values[index];
    }

    /**
     * Get value for a specific category.
     * @param category the category
     * @return value 0.0-1.0
     */
    public float getValue(NutritionCategory category) {
        return getValue(category.getIndex());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // 6 floats (24) + 4 floats (16) + 1 byte = 41 bytes
        return 41;
    }
}
