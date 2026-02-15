package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.clone.network.MannequinRotationPayload;
import com.devmod.clone.network.MannequinSkinPayload;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.clone.network.TelepadOpenScreenPayload;

/**
 * Tests for estimated wire sizes of clone network payloads.
 * Validates that estimatedSize() returns reasonable values consistent
 * with the actual encoding format (BlockPos=8, float=4, UUID=16, bool=1).
 */
class PayloadEstimatedSizeTest {

    private static final BlockPos TEST_POS = new BlockPos(10, 64, -20);

    @Nested
    @DisplayName("MannequinRotationPayload.estimatedSize")
    class MannequinRotationSizeTests {

        @Test
        @DisplayName("Size is 8 (BlockPos) + 4 (float) = 12")
        void expectedSize() {
            var payload = new MannequinRotationPayload(TEST_POS, 15.0f);
            assertEquals(12, payload.estimatedSize());
        }

        @Test
        @DisplayName("Size is constant regardless of rotation value")
        void sizeIndependentOfValue() {
            var a = new MannequinRotationPayload(TEST_POS, 0.0f);
            var b = new MannequinRotationPayload(TEST_POS, 359.9f);
            var c = new MannequinRotationPayload(TEST_POS, -180.0f);
            assertEquals(a.estimatedSize(), b.estimatedSize());
            assertEquals(b.estimatedSize(), c.estimatedSize());
        }

        @Test
        @DisplayName("Size is positive")
        void sizeIsPositive() {
            assertTrue(new MannequinRotationPayload(BlockPos.ZERO, 0f).estimatedSize() > 0);
        }
    }

    @Nested
    @DisplayName("MannequinSkinPayload.estimatedSize")
    class MannequinSkinSizeTests {

        @Test
        @DisplayName("Size with UUID: 8 + 1 + 16 = 25")
        void sizeWithUuid() {
            var payload = new MannequinSkinPayload(TEST_POS, UUID.randomUUID());
            assertEquals(25, payload.estimatedSize());
        }

        @Test
        @DisplayName("Size without UUID: 8 + 1 = 9")
        void sizeWithoutUuid() {
            var payload = new MannequinSkinPayload(TEST_POS, null);
            assertEquals(9, payload.estimatedSize());
        }

        @Test
        @DisplayName("Size with UUID is larger than without")
        void sizeWithUuidLarger() {
            var withUuid = new MannequinSkinPayload(TEST_POS, UUID.randomUUID());
            var withoutUuid = new MannequinSkinPayload(TEST_POS, null);
            assertTrue(withUuid.estimatedSize() > withoutUuid.estimatedSize());
        }
    }

    @Nested
    @DisplayName("TelepadConfigPayload.estimatedSize")
    class TelepadConfigSizeTests {

        @Test
        @DisplayName("Size includes BlockPos (8) plus string overhead")
        void minimumSize() {
            var payload = new TelepadConfigPayload(TEST_POS, "");
            // 8 (BlockPos) + varint(0) for empty string = 9
            assertTrue(payload.estimatedSize() >= 9);
        }

        @Test
        @DisplayName("Longer name increases estimated size")
        void longerNameIncreasesSize() {
            var shortName = new TelepadConfigPayload(TEST_POS, "ab");
            var longName = new TelepadConfigPayload(TEST_POS, "a".repeat(30));
            assertTrue(longName.estimatedSize() > shortName.estimatedSize());
        }

        @Test
        @DisplayName("Name exceeding max is truncated in size calculation")
        void oversizeName() {
            var normal = new TelepadConfigPayload(TEST_POS, "a".repeat(32));
            var oversize = new TelepadConfigPayload(TEST_POS, "a".repeat(1000));
            // Both should report the same size because truncation happens
            assertEquals(normal.estimatedSize(), oversize.estimatedSize());
        }
    }

    @Nested
    @DisplayName("TelepadOpenScreenPayload.estimatedSize")
    class TelepadOpenScreenSizeTests {

        @Test
        @DisplayName("Size matches TelepadConfigPayload for same data")
        void sizeMatchesConfig() {
            var config = new TelepadConfigPayload(TEST_POS, "home");
            var openScreen = new TelepadOpenScreenPayload(TEST_POS, "home");
            assertEquals(config.estimatedSize(), openScreen.estimatedSize());
        }

        @Test
        @DisplayName("Empty name gives minimum size")
        void emptyName() {
            var payload = new TelepadOpenScreenPayload(TEST_POS, "");
            assertTrue(payload.estimatedSize() >= 9);
        }
    }
}
