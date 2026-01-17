package com.devmod.area.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material palette for area construction.
 * Uses NexusPalette.Key IDs as keys mapping to block registry names.
 *
 * @param materials  Map of palette key ID to block registry name
 * @param presetId   ID of the preset used (e.g., "default", "industrial")
 * @param customized True if materials have been modified from the preset
 */
public record AreaPalette(
    Map<String, String> materials,
    String presetId,
    boolean customized
) {
    /**
     * M-14 fix: Minimum required palette entries for a valid area.
     * Set to 3 to ensure at least floor checkerboard (FLOOR, FLOOR_ALT) plus
     * one structural element (WALL or CEILING). This prevents nearly-empty
     * palettes that would result in mostly air blocks being placed.
     */
    public static final int MIN_REQUIRED_MATERIALS = 3;

    /**
     * M-13 fix: Compact constructor ensures defensive copy of materials map.
     */
    public AreaPalette {
        Objects.requireNonNull(materials, "materials cannot be null");
        Objects.requireNonNull(presetId, "presetId cannot be null");
        // M-13 fix: Defensive copy to prevent external modification
        materials = Map.copyOf(materials);
    }

    public static final Codec<AreaPalette> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("materials")
                .forGetter(AreaPalette::materials),
            Codec.STRING.fieldOf("presetId").forGetter(AreaPalette::presetId),
            Codec.BOOL.fieldOf("customized").forGetter(AreaPalette::customized)
        ).apply(instance, (m, p, c) -> new AreaPalette(m, p, Objects.requireNonNull(c)))
    );

    public static final StreamCodec<ByteBuf, AreaPalette> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.map(
            HashMap::new,
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8)
        )), AreaPalette::materials,
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), AreaPalette::presetId,
        Objects.requireNonNull(ByteBufCodecs.BOOL), AreaPalette::customized,
        (m, p, c) -> new AreaPalette(m, p, Objects.requireNonNull(c))
    );

    /**
     * Creates an empty palette with the given preset ID.
     */
    public static AreaPalette empty(String presetId) {
        return new AreaPalette(new HashMap<>(), presetId, false);
    }

    /**
     * Returns a copy with the specified material changed.
     */
    @Nonnull
    public AreaPalette withMaterial(@Nonnull String key, @Nonnull String blockId) {
        Map<String, String> newMaterials = new HashMap<>(materials);
        newMaterials.put(Objects.requireNonNull(key), Objects.requireNonNull(blockId));
        return new AreaPalette(newMaterials, presetId, true);
    }

    /**
     * Returns a copy marked as using a different preset.
     */
    @Nonnull
    public AreaPalette withPreset(@Nonnull String newPresetId, @Nonnull Map<String, String> newMaterials) {
        return new AreaPalette(new HashMap<>(newMaterials), newPresetId, false);
    }

    /**
     * Gets the block ID for a palette key.
     *
     * @param key The palette key ID (e.g., "floor", "wall")
     * @return The block registry name, or null if not set
     */
    @Nullable
    public String getBlockId(@Nonnull String key) {
        return materials.get(Objects.requireNonNull(key));
    }

    /**
     * Gets the BlockState for a palette key.
     *
     * @param key The palette key ID
     * @return The BlockState, or air if not found/invalid
     */
    @Nonnull
    public BlockState getBlockState(@Nonnull String key) {
        String blockId = materials.get(Objects.requireNonNull(key));
        if (blockId == null || blockId.isBlank()) {
            return Objects.requireNonNull(Blocks.AIR.defaultBlockState());
        }
        String trimmedId = Objects.requireNonNull(blockId.trim());
        ResourceLocation id = ResourceLocation.tryParse(trimmedId);
        if (id == null) {
            return Objects.requireNonNull(Blocks.AIR.defaultBlockState());
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR && !"minecraft:air".equals(trimmedId)) {
            return Objects.requireNonNull(Blocks.AIR.defaultBlockState());
        }
        return Objects.requireNonNull(block.defaultBlockState());
    }

    /**
     * Returns true if this palette has a material for the given key.
     */
    public boolean hasMaterial(@Nonnull String key) {
        String blockId = materials.get(Objects.requireNonNull(key));
        return blockId != null && !blockId.isBlank();
    }

    /**
     * Returns the number of materials defined.
     */
    public int size() {
        return materials.size();
    }
}
