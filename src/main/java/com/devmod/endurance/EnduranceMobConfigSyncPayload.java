package com.devmod.endurance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.config.ConfigScope;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.endurance.config.EnduranceMobPoolConfig;
import com.devmod.network.PayloadValidation;

/**
 * Payload for syncing Endurance mob configuration from client to server.
 *
 * Supports 3 permission levels:
 * - GLOBAL: OP 2+ only, permanently modifies server mob config
 * - PROPOSAL: Testers can propose changes for admin approval
 * - SESSION: Quest host can override mob settings for current session only
 *
 * Contains:
 * - List of per-mob config entries
 * - Global multipliers (health, damage, speed, elite chance)
 * - Affix weight overrides
 */
public record EnduranceMobConfigSyncPayload(
    List<MobConfigEntry> mobEntries,
    GlobalMobOverrides globalOverrides,
    ConfigScope scope
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * Per-mob configuration entry.
     */
    public record MobConfigEntry(
        String mobId,        // ResourceLocation.toString()
        boolean enabled,
        float health,
        float damage,
        float speed,
        float armor,
        int countPerWave,
        float countScaling,
        int maxPerWave,
        float eliteChance
    ) {
        private static final int MAX_MOB_ID_LENGTH = 128;

        public static MobConfigEntry decode(RegistryFriendlyByteBuf buf) {
            String mobId = buf.readUtf(MAX_MOB_ID_LENGTH);
            boolean enabled = buf.readBoolean();
            float health = buf.readFloat();
            float damage = buf.readFloat();
            float speed = buf.readFloat();
            float armor = buf.readFloat();
            int countPerWave = buf.readVarInt();
            float countScaling = buf.readFloat();
            int maxPerWave = buf.readVarInt();
            float eliteChance = buf.readFloat();
            return new MobConfigEntry(mobId, enabled, health, damage, speed, armor,
                countPerWave, countScaling, maxPerWave, eliteChance);
        }

        public void encode(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(Objects.requireNonNull(mobId));
            buf.writeBoolean(enabled);
            buf.writeFloat(health);
            buf.writeFloat(damage);
            buf.writeFloat(speed);
            buf.writeFloat(armor);
            buf.writeVarInt(countPerWave);
            buf.writeFloat(countScaling);
            buf.writeVarInt(maxPerWave);
            buf.writeFloat(eliteChance);
        }

        public int estimatedSize() {
            // String mobId + boolean + 6 floats + 2 VarInts
            return varIntSize(mobId.length()) + mobId.length()
                 + 1  // boolean enabled
                 + 4 * 6  // 6 floats: health, damage, speed, armor, countScaling, eliteChance
                 + varIntSize(countPerWave)
                 + varIntSize(maxPerWave);
        }

        /**
         * Create from EnduranceMobConfig.
         */
        public static MobConfigEntry fromConfig(EnduranceMobConfig config) {
            return new MobConfigEntry(
                config.mobId().toString(),
                config.enabled(),
                config.baseHealth(),
                config.baseDamage(),
                config.baseSpeed(),
                config.baseArmor(),
                config.baseCountPerWave(),
                config.countScalingPerWave(),
                config.maxPerWave(),
                config.eliteChance()
            );
        }

        /**
         * Convert to EnduranceMobConfig.
         */
        public EnduranceMobConfig toConfig() {
            String safeMobId = Objects.requireNonNull(mobId);
            ResourceLocation id = ResourceLocation.tryParse(safeMobId);
            if (id == null) {
                id = ResourceLocation.withDefaultNamespace(safeMobId);
            }
            return new EnduranceMobConfig(
                id, enabled, health, damage, speed, armor,
                countPerWave, countScaling, maxPerWave, eliteChance,
                EnduranceQuestRegistry.MobTier.MEDIUM,  // Default tier
                EnduranceQuestRegistry.MobDifficultyPreset.STANDARD  // Default preset
            );
        }

        private static int varIntSize(int value) {
            int size = 1;
            while ((value & ~0x7F) != 0) {
                value >>>= 7;
                size++;
            }
            return size;
        }
    }

    /**
     * Global mob overrides applied to all mobs.
     */
    public record GlobalMobOverrides(
        float healthMult,
        float damageMult,
        float speedMult,
        float eliteChanceMult,
        Map<String, Float> affixWeights  // SpawnAffix.name() -> weight
    ) {
        private static final int MAX_AFFIX_ENTRIES = 10;

        public static GlobalMobOverrides decode(RegistryFriendlyByteBuf buf) {
            float healthMult = buf.readFloat();
            float damageMult = buf.readFloat();
            float speedMult = buf.readFloat();
            float eliteChanceMult = buf.readFloat();

            int affixCount = buf.readVarInt();
            affixCount = Math.min(affixCount, MAX_AFFIX_ENTRIES);
            Map<String, Float> affixWeights = new HashMap<>(affixCount);
            for (int i = 0; i < affixCount; i++) {
                String affixName = buf.readUtf(32);
                float weight = buf.readFloat();
                affixWeights.put(affixName, weight);
            }

            return new GlobalMobOverrides(healthMult, damageMult, speedMult, eliteChanceMult, affixWeights);
        }

        public void encode(RegistryFriendlyByteBuf buf) {
            buf.writeFloat(healthMult);
            buf.writeFloat(damageMult);
            buf.writeFloat(speedMult);
            buf.writeFloat(eliteChanceMult);

            int count = Math.min(affixWeights.size(), MAX_AFFIX_ENTRIES);
            buf.writeVarInt(count);
            int written = 0;
            for (var entry : affixWeights.entrySet()) {
                if (written >= count) break;
                buf.writeUtf(Objects.requireNonNull(entry.getKey()));
                buf.writeFloat(entry.getValue());
                written++;
            }
        }

        public int estimatedSize() {
            int size = 4 * 4; // 4 floats
            size += varIntSize(affixWeights.size());
            for (var entry : affixWeights.entrySet()) {
                size += varIntSize(entry.getKey().length()) + entry.getKey().length() + 4;
            }
            return size;
        }

        /**
         * Create default overrides (all 1.0).
         */
        public static GlobalMobOverrides defaults() {
            return new GlobalMobOverrides(1.0f, 1.0f, 1.0f, 1.0f, Map.of());
        }

        /**
         * Create from EnduranceMobPoolConfig.
         */
        public static GlobalMobOverrides fromPoolConfig(EnduranceMobPoolConfig poolConfig) {
            Map<String, Float> affixWeights = new HashMap<>();
            for (SpawnAffix affix : SpawnAffix.values()) {
                float weight = poolConfig.getAffixWeight(affix);
                if (weight != 1.0f) {  // Only send non-default weights
                    affixWeights.put(affix.name(), weight);
                }
            }
            return new GlobalMobOverrides(
                poolConfig.getGlobalHealthMult(),
                poolConfig.getGlobalDamageMult(),
                poolConfig.getGlobalSpeedMult(),
                poolConfig.getGlobalEliteChanceMult(),
                affixWeights
            );
        }

        private static int varIntSize(int value) {
            int size = 1;
            while ((value & ~0x7F) != 0) {
                value >>>= 7;
                size++;
            }
            return size;
        }
    }

    public static final Type<EnduranceMobConfigSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "endurance_mob_config_sync"))
    );

    // Security limits
    private static final int MAX_MOB_ENTRIES = 200;

    public static final StreamCodec<RegistryFriendlyByteBuf, EnduranceMobConfigSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EnduranceMobConfigSyncPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            // Read scope
            int scopeOrdinal = buf.readVarInt();
            ConfigScope scope = ConfigScope.values()[Math.min(scopeOrdinal, ConfigScope.values().length - 1)];

            // Read global overrides
            GlobalMobOverrides globalOverrides = GlobalMobOverrides.decode(buf);

            // Read mob entries
            int entryCount = buf.readVarInt();
            entryCount = Math.min(entryCount, MAX_MOB_ENTRIES);

            List<MobConfigEntry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(MobConfigEntry.decode(buf));
            }

            return new EnduranceMobConfigSyncPayload(entries, globalOverrides, scope);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull EnduranceMobConfigSyncPayload payload) {
            // Write scope
            buf.writeVarInt(payload.scope.ordinal());

            // Write global overrides
            payload.globalOverrides.encode(buf);

            // Write mob entries (limit to MAX_MOB_ENTRIES)
            int count = Math.min(payload.mobEntries.size(), MAX_MOB_ENTRIES);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                payload.mobEntries.get(i).encode(buf);
            }
        }
    };

    /**
     * Create a payload with only global overrides (no per-mob configs).
     */
    public EnduranceMobConfigSyncPayload(GlobalMobOverrides globalOverrides, ConfigScope scope) {
        this(List.of(), globalOverrides, scope);
    }

    /**
     * Create from EnduranceMobPoolConfig.
     */
    public static EnduranceMobConfigSyncPayload fromPoolConfig(EnduranceMobPoolConfig poolConfig, ConfigScope scope) {
        List<MobConfigEntry> entries = new ArrayList<>();
        for (EnduranceMobConfig config : poolConfig.getAllMobConfigs()) {
            entries.add(MobConfigEntry.fromConfig(config));
        }
        GlobalMobOverrides globalOverrides = GlobalMobOverrides.fromPoolConfig(poolConfig);
        return new EnduranceMobConfigSyncPayload(entries, globalOverrides, scope);
    }

    /**
     * Convert to EnduranceMobPoolConfig.
     */
    public EnduranceMobPoolConfig toPoolConfig() {
        EnduranceMobPoolConfig poolConfig = new EnduranceMobPoolConfig();

        // Apply global overrides
        poolConfig.setGlobalHealthMult(globalOverrides.healthMult());
        poolConfig.setGlobalDamageMult(globalOverrides.damageMult());
        poolConfig.setGlobalSpeedMult(globalOverrides.speedMult());
        poolConfig.setGlobalEliteChanceMult(globalOverrides.eliteChanceMult());

        // Apply affix weights
        for (var entry : globalOverrides.affixWeights().entrySet()) {
            try {
                SpawnAffix affix = SpawnAffix.valueOf(entry.getKey());
                poolConfig.setAffixWeight(affix, entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Unknown affix name, skip
            }
        }

        // Apply per-mob configs
        for (MobConfigEntry entry : mobEntries) {
            poolConfig.setMobConfig(entry.toConfig());
        }

        return poolConfig;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // scope ordinal
        size += globalOverrides.estimatedSize();
        size += varIntSize(mobEntries.size());
        for (MobConfigEntry entry : mobEntries) {
            size += entry.estimatedSize();
        }
        return size;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}
