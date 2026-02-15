package com.devmod.debug;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DebugClientBridge}.
 */
class DebugClientBridgeTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void noOpInstanceReturnedByDefault() {
        DebugClientBridge bridge = DebugClientBridge.get();
        assertNotNull(bridge, "Default bridge instance must not be null");
        assertInstanceOf(DebugClientBridge.NoOp.class, bridge);
    }

    @Test
    void noOpHandleDebugSyncDoesNotThrow() {
        DebugClientBridge bridge = new DebugClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.handleDebugSync(null));
    }

    @Test
    void noOpHandleEntityPathingDoesNotThrow() {
        DebugClientBridge bridge = new DebugClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.handleEntityPathing(null));
    }

    @Test
    void noOpHandleScanDataDoesNotThrow() {
        DebugClientBridge bridge = new DebugClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.handleScanData(null));
    }

    @Test
    void noOpHandleOpenScreenDoesNotThrow() {
        DebugClientBridge bridge = new DebugClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.handleOpenScreen(null));
    }

    @Test
    void setInstanceReplacesDefault() {
        DebugClientBridge original = DebugClientBridge.get();
        try {
            DebugClientBridge custom = new DebugClientBridge() {};
            DebugClientBridge.setInstance(custom);
            assertSame(custom, DebugClientBridge.get());
        } finally {
            DebugClientBridge.setInstance(original);
        }
    }

    @Test
    void interfaceDeclaresAllExpectedMethods() throws NoSuchMethodException {
        assertNotNull(DebugClientBridge.class.getMethod("handleDebugSync", DebugSyncPayload.class));
        assertNotNull(DebugClientBridge.class.getMethod("handleEntityPathing", EntityPathingPayload.class));
        assertNotNull(DebugClientBridge.class.getMethod("handleScanData",
                com.devmod.debug.network.EntityScanDataPayload.class));
        assertNotNull(DebugClientBridge.class.getMethod("handleOpenScreen",
                com.devmod.debug.network.EntityScanDataPayload.OpenScreenPayload.class));
    }
}
