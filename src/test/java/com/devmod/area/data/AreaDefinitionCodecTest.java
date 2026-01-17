package com.devmod.area.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import com.devmod.TestBootstrap;

/**
 * Unit tests for AreaDefinition codec and builder functionality.
 */
@DisplayName("AreaDefinition")
class AreaDefinitionCodecTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // NBT Codec Tests
    // ========================================================================

    @Nested
    @DisplayName("NBT Codec")
    class NbtCodecTests {

        @Test
        @DisplayName("codec round-trip preserves all required fields")
        void codec_roundTrip_preservesAllFields() {
            UUID id = UUID.randomUUID();
            UUID creatorId = UUID.randomUUID();
            long now = System.currentTimeMillis();

            AreaDefinition original = new AreaDefinition(
                id,
                "Test Area",
                AreaGenerationType.CUSTOM,
                AreaShape.RECTANGULAR,
                new AreaDimensions(32, 64, 16, 100),
                new BlockPos(100, 200, 300),
                ResourceLocation.withDefaultNamespace("the_nether"),
                createTestPalette(),
                null, // biomeConfig
                AreaOptions.DEFAULT,
                creatorId,
                now,
                now + 1000,
                5,
                true,
                "test-zone",
                null // customShapeNbt
            );

            // Encode to NBT
            DataResult<Tag> encodeResult = AreaDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original);
            assertTrue(encodeResult.result().isPresent(), "Encoding should succeed");
            Tag nbt = encodeResult.result().get();

            // Decode from NBT
            DataResult<AreaDefinition> decodeResult = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, nbt);
            assertTrue(decodeResult.result().isPresent(), "Decoding should succeed: " + decodeResult.error());
            AreaDefinition decoded = decodeResult.result().get();

            // Verify all fields
            assertEquals(original.id(), decoded.id());
            assertEquals(original.name(), decoded.name());
            assertEquals(original.generationType(), decoded.generationType());
            assertEquals(original.shape(), decoded.shape());
            assertEquals(original.dimensions(), decoded.dimensions());
            assertEquals(original.centerPosition(), decoded.centerPosition());
            assertEquals(original.dimensionId(), decoded.dimensionId());
            assertEquals(original.palette(), decoded.palette());
            assertEquals(original.biomeConfig(), decoded.biomeConfig());
            assertEquals(original.options(), decoded.options());
            assertEquals(original.creatorUUID(), decoded.creatorUUID());
            assertEquals(original.createdAt(), decoded.createdAt());
            assertEquals(original.updatedAt(), decoded.updatedAt());
            assertEquals(original.revision(), decoded.revision());
            assertEquals(original.isMainHub(), decoded.isMainHub());
            assertEquals(original.linkedZoneId(), decoded.linkedZoneId());
            assertEquals(original.customShapeNbt(), decoded.customShapeNbt());
        }

        @Test
        @DisplayName("codec round-trip preserves biomeConfig when present")
        void codec_roundTrip_preservesBiomeConfig() {
            BiomeGenerationConfig biomeConfig = new BiomeGenerationConfig(
                ResourceLocation.withDefaultNamespace("forest"),
                12345L,
                true,
                true,
                true,
                63,
                BiomeGenerationConfig.TerrainStyle.AMPLIFIED
            );

            AreaDefinition original = AreaDefinition.builder()
                .generationType(AreaGenerationType.BIOME)
                .biomeConfig(biomeConfig)
                .build();

            // Round-trip
            Tag nbt = AreaDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaDefinition decoded = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertNotNull(decoded.biomeConfig());
            assertEquals(biomeConfig.biomeId(), Objects.requireNonNull(decoded.biomeConfig()).biomeId());
            assertEquals(biomeConfig.seed(), Objects.requireNonNull(decoded.biomeConfig()).seed());
            assertEquals(biomeConfig.terrainStyle(), Objects.requireNonNull(decoded.biomeConfig()).terrainStyle());
        }

        @Test
        @DisplayName("codec round-trip preserves customShapeNbt when present")
        void codec_roundTrip_preservesCustomShapeNbt() {
            CompoundTag customNbt = new CompoundTag();
            customNbt.putString("testKey", "testValue");
            customNbt.putInt("testInt", 42);

            AreaDefinition original = AreaDefinition.builder()
                .shape(AreaShape.CUSTOM_NBT)
                .customShapeNbt(customNbt)
                .build();

            // Round-trip
            Tag nbt = AreaDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaDefinition decoded = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertNotNull(decoded.customShapeNbt());
            assertEquals("testValue", Objects.requireNonNull(decoded.customShapeNbt()).getString("testKey"));
            assertEquals(42, Objects.requireNonNull(decoded.customShapeNbt()).getInt("testInt"));
        }

        @Test
        @DisplayName("codec handles null optional fields correctly")
        void codec_nullOptionalFields_usesDefaults() {
            AreaDefinition original = AreaDefinition.builder()
                .biomeConfig(null)
                .creator(null)
                .linkedZone(null)
                .customShapeNbt(null)
                .build();

            // Round-trip
            Tag nbt = AreaDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
            AreaDefinition decoded = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();

            assertNull(decoded.biomeConfig());
            assertNull(decoded.creatorUUID());
            assertNull(decoded.linkedZoneId());
            assertNull(decoded.customShapeNbt());
        }

        @Test
        @DisplayName("codec decoding fails for missing required fields")
        void codec_missingRequiredField_fails() {
            // Create an NBT tag missing the 'name' field
            CompoundTag incompleteNbt = new CompoundTag();
            incompleteNbt.putUUID("id", Objects.requireNonNull(UUID.randomUUID()));
            // Missing: name, generationType, shape, dimensions, etc.

            DataResult<AreaDefinition> result = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, incompleteNbt);

            assertTrue(result.error().isPresent(), "Decoding should fail for incomplete NBT");
        }
    }

    // ========================================================================
    // Builder Tests
    // ========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates valid AreaDefinition with defaults")
        void builder_createsValidDefaults() {
            AreaDefinition area = AreaDefinition.builder().build();

            assertNotNull(area.id());
            assertEquals("New Area", area.name());
            assertEquals(AreaGenerationType.CUSTOM, area.generationType());
            assertEquals(AreaShape.RECTANGULAR, area.shape());
            assertEquals(AreaDimensions.DEFAULT, area.dimensions());
            assertEquals(BlockPos.ZERO, area.centerPosition());
            assertNotNull(area.palette());
            assertEquals(AreaOptions.DEFAULT, area.options());
            assertNull(area.creatorUUID());
            assertFalse(area.isMainHub());
            assertNull(area.linkedZoneId());
            assertNull(area.customShapeNbt());
            assertEquals(0, area.revision());
            assertTrue(area.createdAt() > 0);
            assertEquals(area.createdAt(), area.updatedAt());
        }

        @Test
        @DisplayName("builder sets all fields correctly")
        void builder_setsAllFields() {
            UUID id = Objects.requireNonNull(UUID.randomUUID());
            UUID creator = Objects.requireNonNull(UUID.randomUUID());
            BlockPos center = new BlockPos(10, 20, 30);
            AreaDimensions dims = new AreaDimensions(100, 100, 50, 64);
            AreaPalette palette = Objects.requireNonNull(createTestPalette());
            BiomeGenerationConfig biomeConfig = BiomeGenerationConfig.DEFAULT;
            CompoundTag customNbt = new CompoundTag();
            customNbt.putString("test", "value");

            AreaDefinition area = AreaDefinition.builder()
                .id(id)
                .name("Custom Area")
                .generationType(AreaGenerationType.BIOME)
                .shape(AreaShape.CIRCULAR)
                .dimensions(dims)
                .center(center)
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("the_end")))
                .palette(palette)
                .biomeConfig(biomeConfig)
                .options(Objects.requireNonNull(AreaOptions.OPEN))
                .creator(creator)
                .mainHub(true)
                .linkedZone("linked-zone-id")
                .customShapeNbt(customNbt)
                .build();

            assertEquals(id, area.id());
            assertEquals("Custom Area", area.name());
            assertEquals(AreaGenerationType.BIOME, area.generationType());
            assertEquals(AreaShape.CIRCULAR, area.shape());
            assertEquals(dims, area.dimensions());
            assertEquals(center, area.centerPosition());
            assertEquals(ResourceLocation.withDefaultNamespace("the_end"), area.dimensionId());
            assertEquals(palette, area.palette());
            assertEquals(biomeConfig, area.biomeConfig());
            assertEquals(AreaOptions.OPEN, area.options());
            assertEquals(creator, area.creatorUUID());
            assertTrue(area.isMainHub());
            assertEquals("linked-zone-id", area.linkedZoneId());
            assertEquals(customNbt, area.customShapeNbt());
        }

        @Test
        @DisplayName("builder generates unique UUIDs by default")
        void builder_generatesUniqueIds() {
            AreaDefinition area1 = AreaDefinition.builder().build();
            AreaDefinition area2 = AreaDefinition.builder().build();

            assertNotEquals(area1.id(), area2.id());
        }
    }

    // ========================================================================
    // Transformation Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("With Methods (Immutable Transformations)")
    class WithMethodsTests {

        @Test
        @DisplayName("withUpdate increments revision and updates timestamp")
        void withUpdate_incrementsRevision() throws InterruptedException {
            AreaDefinition original = AreaDefinition.builder().build();
            int originalRevision = original.revision();
            long originalUpdatedAt = original.updatedAt();

            Thread.sleep(1); // Ensure time passes

            AreaDefinition updated = original.withUpdate();

            assertEquals(originalRevision + 1, updated.revision());
            assertTrue(updated.updatedAt() >= originalUpdatedAt);
            // Other fields remain the same
            assertEquals(original.id(), updated.id());
            assertEquals(original.name(), updated.name());
            assertEquals(original.createdAt(), updated.createdAt());
        }

        @Test
        @DisplayName("withUpdate preserves original (immutability)")
        void withUpdate_preservesOriginal() {
            AreaDefinition original = AreaDefinition.builder().build();
            int originalRevision = original.revision();

            AreaDefinition updated = original.withUpdate();

            // Original is unchanged
            assertEquals(originalRevision, original.revision());
            // Updated is different object
            assertNotSame(original, updated);
        }

        @Test
        @DisplayName("withDimensions creates new instance with updated dimensions")
        void withDimensions_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().build();
            AreaDimensions newDims = new AreaDimensions(200, 200, 100, 0);

            AreaDefinition updated = original.withDimensions(newDims);

            assertEquals(newDims, updated.dimensions());
            assertEquals(AreaDimensions.DEFAULT, original.dimensions());
            assertEquals(original.revision() + 1, updated.revision());
        }

        @Test
        @DisplayName("withPalette creates new instance with updated palette")
        void withPalette_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().build();
            AreaPalette newPalette = Objects.requireNonNull(createTestPalette());

            AreaDefinition updated = original.withPalette(newPalette);

            assertEquals(newPalette, updated.palette());
            assertEquals(original.revision() + 1, updated.revision());
        }

        @Test
        @DisplayName("withOptions creates new instance with updated options")
        void withOptions_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().build();
            AreaOptions newOptions = Objects.requireNonNull(AreaOptions.OPEN);

            AreaDefinition updated = original.withOptions(newOptions);

            assertEquals(newOptions, updated.options());
            assertEquals(AreaOptions.DEFAULT, original.options());
        }

        @Test
        @DisplayName("withName creates new instance with updated name")
        void withName_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().name("Original").build();

            AreaDefinition updated = original.withName("Updated Name");

            assertEquals("Updated Name", updated.name());
            assertEquals("Original", original.name());
        }

        @Test
        @DisplayName("withLinkedZone creates new instance with updated zone")
        void withLinkedZone_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().build();

            AreaDefinition updated = original.withLinkedZone("new-zone");

            assertEquals("new-zone", updated.linkedZoneId());
            assertNull(original.linkedZoneId());
        }

        @Test
        @DisplayName("withLinkedZone accepts null to clear zone")
        void withLinkedZone_acceptsNull() {
            AreaDefinition original = AreaDefinition.builder().linkedZone("zone").build();

            AreaDefinition updated = original.withLinkedZone(null);

            assertNull(updated.linkedZoneId());
            assertEquals("zone", original.linkedZoneId());
        }

        @Test
        @DisplayName("withMainHub creates new instance with updated flag")
        void withMainHub_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().mainHub(false).build();

            AreaDefinition updated = original.withMainHub(true);

            assertTrue(updated.isMainHub());
            assertFalse(original.isMainHub());
        }

        @Test
        @DisplayName("withCustomShapeNbt creates new instance with NBT")
        void withCustomShapeNbt_createsNewInstance() {
            AreaDefinition original = AreaDefinition.builder().build();
            CompoundTag nbt = new CompoundTag();
            nbt.putString("key", "value");

            AreaDefinition updated = original.withCustomShapeNbt(nbt);

            assertNotNull(updated.customShapeNbt());
            assertEquals("value", Objects.requireNonNull(updated.customShapeNbt()).getString("key"));
            assertNull(original.customShapeNbt());
        }
    }

    // ========================================================================
    // Query Methods Tests
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodsTests {

        @Test
        @DisplayName("isBiomeGeneration returns true for BIOME type with config")
        void isBiomeGeneration_trueForBiomeWithConfig() {
            AreaDefinition area = AreaDefinition.builder()
                .generationType(AreaGenerationType.BIOME)
                .biomeConfig(BiomeGenerationConfig.DEFAULT)
                .build();

            assertTrue(area.isBiomeGeneration());
        }

        @Test
        @DisplayName("isBiomeGeneration returns false for BIOME type without config")
        void isBiomeGeneration_falseForBiomeWithoutConfig() {
            AreaDefinition area = AreaDefinition.builder()
                .generationType(AreaGenerationType.BIOME)
                .biomeConfig(null)
                .build();

            assertFalse(area.isBiomeGeneration());
        }

        @Test
        @DisplayName("isBiomeGeneration returns false for CUSTOM type")
        void isBiomeGeneration_falseForCustom() {
            AreaDefinition area = AreaDefinition.builder()
                .generationType(AreaGenerationType.CUSTOM)
                .build();

            assertFalse(area.isBiomeGeneration());
        }

        @Test
        @DisplayName("isCustomGeneration returns true for CUSTOM type")
        void isCustomGeneration_trueForCustom() {
            AreaDefinition area = AreaDefinition.builder()
                .generationType(AreaGenerationType.CUSTOM)
                .build();

            assertTrue(area.isCustomGeneration());
        }

        @Test
        @DisplayName("isCustomGeneration returns false for BIOME type")
        void isCustomGeneration_falseForBiome() {
            AreaDefinition area = AreaDefinition.builder()
                .generationType(AreaGenerationType.BIOME)
                .build();

            assertFalse(area.isCustomGeneration());
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private static AreaPalette createTestPalette() {
        Map<String, String> materials = new HashMap<>();
        materials.put("floor", "minecraft:stone");
        materials.put("wall", "minecraft:stone_bricks");
        materials.put("ceiling", "minecraft:smooth_stone");
        return new AreaPalette(materials, "test-preset", true);
    }
}
