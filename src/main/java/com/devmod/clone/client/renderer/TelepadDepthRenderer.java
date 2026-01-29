package com.devmod.clone.client.renderer;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tracks active telepads for client-side occlusion checks.
 */
@OnlyIn(Dist.CLIENT)
public final class TelepadDepthRenderer {
    private static final Set<BlockPos> activeTelepadPositions = new HashSet<>();

    private TelepadDepthRenderer() {
    }

    /**
     * Register a telepad position for occlusion checks.
     */
    public static void registerTelepad(BlockPos pos) {
        activeTelepadPositions.add(pos.immutable());
    }

    /**
     * Unregister a telepad position.
     */
    public static void unregisterTelepad(BlockPos pos) {
        activeTelepadPositions.remove(pos);
    }

    /**
     * Clear all registered telepads (called on world unload).
     */
    public static void clearAll() {
        activeTelepadPositions.clear();
    }

    /**
     * Get a snapshot of active telepad positions.
     */
    public static Set<BlockPos> getActiveTelepadPositions() {
        return Set.copyOf(activeTelepadPositions);
    }
}
