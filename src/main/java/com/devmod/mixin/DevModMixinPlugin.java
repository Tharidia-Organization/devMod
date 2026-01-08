package com.devmod.mixin;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.devmod.compat.Compat;

public class DevModMixinPlugin implements IMixinConfigPlugin {

    private static final String HEXEREI_MIXIN = "com.devmod.mixin.client.HexereiDynamicRegistriesMixin";

    @Override
    public void onLoad(String mixinPackage) {
        // No-op
    }

    @Override
    @Nullable
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (HEXEREI_MIXIN.equals(mixinClassName)) {
            return Compat.isLoaded("hexerei");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No-op
    }

    @Override
    @Nullable
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }
}
