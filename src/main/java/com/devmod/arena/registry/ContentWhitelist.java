package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-009: Block and entity whitelist validation for arena templates.
 *
 * Ensures templates only reference allowed content to prevent:
 * <ul>
 *   <li>Command blocks and other dangerous blocks</li>
 *   <li>Entities that could cause server issues</li>
 *   <li>Modded content that may not be installed</li>
 *   <li>Creative-only blocks in survival contexts</li>
 * </ul>
 *
 * <p>The whitelist operates in two modes:
 * <ul>
 *   <li>STRICT: Only explicitly whitelisted items allowed</li>
 *   <li>PERMISSIVE: Blacklist-based, allows anything not explicitly blocked</li>
 * </ul>
 */
public final class ContentWhitelist {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentWhitelist.class);

    /**
     * Validation mode.
     */
    public enum Mode {
        /** Only whitelisted items allowed */
        STRICT,
        /** Everything allowed except blacklisted items */
        PERMISSIVE
    }

    // ============================================================================
    // DANGEROUS BLOCKS (always blocked in both modes)
    // ============================================================================

    private static final Set<String> DANGEROUS_BLOCKS = Set.of(
        // Command blocks
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",

        // Structure blocks
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",

        // Debug items
        "minecraft:debug_stick",

        // Potentially harmful
        "minecraft:tnt",
        "minecraft:spawner",
        "minecraft:end_portal",
        "minecraft:end_portal_frame",
        "minecraft:end_gateway",

        // Light blocks (can cause issues)
        "minecraft:light"
    );

    // ============================================================================
    // DANGEROUS ENTITIES (always blocked)
    // ============================================================================

    private static final Set<String> DANGEROUS_ENTITIES = Set.of(
        // Area-of-effect damage
        "minecraft:ender_dragon",
        "minecraft:wither",

        // Duplicators/exploits
        "minecraft:item",
        "minecraft:experience_orb",

        // Technical entities
        "minecraft:marker",
        "minecraft:interaction",
        "minecraft:area_effect_cloud",

        // Command-capable
        "minecraft:command_block_minecart"
    );

    // ============================================================================
    // ALLOWED BLOCKS (for strict mode)
    // ============================================================================

    private static final Set<String> ALLOWED_BLOCKS = Set.of(
        // Building materials
        "minecraft:stone", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
        "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
        "minecraft:cobblestone", "minecraft:mossy_cobblestone",
        "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:polished_deepslate",
        "minecraft:deepslate_bricks", "minecraft:deepslate_tiles",
        "minecraft:granite", "minecraft:polished_granite",
        "minecraft:diorite", "minecraft:polished_diorite",
        "minecraft:andesite", "minecraft:polished_andesite",
        "minecraft:bricks", "minecraft:nether_bricks", "minecraft:red_nether_bricks",
        "minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine",
        "minecraft:purpur_block", "minecraft:purpur_pillar",
        "minecraft:quartz_block", "minecraft:chiseled_quartz_block", "minecraft:quartz_bricks",
        "minecraft:sandstone", "minecraft:red_sandstone",
        "minecraft:smooth_stone", "minecraft:smooth_sandstone", "minecraft:smooth_red_sandstone",

        // Natural blocks
        "minecraft:dirt", "minecraft:grass_block", "minecraft:sand", "minecraft:gravel",
        "minecraft:clay", "minecraft:mud", "minecraft:packed_mud", "minecraft:mud_bricks",

        // Ores and minerals
        "minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block",
        "minecraft:emerald_block", "minecraft:lapis_block", "minecraft:redstone_block",
        "minecraft:copper_block", "minecraft:netherite_block",
        "minecraft:amethyst_block", "minecraft:coal_block",

        // Wood types (all)
        "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
        "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
        "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
        "minecraft:crimson_planks", "minecraft:warped_planks",

        // Glass
        "minecraft:glass", "minecraft:white_stained_glass", "minecraft:orange_stained_glass",
        "minecraft:magenta_stained_glass", "minecraft:light_blue_stained_glass",
        "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass",
        "minecraft:pink_stained_glass", "minecraft:gray_stained_glass",
        "minecraft:light_gray_stained_glass", "minecraft:cyan_stained_glass",
        "minecraft:purple_stained_glass", "minecraft:blue_stained_glass",
        "minecraft:brown_stained_glass", "minecraft:green_stained_glass",
        "minecraft:red_stained_glass", "minecraft:black_stained_glass",
        "minecraft:tinted_glass",

        // Concrete
        "minecraft:white_concrete", "minecraft:orange_concrete", "minecraft:magenta_concrete",
        "minecraft:light_blue_concrete", "minecraft:yellow_concrete", "minecraft:lime_concrete",
        "minecraft:pink_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
        "minecraft:cyan_concrete", "minecraft:purple_concrete", "minecraft:blue_concrete",
        "minecraft:brown_concrete", "minecraft:green_concrete", "minecraft:red_concrete",
        "minecraft:black_concrete",

        // Terracotta
        "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta",
        "minecraft:magenta_terracotta", "minecraft:light_blue_terracotta",
        "minecraft:yellow_terracotta", "minecraft:lime_terracotta", "minecraft:pink_terracotta",
        "minecraft:gray_terracotta", "minecraft:light_gray_terracotta",
        "minecraft:cyan_terracotta", "minecraft:purple_terracotta", "minecraft:blue_terracotta",
        "minecraft:brown_terracotta", "minecraft:green_terracotta", "minecraft:red_terracotta",
        "minecraft:black_terracotta",

        // Lighting
        "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:shroomlight",
        "minecraft:lantern", "minecraft:soul_lantern", "minecraft:torch", "minecraft:soul_torch",
        "minecraft:redstone_torch", "minecraft:jack_o_lantern", "minecraft:froglight",
        "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight",

        // Barriers and air
        "minecraft:barrier", "minecraft:air", "minecraft:void_air", "minecraft:cave_air",

        // Nether
        "minecraft:netherrack", "minecraft:soul_sand", "minecraft:soul_soil",
        "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks",
        "minecraft:basalt", "minecraft:polished_basalt", "minecraft:smooth_basalt",
        "minecraft:magma_block", "minecraft:obsidian", "minecraft:crying_obsidian",

        // End
        "minecraft:end_stone", "minecraft:end_stone_bricks",

        // Special
        "minecraft:bedrock", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice",
        "minecraft:honey_block", "minecraft:slime_block", "minecraft:hay_block",
        "minecraft:sponge", "minecraft:wet_sponge",

        // Fluids
        "minecraft:water", "minecraft:lava"
    );

    // ============================================================================
    // ALLOWED ENTITIES (for strict mode)
    // ============================================================================

    private static final Set<String> ALLOWED_ENTITIES = Set.of(
        // Hostile mobs
        "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
        "minecraft:cave_spider", "minecraft:enderman", "minecraft:slime", "minecraft:magma_cube",
        "minecraft:blaze", "minecraft:ghast", "minecraft:wither_skeleton", "minecraft:husk",
        "minecraft:stray", "minecraft:drowned", "minecraft:phantom", "minecraft:pillager",
        "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager", "minecraft:witch",
        "minecraft:vex", "minecraft:hoglin", "minecraft:zoglin", "minecraft:piglin",
        "minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:guardian",
        "minecraft:elder_guardian", "minecraft:shulker", "minecraft:silverfish",
        "minecraft:endermite", "minecraft:warden",

        // Passive mobs (for special arenas)
        "minecraft:armor_stand",
        "minecraft:villager",
        "minecraft:iron_golem",

        // Projectiles
        "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:fireball",
        "minecraft:small_fireball", "minecraft:dragon_fireball", "minecraft:wither_skull"
    );

    // ============================================================================
    // VALIDATION
    // ============================================================================

    private final Mode mode;
    private final Set<String> additionalAllowedBlocks;
    private final Set<String> additionalAllowedEntities;
    private final Set<Pattern> modPatterns;

    /**
     * Create a whitelist with the specified mode.
     */
    public ContentWhitelist(Mode mode) {
        this.mode = mode;
        this.additionalAllowedBlocks = new HashSet<>();
        this.additionalAllowedEntities = new HashSet<>();
        this.modPatterns = new HashSet<>();
    }

    /**
     * Allow additional blocks beyond the default whitelist.
     */
    public ContentWhitelist allowBlocks(String... blocks) {
        for (String block : blocks) {
            additionalAllowedBlocks.add(normalizeId(block));
        }
        return this;
    }

    /**
     * Allow additional entities beyond the default whitelist.
     */
    public ContentWhitelist allowEntities(String... entities) {
        for (String entity : entities) {
            additionalAllowedEntities.add(normalizeId(entity));
        }
        return this;
    }

    /**
     * Allow all content from a mod namespace.
     *
     * @param modId The mod namespace (e.g., "alexsmobs")
     */
    public ContentWhitelist allowMod(String modId) {
        modPatterns.add(Pattern.compile("^" + Pattern.quote(modId) + ":.*$"));
        return this;
    }

    /**
     * Check if a block is allowed.
     */
    public boolean isBlockAllowed(String blockId) {
        String normalized = normalizeId(blockId);

        // Always block dangerous blocks
        if (DANGEROUS_BLOCKS.contains(normalized)) {
            return false;
        }

        // Check mod patterns
        for (Pattern pattern : modPatterns) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }

        // Check additional allowed
        if (additionalAllowedBlocks.contains(normalized)) {
            return true;
        }

        return switch (mode) {
            case STRICT -> ALLOWED_BLOCKS.contains(normalized);
            case PERMISSIVE -> true; // Everything allowed except dangerous
        };
    }

    /**
     * Check if an entity is allowed.
     */
    public boolean isEntityAllowed(String entityId) {
        String normalized = normalizeId(entityId);

        // Always block dangerous entities
        if (DANGEROUS_ENTITIES.contains(normalized)) {
            return false;
        }

        // Check mod patterns
        for (Pattern pattern : modPatterns) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }

        // Check additional allowed
        if (additionalAllowedEntities.contains(normalized)) {
            return true;
        }

        return switch (mode) {
            case STRICT -> ALLOWED_ENTITIES.contains(normalized);
            case PERMISSIVE -> true;
        };
    }

    /**
     * Validate a template against this whitelist.
     *
     * @param template The template to validate
     * @return Validation result
     */
    public ValidationResult validate(ArenaTemplate template) {
        Objects.requireNonNull(template, "template");

        List<String> violations = new ArrayList<>();

        // Check floor material
        if (template.floor() != null) {
            checkBlock(template.floor().material(), "floor.material", violations);
            if (template.floor().borderMaterial() != null) {
                checkBlock(template.floor().borderMaterial(), "floor.borderMaterial", violations);
            }
        }

        // Check walls material
        if (template.walls() != null && template.walls().enabled()) {
            checkBlock(template.walls().material(), "walls.material", violations);
        }

        // Check ceiling material
        if (template.ceiling() != null && template.ceiling().enabled()) {
            checkBlock(template.ceiling().material(), "ceiling.material", violations);
        }

        // Check underfloor material
        if (template.underfloor() != null) {
            checkBlock(template.underfloor().material(), "underfloor.material", violations);
        }

        // Check palette (accent, highlight, hazardBorder are color names, not blocks)
        // Palette doesn't contain block references, so no validation needed

        // Check hazards (hazards use type + params, block is determined at runtime)
        // Hazard validation is done via HazardValidator, not block whitelist

        // Check lighting
        if (template.lighting() != null && template.lighting().lightSources() != null) {
            for (int i = 0; i < template.lighting().lightSources().size(); i++) {
                ArenaTemplate.Lighting.LightSource source = template.lighting().lightSources().get(i);
                if (source.block() != null) {
                    checkBlock(source.block(), "lighting.lightSources[" + i + "].block", violations);
                }
            }
        }

        if (violations.isEmpty()) {
            return ValidationResult.success(template.id());
        }

        return ValidationResult.failure(template.id(), violations);
    }

    private void checkBlock(@Nullable String blockId, String path, List<String> violations) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        if (!isBlockAllowed(blockId)) {
            violations.add(path + ": '" + blockId + "' is not allowed");
            LOGGER.warn("[ContentWhitelist] Blocked: {} at {}", blockId, path);
        }
    }

    private static String normalizeId(String id) {
        if (id == null) return "";
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean valid,
        String templateId,
        List<String> violations
    ) {
        public static ValidationResult success(String templateId) {
            return new ValidationResult(true, templateId, List.of());
        }

        public static ValidationResult failure(String templateId, List<String> violations) {
            return new ValidationResult(false, templateId, List.copyOf(violations));
        }
    }

    // ============================================================================
    // FACTORY METHODS
    // ============================================================================

    /**
     * Create a strict whitelist (only explicitly allowed content).
     */
    public static ContentWhitelist strict() {
        return new ContentWhitelist(Mode.STRICT);
    }

    /**
     * Create a permissive whitelist (everything except dangerous content).
     */
    public static ContentWhitelist permissive() {
        return new ContentWhitelist(Mode.PERMISSIVE);
    }

    /**
     * Get the set of always-dangerous blocks.
     */
    public static Set<String> getDangerousBlocks() {
        return Set.copyOf(DANGEROUS_BLOCKS);
    }

    /**
     * Get the set of always-dangerous entities.
     */
    public static Set<String> getDangerousEntities() {
        return Set.copyOf(DANGEROUS_ENTITIES);
    }
}
