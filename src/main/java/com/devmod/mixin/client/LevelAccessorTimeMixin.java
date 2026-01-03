package com.devmod.mixin.client;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import com.devmod.client.environment.ClientEnvironmentCache;

/**
 * Override LevelAccessor.dayTime() for client levels with frozen time.
 * This feeds the LevelTimeAccess default methods used by rendering.
 */
@Mixin(LevelAccessor.class)
public interface LevelAccessorTimeMixin {

    @Inject(method = "dayTime", at = @At("HEAD"), cancellable = true)
    default void devmod$overrideDayTime(CallbackInfoReturnable<Long> cir) {
        Object self = this;
        if (!(self instanceof ClientLevel clientLevel)) {
            return;
        }

        ResourceKey<Level> dimension = clientLevel.dimension();
        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            cir.setReturnValue(frozenTime.get());
        }
    }
}
