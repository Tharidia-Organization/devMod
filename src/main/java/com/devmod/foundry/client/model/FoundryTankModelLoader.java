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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import com.devmod.DevMod;
import com.devmod.foundry.client.model.FoundryFluidCuboid.ScaledBounds;

/**
 * Custom model loader for Foundry tank blocks with dynamic fluid rendering.
 * Renders fluid inside the tank based on ModelData provided by block entity.
 *
 * JSON format:
 * {
 *   "loader": "devmod:foundry_tank",
 *   "fluid": {
 *     "from": [1, 1, 1],
 *     "to": [15, 15, 15],
 *     "increments": 256
 *   },
 *   "textures": { ... },
 *   "elements": [ ... ]
 * }
 */
public class FoundryTankModelLoader implements IGeometryLoader<FoundryTankModelLoader.TankGeometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_tank");
    public static final FoundryTankModelLoader INSTANCE = new FoundryTankModelLoader();

    /** Model property for fluid stack */
    public static final ModelProperty<FluidStack> FLUID_STACK = new ModelProperty<>();
    /** Model property for tank capacity */
    public static final ModelProperty<Integer> TANK_CAPACITY = new ModelProperty<>();

    private FoundryTankModelLoader() {}

    @Override
    public TankGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        // Parse fluid cuboid
        FoundryFluidCuboid fluidCuboid = null;
        if (json.has("fluid")) {
            JsonObject fluidObj = GsonHelper.getAsJsonObject(json, "fluid");
            float[] from = parseVector3(GsonHelper.getAsJsonArray(fluidObj, "from"));
            float[] to = parseVector3(GsonHelper.getAsJsonArray(fluidObj, "to"));
            int increments = GsonHelper.getAsInt(fluidObj, "increments", FoundryFluidCuboid.DEFAULT_INCREMENTS);
            fluidCuboid = FoundryFluidCuboid.create(from[0], from[1], from[2], to[0], to[1], to[2], increments);
        }

        // Create a copy of the JSON without the "loader" and "fluid" fields to prevent recursion
        // When we deserialize as BlockModel, it would trigger this loader again if "loader" is present
        JsonObject baseJson = new JsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            // Skip loader and fluid fields - they're specific to our custom loader
            if (!key.equals("loader") && !key.equals("fluid")) {
                baseJson.add(key, entry.getValue());
            }
        }

        // Parse the base block model for tank shell geometry (without loader to avoid recursion)
        BlockModel baseModel = context.deserialize(baseJson, BlockModel.class);

        return new TankGeometry(fluidCuboid, baseModel);
    }

    private float[] parseVector3(com.google.gson.JsonArray array) {
        return new float[] {
            array.get(0).getAsFloat(),
            array.get(1).getAsFloat(),
            array.get(2).getAsFloat()
        };
    }

    /**
     * Unbaked geometry for a foundry tank.
     */
    public static class TankGeometry implements IUnbakedGeometry<TankGeometry> {
        @Nullable
        private final FoundryFluidCuboid fluidCuboid;
        @Nullable
        private final BlockModel baseModel;

        public TankGeometry(@Nullable FoundryFluidCuboid fluidCuboid, @Nullable BlockModel baseModel) {
            this.fluidCuboid = fluidCuboid;
            this.baseModel = baseModel;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BakedModel bake(
            IGeometryBakingContext context,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ItemOverrides overrides
        ) {
            // Bake the base model for static tank geometry
            BakedModel bakedBase = null;
            TextureAtlasSprite particle = null;
            if (baseModel != null) {
                bakedBase = baseModel.bake(baker, spriteGetter, modelState);
                particle = bakedBase.getParticleIcon();
            }

            return new BakedTankModel(fluidCuboid, bakedBase, particle, context.getTransforms());
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            if (baseModel != null) {
                baseModel.resolveParents(modelGetter);
            }
        }
    }

    /**
     * Baked tank model with dynamic fluid rendering.
     */
    @SuppressWarnings("deprecation")
    public static class BakedTankModel implements IDynamicBakedModel {
        @Nullable
        private final FoundryFluidCuboid fluidCuboid;
        @Nullable
        private final BakedModel baseModel;
        @Nullable
        private final TextureAtlasSprite particleSprite;
        private final ItemTransforms transforms;
        private final Map<FluidCacheKey, List<BakedQuad>> fluidQuadCache = new ConcurrentHashMap<>();

        public BakedTankModel(
            @Nullable FoundryFluidCuboid fluidCuboid,
            @Nullable BakedModel baseModel,
            @Nullable TextureAtlasSprite particleSprite,
            ItemTransforms transforms
        ) {
            this.fluidCuboid = fluidCuboid;
            this.baseModel = baseModel;
            this.particleSprite = particleSprite;
            this.transforms = transforms;
        }

        @Override
        @Nonnull
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand, @Nonnull ModelData modelData, @Nullable RenderType renderType) {
            List<BakedQuad> quads = new ArrayList<>();

            // Add base model quads (tank shell geometry)
            if (baseModel != null) {
                quads.addAll(baseModel.getQuads(state, side, rand, modelData, renderType));
            }

            // Only add fluid quads for non-culled faces (side == null)
            if (side == null && fluidCuboid != null) {
                // Get fluid from model data
                FluidStack fluid = modelData.get(FLUID_STACK);
                Integer capacity = modelData.get(TANK_CAPACITY);

                if (fluid != null && !fluid.isEmpty() && capacity != null && capacity > 0) {
                    // Calculate fill percentage
                    float fillPercentage = Math.min(1.0f, (float) fluid.getAmount() / capacity);

                    // Check cache
                    FluidCacheKey cacheKey = new FluidCacheKey(BuiltInRegistries.FLUID.getKey(fluid.getFluid()), fillPercentage);
                    quads.addAll(fluidQuadCache.computeIfAbsent(cacheKey, key -> generateFluidQuads(fluid, fillPercentage)));
                }
            }

            return quads;
        }

        private List<BakedQuad> generateFluidQuads(FluidStack fluid, float fillPercentage) {
            List<BakedQuad> quads = new ArrayList<>();

            if (fluidCuboid == null || fillPercentage <= 0) {
                return quads;
            }

            // Get fluid rendering properties
            IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid.getFluid());
            FluidType fluidType = fluid.getFluid().getFluidType();

            // Check if gas (lighter than air)
            boolean isGas = fluidType.isLighterThanAir();

            // Get scaled bounds
            ScaledBounds bounds = fluidCuboid.getScaledBounds(fillPercentage, isGas);
            if (bounds == null) {
                return quads;
            }

            // Get texture
            ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluid);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(stillTexture);

            // Get color
            int color = fluidExtensions.getTintColor(fluid);
            if (color == -1) {
                color = 0xFFFFFFFF;
            }

            // Get luminosity
            int luminosity = fluidType.getLightLevel(fluid);

            // Generate quads for each face
            quads.addAll(generateFluidFaceQuads(bounds, sprite, color, luminosity));

            return quads;
        }

        private List<BakedQuad> generateFluidFaceQuads(ScaledBounds bounds, TextureAtlasSprite sprite, int color, int luminosity) {
            List<BakedQuad> quads = new ArrayList<>();

            float minX = bounds.minX();
            float minY = bounds.minY();
            float minZ = bounds.minZ();
            float maxX = bounds.maxX();
            float maxY = bounds.maxY();
            float maxZ = bounds.maxZ();

            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            // Scale V coordinates based on height
            float vScale = maxY - minY;
            float scaledV1 = v0 + (v1 - v0) * vScale;

            // Top face (always render)
            quads.add(createQuad(
                new Vector3f(minX, maxY, minZ),
                new Vector3f(minX, maxY, maxZ),
                new Vector3f(maxX, maxY, maxZ),
                new Vector3f(maxX, maxY, minZ),
                sprite, u0, v0, u1, v1, color, Direction.UP, luminosity
            ));

            // Bottom face (only if not at bottom)
            if (minY > 0.001f) {
                quads.add(createQuad(
                    new Vector3f(minX, minY, maxZ),
                    new Vector3f(minX, minY, minZ),
                    new Vector3f(maxX, minY, minZ),
                    new Vector3f(maxX, minY, maxZ),
                    sprite, u0, v0, u1, v1, color, Direction.DOWN, luminosity
                ));
            }

            // North face (-Z)
            quads.add(createQuad(
                new Vector3f(maxX, minY, minZ),
                new Vector3f(minX, minY, minZ),
                new Vector3f(minX, maxY, minZ),
                new Vector3f(maxX, maxY, minZ),
                sprite, u0, v0, u1, scaledV1, color, Direction.NORTH, luminosity
            ));

            // South face (+Z)
            quads.add(createQuad(
                new Vector3f(minX, minY, maxZ),
                new Vector3f(maxX, minY, maxZ),
                new Vector3f(maxX, maxY, maxZ),
                new Vector3f(minX, maxY, maxZ),
                sprite, u0, v0, u1, scaledV1, color, Direction.SOUTH, luminosity
            ));

            // West face (-X)
            quads.add(createQuad(
                new Vector3f(minX, minY, minZ),
                new Vector3f(minX, minY, maxZ),
                new Vector3f(minX, maxY, maxZ),
                new Vector3f(minX, maxY, minZ),
                sprite, u0, v0, u1, scaledV1, color, Direction.WEST, luminosity
            ));

            // East face (+X)
            quads.add(createQuad(
                new Vector3f(maxX, minY, maxZ),
                new Vector3f(maxX, minY, minZ),
                new Vector3f(maxX, maxY, minZ),
                new Vector3f(maxX, maxY, maxZ),
                sprite, u0, v0, u1, scaledV1, color, Direction.EAST, luminosity
            ));

            return quads;
        }

        private BakedQuad createQuad(
            Vector3f pos1, Vector3f pos2, Vector3f pos3, Vector3f pos4,
            TextureAtlasSprite sprite,
            float u0, float v0, float u1, float v1,
            int color, Direction face, int luminosity
        ) {
            int[] vertexData = new int[32];

            float alpha = ((color >> 24) & 0xFF) / 255f;
            if (alpha <= 0) alpha = 1.0f;
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;

            int packedColor = ((int)(alpha * 255) << 24) | ((int)(red * 255) << 16) | ((int)(green * 255) << 8) | (int)(blue * 255);

            // Calculate light based on luminosity
            int light = luminosity > 0 ? (luminosity << 4) | (luminosity << 20) : 0;

            putVertex(vertexData, 0, pos1, u0, v1, packedColor, face, light);
            putVertex(vertexData, 8, pos2, u1, v1, packedColor, face, light);
            putVertex(vertexData, 16, pos3, u1, v0, packedColor, face, light);
            putVertex(vertexData, 24, pos4, u0, v0, packedColor, face, light);

            return new BakedQuad(vertexData, 0, face, sprite, true);
        }

        private void putVertex(int[] data, int offset, Vector3f pos, float u, float v, int color, Direction face, int light) {
            data[offset] = Float.floatToRawIntBits(pos.x());
            data[offset + 1] = Float.floatToRawIntBits(pos.y());
            data[offset + 2] = Float.floatToRawIntBits(pos.z());
            data[offset + 3] = color;
            data[offset + 4] = Float.floatToRawIntBits(u);
            data[offset + 5] = Float.floatToRawIntBits(v);
            data[offset + 6] = packNormal(face);
            data[offset + 7] = light;
        }

        private int packNormal(Direction face) {
            int x = (face.getStepX() * 127) & 0xFF;
            int y = (face.getStepY() * 127) & 0xFF;
            int z = (face.getStepZ() * 127) & 0xFF;
            return x | (y << 8) | (z << 16);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        @Nonnull
        public TextureAtlasSprite getParticleIcon() {
            return particleSprite != null ? particleSprite : Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(ResourceLocation.withDefaultNamespace("missingno"));
        }

        @Override
        @Nonnull
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        @Nonnull
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    /**
     * Cache key for fluid quads.
     */
    private record FluidCacheKey(ResourceLocation fluidId, float fillPercentage) {}
}
