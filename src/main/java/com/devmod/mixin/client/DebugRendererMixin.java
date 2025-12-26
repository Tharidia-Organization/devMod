package com.devmod.mixin.client;

import javax.annotation.Nonnull;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;

import com.devmod.debug.client.DebugRenderBools;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Client-side mixin to enable debug renderers.
 * In release builds, DebugRenderer.render() never calls the actual renderers.
 * This mixin adds those calls back when debug features are enabled.
 *
 * Uses direct field access on DebugRenderer instance to avoid @Shadow issues.
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Shadow @Final public net.minecraft.client.renderer.debug.PathfindingRenderer pathfindingRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.GoalSelectorDebugRenderer goalSelectorRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.RaidDebugRenderer raidDebugRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.VillageSectionsDebugRenderer villageSectionsDebugRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.BrainDebugRenderer brainDebugRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.BeeDebugRenderer beeDebugRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.GameEventListenerRenderer gameEventListenerRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.StructureRenderer structureRenderer;
    @Shadow @Final public net.minecraft.client.renderer.debug.BreezeDebugRenderer breezeDebugRenderer;

    /**
     * Inject at the end of render() to actually render the debug data.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void devmod_render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource.BufferSource bufferSource,
                               double camX, double camY, double camZ, @Nonnull CallbackInfo ci) {

        if (DebugRenderBools.ENTITY_PATHING) {
            this.pathfindingRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.ENTITY_GOALS) {
            this.goalSelectorRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.RAIDS) {
            this.raidDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.ENTITY_BRAINS) {
            this.brainDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.BEES || DebugRenderBools.BEE_HIVES) {
            this.beeDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.GAME_EVENTS) {
            this.gameEventListenerRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.STRUCTURES) {
            this.structureRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.POI) {
            this.villageSectionsDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.BREEZE) {
            this.breezeDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
    }

    /**
     * Clear all renderers when they should be reset.
     */
    @Inject(method = "clear", at = @At("TAIL"))
    private void devmod_clear(@Nonnull CallbackInfo ci) {
        this.pathfindingRenderer.clear();
        this.goalSelectorRenderer.clear();
        this.raidDebugRenderer.clear();
        this.brainDebugRenderer.clear();
        this.beeDebugRenderer.clear();
        this.gameEventListenerRenderer.clear();
        this.structureRenderer.clear();
        this.villageSectionsDebugRenderer.clear();
        this.breezeDebugRenderer.clear();
    }
}
