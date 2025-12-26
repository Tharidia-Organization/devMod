package com.devmod.gametest;

import java.util.Objects;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.devmod.DevMod;
import com.devmod.config.MobConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
import com.devmod.runtime.InstanceState;
import com.devmod.runtime.PlayerInstanceSnapshot;
import com.devmod.runtime.PlayerInstanceState;
import com.devmod.runtime.RecoverySystem;
@GameTestHolder(DevMod.MODID)
@PrefixGameTestTemplate(false)
public class L0BootVerificationTests {

    private static final String TEMPLATE = "empty";

    // ============================================================
    // BATCH LIFECYCLE
    // ============================================================

    @BeforeBatch(batch = "l0_boot")
    public static void setupL0Batch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] === L0 BOOT VERIFICATION STARTING ===");
    }

    @AfterBatch(batch = "l0_boot")
    public static void cleanupL0Batch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] === L0 BOOT VERIFICATION COMPLETE ===");
    }

    // ============================================================
    // L0-01: SERVER STARTUP
    // ============================================================

    /**
     * L0-01: Server started successfully
     * If this test runs, the server has started without crash.
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_01_serverStarted(GameTestHelper helper) {
        // If we get here, server started successfully
        helper.assertTrue(true, "Server started successfully");
        DevMod.LOGGER.info("[L0-01] PASS: Server started successfully");
        helper.succeed();
    }

    // ============================================================
    // L0-02: INSTANCE SYSTEM
    // ============================================================

    /**
     * L0-02: InstanceManager initialized
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_02_instanceManagerReady(GameTestHelper helper) {
        boolean ready = InstanceManager.INSTANCE.isReady();
        helper.assertTrue(ready, "InstanceManager should be ready");
        DevMod.LOGGER.info("[L0-02] PASS: InstanceManager ready = {}", ready);
        helper.succeed();
    }

    // ============================================================
    // L0-03: RECOVERY SYSTEM
    // ============================================================

    /**
     * L0-03: RecoverySystem initialized
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_03_recoverySystemReady(GameTestHelper helper) {
        // RecoverySystem doesn't have isReady(), so we test by calling a method
        try {
            boolean hasSnapshot = RecoverySystem.INSTANCE.hasSnapshot(java.util.UUID.randomUUID());
            // If we get here without exception, system is initialized
            helper.assertTrue(!hasSnapshot, "Random UUID should not have snapshot");
            DevMod.LOGGER.info("[L0-03] PASS: RecoverySystem accessible");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("RecoverySystem not initialized: " + e.getMessage());
        }
    }

    // ============================================================
    // L0-04: DIMENSION MANAGER
    // ============================================================

    /**
     * L0-04: DynamicDimensionManager initialized
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_04_dimensionManagerReady(GameTestHelper helper) {
        boolean ready = DynamicDimensionManager.INSTANCE.isReady();
        helper.assertTrue(ready, "DynamicDimensionManager should be ready");
        DevMod.LOGGER.info("[L0-04] PASS: DynamicDimensionManager ready = {}", ready);
        helper.succeed();
    }

    // ============================================================
    // L0-05: REGISTRY
    // ============================================================

    /**
     * L0-05: InstanceRegistry accessible
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_05_registryAccessible(GameTestHelper helper) {
        try {
            // Test basic operations
            java.util.Optional<InstanceData> instance =
                InstanceRegistry.INSTANCE.getInstance(java.util.UUID.randomUUID());
            helper.assertTrue(instance.isEmpty(), "Random UUID should not have instance");

            java.util.Optional<InstanceData> playerInstance =
                InstanceRegistry.INSTANCE.getPlayerInstance(java.util.UUID.randomUUID());
            helper.assertTrue(playerInstance.isEmpty(), "Random player should not be in instance");

            DevMod.LOGGER.info("[L0-05] PASS: InstanceRegistry accessible");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("InstanceRegistry not accessible: " + e.getMessage());
        }
    }

    // ============================================================
    // L0-06: CONFIG MANAGERS
    // ============================================================

    /**
     * L0-06a: MobConfigManager accessible
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_06a_mobConfigAccessible(GameTestHelper helper) {
        try {
            boolean hasConfig = MobConfigManager.hasConfig(EntityType.ZOMBIE);
            // Just checking it doesn't throw
            DevMod.LOGGER.info("[L0-06a] PASS: MobConfigManager accessible, zombie config = {}", hasConfig);
            helper.succeed();
        } catch (Exception e) {
            helper.fail("MobConfigManager not accessible: " + e.getMessage());
        }
    }

    /**
     * L0-06b: WeaponConfigManager accessible
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_06b_weaponConfigAccessible(GameTestHelper helper) {
        try {
            net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(
                Objects.requireNonNull(Items.DIAMOND_SWORD));
            var stats = WeaponConfigManager.getStats(sword);
            helper.assertTrue(stats != null, "WeaponStats should not be null");
            DevMod.LOGGER.info("[L0-06b] PASS: WeaponConfigManager accessible");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("WeaponConfigManager not accessible: " + e.getMessage());
        }
    }

    // ============================================================
    // L0-07: ENTITY TYPES
    // ============================================================

    /**
     * L0-07: Common entity types registered
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_07_entityTypesRegistered(GameTestHelper helper) {
        // Verify standard entity types are accessible (for mob spawning)
        helper.assertTrue(EntityType.ZOMBIE != null, "ZOMBIE type should be registered");
        helper.assertTrue(EntityType.SKELETON != null, "SKELETON type should be registered");
        helper.assertTrue(EntityType.SPIDER != null, "SPIDER type should be registered");
        helper.assertTrue(EntityType.CREEPER != null, "CREEPER type should be registered");

        DevMod.LOGGER.info("[L0-07] PASS: Entity types registered");
        helper.succeed();
    }

    // ============================================================
    // L0-08: ITEMS
    // ============================================================

    /**
     * L0-08: Common items accessible
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_08_itemsAccessible(GameTestHelper helper) {
        // Verify standard items are accessible (for weapon config)
        helper.assertTrue(Items.DIAMOND_SWORD != null, "DIAMOND_SWORD should be registered");
        helper.assertTrue(Items.IRON_SWORD != null, "IRON_SWORD should be registered");
        helper.assertTrue(Items.BOW != null, "BOW should be registered");

        DevMod.LOGGER.info("[L0-08] PASS: Items accessible");
        helper.succeed();
    }

    // ============================================================
    // L0-09: STATE ENUMS
    // ============================================================

    /**
     * L0-09: State enums correctly defined
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_09_stateEnumsValid(GameTestHelper helper) {
        // Verify InstanceState enum
        InstanceState[] instanceStates = InstanceState.values();
        helper.assertTrue(instanceStates.length >= 6, "InstanceState should have at least 6 values");

        // Verify PlayerInstanceState enum
        PlayerInstanceState[] playerStates = PlayerInstanceState.values();
        helper.assertTrue(playerStates.length >= 5, "PlayerInstanceState should have at least 5 values");

        // Verify initial states
        helper.assertTrue(InstanceState.CREATING.ordinal() == 0, "CREATING should be first");
        helper.assertTrue(PlayerInstanceState.NORMAL.ordinal() == 0, "NORMAL should be first");

        DevMod.LOGGER.info("[L0-09] PASS: State enums valid (Instance: {}, Player: {})",
            instanceStates.length, playerStates.length);
        helper.succeed();
    }

    // ============================================================
    // L0-10: DATA STRUCTURES
    // ============================================================

    /**
     * L0-10: Core data structures work correctly
     */
    @GameTest(template = TEMPLATE, batch = "l0_boot", required = true)
    public static void l0_10_dataStructuresWork(GameTestHelper helper) {
        // Test InstanceData creation
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        InstanceData instance = InstanceData.createSolo(ownerId);

        if (instance == null) {
            helper.fail("InstanceData.createSolo() returned null");
            return;
        }
        helper.assertTrue(instance.getInstanceId() != null, "Instance should have ID");
        helper.assertTrue(instance.getState() == InstanceState.CREATING, "Initial state should be CREATING");
        helper.assertTrue(instance.getOwnerId().equals(ownerId), "Owner should match");

        // Test PlayerInstanceSnapshot creation
        java.util.UUID playerId = java.util.UUID.randomUUID();
        PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(playerId);

        helper.assertTrue(snapshot != null, "Snapshot should be created");
        helper.assertTrue(snapshot.getPlayerId().equals(playerId), "Player ID should match");
        helper.assertTrue(snapshot.getState() == PlayerInstanceState.NORMAL, "Initial state should be NORMAL");

        DevMod.LOGGER.info("[L0-10] PASS: Data structures work correctly");
        helper.succeed();
    }
}
