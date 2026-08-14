package com.devmod.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-level guard for the one recorder that feeds {@code DebugFeature.BLOCK_UPDATES}.
 *
 * <p>A mixin that is not listed in the config is loaded by nothing and fails silently, which
 * would turn the feature back into a toggle that renders nothing - the exact failure this
 * feature was built to avoid. These checks need no Minecraft runtime, following the same
 * pattern as {@code ActionBaselineValidationTest}.
 */
@DisplayName("Block update recorder wiring")
class BlockUpdateRecorderWiringTest {

    private static final Path MIXIN_CONFIG_PATH = Paths.get("src/main/resources/devmod.mixins.json");
    private static final Path RECORDER_MIXIN_PATH = Paths.get(
        "src/main/java/com/devmod/mixin/BlockStateNeighborUpdateMixin.java");
    private static final Path DEBUG_PACKETS_MIXIN_PATH = Paths.get(
        "src/main/java/com/devmod/mixin/DebugPacketsMixin.java");

    private static String mixinConfig;
    private static String recorderMixin;
    private static String debugPacketsMixin;

    @BeforeAll
    static void readSources() throws IOException {
        mixinConfig = Files.readString(MIXIN_CONFIG_PATH);
        recorderMixin = Files.readString(RECORDER_MIXIN_PATH);
        debugPacketsMixin = Files.readString(DEBUG_PACKETS_MIXIN_PATH);
    }

    @Test
    @DisplayName("Recorder mixin is registered in the mixin config")
    void recorderMixinIsRegistered() {
        assertTrue(mixinConfig.contains("\"BlockStateNeighborUpdateMixin\""),
            "BlockStateNeighborUpdateMixin must be listed in devmod.mixins.json or it never loads");
    }

    @Test
    @DisplayName("Recorder hooks the dispatch site, not the base implementation")
    void recorderTargetsDispatchSite() {
        // BlockBehaviour.neighborChanged is overridden without super by most redstone blocks, and
        // Level.neighborChanged is bypassed by updateNeighborsAt; only the dispatch site sees all.
        assertTrue(recorderMixin.contains("BlockBehaviour.BlockStateBase.class"),
            "Recorder must target BlockStateBase");
        assertTrue(recorderMixin.contains("\"handleNeighborChanged\""),
            "Recorder must inject into handleNeighborChanged");
        assertTrue(recorderMixin.contains("recordBlockUpdate"),
            "Recorder must feed NativeDebugSender.recordBlockUpdate");
    }

    @Test
    @DisplayName("Only one recorder feeds the block-update buffer")
    void singleRecorder() {
        assertFalse(debugPacketsMixin.contains("recordBlockUpdate"),
            "DebugPacketsMixin must not also record: sendNeighborsUpdatePacket only fires for "
                + "blocks that do not override neighborChanged, so it is a redundant subset");
        // Matches the injection only - the class javadoc still names the method to explain why
        // it is not used, and that prose must not trip this check.
        assertFalse(debugPacketsMixin.contains("method = \"sendNeighborsUpdatePacket\""),
            "The redundant sendNeighborsUpdatePacket injection should stay removed");
    }
}
