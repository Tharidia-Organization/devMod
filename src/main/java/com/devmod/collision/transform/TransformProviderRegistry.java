package com.devmod.collision.transform;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.neoforged.fml.loading.FMLEnvironment;

public final class TransformProviderRegistry {

    private TransformProviderRegistry() {} // Utility class

    // Lazy-initialized client provider (only loaded on client)
    @Nullable
    private static volatile TransformProvider clientProvider;
    private static volatile boolean clientProviderChecked = false;

    /**
     * Gets the transform provider for the current side.
     *
     * @return TransformProvider appropriate for client or server
     */
    @Nonnull
    public static TransformProvider getProvider() {
        return FMLEnvironment.dist.isClient() ? getClientProvider() : getServerProvider();
    }

    /**
     * Gets the server-side transform provider.
     * Can be called from either side.
     *
     * @return Server transform provider
     */
    @Nonnull
    public static TransformProvider getServerProvider() {
        return ServerTransformProvider.getInstance();
    }

    /**
     * Gets the client-side transform provider.
     * Only call from client side - will return server provider on server.
     *
     * @return Client transform provider if on client, server provider otherwise
     */
    @Nonnull
    public static TransformProvider getClientProvider() {
        if (!FMLEnvironment.dist.isClient()) {
            return getServerProvider();
        }

        if (!clientProviderChecked) {
            synchronized (TransformProviderRegistry.class) {
                if (!clientProviderChecked) {
                    try {
                        // Load the client provider via reflection to avoid classloading issues
                        clientProvider = loadClientProvider();
                    } catch (Exception e) {
                        // Fallback to server provider if client provider fails to load
                        clientProvider = getServerProvider();
                    }
                    clientProviderChecked = true;
                }
            }
        }
        TransformProvider provider = clientProvider;
        if (provider == null) {
            provider = getServerProvider();
        }
        return Objects.requireNonNull(provider, "ClientTransformProvider");
    }

    /**
     * Loads the client provider using the client delegate.
     */
    @Nonnull
    private static TransformProvider loadClientProvider() throws ReflectiveOperationException {
        Class<?> providerClass = Class.forName("com.devmod.client.collision.transform.ClientTransformProvider");
        Object instance = providerClass.getField("INSTANCE").get(null);
        if (!(instance instanceof TransformProvider provider)) {
            throw new IllegalStateException("ClientTransformProvider.INSTANCE is not a TransformProvider");
        }
        return provider;
    }

    /**
     * Clears all transform caches on both client and server providers.
     * Call during world unload or entity cleanup.
     */
    public static void clearAllCaches() {
        getServerProvider().clearAllCaches();
        if (clientProviderChecked && clientProvider != null) {
            clientProvider.clearAllCaches();
        }
    }

    /**
     * Clears cached transforms for a specific entity on both providers.
     *
     * @param entityId The entity ID
     */
    public static void clearCache(int entityId) {
        getServerProvider().clearCache(entityId);
        if (clientProviderChecked && clientProvider != null) {
            clientProvider.clearCache(entityId);
        }
    }

}
