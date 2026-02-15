package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.TestBootstrap;
import com.devmod.hologram.runtime.HologramNaming;

@DisplayName("HologramDefinition")
class HologramDefinitionTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    private HologramDefinition createTestDef() {
        return HologramDefinition.builder()
            .id(UUID.fromString("12345678-1234-1234-1234-123456789abc"))
            .label("Test Hologram")
            .type(HologramType.DYNAMIC)
            .lines(List.of("Line 1", "Line 2"))
            .style(HologramStyle.DEFAULT)
            .position(HologramPosition.atBlock(new BlockPos(10, 64, -5), OVERWORLD))
            .options(HologramOptions.DEFAULT)
            .creator(UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789"))
            .createdAt(1000L)
            .updatedAt(2000L)
            .revision(3)
            .build();
    }

    @Nested
    @DisplayName("compact constructor validation")
    class CompactConstructor {
        @Test
        @DisplayName("rejects null id")
        void rejectsNullId() {
            assertThrows(NullPointerException.class, () ->
                new HologramDefinition(null, "label", HologramType.STATIC, List.of(),
                    HologramStyle.DEFAULT, HologramPosition.ZERO, HologramOptions.DEFAULT,
                    null, 0, 0, 1));
        }

        @Test
        @DisplayName("rejects null label")
        void rejectsNullLabel() {
            assertThrows(NullPointerException.class, () ->
                new HologramDefinition(UUID.randomUUID(), null, HologramType.STATIC, List.of(),
                    HologramStyle.DEFAULT, HologramPosition.ZERO, HologramOptions.DEFAULT,
                    null, 0, 0, 1));
        }

        @Test
        @DisplayName("truncates long label to MAX_LABEL_LENGTH")
        void truncatesLabel() {
            String longLabel = "x".repeat(100);
            var def = new HologramDefinition(UUID.randomUUID(), longLabel, HologramType.STATIC,
                List.of(), HologramStyle.DEFAULT, HologramPosition.ZERO, HologramOptions.DEFAULT,
                null, 0, 0, 1);
            assertEquals(HologramNaming.MAX_LABEL_LENGTH, def.label().length());
        }

        @Test
        @DisplayName("truncates lines exceeding MAX_LINES")
        void truncatesExcessLines() {
            List<String> manyLines = new ArrayList<>(Collections.nCopies(32, "line"));
            var def = new HologramDefinition(UUID.randomUUID(), "test", HologramType.STATIC,
                manyLines, HologramStyle.DEFAULT, HologramPosition.ZERO, HologramOptions.DEFAULT,
                null, 0, 0, 1);
            assertEquals(HologramNaming.MAX_LINES, def.lines().size());
        }

        @Test
        @DisplayName("truncates individual long lines")
        void truncatesLongLines() {
            String longLine = "x".repeat(200);
            var def = new HologramDefinition(UUID.randomUUID(), "test", HologramType.STATIC,
                List.of(longLine), HologramStyle.DEFAULT, HologramPosition.ZERO,
                HologramOptions.DEFAULT, null, 0, 0, 1);
            assertEquals(HologramNaming.MAX_LINE_LENGTH, def.lines().get(0).length());
        }

