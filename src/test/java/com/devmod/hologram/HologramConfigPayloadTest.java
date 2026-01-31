package com.devmod.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import com.devmod.TestBootstrap;
import com.devmod.hologram.network.HologramConfigPayload;

@DisplayName("HologramConfigPayload")
class HologramConfigPayloadTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("encode/decode round-trip preserves all fields")
    void roundTripPreservesFields() {
        HologramConfigPayload original = new HologramConfigPayload(
            new BlockPos(10, 64, -5),
            64,
            2,
            true,
            false,
            true,
            0b10101,
            true,
            true,
            false,
            true,
            42,
            0b111,
            true,
            72,
            3
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
        HologramConfigPayload.STREAM_CODEC.encode(buf, original);

        buf.readerIndex(0);
        HologramConfigPayload decoded = HologramConfigPayload.STREAM_CODEC.decode(buf);

        assertEquals(original, decoded);
        buf.release();
    }

    @Test
    @DisplayName("estimatedSize returns reasonable value")
    void estimatedSizeReturnsReasonableValue() {
        HologramConfigPayload payload = new HologramConfigPayload(
            BlockPos.ZERO, 32, 1, false, false, false, 0, false, false, false, false, 10, 0, false, 0, 1
        );
        assertTrue(payload.estimatedSize() > 0);
        assertTrue(payload.estimatedSize() < 256);
    }
}
