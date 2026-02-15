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

@DisplayName("HologramOptions")
class HologramOptionsTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("DEFAULT")
    class Default {
        @Test
        @DisplayName("default is enabled")
        void enabled() {
            assertTrue(HologramOptions.DEFAULT.enabled());
        }

        @Test
        @DisplayName("default is not locked")
        void notLocked() {
            assertFalse(HologramOptions.DEFAULT.locked());
        }

        @Test
        @DisplayName("default refresh interval is 0 (use global)")
        void refreshZero() {
            assertEquals(0, HologramOptions.DEFAULT.refreshInterval());
        }

        @Test
        @DisplayName("default visibility range is 0 (use global)")
        void visibilityZero() {
            assertEquals(0, HologramOptions.DEFAULT.visibilityRange());
        }

        @Test
        @DisplayName("default has no linked zone")
        void noLinkedZone() {
            assertNull(HologramOptions.DEFAULT.linkedZone());
        }
    }

    @Nested
    @DisplayName("getEffectiveRefreshInterval")
    class EffectiveRefresh {
        @Test
        @DisplayName("returns global default when 0")
        void returnsDefault() {
            assertEquals(HologramOptions.DEFAULT_REFRESH_INTERVAL,
                HologramOptions.DEFAULT.getEffectiveRefreshInterval());
        }

        @Test
        @DisplayName("returns custom value when positive")
        void returnsCustom() {
            var opts = new HologramOptions(true, false, 100, 0, null);
            assertEquals(100, opts.getEffectiveRefreshInterval());
        }
    }

    @Nested
    @DisplayName("getEffectiveVisibilityRange")
    class EffectiveVisibility {
        @Test
        @DisplayName("returns global default when 0")
        void returnsDefault() {
            assertEquals(HologramOptions.DEFAULT_VISIBILITY_RANGE,
                HologramOptions.DEFAULT.getEffectiveVisibilityRange());
        }

        @Test
        @DisplayName("returns custom value when positive")
        void returnsCustom() {
            var opts = new HologramOptions(true, false, 0, 100, null);
            assertEquals(100, opts.getEffectiveVisibilityRange());
        }
    }

    @Nested
    @DisplayName("hasLinkedZone")
    class HasLinkedZone {
        @Test
        @DisplayName("false when null")
        void falseForNull() {
            assertFalse(new HologramOptions(true, false, 0, 0, null).hasLinkedZone());
        }

        @Test
        @DisplayName("false when empty")
        void falseForEmpty() {
            assertFalse(new HologramOptions(true, false, 0, 0, "").hasLinkedZone());
        }

        @Test
        @DisplayName("true when set")
        void trueWhenSet() {
            assertTrue(new HologramOptions(true, false, 0, 0, "spawn_zone").hasLinkedZone());
        }
    }

    @Nested
    @DisplayName("withEnabled / withLocked")
    class WithMethods {
        @Test
        @DisplayName("withEnabled creates copy with new enabled state")
        void withEnabled() {
            var opts = HologramOptions.DEFAULT.withEnabled(false);
            assertFalse(opts.enabled());
            assertFalse(opts.locked()); // other fields preserved
        }

        @Test
        @DisplayName("withLocked creates copy with new locked state")
        void withLocked() {
            var opts = HologramOptions.DEFAULT.withLocked(true);
            assertTrue(opts.locked());
            assertTrue(opts.enabled()); // other fields preserved
        }
    }

    @Nested
    @DisplayName("STREAM_CODEC")
    class StreamCodec {
        @Test
        @DisplayName("round-trip preserves all fields without linked zone")
        void roundTripNoZone() {
            var original = new HologramOptions(true, true, 200, 64, null);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramOptions.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramOptions.STREAM_CODEC.decode(buf);
                assertEquals(original, decoded);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("round-trip preserves linked zone")
        void roundTripWithZone() {
            var original = new HologramOptions(false, true, 100, 48, "my_zone");
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramOptions.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramOptions.STREAM_CODEC.decode(buf);
                assertEquals(original, decoded);
            } finally {
                buf.release();
            }
        }
    }
}
