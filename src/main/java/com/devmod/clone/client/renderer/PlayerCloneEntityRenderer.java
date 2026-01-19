package com.devmod.clone.client.renderer;

import java.util.Objects;
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

import com.devmod.clone.entity.PlayerCloneEntity;

/**
 * Renderer for PlayerCloneEntity.
 * Renders the clone using a humanoid model with the original player's skin.
 *
 * <p>Attempts to fetch the skin from the session server using the original player UUID.
 * Falls back to Steve skin if no skin is available.
 */
public class PlayerCloneEntityRenderer extends MobRenderer<PlayerCloneEntity, HumanoidModel<PlayerCloneEntity>> {

    /** Default Steve skin texture location. */
    private static final ResourceLocation STEVE_SKIN =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    /** Wide (Steve) model for normal skins. */
    private final HumanoidModel<PlayerCloneEntity> steveModel;

    /** Slim (Alex) model for slim skins. */
    private final HumanoidModel<PlayerCloneEntity> alexModel;

    public PlayerCloneEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(Objects.requireNonNull(context.bakeLayer(Objects.requireNonNull(ModelLayers.PLAYER)))), 0.5f);
        this.steveModel = new HumanoidModel<>(Objects.requireNonNull(context.bakeLayer(Objects.requireNonNull(ModelLayers.PLAYER))));
        this.alexModel = new HumanoidModel<>(Objects.requireNonNull(context.bakeLayer(Objects.requireNonNull(ModelLayers.PLAYER_SLIM))));
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureLocation(@Nonnull PlayerCloneEntity entity) {
        // Try to resolve skin from entity's original player UUID
        UUID originalUuid = entity.getOriginalPlayerUUID();
        if (originalUuid != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo playerInfo = connection.getPlayerInfo(originalUuid);
                if (playerInfo != null) {
                    PlayerSkin skin = playerInfo.getSkin();
                    // Update model based on skin type
                    this.model = skin.model() == PlayerSkin.Model.SLIM ? this.alexModel : this.steveModel;
                    return Objects.requireNonNull(skin.texture());
                }
            }
        }

        // Fallback to Steve skin
        this.model = this.steveModel;
        return Objects.requireNonNull(STEVE_SKIN);
    }

    @Override
    protected void scale(@Nonnull PlayerCloneEntity entity, @Nonnull PoseStack poseStack, float partialTick) {
        // Match player proportions
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected boolean shouldShowName(@Nonnull PlayerCloneEntity entity) {
        // Show name if clone has a skin name set or custom name
        return entity.hasCustomName() || !entity.getSkinName().isEmpty();
    }
}
