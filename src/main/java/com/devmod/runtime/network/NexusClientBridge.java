package com.devmod.runtime.network;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge interface decoupling Nexus network handler code from client-only classes.
 * <p>
 * On a dedicated server every method is a no-op (the default implementation).
 * On the client the real implementation is registered via {@link #setInstance}.
 * <p>
 * Thread-safe: the singleton is stored in an {@link AtomicReference}.
 */
public interface NexusClientBridge {

    final class Holder {
        private static final AtomicReference<NexusClientBridge> INSTANCE =
                new AtomicReference<>(new NoOp());
        private Holder() {}
    }

    Logger LOGGER = LoggerFactory.getLogger(NexusClientBridge.class);

    static NexusClientBridge get() {
        return Holder.INSTANCE.get();
    }

    static void setInstance(NexusClientBridge impl) {
        Holder.INSTANCE.set(impl);
    }

    /** Open a dialog screen with the given payload. */
    default void openDialog(NexusDialogPayload payload) {}

    /** Open a Nexus UI screen based on the payload action. */
    default void openUi(NexusUiPayload payload) {}

    /** Apply a log snapshot to the log viewer screen. */
    default void applyLogSnapshot(NexusLogSnapshotPayload payload) {}

    /** No-op implementation for dedicated server. */
    final class NoOp implements NexusClientBridge {}
}
