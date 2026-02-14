package com.devmod.npc.client.renderer;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Custom item renderer for Neurocell NPC item.
 * Renders a small humanoid model in the inventory/hand.
 */
@OnlyIn(Dist.CLIENT)
public class NeurocellNpcItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Steve skin texture for the NPC preview. */
    private static final ResourceLocation STEVE_SKIN =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    /** The humanoid model used for rendering. */
    private HumanoidModel<?> playerModel;

    /** Whether the model has been initialized. */
    private boolean initialized = false;

    public NeurocellNpcItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    /**
     * Lazy initialization of the player model.
     * Must be called on render thread after entity models are loaded.
     */
    private void ensureInitialized() {
        if (initialized) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityModels() == null) return;

        try {
            ModelPart modelPart = mc.getEntityModels().bakeLayer(Objects.requireNonNull(ModelLayers.PLAYER));
            playerModel = new HumanoidModel<>(Objects.requireNonNull(modelPart));
            initialized = true;
        } catch (Exception e) {
            // Models not ready yet, will try again next frame
        }
    }

    @Override
    public void renderByItem(
        @Nonnull ItemStack stack,
        @Nonnull ItemDisplayContext displayContext,
        @Nonnull PoseStack poseStack,
        @Nonnull MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        ensureInitialized();
        if (playerModel == null) return;

        poseStack.pushPose();

        // Adjust transform based on display context
        switch (displayContext) {
            case GUI -> {
                // Inventory slot - small centered model
                poseStack.translate(0.5, 0.1, 0.5);
                poseStack.scale(0.4f, 0.4f, 0.4f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
                poseStack.mulPose(Objects.requireNonNull(Axis.XP.rotationDegrees(10)));
            }
            case FIXED -> {
                // Item frame
                poseStack.translate(0.5, 0.2, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
            }
            case GROUND -> {
                // Dropped item
                poseStack.translate(0.5, 0.15, 0.5);
                poseStack.scale(0.35f, 0.35f, 0.35f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                // First person view
                poseStack.translate(0.5, 0.3, 0.5);
                poseStack.scale(0.3f, 0.3f, 0.3f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                // Third person view
                poseStack.translate(0.5, 0.3, 0.5);
                poseStack.scale(0.35f, 0.35f, 0.35f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
            }
            default -> {
                poseStack.translate(0.5, 0.2, 0.5);
                poseStack.scale(0.4f, 0.4f, 0.4f);
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180)));
            }
        }

        // Animate rotation in GUI for visual interest
        if (displayContext == ItemDisplayContext.GUI) {
            long time = System.currentTimeMillis();
            float rotation = (time % 4000) / 4000f * 360f;
            poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation)));
        }

        // Reset model pose
        playerModel.setAllVisible(true);
        playerModel.head.xRot = 0;
        playerModel.head.yRot = 0;
        playerModel.body.xRot = 0;
        playerModel.leftArm.xRot = 0;
        playerModel.leftArm.zRot = 0.1f;
        playerModel.rightArm.xRot = 0;
        playerModel.rightArm.zRot = -0.1f;
        playerModel.leftLeg.xRot = 0;
        playerModel.rightLeg.xRot = 0;

        // Render the model
        RenderType renderType = Objects.requireNonNull(RenderType.entityCutoutNoCull(Objects.requireNonNull(STEVE_SKIN)));
        VertexConsumer vertexConsumer = Objects.requireNonNull(bufferSource.getBuffer(renderType));
        playerModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}
