package com.devmod.client.endurance;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side delegate for endurance system operations that require client context.
 * This class isolates Minecraft.getInstance() calls from common packages to maintain
 * proper client/server separation.
 */
@OnlyIn(Dist.CLIENT)
public final class EnduranceClientDelegate {
    private EnduranceClientDelegate() {}

    /**
     * Gets the registry access from the current client level.
     *
     * @return The registry access, or null if not available
     */
    @Nullable
    public static RegistryAccess getClientRegistryAccess() {
        var mc = Minecraft.getInstance();
        return (mc != null && mc.level != null) ? mc.level.registryAccess() : null;
    }
}
