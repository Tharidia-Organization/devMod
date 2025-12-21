package com.devmod.arena.registry;

import com.devmod.arena.telemetry.ArenaTelemetry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceResolutionTest {

    @Test
    void depthExceededFailsHotReload() {
        ArenaTemplate base = ArenaTemplate.defaultTemplate();

        ArenaTemplate t4 = copyWith(base, "t4", null, List.of("t4"));
        ArenaTemplate t3 = copyWith(base, "t3", "t4", List.of("t3"));
        ArenaTemplate t2 = copyWith(base, "t2", "t3", List.of("t2"));
        ArenaTemplate t1 = copyWith(base, "t1", "t2", List.of("t1"));
        ArenaTemplate t0 = copyWith(base, "t0", "t1", List.of("t0"));

        try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
            ArenaTemplateRegistry.ReloadResult result = registry.hotReload(List.of(t0, t1, t2, t3, t4));

            assertFalse(result.success(), "Reload should fail when inheritance depth exceeds limit");
            assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("depth")),
                "Expected depth exceeded error");
        }
    }

    @Test
    void diamondInheritanceDetected() {
        ArenaTemplate base = ArenaTemplate.defaultTemplate();

        ArenaTemplate a = copyWith(base, "a", "b", List.of("a"));
        ArenaTemplate b = copyWith(base, "b", "c", List.of("b"));
        ArenaTemplate c = copyWith(base, "c", "b", List.of("c"));

        try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
            ArenaTemplateRegistry.ReloadResult result = registry.hotReload(List.of(a, b, c));

            assertFalse(result.success(), "Reload should fail for diamond inheritance");
            assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("diamond")),
                "Expected diamond inheritance error");
        }
    }

    @Test
    void childTagsOverrideParentTags() {
        ArenaTemplate base = ArenaTemplate.defaultTemplate();

        ArenaTemplate parent = copyWith(base, "parent", null, List.of("parent"));
        ArenaTemplate child = copyWith(base, "child", "parent", List.of("child"));

        try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
            registry.load(parent);
            registry.load(child);

            ArenaTemplate resolved = registry.get("child").orElseThrow();
            assertEquals(List.of("child"), resolved.tags(), "Child tags should override parent tags");
            assertEquals(Set.of("child"), Set.copyOf(resolved.tags()));
        }
    }

    private ArenaTemplate copyWith(ArenaTemplate base, String id, String extendsId, List<String> tags) {
        return new ArenaTemplate(
            id,
            extendsId,
            base.version(),
            base.schemaVersion(),
            base.breakingChange(),
            base.deprecated(),
            base.replacementVersion(),
            base.minParentVersion(),
            base.templateType(),
            base.origin(),
            base.size(),
            base.sizeX(),
            base.sizeZ(),
            base.floor(),
            base.walls(),
            base.ceiling(),
            base.underfloor(),
            base.palette(),
            base.biome(),
            base.lighting(),
            base.spawnSlots(),
            base.playerSpawnOffset(),
            base.mobSpawnStrategy(),
            base.forbiddenZones(),
            base.hazards(),
            base.environment(),
            base.compat(),
            base.instanceSettings(),
            base.structureNbt(),
            base.limits(),
            base.buildSettings(),
            tags
        );
    }
}
