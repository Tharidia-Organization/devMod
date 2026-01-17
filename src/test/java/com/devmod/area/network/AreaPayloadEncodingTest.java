package com.devmod.area.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;

/**
 * Unit tests for Area network payload encoding/decoding.
 */
@DisplayName("Area Payload Encoding")
class AreaPayloadEncodingTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // BuildAreaPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("BuildAreaPayload")
    class BuildAreaPayloadTests {

        @Test
        @DisplayName("encode/decode round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            UUID areaId = UUID.randomUUID();
            BuildAreaPayload original = new BuildAreaPayload(areaId, true, true);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BuildAreaPayload.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            BuildAreaPayload decoded = BuildAreaPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.areaId(), decoded.areaId());
            assertEquals(original.clearFirst(), decoded.clearFirst());
            assertEquals(original.useMultiTick(), decoded.useMultiTick());
            buf.release();
        }

        @Test
        @DisplayName("immediate factory creates correct payload")
        void immediate_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildAreaPayload payload = BuildAreaPayload.immediate(areaId);

            assertEquals(areaId, payload.areaId());
            assertFalse(payload.clearFirst());
            assertFalse(payload.useMultiTick());
        }

        @Test
        @DisplayName("withClear factory creates correct payload")
        void withClear_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildAreaPayload payload = BuildAreaPayload.withClear(areaId);

            assertEquals(areaId, payload.areaId());
            assertTrue(payload.clearFirst());
            assertFalse(payload.useMultiTick());
        }

        @Test
        @DisplayName("multiTick factory creates correct payload")
        void multiTick_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildAreaPayload payload = BuildAreaPayload.multiTick(areaId, true);

            assertEquals(areaId, payload.areaId());
            assertTrue(payload.clearFirst());
            assertTrue(payload.useMultiTick());
        }

        @Test
        @DisplayName("estimatedSize returns reasonable value")
        void estimatedSize_returnsReasonableValue() {
            BuildAreaPayload payload = BuildAreaPayload.immediate(UUID.randomUUID());
            assertTrue(payload.estimatedSize() > 0);
            assertTrue(payload.estimatedSize() < 100); // Should be small
        }

        @Test
        @DisplayName("type returns correct type")
        void type_returnsCorrectType() {
            BuildAreaPayload payload = BuildAreaPayload.immediate(UUID.randomUUID());
            assertEquals(BuildAreaPayload.TYPE, payload.type());
        }
    }

    // ========================================================================
    // BuildStatusPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("BuildStatusPayload")
    class BuildStatusPayloadTests {

        @Test
        @DisplayName("encode/decode round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload original = new BuildStatusPayload(
                areaId, BuildStatusPayload.Status.IN_PROGRESS, 75);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BuildStatusPayload.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            BuildStatusPayload decoded = BuildStatusPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.areaId(), decoded.areaId());
            assertEquals(original.status(), decoded.status());
            assertEquals(original.progressPercent(), decoded.progressPercent());
            buf.release();
        }

        @Test
        @DisplayName("constructor clamps progress to 0-100")
        void constructor_clampsProgress() {
            UUID areaId = UUID.randomUUID();

            BuildStatusPayload negative = new BuildStatusPayload(
                areaId, BuildStatusPayload.Status.IN_PROGRESS, -50);
            assertEquals(0, negative.progressPercent());

            BuildStatusPayload overMax = new BuildStatusPayload(
                areaId, BuildStatusPayload.Status.IN_PROGRESS, 150);
            assertEquals(100, overMax.progressPercent());
        }

        @Test
        @DisplayName("started factory creates correct payload")
        void started_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.started(areaId);

            assertEquals(areaId, payload.areaId());
            assertEquals(BuildStatusPayload.Status.STARTED, payload.status());
            assertEquals(0, payload.progressPercent());
        }

        @Test
        @DisplayName("progress factory creates correct payload")
        void progress_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.progress(areaId, 50);

            assertEquals(areaId, payload.areaId());
            assertEquals(BuildStatusPayload.Status.IN_PROGRESS, payload.status());
            assertEquals(50, payload.progressPercent());
        }

        @Test
        @DisplayName("paused factory creates correct payload")
        void paused_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.paused(areaId, 30);

            assertEquals(BuildStatusPayload.Status.PAUSED, payload.status());
            assertEquals(30, payload.progressPercent());
        }

        @Test
        @DisplayName("resumed factory creates correct payload")
        void resumed_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.resumed(areaId, 30);

            assertEquals(BuildStatusPayload.Status.RESUMED, payload.status());
            assertEquals(30, payload.progressPercent());
        }

        @Test
        @DisplayName("completed factory creates correct payload")
        void completed_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.completed(areaId);

            assertEquals(BuildStatusPayload.Status.COMPLETED, payload.status());
            assertEquals(100, payload.progressPercent());
        }

        @Test
        @DisplayName("failed factory creates correct payload")
        void failed_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.failed(areaId);

            assertEquals(BuildStatusPayload.Status.FAILED, payload.status());
            assertEquals(0, payload.progressPercent());
        }

        @Test
        @DisplayName("queued factory creates correct payload")
        void queued_createsCorrectPayload() {
            UUID areaId = UUID.randomUUID();
            BuildStatusPayload payload = BuildStatusPayload.queued(areaId, 3);

            assertEquals(BuildStatusPayload.Status.QUEUED, payload.status());
            assertEquals(3, payload.progressPercent());
        }

        @Test
        @DisplayName("Status.byOrdinal handles invalid ordinals")
        void statusByOrdinal_handlesInvalid() {
            assertEquals(BuildStatusPayload.Status.FAILED,
                BuildStatusPayload.Status.byOrdinal(-1));
            assertEquals(BuildStatusPayload.Status.FAILED,
                BuildStatusPayload.Status.byOrdinal(100));
        }

        @Test
        @DisplayName("Status.byOrdinal returns correct status for valid ordinals")
        void statusByOrdinal_validOrdinals() {
            assertEquals(BuildStatusPayload.Status.STARTED,
                BuildStatusPayload.Status.byOrdinal(0));
            assertEquals(BuildStatusPayload.Status.COMPLETED,
                BuildStatusPayload.Status.byOrdinal(4));
        }

        @Test
        @DisplayName("all statuses round-trip correctly")
        void allStatuses_roundTrip() {
            UUID areaId = UUID.randomUUID();
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));

            for (BuildStatusPayload.Status status : BuildStatusPayload.Status.values()) {
                BuildStatusPayload original = new BuildStatusPayload(areaId, status, 50);

                buf.clear();
                BuildStatusPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                BuildStatusPayload decoded = BuildStatusPayload.STREAM_CODEC.decode(buf);

                assertEquals(status, decoded.status(),
                    "Status " + status + " should round-trip correctly");
            }

            buf.release();
        }
    }

    // ========================================================================
    // AreaPreviewPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("AreaPreviewPayload")
    class AreaPreviewPayloadTests {

        @Test
        @DisplayName("encode/decode round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            BlockPos center = new BlockPos(100, 64, 200);
            List<AreaPreviewPayload.PreviewBlock> blocks = List.of(
                new AreaPreviewPayload.PreviewBlock(center, AreaPreviewPayload.PreviewBlockType.CENTER),
                new AreaPreviewPayload.PreviewBlock(center.above(), AreaPreviewPayload.PreviewBlockType.EDGE)
            );

            AreaPreviewPayload original = new AreaPreviewPayload(
                center, 32, 64, 16, "RECTANGULAR", blocks,
                true, "minecraft:forest", "NATURAL", 12345L
            );

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaPreviewPayload.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaPreviewPayload decoded = AreaPreviewPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.center(), decoded.center());
            assertEquals(original.width(), decoded.width());
            assertEquals(original.length(), decoded.length());
            assertEquals(original.height(), decoded.height());
            assertEquals(original.shape(), decoded.shape());
            assertEquals(original.outlineBlocks().size(), decoded.outlineBlocks().size());
            assertEquals(original.useBiome(), decoded.useBiome());
            assertEquals(original.biomeId(), decoded.biomeId());
            assertEquals(original.terrainStyle(), decoded.terrainStyle());
            assertEquals(original.biomeSeed(), decoded.biomeSeed());
            buf.release();
        }

        @Test
        @DisplayName("boundingBox factory creates valid preview")
        void boundingBox_createsValidPreview() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaPreviewPayload payload = AreaPreviewPayload.boundingBox(
                center, 32, 32, 16, "RECTANGULAR");

            assertEquals(center, payload.center());
            assertEquals(32, payload.width());
            assertEquals(32, payload.length());
            assertEquals(16, payload.height());
            assertEquals("RECTANGULAR", payload.shape());
            assertFalse(payload.useBiome());
            assertFalse(payload.outlineBlocks().isEmpty());
        }

        @Test
        @DisplayName("boundingBox with biome creates valid preview")
        void boundingBoxWithBiome_createsValidPreview() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaPreviewPayload payload = AreaPreviewPayload.boundingBox(
                center, 32, 32, 16, "CIRCULAR",
                true, "minecraft:desert", "AMPLIFIED", 99999L);

            assertTrue(payload.useBiome());
            assertEquals("minecraft:desert", payload.biomeId());
            assertEquals("AMPLIFIED", payload.terrainStyle());
            assertEquals(99999L, payload.biomeSeed());
        }

        @Test
        @DisplayName("fromShape creates accurate shape preview")
        void fromShape_createsAccuratePreview() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 10, 64);

            AreaPreviewPayload payload = AreaPreviewPayload.fromShape(
                center, AreaShape.CIRCULAR, dims, null);

            assertEquals(center, payload.center());
            assertEquals(20, payload.width());
            assertEquals(20, payload.length());
            assertEquals(10, payload.height());
            assertEquals("CIRCULAR", payload.shape());
            assertFalse(payload.outlineBlocks().isEmpty());
        }

        @Test
        @DisplayName("fromShape limits blocks to MAX_PREVIEW_BLOCKS")
        void fromShape_limitsBlockCount() {
            BlockPos center = new BlockPos(0, 64, 0);
            // Large area that would generate many outline blocks
            AreaDimensions dims = new AreaDimensions(256, 256, 128, 64);

            AreaPreviewPayload payload = AreaPreviewPayload.fromShape(
                center, AreaShape.RECTANGULAR, dims, null);

            assertTrue(payload.outlineBlocks().size() <= AreaPreviewPayload.MAX_PREVIEW_BLOCKS);
        }

        @Test
        @DisplayName("PreviewBlock round-trip")
        void previewBlock_roundTrip() {
            AreaPreviewPayload.PreviewBlock original = new AreaPreviewPayload.PreviewBlock(
                new BlockPos(10, 20, 30), AreaPreviewPayload.PreviewBlockType.WALL);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaPreviewPayload.PreviewBlock.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaPreviewPayload.PreviewBlock decoded = AreaPreviewPayload.PreviewBlock.STREAM_CODEC.decode(buf);

            assertEquals(original.pos(), decoded.pos());
            assertEquals(original.type(), decoded.type());
            buf.release();
        }

        @Test
        @DisplayName("all PreviewBlockTypes round-trip")
        void allPreviewBlockTypes_roundTrip() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BlockPos pos = new BlockPos(0, 0, 0);

            for (AreaPreviewPayload.PreviewBlockType type : AreaPreviewPayload.PreviewBlockType.values()) {
                AreaPreviewPayload.PreviewBlock original = new AreaPreviewPayload.PreviewBlock(pos, type);

                buf.clear();
                AreaPreviewPayload.PreviewBlock.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                AreaPreviewPayload.PreviewBlock decoded = AreaPreviewPayload.PreviewBlock.STREAM_CODEC.decode(buf);

                assertEquals(type, decoded.type(),
                    "PreviewBlockType " + type + " should round-trip correctly");
            }

            buf.release();
        }

        @Test
        @DisplayName("estimatedSize returns positive value")
        void estimatedSize_returnsPositiveValue() {
            AreaPreviewPayload payload = AreaPreviewPayload.boundingBox(
                BlockPos.ZERO, 32, 32, 16, "RECTANGULAR");

            assertTrue(payload.estimatedSize() > 0);
        }

        @Test
        @DisplayName("empty outline blocks round-trip")
        void emptyOutlineBlocks_roundTrip() {
            AreaPreviewPayload original = new AreaPreviewPayload(
                BlockPos.ZERO, 10, 10, 5, "RECTANGULAR", List.of(),
                false, "", "NATURAL", 0L);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaPreviewPayload.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaPreviewPayload decoded = AreaPreviewPayload.STREAM_CODEC.decode(buf);

            assertTrue(decoded.outlineBlocks().isEmpty());
            buf.release();
        }
    }

    // ========================================================================
    // Type and ID Tests
    // ========================================================================

    @Nested
    @DisplayName("Payload Types and IDs")
    class PayloadTypesTests {

        @Test
        @DisplayName("BuildAreaPayload has unique ID")
        void buildAreaPayload_hasUniqueId() {
            assertNotNull(BuildAreaPayload.ID);
            assertEquals("devmod", BuildAreaPayload.ID.getNamespace());
            assertEquals("build_area", BuildAreaPayload.ID.getPath());
        }

        @Test
        @DisplayName("BuildStatusPayload has unique ID")
        void buildStatusPayload_hasUniqueId() {
            assertNotNull(BuildStatusPayload.ID);
            assertEquals("devmod", BuildStatusPayload.ID.getNamespace());
            assertEquals("build_status", BuildStatusPayload.ID.getPath());
        }

        @Test
        @DisplayName("AreaPreviewPayload has unique ID")
        void areaPreviewPayload_hasUniqueId() {
            assertNotNull(AreaPreviewPayload.ID);
            assertEquals("devmod", AreaPreviewPayload.ID.getNamespace());
            assertEquals("area_preview", AreaPreviewPayload.ID.getPath());
        }

        @Test
        @DisplayName("All IDs are distinct")
        void allIds_areDistinct() {
            assertNotEquals(BuildAreaPayload.ID, BuildStatusPayload.ID);
            assertNotEquals(BuildAreaPayload.ID, AreaPreviewPayload.ID);
            assertNotEquals(BuildStatusPayload.ID, AreaPreviewPayload.ID);
        }
    }
}
