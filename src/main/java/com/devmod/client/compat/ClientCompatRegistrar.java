package com.devmod.client.compat;

import com.devmod.client.compat.mods.controlling.ControllingCompat;
import com.devmod.client.compat.mods.fancymenu.FancyMenuCompat;
import com.devmod.client.compat.mods.yacl.YaclCompat;
import com.devmod.compat.CompatRegistry;

/**
 * Registers client-only compat modules.
 * Invoked via DistExecutor to avoid server-side classloading.
 */
public final class ClientCompatRegistrar {

    private ClientCompatRegistrar() {}

    public static void registerClientModules() {
        CompatRegistry.register(new ControllingCompat());
        CompatRegistry.register(new YaclCompat());
        CompatRegistry.register(new FancyMenuCompat());
    }
}
