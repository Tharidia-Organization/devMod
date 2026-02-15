package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.TestBootstrap;

@DisplayName("HologramPosition")
class HologramPositionTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    @Nested
    @DisplayName("compact constructor")
    class CompactConstructor {
        @Test
        @DisplayName("rejects null blockPos")
        void rejectsNullBlockPos() {
            assertThrows(NullPointerException.class,
                () -> new HologramPosition(null, Vec3.ZERO, OVERWORLD, 0, 0));
        }

        @Test
        @DisplayName("rejects null offset")
        void rejectsNullOffset() {
            assertThrows(NullPointerException.class,
                () -> new HologramPosition(BlockPos.ZERO, null, OVERWORLD, 0, 0));
        }

        @Test
        @DisplayName("rejects null dimension")
        void rejectsNullDimension() {
            assertThrows(NullPointerException.class,
                () -> new HologramPosition(BlockPos.ZERO, Vec3.ZERO, null, 0, 0));
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {
        @Test
        @DisplayName("atBlock creates position at given block")
        void atBlock() {
            BlockPos pos = new BlockPos(10, 64, -5);
            var hPos = HologramPosition.atBlock(pos, OVERWORLD);
            assertEquals(pos, hPos.blockPos());
            assertEquals(Vec3.ZERO, hPos.offset());
            assertEquals(OVERWORLD, hPos.dimension());
            assertEquals(0f, hPos.yaw());
            assertEquals(0f, hPos.pitch());
        }

        @Test
        @DisplayName("aboveBlock creates position one block up")
        void aboveBlock() {
            BlockPos pos = new BlockPos(10, 64, -5);
            var hPos = HologramPosition.aboveBlock(pos, OVERWORLD);
            assertEquals(pos.above(), hPos.blockPos());
        }
    }

    @Nested
    @DisplayName("getExactPosition")
    class GetExactPosition {
        @Test
        @DisplayName("returns center of block plus offset")
        void centerPlusOffset() {
            var hPos = new HologramPosition(
                new BlockPos(10, 64, -5),
                new Vec3(0.5, 1.0, -0.5),
                OVERWORLD, 0, 0);
            Vec3 exact = hPos.getExactPosition();
            assertEquals(10.5 + 0.5, exact.x, 0.001);
            assertEquals(64.5 + 1.0, exact.y, 0.001);
            assertEquals(-4.5 - 0.5, exact.z, 0.001);
        }

        @Test
        @DisplayName("ZERO returns center of origin block")
        void zeroCase() {
            Vec3 exact = HologramPosition.ZERO.getExactPosition();
            assertEquals(0.5, exact.x, 0.001);
            assertEquals(0.5, exact.y, 0.001);
            assertEquals(0.5, exact.z, 0.001);
        }
    }

    @Nested
    @DisplayName("with methods")
    class WithMethods {
        @Test
        @DisplayName("withOffset returns new position with offset")
        void withOffset() {
            var pos = HologramPosition.ZERO;
            var modified = pos.withOffset(new Vec3(1.0, 2.0, 3.0));
            assertEquals(new Vec3(1.0, 2.0, 3.0), modified.offset());
            assertEquals(pos.blockPos(), modified.blockPos());
            assertEquals(pos.dimension(), modified.dimension());
        }

        @Test
        @DisplayName("withRotation returns new position with rotation")
        void withRotation() {
            var pos = HologramPosition.ZERO;
            var modified = pos.withRotation(45f, 30f);
            assertEquals(45f, modified.yaw(), 0.001f);
            assertEquals(30f, modified.pitch(), 0.001f);
            assertEquals(pos.blockPos(), modified.blockPos());
        }
    }

    @Nested
    @DisplayName("STREAM_CODEC")
    class StreamCodec {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = new HologramPosition(
                new BlockPos(100, -60, 200),
                new Vec3(0.25, 0.5, 0.75),
                OVERWORLD,
                45.5f,
                -30.0f);
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramPosition.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramPosition.STREAM_CODEC.decode(buf);
                assertEquals(original.blockPos(), decoded.blockPos());
                assertEquals(original.offset().x, decoded.offset().x, 0.001);
                assertEquals(original.offset().y, decoded.offset().y, 0.001);
                assertEquals(original.offset().z, decoded.offset().z, 0.001);
                assertEquals(original.dimension(), decoded.dimension());
                assertEquals(original.yaw(), decoded.yaw(), 0.001f);
                assertEquals(original.pitch(), decoded.pitch(), 0.001f);
            } finally {
                buf.release();
            }
        }
    }

    @Test
    @DisplayName("toShortString delegates to BlockPos")
    void toShortString() {
        var pos = HologramPosition.atBlock(new BlockPos(10, 64, -5), OVERWORLD);
        assertEquals(new BlockPos(10, 64, -5).toShortString(), pos.toShortString());
    }
}
