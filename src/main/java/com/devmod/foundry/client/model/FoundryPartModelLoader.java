package com.devmod.foundry.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.devmod.DevMod;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfo.TintedSprite;
import com.devmod.foundry.tool.FoundryPartItem;

/**
 * Custom model loader for Foundry tool parts.
 * Dynamically renders parts with material-specific textures based on ItemStack NBT.
 *
 * JSON format:
 * {
 *   "loader": "devmod:foundry_part",
 *   "texture": "devmod:item/tool/pickaxe/head"
 * }
 */
public class FoundryPartModelLoader implements IGeometryLoader<FoundryPartModelLoader.PartGeometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_part");
    public static final FoundryPartModelLoader INSTANCE = new FoundryPartModelLoader();

    private FoundryPartModelLoader() {}

    @Override
    public PartGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        String textureStr = GsonHelper.getAsString(json, "texture");
        ResourceLocation texture = ResourceLocation.parse(textureStr);
        return new PartGeometry(texture);
    }

    /**
     * Unbaked geometry for a foundry part.
     */
    public static class PartGeometry implements IUnbakedGeometry<PartGeometry> {
        private final ResourceLocation baseTexture;

        public PartGeometry(ResourceLocation baseTexture) {
            this.baseTexture = baseTexture;
        }

        @Override
        public BakedModel bake(
            IGeometryBakingContext context,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ItemOverrides overrides
        ) {
            // Get default sprite for particle texture
            TextureAtlasSprite particleSprite = spriteGetter.apply(
                new Material(InventoryMenu.BLOCK_ATLAS, baseTexture)
            );

            return new BakedPartModel(baseTexture, particleSprite, context.getTransforms());
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parents to resolve
        }
    }

    /**
     * Baked model that dynamically renders based on material from ItemStack.
     */
    public static class BakedPartModel implements IDynamicBakedModel {
        private final ResourceLocation baseTexture;
        private final TextureAtlasSprite particleSprite;
        private final ItemTransforms transforms;
        private final MaterialOverrideHandler overrideHandler;
        private final Map<ResourceLocation, BakedModel> modelCache = new ConcurrentHashMap<>();

        public BakedPartModel(ResourceLocation baseTexture, TextureAtlasSprite particleSprite, ItemTransforms transforms) {
            this.baseTexture = baseTexture;
            this.particleSprite = particleSprite;
            this.transforms = transforms;
            this.overrideHandler = new MaterialOverrideHandler(this);
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand) {
            // Default quads without material - will be overridden by item overrides
            return List.of();
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand, @Nonnull net.neoforged.neoforge.client.model.data.ModelData data, @Nullable net.minecraft.client.renderer.RenderType renderType) {
            // Default quads without material - will be overridden by item overrides
            return List.of();
        }

        /**
         * Get quads for a specific material.
         */
        public List<BakedQuad> getQuadsForMaterial(@Nullable ResourceLocation materialId, @Nullable Direction side) {
            if (side != null) {
                return List.of(); // Item models don't have sided quads
            }

            FoundryMaterialRenderInfo renderInfo = materialId != null
                ? FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(materialId)
                : null;

            if (renderInfo == null) {
                renderInfo = FoundryMaterialRenderInfo.EMPTY;
            }

            TintedSprite tintedSprite = renderInfo.getSprite(baseTexture);
            return generateQuads(tintedSprite);
        }

        /**
         * Generate quads for a tinted sprite using item/generated style.
         */
        private List<BakedQuad> generateQuads(TintedSprite tintedSprite) {
            List<BakedQuad> quads = new ArrayList<>();
            TextureAtlasSprite sprite = tintedSprite.sprite();

            if (sprite == null) {
                return quads;
            }

            // Generate flat item quads similar to minecraft:item/generated
            float[] uvs = new float[] {
                sprite.getU0(), sprite.getV0(),
                sprite.getU1(), sprite.getV1()
            };

            // Pack color into ARGB int
            int color = packColor(tintedSprite);

            // Front face (south, +Z)
            quads.add(createQuad(
                new Vector3f(0, 0, 0.5f),
                new Vector3f(1, 0, 0.5f),
                new Vector3f(1, 1, 0.5f),
                new Vector3f(0, 1, 0.5f),
                sprite, uvs, color, Direction.SOUTH
            ));

            // Back face (north, -Z)
            quads.add(createQuad(
                new Vector3f(1, 0, 0.5f),
                new Vector3f(0, 0, 0.5f),
                new Vector3f(0, 1, 0.5f),
                new Vector3f(1, 1, 0.5f),
                sprite, uvs, color, Direction.NORTH
            ));

            return quads;
        }

        private int packColor(TintedSprite tintedSprite) {
            int a = (int)(tintedSprite.alpha() * 255) & 0xFF;
            int r = (int)(tintedSprite.red() * 255) & 0xFF;
            int g = (int)(tintedSprite.green() * 255) & 0xFF;
            int b = (int)(tintedSprite.blue() * 255) & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private BakedQuad createQuad(
            Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
            TextureAtlasSprite sprite, float[] uvs, int color, Direction face
        ) {
            int[] vertexData = new int[32]; // 4 vertices * 8 ints each

            putVertex(vertexData, 0, v1, uvs[0], uvs[3], color, face);
            putVertex(vertexData, 8, v2, uvs[2], uvs[3], color, face);
            putVertex(vertexData, 16, v3, uvs[2], uvs[1], color, face);
            putVertex(vertexData, 24, v4, uvs[0], uvs[1], color, face);

            return new BakedQuad(vertexData, 0, face, sprite, true);
        }

        private void putVertex(int[] data, int offset, Vector3f pos, float u, float v, int color, Direction face) {
            data[offset] = Float.floatToRawIntBits(pos.x());
            data[offset + 1] = Float.floatToRawIntBits(pos.y());
            data[offset + 2] = Float.floatToRawIntBits(pos.z());
            data[offset + 3] = color;
            data[offset + 4] = Float.floatToRawIntBits(u);
            data[offset + 5] = Float.floatToRawIntBits(v);
            // Pack normal
            data[offset + 6] = packNormal(face);
            data[offset + 7] = 0; // Padding
        }

        private int packNormal(Direction face) {
            int x = (int)(face.getStepX() * 127) & 0xFF;
            int y = (int)(face.getStepY() * 127) & 0xFF;
            int z = (int)(face.getStepZ() * 127) & 0xFF;
            return x | (y << 8) | (z << 16);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        @Nonnull
        public TextureAtlasSprite getParticleIcon() {
            return particleSprite;
        }

        @Override
        @Nonnull
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        @Nonnull
        public ItemOverrides getOverrides() {
            return overrideHandler;
        }
    }

    /**
     * Override handler that returns cached models based on material.
     */
    public static class MaterialOverrideHandler extends ItemOverrides {
        private final BakedPartModel parent;
        private final Map<ResourceLocation, BakedModel> cache = new ConcurrentHashMap<>();

        public MaterialOverrideHandler(BakedPartModel parent) {
            this.parent = parent;
        }

        @Override
        @Nullable
        public BakedModel resolve(
            @Nonnull BakedModel model,
            @Nonnull ItemStack stack,
            @Nullable ClientLevel level,
            @Nullable LivingEntity entity,
            int seed
        ) {
            if (!(stack.getItem() instanceof FoundryPartItem partItem)) {
                return model;
            }

            ResourceLocation materialId = partItem.getMaterialId(stack).orElse(null);
            if (materialId == null) {
                return model;
            }

            return cache.computeIfAbsent(materialId, id -> new MaterialBakedModel(parent, id));
        }
    }

    /**
     * Baked model for a specific material.
     */
    public static class MaterialBakedModel implements BakedModel {
        private final BakedPartModel parent;
        private final ResourceLocation materialId;
        private List<BakedQuad> cachedQuads;

        public MaterialBakedModel(BakedPartModel parent, ResourceLocation materialId) {
            this.parent = parent;
            this.materialId = materialId;
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand) {
            if (side != null) {
                return List.of();
            }
            if (cachedQuads == null) {
                cachedQuads = parent.getQuadsForMaterial(materialId, side);
            }
            return cachedQuads;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return parent.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return parent.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return parent.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return parent.isCustomRenderer();
        }

        @Override
        @Nonnull
        public TextureAtlasSprite getParticleIcon() {
            return parent.getParticleIcon();
        }

        @Override
        @Nonnull
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        @Nonnull
        public ItemTransforms getTransforms() {
            return parent.getTransforms();
        }
    }
}
