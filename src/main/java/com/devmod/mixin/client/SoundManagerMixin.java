package com.devmod.mixin.client;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.errorprone.annotations.Keep;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    private static final ResourceLocation GOAT_HORN_PLAY = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("minecraft", "item.goat_horn.play"));
    private static final ResourceLocation GOAT_HORN_BREAK = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("minecraft", "entity.goat.horn_break"));
    private static final ResourceLocation GOAT_HORN_SCREAMING_BREAK = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("minecraft", "entity.goat.screaming.horn_break"));

    @Shadow
    @Final
    @Nullable
    private Map<ResourceLocation, WeighedSoundEvents> registry;

    @Inject(method = "getSoundEvent", at = @At("RETURN"), cancellable = true)
    @Keep
    private void devmod$aliasMissingSoundEvents(
            ResourceLocation id,
            CallbackInfoReturnable<WeighedSoundEvents> cir) {
        if (cir.getReturnValue() != null || id == null) {
            return;
        }
        if (registry == null) {
            return;
        }

        if (GOAT_HORN_PLAY.equals(id)) {
            WeighedSoundEvents fallback = buildGoatHornFallback();
            if (fallback != null) {
                registry.put(id, fallback);
                cir.setReturnValue(fallback);
            }
            return;
        }

        if (GOAT_HORN_SCREAMING_BREAK.equals(id)) {
            WeighedSoundEvents fallback = registry.get(GOAT_HORN_BREAK);
            if (fallback != null) {
                registry.put(id, fallback);
                cir.setReturnValue(fallback);
            }
        }
    }

    @Nullable
    private WeighedSoundEvents buildGoatHornFallback() {
        if (registry == null) {
            return null;
        }
        WeighedSoundEvents combined =
            new WeighedSoundEvents(GOAT_HORN_PLAY, "subtitles.item.goat_horn.play");
        boolean added = false;
        RandomSource random = RandomSource.create();

        for (int i = 0; i < 8; i++) {
            ResourceLocation variantId = Objects.requireNonNull(
                ResourceLocation.fromNamespaceAndPath("minecraft", "item.goat_horn.sound." + i));
            WeighedSoundEvents variant = registry.get(variantId);
            if (variant == null) {
                continue;
            }
            Sound sound = variant.getSound(random);
            if (sound != null) {
                combined.addSound(sound);
                added = true;
            }
        }

        return added ? combined : null;
    }
}
