package com.devmod.clone.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity;
import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity.RotationMode;

/**
 * Renderer for the Neurocell Mannequin block entity.
 * Renders a generic player (Steve skin) with equipment inside the chamber.
 *
 * <p>Uses LOD (Level of Detail) system matching NeurocellRenderer:
 * <ul>
 *   <li>Distance &lt; 16 blocks: Full 3D player model rendering</li>
 *   <li>Distance 16-64 blocks: 2D billboard sprite</li>
 *   <li>Distance &gt; 64 blocks: Culled (not rendered)</li>
 * </ul>
 *
 * <p>Uses a cached RemotePlayer entity with generic Steve skin for efficient rendering.
 */
public class NeurocellMannequinRenderer implements BlockEntityRenderer<NeurocellMannequinBlockEntity> {

    /** Distance threshold for LOD billboard rendering (16 blocks squared) */
    private static final double LOD_BILLBOARD_DISTANCE_SQ = 16 * 16;

    /** Maximum render distance (64 blocks squared) */
    private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;

    /** Fixed UUID for the mannequin's generic Steve profile */
    private static final UUID MANNEQUIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Generic Steve profile for mannequin rendering */
    private static final GameProfile STEVE_PROFILE = new GameProfile(MANNEQUIN_UUID, "Steve");

    private final EntityRenderDispatcher entityRenderer;

