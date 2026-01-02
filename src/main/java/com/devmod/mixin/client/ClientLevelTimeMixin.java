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
 * Mixin to override getDayTime() on ClientLevel for arena dimensions with frozen time.
 * This ensures the client renders the correct time of day (day/night) for mob testing.
 */
@Mixin(ClientLevel.class)
public class ClientLevelTimeMixin {

    /**
     * Overrides getDayTime() to return frozen time for arena dimensions.
     * This affects sky rendering, lighting calculations, and mob AI on client.
     */
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    void devmod$overrideDayTime(CallbackInfoReturnable<Long> cir) {
        ResourceKey<Level> dimension = ((ClientLevel) (Object) this).dimension();

        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            cir.setReturnValue(frozenTime.get());
        }
    }

    /**
     * Also override getTimeOfDay for consistency (used by some rendering).
     */
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    void devmod$overrideTimeOfDay(float partialTick, CallbackInfoReturnable<Float> cir) {
        ResourceKey<Level> dimension = ((ClientLevel) (Object) this).dimension();

        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            // Calculate time of day from frozen time (same formula as vanilla)
            float timeOfDay = (frozenTime.get() % 24000L + partialTick) / 24000.0F - 0.25F;
            if (timeOfDay < 0.0F) {
                timeOfDay += 1.0F;
            }
            if (timeOfDay > 1.0F) {
                timeOfDay -= 1.0F;
            }
            cir.setReturnValue(timeOfDay);
        }
    }
}
