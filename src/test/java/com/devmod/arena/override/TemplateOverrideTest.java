package com.devmod.arena.override;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TemplateOverrideTest {

    @Test
    void createWithoutExpiration() {
        TemplateOverride override = TemplateOverride.create("template1", OverrideScope.PLAYER, "command");
        assertEquals("template1", override.templateId());
        assertNull(override.policyId());
        assertEquals(OverrideScope.PLAYER, override.scope());
        assertNotNull(override.createdAt());
        assertNull(override.expiresAt());
        assertEquals("command", override.source());
        assertFalse(override.isExpired());
    }

    @Test
    void createWithPolicy() {
        TemplateOverride override = TemplateOverride.create("t1", "p1", OverrideScope.PARTY, "wizard");
        assertEquals("t1", override.templateId());
        assertEquals("p1", override.policyId());
        assertEquals(OverrideScope.PARTY, override.scope());
    }

    @Test
    void createWithTTLNotExpired() {
        Instant future = Instant.now().plusSeconds(3600);
        TemplateOverride override = TemplateOverride.createWithTTL("t1", OverrideScope.QUEST, "api", future);
        assertFalse(override.isExpired());
        assertEquals(future, override.expiresAt());
    }

    @Test
    void createWithTTLExpired() {
        Instant past = Instant.now().minusSeconds(3600);
        TemplateOverride override = TemplateOverride.createWithTTL("t1", OverrideScope.QUEST, "api", past);
        assertTrue(override.isExpired());
    }

    @Test
    void builderDefaults() {
        TemplateOverride override = TemplateOverride.builder("t1").build();
        assertEquals("t1", override.templateId());
        assertEquals(OverrideScope.PLAYER, override.scope());
        assertEquals("api", override.source());
        assertNull(override.policyId());
        assertNull(override.expiresAt());
    }

    @Test
    void builderFullyConfigured() {
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        Instant expires = Instant.parse("2025-12-31T00:00:00Z");
        TemplateOverride override = TemplateOverride.builder("t1")
            .policyId("policy1")
            .scope(OverrideScope.PARTY)
            .createdAt(created)
            .expiresAt(expires)
            .source("test")
            .build();

        assertEquals("t1", override.templateId());
        assertEquals("policy1", override.policyId());
        assertEquals(OverrideScope.PARTY, override.scope());
        assertEquals(created, override.createdAt());
        assertEquals(expires, override.expiresAt());
        assertEquals("test", override.source());
    }

    @Test
    void overrideScopeValues() {
        assertEquals(3, OverrideScope.values().length);
        assertNotNull(OverrideScope.valueOf("PLAYER"));
        assertNotNull(OverrideScope.valueOf("PARTY"));
        assertNotNull(OverrideScope.valueOf("QUEST"));
    }
}