        @Test
        @DisplayName("lines are immutable after construction")
        void linesImmutable() {
            var def = createTestDef();
            assertThrows(UnsupportedOperationException.class, () -> def.lines().add("extra"));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {
        @Test
        @DisplayName("generates UUID when not provided")
        void generatesUUID() {
            var def = HologramDefinition.builder()
                .label("Auto ID")
                .build();
            assertNotNull(def.id());
        }

        @Test
        @DisplayName("sets timestamps when not provided")
        void setsTimestamps() {
            var def = HologramDefinition.builder()
                .label("Test")
                .build();
            assertTrue(def.createdAt() > 0);
            assertTrue(def.updatedAt() > 0);
        }

        @Test
        @DisplayName("addLine appends to lines")
        void addLine() {
            var def = HologramDefinition.builder()
                .addLine("First")
                .addLine("Second")
                .build();
            assertEquals(2, def.lines().size());
            assertEquals("First", def.lines().get(0));
            assertEquals("Second", def.lines().get(1));
        }

        @Test
        @DisplayName("fromPreset copies preset properties")
        void fromPreset() {
            var def = HologramDefinition.builder()
                .fromPreset(HologramPreset.WELCOME, new BlockPos(0, 64, 0), OVERWORLD)
                .build();
            assertEquals("Welcome", def.label());
            assertEquals(HologramType.DYNAMIC, def.type());
            assertFalse(def.lines().isEmpty());
        }

        @Test
        @DisplayName("position with BlockPos and dimension creates aboveBlock")
        void positionFromBlockPos() {
            BlockPos pos = new BlockPos(10, 64, -5);
            var def = HologramDefinition.builder()
                .position(pos, OVERWORLD)
                .build();
            assertEquals(pos.above(), def.position().blockPos());
        }

        @Test
        @DisplayName("toBuilder produces equivalent definition")
        void toBuilderRoundTrip() {
            var original = createTestDef();
            var rebuilt = original.toBuilder().build();
            assertEquals(original.id(), rebuilt.id());
            assertEquals(original.label(), rebuilt.label());
            assertEquals(original.type(), rebuilt.type());
            assertEquals(original.lines(), rebuilt.lines());
            assertEquals(original.style(), rebuilt.style());
            assertEquals(original.revision(), rebuilt.revision());
        }
    }

    @Nested
    @DisplayName("withUpdate")
    class WithUpdate {
        @Test
        @DisplayName("increments revision")
        void incrementsRevision() {
            var def = createTestDef();
            var updated = def.withUpdate(List.of("new line"), def.style(), def.options());
            assertEquals(def.revision() + 1, updated.revision());
        }

        @Test
        @DisplayName("preserves id")
        void preservesId() {
            var def = createTestDef();
            var updated = def.withUpdate(List.of("new line"), def.style(), def.options());
            assertEquals(def.id(), updated.id());
        }

        @Test
        @DisplayName("updates lines")
        void updatesLines() {
            var def = createTestDef();
            var newLines = List.of("updated");
            var updated = def.withUpdate(newLines, def.style(), def.options());
            assertEquals(newLines, updated.lines());
        }
    }

    @Nested
    @DisplayName("withPosition")
    class WithPosition {
        @Test
        @DisplayName("changes position and increments revision")
        void changesPosition() {
            var def = createTestDef();
            var newPos = HologramPosition.atBlock(new BlockPos(99, 99, 99), OVERWORLD);
            var updated = def.withPosition(newPos);
            assertEquals(newPos, updated.position());
            assertEquals(def.revision() + 1, updated.revision());
            assertEquals(def.id(), updated.id());
        }
    }

    @Nested
    @DisplayName("STREAM_CODEC")
    class StreamCodec {
        @Test
        @DisplayName("round-trip preserves all fields")
        void roundTrip() {
            var original = createTestDef();
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramDefinition.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramDefinition.STREAM_CODEC.decode(buf);
                assertEquals(original.id(), decoded.id());
                assertEquals(original.label(), decoded.label());
                assertEquals(original.type(), decoded.type());
                assertEquals(original.lines(), decoded.lines());
                assertEquals(original.style(), decoded.style());
                assertEquals(original.options(), decoded.options());
                assertEquals(original.creatorUUID(), decoded.creatorUUID());
                assertEquals(original.createdAt(), decoded.createdAt());
                assertEquals(original.updatedAt(), decoded.updatedAt());
                assertEquals(original.revision(), decoded.revision());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("round-trip without creator UUID")
        void roundTripNoCreator() {
            var original = HologramDefinition.builder()
                .id(UUID.randomUUID())
                .label("No Creator")
                .lines(List.of("test"))
                .createdAt(1000L)
                .updatedAt(2000L)
                .build();
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                HologramDefinition.STREAM_CODEC.encode(buf, original);
                buf.readerIndex(0);
                var decoded = HologramDefinition.STREAM_CODEC.decode(buf);
                assertNull(decoded.creatorUUID());
                assertEquals(original.id(), decoded.id());
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("rejects negative line count")
        void rejectsNegativeLineCount() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            try {
                // Write UUID
                buf.writeUUID(UUID.randomUUID());
                // Write label
                buf.writeUtf("test", HologramNaming.MAX_LABEL_LENGTH);
                // Write type
                buf.writeEnum(HologramType.STATIC);
                // Write negative line count
                buf.writeVarInt(-1);

                buf.readerIndex(0);
                assertThrows(IllegalArgumentException.class,
                    () -> HologramDefinition.STREAM_CODEC.decode(buf));
            } finally {
                buf.release();
            }
        }
    }
}
