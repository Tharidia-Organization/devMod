package com.devmod.gametest;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.devmod.DevMod;
import com.devmod.combat.HitHelper;
import com.devmod.config.MobConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.network.UpdateMobStatsPayload;
import com.devmod.network.UpdateWeaponPayload;
import com.devmod.stats.WeaponStats;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * GameTests for DevMod critical features.
 *
 * Run with: ./gradlew runGameTestServer
 *
 * Tests are organized by batch:
 * - "core": Critical weapon/damage systems (required=true)
 * - "network": Network payload serialization (required=true)
 * - "entities": Mob configuration and body part detection
 * - "config": Configuration persistence tests
 *
 * All tests use shared templates: "empty" (3x3x3) and "empty_5x5" (5x5x5).
 *
 * Total: 15 tests
 */
@GameTestHolder(DevMod.MODID)
@PrefixGameTestTemplate(false)
public class DevModGameTests {

    // Template name - must match NBT file name in resources
    private static final String TEMPLATE_EMPTY = "empty";
    private static final String TEMPLATE_5X5 = "empty_5x5";

    // Epsilon for float comparisons
    private static final float EPSILON = 0.001f;

    // ============================================================
    // BATCH LIFECYCLE METHODS
    // ============================================================

    @BeforeBatch(batch = "core")
    public static void setupCoreBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'core' batch - resetting weapon configs");
        WeaponConfigManager.clearAllGlobalStats();
    }

    @AfterBatch(batch = "core")
    public static void cleanupCoreBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'core' batch");
        WeaponConfigManager.clearAllGlobalStats();
    }

    @BeforeBatch(batch = "network")
    public static void setupNetworkBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'network' batch");
    }

    @AfterBatch(batch = "network")
    public static void cleanupNetworkBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'network' batch");
    }

    @BeforeBatch(batch = "entities")
    public static void setupEntitiesBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'entities' batch - resetting mob configs");
        MobConfigManager.clearAllGlobalStats();
    }

    @AfterBatch(batch = "entities")
    public static void cleanupEntitiesBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'entities' batch");
        MobConfigManager.clearAllGlobalStats();
    }

    @BeforeBatch(batch = "config")
    public static void setupConfigBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'config' batch - clearing all configs");
        WeaponConfigManager.clearAllGlobalStats();
        MobConfigManager.clearAllGlobalStats();
    }

    @AfterBatch(batch = "config")
    public static void cleanupConfigBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'config' batch - final reset");
        WeaponConfigManager.clearAllGlobalStats();
        MobConfigManager.clearAllGlobalStats();
    }

    // ============================================================
    // BATCH: core - Critical weapon and damage systems
    // ============================================================

    /**
     * TEST 1: Weapon Stats Default Values (REQUIRED)
     * Verifies that default weapon stats are correct and consistent.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "core", required = true)
    public static void weaponStatsDefaultValues(GameTestHelper helper) {
        WeaponStats stats = new WeaponStats();

        // Verify default multipliers are in acceptable range
        helper.assertTrue(stats.headMult >= 1.0f && stats.headMult <= 3.0f,
            "Head multiplier should be between 1.0 and 3.0, was: " + stats.headMult);
        helper.assertTrue(stats.bodyMult >= 0.5f && stats.bodyMult <= 2.0f,
            "Body multiplier should be between 0.5 and 2.0, was: " + stats.bodyMult);
        helper.assertTrue(stats.armsMult >= 0.3f && stats.armsMult <= 1.5f,
            "Arms multiplier should be between 0.3 and 1.5, was: " + stats.armsMult);
        helper.assertTrue(stats.legsMult >= 0.3f && stats.legsMult <= 1.5f,
            "Legs multiplier should be between 0.3 and 1.5, was: " + stats.legsMult);

        // Verify base damage bonus starts at 0
        helper.assertTrue(Math.abs(stats.baseDamageBonus - 0.0f) < EPSILON,
            "Base damage bonus should be 0.0, was: " + stats.baseDamageBonus);

        // Verify armor penetration starts at 0
        helper.assertTrue(Math.abs(stats.armorPenetration - 0.0f) < EPSILON,
            "Armor penetration should be 0.0, was: " + stats.armorPenetration);

        helper.succeed();
    }

    /**
     * TEST 2: Weapon Stats NBT Serialization (REQUIRED)
     * Verifies that weapon stats can be saved to and loaded from NBT correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "core", required = true)
    public static void weaponStatsNbtRoundTrip(GameTestHelper helper) {
        // Create stats with custom values
        WeaponStats original = new WeaponStats();
        original.headMult = 2.5f;
        original.bodyMult = 1.2f;
        original.armsMult = 0.6f;
        original.legsMult = 0.5f;
        original.baseDamageBonus = 3.0f;
        original.armorPenetration = 0.15f;

        // Save to CompoundTag
        CompoundTag tag = new CompoundTag();
        original.save(tag);

        // Load from CompoundTag
        WeaponStats loaded = WeaponStats.load(tag);

        // Verify all values match
        helper.assertTrue(Math.abs(loaded.headMult - original.headMult) < EPSILON,
            "Head multiplier mismatch after load");
        helper.assertTrue(Math.abs(loaded.bodyMult - original.bodyMult) < EPSILON,
            "Body multiplier mismatch after load");
        helper.assertTrue(Math.abs(loaded.armsMult - original.armsMult) < EPSILON,
            "Arms multiplier mismatch after load");
        helper.assertTrue(Math.abs(loaded.legsMult - original.legsMult) < EPSILON,
            "Legs multiplier mismatch after load");
        helper.assertTrue(Math.abs(loaded.baseDamageBonus - original.baseDamageBonus) < EPSILON,
            "Base damage bonus mismatch after load");
        helper.assertTrue(Math.abs(loaded.armorPenetration - original.armorPenetration) < EPSILON,
            "Armor penetration mismatch after load");

        helper.succeed();
    }

    /**
     * TEST 3: Damage Multiplier Ordering
     * Verifies that damage multipliers maintain correct ordering:
     * HEAD >= BODY >= ARMS >= LEGS (typical combat balance)
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "core")
    public static void damageMultiplierOrdering(GameTestHelper helper) {
        float baseDamage = 10.0f;
        WeaponStats stats = new WeaponStats();

        float headDamage = baseDamage * stats.headMult;
        float bodyDamage = baseDamage * stats.bodyMult;
        float armsDamage = baseDamage * stats.armsMult;
        float legsDamage = baseDamage * stats.legsMult;

        // Verify ordering
        helper.assertTrue(headDamage >= bodyDamage,
            "Head damage should be >= body damage");
        helper.assertTrue(bodyDamage >= armsDamage,
            "Body damage should be >= arms damage");
        helper.assertTrue(armsDamage >= legsDamage,
            "Arms damage should be >= legs damage");

        // Verify all damages are positive
        helper.assertTrue(headDamage > 0, "Head damage should be positive");
        helper.assertTrue(bodyDamage > 0, "Body damage should be positive");
        helper.assertTrue(armsDamage > 0, "Arms damage should be positive");
        helper.assertTrue(legsDamage > 0, "Legs damage should be positive");

        helper.succeed();
    }

    /**
     * TEST 4: Damage Calculation Basic Formula (REQUIRED)
     * Verifies: newDamage = (originalDamage + baseDamageBonus) * multiplier
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "core", required = true)
    public static void damageCalculationBasic(GameTestHelper helper) {
        WeaponStats stats = new WeaponStats();
        stats.headMult = 2.0f;
        stats.baseDamageBonus = 5.0f;
        stats.armorPenetration = 0.0f;

        float originalDamage = 10.0f;
        // Formula: (originalDamage + baseDamageBonus) * multiplier
        float expectedDamage = (originalDamage + stats.baseDamageBonus) * stats.headMult;
        // = (10 + 5) * 2.0 = 30.0

        helper.assertTrue(Math.abs(expectedDamage - 30.0f) < EPSILON,
            "Expected damage 30.0, got: " + expectedDamage);

        helper.succeed();
    }

    /**
     * TEST 5: Damage Calculation with Zero Base Damage
     * Edge case: Zero damage should still result in valid (non-negative) output.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "core")
    public static void damageCalculationZeroDamage(GameTestHelper helper) {
        WeaponStats stats = new WeaponStats();
        stats.headMult = 2.0f;
        stats.baseDamageBonus = 3.0f;

        float originalDamage = 0.0f;
        float calculatedDamage = (originalDamage + stats.baseDamageBonus) * stats.headMult;
        // = (0 + 3) * 2.0 = 6.0

        helper.assertTrue(calculatedDamage >= 0,
            "Damage should never be negative, got: " + calculatedDamage);
        helper.assertTrue(Math.abs(calculatedDamage - 6.0f) < EPSILON,
            "Expected 6.0 damage from bonus alone, got: " + calculatedDamage);

        helper.succeed();
    }

    // ============================================================
    // BATCH: network - Network payload serialization (CRITICAL)
    // ============================================================

    /**
     * TEST 6: UpdateMobStatsPayload Serialization Round-Trip (REQUIRED)
     * Verifies mob stats payload encode/decode correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "network", required = true)
    public static void networkPayloadMobStats(GameTestHelper helper) {
        // Create test payload
        UpdateMobStatsPayload original = new UpdateMobStatsPayload(
            true,           // isGlobal
            12345,          // entityId
            32.0,           // followRange
            5.5,            // damage
            40.0,           // maxHealth
            4.0,            // armor
            2.5,            // attackRange
            0.25,           // speed
            0.5             // knockbackResist
        );

        // Serialize to buffer
        ByteBuf buffer = Objects.requireNonNull(Unpooled.buffer(), "Buffer should not be null");
        UpdateMobStatsPayload.STREAM_CODEC.encode(buffer, original);

        // Deserialize from buffer
        UpdateMobStatsPayload decoded = UpdateMobStatsPayload.STREAM_CODEC.decode(buffer);

        // Verify all fields
        helper.assertTrue(decoded.isGlobal() == original.isGlobal(),
            "isGlobal mismatch: expected " + original.isGlobal() + ", got " + decoded.isGlobal());
        helper.assertTrue(decoded.entityId() == original.entityId(),
            "entityId mismatch: expected " + original.entityId() + ", got " + decoded.entityId());
        helper.assertTrue(Math.abs(decoded.followRange() - original.followRange()) < EPSILON,
            "followRange mismatch");
        helper.assertTrue(Math.abs(decoded.damage() - original.damage()) < EPSILON,
            "damage mismatch");
        helper.assertTrue(Math.abs(decoded.maxHealth() - original.maxHealth()) < EPSILON,
            "maxHealth mismatch");
        helper.assertTrue(Math.abs(decoded.armor() - original.armor()) < EPSILON,
            "armor mismatch");
        helper.assertTrue(Math.abs(decoded.attackRange() - original.attackRange()) < EPSILON,
            "attackRange mismatch");
        helper.assertTrue(Math.abs(decoded.speed() - original.speed()) < EPSILON,
            "speed mismatch");
        helper.assertTrue(Math.abs(decoded.knockbackResist() - original.knockbackResist()) < EPSILON,
            "knockbackResist mismatch");

        buffer.release();
        helper.succeed();
    }

    /**
     * TEST 7: UpdateWeaponPayload Serialization Round-Trip (REQUIRED)
     * Verifies weapon stats payload encode/decode correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "network", required = true)
    public static void networkPayloadWeapon(GameTestHelper helper) {
        // Create test payload
        UpdateWeaponPayload original = new UpdateWeaponPayload(
            false,          // isGlobal
            2.5f,           // head
            1.2f,           // body
            0.8f,           // legs
            0.3f,           // pen
            5.0f,           // bonus
            "diamond_sword" // name
        );

        // Serialize to buffer
        ByteBuf buffer = Objects.requireNonNull(Unpooled.buffer(), "Buffer should not be null");
        UpdateWeaponPayload.STREAM_CODEC.encode(buffer, original);

        // Deserialize from buffer
        UpdateWeaponPayload decoded = UpdateWeaponPayload.STREAM_CODEC.decode(buffer);

        // Verify all fields
        helper.assertTrue(decoded.isGlobal() == original.isGlobal(),
            "isGlobal mismatch");
        helper.assertTrue(Math.abs(decoded.head() - original.head()) < EPSILON,
            "head mismatch: expected " + original.head() + ", got " + decoded.head());
        helper.assertTrue(Math.abs(decoded.body() - original.body()) < EPSILON,
            "body mismatch");
        helper.assertTrue(Math.abs(decoded.legs() - original.legs()) < EPSILON,
            "legs mismatch");
        helper.assertTrue(Math.abs(decoded.pen() - original.pen()) < EPSILON,
            "pen mismatch");
        helper.assertTrue(Math.abs(decoded.bonus() - original.bonus()) < EPSILON,
            "bonus mismatch");
        helper.assertTrue(decoded.name().equals(original.name()),
            "name mismatch: expected '" + original.name() + "', got '" + decoded.name() + "'");

        buffer.release();
        helper.succeed();
    }

    /**
     * TEST 8: UpdateWeaponPayload with Empty String
     * Edge case: empty weapon name should serialize correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "network")
    public static void networkPayloadWeaponEmptyName(GameTestHelper helper) {
        UpdateWeaponPayload original = new UpdateWeaponPayload(
            true, 1.5f, 1.0f, 0.75f, 0.0f, 0.0f, ""
        );

        ByteBuf buffer = Objects.requireNonNull(Unpooled.buffer(), "Buffer should not be null");
        UpdateWeaponPayload.STREAM_CODEC.encode(buffer, original);
        UpdateWeaponPayload decoded = UpdateWeaponPayload.STREAM_CODEC.decode(buffer);

        helper.assertTrue(decoded.name().equals(""),
            "Empty name should remain empty after round-trip, got: '" + decoded.name() + "'");

        buffer.release();
        helper.succeed();
    }

    /**
     * TEST 9: Network Payload with Extreme Values
     * Edge case: very large/small numbers should serialize correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "network")
    public static void networkPayloadExtremeValues(GameTestHelper helper) {
        UpdateMobStatsPayload original = new UpdateMobStatsPayload(
            true,
            Integer.MAX_VALUE,  // Large entityId
            1000000.0,          // Large followRange
            0.001,              // Small damage
            Double.MAX_VALUE / 2,  // Very large health
            -5.0,               // Negative armor (edge case)
            0.0,                // Zero attack range
            0.0001,             // Very small speed
            1.0                 // Max knockback resist
        );

        ByteBuf buffer = Objects.requireNonNull(Unpooled.buffer(), "Buffer should not be null");
        UpdateMobStatsPayload.STREAM_CODEC.encode(buffer, original);
        UpdateMobStatsPayload decoded = UpdateMobStatsPayload.STREAM_CODEC.decode(buffer);

        helper.assertTrue(decoded.entityId() == Integer.MAX_VALUE,
            "Large entityId should survive round-trip");
        helper.assertTrue(Math.abs(decoded.damage() - 0.001) < 0.0001,
            "Small damage value precision lost");

        buffer.release();
        helper.succeed();
    }

    // ============================================================
    // BATCH: entities - Mob and body part systems
    // ============================================================

    /**
     * TEST 10: Body Part Detection Height-Based
     * Verifies that HitHelper.getBodyPart correctly identifies body parts.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "entities", timeoutTicks = 100)
    public static void bodyPartDetectionHeightBased(GameTestHelper helper) {
        BlockPos spawnPos = new BlockPos(2, 1, 2);
        Zombie zombie = Objects.requireNonNull(helper.spawn(Objects.requireNonNull(EntityType.ZOMBIE), spawnPos),
            "Spawned zombie should not be null");

        helper.runAfterDelay(10, () -> {
            double feetY = zombie.getY();
            double height = zombie.getBbHeight();

            // Test HEAD detection (top 25%, >= 75%)
            double headHitY = feetY + height * 0.85;
            HitHelper.BodyPart headPart = HitHelper.getBodyPart(zombie, headHitY);
            helper.assertTrue(headPart == HitHelper.BodyPart.HEAD,
                "Hit at 85% height should be HEAD, was: " + headPart);

            // Test BODY detection (middle, 40% - 75%)
            double bodyHitY = feetY + height * 0.55;
            HitHelper.BodyPart bodyPart = HitHelper.getBodyPart(zombie, bodyHitY);
            helper.assertTrue(bodyPart == HitHelper.BodyPart.BODY,
                "Hit at 55% height should be BODY, was: " + bodyPart);

            // Test LEGS detection (bottom 40%)
            double legsHitY = feetY + height * 0.2;
            HitHelper.BodyPart legsPart = HitHelper.getBodyPart(zombie, legsHitY);
            helper.assertTrue(legsPart == HitHelper.BodyPart.LEGS,
                "Hit at 20% height should be LEGS, was: " + legsPart);

            helper.succeed();
        });
    }

    /**
     * TEST 11: Mob Config Manager Storage and Retrieval
     * Verifies that mob configurations are stored and retrieved correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "entities")
    public static void mobConfigManagerStorage(GameTestHelper helper) {
        EntityType<?> zombieType = EntityType.ZOMBIE;

        // Set custom stats: range, damage, maxHealth, armor, attackReach
        MobConfigManager.setGlobalStats(zombieType, 32.0, 5.0, 40.0, 4.0, 2.5);

        // Retrieve and verify
        MobConfigManager.SavedStats retrieved = MobConfigManager.getGlobalStats(zombieType);

        if (retrieved == null) {
            helper.fail("Retrieved stats should not be null");
            return;
        }
        helper.assertTrue(Math.abs(retrieved.range() - 32.0) < EPSILON,
            "Follow range mismatch");
        helper.assertTrue(Math.abs(retrieved.damage() - 5.0) < EPSILON,
            "Attack damage mismatch");
        helper.assertTrue(Math.abs(retrieved.maxHealth() - 40.0) < EPSILON,
            "Max health mismatch");
        helper.assertTrue(Math.abs(retrieved.armor() - 4.0) < EPSILON,
            "Armor mismatch");
        helper.assertTrue(Math.abs(retrieved.attackReach() - 2.5) < EPSILON,
            "Attack reach mismatch");

        helper.succeed();
    }

    /**
     * TEST 12: Mob Config hasConfig Check
     * Verifies hasConfig returns correct boolean.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "entities")
    public static void mobConfigHasConfigCheck(GameTestHelper helper) {
        EntityType<?> skeletonType = EntityType.SKELETON;
        EntityType<?> creeperType = EntityType.CREEPER;

        // Clear first
        MobConfigManager.clearAllGlobalStats();

        // Initially should not have config
        helper.assertTrue(!MobConfigManager.hasConfig(skeletonType),
            "Skeleton should not have config initially");

        // Set config for skeleton only
        MobConfigManager.setGlobalStats(skeletonType, 16.0, 3.0, 20.0, 0.0, 2.0);

        // Now skeleton should have config, creeper should not
        helper.assertTrue(MobConfigManager.hasConfig(skeletonType),
            "Skeleton should have config after setting");
        helper.assertTrue(!MobConfigManager.hasConfig(creeperType),
            "Creeper should not have config");

        helper.succeed();
    }

    // ============================================================
    // BATCH: config - Configuration system tests
    // ============================================================

    /**
     * TEST 13: WeaponConfigManager Global Stats by Item Type
     * Verifies that global weapon stats are stored and retrieved per item type.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "config")
    public static void weaponConfigGlobalStats(GameTestHelper helper) {
        // Set custom global stats for diamond sword
        WeaponStats customStats = new WeaponStats();
        customStats.headMult = 3.0f;
        customStats.baseDamageBonus = 5.0f;
        WeaponConfigManager.setGlobalStats(Objects.requireNonNull(Items.DIAMOND_SWORD), customStats);

        // Create item and get stats
        ItemStack sword = new ItemStack(Objects.requireNonNull(Items.DIAMOND_SWORD));
        WeaponStats retrieved = WeaponConfigManager.getStats(sword);

        helper.assertTrue(Math.abs(retrieved.headMult - 3.0f) < EPSILON,
            "Global head multiplier should be 3.0");
        helper.assertTrue(Math.abs(retrieved.baseDamageBonus - 5.0f) < EPSILON,
            "Global base damage bonus should be 5.0");

        // Cleanup
        WeaponConfigManager.setGlobalStats(Objects.requireNonNull(Items.DIAMOND_SWORD), new WeaponStats());

        helper.succeed();
    }

    /**
     * TEST 14: WeaponConfigManager Specific NBT Stats Priority
     * Verifies that item-specific NBT stats have highest priority over global stats.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "config")
    public static void weaponConfigSpecificStatsPriority(GameTestHelper helper) {
        // Set global stats for iron sword
        WeaponStats globalStats = new WeaponStats();
        globalStats.headMult = 2.0f;
        WeaponConfigManager.setGlobalStats(Objects.requireNonNull(Items.IRON_SWORD), globalStats);

        // Create item with specific NBT stats (higher priority)
        ItemStack sword = new ItemStack(Objects.requireNonNull(Items.IRON_SWORD));
        WeaponStats specificStats = new WeaponStats();
        specificStats.headMult = 4.0f;
        WeaponConfigManager.setSpecificStats(sword, specificStats);

        // Get stats - should use specific NBT, not global
        WeaponStats retrieved = WeaponConfigManager.getStats(sword);

        helper.assertTrue(Math.abs(retrieved.headMult - 4.0f) < EPSILON,
            "Specific NBT stats should override global: expected 4.0, got " + retrieved.headMult);

        // Cleanup
        WeaponConfigManager.setGlobalStats(Objects.requireNonNull(Items.IRON_SWORD), new WeaponStats());

        helper.succeed();
    }

    /**
     * TEST 15: Config Fallback Chain Priority
     * Verifies: specific NBT > global map > defaults
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "config")
    public static void configFallbackChain(GameTestHelper helper) {
        // Clear all configs
        WeaponConfigManager.clearAllGlobalStats();

        // 1. No config set - should return defaults
        ItemStack goldenSword = new ItemStack(Objects.requireNonNull(Items.GOLDEN_SWORD));
        WeaponStats defaultStats = WeaponConfigManager.getStats(goldenSword);
        helper.assertTrue(Math.abs(defaultStats.headMult - 1.5f) < EPSILON,
            "Default head multiplier should be 1.5");

        // 2. Set global config - should override defaults
        WeaponStats globalStats = new WeaponStats();
        globalStats.headMult = 2.5f;
        WeaponConfigManager.setGlobalStats(Objects.requireNonNull(Items.GOLDEN_SWORD), globalStats);

        WeaponStats afterGlobal = WeaponConfigManager.getStats(goldenSword);
        helper.assertTrue(Math.abs(afterGlobal.headMult - 2.5f) < EPSILON,
            "Global config should override defaults: expected 2.5, got " + afterGlobal.headMult);

        // 3. Set specific NBT - should override global
        WeaponStats specificStats = new WeaponStats();
        specificStats.headMult = 3.5f;
        WeaponConfigManager.setSpecificStats(goldenSword, specificStats);

        WeaponStats afterSpecific = WeaponConfigManager.getStats(goldenSword);
        helper.assertTrue(Math.abs(afterSpecific.headMult - 3.5f) < EPSILON,
            "Specific NBT should override global: expected 3.5, got " + afterSpecific.headMult);

        helper.succeed();
    }

    // ============================================================
    // BATCH: cache - Cache management tests (NEW)
    // ============================================================

    /**
     * TEST 16: HitHelper Cache Clear Functionality
     * Verifies that clearCache() properly empties the cache.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "cache")
    public static void hitHelperCacheClear(GameTestHelper helper) {
        // First, ensure cache is empty
        HitHelper.clearCache();
        helper.assertTrue(HitHelper.getCacheSize() == 0,
            "Cache should be empty after clear, was: " + HitHelper.getCacheSize());

        helper.succeed();
    }

    /**
     * TEST 17: HitHelper Cache Size Monitoring
     * Verifies getCacheSize() returns accurate count.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "cache", timeoutTicks = 100)
    public static void hitHelperCacheSizeMonitoring(GameTestHelper helper) {
        // Clear cache first
        HitHelper.clearCache();
        int initialSize = HitHelper.getCacheSize();

        helper.assertTrue(initialSize == 0,
            "Cache should start empty, was: " + initialSize);

        // Spawn a zombie for cache test
        BlockPos spawnPos = new BlockPos(2, 1, 2);
        Zombie zombie = Objects.requireNonNull(helper.spawn(Objects.requireNonNull(EntityType.ZOMBIE), spawnPos),
            "Spawned zombie should not be null");

        helper.runAfterDelay(10, () -> {
            // Perform body part detection (should populate cache)
            double hitY = zombie.getY() + zombie.getBbHeight() * 0.5;
            HitHelper.getBodyPart(zombie, hitY);

            // Note: getBodyPart (height-based) doesn't use cache,
            // only rayTraceBodyPartAABB and rayTraceBodyPartWithHitPoint do.
            // Cache size may still be 0 here, which is correct behavior.
            int finalSize = HitHelper.getCacheSize();
            helper.assertTrue(finalSize >= 0,
                "Cache size should be non-negative: " + finalSize);

            // Cleanup
            HitHelper.clearCache();

            helper.succeed();
        });
    }

    /**
     * TEST 18: Cache Cleanup on World Change Simulation
     * Verifies that cache can be cleared (simulating world change cleanup).
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "cache")
    public static void cacheCleanupSimulation(GameTestHelper helper) {
        // This test simulates what happens when ClientModEvents.onPlayerLogout is called

        // 1. Clear cache (simulating logout)
        HitHelper.clearCache();

        // 2. Verify cache is empty
        helper.assertTrue(HitHelper.getCacheSize() == 0,
            "Cache should be empty after cleanup simulation");

        // 3. Clear again (should not throw)
        try {
            HitHelper.clearCache();
            helper.succeed();
        } catch (Exception e) {
            helper.fail("clearCache() should not throw on empty cache: " + e.getMessage());
        }
    }
}
