package com.devmod.client.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.debug.DebugFeature;
import com.devmod.debug.DebugSyncPayload;
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
            case ENTITY_PATHING -> DebugRenderBools.ENTITY_PATHING = payload.enabled();
            case ENTITY_GOALS -> DebugRenderBools.ENTITY_GOALS = payload.enabled();
            case ENTITY_BRAINS -> DebugRenderBools.ENTITY_BRAINS = payload.enabled();
            case POI -> DebugRenderBools.POI = payload.enabled();
            case BLOCK_UPDATES -> DebugRenderBools.BLOCK_UPDATES = payload.enabled();
            case STRUCTURE_GENERATIONS -> DebugRenderBools.STRUCTURES = payload.enabled();
            case RAIDS -> DebugRenderBools.RAIDS = payload.enabled();
            case GAME_EVENTS -> DebugRenderBools.GAME_EVENTS = payload.enabled();
            case BEE_HIVES -> DebugRenderBools.BEE_HIVES = payload.enabled();
            case BEES -> DebugRenderBools.BEES = payload.enabled();
            case WATER -> DebugRenderBools.WATER = payload.enabled();
            case HEIGHTMAP -> DebugRenderBools.HEIGHTMAP = payload.enabled();
            case COLLISION -> DebugRenderBools.COLLISION = payload.enabled();
            case LIGHT -> DebugRenderBools.LIGHT = payload.enabled();
            case SOLID_FACES -> DebugRenderBools.SOLID_FACES = payload.enabled();
            case CHUNK -> DebugRenderBools.CHUNK = payload.enabled();
            case SPAWN_CHUNKS -> {} // No specific renderer for this
        }

        LOGGER.debug("[Debug] Client synced {} = {}", feature.getDisplayName(), payload.enabled());
    }
}
