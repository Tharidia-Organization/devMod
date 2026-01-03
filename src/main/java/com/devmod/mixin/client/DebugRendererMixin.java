package com.devmod.mixin.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;

import com.devmod.debug.client.DebugRenderBools;

@Mixin(DebugRenderer.class)
@SuppressWarnings({"NullAway.Init", "UnusedMethod"})
public class DebugRendererMixin {

    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.PathfindingRenderer pathfindingRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.GoalSelectorDebugRenderer goalSelectorRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.RaidDebugRenderer raidDebugRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.VillageSectionsDebugRenderer villageSectionsDebugRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.BrainDebugRenderer brainDebugRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.BeeDebugRenderer beeDebugRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.GameEventListenerRenderer gameEventListenerRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.StructureRenderer structureRenderer;
    @Nullable @Shadow @Final public net.minecraft.client.renderer.debug.BreezeDebugRenderer breezeDebugRenderer;

    /**
     * Inject at the end of render() to actually render the debug data.
     */
    @Inject(method = "render", at = @At("TAIL"))
    void devmod_render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource.BufferSource bufferSource,
                       double camX, double camY, double camZ, @Nonnull CallbackInfo ci) {

        if (DebugRenderBools.isEntityPathing() && this.pathfindingRenderer != null) {
            this.pathfindingRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isEntityGoals() && this.goalSelectorRenderer != null) {
            this.goalSelectorRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isRaids() && this.raidDebugRenderer != null) {
            this.raidDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isEntityBrains() && this.brainDebugRenderer != null) {
            this.brainDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if ((DebugRenderBools.isBees() || DebugRenderBools.isBeeHives()) && this.beeDebugRenderer != null) {
            this.beeDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isGameEvents() && this.gameEventListenerRenderer != null) {
            this.gameEventListenerRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isStructures() && this.structureRenderer != null) {
            this.structureRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isPoi() && this.villageSectionsDebugRenderer != null) {
            this.villageSectionsDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        if (DebugRenderBools.isBreeze() && this.breezeDebugRenderer != null) {
            this.breezeDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
    }

    /**
     * Clear all renderers when they should be reset.
     */
    @Inject(method = "clear", at = @At("TAIL"))
    void devmod_clear(@Nonnull CallbackInfo ci) {
        if (this.pathfindingRenderer != null) {
            this.pathfindingRenderer.clear();
        }
        if (this.goalSelectorRenderer != null) {
            this.goalSelectorRenderer.clear();
        }
        if (this.raidDebugRenderer != null) {
            this.raidDebugRenderer.clear();
        }
        if (this.brainDebugRenderer != null) {
            this.brainDebugRenderer.clear();
        }
        if (this.beeDebugRenderer != null) {
            this.beeDebugRenderer.clear();
        }
        if (this.gameEventListenerRenderer != null) {
            this.gameEventListenerRenderer.clear();
        }
        if (this.structureRenderer != null) {
            this.structureRenderer.clear();
        }
        if (this.villageSectionsDebugRenderer != null) {
            this.villageSectionsDebugRenderer.clear();
        }
        if (this.breezeDebugRenderer != null) {
            this.breezeDebugRenderer.clear();
        }
    }
}
