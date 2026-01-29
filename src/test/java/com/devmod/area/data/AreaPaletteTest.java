package com.devmod.area.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;

import com.devmod.TestBootstrap;

/**
 * Unit tests for AreaPalette record.
 */
@DisplayName("AreaPalette")
class AreaPaletteTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Constructor Tests
    // ========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates defensive copy of materials map")
        void constructor_defensiveCopy() {
            Map<String, String> originalMap = new HashMap<>();
            originalMap.put("floor", "minecraft:stone");

            AreaPalette palette = new AreaPalette(originalMap, "test", false);

            // Modify original map
            originalMap.put("wall", "minecraft:dirt");

            // Palette should not be affected
            assertFalse(palette.materials().containsKey("wall"));
            assertEquals(1, palette.size());
        }

        @Test
        @DisplayName("materials map is immutable")
        void constructor_immutableMaterials() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");

            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertThrows(UnsupportedOperationException.class, () -> {
                palette.materials().put("wall", "minecraft:dirt");
            });
        }

        @Test
        @DisplayName("throws on null materials")
        @SuppressWarnings("NullAway")
        void constructor_nullMaterialsThrows() {
            assertThrows(NullPointerException.class, () -> {
                new AreaPalette(null, "test", false);
            });
        }

        @Test
        @DisplayName("throws on null presetId")
        @SuppressWarnings("NullAway")
        void constructor_nullPresetIdThrows() {
            assertThrows(NullPointerException.class, () -> {
                new AreaPalette(new HashMap<>(), null, false);
            });
        }
    }

    // ========================================================================
    // Factory Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("empty creates empty palette with preset")
        void empty_createsEmptyPalette() {
            AreaPalette palette = AreaPalette.empty("default");

            assertTrue(palette.materials().isEmpty());
            assertEquals("default", palette.presetId());
            assertFalse(palette.customized());
        }

        @Test
        @DisplayName("empty returns non-null")
        void empty_returnsNonNull() {
            assertNotNull(AreaPalette.empty("test"));
        }
    }

    // ========================================================================
    // With Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("With Methods")
    class WithMethodsTests {

        @Test
        @DisplayName("withMaterial creates new instance with added material")
        void withMaterial_addsNewMaterial() {
            AreaPalette original = AreaPalette.empty("test");

            AreaPalette updated = original.withMaterial("floor", "minecraft:stone");

            assertEquals("minecraft:stone", updated.getBlockId("floor"));
            assertTrue(updated.customized());
            assertNull(original.getBlockId("floor")); // Original unchanged
        }

        @Test
        @DisplayName("withMaterial preserves existing materials")
        void withMaterial_preservesExisting() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            AreaPalette original = new AreaPalette(materials, "test", false);

            AreaPalette updated = original.withMaterial("wall", "minecraft:bricks");

            assertEquals("minecraft:stone", updated.getBlockId("floor"));
            assertEquals("minecraft:bricks", updated.getBlockId("wall"));
        }

        @Test
        @DisplayName("withMaterial replaces existing material")
        void withMaterial_replacesExisting() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            AreaPalette original = new AreaPalette(materials, "test", false);

            AreaPalette updated = original.withMaterial("floor", "minecraft:dirt");

            assertEquals("minecraft:dirt", updated.getBlockId("floor"));
        }

        @Test
        @DisplayName("withPreset creates new instance with preset")
        void withPreset_createsWithPreset() {
            AreaPalette original = AreaPalette.empty("old");
            Map<String, String> newMaterials = new HashMap<>();
            newMaterials.put("floor", "minecraft:obsidian");

            AreaPalette updated = original.withPreset("new-preset", newMaterials);

            assertEquals("new-preset", updated.presetId());
            assertEquals("minecraft:obsidian", updated.getBlockId("floor"));
            assertFalse(updated.customized());
        }
    }

    // ========================================================================
    // Query Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodsTests {

        @Test
        @DisplayName("getBlockId returns material for existing key")
        void getBlockId_existingKey() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertEquals("minecraft:stone", palette.getBlockId("floor"));
        }

        @Test
        @DisplayName("getBlockId returns null for missing key")
        void getBlockId_missingKey() {
            AreaPalette palette = AreaPalette.empty("test");

            assertNull(palette.getBlockId("nonexistent"));
        }

        @Test
        @DisplayName("getBlockState returns block for valid ID")
        void getBlockState_validId() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertEquals(Blocks.STONE.defaultBlockState(), palette.getBlockState("floor"));
        }

        @Test
        @DisplayName("getBlockState returns air for missing key")
        void getBlockState_missingKey() {
            AreaPalette palette = AreaPalette.empty("test");

            assertEquals(Blocks.AIR.defaultBlockState(), palette.getBlockState("nonexistent"));
        }

        @Test
        @DisplayName("getBlockState returns air for invalid block ID")
        void getBlockState_invalidBlockId() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "invalid:block_that_does_not_exist");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertEquals(Blocks.AIR.defaultBlockState(), palette.getBlockState("floor"));
        }

        @Test
        @DisplayName("getBlockState returns air for blank value")
        void getBlockState_blankValue() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "   ");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertEquals(Blocks.AIR.defaultBlockState(), palette.getBlockState("floor"));
        }

        @Test
        @DisplayName("hasMaterial returns true for existing non-blank key")
        void hasMaterial_existingNonBlank() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertTrue(palette.hasMaterial("floor"));
        }

        @Test
        @DisplayName("hasMaterial returns false for missing key")
        void hasMaterial_missingKey() {
            AreaPalette palette = AreaPalette.empty("test");

            assertFalse(palette.hasMaterial("floor"));
        }

        @Test
        @DisplayName("hasMaterial returns false for blank value")
        void hasMaterial_blankValue() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "   ");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertFalse(palette.hasMaterial("floor"));
        }

        @Test
        @DisplayName("size returns material count")
        void size_returnsMaterialCount() {
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            materials.put("wall", "minecraft:bricks");
            materials.put("ceiling", "minecraft:oak_planks");
            AreaPalette palette = new AreaPalette(materials, "test", false);

            assertEquals(3, palette.size());
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
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:stone");
            materials.put("wall", "minecraft:bricks");
            AreaPalette original = new AreaPalette(materials, "industrial", true);

            Tag nbt = AreaPalette.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaPalette decoded = AreaPalette.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertEquals(original.materials(), decoded.materials());
            assertEquals(original.presetId(), decoded.presetId());
            assertEquals(original.customized(), decoded.customized());
        }

        @Test
        @DisplayName("codec handles empty materials")
        void codec_emptyMaterials() {
            AreaPalette original = AreaPalette.empty("empty-preset");

            Tag nbt = AreaPalette.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaPalette decoded = AreaPalette.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertTrue(decoded.materials().isEmpty());
            assertEquals("empty-preset", decoded.presetId());
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
            Map<String, String> materials = new HashMap<>();
            materials.put("floor", "minecraft:deepslate");
            materials.put("wall", "minecraft:polished_blackstone");
            AreaPalette original = new AreaPalette(materials, "nether", true);

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaPalette.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaPalette decoded = AreaPalette.STREAM_CODEC.decode(buf);

            assertEquals(original.materials(), decoded.materials());
            assertEquals(original.presetId(), decoded.presetId());
            assertEquals(original.customized(), decoded.customized());
            buf.release();
        }

        @Test
        @DisplayName("stream codec handles empty palette")
        void streamCodec_emptyPalette() {
            AreaPalette original = Objects.requireNonNull(AreaPalette.empty("empty"));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            AreaPalette.STREAM_CODEC.encode(buf, original);

            buf.readerIndex(0);
            AreaPalette decoded = AreaPalette.STREAM_CODEC.decode(buf);

            assertTrue(decoded.materials().isEmpty());
            buf.release();
        }
    }

    // ========================================================================
    // Constants Tests
    // ========================================================================

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("MIN_REQUIRED_MATERIALS is 3")
        void minRequiredMaterials_isThree() {
            assertEquals(3, AreaPalette.MIN_REQUIRED_MATERIALS);
        }
    }
}
