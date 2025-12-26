package com.devmod.endurance.contracts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.config.gamedesign.GameDesignConfig;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.endurance.ComboSystem;

/**
 * Registry of all available Blood Contracts.
 *
 * Contracts are organized by tier and effect type.
 * The system offers 3 random contracts per wave checkpoint.
 */
public final class BloodContractRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BloodContractRegistry.class);

    public static final BloodContractRegistry INSTANCE = new BloodContractRegistry();

    private final Map<ResourceLocation, BloodContract> contracts = new LinkedHashMap<>();
    private final Map<BloodContract.ContractTier, List<BloodContract>> byTier = new EnumMap<>(BloodContract.ContractTier.class);
    private final Random random = new Random();

    private BloodContractRegistry() {
        registerBuiltinContracts();
    }

    /**
     * Get config for the given quest instance.
     */
    private GameDesignConfig.ContractsConfig getConfig(@Nullable UUID questId) {
        return GameDesignConfigManager.INSTANCE.getContractsConfig(questId);
    }

    // ========== Registration ==========

    public void register(BloodContract contract) {
        contracts.put(contract.getId(), contract);
        byTier.computeIfAbsent(contract.getTier(), k -> new ArrayList<>()).add(contract);
        LOGGER.debug("[BloodContracts] Registered: {} ({}x)", contract.getId(), contract.getTotalMultiplier());
    }

    @Nullable
    public BloodContract get(ResourceLocation id) {
        return contracts.get(id);
    }

    @Nonnull
    public Collection<BloodContract> getAll() {
        return Collections.unmodifiableCollection(contracts.values());
    }

    @Nonnull
    public List<BloodContract> getByTier(BloodContract.ContractTier tier) {
        return byTier.getOrDefault(tier, Collections.emptyList());
    }

    // ========== Contract Selection ==========

    /**
     * Get a random selection of contracts for a wave checkpoint.
     *
     * @param count Number of contracts to offer
     * @param waveNumber Current wave (affects tier availability)
     * @param excludeIds Contracts to exclude (already signed, etc.)
     * @param questId Quest ID for config lookup (nullable)
     * @return List of contract offers
     */
    @Nonnull
    public List<BloodContract> getRandomOffers(int count, int waveNumber, Set<ResourceLocation> excludeIds,
                                                @Nullable UUID questId) {
        // Check if contracts are enabled
        GameDesignConfig.ContractsConfig config = getConfig(questId);
        if (!config.enabled) {
            return Collections.emptyList();
        }

        List<BloodContract> available = new ArrayList<>();

        // Filter by wave progression (using config thresholds)
        for (BloodContract contract : contracts.values()) {
            if (excludeIds.contains(contract.getId())) continue;

            // BLOOD tier only after configured wave threshold
            if (contract.getTier() == BloodContract.ContractTier.BLOOD
                && waveNumber < config.bloodTierMinWave) continue;

            // MAJOR tier only after configured wave threshold
            if (contract.getTier() == BloodContract.ContractTier.MAJOR
                && waveNumber < config.majorTierMinWave) continue;

            available.add(contract);
        }

        // Shuffle and take
        Collections.shuffle(available, random);
        return available.subList(0, Math.min(count, available.size()));
    }

    /**
     * Get a random selection of contracts (backward compatible, no questId).
     * @deprecated Use {@link #getRandomOffers(int, int, Set, UUID)} instead.
     */
    @Deprecated
    @Nonnull
    public List<BloodContract> getRandomOffers(int count, int waveNumber, Set<ResourceLocation> excludeIds) {
        return getRandomOffers(count, waveNumber, excludeIds, null);
    }

    /**
     * Get contracts compatible with currently active contracts.
     */
    @Nonnull
    public List<BloodContract> getCompatibleOffers(int count, int waveNumber, List<BloodContract> activeContracts) {
        Set<ResourceLocation> excludeIds = new HashSet<>();
        for (BloodContract active : activeContracts) {
            excludeIds.add(active.getId());
        }

        List<BloodContract> offers = new ArrayList<>();
        List<BloodContract> candidates = getRandomOffers(count * 3, waveNumber, excludeIds);

        for (BloodContract candidate : candidates) {
            boolean compatible = true;
            for (BloodContract active : activeContracts) {
                if (!candidate.isCompatibleWith(active)) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                offers.add(candidate);
                if (offers.size() >= count) break;
            }
        }

        return offers;
    }

    // ========== Builtin Contracts ==========

    private void registerBuiltinContracts() {
        // ========== MINOR TIER ==========

        register(BloodContract.builder(loc("aggression"))
            .tier(BloodContract.ContractTier.MINOR)
            .effect(BloodContract.ContractEffect.GLASS_CANNON)
            .rewardMultiplier(1.5f)
            .build());

        register(BloodContract.builder(loc("blindfold"))
            .tier(BloodContract.ContractTier.MINOR)
            .effect(BloodContract.ContractEffect.BLINDFOLD)
            .rewardMultiplier(1.25f)
            .build());

        register(BloodContract.builder(loc("silence"))
            .tier(BloodContract.ContractTier.MINOR)
            .effect(BloodContract.ContractEffect.SILENCE)
            .rewardMultiplier(1.3f)
            .build());

        // ========== STANDARD TIER ==========

        register(BloodContract.builder(loc("vampire"))
            .tier(BloodContract.ContractTier.STANDARD)
            .effect(BloodContract.ContractEffect.VAMPIRE)
            .rewardMultiplier(1.5f)
            .build());

        register(BloodContract.builder(loc("pacifist"))
            .tier(BloodContract.ContractTier.STANDARD)
            .effect(BloodContract.ContractEffect.PACIFIST)
            .rewardMultiplier(1.75f)
            .build());

        register(BloodContract.builder(loc("haste"))
            .tier(BloodContract.ContractTier.STANDARD)
            .effect(BloodContract.ContractEffect.HASTE)
            .rewardMultiplier(1.5f)
            .violationCheck((player, ctx) -> {
                long elapsed = ctx.currentTime() - ctx.waveStartTime();
                return elapsed > 60_000; // 60 seconds
            })
            .build());

        register(BloodContract.builder(loc("berserker"))
            .tier(BloodContract.ContractTier.STANDARD)
            .effect(BloodContract.ContractEffect.BERSERKER)
            .rewardMultiplier(1.6f)
            .build());

        // ========== MAJOR TIER ==========

        register(BloodContract.builder(loc("untouchable"))
            .tier(BloodContract.ContractTier.MAJOR)
            .effect(BloodContract.ContractEffect.UNTOUCHABLE)
            .rewardMultiplier(2.0f)
            .violationCheck((player, ctx) -> ctx.hitsTaken() > 3)
            .build());

        register(BloodContract.builder(loc("blitz"))
            .tier(BloodContract.ContractTier.MAJOR)
            .effect(BloodContract.ContractEffect.BLITZ)
            .rewardMultiplier(2.5f)
            .violationCheck((player, ctx) -> {
                long elapsed = ctx.currentTime() - ctx.waveStartTime();
                return elapsed > 30_000; // 30 seconds
            })
            .build());

        register(BloodContract.builder(loc("sacrifice"))
            .tier(BloodContract.ContractTier.MAJOR)
            .effect(BloodContract.ContractEffect.SACRIFICE)
            .rewardMultiplier(1.75f)
            .partyContract()
            .build());

        register(BloodContract.builder(loc("linked_fate"))
            .tier(BloodContract.ContractTier.MAJOR)
            .effect(BloodContract.ContractEffect.LINKED_FATE)
            .rewardMultiplier(2.0f)
            .partyContract()
            .violationCheck((player, ctx) -> ctx.playerDied())
            .build());

        // ========== BLOOD TIER ==========

        register(BloodContract.builder(loc("glass"))
            .tier(BloodContract.ContractTier.BLOOD)
            .effect(BloodContract.ContractEffect.GLASS_CANNON)
            .rewardMultiplier(3.0f)
            .violationCheck((player, ctx) -> ctx.hitsTaken() > 0)
            .build());

        register(BloodContract.builder(loc("hubris"))
            .tier(BloodContract.ContractTier.BLOOD)
            .effect(BloodContract.ContractEffect.HUBRIS)
            .rewardMultiplier(2.5f)
            .violationCheck((player, ctx) -> {
                // S rank is ordinal 4 (D=0, C=1, B=2, A=3, S=4, SS=5, SSS=6)
                return ctx.currentStyleRank() < ComboSystem.StyleRank.S.ordinal();
            })
            .build());

        register(BloodContract.builder(loc("perfectionist"))
            .tier(BloodContract.ContractTier.BLOOD)
            .effect(BloodContract.ContractEffect.PERFECTIONIST)
            .rewardMultiplier(3.5f)
            .violationCheck((player, ctx) -> ctx.damageTaken() > 0)
            .build());

        register(BloodContract.builder(loc("blood_pact"))
            .tier(BloodContract.ContractTier.BLOOD)
            .effect(BloodContract.ContractEffect.BLOOD_PACT)
            .rewardMultiplier(2.0f)
            .partyContract()
            .build());

        LOGGER.info("[BloodContracts] Registered {} contracts", contracts.size());
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, path);
    }
}
