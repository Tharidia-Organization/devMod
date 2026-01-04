package com.devmod.mixin.client;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Compatibility mixin for MoreCulling mod.
 *
 * MoreCulling adds a lazy-initialized field `moreculling$cullingShapesByFace` to BlockStateBase.
 * In DevMod's dynamic dimensions, there can be a race condition where the render thread
 * tries to access the culling shapes before they're initialized, causing a NullPointerException.
 *
 * This mixin intercepts the getter and returns an empty shape if the cache is null,
 * preventing the crash and allowing normal rendering to proceed.
 *
 * The injection uses `require = 0` so it gracefully does nothing if MoreCulling isn't installed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
@SuppressWarnings({"UnusedMethod", "UnusedVariable", "NullAway", "NullAway.Init"})
public class MoreCullingCompatMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod/MoreCullingCompat");

    // Cache the field reference to avoid repeated reflection lookups
    private static volatile Field cullingShapesByFaceField;
    private static volatile boolean fieldLookupAttempted = false;
    private static volatile boolean fieldLookupLogged = false;

    /**
     * Intercepts MoreCulling's getFaceCullingShape method to add null protection.
     * If the culling shapes array is null (not yet initialized), returns an empty shape
     * instead of causing a NullPointerException.
     */
    @Inject(
        method = "moreculling$getFaceCullingShape",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,  // Don't fail if MoreCulling isn't installed
        remap = false  // This method is added by MoreCulling, not vanilla
    )
    private void devmod$nullSafeFaceCullingShape(Direction face, CallbackInfoReturnable<VoxelShape> cir) {
        // Fast path: check if field is known to be null using cached reflection
        if (isCullingCacheNull()) {
            // Return empty shape - this means "don't cull" which is safe
            cir.setReturnValue(Shapes.empty());
        }
    }

    /**
     * Checks if the MoreCulling culling shapes cache is null for this block state.
     * Uses cached reflection to minimize performance impact.
     */
    private boolean isCullingCacheNull() {
        // Initialize field reference on first access
        if (!fieldLookupAttempted) {
            synchronized (MoreCullingCompatMixin.class) {
                if (!fieldLookupAttempted) {
                    try {
                        cullingShapesByFaceField = BlockBehaviour.BlockStateBase.class
                            .getDeclaredField("moreculling$cullingShapesByFace");
                        cullingShapesByFaceField.setAccessible(true);
                        if (!fieldLookupLogged) {
                            LOGGER.debug("MoreCulling compatibility: field found, null-safety protection active");
                            fieldLookupLogged = true;
                        }
                    } catch (NoSuchFieldException e) {
                        // Field doesn't exist - MoreCulling might not be installed or uses different version
                        cullingShapesByFaceField = null;
                        if (!fieldLookupLogged) {
                            LOGGER.debug("MoreCulling compatibility: field not found (mod not installed or different version)");
                            fieldLookupLogged = true;
                        }
                    }
                    fieldLookupAttempted = true;
                }
            }
        }

        // If field wasn't found, we can't check - assume not null (let original code handle it)
        if (cullingShapesByFaceField == null) {
            return false;
        }

        try {
            return cullingShapesByFaceField.get(this) == null;
        } catch (IllegalAccessException e) {
            // Shouldn't happen since we set accessible, but be safe
            return false;
        }
    }
}
