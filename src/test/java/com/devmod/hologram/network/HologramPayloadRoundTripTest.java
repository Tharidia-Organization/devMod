package com.devmod.hologram.network;

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
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramStyle;

@DisplayName("Hologram Network Payloads")
class HologramPayloadRoundTripTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("DeleteHologramPayload")
    class Delete {
        @Test
        @DisplayName("round-trip preserves UUID")
        void roundTrip() {
            UUID id = UUID.randomUUID();
            var original = new DeleteHologramPayload(id);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                DeleteHologramPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = DeleteHologramPayload.STREAM_CODEC.decode(buf);
                assertEquals(id, decoded.hologramId());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("type returns correct type")
        void type() {
            assertEquals(DeleteHologramPayload.TYPE, new DeleteHologramPayload(UUID.randomUUID()).type());
        }

        @Test
        @DisplayName("estimatedSize is 16 (UUID)")
        void estimatedSize() {
            assertEquals(16, new DeleteHologramPayload(UUID.randomUUID()).estimatedSize());
        }

        @Test
        @DisplayName("rejects null UUID")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> new DeleteHologramPayload(null));
        }
    }

    @Nested
    @DisplayName("HologramSyncPayload")
    class Sync {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new HologramSyncPayload(
                UUID.randomUUID(), 5, List.of("Line 1", "Line 2", "Line 3"));
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramSyncPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramSyncPayload.STREAM_CODEC.decode(buf);
                assertEquals(original.hologramId(), decoded.hologramId());
                assertEquals(original.revision(), decoded.revision());
                assertEquals(original.lines(), decoded.lines());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("truncates excess lines in constructor")
        void truncatesExcessLines() {
            List<String> many = new java.util.ArrayList<>(java.util.Collections.nCopies(32, "x"));
            var payload = new HologramSyncPayload(UUID.randomUUID(), 1, many);
            assertEquals(16, payload.lines().size()); // MAX_LINES
        }

        @Test
        @DisplayName("truncates long individual lines")
        void truncatesLongLines() {
            String longLine = "x".repeat(200);
            var payload = new HologramSyncPayload(UUID.randomUUID(), 1, List.of(longLine));
            assertEquals(128, payload.lines().get(0).length()); // MAX_LINE_LENGTH
        }

        @Test
        @DisplayName("estimatedSize is positive")
        void estimatedSizePositive() {
            var payload = new HologramSyncPayload(UUID.randomUUID(), 1, List.of("test"));
            assertTrue(payload.estimatedSize() > 0);
        }

        @Test
        @DisplayName("type returns correct type")
        void type() {
            assertEquals(HologramSyncPayload.TYPE,
                new HologramSyncPayload(UUID.randomUUID(), 1, List.of()).type());
        }
    }

    @Nested
    @DisplayName("SaveHologramPayload")
    class Save {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new SaveHologramPayload(
                UUID.randomUUID(),
                List.of("Content 1", "Content 2"),
                HologramStyle.DEFAULT,
                HologramOptions.DEFAULT,
                7);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                SaveHologramPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = SaveHologramPayload.STREAM_CODEC.decode(buf);
                assertEquals(original.hologramId(), decoded.hologramId());
                assertEquals(original.lines(), decoded.lines());
                assertEquals(original.style(), decoded.style());
                assertEquals(original.options(), decoded.options());
                assertEquals(original.expectedRevision(), decoded.expectedRevision());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("truncates excess lines")
        void truncatesLines() {
            List<String> many = new java.util.ArrayList<>(java.util.Collections.nCopies(32, "x"));
            var payload = new SaveHologramPayload(UUID.randomUUID(), many,
                HologramStyle.DEFAULT, HologramOptions.DEFAULT, 1);
            assertEquals(16, payload.lines().size());
        }

        @Test
        @DisplayName("estimatedSize is positive")
        void estimatedSizePositive() {
            var payload = new SaveHologramPayload(UUID.randomUUID(), List.of("test"),
                HologramStyle.DEFAULT, HologramOptions.DEFAULT, 1);
            assertTrue(payload.estimatedSize() > 0);
        }
    }

    @Nested
    @DisplayName("HologramPresetPayload")
    class Preset {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new HologramPresetPayload(new BlockPos(10, 64, -5), 3, true);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramPresetPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramPresetPayload.STREAM_CODEC.decode(buf);
                assertEquals(original.pos(), decoded.pos());
                assertEquals(original.slot(), decoded.slot());
                assertEquals(original.isSave(), decoded.isSave());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("isValidSlot returns true for 0-4")
        void validSlots() {
            for (int i = 0; i < HologramPresetPayload.MAX_PRESETS; i++) {
                assertTrue(new HologramPresetPayload(BlockPos.ZERO, i, false).isValidSlot());
            }
        }

        @Test
        @DisplayName("isValidSlot returns false for negative")
        void invalidNegative() {
            assertFalse(new HologramPresetPayload(BlockPos.ZERO, -1, false).isValidSlot());
        }

        @Test
        @DisplayName("isValidSlot returns false for MAX_PRESETS")
        void invalidMax() {
            assertFalse(new HologramPresetPayload(BlockPos.ZERO, HologramPresetPayload.MAX_PRESETS, false).isValidSlot());
        }

        @Test
        @DisplayName("estimatedSize is reasonable")
        void estimatedSize() {
            var payload = new HologramPresetPayload(BlockPos.ZERO, 0, false);
            assertTrue(payload.estimatedSize() > 0);
            assertTrue(payload.estimatedSize() < 64);
        }
    }

    @Nested
    @DisplayName("HologramOpenScreenPayload")
    class OpenScreen {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new HologramOpenScreenPayload(
                new BlockPos(10, 64, -5), 64, 2, true, false, 0b10101,
                true, true, false, true, 42, 0b111, true, 72, 3);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramOpenScreenPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramOpenScreenPayload.STREAM_CODEC.decode(buf);
                assertEquals(original, decoded);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("estimatedSize is reasonable")
        void estimatedSize() {
            var payload = new HologramOpenScreenPayload(
                BlockPos.ZERO, 32, 1, false, false, 0, false, false, false, false, 10, 0, false, 0, 1);
            assertTrue(payload.estimatedSize() > 0);
            assertTrue(payload.estimatedSize() < 128);
        }
    }

    @Nested
    @DisplayName("HologramConfigPayload")
    class Config {
        @Test
        @DisplayName("round-trip preserves all 16 fields")
        void roundTrip() {
            var original = new HologramConfigPayload(
                new BlockPos(10, 64, -5), 64, 2, true, false, true, 0b10101,
                true, true, false, true, 42, 0b111, true, 72, 3);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramConfigPayload.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramConfigPayload.STREAM_CODEC.decode(buf);
                assertEquals(original, decoded);
            } finally {
                buf.release();
            }
        }
    }
}
