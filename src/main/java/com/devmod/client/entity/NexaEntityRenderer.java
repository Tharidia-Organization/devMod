package com.devmod.client.entity;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import com.devmod.entity.NexaEntity;

/**
 * Renderer for NexaEntity.
 * Renders the entity using a humanoid model with customizable skin.
 *
 * <p>Uses the default Steve skin if no custom skin is specified.
 * When a skin name is set, attempts to fetch the player's skin from the session server.
 */
public class NexaEntityRenderer extends MobRenderer<NexaEntity, HumanoidModel<NexaEntity>> {

    /** Default Steve skin texture location. */
    private static final ResourceLocation STEVE_SKIN =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    /** Wide (Steve) model for normal skins. */
    private final HumanoidModel<NexaEntity> steveModel;

    /** Slim (Alex) model for slim skins. */
    private final HumanoidModel<NexaEntity> alexModel;

    public NexaEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.steveModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        this.alexModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureLocation(@Nonnull NexaEntity entity) {
        // Try to resolve skin from entity's skin UUID
        UUID skinUuid = entity.getSkinUUID();
        if (skinUuid != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo playerInfo = connection.getPlayerInfo(skinUuid);
                if (playerInfo != null) {
                    PlayerSkin skin = playerInfo.getSkin();
                    // Update model based on skin type
                    this.model = skin.model() == PlayerSkin.Model.SLIM ? this.alexModel : this.steveModel;
                    return skin.texture();
                }
            }
        }

        // Fallback to Steve skin
        this.model = this.steveModel;
        return STEVE_SKIN;
    }

    @Override
    protected void scale(@Nonnull NexaEntity entity, @Nonnull PoseStack poseStack, float partialTick) {
        // Slight scale matching player proportions
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected boolean shouldShowName(@Nonnull NexaEntity entity) {
        return entity.hasCustomName();
    }
}
