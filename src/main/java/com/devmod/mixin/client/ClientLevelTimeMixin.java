package com.devmod.mixin.client;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.devmod.client.environment.ClientEnvironmentCache;

/**
 * Mixin to override getDayTime() on Level for arena dimensions with frozen time.
 * This ensures the client renders the correct time of day (day/night) for mob testing.
 */
@Mixin(Level.class)
public class ClientLevelTimeMixin {

    /**
     * Overrides getDayTime() to return frozen time for arena dimensions.
     * This affects sky rendering, lighting calculations, and mob AI on client.
     */
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    void devmod$overrideDayTime(CallbackInfoReturnable<Long> cir) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ClientLevel)) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();

        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            cir.setReturnValue(frozenTime.get());
        }
    }

    // getTimeOfDay is now a default method on LevelTimeAccess, so time-of-day
    // overrides are handled by LevelAccessorTimeMixin.
}
