package com.devmod.mixin;

import java.util.Collection;
import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.errorprone.annotations.Keep;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.Commands;

@Mixin(Commands.class)
public class CommandsMixin {

    @Inject(method = "lambda$validate$10", at = @At("HEAD"), cancellable = true, remap = false)
    @Keep
    private static <S> void devmod$filterTeleportAmbiguity(
            CommandDispatcher<S> dispatcher,
            CommandNode<S> first,
            CommandNode<S> second,
            CommandNode<S> third,
            Collection<String> inputs,
            CallbackInfo ci) {
        if (dispatcher == null || first == null || second == null || third == null || inputs == null
                || inputs.isEmpty()) {
            return;
        }

        if (isTeleportPath(dispatcher.getPath(second)) && isTeleportPath(dispatcher.getPath(third))) {
            ci.cancel();
        }
    }

    private static boolean isTeleportPath(Collection<String> path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Iterator<String> iterator = path.iterator();
        return iterator.hasNext() && "teleport".equals(iterator.next());
    }
}
