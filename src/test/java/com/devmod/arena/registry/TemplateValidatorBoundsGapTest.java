package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateValidatorBoundsGapTest {

    @Test
    void failsWhenWallsDisabled() {
        ArenaTemplate template = withOverrides(
            baseTemplate(),
            new ArenaTemplate.Walls(false, "minecraft:barrier", 10, 1, 64, "solid"),
            null
        );

        TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);
        ValidationResult result = validator.validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("bounds gap: walls must be enabled")));
    }

    @Test
    void failsWhenCeilingDisabled() {
        ArenaTemplate template = withOverrides(
            baseTemplate(),
            null,
            new ArenaTemplate.Ceiling(false, "minecraft:barrier", 74, 1)
        );

        TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);
        ValidationResult result = validator.validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("bounds gap: ceiling must be enabled")));
    }

    @Test
    void failsWhenWallsDoNotReachCeiling() {
        ArenaTemplate template = withOverrides(
            baseTemplate(),
            new ArenaTemplate.Walls(true, "minecraft:barrier", 4, 1, 64, "solid"),
            new ArenaTemplate.Ceiling(true, "minecraft:barrier", 74, 1)
        );

        TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);
        ValidationResult result = validator.validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("bounds gap: walls.height does not reach ceiling")));
    }

    @Test
    void failsWhenWallsAreFenceStyle() {
        ArenaTemplate template = withOverrides(
            baseTemplate(),
            new ArenaTemplate.Walls(true, "minecraft:barrier", 10, 1, 64, "fence"),
            null
        );

        TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);
        ValidationResult result = validator.validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("bounds gap: walls.style fence")));
    }

    private static ArenaTemplate baseTemplate() {
        return ArenaTemplate.builder("gap_test")
            .version(1)
            .schemaVersion(1)
            .templateType(TemplateType.FlatTemplate.defaults())
            .origin(new ArenaTemplate.Origin(ArenaTemplate.OriginMode.CENTER, 0, 64, 0))
            .size(64)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid",
                "minecraft:polished_andesite", 0))
            .walls(new ArenaTemplate.Walls(true, "minecraft:barrier", 10, 1, 64, "solid"))
            .ceiling(new ArenaTemplate.Ceiling(true, "minecraft:barrier", 74, 1))
            .build();
    }

    private static ArenaTemplate withOverrides(
        ArenaTemplate base,
        ArenaTemplate.Walls walls,
        ArenaTemplate.Ceiling ceiling
    ) {
        return new ArenaTemplate(
            base.id(),
            base.extendsTemplate(),
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
            base.arenaShape(),
            base.ringInnerRadius(),
            base.floor(),
            walls != null ? walls : base.walls(),
            ceiling != null ? ceiling : base.ceiling(),
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
            base.zoneSettings(),
            base.terrainSettings(),
            base.tags()
        );
    }
}
