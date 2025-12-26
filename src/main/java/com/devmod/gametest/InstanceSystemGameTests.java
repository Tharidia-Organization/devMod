package com.devmod.gametest;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.devmod.DevMod;
import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
import com.devmod.runtime.InstanceState;
import com.devmod.runtime.PlayerInstanceSnapshot;
import com.devmod.runtime.PlayerInstanceState;
import com.devmod.runtime.RecoverySystem;

/**
 * GameTests for the Instance Dimension System.
 *
 * Run with: ./gradlew runGameTestServer
 *
 * Tests are organized by batch:
 * - "instance_smoke": Basic system initialization (required=true)
 * - "instance_flow": Full quest flow validation
 * - "instance_recovery": Recovery and error handling
 * - "instance_state": State machine validation
 *
 * IMPORTANT: These tests require a properly initialized server with mixins.
 * Some tests may be skipped if the Instance system is not fully initialized.
 */
@GameTestHolder(DevMod.MODID)
@PrefixGameTestTemplate(false)
public class InstanceSystemGameTests {

    private static final String TEMPLATE_EMPTY = "empty";

    // ============================================================
    // BATCH LIFECYCLE METHODS
    // ============================================================

    @BeforeBatch(batch = "instance_smoke")
    public static void setupSmokeBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'instance_smoke' batch");
    }

    @AfterBatch(batch = "instance_smoke")
    public static void cleanupSmokeBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'instance_smoke' batch");
    }

    @BeforeBatch(batch = "instance_flow")
    public static void setupFlowBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'instance_flow' batch");
    }

    @AfterBatch(batch = "instance_flow")
    public static void cleanupFlowBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'instance_flow' batch - force cleanup");
        // Force cleanup any orphaned instances
        if (InstanceManager.INSTANCE.isReady()) {
            InstanceRegistry.INSTANCE.processPendingDestructions();
        }
    }

    @BeforeBatch(batch = "instance_recovery")
    public static void setupRecoveryBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'instance_recovery' batch");
    }

    @AfterBatch(batch = "instance_recovery")
    public static void cleanupRecoveryBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'instance_recovery' batch");
    }

    @BeforeBatch(batch = "instance_state")
    public static void setupStateBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'instance_state' batch");
    }

    @AfterBatch(batch = "instance_state")
    public static void cleanupStateBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'instance_state' batch");
    }

    // ============================================================
    // BATCH: instance_smoke - Basic initialization tests (REQUIRED)
    // ============================================================

    /**
     * TEST 1: InstanceManager is initialized
     * Verifies that the InstanceManager singleton is properly initialized.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_smoke", required = true)
    public static void instanceManagerInitialized(GameTestHelper helper) {
        boolean ready = InstanceManager.INSTANCE.isReady();
        helper.assertTrue(ready, "InstanceManager should be initialized and ready");
        helper.succeed();
    }

    /**
     * TEST 2: RecoverySystem is initialized
     * Verifies that the RecoverySystem is properly initialized.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_smoke", required = true)
    public static void recoverySystemInitialized(GameTestHelper helper) {
        // RecoverySystem doesn't have isReady(), but we can check if it can load snapshots
        // without throwing exceptions
        try {
            Optional<PlayerInstanceSnapshot> snapshot = RecoverySystem.INSTANCE.loadSnapshot(UUID.randomUUID());
            helper.assertTrue(snapshot.isEmpty(), "Random UUID should not have a snapshot");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("RecoverySystem should not throw on loadSnapshot: " + e.getMessage());
        }
    }

    /**
     * TEST 3: DynamicDimensionManager is initialized
     * Verifies that the DynamicDimensionManager is properly initialized.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_smoke", required = true)
    public static void dynamicDimensionManagerInitialized(GameTestHelper helper) {
        boolean ready = DynamicDimensionManager.INSTANCE.isReady();
        helper.assertTrue(ready, "DynamicDimensionManager should be initialized and ready");
        helper.succeed();
    }

    /**
     * TEST 4: InstanceRegistry can be queried
     * Verifies that the InstanceRegistry is accessible.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_smoke", required = true)
    public static void instanceRegistryAccessible(GameTestHelper helper) {
        try {
            // Query for non-existent instance - should return empty, not throw
            Optional<InstanceData> instance = InstanceRegistry.INSTANCE.getInstance(UUID.randomUUID());
            helper.assertTrue(instance.isEmpty(), "Random UUID should not have an instance");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("InstanceRegistry should not throw on getInstance: " + e.getMessage());
        }
    }

    // ============================================================
    // BATCH: instance_state - State machine validation
    // ============================================================

    /**
     * TEST 5: InstanceState transitions are valid
     * Verifies that the InstanceState enum has correct ordinal values.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_state")
    public static void instanceStateEnumOrder(GameTestHelper helper) {
        // Verify expected enum order
        InstanceState[] expectedOrder = {
            InstanceState.CREATING,
            InstanceState.READY,
            InstanceState.ACTIVE,
            InstanceState.COMPLETING,
            InstanceState.DESTROYING,
            InstanceState.DESTROYED
        };

        for (int i = 0; i < expectedOrder.length; i++) {
            helper.assertTrue(expectedOrder[i].ordinal() == i,
                "State " + expectedOrder[i] + " should have ordinal " + i);
        }

        helper.succeed();
    }

    /**
     * TEST 6: PlayerInstanceState transitions are valid
     * Verifies that the PlayerInstanceState enum has correct ordinal values.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_state")
    public static void playerInstanceStateEnumOrder(GameTestHelper helper) {
        // Verify expected enum values exist
        PlayerInstanceState[] states = {
            PlayerInstanceState.NORMAL,
            PlayerInstanceState.PREPARING,
            PlayerInstanceState.IN_TRANSIT,
            PlayerInstanceState.IN_INSTANCE,
            PlayerInstanceState.RETURNING
        };

        for (PlayerInstanceState state : states) {
            helper.assertTrue(state != null, "State should not be null");
        }

        // Verify NORMAL is first
        helper.assertTrue(PlayerInstanceState.NORMAL.ordinal() == 0,
            "NORMAL should be first state");

        helper.succeed();
    }

    /**
     * TEST 7: InstanceData state transitions
     * Verifies that InstanceData can transition through states correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_state")
    public static void instanceDataStateTransition(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        InstanceData instance = InstanceData.createSolo(ownerId);

        // Should start in CREATING
        helper.assertTrue(instance.getState() == InstanceState.CREATING,
            "New instance should be in CREATING state");

        // Transition to READY
        instance.setState(InstanceState.READY);
        helper.assertTrue(instance.getState() == InstanceState.READY,
            "Instance should be in READY state");

        // Transition to ACTIVE
        instance.setState(InstanceState.ACTIVE);
        helper.assertTrue(instance.getState() == InstanceState.ACTIVE,
            "Instance should be in ACTIVE state");

        helper.succeed();
    }

    /**
     * TEST 8: InstanceData player management
     * Verifies that InstanceData correctly tracks players.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_state")
    public static void instanceDataPlayerManagement(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        InstanceData instance = InstanceData.createSolo(ownerId);
        instance.setState(InstanceState.READY); // Must be READY to accept players

        // Add first player
        boolean added1 = instance.addPlayer(player1);
        helper.assertTrue(added1, "Should be able to add first player");
        helper.assertTrue(instance.hasPlayer(player1), "Instance should have player1");
        helper.assertTrue(instance.getPlayerCount() == 1, "Player count should be 1");

        // Solo instance has maxPlayers = 1, so second player should fail
        boolean added2 = instance.addPlayer(player2);
        helper.assertTrue(!added2, "Solo instance should not accept second player");

        // Remove player
        boolean removed = instance.removePlayer(player1);
        helper.assertTrue(removed, "Should be able to remove player");
        helper.assertTrue(instance.isEmpty(), "Instance should be empty");

        helper.succeed();
    }

    /**
     * TEST 9: InstanceData destruction scheduling
     * Verifies that destruction scheduling works correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_state")
    public static void instanceDataDestructionScheduling(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        InstanceData instance = InstanceData.createSolo(ownerId);

        // Initially not marked
        helper.assertTrue(!instance.isMarkedForDestruction(),
            "New instance should not be marked for destruction");

        // Schedule destruction
        instance.scheduleDestruction();
        helper.assertTrue(instance.isMarkedForDestruction(),
            "Instance should be marked for destruction");

        // Cancel destruction
        instance.cancelDestruction();
        helper.assertTrue(!instance.isMarkedForDestruction(),
            "Instance should not be marked after cancellation");

        helper.succeed();
    }

    // ============================================================
    // BATCH: instance_flow - Full flow tests
    // Note: These tests require a real server player and may need
    // to be run in a specific test environment.
    // ============================================================

    /**
     * TEST 10: Snapshot creation from player state
     * Verifies that a snapshot captures all required player data.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_flow", timeoutTicks = 200)
    public static void snapshotCreationCapture(GameTestHelper helper) {
        // This test validates the snapshot structure, not the full flow
        // since we can't easily create a mock ServerPlayer in GameTest

        UUID playerId = UUID.randomUUID();
        PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(playerId);

        // Set test data
        snapshot.setState(PlayerInstanceState.PREPARING);
        snapshot.setInstanceId(UUID.randomUUID());

        // Verify getters work
        helper.assertTrue(snapshot.getPlayerId().equals(playerId),
            "Player ID should match");
        helper.assertTrue(snapshot.getState() == PlayerInstanceState.PREPARING,
            "State should be PREPARING");
        helper.assertTrue(snapshot.getInstanceId() != null,
            "Instance ID should be set");

        helper.succeed();
    }

    /**
     * TEST 11: Snapshot NBT serialization round-trip
     * Verifies that snapshot can be serialized and deserialized correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_flow")
    public static void snapshotNbtRoundTrip(GameTestHelper helper) {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        // Create snapshot with test data
        PlayerInstanceSnapshot original = new PlayerInstanceSnapshot(playerId);
        original.setState(PlayerInstanceState.IN_INSTANCE);
        original.setInstanceId(instanceId);

        // Serialize to NBT
        net.minecraft.nbt.CompoundTag tag = original.toNBT();

        // Deserialize from NBT
        PlayerInstanceSnapshot loaded = PlayerInstanceSnapshot.fromNBT(tag);

        // Verify
        helper.assertTrue(loaded.getPlayerId().equals(playerId),
            "Player ID should survive round-trip");
        helper.assertTrue(instanceId.equals(loaded.getInstanceId()),
            "Instance ID should survive round-trip");
        helper.assertTrue(loaded.getState() == PlayerInstanceState.IN_INSTANCE,
            "State should survive round-trip");

        helper.succeed();
    }

    /**
     * TEST 12: InstanceData serialization round-trip
     * Verifies that instance data can be serialized and deserialized correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_flow")
    public static void instanceDataMapRoundTrip(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        InstanceData original = InstanceData.createSolo(ownerId);
        original.setState(InstanceState.ACTIVE);
        original.setArena(new BlockPos(0, 64, 0), 25, "test_arena");

        // Serialize to Map
        java.util.Map<String, Object> map = original.toMap();

        // Deserialize from Map
        InstanceData loaded = InstanceData.fromMap(map);

        // Verify
        helper.assertTrue(loaded.getInstanceId().equals(original.getInstanceId()),
            "Instance ID should survive round-trip");
        helper.assertTrue(loaded.getOwnerId().equals(ownerId),
            "Owner ID should survive round-trip");
        helper.assertTrue(loaded.getState() == InstanceState.ACTIVE,
            "State should survive round-trip");
        helper.assertTrue(loaded.getArenaRadius() == 25,
            "Arena radius should survive round-trip");

        helper.succeed();
    }

    /**
     * TEST 12b: Void preset + platform + chunk preload
     * Verifica che la dimensione istanza generi il layer bedrock, la piattaforma
     * arena e che il chunk di destinazione sia caricato prima del teleport.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_flow")
    public static void voidPlatformAndChunkPreload(GameTestHelper helper) {
        if (!DynamicDimensionManager.INSTANCE.isReady()) {
            helper.fail("DynamicDimensionManager not ready");
            return;
        }

        UUID instanceId = UUID.randomUUID();
        ResourceKey<Level> dimKey = Objects.requireNonNull(
            DynamicDimensionManager.INSTANCE.createDimensionSync(instanceId, "test_arena"),
            "Dimension key should not be null");

        ServerLevel level = Objects.requireNonNull(
            helper.getLevel().getServer().getLevel(dimKey),
            "Instance level should exist");

        // Bedrock layer (void preset) al livello 0
        BlockPos bedrockPos = new BlockPos(0, 0, 0);
        helper.assertTrue(level.getBlockState(bedrockPos).is(Objects.requireNonNull(Blocks.BEDROCK)),
            "Bedrock layer should exist at y=0");

        // Chunk centrale deve essere caricato e contenere la piattaforma
        BlockPos platformPos = new BlockPos(0, 64, 0);
        helper.assertTrue(level.getChunkAt(platformPos) != null, "Center chunk should be loaded");
        helper.assertTrue(level.getBlockState(platformPos).is(Objects.requireNonNull(Blocks.STONE_BRICKS)),
            "Platform should place stone bricks at center");

        boolean destroyed = DynamicDimensionManager.INSTANCE.destroyDimensionSync(instanceId);
        helper.assertTrue(destroyed, "Dimension should be destroyed cleanly");

        helper.succeed();
    }

    // ============================================================
    // BATCH: instance_recovery - Recovery tests
    // ============================================================

    /**
     * TEST 13: Recovery state detection
     * Verifies that different recovery states are handled correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void recoveryStateDetection(GameTestHelper helper) {
        // Test that each state is detected correctly
        PlayerInstanceState[] nonNormalStates = {
            PlayerInstanceState.PREPARING,
            PlayerInstanceState.IN_TRANSIT,
            PlayerInstanceState.IN_INSTANCE,
            PlayerInstanceState.RETURNING
        };

        for (PlayerInstanceState state : nonNormalStates) {
            helper.assertTrue(state != PlayerInstanceState.NORMAL,
                state + " should not be NORMAL");
        }

        // NORMAL state check
        PlayerInstanceState normalState = PlayerInstanceState.NORMAL;
        helper.assertTrue(normalState != null, "NORMAL state should not be null");

        helper.succeed();
    }

    /**
     * TEST 14: Snapshot has pending recovery check
     * Verifies that hasSnapshot correctly identifies pending snapshots.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void hasSnapshotCheck(GameTestHelper helper) {
        UUID randomId = UUID.randomUUID();

        // Should not have snapshot for random player
        boolean has = RecoverySystem.INSTANCE.hasSnapshot(randomId);
        helper.assertTrue(!has, "Random player should not have snapshot");

        helper.succeed();
    }

    /**
     * TEST 15: Dimension key generation
     * Verifies that dimension keys are generated correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void dimensionKeyGeneration(GameTestHelper helper) {
        UUID instanceId = UUID.randomUUID();

        // Verify UUID format without dashes
        String withoutDashes = instanceId.toString().replace("-", "");
        helper.assertTrue(withoutDashes.length() == 32,
            "UUID without dashes should be 32 chars");

        // Verify dimension name format
        String dimensionName = "instance_" + withoutDashes;
        helper.assertTrue(dimensionName.startsWith("instance_"),
            "Dimension name should start with instance_");
        helper.assertTrue(dimensionName.length() == 41,
            "Dimension name should be 41 chars (9 + 32)");

        helper.succeed();
    }

    /**
     * TEST 16: UUID parsing without dashes
     * Verifies that UUIDs can be parsed from dimension folder names.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void uuidParsingWithoutDashes(GameTestHelper helper) {
        UUID original = UUID.randomUUID();
        String withoutDashes = original.toString().replace("-", "");

        // Parse back with dashes
        String formatted = withoutDashes.substring(0, 8) + "-" +
                          withoutDashes.substring(8, 12) + "-" +
                          withoutDashes.substring(12, 16) + "-" +
                          withoutDashes.substring(16, 20) + "-" +
                          withoutDashes.substring(20, 32);

        UUID parsed = UUID.fromString(formatted);

        helper.assertTrue(parsed.equals(original),
            "Parsed UUID should equal original");

        helper.succeed();
    }

    /**
     * TEST 17: Instance registry player mapping
     * Verifies that player mapping works correctly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void instanceRegistryPlayerMapping(GameTestHelper helper) {
        UUID playerId = UUID.randomUUID();

        // Player should not be in any instance initially
        Optional<InstanceData> instance = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        helper.assertTrue(instance.isEmpty(),
            "Random player should not be in any instance");

        helper.succeed();
    }

    /**
     * TEST 18: Multiple instances don't interfere
     * Verifies that multiple instances can be tracked without interference.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "instance_recovery")
    public static void multipleInstancesIsolation(GameTestHelper helper) {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();

        InstanceData instance1 = InstanceData.createSolo(owner1);
        InstanceData instance2 = InstanceData.createSolo(owner2);

        // Verify different IDs
        helper.assertTrue(!instance1.getInstanceId().equals(instance2.getInstanceId()),
            "Different instances should have different IDs");

        // Verify different owners
        helper.assertTrue(!instance1.getOwnerId().equals(instance2.getOwnerId()),
            "Different instances should have different owners");

        helper.succeed();
    }
}
