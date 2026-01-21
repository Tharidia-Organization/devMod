package com.devmod.foundry.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec2;

import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.devmod.DevMod;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfo;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfo.TintedSprite;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfoLoader;
import com.devmod.foundry.client.model.util.FoundryItemLayerModel;
import com.devmod.foundry.client.model.util.FoundryItemLayerPixels;
import com.devmod.foundry.client.model.util.FoundryReversedListBuilder;
import com.devmod.foundry.tool.FoundryToolData;

/**
 * Foundry tool model loader that supports the TConstruct-style tool JSON schema.
 * Handles material-based part textures, modifier overlays, and optional ammo rendering.
 */
public class FoundryToolModelLoader implements IGeometryLoader<FoundryToolModelLoader.ToolGeometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_tool");
    public static final FoundryToolModelLoader INSTANCE = new FoundryToolModelLoader();

    private static final BitSet SMALL_TOOL_TYPES = new BitSet();

    static {
        registerSmallTool(ItemDisplayContext.FIXED);
        registerSmallTool(ItemDisplayContext.GROUND);
    }

    private FoundryToolModelLoader() {}

    public static synchronized void registerSmallTool(ItemDisplayContext type) {
        SMALL_TOOL_TYPES.set(type.ordinal());
    }

    @Override
    public ToolGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        List<ToolPart> parts = Collections.emptyList();
        if (json.has("parts")) {
            JsonArray partsArray = GsonHelper.getAsJsonArray(json, "parts");
            List<ToolPart> parsed = new ArrayList<>(partsArray.size());
            for (int i = 0; i < partsArray.size(); i++) {
                parsed.add(ToolPart.read(GsonHelper.convertToJsonObject(partsArray.get(i), "parts[" + i + "]")));
            }
            parts = List.copyOf(parsed);
        }

        boolean isLarge = GsonHelper.getAsBoolean(json, "large", false);
        boolean showTraits = GsonHelper.getAsBoolean(json, "show_traits", false);
        Vec2 offset = getOffset(json, "large_offset");

        List<ResourceLocation> smallModifierRoots = Collections.emptyList();
        List<ResourceLocation> largeModifierRoots = Collections.emptyList();
        if (json.has("modifier_roots")) {
            if (isLarge) {
                JsonObject modifierRoots = GsonHelper.getAsJsonObject(json, "modifier_roots");
                smallModifierRoots = parseResourceList(modifierRoots, "small");
                largeModifierRoots = parseResourceList(modifierRoots, "large");
            } else {
                smallModifierRoots = parseResourceList(json, "modifier_roots");
            }
        }

        List<FirstModifier> firstModifiers = parseFirstModifiers(json);

        ResourceLocation ammoKey = null;
        Vec2 smallAmmoOffset = Vec2.ZERO;
        Vec2 largeAmmoOffset = Vec2.ZERO;
        boolean flipAmmo = false;
        boolean leftAmmo = false;
        if (json.has("ammo")) {
            JsonObject ammo = GsonHelper.getAsJsonObject(json, "ammo");
            ammoKey = ResourceLocation.parse(GsonHelper.getAsString(ammo, "key"));
            flipAmmo = GsonHelper.getAsBoolean(ammo, "flip", false);
            leftAmmo = GsonHelper.getAsBoolean(ammo, "left", false);
            if (isLarge) {
                if (!ammo.has("small_offset") && !ammo.has("large_offset")) {
                    throw new JsonSyntaxException("Ammo must have either small_offset or large_offset provided");
                }
                smallAmmoOffset = getOffset(ammo, "small_offset");
                largeAmmoOffset = getOffset(ammo, "large_offset");
            } else {
                smallAmmoOffset = getVec2(ammo, "offset");
            }
        }

        return new ToolGeometry(
            parts,
            isLarge,
            offset,
            smallModifierRoots,
            largeModifierRoots,
            firstModifiers,
            ammoKey,
            flipAmmo,
            leftAmmo,
            smallAmmoOffset,
            largeAmmoOffset,
            showTraits
        );
    }

    private static Vec2 getOffset(JsonObject json, String key) {
        if (!json.has(key)) {
            return Vec2.ZERO;
        }
        return getVec2(json, key);
    }

    private static Vec2 getVec2(JsonObject json, String key) {
        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        if (array.size() != 2) {
            throw new JsonSyntaxException("Expected 2 values for " + key + " offset, found " + array.size());
        }
        float x = GsonHelper.convertToFloat(array.get(0), key + "[0]");
        float y = GsonHelper.convertToFloat(array.get(1), key + "[1]");
        return new Vec2(x, y);
    }

    private static List<ResourceLocation> parseResourceList(JsonObject json, String key) {
        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        List<ResourceLocation> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            list.add(ResourceLocation.parse(GsonHelper.convertToString(array.get(i), key + "[" + i + "]")));
        }
        return List.copyOf(list);
    }

    private static List<FirstModifier> parseFirstModifiers(JsonObject json) {
        if (!json.has("first_modifiers")) {
            return List.of();
        }
        JsonArray array = GsonHelper.getAsJsonArray(json, "first_modifiers");
        List<FirstModifier> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive()) {
                list.add(new FirstModifier(ResourceLocation.parse(element.getAsString()), false));
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(obj, "name"));
                boolean forced = GsonHelper.getAsBoolean(obj, "forced", false);
                list.add(new FirstModifier(id, forced));
            } else {
                throw new JsonSyntaxException("Expected first_modifiers[" + i + "] to be a string or object");
            }
        }
        return List.copyOf(list);
    }

    /** Data class for a single tool part. */
    private record ToolPart(String name, int index) {
        public static final ToolPart DEFAULT = new ToolPart("tool", -1);
        public static final List<ToolPart> DEFAULT_PARTS = List.of(DEFAULT);

        public boolean hasMaterials() {
            return index >= 0;
        }

        public String getName(boolean isLarge) {
            return isLarge ? "large_" + name : name;
        }

        public static ToolPart read(JsonObject json) {
            String name = GsonHelper.getAsString(json, "name");
            int index = GsonHelper.getAsInt(json, "index", -1);
            return new ToolPart(name, index);
        }
    }

    private record FirstModifier(ResourceLocation id, boolean forced) {
    }

    private record ModifierEntry(ResourceLocation id, int level) {}

    private record ToolCacheKey(List<ResourceLocation> materials, List<ModifierEntry> modifiers, @Nullable CompoundTag ammoTag) {}

    /** Unbaked geometry for Foundry tools. */
    public static class ToolGeometry implements IUnbakedGeometry<ToolGeometry> {
        private final List<ToolPart> toolParts;
        private final boolean isLarge;
        private final Vec2 offset;
        private final List<ResourceLocation> smallModifierRoots;
        private final List<ResourceLocation> largeModifierRoots;
        private final List<FirstModifier> firstModifiers;
        @Nullable
        private final ResourceLocation ammoKey;
        private final boolean flipAmmo;
        private final boolean leftAmmo;
        private final Vec2 smallAmmoOffset;
        private final Vec2 largeAmmoOffset;
        private final boolean showTraits;

        public ToolGeometry(
            List<ToolPart> toolParts,
            boolean isLarge,
            Vec2 offset,
            List<ResourceLocation> smallModifierRoots,
            List<ResourceLocation> largeModifierRoots,
            List<FirstModifier> firstModifiers,
            @Nullable ResourceLocation ammoKey,
            boolean flipAmmo,
            boolean leftAmmo,
            Vec2 smallAmmoOffset,
            Vec2 largeAmmoOffset,
            boolean showTraits
        ) {
            this.toolParts = toolParts;
            this.isLarge = isLarge;
            this.offset = offset;
            this.smallModifierRoots = smallModifierRoots;
            this.largeModifierRoots = largeModifierRoots;
            this.firstModifiers = firstModifiers;
            this.ammoKey = ammoKey;
            this.flipAmmo = flipAmmo;
            this.leftAmmo = leftAmmo;
            this.smallAmmoOffset = smallAmmoOffset;
            this.largeAmmoOffset = largeAmmoOffset;
            this.showTraits = showTraits;
        }

        @Override
        public BakedModel bake(
            IGeometryBakingContext owner,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelTransform,
            ItemOverrides overrides
        ) {
            List<ToolPart> parts = toolParts.isEmpty() ? ToolPart.DEFAULT_PARTS : toolParts;

            Transformation largeTransforms = null;
            if (isLarge) {
                largeTransforms = new Transformation(
                    new Vector3f((offset.x - 8f) / 32f, (-offset.y - 8f) / 32f, 0f),
                    null,
                    new Vector3f(2f, 2f, 1f),
                    null
                );
            }

            Transformation smallAmmoTransforms = null;
            Transformation largeAmmoTransforms = null;
            Transformation leftAmmoTransforms = null;
            if (ammoKey != null) {
                Quaternionf ammoRotation = flipAmmo ? Axis.YP.rotationDegrees(-180) : null;
                float flipOffset = flipAmmo ? 1f : 0f;

                Vector3f translation = new Vector3f(
                    smallAmmoOffset.x / 16f + flipOffset,
                    -smallAmmoOffset.y / 16f,
                    1f / 16f + flipOffset
                );
                smallAmmoTransforms = new Transformation(translation, ammoRotation, null, null);
                if (isLarge && largeTransforms != null) {
                    translation = new Vector3f(
                        (offset.x / 2f + largeAmmoOffset.x + 4f) / 16f + flipOffset,
                        (-offset.y / 2f - largeAmmoOffset.y + 4f) / 16f,
                        1f / 16f + flipOffset
                    );
                    largeAmmoTransforms = new Transformation(translation, ammoRotation, null, null);
                }
                if (leftAmmo) {
                    translation = new Vector3f(translation);
                    translation.z = -1f / 16f + flipOffset;
                    leftAmmoTransforms = new Transformation(translation, ammoRotation, null, null);
                }
            }

            overrides = new MaterialOverrideHandler(
                owner,
                parts,
                firstModifiers,
                showTraits,
                largeTransforms,
                smallModifierRoots,
                largeModifierRoots,
                overrides,
                ammoKey,
                flipAmmo,
                smallAmmoTransforms,
                largeAmmoTransforms,
                leftAmmoTransforms
            );

            return bakeInternal(
                owner,
                spriteGetter,
                largeTransforms,
                parts,
                smallModifierRoots,
                largeModifierRoots,
                List.of(),
                List.of(),
                overrides,
                List.of(),
                List.of(),
                List.of()
            );
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        }
    }

    /** Wrapper that swaps models based on display context. */
    private static class BakedToolModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel left;
        private final BakedModel small;
        private final BakedModel gui;

        private BakedToolModel(BakedModel right, BakedModel left, BakedModel small, BakedModel gui) {
            super(right);
            this.left = left;
            this.small = small;
            this.gui = gui;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext cameraTransformType, com.mojang.blaze3d.vertex.PoseStack mat, boolean applyLeftHandTransform) {
            BakedModel model = originalModel;
            if (cameraTransformType == ItemDisplayContext.GUI) {
                model = gui;
            } else if (cameraTransformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || cameraTransformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                model = left;
            } else if (originalModel != small && SMALL_TOOL_TYPES.get(cameraTransformType.ordinal())) {
                model = small;
            }
            return model.applyTransform(cameraTransformType, mat, applyLeftHandTransform);
        }
    }

    /** Dynamic override handler that swaps in material and modifier textures. */
    private static class MaterialOverrideHandler extends NestedOverrides {
        private final Map<ToolCacheKey, BakedModel> cache = new ConcurrentHashMap<>();

        private final IGeometryBakingContext owner;
        private final List<ToolPart> toolParts;
        private final List<FirstModifier> firstModifiers;
        private final boolean showTraits;
        @Nullable
        private final Transformation largeTransforms;
        private final List<ResourceLocation> smallModifierRoots;
        private final List<ResourceLocation> largeModifierRoots;
        @Nullable
        private final ResourceLocation ammoKey;
        private final boolean flipAmmo;
        @Nullable
        private final Transformation smallAmmoTransforms;
        @Nullable
        private final Transformation largeAmmoTransforms;
        @Nullable
        private final Transformation leftAmmoTransforms;

        private MaterialOverrideHandler(
            IGeometryBakingContext owner,
            List<ToolPart> toolParts,
            List<FirstModifier> firstModifiers,
            boolean showTraits,
            @Nullable Transformation largeTransforms,
            List<ResourceLocation> smallModifierRoots,
            List<ResourceLocation> largeModifierRoots,
            ItemOverrides nested,
            @Nullable ResourceLocation ammoKey,
            boolean flipAmmo,
            @Nullable Transformation smallAmmoTransforms,
            @Nullable Transformation largeAmmoTransforms,
            @Nullable Transformation leftAmmoTransforms
        ) {
            super(nested);
            this.owner = owner;
            this.toolParts = toolParts;
            this.firstModifiers = firstModifiers;
            this.showTraits = showTraits;
            this.largeTransforms = largeTransforms;
            this.smallModifierRoots = smallModifierRoots;
            this.largeModifierRoots = largeModifierRoots;
            this.ammoKey = ammoKey;
            this.flipAmmo = flipAmmo;
            this.smallAmmoTransforms = smallAmmoTransforms;
            this.largeAmmoTransforms = largeAmmoTransforms;
            this.leftAmmoTransforms = leftAmmoTransforms;
        }

        private BakedModel bakeDynamic(List<ResourceLocation> materials, List<ModifierEntry> modifiers, AmmoInfo ammoInfo, int seed) {
            List<BakedQuad> smallAmmoQuads = List.of();
            List<BakedQuad> largeAmmoQuads = List.of();
            List<BakedQuad> leftAmmoQuads = List.of();
            if (!ammoInfo.stack().isEmpty()) {
                List<BakedQuad> ammoQuads = bakeAmmoQuads(ammoInfo.stack(), seed, flipAmmo);
                if (smallAmmoTransforms != null) {
                    smallAmmoQuads = QuadTransformers.applying(smallAmmoTransforms).process(ammoQuads);
                }
                if (largeAmmoTransforms != null) {
                    largeAmmoQuads = QuadTransformers.applying(largeAmmoTransforms).process(ammoQuads);
                }
                if (leftAmmoTransforms != null) {
                    leftAmmoQuads = QuadTransformers.applying(leftAmmoTransforms).process(ammoQuads);
                }
            }

            return bakeInternal(
                owner,
                Material::sprite,
                largeTransforms,
                toolParts,
                smallModifierRoots,
                largeModifierRoots,
                materials,
                modifiers,
                ItemOverrides.EMPTY,
                smallAmmoQuads,
                largeAmmoQuads,
                leftAmmoQuads
            );
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
            BakedModel resolved = super.resolve(originalModel, stack, world, entity, seed);
            if (resolved != originalModel) {
                return resolved;
            }

            FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
            if (data == null) {
                return originalModel;
            }

            List<ResourceLocation> materials = data.materials();
            Map<ResourceLocation, Integer> modifierMap = showTraits ? data.modifiersWithEmbossment() : data.modifiers();
            List<ModifierEntry> orderedModifiers = orderModifiers(modifierMap, firstModifiers);

            AmmoInfo ammoInfo = getAmmoInfo(stack, world);
            ToolCacheKey key = new ToolCacheKey(List.copyOf(materials), List.copyOf(orderedModifiers), ammoInfo.tag());
            return cache.computeIfAbsent(key, k -> bakeDynamic(materials, orderedModifiers, ammoInfo, seed));
        }

        private AmmoInfo getAmmoInfo(ItemStack stack, @Nullable ClientLevel world) {
            if (ammoKey == null) {
                return AmmoInfo.empty();
            }
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag root = data.copyTag();
            String key = ammoKey.toString();
            if (!root.contains(key, Tag.TAG_COMPOUND)) {
                return AmmoInfo.empty();
            }
            CompoundTag ammoTag = root.getCompound(key).copy();
            HolderLookup.Provider registries = null;
            if (world != null) {
                registries = world.registryAccess();
            } else if (Minecraft.getInstance().level != null) {
                registries = Minecraft.getInstance().level.registryAccess();
            }
            if (registries == null) {
                return AmmoInfo.empty();
            }
            ItemStack ammo = ItemStack.parseOptional(registries, ammoTag);
            return new AmmoInfo(ammo, ammoTag);
        }
    }

    private record AmmoInfo(ItemStack stack, @Nullable CompoundTag tag) {
        private static AmmoInfo empty() {
            return new AmmoInfo(ItemStack.EMPTY, null);
        }
    }

    /** Helper class for delegating to nested overrides while also doing item-specific overrides. */
    private static class NestedOverrides extends ItemOverrides {
        private static boolean ignoreNested = false;
        private final ItemOverrides nested;

        private NestedOverrides(ItemOverrides nested) {
            this.nested = nested;
        }

        @Override
        @Nullable
        public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
            if (!ignoreNested) {
                BakedModel overridden = nested.resolve(originalModel, stack, world, entity, seed);
                if (overridden != null && overridden != originalModel) {
                    ignoreNested = true;
                    BakedModel finalModel = overridden.getOverrides().resolve(overridden, stack, world, entity, seed);
                    ignoreNested = false;
                    return finalModel;
                }
            }
            return originalModel;
        }
    }

    private static IModelBuilder<?> makeModelBuilder(IGeometryBakingContext context, ItemOverrides overrides, TextureAtlasSprite particle) {
        return IModelBuilder.of(
            context.useAmbientOcclusion(),
            false,
            false,
            context.getTransforms(),
            overrides,
            particle,
            FoundryItemLayerModel.getDefaultRenderType(context)
        );
    }

    private static BakedModel bakeInternal(
        IGeometryBakingContext owner,
        Function<Material, TextureAtlasSprite> spriteGetter,
        @Nullable Transformation largeTransforms,
        List<ToolPart> parts,
        List<ResourceLocation> smallModifierRoots,
        List<ResourceLocation> largeModifierRoots,
        List<ResourceLocation> materials,
        List<ModifierEntry> modifiers,
        ItemOverrides overrides,
        List<BakedQuad> smallExtraQuads,
        List<BakedQuad> largeExtraQuads,
        List<BakedQuad> leftExtraQuads
    ) {
        Transformation smallTransforms = Transformation.identity();

        FoundryReversedListBuilder<Collection<BakedQuad>> smallQuads = new FoundryReversedListBuilder<>();
        FoundryItemLayerPixels smallPixels = new FoundryItemLayerPixels();
        FoundryReversedListBuilder<Collection<BakedQuad>> largeQuads = largeTransforms != null ? new FoundryReversedListBuilder<>() : smallQuads;
        FoundryItemLayerPixels largePixels = largeTransforms != null ? new FoundryItemLayerPixels() : smallPixels;

        if (!modifiers.isEmpty()) {
            addModifierQuads(spriteGetter, modifiers, smallModifierRoots, smallQuads::add, smallPixels, smallTransforms);
            if (largeTransforms != null) {
                List<ResourceLocation> roots = largeModifierRoots.isEmpty() ? smallModifierRoots : largeModifierRoots;
                addModifierQuads(spriteGetter, modifiers, roots, largeQuads::add, largePixels, largeTransforms);
            }
        }

        TextureAtlasSprite particle = null;
        for (int i = parts.size() - 1; i >= 0; i--) {
            ToolPart part = parts.get(i);

            Material baseMaterialSmall = resolvePartMaterial(owner, part, false);
            ResourceLocation baseTextureSmall = baseMaterialSmall.texture();
            TintedSprite smallSprite = getPartSprite(materials, part, baseTextureSmall);
            particle = smallSprite.sprite();
            smallQuads.add(FoundryItemLayerModel.getQuadsForSprite(
                smallSprite.color(),
                -1,
                smallSprite.sprite(),
                smallTransforms,
                smallSprite.luminosity(),
                smallPixels
            ));

            if (largeTransforms != null) {
                Material baseMaterialLarge = resolvePartMaterial(owner, part, true);
                ResourceLocation baseTextureLarge = baseMaterialLarge.texture();
                TintedSprite largeSprite = getPartSprite(materials, part, baseTextureLarge);
                largeQuads.add(FoundryItemLayerModel.getQuadsForSprite(
                    largeSprite.color(),
                    -1,
                    largeSprite.sprite(),
                    largeTransforms,
                    largeSprite.luminosity(),
                    largePixels
                ));
            }
        }

        if (particle == null) {
            particle = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, MissingTextureAtlasSprite.getLocation()));
            DevMod.LOGGER.warn("[Foundry] Tool model baked without particle sprite: {}", owner.getModelName());
        }

        IModelBuilder<?> smallBuilder = makeModelBuilder(owner, overrides, particle);
        IModelBuilder<?> guiBuilder = makeModelBuilder(owner, overrides, particle);
        IModelBuilder<?> leftBuilder = !leftExtraQuads.isEmpty() ? makeModelBuilder(owner, overrides, particle) : null;

        if (largeTransforms == null && leftBuilder != null) {
            smallQuads.build(quads -> quads.forEach(quad -> {
                smallBuilder.addUnculledFace(quad);
                leftBuilder.addUnculledFace(quad);
                if (quad.getDirection() == net.minecraft.core.Direction.SOUTH) {
                    guiBuilder.addUnculledFace(quad);
                }
            }));
        } else {
            smallQuads.build(quads -> quads.forEach(quad -> {
                smallBuilder.addUnculledFace(quad);
                if (quad.getDirection() == net.minecraft.core.Direction.SOUTH) {
                    guiBuilder.addUnculledFace(quad);
                }
            }));
        }

        for (BakedQuad quad : smallExtraQuads) {
            smallBuilder.addUnculledFace(quad);
            if (quad.getDirection() == net.minecraft.core.Direction.SOUTH) {
                guiBuilder.addUnculledFace(quad);
            }
        }

        BakedModel small = smallBuilder.build();
        BakedModel gui = guiBuilder.build();

        BakedModel right;
        if (largeTransforms != null) {
            IModelBuilder<?> largeBuilder = makeModelBuilder(owner, overrides, particle);
            if (leftBuilder != null) {
                largeQuads.build(quads -> quads.forEach(quad -> {
                    largeBuilder.addUnculledFace(quad);
                    leftBuilder.addUnculledFace(quad);
                }));
            } else {
                largeQuads.build(quads -> quads.forEach(largeBuilder::addUnculledFace));
            }
            for (BakedQuad quad : largeExtraQuads) {
                largeBuilder.addUnculledFace(quad);
            }
            right = largeBuilder.build();
        } else {
            right = small;
        }

        BakedModel left;
        if (leftBuilder != null) {
            for (BakedQuad quad : leftExtraQuads) {
                leftBuilder.addUnculledFace(quad);
            }
            left = leftBuilder.build();
        } else {
            left = right;
        }

        return new BakedToolModel(right, left, small, gui);
    }

    private static Material resolvePartMaterial(IGeometryBakingContext owner, ToolPart part, boolean isLarge) {
        String name = part.getName(isLarge);
        if (isLarge && !owner.hasMaterial(name)) {
            name = part.getName(false);
        }
        return owner.getMaterial(name);
    }

    private static TintedSprite getPartSprite(List<ResourceLocation> materials, ToolPart part, ResourceLocation baseTexture) {
        ResourceLocation materialId = part.hasMaterials() && part.index() < materials.size()
            ? materials.get(part.index())
            : null;

        FoundryMaterialRenderInfo renderInfo = materialId != null
            ? FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(materialId)
            : FoundryMaterialRenderInfo.EMPTY;

        if (renderInfo == null) {
            renderInfo = FoundryMaterialRenderInfo.EMPTY;
        }

        return renderInfo.getSprite(baseTexture);
    }

    private static void addModifierQuads(
        Function<Material, TextureAtlasSprite> spriteGetter,
        List<ModifierEntry> modifiers,
        List<ResourceLocation> modifierRoots,
        java.util.function.Consumer<Collection<BakedQuad>> quadConsumer,
        @Nullable FoundryItemLayerPixels pixels,
        Transformation transforms
    ) {
        if (modifierRoots.isEmpty()) {
            return;
        }

        for (ModifierEntry entry : modifiers) {
            TextureAtlasSprite sprite = findModifierSprite(spriteGetter, modifierRoots, entry.id());
            if (sprite != null) {
                quadConsumer.accept(FoundryItemLayerModel.getQuadsForSprite(
                    -1,
                    -1,
                    sprite,
                    transforms,
                    0,
                    pixels
                ));
            }
        }
    }

    @Nullable
    private static TextureAtlasSprite findModifierSprite(Function<Material, TextureAtlasSprite> spriteGetter, List<ResourceLocation> roots, ResourceLocation modifierId) {
        String primary = modifierId.getNamespace() + "_" + modifierId.getPath();
        String fallback = modifierId.getPath();
        for (ResourceLocation root : roots) {
            TextureAtlasSprite sprite = getSpriteIfPresent(spriteGetter, root, primary);
            if (sprite != null) {
                return sprite;
            }
            sprite = getSpriteIfPresent(spriteGetter, root, fallback);
            if (sprite != null) {
                return sprite;
            }
        }
        return null;
    }

    @Nullable
    private static TextureAtlasSprite getSpriteIfPresent(Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation root, String suffix) {
        String path = root.getPath();
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(root.getNamespace(), path + suffix);
        TextureAtlasSprite sprite = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, location));
        return isMissingSprite(sprite) ? null : sprite;
    }

    private static boolean isMissingSprite(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    private static List<ModifierEntry> orderModifiers(Map<ResourceLocation, Integer> modifiers, List<FirstModifier> firstModifiers) {
        if (modifiers.isEmpty() && firstModifiers.isEmpty()) {
            return List.of();
        }

        List<ModifierEntry> ordered = new ArrayList<>();
        Set<ResourceLocation> handled = new HashSet<>();

        for (FirstModifier first : firstModifiers) {
            Integer level = modifiers.get(first.id());
            if (level != null || first.forced()) {
                ordered.add(new ModifierEntry(first.id(), level == null ? 0 : level));
                handled.add(first.id());
            }
        }

        List<ModifierEntry> remaining = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : modifiers.entrySet()) {
            if (!handled.contains(entry.getKey())) {
                remaining.add(new ModifierEntry(entry.getKey(), entry.getValue()));
            }
        }
        remaining.sort(Comparator.comparing(entry -> entry.id().toString()));
        ordered.addAll(remaining);
        return List.copyOf(ordered);
    }

    private static List<BakedQuad> bakeAmmoQuads(ItemStack ammo, int seed, boolean flipAmmo) {
        BakedModel ammoModel = Minecraft.getInstance().getItemRenderer().getModel(ammo, null, null, seed);
        if (ammoModel == Minecraft.getInstance().getModelManager().getMissingModel()) {
            return List.of();
        }
        ammoModel = ammoModel.getOverrides().resolve(ammoModel, ammo, null, null, seed);
        if (ammoModel == null) {
            return List.of();
        }

        List<BakedQuad> ammoQuads = new ArrayList<>();
        RandomSource rand = RandomSource.create();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            ammoQuads.addAll(ammoModel.getQuads(null, direction, rand, ModelData.EMPTY, null));
        }
        ammoQuads.addAll(ammoModel.getQuads(null, null, rand, ModelData.EMPTY, null));

        ItemColors colors = Minecraft.getInstance().getItemColors();
        List<BakedQuad> baked = new ArrayList<>(ammoQuads.size());
        for (BakedQuad quad : ammoQuads) {
            boolean modified = false;
            int[] vertices = quad.getVertices();
            net.minecraft.core.Direction direction = quad.getDirection();

            if (quad.isTinted()) {
                int color = 0xFF000000 | colors.getColor(ammo, quad.getTintIndex());
                int abgr = swapColorRedBlue(color);
                vertices = Arrays.copyOf(vertices, vertices.length);
                for (int i = 0; i < 4; i++) {
                    vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = abgr;
                }
                modified = true;
            }

            if (flipAmmo && direction.getAxis() != net.minecraft.core.Direction.Axis.Y) {
                direction = direction.getOpposite();
                modified = true;
            }

            if (modified) {
                baked.add(new BakedQuad(vertices, -1, direction, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
            } else {
                baked.add(quad);
            }
        }

        return List.copyOf(baked);
    }

    private static int swapColorRedBlue(int color) {
        return (color & 0xFF00FF00)
            | ((color >> 16) & 0x000000FF)
            | ((color << 16) & 0x00FF0000);
    }
}
