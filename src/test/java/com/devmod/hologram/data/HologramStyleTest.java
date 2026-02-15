package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;

import com.devmod.TestBootstrap;

@DisplayName("HologramStyle")
class HologramStyleTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("compact constructor validation")
    class Validation {
        @Test
        @DisplayName("clamps scale below 0.1 to 0.1")
        void clampsScaleBelow() {
            var style = new HologramStyle(0xFFFFFF, 0, 0.01f, 1.0f, HologramBillboard.CENTER, false, false, 0);
            assertEquals(0.1f, style.scale(), 0.001f);
        }

        @Test
        @DisplayName("clamps scale above 4.0 to 4.0")
        void clampsScaleAbove() {
            var style = new HologramStyle(0xFFFFFF, 0, 10.0f, 1.0f, HologramBillboard.CENTER, false, false, 0);
            assertEquals(4.0f, style.scale(), 0.001f);
        }

        @Test
        @DisplayName("clamps lineSpacing below 0.5 to 0.5")
        void clampsSpacingBelow() {
            var style = new HologramStyle(0xFFFFFF, 0, 1.0f, 0.1f, HologramBillboard.CENTER, false, false, 0);
            assertEquals(0.5f, style.lineSpacing(), 0.001f);
        }

        @Test
        @DisplayName("clamps lineSpacing above 3.0 to 3.0")
        void clampsSpacingAbove() {
            var style = new HologramStyle(0xFFFFFF, 0, 1.0f, 5.0f, HologramBillboard.CENTER, false, false, 0);
            assertEquals(3.0f, style.lineSpacing(), 0.001f);
        }

        @Test
        @DisplayName("throws on null billboard")
        void throwsNullBillboard() {
            assertThrows(NullPointerException.class,
                () -> new HologramStyle(0xFFFFFF, 0, 1.0f, 1.0f, null, false, false, 0));
        }

        @Test
        @DisplayName("accepts valid values unchanged")
        void acceptsValid() {
            var style = new HologramStyle(0xFFFFFF, 0x40000000, 2.5f, 1.5f,
                HologramBillboard.FIXED, true, true, 0xFF0000);
            assertEquals(2.5f, style.scale(), 0.001f);
            assertEquals(1.5f, style.lineSpacing(), 0.001f);
        }
    }

    @Nested
    @DisplayName("hasGlow")
    class HasGlow {
        @Test
        @DisplayName("returns false when glowColor is 0")
        void noGlow() {
            assertFalse(HologramStyle.DEFAULT.hasGlow());
        }

        @Test
        @DisplayName("returns true when glowColor is non-zero")
        void hasGlow() {
            var style = HologramStyle.DEFAULT.toBuilder().glowColor(0xFF0000).build();
            assertTrue(style.hasGlow());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {
        @Test
        @DisplayName("toBuilder produces equivalent style")
        void toBuilderRoundTrip() {
            var original = new HologramStyle(0x123456, 0x654321, 2.0f, 1.5f,
                HologramBillboard.VERTICAL, true, true, 0xABCDEF);
            var rebuilt = original.toBuilder().build();
            assertEquals(original, rebuilt);
        }

        @Test
        @DisplayName("builder sets all fields")
        void builderSetsFields() {
            var style = new HologramStyle.Builder()
                .textColor(0x111111)
                .backgroundColor(0x222222)
                .scale(3.0f)
                .lineSpacing(2.0f)
                .billboard(HologramBillboard.HORIZONTAL)
                .shadow(false)
                .seeThrough(true)
                .glowColor(0x333333)
                .build();

            assertEquals(0x111111, style.textColor());
            assertEquals(0x222222, style.backgroundColor());
            assertEquals(3.0f, style.scale(), 0.001f);
            assertEquals(2.0f, style.lineSpacing(), 0.001f);
            assertEquals(HologramBillboard.HORIZONTAL, style.billboard());
            assertFalse(style.shadow());
            assertTrue(style.seeThrough());
            assertEquals(0x333333, style.glowColor());
        }
    }

    @Nested
    @DisplayName("STREAM_CODEC")
    class StreamCodec {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new HologramStyle(0x123456, 0x654321, 2.0f, 1.5f,
                HologramBillboard.VERTICAL, true, true, 0xABCDEF);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramStyle.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramStyle.STREAM_CODEC.decode(buf);
                assertEquals(original, decoded);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("round-trip with default style")
        void roundTripDefault() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramStyle.STREAM_CODEC.encode(buf, HologramStyle.DEFAULT);
                buf.readerIndex(0);
                var decoded = HologramStyle.STREAM_CODEC.decode(buf);
                assertEquals(HologramStyle.DEFAULT, decoded);
            } finally {
                buf.release();
            }
        }
    }

    @Test
    @DisplayName("DEFAULT has expected values")
    void defaultValues() {
        assertEquals(0xFFFFFF, HologramStyle.DEFAULT.textColor());
        assertEquals(0x40000000, HologramStyle.DEFAULT.backgroundColor());
        assertEquals(1.0f, HologramStyle.DEFAULT.scale(), 0.001f);
        assertEquals(1.0f, HologramStyle.DEFAULT.lineSpacing(), 0.001f);
        assertEquals(HologramBillboard.CENTER, HologramStyle.DEFAULT.billboard());
        assertTrue(HologramStyle.DEFAULT.shadow());
        assertFalse(HologramStyle.DEFAULT.seeThrough());
        assertEquals(0, HologramStyle.DEFAULT.glowColor());
    }
}
