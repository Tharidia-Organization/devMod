package com.devmod.mixin;

import java.net.URI;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.errorprone.annotations.Keep;

import net.minecraft.server.packs.VanillaPackResourcesBuilder;

@Mixin(VanillaPackResourcesBuilder.class)
public class VanillaPackResourcesBuilderMixin {

    @Redirect(
            method = "lambda$static$1",
            at = @At(
                value = "INVOKE",
                target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V",
                remap = false),
            remap = false)
    @Keep
    private static void devmod$ignoreUnionSchema(Logger logger, String message, Object argument) {
        if (argument instanceof URI uri && "union".equals(uri.getScheme())) {
            return;
        }
        logger.warn(message, argument);
    }
}
