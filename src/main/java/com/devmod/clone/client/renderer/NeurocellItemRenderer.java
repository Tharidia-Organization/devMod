package com.devmod.clone.client.renderer;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.block.entity.NeurocellItemBlockEntity;

/**
 * Renderer for the Neurocell Item Display block entity.
 * Renders a single item floating and rotating inside the chamber.
 *
 * <p>Uses LOD (Level of Detail) system matching NeurocellRenderer:
 * <ul>
 *   <li>Distance &lt; 16 blocks: Full 3D item rendering</li>
 *   <li>Distance 16-64 blocks: 2D billboard sprite (uses item texture)</li>
 *   <li>Distance &gt; 64 blocks: Culled (not rendered)</li>
 * </ul>
 */
public class NeurocellItemRenderer implements BlockEntityRenderer<NeurocellItemBlockEntity> {

    /** Distance threshold for LOD billboard rendering (16 blocks squared) */
    private static final double LOD_BILLBOARD_DISTANCE_SQ = 16 * 16;

    /** Maximum render distance (64 blocks squared) */
    private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;

    private final ItemRenderer itemRenderer;

    public NeurocellItemRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(
        @Nonnull NeurocellItemBlockEntity blockEntity,
        float partialTick,
        @Nonnull PoseStack poseStack,
        @Nonnull MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        ItemStack displayItem = blockEntity.getDisplayItem();
        if (displayItem.isEmpty()) {
            return;
        }

        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        // Calculate distance for LOD
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        long gameTime = level.getGameTime();
        float animTime = (gameTime + partialTick) * 0.05f;

        // LOD: Use billboard for distant rendering (16-64 blocks)
        if (distSq > LOD_BILLBOARD_DISTANCE_SQ) {
            // Use item's registry name as billboard type
            // Item is guaranteed non-null since we checked isEmpty() above
            @SuppressWarnings("null")
            var itemKey = BuiltInRegistries.ITEM.getKey(displayItem.getItem());
            String itemType = itemKey != null ? Objects.requireNonNull(itemKey.toString()) : "minecraft:stone";

            // Add billboard to batcher
            BillboardBatcher.getInstance().addBillboard(
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                itemType,
                0.6f,  // Scale (smaller for items)
                1.0f   // Alpha
            );

            // Still render energy effects at medium distance
            renderEnergyEffects(poseStack, animTime);
            return;
        }

        // Full 3D rendering for close range (< 16 blocks)

        // Get rotation parameters based on mode
        NeurocellItemBlockEntity.RotationMode rotationMode = blockEntity.getRotationMode();
        float rotationSpeed = blockEntity.getRotationSpeed();
        float rotation;

        switch (rotationMode) {
            case AUTO:
                // Automatic rotation - aligned with NeurocellRenderer pattern: 8.0°/frame base
                rotation = animTime * 8.0f * rotationSpeed;
                break;
            case FIXED:
            case MANUAL:
                // Fixed or manual angle - use stored rotation angle
                rotation = blockEntity.getRotationAngle();
                break;
            default:
                rotation = 0.0f;
        }

        // Bobbing motion aligned with NeurocellRenderer: 0.8 freq, 0.03 amp
        float bobOffset = Mth.sin(animTime * 0.8f) * 0.03f;

        // Render the item
        poseStack.pushPose();

        // Position in center of block, at mid-height of double block
        poseStack.translate(0.5, 1.0 + bobOffset, 0.5);

        // Apply rotation (with 180° base like NeurocellRenderer)
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180.0f + rotation)));

        // Slight tilt for visual interest
        poseStack.mulPose(Objects.requireNonNull(Axis.XP.rotationDegrees(15.0f)));

        // Scale for visibility
        float scale = 1.2f;
        poseStack.scale(scale, scale, scale);

        // Render the item with full brightness (like NeurocellRenderer)
        try {
            itemRenderer.renderStatic(
                displayItem,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                overlay,
                poseStack,
                buffer,
                level,
                0
            );
        } catch (RuntimeException e) {
            // Ignore rendering errors
        }

        poseStack.popPose();

        // Render energy effects
        renderEnergyEffects(poseStack, animTime);
    }

    /**
     * Render energy effects around the item.
     * Uses NeurocellEffectsVBO for efficient batched rendering.
     * Uses ORANGE color variant for visual distinction.
     */
    private void renderEnergyEffects(PoseStack poseStack, float animTime) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Set shader for VBO rendering
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Render VBO effects with scale matching chamber size
        NeurocellEffectsVBO effects = NeurocellEffectsVBO.getInstance();
        if (effects != null) {
            // Scale for item display chamber (smaller than standard neurocell)
            poseStack.scale(0.7f, 0.7f, 0.7f);
            // Uses ORANGE color for NeurocellItem distinction
            effects.renderEffects(poseStack, animTime, 1.0f, NeurocellEffectsMesh.EffectColor.ORANGE);
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellItemBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@Nonnull NeurocellItemBlockEntity blockEntity, @Nonnull Vec3 cameraPos) {
        // Skip if no item (early exit like NeurocellRenderer)
        if (!blockEntity.hasDisplayItem()) {
            return false;
        }

        // Distance-based culling (max 64 blocks)
        BlockPos pos = blockEntity.getBlockPos();
        double distSq = cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }

        return true;
    }
}
