package com.devmod.arena.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class TemplateTypeTest {

    // ---- TypeDiscriminator ----

    @Test
    void discriminatorValues() {
        assertEquals("flat", TemplateType.TypeDiscriminator.FLAT.value());
        assertEquals("structure", TemplateType.TypeDiscriminator.STRUCTURE.value());
        assertEquals("schematic", TemplateType.TypeDiscriminator.SCHEMATIC.value());
        assertEquals("composite", TemplateType.TypeDiscriminator.COMPOSITE.value());
    }

    @Test
    void discriminatorFromValue() {
        assertEquals(TemplateType.TypeDiscriminator.FLAT, TemplateType.TypeDiscriminator.fromValue("flat"));
        assertEquals(TemplateType.TypeDiscriminator.STRUCTURE, TemplateType.TypeDiscriminator.fromValue("structure"));
    }

    @Test
    void discriminatorFromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TemplateType.TypeDiscriminator.fromValue("unknown"));
    }

    // ---- FlatTemplate ----

    @Test
    void flatTemplateDefaults() {
        var flat = TemplateType.FlatTemplate.defaults();
        assertEquals(64, flat.sizeX());
        assertEquals(64, flat.sizeZ());
        assertEquals(64, flat.floorY());
        assertEquals(10, flat.wallHeight());
        assertFalse(flat.requiresExternalResources());
        assertEquals(TemplateType.TypeDiscriminator.FLAT, flat.discriminator());
    }

    @Test
    void flatTemplateBlockCount() {
        var flat = new TemplateType.FlatTemplate(10, 10, 0, 5, "stone", "barrier", "stone", false);
        // floor=100, walls=2*(10+10)*5=200, ceiling=100, underfloor=0
        assertEquals(400, flat.estimatedBlockCount());
    }

    @Test
    void flatTemplateBlockCountWithUnderfloor() {
        var flat = new TemplateType.FlatTemplate(10, 10, 0, 5, "stone", "barrier", null, true);
        // floor=100, walls=200, ceiling=0, underfloor=10*10*3=300
        assertEquals(600, flat.estimatedBlockCount());
    }

    @Test
    void flatTemplateValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.FlatTemplate(0, 10, 0, 5, "s", "w", null, false));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.FlatTemplate(10, -1, 0, 5, "s", "w", null, false));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.FlatTemplate(10, 10, 0, -1, "s", "w", null, false));
    }

    // ---- StructureTemplate ----

    @Test
    void structureTemplateOf() {
        var structure = TemplateType.StructureTemplate.of("devmod:arena/test");
        assertEquals("devmod:arena/test", structure.structurePath());
        assertEquals(TemplateType.StructureTemplate.Rotation.NONE, structure.rotation());
        assertEquals(TemplateType.StructureTemplate.Mirror.NONE, structure.mirror());
        assertTrue(structure.requiresExternalResources());
        assertEquals(TemplateType.TypeDiscriminator.STRUCTURE, structure.discriminator());
    }

    @Test
    void structureTemplateValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.StructureTemplate(null, 0, 0, 0,
                TemplateType.StructureTemplate.Rotation.NONE,
                TemplateType.StructureTemplate.Mirror.NONE, false, null));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.StructureTemplate("", 0, 0, 0,
                TemplateType.StructureTemplate.Rotation.NONE,
                TemplateType.StructureTemplate.Mirror.NONE, false, null));
    }

    // ---- SchematicTemplate ----

    @Test
    void schematicTemplateOf() {
        var schem = TemplateType.SchematicTemplate.of("arenas/colosseum.schem");
        assertEquals("arenas/colosseum.schem", schem.schematicPath());
        assertEquals(TemplateType.SchematicTemplate.SchematicFormat.SPONGE_V2, schem.format());
        assertTrue(schem.requiresExternalResources());
        assertEquals(TemplateType.TypeDiscriminator.SCHEMATIC, schem.discriminator());
    }

    @Test
    void schematicFormatFromPath() {
        assertEquals(TemplateType.SchematicTemplate.SchematicFormat.LEGACY,
            TemplateType.SchematicTemplate.SchematicFormat.fromPath("arena.schematic"));
        assertEquals(TemplateType.SchematicTemplate.SchematicFormat.SPONGE_V2,
            TemplateType.SchematicTemplate.SchematicFormat.fromPath("arena.schem"));
    }

    @Test
    void schematicTemplateValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.SchematicTemplate(null, TemplateType.SchematicTemplate.SchematicFormat.SPONGE_V2, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.SchematicTemplate("", TemplateType.SchematicTemplate.SchematicFormat.SPONGE_V2, 0, 0, 0, true));
    }

    // ---- CompositeTemplate ----

    @Test
    void compositeTemplateWithOverlay() {
        var base = TemplateType.FlatTemplate.defaults();
        var overlay = TemplateType.StructureTemplate.of("devmod:overlay");
        var composite = TemplateType.CompositeTemplate.withOverlay(base, overlay);

        assertEquals(TemplateType.TypeDiscriminator.COMPOSITE, composite.discriminator());
        assertEquals(2, composite.layers().size());
        assertTrue(composite.requiresExternalResources());
        assertEquals(base.estimatedBlockCount() + overlay.estimatedBlockCount(), composite.estimatedBlockCount());
    }

    @Test
    void compositeTemplateRequiresLayers() {
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.CompositeTemplate(null, TemplateType.CompositeTemplate.MergeStrategy.OVERWRITE));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.CompositeTemplate(List.of(), TemplateType.CompositeTemplate.MergeStrategy.OVERWRITE));
    }

    @Test
    void compositeLayerValidation() {
        var flat = TemplateType.FlatTemplate.defaults();
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.CompositeTemplate.Layer(null, flat, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.CompositeTemplate.Layer("", flat, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
            new TemplateType.CompositeTemplate.Layer("name", null, 0, null));
    }

    @Test
    void compositeNoExternalResourcesWithFlatOnly() {
        var flat = TemplateType.FlatTemplate.defaults();
        var layer = TemplateType.CompositeTemplate.Layer.of("base", flat);
        var composite = new TemplateType.CompositeTemplate(
            List.of(layer), TemplateType.CompositeTemplate.MergeStrategy.OVERWRITE);
        assertFalse(composite.requiresExternalResources());
    }
}
