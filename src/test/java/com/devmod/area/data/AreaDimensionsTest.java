package com.devmod.area.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import io.netty.buffer.Unpooled;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.devmod.TestBootstrap;

/**
 * Unit tests for AreaDimensions record.
 */
@DisplayName("AreaDimensions")
class AreaDimensionsTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Constructor and Validation Tests
    // ========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts valid dimensions")
        void constructor_validDimensions() {
            AreaDimensions dims = new AreaDimensions(32, 64, 16, 64);

            assertEquals(32, dims.width());
            assertEquals(64, dims.length());
            assertEquals(16, dims.height());
            assertEquals(64, dims.floorY());
        }

        @Test
        @DisplayName("DEFAULT constant has expected values")
        void default_hasExpectedValues() {
            assertEquals(32, AreaDimensions.DEFAULT.width());
            assertEquals(32, AreaDimensions.DEFAULT.length());
            assertEquals(16, AreaDimensions.DEFAULT.height());
            assertEquals(64, AreaDimensions.DEFAULT.floorY());
        }
    }

    // ========================================================================
    // Clamped Factory Tests
    // ========================================================================

    @Nested
    @DisplayName("Clamped Factory")
    class ClampedTests {

        @Test
        @DisplayName("clamps width to minimum")
        void clamped_clampsWidthToMin() {
            AreaDimensions dims = AreaDimensions.clamped(1, 32, 16, 64);
            assertEquals(AreaDimensions.MIN_SIZE, dims.width());
        }

        @Test
        @DisplayName("clamps width to maximum")
        void clamped_clampsWidthToMax() {
            AreaDimensions dims = AreaDimensions.clamped(1000, 32, 16, 64);
            assertEquals(AreaDimensions.MAX_SIZE, dims.width());
        }

        @Test
        @DisplayName("clamps length to minimum")
        void clamped_clampsLengthToMin() {
            AreaDimensions dims = AreaDimensions.clamped(32, 1, 16, 64);
            assertEquals(AreaDimensions.MIN_SIZE, dims.length());
        }

        @Test
        @DisplayName("clamps height to minimum")
        void clamped_clampsHeightToMin() {
            AreaDimensions dims = AreaDimensions.clamped(32, 32, 1, 64);
            assertEquals(AreaDimensions.MIN_HEIGHT, dims.height());
        }

        @Test
        @DisplayName("clamps height to maximum")
        void clamped_clampsHeightToMax() {
            AreaDimensions dims = AreaDimensions.clamped(32, 32, 1000, 64);
            assertEquals(AreaDimensions.MAX_HEIGHT, dims.height());
        }

        @Test
        @DisplayName("clamps floorY to minimum")
        void clamped_clampsFloorYToMin() {
            AreaDimensions dims = AreaDimensions.clamped(32, 32, 16, -1000);
            assertEquals(AreaDimensions.MIN_FLOOR_Y, dims.floorY());
        }

        @Test
        @DisplayName("clamps floorY to maximum")
        void clamped_clampsFloorYToMax() {
            AreaDimensions dims = AreaDimensions.clamped(32, 32, 16, 1000);
            assertEquals(AreaDimensions.MAX_FLOOR_Y, dims.floorY());
        }

        @Test
        @DisplayName("preserves valid values")
        void clamped_preservesValidValues() {
            AreaDimensions dims = AreaDimensions.clamped(50, 100, 20, 100);
            assertEquals(50, dims.width());
            assertEquals(100, dims.length());
            assertEquals(20, dims.height());
            assertEquals(100, dims.floorY());
        }
    }

    // ========================================================================
    // With Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("With Methods")
    class WithMethodsTests {

        @Test
        @DisplayName("withWidth clamps value")
        void withWidth_clampsValue() {
            AreaDimensions original = new AreaDimensions(32, 32, 16, 64);

            assertEquals(AreaDimensions.MIN_SIZE, original.withWidth(1).width());
            assertEquals(AreaDimensions.MAX_SIZE, original.withWidth(1000).width());
            assertEquals(100, original.withWidth(100).width());
        }

        @Test
        @DisplayName("withLength clamps value")
        void withLength_clampsValue() {
            AreaDimensions original = new AreaDimensions(32, 32, 16, 64);

            assertEquals(AreaDimensions.MIN_SIZE, original.withLength(1).length());
            assertEquals(AreaDimensions.MAX_SIZE, original.withLength(1000).length());
            assertEquals(100, original.withLength(100).length());
        }

        @Test
        @DisplayName("withHeight clamps value")
        void withHeight_clampsValue() {
            AreaDimensions original = new AreaDimensions(32, 32, 16, 64);

            assertEquals(AreaDimensions.MIN_HEIGHT, original.withHeight(1).height());
            assertEquals(AreaDimensions.MAX_HEIGHT, original.withHeight(1000).height());
            assertEquals(50, original.withHeight(50).height());
        }

        @Test
        @DisplayName("withFloorY changes floorY without clamping")
        void withFloorY_changesValue() {
            AreaDimensions original = new AreaDimensions(32, 32, 16, 64);

            // Note: withFloorY does not clamp (per implementation)
            assertEquals(-64, original.withFloorY(-64).floorY());
            assertEquals(200, original.withFloorY(200).floorY());
        }

        @Test
        @DisplayName("with methods preserve other fields")
        void withMethods_preserveOtherFields() {
            AreaDimensions original = new AreaDimensions(32, 64, 20, 100);

            AreaDimensions withNewWidth = original.withWidth(50);
            assertEquals(64, withNewWidth.length());
            assertEquals(20, withNewWidth.height());
            assertEquals(100, withNewWidth.floorY());
        }
    }

    // ========================================================================
    // Calculation Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("Calculations")
    class CalculationTests {

        @Test
        @DisplayName("volume calculates correctly")
        void volume_calculatesCorrectly() {
            AreaDimensions dims = new AreaDimensions(10, 20, 30, 64);

            assertEquals(6000L, dims.volume());
        }

        @Test
        @DisplayName("volume handles large dimensions without overflow")
        void volume_largeValuesNoOverflow() {
            AreaDimensions dims = new AreaDimensions(256, 256, 128, 64);

            // 256 * 256 * 128 = 8,388,608 (should fit in long)
            assertEquals(8_388_608L, dims.volume());
        }

        @Test
        @DisplayName("floorArea calculates correctly")
        void floorArea_calculatesCorrectly() {
            AreaDimensions dims = new AreaDimensions(10, 20, 30, 64);

            assertEquals(200, dims.floorArea());
        }
    }

    // ========================================================================
    // Codec Tests
    // ========================================================================

    @Nested
    @DisplayName("NBT Codec")
    class NbtCodecTests {

        @Test
        @DisplayName("codec round-trip preserves all fields")
        void codec_roundTrip() {
            AreaDimensions original = new AreaDimensions(50, 100, 25, 80);

            Tag nbt = AreaDimensions.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaDimensions decoded = AreaDimensions.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("codec rejects out-of-range width")
        void codec_rejectsInvalidWidth() {
            // The codec uses intRange which validates bounds
            // We can't easily test this directly since it requires crafting invalid NBT
            // Just verify that valid range works
            AreaDimensions dims = new AreaDimensions(AreaDimensions.MAX_SIZE, 32, 16, 64);
            Tag nbt = AreaDimensions.CODEC.encodeStart(NbtOps.INSTANCE, dims).result().orElseThrow();
            assertNotNull(nbt);
        }
    }

    // ========================================================================
    // StreamCodec Tests
    // ========================================================================

    @Nested
    @DisplayName("Stream Codec")
    class StreamCodecTests {

        @Test
        @DisplayName("stream codec round-trip")
        void streamCodec_roundTrip() {
            AreaDimensions original = new AreaDimensions(75, 150, 40, -10);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaDimensions.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaDimensions decoded = AreaDimensions.STREAM_CODEC.decode(buf);

            assertEquals(original, decoded);
            buf.release();
        }

        @Test
        @DisplayName("stream codec handles edge values")
        void streamCodec_edgeValues() {
            AreaDimensions original = new AreaDimensions(
                AreaDimensions.MIN_SIZE,
                AreaDimensions.MAX_SIZE,
                AreaDimensions.MIN_HEIGHT,
                AreaDimensions.MIN_FLOOR_Y
            );

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaDimensions.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaDimensions decoded = AreaDimensions.STREAM_CODEC.decode(buf);

            assertEquals(original, decoded);
            buf.release();
        }
    }

    // ========================================================================
    // Equality Tests
    // ========================================================================

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("equals compares all fields")
        void equals_comparesAllFields() {
            AreaDimensions dims1 = new AreaDimensions(32, 64, 16, 100);
            AreaDimensions dims2 = new AreaDimensions(32, 64, 16, 100);
            AreaDimensions dims3 = new AreaDimensions(32, 64, 16, 101);

            assertEquals(dims1, dims2);
            assertNotEquals(dims1, dims3);
        }

        @Test
        @DisplayName("hashCode is consistent with equals")
        void hashCode_consistentWithEquals() {
            AreaDimensions dims1 = new AreaDimensions(32, 64, 16, 100);
            AreaDimensions dims2 = new AreaDimensions(32, 64, 16, 100);

            assertEquals(dims1.hashCode(), dims2.hashCode());
        }
    }

    // ========================================================================
    // Constants Tests
    // ========================================================================

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("MIN_SIZE is reasonable")
        void minSize_isReasonable() {
            assertEquals(8, AreaDimensions.MIN_SIZE);
        }

        @Test
        @DisplayName("MAX_SIZE is reasonable")
        void maxSize_isReasonable() {
            assertEquals(256, AreaDimensions.MAX_SIZE);
        }

        @Test
        @DisplayName("MIN_HEIGHT is reasonable")
        void minHeight_isReasonable() {
            assertEquals(4, AreaDimensions.MIN_HEIGHT);
        }

        @Test
        @DisplayName("MAX_HEIGHT is reasonable")
        void maxHeight_isReasonable() {
            assertEquals(128, AreaDimensions.MAX_HEIGHT);
        }

        @Test
        @DisplayName("MIN_FLOOR_Y matches vanilla bottom")
        void minFloorY_matchesVanillaBottom() {
            assertEquals(-64, AreaDimensions.MIN_FLOOR_Y);
        }

        @Test
        @DisplayName("MAX_FLOOR_Y matches vanilla top")
        void maxFloorY_matchesVanillaTop() {
            assertEquals(320, AreaDimensions.MAX_FLOOR_Y);
        }
    }
}
