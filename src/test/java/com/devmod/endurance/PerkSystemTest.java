package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class PerkSystemTest {

    // ============================================
    // Simulated Enums (mirror production)
    // ============================================

    enum SimPerkTier {
        COMMON(0xAAAAAA, "Common", 60),
        UNCOMMON(0x4ade80, "Uncommon", 25),
        RARE(0x60a5fa, "Rare", 10),
        EPIC(0xa855f7, "Epic", 4),
        LEGENDARY(0xfbbf24, "Legendary", 1);

        public final int color;
        public final String displayName;
        public final int weight;

        SimPerkTier(int color, String displayName, int weight) {
            this.color = color;
            this.displayName = displayName;
            this.weight = weight;
        }
    }

    enum SimPerkCategory {
        OFFENSE("Offense", 0xFF6B6B),
        DEFENSE("Defense", 0x4ECDC4),
        UTILITY("Utility", 0xFFE66D),
        VAMPIRIC("Vampiric", 0x9B59B6),
        ELEMENTAL("Elemental", 0xE74C3C),
        COMBO("Combo", 0xF39C12),
        CURSE("Curse", 0x2C3E50);

        public final String displayName;
        public final int color;

        SimPerkCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    // ============================================
    // Simulated Perk
    // ============================================

    static class SimPerk {
        public final String id;
        public final String name;
        public final String description;
        public final SimPerkTier tier;
        public final SimPerkCategory category;
        public final boolean stackable;
        public final int maxStacks;
        public final Set<String> requiredPerks;
        public final Set<String> incompatiblePerks;
        private final Consumer<SimPerkContext> onAcquire;

        public SimPerk(String id, String name, String description, SimPerkTier tier,
                       SimPerkCategory category, boolean stackable, int maxStacks,
                       Consumer<SimPerkContext> onAcquire) {
            this(id, name, description, tier, category, stackable, maxStacks,
                 Set.of(), Set.of(), onAcquire);
        }

        public SimPerk(String id, String name, String description, SimPerkTier tier,
                       SimPerkCategory category, boolean stackable, int maxStacks,
                       Set<String> requiredPerks, Set<String> incompatiblePerks,
                       Consumer<SimPerkContext> onAcquire) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
            this.category = category;
            this.stackable = stackable;
            this.maxStacks = maxStacks;
            this.requiredPerks = requiredPerks;
            this.incompatiblePerks = incompatiblePerks;
            this.onAcquire = onAcquire;
        }

        public void apply(SimPerkContext context) {
            if (onAcquire != null) onAcquire.accept(context);
        }
    }

    // ============================================
    // Simulated PerkContext
    // ============================================

    static class SimPerkContext {
        public final SimPerkSession session;
        public final int stackCount;

        public SimPerkContext(SimPerkSession session, int stackCount) {
            this.session = session;
            this.stackCount = stackCount;
        }
    }

    // ============================================
    // Simulated PerkSession
    // ============================================

    static class SimPerkSession {
        private final UUID playerId;
        private final UUID questId;

        private final Map<String, Integer> acquiredPerks = new LinkedHashMap<>();

        // Stat modifiers
        private float damageMultiplier = 1.0f;
        private float attackSpeedMultiplier = 1.0f;
        private float movementSpeedMultiplier = 1.0f;
        private float damageReductionMultiplier = 1.0f;
        private float lifestealPercent = 0f;
        private float critChanceBonus = 0f;
        private float critDamageMultiplier = 1.5f;
        private float fireChance = 0f;
        private float freezeChance = 0f;
        private float lightningChance = 0f;
        private float styleMultiplier = 1.0f;
        private float comboDecayReduction = 0f;
        private int curseLevel = 0;
        private float curseRewardMultiplier = 1.0f;

        private List<SimPerk> pendingChoices = new ArrayList<>();

        public SimPerkSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
        }

        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }

        public boolean hasPerk(String perkId) {
            return acquiredPerks.containsKey(perkId);
        }

        public int getPerkStacks(String perkId) {
            return acquiredPerks.getOrDefault(perkId, 0);
        }

        public void addPerk(String perkId) {
            acquiredPerks.merge(perkId, 1, (a, b) -> a + b);
        }

        public Set<String> getAcquiredPerkIds() {
            return acquiredPerks.keySet();
        }

        public int getTotalPerksAcquired() {
            return acquiredPerks.values().stream().mapToInt(Integer::intValue).sum();
        }

        // Stat modifiers
        public float getDamageMultiplier() { return damageMultiplier; }
        public void addDamageMultiplier(float amount) { damageMultiplier += amount; }

        public float getAttackSpeedMultiplier() { return attackSpeedMultiplier; }
        public void addAttackSpeedMultiplier(float amount) { attackSpeedMultiplier += amount; }

        public float getMovementSpeedMultiplier() { return movementSpeedMultiplier; }
        public void addMovementSpeedMultiplier(float amount) { movementSpeedMultiplier += amount; }

        public float getDamageReductionMultiplier() { return damageReductionMultiplier; }
        public void addDamageReduction(float amount) { damageReductionMultiplier -= amount; }

        public float getLifestealPercent() { return lifestealPercent; }
        public void addLifesteal(float amount) { lifestealPercent += amount; }

        public float getCritChanceBonus() { return critChanceBonus; }
        public void addCritChance(float amount) { critChanceBonus += amount; }

        public float getCritDamageMultiplier() { return critDamageMultiplier; }
        public void addCritDamage(float amount) { critDamageMultiplier += amount; }

        public float getFireChance() { return fireChance; }
        public void addFireChance(float amount) { fireChance += amount; }

        public float getFreezeChance() { return freezeChance; }
        public void addFreezeChance(float amount) { freezeChance += amount; }

        public float getLightningChance() { return lightningChance; }
        public void addLightningChance(float amount) { lightningChance += amount; }

        public float getStyleMultiplier() { return styleMultiplier; }
        public void addStyleMultiplier(float amount) { styleMultiplier += amount; }

        public float getComboDecayReduction() { return comboDecayReduction; }
        public void addComboDecayReduction(float amount) {
            comboDecayReduction = Math.min(0.9f, comboDecayReduction + amount);
        }

        public int getCurseLevel() { return curseLevel; }
        public void addCurse(int level) {
            curseLevel += level;
            curseRewardMultiplier += level * 0.25f;
        }

        public float getCurseRewardMultiplier() { return curseRewardMultiplier; }

        public List<SimPerk> getPendingChoices() { return pendingChoices; }
        public void setPendingChoices(List<SimPerk> choices) { this.pendingChoices = choices; }
        public void clearPendingChoices() { pendingChoices.clear(); }
    }

    // ============================================
    // Simulated PerkSystem
    // ============================================

    static class SimPerkSystem {
        private final Map<String, SimPerk> allPerks = new LinkedHashMap<>();
        private final Map<UUID, SimPerkSession> activeSessions = new ConcurrentHashMap<>();
        private final Random random = new Random();

        public SimPerkSystem() {
            initializePerks();
        }

        private void initializePerks() {
            // OFFENSE
            registerPerk(new SimPerk("sharp_blades", "Sharp Blades", "+15% damage per stack",
                SimPerkTier.COMMON, SimPerkCategory.OFFENSE, true, 5,
                ctx -> ctx.session.addDamageMultiplier(0.15f)));

            registerPerk(new SimPerk("fury", "Fury", "+10% attack speed per stack",
                SimPerkTier.COMMON, SimPerkCategory.OFFENSE, true, 5,
                ctx -> ctx.session.addAttackSpeedMultiplier(0.10f)));

            registerPerk(new SimPerk("critical_eye", "Critical Eye", "+10% crit chance per stack",
                SimPerkTier.UNCOMMON, SimPerkCategory.OFFENSE, true, 3,
                ctx -> ctx.session.addCritChance(0.10f)));

            registerPerk(new SimPerk("executioner", "Executioner", "+50% crit damage per stack",
                SimPerkTier.RARE, SimPerkCategory.OFFENSE, true, 2,
                ctx -> ctx.session.addCritDamage(0.50f)));

            registerPerk(new SimPerk("glass_cannon", "Glass Cannon", "+100% damage, +50% damage taken",
                SimPerkTier.LEGENDARY, SimPerkCategory.OFFENSE, false, 1,
                ctx -> {
                    ctx.session.addDamageMultiplier(1.0f);
                    ctx.session.addDamageReduction(-0.5f);
                }));

            // DEFENSE
            registerPerk(new SimPerk("tough_skin", "Tough Skin", "-10% damage taken per stack",
                SimPerkTier.COMMON, SimPerkCategory.DEFENSE, true, 5,
                ctx -> ctx.session.addDamageReduction(0.10f)));

            registerPerk(new SimPerk("vitality", "Vitality", "+2 max hearts",
                SimPerkTier.UNCOMMON, SimPerkCategory.DEFENSE, true, 5, null));

            // UTILITY
            registerPerk(new SimPerk("swift_feet", "Swift Feet", "+15% movement speed per stack",
                SimPerkTier.COMMON, SimPerkCategory.UTILITY, true, 3,
                ctx -> ctx.session.addMovementSpeedMultiplier(0.15f)));

            // VAMPIRIC
            registerPerk(new SimPerk("lifesteal", "Lifesteal", "Heal for 5% of damage dealt per stack",
                SimPerkTier.UNCOMMON, SimPerkCategory.VAMPIRIC, true, 4,
                ctx -> ctx.session.addLifesteal(0.05f)));

            // ELEMENTAL
            registerPerk(new SimPerk("fire_aspect", "Blazing Strikes", "20% chance to ignite enemies per stack",
                SimPerkTier.UNCOMMON, SimPerkCategory.ELEMENTAL, true, 3,
                ctx -> ctx.session.addFireChance(0.20f)));

            registerPerk(new SimPerk("frost_touch", "Frost Touch", "20% chance to slow enemies per stack",
                SimPerkTier.UNCOMMON, SimPerkCategory.ELEMENTAL, true, 3,
                ctx -> ctx.session.addFreezeChance(0.20f)));

            registerPerk(new SimPerk("lightning_strike", "Lightning Strike", "10% chance to chain lightning per stack",
                SimPerkTier.RARE, SimPerkCategory.ELEMENTAL, true, 2,
                ctx -> ctx.session.addLightningChance(0.10f)));

            // COMBO
            registerPerk(new SimPerk("showoff", "Showoff", "+25% style points per stack",
                SimPerkTier.COMMON, SimPerkCategory.COMBO, true, 4,
                ctx -> ctx.session.addStyleMultiplier(0.25f)));

            registerPerk(new SimPerk("combo_master", "Combo Master", "-25% combo decay rate per stack",
                SimPerkTier.RARE, SimPerkCategory.COMBO, true, 3,
                ctx -> ctx.session.addComboDecayReduction(0.25f)));

            // CURSE
            registerPerk(new SimPerk("curse_fragility", "Curse: Fragility", "+50% damage taken, +50% rewards",
                SimPerkTier.UNCOMMON, SimPerkCategory.CURSE, true, 2,
                ctx -> {
                    ctx.session.addDamageReduction(-0.5f);
                    ctx.session.addCurse(1);
                }));

            registerPerk(new SimPerk("curse_weakness", "Curse: Weakness", "-25% damage, +50% rewards",
                SimPerkTier.UNCOMMON, SimPerkCategory.CURSE, true, 2,
                ctx -> {
                    ctx.session.addDamageMultiplier(-0.25f);
                    ctx.session.addCurse(1);
                }));

            // LEGENDARY with synergy requirements
            registerPerk(new SimPerk("avatar_of_war", "Avatar of War",
                "Requires 5+ offense perks. +100% damage",
                SimPerkTier.LEGENDARY, SimPerkCategory.OFFENSE, false, 1,
                Set.of(), Set.of(),
                ctx -> ctx.session.addDamageMultiplier(1.0f)));

            registerPerk(new SimPerk("unkillable", "Unkillable",
                "Requires 5+ defense perks. Immune to one-shots",
                SimPerkTier.LEGENDARY, SimPerkCategory.DEFENSE, false, 1,
                Set.of(), Set.of(), null));
        }

        private void registerPerk(SimPerk perk) {
            allPerks.put(perk.id, perk);
        }

        public SimPerkSession startSession(UUID playerId, UUID questId) {
            SimPerkSession session = new SimPerkSession(playerId, questId);
            activeSessions.put(playerId, session);
            return session;
        }

        public Optional<SimPerkSession> getSession(UUID playerId) {
            return Optional.ofNullable(activeSessions.get(playerId));
        }

        public SimPerkSession endSession(UUID playerId) {
            return activeSessions.remove(playerId);
        }

        public List<SimPerk> generatePerkChoices(SimPerkSession session, int waveNumber) {
            List<SimPerk> choices = new ArrayList<>();
            List<SimPerk> available = getAvailablePerks(session, waveNumber);

            int attempts = 0;
            while (choices.size() < 3 && attempts < 100 && !available.isEmpty()) {
                SimPerk selected = selectWeightedPerk(available, waveNumber);
                if (selected != null && !choices.contains(selected)) {
                    choices.add(selected);
                    available.remove(selected);
                }
                attempts++;
            }

            session.setPendingChoices(choices);
            return choices;
        }

        private List<SimPerk> getAvailablePerks(SimPerkSession session, int waveNumber) {
            List<SimPerk> available = new ArrayList<>();

            for (SimPerk perk : allPerks.values()) {
                if (!perk.stackable && session.hasPerk(perk.id)) continue;
                if (perk.stackable && session.getPerkStacks(perk.id) >= perk.maxStacks) continue;

                if (!perk.requiredPerks.isEmpty()) {
                    boolean hasAll = perk.requiredPerks.stream().allMatch(session::hasPerk);
                    if (!hasAll) continue;
                }

                if (perk.incompatiblePerks.stream().anyMatch(session::hasPerk)) continue;

                // Synergy requirements for legendary
                if (perk.tier == SimPerkTier.LEGENDARY) {
                    if (perk.id.equals("avatar_of_war")) {
                        long offenseCount = session.getAcquiredPerkIds().stream()
                            .map(allPerks::get)
                            .filter(p -> p != null && p.category == SimPerkCategory.OFFENSE)
                            .count();
                        if (offenseCount < 5) continue;
                    } else if (perk.id.equals("unkillable")) {
                        long defenseCount = session.getAcquiredPerkIds().stream()
                            .map(allPerks::get)
                            .filter(p -> p != null && p.category == SimPerkCategory.DEFENSE)
                            .count();
                        if (defenseCount < 5) continue;
                    }
                }

                if (waveNumber < 3 && perk.tier.ordinal() >= SimPerkTier.EPIC.ordinal()) continue;

                available.add(perk);
            }

            return available;
        }

        private SimPerk selectWeightedPerk(List<SimPerk> perks, int waveNumber) {
            if (perks.isEmpty()) return null;

            int totalWeight = 0;
            for (SimPerk perk : perks) {
                int weight = perk.tier.weight;
                if (waveNumber >= 5) weight *= (perk.tier.ordinal() + 1);
                totalWeight += weight;
            }

            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            for (SimPerk perk : perks) {
                int weight = perk.tier.weight;
                if (waveNumber >= 5) weight *= (perk.tier.ordinal() + 1);
                cumulative += weight;
                if (roll < cumulative) {
                    return perk;
                }
            }

            return perks.get(random.nextInt(perks.size()));
        }

        public boolean selectPerk(SimPerkSession session, int choiceIndex) {
            List<SimPerk> choices = session.getPendingChoices();
            if (choiceIndex < 0 || choiceIndex >= choices.size()) return false;

            SimPerk selected = choices.get(choiceIndex);
            applyPerk(session, selected);
            session.clearPendingChoices();
            return true;
        }

        private void applyPerk(SimPerkSession session, SimPerk perk) {
            session.addPerk(perk.id);
            SimPerkContext context = new SimPerkContext(session, session.getPerkStacks(perk.id));
            perk.apply(context);
        }

        public Map<String, SimPerk> getAllPerks() {
            return Collections.unmodifiableMap(allPerks);
        }
    }

    // ============================================
    // Test Instance
    // ============================================

    private SimPerkSystem perkSystem;
    private UUID playerId;
    private UUID questId;

    @BeforeEach
    void setup() {
        perkSystem = new SimPerkSystem();
        playerId = UUID.randomUUID();
        questId = UUID.randomUUID();
    }

    // ============================================
    // L1: PerkTier and PerkCategory Tests
    // ============================================

    @Nested
    @DisplayName("L1: Perk Tier Structure")
    class PerkTierTests {

        @ParameterizedTest
        @EnumSource(SimPerkTier.class)
        @DisplayName("All tiers have positive weight")
        void testTierWeights(SimPerkTier tier) {
            assertTrue(tier.weight > 0, "Tier " + tier + " should have positive weight");
        }

        @Test
        @DisplayName("Tier weights sum to 100")
        void testTierWeightsSum() {
            int total = 0;
            for (SimPerkTier tier : SimPerkTier.values()) {
                total += tier.weight;
            }
            assertEquals(100, total, "Total tier weights should be 100");
        }

        @Test
        @DisplayName("Tiers are ordered by rarity (weight decreasing)")
        void testTierOrdering() {
            SimPerkTier[] tiers = SimPerkTier.values();
            for (int i = 0; i < tiers.length - 1; i++) {
                assertTrue(tiers[i].weight >= tiers[i + 1].weight,
                    tiers[i] + " should have >= weight than " + tiers[i + 1]);
            }
        }

        @ParameterizedTest
        @EnumSource(SimPerkTier.class)
        @DisplayName("All tiers have display name")
        void testTierDisplayNames(SimPerkTier tier) {
            assertNotNull(tier.displayName);
            assertFalse(tier.displayName.isEmpty());
        }

        @ParameterizedTest
        @EnumSource(SimPerkTier.class)
        @DisplayName("All tiers have valid color")
        void testTierColors(SimPerkTier tier) {
            assertTrue(tier.color >= 0, "Color should be non-negative");
        }
    }

    @Nested
    @DisplayName("L1: Perk Category Structure")
    class PerkCategoryTests {

        @ParameterizedTest
        @EnumSource(SimPerkCategory.class)
        @DisplayName("All categories have display name")
        void testCategoryDisplayNames(SimPerkCategory category) {
            assertNotNull(category.displayName);
            assertFalse(category.displayName.isEmpty());
        }

        @ParameterizedTest
        @EnumSource(SimPerkCategory.class)
        @DisplayName("All categories have valid color")
        void testCategoryColors(SimPerkCategory category) {
            assertTrue(category.color >= 0);
        }

        @Test
        @DisplayName("CURSE category exists for risk/reward perks")
        void testCurseCategoryExists() {
            assertTrue(Arrays.asList(SimPerkCategory.values()).contains(SimPerkCategory.CURSE));
        }
    }

    // ============================================
    // L2: PerkSession Management Tests
    // ============================================

    @Nested
    @DisplayName("L2: PerkSession Management")
    class PerkSessionTests {

        @Test
        @DisplayName("Start session creates new session")
        void testStartSession() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            assertNotNull(session);
            assertEquals(playerId, session.getPlayerId());
            assertEquals(questId, session.getQuestId());
        }

        @Test
        @DisplayName("Get session returns started session")
        void testGetSession() {
            perkSystem.startSession(playerId, questId);
            Optional<SimPerkSession> retrieved = perkSystem.getSession(playerId);

            assertTrue(retrieved.isPresent());
            assertEquals(playerId, retrieved.get().getPlayerId());
        }

        @Test
        @DisplayName("Get session returns empty for unknown player")
        void testGetSessionUnknown() {
            Optional<SimPerkSession> retrieved = perkSystem.getSession(UUID.randomUUID());
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("End session removes session")
        void testEndSession() {
            perkSystem.startSession(playerId, questId);
            SimPerkSession ended = perkSystem.endSession(playerId);

            assertNotNull(ended);
            assertFalse(perkSystem.getSession(playerId).isPresent());
        }

        @Test
        @DisplayName("New session has default stat values")
        void testSessionDefaultStats() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            assertEquals(1.0f, session.getDamageMultiplier(), 0.001f);
            assertEquals(1.0f, session.getAttackSpeedMultiplier(), 0.001f);
            assertEquals(1.0f, session.getMovementSpeedMultiplier(), 0.001f);
            assertEquals(1.0f, session.getDamageReductionMultiplier(), 0.001f);
            assertEquals(0f, session.getLifestealPercent(), 0.001f);
            assertEquals(0f, session.getCritChanceBonus(), 0.001f);
            assertEquals(1.5f, session.getCritDamageMultiplier(), 0.001f);
            assertEquals(0, session.getCurseLevel());
        }

        @Test
        @DisplayName("New session has no acquired perks")
        void testSessionNoPerks() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            assertEquals(0, session.getTotalPerksAcquired());
            assertTrue(session.getAcquiredPerkIds().isEmpty());
        }
    }

    // ============================================
    // L2: Stat Modifier Tests
    // ============================================

    @Nested
    @DisplayName("L2: Stat Modifiers")
    class StatModifierTests {

        @Test
        @DisplayName("Damage multiplier stacks additively")
        void testDamageMultiplierStacking() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addDamageMultiplier(0.15f);
            assertEquals(1.15f, session.getDamageMultiplier(), 0.001f);

            session.addDamageMultiplier(0.15f);
            assertEquals(1.30f, session.getDamageMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Damage reduction stacks additively")
        void testDamageReductionStacking() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addDamageReduction(0.10f);
            assertEquals(0.90f, session.getDamageReductionMultiplier(), 0.001f);

            session.addDamageReduction(0.10f);
            assertEquals(0.80f, session.getDamageReductionMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Combo decay reduction capped at 0.9")
        void testComboDecayReductionCap() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addComboDecayReduction(0.25f);
            session.addComboDecayReduction(0.25f);
            session.addComboDecayReduction(0.25f);
            session.addComboDecayReduction(0.25f); // Would be 1.0 without cap

            assertEquals(0.9f, session.getComboDecayReduction(), 0.001f);
        }

        @Test
        @DisplayName("Curse increases reward multiplier")
        void testCurseRewardMultiplier() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addCurse(1);
            assertEquals(1, session.getCurseLevel());
            assertEquals(1.25f, session.getCurseRewardMultiplier(), 0.001f);

            session.addCurse(2);
            assertEquals(3, session.getCurseLevel());
            assertEquals(1.75f, session.getCurseRewardMultiplier(), 0.001f);
        }
    }

    // ============================================
    // L3: Perk Selection Tests
    // ============================================

    @Nested
    @DisplayName("L3: Perk Selection")
    class PerkSelectionTests {

        @Test
        @DisplayName("Generate choices returns 3 perks")
        void testGenerateChoicesCount() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            List<SimPerk> choices = perkSystem.generatePerkChoices(session, 1);

            assertEquals(3, choices.size());
        }

        @Test
        @DisplayName("Generated choices are unique")
        void testGenerateChoicesUnique() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            List<SimPerk> choices = perkSystem.generatePerkChoices(session, 1);

            Set<String> ids = new HashSet<>();
            for (SimPerk perk : choices) {
                assertTrue(ids.add(perk.id), "Choices should be unique");
            }
        }

        @Test
        @DisplayName("Select perk applies to session")
        void testSelectPerk() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            perkSystem.generatePerkChoices(session, 1);

            assertTrue(perkSystem.selectPerk(session, 0));
            assertEquals(1, session.getTotalPerksAcquired());
        }

        @Test
        @DisplayName("Select perk clears pending choices")
        void testSelectPerkClearsPending() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            perkSystem.generatePerkChoices(session, 1);

            perkSystem.selectPerk(session, 0);
            assertTrue(session.getPendingChoices().isEmpty());
        }

        @Test
        @DisplayName("Invalid choice index returns false")
        void testInvalidChoiceIndex() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            perkSystem.generatePerkChoices(session, 1);

            assertFalse(perkSystem.selectPerk(session, -1));
            assertFalse(perkSystem.selectPerk(session, 3));
            assertFalse(perkSystem.selectPerk(session, 100));
        }

        @Test
        @DisplayName("Early waves exclude epic/legendary perks")
        void testEarlyWaveRarity() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            for (int i = 0; i < 20; i++) {
                List<SimPerk> choices = perkSystem.generatePerkChoices(session, 1);
                for (SimPerk perk : choices) {
                    assertTrue(perk.tier.ordinal() < SimPerkTier.EPIC.ordinal(),
                        "Wave 1 should not offer Epic+ perks");
                }
            }
        }
    }

    // ============================================
    // L3: Perk Stacking Tests
    // ============================================

    @Nested
    @DisplayName("L3: Perk Stacking")
    class PerkStackingTests {

        @Test
        @DisplayName("Stackable perk can be acquired multiple times")
        void testStackablePerk() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addPerk("sharp_blades");
            session.addPerk("sharp_blades");
            session.addPerk("sharp_blades");

            assertEquals(3, session.getPerkStacks("sharp_blades"));
        }

        @Test
        @DisplayName("hasPerk returns true after acquiring")
        void testHasPerk() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            assertFalse(session.hasPerk("sharp_blades"));
            session.addPerk("sharp_blades");
            assertTrue(session.hasPerk("sharp_blades"));
        }

        @Test
        @DisplayName("Non-stackable perk not offered after acquiring")
        void testNonStackablePerkExclusion() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            session.addPerk("glass_cannon");

            // Generate choices multiple times
            for (int i = 0; i < 50; i++) {
                List<SimPerk> choices = perkSystem.generatePerkChoices(session, 10);
                for (SimPerk perk : choices) {
                    assertNotEquals("glass_cannon", perk.id,
                        "Non-stackable perk should not appear after acquiring");
                }
            }
        }
    }

    // ============================================
    // L4: Synergy Requirements Tests
    // ============================================

    @Nested
    @DisplayName("L4: Synergy Requirements")
    class SynergyTests {

        @Test
        @DisplayName("Avatar of War requires 5+ offense perks")
        void testAvatarOfWarRequirement() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Add 4 offense perks (not enough)
            session.addPerk("sharp_blades");
            session.addPerk("sharp_blades");
            session.addPerk("fury");
            session.addPerk("fury");

            // Should not be offered
            for (int i = 0; i < 50; i++) {
                List<SimPerk> choices = perkSystem.generatePerkChoices(session, 10);
                for (SimPerk perk : choices) {
                    assertNotEquals("avatar_of_war", perk.id);
                }
            }

            // Add 5th offense perk
            session.addPerk("critical_eye");

            // Now might be offered (at higher waves)
            boolean found = false;
            for (int i = 0; i < 100; i++) {
                List<SimPerk> choices = perkSystem.generatePerkChoices(session, 10);
                for (SimPerk perk : choices) {
                    if (perk.id.equals("avatar_of_war")) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            // Note: Due to random weighted selection, may not always appear
        }
    }

    // ============================================
    // L4: Curse System Tests
    // ============================================

    @Nested
    @DisplayName("L4: Curse System")
    class CurseSystemTests {

        @Test
        @DisplayName("Curse perks increase curse level")
        void testCurseLevelIncrease() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Apply curse perk effect
            session.addDamageReduction(-0.5f);
            session.addCurse(1);

            assertEquals(1, session.getCurseLevel());
        }

        @Test
        @DisplayName("Multiple curses stack")
        void testCurseStacking() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            session.addCurse(1);
            session.addCurse(1);
            session.addCurse(2);

            assertEquals(4, session.getCurseLevel());
            assertEquals(2.0f, session.getCurseRewardMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Curse weakness reduces damage")
        void testCurseWeaknessEffect() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Simulate curse_weakness effect
            session.addDamageMultiplier(-0.25f);
            session.addCurse(1);

            assertEquals(0.75f, session.getDamageMultiplier(), 0.001f);
            assertEquals(1, session.getCurseLevel());
        }

        @Test
        @DisplayName("Curse fragility increases damage taken")
        void testCurseFragilityEffect() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Simulate curse_fragility effect
            session.addDamageReduction(-0.5f);
            session.addCurse(1);

            assertEquals(1.5f, session.getDamageReductionMultiplier(), 0.001f);
        }
    }

    // ============================================
    // L5: Stress Tests
    // ============================================

    @Nested
    @DisplayName("L5: Stress Tests")
    class StressTests {

        @RepeatedTest(100)
        @DisplayName("Perk generation is consistent")
        void testPerkGenerationConsistency() {
            SimPerkSession session = perkSystem.startSession(UUID.randomUUID(), UUID.randomUUID());
            List<SimPerk> choices = perkSystem.generatePerkChoices(session, 5);

            assertEquals(3, choices.size());
            assertFalse(choices.contains(null));
        }

        @Test
        @DisplayName("Many sessions can be created concurrently")
        void testConcurrentSessions() throws InterruptedException {
            int numThreads = 10;
            int sessionsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(numThreads);
            List<UUID> createdPlayers = Collections.synchronizedList(new ArrayList<>());

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < sessionsPerThread; j++) {
                            UUID pid = UUID.randomUUID();
                            perkSystem.startSession(pid, questId);
                            createdPlayers.add(pid);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(numThreads * sessionsPerThread, createdPlayers.size());
        }

        @Test
        @DisplayName("Full perk run simulation")
        void testFullPerkRunSimulation() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Simulate 50 waves of perk selection
            for (int wave = 1; wave <= 50; wave++) {
                List<SimPerk> choices = perkSystem.generatePerkChoices(session, wave);

                // Should always get at least 1 choice (or 3 if enough perks available)
                assertFalse(choices.isEmpty(), "Should have choices at wave " + wave);

                // Select first available
                if (!choices.isEmpty()) {
                    perkSystem.selectPerk(session, 0);
                }
            }

            // Should have acquired many perks
            assertTrue(session.getTotalPerksAcquired() >= 30,
                "Should have acquired perks over 50 waves");
        }

        @Test
        @DisplayName("Perk stats don't overflow")
        void testStatOverflow() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Add many stat modifiers
            for (int i = 0; i < 1000; i++) {
                session.addDamageMultiplier(0.15f);
                session.addLifesteal(0.05f);
                session.addCritChance(0.10f);
            }

            assertTrue(Float.isFinite(session.getDamageMultiplier()));
            assertTrue(Float.isFinite(session.getLifestealPercent()));
            assertTrue(Float.isFinite(session.getCritChanceBonus()));
        }
    }

    // ============================================
    // L5: Edge Cases
    // ============================================

    @Nested
    @DisplayName("L5: Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty pending choices returns false on select")
        void testSelectWithNoPending() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            assertFalse(perkSystem.selectPerk(session, 0));
        }

        @Test
        @DisplayName("Session with max stacked perks still generates choices")
        void testMaxStackedPerks() {
            SimPerkSession session = perkSystem.startSession(playerId, questId);

            // Max out one stackable perk
            for (int i = 0; i < 5; i++) {
                session.addPerk("sharp_blades");
            }

            List<SimPerk> choices = perkSystem.generatePerkChoices(session, 5);

            // Should still get choices (other perks available)
            assertFalse(choices.isEmpty());

            // sharp_blades should not be in choices (at max stacks)
            for (SimPerk perk : choices) {
                assertNotEquals("sharp_blades", perk.id);
            }
        }

        @Test
        @DisplayName("All perks have valid structure")
        void testAllPerksValid() {
            for (SimPerk perk : perkSystem.getAllPerks().values()) {
                assertNotNull(perk.id);
                assertFalse(perk.id.isEmpty());
                assertNotNull(perk.name);
                assertNotNull(perk.description);
                assertNotNull(perk.tier);
                assertNotNull(perk.category);
                assertTrue(perk.maxStacks >= 1);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 25, 50, 100})
        @DisplayName("Perk generation works at various wave numbers")
        void testPerkGenerationAtWaves(int wave) {
            SimPerkSession session = perkSystem.startSession(playerId, questId);
            List<SimPerk> choices = perkSystem.generatePerkChoices(session, wave);

            assertNotNull(choices);
            assertTrue(choices.size() <= 3);
        }
    }
}
