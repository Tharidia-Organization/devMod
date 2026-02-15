package com.devmod.runtime.network;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NexusClientBridge}.
 */
class NexusClientBridgeTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void noOpInstanceReturnedByDefault() {
        NexusClientBridge bridge = NexusClientBridge.get();
        assertNotNull(bridge, "Default bridge instance must not be null");
        assertInstanceOf(NexusClientBridge.NoOp.class, bridge);
    }

    @Test
    void noOpOpenDialogDoesNotThrow() {
        NexusClientBridge bridge = new NexusClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.openDialog(null));
    }

    @Test
    void noOpOpenUiDoesNotThrow() {
        NexusClientBridge bridge = new NexusClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.openUi(null));
    }

    @Test
    void noOpApplyLogSnapshotDoesNotThrow() {
        NexusClientBridge bridge = new NexusClientBridge.NoOp();
        assertDoesNotThrow(() -> bridge.applyLogSnapshot(null));
    }

    @Test
    void setInstanceReplacesDefault() {
        NexusClientBridge original = NexusClientBridge.get();
        try {
            NexusClientBridge custom = new NexusClientBridge() {};
            NexusClientBridge.setInstance(custom);
            assertSame(custom, NexusClientBridge.get());
        } finally {
            NexusClientBridge.setInstance(original);
        }
    }

    @Test
    void interfaceDeclaresAllExpectedMethods() throws NoSuchMethodException {
        assertNotNull(NexusClientBridge.class.getMethod("openDialog", NexusDialogPayload.class));
        assertNotNull(NexusClientBridge.class.getMethod("openUi", NexusUiPayload.class));
        assertNotNull(NexusClientBridge.class.getMethod("applyLogSnapshot", NexusLogSnapshotPayload.class));
    }
}
