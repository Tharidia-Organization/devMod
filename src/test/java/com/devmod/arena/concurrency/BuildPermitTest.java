package com.devmod.arena.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BuildPermitTest {

    @Test
    void grantedPermit() {
        BuildPermit.Granted granted = BuildPermit.Granted.now("p1", 100);
        assertTrue(granted.isGranted());
        assertEquals("p1", granted.permitId());
        assertEquals(100, granted.waitTimeMs());
        assertNotNull(granted.grantedAt());
    }

    @Test
    void rejectedQueueFull() {
        BuildPermit.Rejected rejected = BuildPermit.Rejected.queueFull(Duration.ofSeconds(10));
        assertFalse(rejected.isGranted());
        assertEquals(BuildPermit.RejectionReason.QUEUE_FULL, rejected.reason());
        assertEquals(Duration.ofSeconds(10), rejected.retryAfter());
        assertEquals(-1, rejected.queuePosition());
    }

    @Test
    void rejectedTimeout() {
        BuildPermit.Rejected rejected = BuildPermit.Rejected.timeout(5);
        assertFalse(rejected.isGranted());
        assertEquals(BuildPermit.RejectionReason.TIMEOUT, rejected.reason());
        assertEquals(5, rejected.queuePosition());
    }

    @Test
    void rejectedRateLimited() {
        BuildPermit.Rejected rejected = BuildPermit.Rejected.rateLimited(Duration.ofMinutes(1), 3);
        assertFalse(rejected.isGranted());
        assertEquals(BuildPermit.RejectionReason.RATE_LIMITED, rejected.reason());
        assertEquals(Duration.ofMinutes(1), rejected.retryAfter());
        assertEquals(3, rejected.queuePosition());
    }

    @Test
    void rejectionReasonValues() {
        assertEquals(3, BuildPermit.RejectionReason.values().length);
        assertNotNull(BuildPermit.RejectionReason.valueOf("QUEUE_FULL"));
        assertNotNull(BuildPermit.RejectionReason.valueOf("TIMEOUT"));
        assertNotNull(BuildPermit.RejectionReason.valueOf("RATE_LIMITED"));
    }

    @Test
    void sealedTypeHierarchy() {
        BuildPermit granted = BuildPermit.Granted.now("p1", 0);
        BuildPermit rejected = BuildPermit.Rejected.queueFull(Duration.ZERO);
        assertInstanceOf(BuildPermit.Granted.class, granted);
        assertInstanceOf(BuildPermit.Rejected.class, rejected);
    }
}
