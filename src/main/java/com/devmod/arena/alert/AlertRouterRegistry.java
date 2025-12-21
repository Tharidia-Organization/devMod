package com.devmod.arena.alert;

/**
 * Global registry for the alert router.
 */
public final class AlertRouterRegistry {

    private static volatile AlertRouter router;

    private AlertRouterRegistry() {}

    public static void set(AlertRouter router) {
        AlertRouterRegistry.router = router;
    }

    public static AlertRouter get() {
        return router;
    }
}
