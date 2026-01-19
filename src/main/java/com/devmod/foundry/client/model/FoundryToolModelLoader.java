package com.devmod.foundry.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
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
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.FoundryToolItem;

/**
 * Custom model loader for Foundry tools.
 * Dynamically renders tools with material-specific textures for each part.
 *
 * JSON format:
 * {
 *   "loader": "devmod:foundry_tool",
 *   "parts": [
 *     { "name": "head", "texture": "devmod:item/tool/pickaxe/head", "index": 0 },
 *     { "name": "handle", "texture": "devmod:item/tool/pickaxe/handle", "index": 1 },
 *     { "name": "binding", "texture": "devmod:item/tool/pickaxe/binding", "index": 2 }
 *   ]
 * }
 */
public class FoundryToolModelLoader implements IGeometryLoader<FoundryToolModelLoader.ToolGeometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_tool");
    public static final FoundryToolModelLoader INSTANCE = new FoundryToolModelLoader();

    private FoundryToolModelLoader() {}

    @Override
    public ToolGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        List<ToolPart> parts = new ArrayList<>();

        if (json.has("parts")) {
            JsonArray partsArray = GsonHelper.getAsJsonArray(json, "parts");
            for (JsonElement element : partsArray) {
                JsonObject partObj = element.getAsJsonObject();
                String name = GsonHelper.getAsString(partObj, "name");
                String textureStr = GsonHelper.getAsString(partObj, "texture");
                int index = GsonHelper.getAsInt(partObj, "index");
                parts.add(new ToolPart(name, ResourceLocation.parse(textureStr), index));
            }
        }

        return new ToolGeometry(List.copyOf(parts));
    }

    /**
     * Represents a tool part with name, texture, and material index.
     */
    public record ToolPart(String name, ResourceLocation texture, int index) {}

    /**
     * Unbaked geometry for a foundry tool.
     */
    public static class ToolGeometry implements IUnbakedGeometry<ToolGeometry> {
        private final List<ToolPart> parts;

        public ToolGeometry(List<ToolPart> parts) {
            this.parts = parts;
        }

        @Override
        public BakedModel bake(
            IGeometryBakingContext context,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ItemOverrides overrides
        ) {
            // Get default sprite for particle texture (use first part)
            TextureAtlasSprite particleSprite = parts.isEmpty() ? null :
                spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, parts.get(0).texture()));

            return new BakedToolModel(parts, particleSprite, context.getTransforms());
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parents to resolve
        }
    }

    /**
     * Baked model that dynamically renders based on materials from ItemStack.
     */
    public static class BakedToolModel implements IDynamicBakedModel {
        private final List<ToolPart> parts;
        private final TextureAtlasSprite particleSprite;
        private final ItemTransforms transforms;
        private final ToolOverrideHandler overrideHandler;

        public BakedToolModel(List<ToolPart> parts, TextureAtlasSprite particleSprite, ItemTransforms transforms) {
            this.parts = parts;
            this.particleSprite = particleSprite;
            this.transforms = transforms;
            this.overrideHandler = new ToolOverrideHandler(this);
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand) {
            // Default quads without materials
            return List.of();
        }

        /**
         * Get quads for a specific set of materials.
         */
        public List<BakedQuad> getQuadsForMaterials(List<ResourceLocation> materials, @Nullable Direction side) {
            if (side != null) {
                return List.of();
            }

            List<BakedQuad> allQuads = new ArrayList<>();

            // Render parts in order (bottom to top)
            for (ToolPart part : parts) {
                ResourceLocation materialId = part.index() < materials.size()
                    ? materials.get(part.index())
                    : null;

                FoundryMaterialRenderInfo renderInfo = materialId != null
                    ? FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(materialId)
                    : null;

                if (renderInfo == null) {
                    renderInfo = FoundryMaterialRenderInfo.EMPTY;
                }

                TintedSprite tintedSprite = renderInfo.getSprite(part.texture());
                allQuads.addAll(generateQuads(tintedSprite));
            }

            return allQuads;
        }

        /**
         * Generate quads for a tinted sprite.
         */
        private List<BakedQuad> generateQuads(TintedSprite tintedSprite) {
            List<BakedQuad> quads = new ArrayList<>();
            TextureAtlasSprite sprite = tintedSprite.sprite();

            if (sprite == null) {
                return quads;
            }

            float[] uvs = new float[] {
                sprite.getU0(), sprite.getV0(),
                sprite.getU1(), sprite.getV1()
            };

            int color = packColor(tintedSprite);

            // Front face
            quads.add(createQuad(
                new Vector3f(0, 0, 0.5f),
                new Vector3f(1, 0, 0.5f),
                new Vector3f(1, 1, 0.5f),
                new Vector3f(0, 1, 0.5f),
                sprite, uvs, color, Direction.SOUTH
            ));

            // Back face
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
            int[] vertexData = new int[32];

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
            data[offset + 6] = packNormal(face);
            data[offset + 7] = 0;
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
     * Override handler for tools.
     */
    public static class ToolOverrideHandler extends ItemOverrides {
        private final BakedToolModel parent;
        private final Map<ToolCacheKey, BakedModel> cache = new ConcurrentHashMap<>();

        public ToolOverrideHandler(BakedToolModel parent) {
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
            return FoundryToolData.fromStack(stack)
                .map(data -> {
                    ToolCacheKey key = new ToolCacheKey(data.materials());
                    return cache.computeIfAbsent(key, k -> new MaterialsBakedModel(parent, data.materials()));
                })
                .orElse(model);
        }
    }

    /**
     * Cache key for tool materials.
     */
    private record ToolCacheKey(List<ResourceLocation> materials) {}

    /**
     * Baked model for specific materials.
     */
    public static class MaterialsBakedModel implements BakedModel {
        private final BakedToolModel parent;
        private final List<ResourceLocation> materials;
        private List<BakedQuad> cachedQuads;

        public MaterialsBakedModel(BakedToolModel parent, List<ResourceLocation> materials) {
            this.parent = parent;
            this.materials = materials;
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand) {
            if (side != null) {
                return List.of();
            }
            if (cachedQuads == null) {
                cachedQuads = parent.getQuadsForMaterials(materials, side);
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
