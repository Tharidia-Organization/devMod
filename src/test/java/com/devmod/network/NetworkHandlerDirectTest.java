package com.devmod.network;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkHandlerDirectTest {

    @AfterEach
    void clearHooks() throws Exception {
        Field field = NetworkHandler.class.getDeclaredField("clientPayloadHooks");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    @DisplayName("withClientHooks is safe when no hooks are registered")
    void withClientHooksIsNoOpWhenUnset() {
        assertDoesNotThrow(() -> NetworkHandler.withClientHooks(hooks -> {}));
    }

    @Test
    @DisplayName("withClientHooks dispatches when hooks are registered")
    void withClientHooksDispatchesWhenSet() {
        AtomicBoolean called = new AtomicBoolean(false);

        NetworkHandler.ClientPayloadHooks hooks = (NetworkHandler.ClientPayloadHooks) Proxy.newProxyInstance(
            NetworkHandler.ClientPayloadHooks.class.getClassLoader(),
            new Class<?>[] { NetworkHandler.ClientPayloadHooks.class },
            (proxy, method, args) -> null
        );

        NetworkHandler.setClientPayloadHooks(hooks);
        NetworkHandler.withClientHooks(h -> called.set(true));

        assertTrue(called.get());
    }
}
