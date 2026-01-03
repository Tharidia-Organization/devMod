package com.devmod.client.debug;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.rendering.PathfindingDebugger;
import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugSyncPayload;
import com.devmod.debug.EntityPathingPayload;
import com.devmod.debug.client.DebugRenderBools;

@OnlyIn(Dist.CLIENT)
public final class DebugNetworkClientHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugNetworkClientHandler.class);

    private DebugNetworkClientHandler() {}

    public static void handleDebugSync(DebugSyncPayload payload) {
        DebugFeature feature = payload.getFeature();
        if (feature == null) {
            LOGGER.warn("Unknown debug feature in sync: {}", payload.featureId());
            return;
        }

        // Update the client-side render booleans
        switch (feature) {
            case ENTITY_PATHING -> DebugRenderBools.setEntityPathing(payload.enabled());
            case ENTITY_GOALS -> DebugRenderBools.setEntityGoals(payload.enabled());
            case ENTITY_BRAINS -> DebugRenderBools.setEntityBrains(payload.enabled());
            case POI -> DebugRenderBools.setPoi(payload.enabled());
            case BLOCK_UPDATES -> DebugRenderBools.setBlockUpdates(payload.enabled());
            case STRUCTURE_GENERATIONS -> DebugRenderBools.setStructures(payload.enabled());
            case RAIDS -> DebugRenderBools.setRaids(payload.enabled());
            case GAME_EVENTS -> DebugRenderBools.setGameEvents(payload.enabled());
            case BEE_HIVES -> DebugRenderBools.setBeeHives(payload.enabled());
            case BEES -> DebugRenderBools.setBees(payload.enabled());
            case WATER -> DebugRenderBools.setWater(payload.enabled());
            case HEIGHTMAP -> DebugRenderBools.setHeightmap(payload.enabled());
            case COLLISION -> DebugRenderBools.setCollision(payload.enabled());
            case LIGHT -> DebugRenderBools.setLight(payload.enabled());
            case SOLID_FACES -> DebugRenderBools.setSolidFaces(payload.enabled());
            case CHUNK -> DebugRenderBools.setChunk(payload.enabled());
            case SPAWN_CHUNKS -> {} // No specific renderer for this
        }

        LOGGER.debug("[Debug] Client synced {} = {}", feature.getDisplayName(), payload.enabled());
    }

    /**
     * Handle entity pathing data from the server.
     * Updates PathfindingDebugger with server-authoritative path information.
     */
    public static void handleEntityPathing(@Nonnull EntityPathingPayload payload) {
        PathfindingDebugger.INSTANCE.receiveServerPath(payload);
        LOGGER.debug("[Debug] Received path for entity {} with {} nodes",
            payload.entityName(), payload.nodes().size());
    }
}
