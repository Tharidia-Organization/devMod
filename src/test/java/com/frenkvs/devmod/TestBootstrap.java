package com.frenkvs.devmod;

/**
 * Ensures Minecraft registries are bootstrapped for JVM tests.
 */
public final class TestBootstrap {
    private static boolean initialized = false;

    private TestBootstrap() {}

    public static synchronized void init() {
        if (initialized) return;
        System.setProperty("devmod.allowFallbackComponents", "true");
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        initialized = true;
    }
}
