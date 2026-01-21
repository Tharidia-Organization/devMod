package com.devmod.foundry.client.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.geometry.BlockGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.devmod.foundry.client.model.util.FoundryItemLayerModel;
import com.devmod.foundry.client.model.util.FoundryModelTextureIteratable;
import com.devmod.foundry.util.FoundryJsonHelper;

/** Model which uses a key in NBT to select which texture variant to load. */
public class FoundryNBTKeyModel implements IUnbakedGeometry<FoundryNBTKeyModel> {
    /** Model loader instance */
    public static final IGeometryLoader<FoundryNBTKeyModel> LOADER = FoundryNBTKeyModel::deserialize;

    /** Map of statically registered extra textures, used for addon mods */
    private static final Multimap<ResourceLocation, Pair<String, ResourceLocation>> EXTRA_TEXTURES = HashMultimap.create();

    /**
     * Registers an extra variant texture for the model with the given key.
     * @param key          Model key, should be defined in the model JSON if supported
     * @param textureName  Name of the texture defined, corresponds to a possible value of the NBT key
     * @param texture      Texture to use, same format as in resource packs
     */
    @SuppressWarnings("unused") // API
    public static void registerExtraTexture(ResourceLocation key, String textureName, ResourceLocation texture) {
        EXTRA_TEXTURES.put(key, Pair.of(textureName, texture));
    }

    /** Key to check in item NBT */
    private final String nbtKey;
    /** Key denoting which extra textures to fetch from the map */
    @Nullable
    private final ResourceLocation extraTexturesKey;

    /** Map of textures for the model */
    private Map<String, Material> textures = Collections.emptyMap();

    public FoundryNBTKeyModel(String nbtKey, @Nullable ResourceLocation extraTexturesKey) {
        this.nbtKey = nbtKey;
        this.extraTexturesKey = extraTexturesKey;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext owner) {
        textures = new HashMap<>();
        Material defaultTexture = owner.getMaterial("default");
        textures.put("default", defaultTexture);
        if (owner instanceof BlockGeometryBakingContext blockContext) {
            FoundryModelTextureIteratable iterable = new FoundryModelTextureIteratable(null, blockContext.owner);
            for (Map<String, Either<Material, String>> map : iterable) {
                for (String key : map.keySet()) {
                    if (!textures.containsKey(key) && owner.hasMaterial(key)) {
                        textures.put(key, owner.getMaterial(key));
                    }
                }
            }
        }
        if (extraTexturesKey != null) {
            for (Pair<String, ResourceLocation> extra : EXTRA_TEXTURES.get(extraTexturesKey)) {
                String key = extra.getFirst();
                if (!textures.containsKey(key)) {
                    textures.put(key, new Material(InventoryMenu.BLOCK_ATLAS, extra.getSecond()));
                }
            }
        }
    }

    private static BakedModel bakeModel(
        IGeometryBakingContext owner,
        Material texture,
        Function<Material, TextureAtlasSprite> spriteGetter,
        Transformation rotation,
        ItemOverrides overrides
    ) {
        TextureAtlasSprite sprite = spriteGetter.apply(texture);
        CompositeModel.Baked.Builder builder = CompositeModel.Baked.builder(owner, sprite, overrides, owner.getTransforms());
        builder.addQuads(
            FoundryItemLayerModel.getDefaultRenderType(owner),
            FoundryItemLayerModel.getQuadsForSprite(-1, -1, sprite, rotation, 0)
        );
        return builder.build();
    }

    @Override
    public BakedModel bake(
        IGeometryBakingContext owner,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> spriteGetter,
        ModelState modelTransform,
        ItemOverrides overrides
    ) {
        Transformation transform = FoundryItemLayerModel.applyTransform(modelTransform, owner.getRootTransform()).getRotation();
        Map<String, BakedModel> variants = new HashMap<>(textures.size());
        for (Entry<String, Material> entry : textures.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("default")) {
                variants.put(key, bakeModel(owner, entry.getValue(), spriteGetter, transform, ItemOverrides.EMPTY));
            }
        }
        Material defaultTexture = textures.get("default");
        if (defaultTexture == null) {
            defaultTexture = owner.getMaterial("default");
        }
        return bakeModel(owner, defaultTexture, spriteGetter, transform, new Overrides(nbtKey, textures, Map.copyOf(variants)));
    }

    public static class Overrides extends ItemOverrides {
        private final String nbtKey;
        private final Map<String, Material> textures;
        private final Map<String, BakedModel> variants;

        public Overrides(String nbtKey, Map<String, Material> textures, Map<String, BakedModel> variants) {
            this.nbtKey = nbtKey;
            this.textures = textures;
            this.variants = variants;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity livingEntity, int pSeed) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt.contains(nbtKey)) {
                    return variants.getOrDefault(nbt.getString(nbtKey), model);
                }
            }
            return model;
        }

        @SuppressWarnings("unused") // API usage
        public Material getTexture(String name) {
            Material texture = textures.get(name);
            if (texture != null) {
                return texture;
            }
            Material fallback = textures.get("default");
            if (fallback != null) {
                return fallback;
            }
            return new Material(InventoryMenu.BLOCK_ATLAS, MissingTextureAtlasSprite.getLocation());
        }
    }

    public static FoundryNBTKeyModel deserialize(JsonObject json, JsonDeserializationContext context) {
        String key = GsonHelper.getAsString(json, "nbt_key");
        ResourceLocation extraTexturesKey = null;
        if (json.has("extra_textures_key")) {
            extraTexturesKey = FoundryJsonHelper.getResourceLocation(json, "extra_textures_key");
        }
        return new FoundryNBTKeyModel(key, extraTexturesKey);
    }
}
