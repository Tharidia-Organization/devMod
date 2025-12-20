package com.devmod.arena.registry;

import javax.annotation.Nullable;

/**
 * DD1: Sealed interface for template type variants.
 *
 * <p>Permits four distinct template types:
 * <ul>
 *   <li>{@link FlatTemplate} - Procedurally generated flat arena</li>
 *   <li>{@link StructureTemplate} - NBT structure-based arena</li>
 *   <li>{@link SchematicTemplate} - External schematic file arena</li>
 *   <li>{@link CompositeTemplate} - Multi-layer composite arena</li>
 * </ul>
 *
 * <p>Each variant implements common behavior while providing
 * type-safe discrimination at compile time.
 */
public sealed interface TemplateType
    permits TemplateType.FlatTemplate,
            TemplateType.StructureTemplate,
            TemplateType.SchematicTemplate,
            TemplateType.CompositeTemplate {

    /**
     * Returns the discriminator for this template type.
     */
    TypeDiscriminator discriminator();

    /**
     * Returns true if this template requires external resources.
     */
    default boolean requiresExternalResources() {
        return false;
    }

    /**
     * Returns the estimated block count for build budgeting.
     */
    int estimatedBlockCount();

    /**
     * Type discriminator enum for serialization and pattern matching.
     */
    enum TypeDiscriminator {
        FLAT("flat"),
        STRUCTURE("structure"),
        SCHEMATIC("schematic"),
        COMPOSITE("composite");

        private final String value;

        TypeDiscriminator(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static TypeDiscriminator fromValue(String value) {
            for (TypeDiscriminator d : values()) {
                if (d.value.equals(value)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("Unknown template type: " + value);
        }
    }

    // ========== DD2: Template Variants ==========

    /**
     * DD2: Flat template - procedurally generated arena with floor, walls, ceiling.
     *
     * <p>Most common template type. Arena is generated from parameters
     * rather than loaded from external files.
     */
    record FlatTemplate(
        int sizeX,
        int sizeZ,
        int floorY,
        int wallHeight,
        String floorMaterial,
        String wallMaterial,
        @Nullable String ceilingMaterial,
        boolean generateUnderfloor
    ) implements TemplateType {

        public FlatTemplate {
            if (sizeX <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("Size must be positive");
            }
            if (wallHeight < 0) {
                throw new IllegalArgumentException("Wall height cannot be negative");
            }
        }

        @Override
        public TypeDiscriminator discriminator() {
            return TypeDiscriminator.FLAT;
        }

        @Override
        public int estimatedBlockCount() {
            int floor = sizeX * sizeZ;
            int walls = 2 * (sizeX + sizeZ) * wallHeight;
            int ceiling = ceilingMaterial != null ? sizeX * sizeZ : 0;
            int underfloor = generateUnderfloor ? sizeX * sizeZ * 3 : 0;
            return floor + walls + ceiling + underfloor;
        }

        /**
         * Creates a default flat template for fallback scenarios.
         */
        public static FlatTemplate defaults() {
            return new FlatTemplate(
                64, 64, 64, 10,
                "minecraft:stone_bricks",
                "minecraft:barrier",
                null,
                true
            );
        }
    }

    /**
     * DD2: Structure template - loaded from NBT structure file.
     *
     * <p>Used for pre-built arenas stored as Minecraft structures.
     * Supports rotation, mirroring, and offset adjustments.
     */
    record StructureTemplate(
        String structurePath,
        int offsetX,
        int offsetY,
        int offsetZ,
        Rotation rotation,
        Mirror mirror,
        boolean ignoreAir,
        @Nullable Long seedOverride
    ) implements TemplateType {

        public enum Rotation { NONE, CW_90, CW_180, CCW_90 }
        public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK }

        public StructureTemplate {
            if (structurePath == null || structurePath.isBlank()) {
                throw new IllegalArgumentException("Structure path is required");
            }
        }

        @Override
        public TypeDiscriminator discriminator() {
            return TypeDiscriminator.STRUCTURE;
        }

        @Override
        public boolean requiresExternalResources() {
            return true;
        }

        @Override
        public int estimatedBlockCount() {
            // Structure size is unknown until loaded; use conservative estimate
            return 10000;
        }

        /**
         * Creates a structure template with default settings.
         */
        public static StructureTemplate of(String path) {
            return new StructureTemplate(
                path, 0, 0, 0,
                Rotation.NONE, Mirror.NONE,
                false, null
            );
        }
    }

    /**
     * DD2: Schematic template - loaded from external schematic file.
     *
     * <p>Supports WorldEdit schematics (.schem) and legacy (.schematic) formats.
     * Used for importing arenas from external sources.
     */
    record SchematicTemplate(
        String schematicPath,
        SchematicFormat format,
        int offsetX,
        int offsetY,
        int offsetZ,
        boolean replaceExisting
    ) implements TemplateType {

        public enum SchematicFormat {
            SPONGE_V2(".schem"),
            SPONGE_V3(".schem"),
            LEGACY(".schematic");

            private final String extension;

            SchematicFormat(String extension) {
                this.extension = extension;
            }

            public String extension() {
                return extension;
            }

            public static SchematicFormat fromPath(String path) {
                if (path.endsWith(".schematic")) {
                    return LEGACY;
                }
                return SPONGE_V2; // Default for .schem
            }
        }

        public SchematicTemplate {
            if (schematicPath == null || schematicPath.isBlank()) {
                throw new IllegalArgumentException("Schematic path is required");
            }
        }

        @Override
        public TypeDiscriminator discriminator() {
            return TypeDiscriminator.SCHEMATIC;
        }

        @Override
        public boolean requiresExternalResources() {
            return true;
        }

        @Override
        public int estimatedBlockCount() {
            // Schematic size unknown until loaded
            return 15000;
        }

        /**
         * Creates a schematic template with default settings.
         */
        public static SchematicTemplate of(String path) {
            return new SchematicTemplate(
                path,
                SchematicFormat.fromPath(path),
                0, 0, 0,
                true
            );
        }
    }

    /**
     * DD2: Composite template - combines multiple template layers.
     *
     * <p>Used for complex arenas that combine procedural generation
     * with structure placement. Layers are applied in order.
     */
    record CompositeTemplate(
        java.util.List<Layer> layers,
        MergeStrategy mergeStrategy
    ) implements TemplateType {

        public enum MergeStrategy {
            /** Later layers overwrite earlier layers */
            OVERWRITE,
            /** Later layers only fill air blocks */
            FILL_AIR,
            /** Merge with conflict resolution */
            MERGE
        }

        public record Layer(
            String name,
            TemplateType template,
            int priority,
            @Nullable int[] offset
        ) {
            public Layer {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("Layer name is required");
                }
                if (template == null) {
                    throw new IllegalArgumentException("Layer template is required");
                }
            }

            public static Layer of(String name, TemplateType template) {
                return new Layer(name, template, 0, null);
            }
        }

        public CompositeTemplate {
            if (layers == null || layers.isEmpty()) {
                throw new IllegalArgumentException("At least one layer is required");
            }
            layers = java.util.List.copyOf(layers);
        }

        @Override
        public TypeDiscriminator discriminator() {
            return TypeDiscriminator.COMPOSITE;
        }

        @Override
        public boolean requiresExternalResources() {
            return layers.stream().anyMatch(l -> l.template().requiresExternalResources());
        }

        @Override
        public int estimatedBlockCount() {
            return layers.stream()
                .mapToInt(l -> l.template().estimatedBlockCount())
                .sum();
        }

        /**
         * Creates a composite from a base flat template with structure overlay.
         */
        public static CompositeTemplate withOverlay(FlatTemplate base, StructureTemplate overlay) {
            return new CompositeTemplate(
                java.util.List.of(
                    new Layer("base", base, 0, null),
                    new Layer("overlay", overlay, 1, null)
                ),
                MergeStrategy.FILL_AIR
            );
        }
    }
}