    /**
     * Cached player entities for rendering, keyed by skin UUID.
     * Uses LRU eviction to limit memory usage.
     */
    private final Map<UUID, RemotePlayer> playerCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RemotePlayer> eldest) {
            return this.size() > 20; // Max 20 cached skins
        }
    };

    public NeurocellMannequinRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
    }

    @Override
    public void render(
        @Nonnull NeurocellMannequinBlockEntity blockEntity,
        float partialTick,
        @Nonnull PoseStack poseStack,
        @Nonnull MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        // Skip if no equipment
        if (!blockEntity.hasEquipment()) {
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
            // Get equipment for billboard key generation
            ItemStack head = blockEntity.getEquipment(EquipmentSlot.HEAD);
            ItemStack chest = blockEntity.getEquipment(EquipmentSlot.CHEST);
            ItemStack legs = blockEntity.getEquipment(EquipmentSlot.LEGS);
            ItemStack feet = blockEntity.getEquipment(EquipmentSlot.FEET);
            ItemStack mainHand = blockEntity.getEquipment(EquipmentSlot.MAINHAND);
            ItemStack offHand = blockEntity.getEquipment(EquipmentSlot.OFFHAND);

            // Generate unique billboard key based on equipment
            MannequinBillboardRegistry registry = MannequinBillboardRegistry.getInstance();
            String billboardKey = registry.generateKey(head, chest, legs, feet, mainHand, offHand);

            // Register equipment data for atlas rendering (only if not already in atlas)
            if (!EntityBillboardAtlas.getInstance().hasEntity(billboardKey)) {
                registry.registerEquipment(billboardKey, head, chest, legs, feet, mainHand, offHand);
            }

            // Add billboard to batcher with equipment-specific key
            BillboardBatcher.getInstance().addBillboard(
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                billboardKey,
                0.8f,  // Scale
                1.0f   // Alpha (full opacity since we always have equipment)
            );

            // Still render energy effects at medium distance
            renderEnergyEffects(poseStack, animTime);
            return;
        }

        // Full 3D rendering for close range (< 16 blocks)
        UUID skinUUID = blockEntity.getSkinUUID();
        RemotePlayer player = getOrCreatePlayer(skinUUID);
        if (player == null) {
            return;
        }

        // Apply equipment from block entity
        applyEquipment(player, blockEntity);

        // Calculate rotation based on mode
        float totalRotation;
        RotationMode rotationMode = blockEntity.getRotationMode();
        float baseRotation = blockEntity.getRotationAngle();

        switch (rotationMode) {
            case FIXED -> totalRotation = baseRotation;
            case MANUAL -> totalRotation = baseRotation;
            case AUTO -> totalRotation = baseRotation + (animTime * 8.0f);
            default -> totalRotation = baseRotation + (animTime * 8.0f);
        }

        // Render the player model
        poseStack.pushPose();

        // Position in center of block, elevated to center mannequin in glass chamber
        // Base Y offset: 0.35 places feet above support block floor
        // Bobbing aligned with NeurocellRenderer: 0.8 freq, 0.03 amp
        float bobOffset = Mth.sin(animTime * 0.8f) * 0.03f;
        poseStack.translate(0.5, 0.35 + bobOffset, 0.5);

        // Apply rotation (180° base + auto rotation like NeurocellRenderer)
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180.0f + totalRotation)));

        // Scale down slightly to fit in chamber (player is taller than armor stand)
        poseStack.scale(0.85f, 0.85f, 0.85f);

        // Update tick count for any item animations
        player.tickCount = (int) (gameTime % Integer.MAX_VALUE);

        // Disable movement animations (pattern from NeurocellRenderer)
        player.walkAnimation.setSpeed(0);
        player.walkAnimation.update(0, 0);
        player.yBodyRotO = player.yBodyRot;
        player.yHeadRotO = player.yHeadRot;
        player.hurtTime = 0;
        player.deathTime = 0;

        // Render the player
        try {
            entityRenderer.render(player, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            // Ignore rendering errors
        }

        poseStack.popPose();

        // Render energy effects
        renderEnergyEffects(poseStack, animTime);
    }

    /**
     * Get or create a cached player for rendering with the specified skin.
     * Pattern from NeurocellRenderer: Entity is positioned at Y=-1000 and never added to level.
     *
     * @param skinUUID UUID for custom skin, or null for default Steve skin
     * @return Cached RemotePlayer with the requested skin
     */
    @Nullable
    private RemotePlayer getOrCreatePlayer(@Nullable UUID skinUUID) {
        // Use default Steve UUID if no custom skin
        UUID cacheKey = skinUUID != null ? skinUUID : MANNEQUIN_UUID;

        // Check cache first
        RemotePlayer cached = playerCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return null;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }

        // Create GameProfile with the appropriate UUID
        GameProfile profile;
        if (skinUUID != null) {
            // Custom skin - use the player's UUID to load their skin
            profile = new GameProfile(skinUUID, "CustomSkin");
        } else {
            // Default Steve skin
            profile = STEVE_PROFILE;
        }

        // Create RemotePlayer with the profile
        RemotePlayer player = new RemotePlayer(clientLevel, Objects.requireNonNull(profile));

        // CRITICAL ISOLATION: Ensure entity is 100% isolated from game logic
        // Entity is NEVER added to level's entity list - exists ONLY in local cache
        player.noPhysics = true;
        player.setNoGravity(true);
        player.setSilent(true);
        player.setInvulnerable(true);

        // Position far from any possible interaction (NeurocellRenderer pattern)
        player.setPos(0, -1000, 0);

        playerCache.put(cacheKey, player);
        return player;
    }

    /**
     * Apply equipment from block entity to player.
     */
    private void applyEquipment(@Nonnull RemotePlayer player, @Nonnull NeurocellMannequinBlockEntity blockEntity) {
        player.setItemSlot(EquipmentSlot.HEAD, blockEntity.getEquipment(EquipmentSlot.HEAD));
        player.setItemSlot(EquipmentSlot.CHEST, blockEntity.getEquipment(EquipmentSlot.CHEST));
        player.setItemSlot(EquipmentSlot.LEGS, blockEntity.getEquipment(EquipmentSlot.LEGS));
        player.setItemSlot(EquipmentSlot.FEET, blockEntity.getEquipment(EquipmentSlot.FEET));
        player.setItemSlot(EquipmentSlot.MAINHAND, blockEntity.getEquipment(EquipmentSlot.MAINHAND));
        player.setItemSlot(EquipmentSlot.OFFHAND, blockEntity.getEquipment(EquipmentSlot.OFFHAND));
    }

    /**
     * Render energy effects around the mannequin.
     * Uses NeurocellEffectsVBO for efficient batched rendering.
     * Uses GREEN color variant for visual distinction.
     */
    private void renderEnergyEffects(PoseStack poseStack, float animTime) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Set shader for VBO rendering
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Render VBO effects with scale matching chamber size
        NeurocellEffectsVBO effects = NeurocellEffectsVBO.getInstance();
        if (effects != null) {
            // Scale for mannequin chamber (slightly smaller than standard neurocell)
            poseStack.scale(0.8f, 0.8f, 0.8f);
            // Uses GREEN color for Mannequin distinction
            effects.renderEffects(poseStack, animTime, 1.0f, NeurocellEffectsMesh.EffectColor.GREEN);
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellMannequinBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@Nonnull NeurocellMannequinBlockEntity blockEntity, @Nonnull Vec3 cameraPos) {
        // Skip if no equipment (early exit like NeurocellRenderer)
        if (!blockEntity.hasEquipment()) {
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
