package com.devmod.arena.alert;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global registry for the alert router.
 */
public final class AlertRouterRegistry {

    private static final AtomicReference<AlertRouter> ROUTER = new AtomicReference<>();

    private AlertRouterRegistry() {}

    public static void set(AlertRouter router) {
        ROUTER.set(router);
    }

    public static AlertRouter get() {
        return ROUTER.get();
    }
}
