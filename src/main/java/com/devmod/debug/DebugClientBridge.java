package com.devmod.debug;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.debug.network.EntityScanDataPayload;

/**
 * Bridge interface decoupling debug network handler code from client-only classes.
 * <p>
 * On a dedicated server every method is a no-op (the default implementation).
 * On the client the real implementation is registered via {@link #setInstance}.
 * <p>
 * Thread-safe: the singleton is stored in an {@link AtomicReference}.
 */
public interface DebugClientBridge {

    final class Holder {
        private static final AtomicReference<DebugClientBridge> INSTANCE =
                new AtomicReference<>(new NoOp());
        private Holder() {}
    }

    Logger LOGGER = LoggerFactory.getLogger(DebugClientBridge.class);

    static DebugClientBridge get() {
        return Holder.INSTANCE.get();
    }

    static void setInstance(DebugClientBridge impl) {
        Holder.INSTANCE.set(impl);
    }

    /** Handle debug sync payload (feature toggle state). */
    default void handleDebugSync(DebugSyncPayload payload) {}

    /** Handle entity pathing data for debug visualization. */
    default void handleEntityPathing(EntityPathingPayload payload) {}

    /** Handle structure bounding boxes for debug visualization. */
    default void handleStructures(StructuresPayload payload) {}

    /** Handle POI records for debug visualization. */
    default void handlePOI(POIPayload payload) {}

    /** Handle active raids for debug visualization. */
    default void handleRaids(RaidsPayload payload) {}

    /** Handle mob → target links for debug visualization. */
    default void handleBrains(BrainsPayload payload) {}

    /** Handle bee hive/flower memories for debug visualization. */
    default void handleBees(BeesPayload payload) {}

    /** Handle incoming scan data for the entity scanner screen. */
    default void handleScanData(EntityScanDataPayload payload) {}

    /** Handle request to open entity scanner screen. */
    default void handleOpenScreen(EntityScanDataPayload.OpenScreenPayload payload) {}

    /** No-op implementation for dedicated server. */
    final class NoOp implements DebugClientBridge {}
}
