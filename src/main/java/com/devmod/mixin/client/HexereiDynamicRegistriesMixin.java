package com.devmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Pseudo
@Mixin(targets = "net.joefoxe.hexerei.Hexerei$DynamicRegistries", remap = false)
public class HexereiDynamicRegistriesMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void devmod$hexereiClientRegistries(CallbackInfoReturnable<RegistryAccess> cir) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            cir.setReturnValue(mc.level.registryAccess());
            return;
        }
        cir.setReturnValue(null);
    }
}
