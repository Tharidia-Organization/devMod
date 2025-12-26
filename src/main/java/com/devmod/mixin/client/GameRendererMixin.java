package com.devmod.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;

import com.devmod.client.effects.ShakeManager;
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    Minecraft minecraft;

    /**
     * Intercepts processBlurEffect to skip blur for DevMod screens.
     * This method is called before the blur shader is applied to the screen.
     */
    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void devmod$skipBlurForModScreens(float partialTick, CallbackInfo ci) {
        Screen currentScreen = this.minecraft.screen;
        if (currentScreen != null) {
            String className = currentScreen.getClass().getName();
            // Skip blur for all DevMod screens
            if (className.contains("com.devmod")) {
                ci.cancel();
            }
        }
    }

    /**
     * Updates screen shake effects each tick.
     * This ensures smooth shake animations and cleanup of finished effects.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void devmod$tickShakeEffects(CallbackInfo ci) {
        if (this.minecraft.player != null) {
            ShakeManager.INSTANCE.tick();
        }
    }
}
