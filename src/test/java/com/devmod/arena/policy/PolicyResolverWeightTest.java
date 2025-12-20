package com.devmod.arena.policy;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.override.OverrideManager;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for routing weight handling (clamp + scoring).
 */
class PolicyResolverWeightTest {

    private ArenaTelemetry telemetry;
    private List<ArenaTelemetry.TelemetryEvent> events;
    private ArenaTemplateRegistry registry;
    private OverrideManager overrideManager;
    private PolicyResolver resolver;

    @BeforeEach
    void setUp() {
        events = new CopyOnWriteArrayList<>();
        telemetry = new ArenaTelemetry(events::add);
        registry = new ArenaTemplateRegistry(telemetry);
        registry.load(ArenaTemplate.defaultTemplate());
        overrideManager = new OverrideManager(telemetry);
        resolver = new PolicyResolver(registry, telemetry, overrideManager, ArenaTemplateConfig.load());
    }

    @AfterEach
    void tearDown() {
        resolver.close();
    }

    @Test
    void prefersHigherWeightWhenBaseScoreTies() {
        ArenaPolicy lowWeight = ArenaPolicy.builder("low-weight")
            .templateId("default_flat_64")
            .mobTypes(Set.of("zombie"))
            .weight(1.0)
            .build();

        ArenaPolicy highWeight = ArenaPolicy.builder("high-weight")
            .templateId("default_flat_64")
            .mobTypes(Set.of("zombie"))
            .weight(5.0)
            .build();

        resolver.registerPolicy(lowWeight);
        resolver.registerPolicy(highWeight);

        ResolveContext context = ResolveContext.builder(UUID.randomUUID())
            .mobType("zombie")
            .playerCount(1)
            .build();

        ResolvedArena resolved = resolver.resolve(context);

        assertEquals("high-weight", resolved.policy().id(), "Resolver should pick policy with higher weight when other scores tie");
        assertEquals(5.0, resolved.scoreBreakdown().get("weightScore"));
    }

    @Test
    void clampsWeightAndEmitsTelemetry() {
        ArenaPolicy overweight = ArenaPolicy.builder("overweight")
            .templateId("default_flat_64")
            .mobTypes(Set.of("skeleton"))
            .weight(99.0)
            .build();

        resolver.registerPolicy(overweight);

        // Policy is clamped in registry map
        ArenaPolicy stored = resolver.getPolicy("overweight").orElseThrow();
        assertEquals(10.0, stored.weight());

        boolean clampedEventFound = events.stream()
            .anyMatch(e -> e.name().equals("arena.routing.weight_clamped"));
        assertTrue(clampedEventFound, "Weight clamp should emit telemetry");
    }
}
