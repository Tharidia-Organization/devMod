package com.devmod.runtime.generator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import com.devmod.runtime.generator.DynamicArenaChunkGenerator.DynamicConfig;

/**
 * Resolves biome based on BiomePolicy configuration.
 *
 * <p>Supports three policies:</p>
 * <ul>
 *   <li>FIXED: Returns the explicitly configured biome</li>
 *   <li>MATCH_MOB: Selects biome based on mob's natural habitat</li>
 *   <li>RANDOM_FROM_TAG: Randomly picks from a biome tag</li>
 * </ul>
 */
public final class BiomePolicyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomePolicyResolver.class);

    // Pre-validated constants (guaranteed non-null)
    @Nonnull private static final ResourceKey<Registry<Biome>> BIOME_REGISTRY_KEY =
            Objects.requireNonNull(Registries.BIOME);
    @Nonnull private static final ResourceKey<Biome> PLAINS_KEY =
            Objects.requireNonNull(Biomes.PLAINS);
    @Nonnull private static final List<String> EMPTY_ISSUES = Objects.requireNonNull(List.of());

    /**
     * Threshold for flagging potentially over-broad keywords in diagnostics.
     * Keywords matching more than this many mobs may need refinement.
     */
    private static final int KEYWORD_MATCH_WARNING_THRESHOLD = 5;

    /**
     * Common mob-to-biome mappings for MATCH_MOB policy.
     * Maps mob type paths to their preferred biome.
     */
    private static final Map<String, ResourceKey<Biome>> MOB_BIOME_PREFERENCES = Map.ofEntries(
        // Overworld - Forest/Plains
        Map.entry("zombie", Biomes.PLAINS),
        Map.entry("skeleton", Biomes.PLAINS),
        Map.entry("creeper", Biomes.FOREST),
        Map.entry("spider", Biomes.FOREST),
        Map.entry("enderman", Biomes.PLAINS),
        Map.entry("witch", Biomes.SWAMP),
        Map.entry("slime", Biomes.SWAMP),

        // Cold biomes
        Map.entry("stray", Biomes.SNOWY_TAIGA),
        Map.entry("polar_bear", Biomes.SNOWY_PLAINS),

        // Desert
        Map.entry("husk", Biomes.DESERT),

        // Ocean
        Map.entry("drowned", Biomes.RIVER),
        Map.entry("guardian", Biomes.DEEP_OCEAN),
        Map.entry("elder_guardian", Biomes.DEEP_OCEAN),

        // Nether-style (use crimson/warped for flavor)
        Map.entry("blaze", Biomes.NETHER_WASTES),
        Map.entry("wither_skeleton", Biomes.SOUL_SAND_VALLEY),
        Map.entry("piglin", Biomes.CRIMSON_FOREST),
        Map.entry("piglin_brute", Biomes.CRIMSON_FOREST),
        Map.entry("hoglin", Biomes.CRIMSON_FOREST),
        Map.entry("ghast", Biomes.NETHER_WASTES),
        Map.entry("magma_cube", Biomes.BASALT_DELTAS),

        // End
        Map.entry("shulker", Biomes.THE_END),
        Map.entry("ender_dragon", Biomes.THE_END),

        // Cave dwellers
        Map.entry("cave_spider", Biomes.LUSH_CAVES),
        Map.entry("silverfish", Biomes.DRIPSTONE_CAVES),
        Map.entry("warden", Biomes.DEEP_DARK),

        // Village/Illager
        Map.entry("pillager", Biomes.PLAINS),
        Map.entry("vindicator", Biomes.DARK_FOREST),
        Map.entry("evoker", Biomes.DARK_FOREST),
        Map.entry("ravager", Biomes.PLAINS),
        Map.entry("vex", Biomes.DARK_FOREST),

        // Misc
        Map.entry("phantom", Biomes.PLAINS),
        Map.entry("wither", Biomes.SOUL_SAND_VALLEY),

        // Nether variants (1.16+)
        Map.entry("strider", Biomes.NETHER_WASTES),
        Map.entry("zoglin", Biomes.CRIMSON_FOREST),

        // 1.21+ mobs
        Map.entry("breeze", Biomes.DRIPSTONE_CAVES),  // Trial Chambers are underground, not Deep Dark
        Map.entry("bogged", Biomes.SWAMP),

        // Zombie variants
        Map.entry("zombie_villager", Biomes.PLAINS),

        // Golems (neutral but can be quest targets)
        Map.entry("iron_golem", Biomes.PLAINS),
        Map.entry("snow_golem", Biomes.SNOWY_PLAINS),

        // Undead horses
        Map.entry("skeleton_horse", Biomes.PLAINS),
        Map.entry("zombie_horse", Biomes.PLAINS)
    );

    private BiomePolicyResolver() {
        // Utility class
    }

    /**
     * Resolves the biome based on the configured policy.
     *
     * @param config The dynamic terrain configuration
     * @param server The Minecraft server (for registry access)
     * @param mobId  The mob ID for MATCH_MOB policy (nullable)
     * @param seed   Seed for random selection
     * @return The resolved biome holder, or empty if resolution fails
     */
    public static Optional<Holder<Biome>> resolve(
            @Nonnull DynamicConfig config,
            @Nonnull MinecraftServer server,
            @Nullable ResourceLocation mobId,
            long seed
    ) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(BIOME_REGISTRY_KEY);

        // Switch expression guarantees exhaustive coverage - result is never null
        return switch (config.biomePolicy()) {
            case FIXED -> resolveFixed(config, biomeRegistry);
            case MATCH_MOB -> resolveMobMatch(mobId, biomeRegistry);
            case RANDOM_FROM_TAG -> resolveRandomFromTag(config, biomeRegistry, seed);
        };
    }

    /**
     * Resolves FIXED biome policy - returns the explicitly configured biome.
     */
    private static Optional<Holder<Biome>> resolveFixed(
            DynamicConfig config,
            Registry<Biome> biomeRegistry
    ) {
        ResourceLocation fixedBiome = config.fixedBiome();
        if (fixedBiome == null) {
            LOGGER.info("[BiomePolicy] FIXED policy but no fixedBiome configured, using plains");
            return getPlainsBiome(biomeRegistry);
        }

        ResourceKey<Biome> key = Objects.requireNonNull(
                ResourceKey.create(BIOME_REGISTRY_KEY, fixedBiome));
        Optional<Holder.Reference<Biome>> holder = biomeRegistry.getHolder(key);

        if (holder.isEmpty()) {
            LOGGER.warn("[BiomePolicy] FIXED biome {} not found, falling back to plains", fixedBiome);
            return getPlainsBiome(biomeRegistry);
        }

        return Optional.of(holder.get());
    }

    /**
     * Resolves MATCH_MOB policy - selects biome based on mob's natural habitat.
     */
    private static Optional<Holder<Biome>> resolveMobMatch(
            @Nullable ResourceLocation mobId,
            Registry<Biome> biomeRegistry
    ) {
        if (mobId == null) {
            LOGGER.debug("[BiomePolicy] MATCH_MOB but no mobId provided, using plains");
            return getPlainsBiome(biomeRegistry);
        }

        String mobPath = mobId.getPath();
        ResourceKey<Biome> preferredBiome = MOB_BIOME_PREFERENCES.get(mobPath);

        if (preferredBiome == null) {
            // Try word-boundary match for modded mobs (e.g., "modid:zombie_variant" -> zombie)
            // Prioritize longer (more specific) matches to avoid "skeleton" matching before "wither_skeleton"
            String bestMatchKey = null;
            ResourceKey<Biome> bestMatchBiome = null;

            for (Map.Entry<String, ResourceKey<Biome>> entry : MOB_BIOME_PREFERENCES.entrySet()) {
                String keyword = entry.getKey();
                if (matchesWithWordBoundary(mobPath, keyword)) {
                    // Keep the longest matching keyword (most specific)
                    // Tie-break by alphabetical order for determinism when lengths equal
                    // (Map.ofEntries has unordered iteration)
                    if (bestMatchKey == null
                            || keyword.length() > bestMatchKey.length()
                            || (keyword.length() == bestMatchKey.length()
                                && keyword.compareTo(bestMatchKey) < 0)) {
                        bestMatchKey = keyword;
                        bestMatchBiome = entry.getValue();
                    }
                }
            }

            if (bestMatchBiome != null) {
                preferredBiome = bestMatchBiome;
                LOGGER.debug("[BiomePolicy] Word-boundary match: {} -> {} -> {}",
                        mobId, bestMatchKey, preferredBiome.location());
            }
        }

        // Use plains as default if no preference found (ternary guarantees non-null)
        if (preferredBiome == null) {
            LOGGER.debug("[BiomePolicy] No biome preference for {}, using plains", mobId);
        }
        final ResourceKey<Biome> resolvedBiome = (preferredBiome != null) ? preferredBiome : PLAINS_KEY;

        Optional<Holder.Reference<Biome>> holder = biomeRegistry.getHolder(resolvedBiome);
        if (holder.isEmpty()) {
            LOGGER.warn("[BiomePolicy] Preferred biome {} not found, falling back to plains",
                    resolvedBiome.location());
            return getPlainsBiome(biomeRegistry);
        }

        LOGGER.debug("[BiomePolicy] MATCH_MOB: {} -> {}", mobId, resolvedBiome.location());
        return Optional.of(holder.get());
    }

    /**
     * Resolves RANDOM_FROM_TAG policy - randomly picks a biome from the configured tag.
     */
    private static Optional<Holder<Biome>> resolveRandomFromTag(
            DynamicConfig config,
            Registry<Biome> biomeRegistry,
            long seed
    ) {
        ResourceLocation biomeTagLocation = config.biomeTag();
        if (biomeTagLocation == null) {
            LOGGER.info("[BiomePolicy] RANDOM_FROM_TAG but no biomeTag configured, using plains");
            return getPlainsBiome(biomeRegistry);
        }

        TagKey<Biome> tagKey = Objects.requireNonNull(
                TagKey.create(BIOME_REGISTRY_KEY, biomeTagLocation));
        List<Holder<Biome>> biomesInTag = biomeRegistry.getTag(tagKey)
                .map(tag -> tag.stream().toList())
                .orElse(List.of());

        if (biomesInTag.isEmpty()) {
            LOGGER.warn("[BiomePolicy] Biome tag {} is empty or doesn't exist, falling back to plains",
                    biomeTagLocation);
            return getPlainsBiome(biomeRegistry);
        }

        // Deterministic random selection based on seed
        RandomSource random = RandomSource.create(seed);
        int index = random.nextInt(biomesInTag.size());
        Holder<Biome> selected = biomesInTag.get(index);

        LOGGER.debug("[BiomePolicy] RANDOM_FROM_TAG: selected {} from tag {} ({} biomes)",
                selected.unwrapKey().map(k -> k.location()).orElse(null),
                biomeTagLocation,
                biomesInTag.size());

        return Optional.of(selected);
    }

    /**
     * Gets the default biome (plains) as a fallback.
     */
    public static Optional<Holder<Biome>> getDefaultBiome(@Nonnull MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(BIOME_REGISTRY_KEY);
        return getPlainsBiome(biomeRegistry);
    }

    /**
     * Validates biome configuration before starting a quest.
     * Returns a list of issues found (empty if config is valid).
     *
     * <p>Note: This method is advisory only - it logs warnings but does not
     * block quest start. Invalid configurations gracefully fall back to plains
     * biome via {@link #resolve}. Use this for early detection and logging.</p>
     *
     * @param config The dynamic terrain configuration
     * @param server The Minecraft server (for registry access)
     * @return List of validation issues, empty if valid
     */
    @Nonnull
    public static List<String> validateConfig(
            @Nonnull DynamicConfig config,
            @Nonnull MinecraftServer server
    ) {
        // Check mode requirements first (avoid allocation if valid MATCH_MOB)
        boolean modeValid = !(config.mode() == DynamicArenaChunkGenerator.Mode.PROXY_WORLDGEN
                && config.sourceDimension() == null);

        // Validate radius constraints
        boolean radiusValid = config.combatRingRadius() > 0
                && config.blendRadius() >= 0
                && config.blendRadius() <= config.combatRingRadius();

        // MATCH_MOB always has plains fallback - skip biome validation if mode/radius valid
        if (modeValid && radiusValid
                && config.biomePolicy() == DynamicArenaChunkGenerator.BiomePolicy.MATCH_MOB) {
            return EMPTY_ISSUES;
        }

        // Only allocate if we have issues to report
        List<String> issues = new java.util.ArrayList<>();

        if (!modeValid) {
            issues.add("PROXY_WORLDGEN mode requires sourceDimension to be set");
        }

        // Warn if CONTROLLED_NATURAL has sourceDimension set (probably meant PROXY_WORLDGEN)
        var sourceDim = config.sourceDimension();
        if (config.mode() == DynamicArenaChunkGenerator.Mode.CONTROLLED_NATURAL
                && sourceDim != null) {
            LOGGER.warn("[BiomePolicy] CONTROLLED_NATURAL mode ignores sourceDimension ({}). Did you mean PROXY_WORLDGEN?",
                    sourceDim.location());
        }

        if (config.combatRingRadius() <= 0) {
            issues.add("combatRingRadius must be positive (got " + config.combatRingRadius() + ")");
        }
        if (config.blendRadius() < 0) {
            issues.add("blendRadius cannot be negative (got " + config.blendRadius() + ")");
        }
        if (config.blendRadius() > config.combatRingRadius()) {
            issues.add("blendRadius (" + config.blendRadius() + ") cannot exceed combatRingRadius (" + config.combatRingRadius() + ")");
        }

        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(BIOME_REGISTRY_KEY);

        switch (config.biomePolicy()) {
            case FIXED -> {
                ResourceLocation fixedBiome = config.fixedBiome();
                if (fixedBiome == null) {
                    issues.add("FIXED policy requires fixedBiome to be set");
                } else {
                    ResourceKey<Biome> key = Objects.requireNonNull(
                            ResourceKey.create(BIOME_REGISTRY_KEY, fixedBiome));
                    if (biomeRegistry.getHolder(key).isEmpty()) {
                        issues.add("Fixed biome '" + fixedBiome + "' not found in registry");
                    }
                }
            }
            case RANDOM_FROM_TAG -> {
                ResourceLocation biomeTag = config.biomeTag();
                if (biomeTag == null) {
                    issues.add("RANDOM_FROM_TAG policy requires biomeTag to be set");
                } else {
                    TagKey<Biome> tagKey = Objects.requireNonNull(
                            TagKey.create(BIOME_REGISTRY_KEY, biomeTag));
                    var tag = biomeRegistry.getTag(tagKey);
                    // HolderSet.Named doesn't have isEmpty(), use size() == 0
                    if (tag.isEmpty() || tag.get().size() == 0) {
                        issues.add("Biome tag '" + biomeTag + "' is empty or doesn't exist");
                    }
                }
            }
            case MATCH_MOB -> {
                // Already handled above with early return
            }
            default -> {
                // Future-proof: warn if new BiomePolicy values are added
                LOGGER.warn("[BiomePolicy] Unknown biomePolicy: {}. No validation performed.", config.biomePolicy());
            }
        }

        if (!issues.isEmpty()) {
            LOGGER.warn("[BiomePolicy] Configuration validation failed: {}", issues);
            return issues;
        }

        return EMPTY_ISSUES;
    }

    /**
     * Helper to get plains biome from registry.
     * Plains is a vanilla biome and MUST exist. If it doesn't, the game is corrupted.
     *
     * <p>If plains is missing (which should never happen), this method:</p>
     * <ul>
     *   <li>Logs CRITICAL error for server admins to investigate</li>
     *   <li>Returns Optional.empty() for graceful degradation (callers use .orElse())</li>
     * </ul>
     */
    private static Optional<Holder<Biome>> getPlainsBiome(Registry<Biome> biomeRegistry) {
        Optional<Holder.Reference<Biome>> holder = biomeRegistry.getHolder(PLAINS_KEY);
        if (holder.isEmpty()) {
            // This should NEVER happen - plains is a vanilla biome
            LOGGER.error("[BiomePolicy] CRITICAL: Plains biome not found in registry! Game may be corrupted.");
            return Optional.empty();
        }
        return Optional.of(holder.get());
    }

    /**
     * Checks if mobPath contains keyword at word boundaries.
     * Supports both underscore and hyphen as word separators.
     * Matches: "zombie", "zombie_variant", "super-zombie", "super_zombie_king"
     * Does NOT match: "blazing_knight" for "blaze" (no word boundary)
     *
     * @param mobPath The full mob path (e.g., "zombie_variant")
     * @param keyword The keyword to find (e.g., "zombie")
     * @return true if keyword appears at word boundary
     */
    private static boolean matchesWithWordBoundary(String mobPath, String keyword) {
        // Search ALL occurrences, not just the first one.
        // Example: "notzombie_zombie_variant" searching for "zombie"
        // - First occurrence at index 3 fails boundary check (inside "notzombie")
        // - Second occurrence at index 10 passes boundary check
        int searchStart = 0;
        int index;

        while ((index = mobPath.indexOf(keyword, searchStart)) >= 0) {
            // Check start boundary: must be at start or preceded by word separator
            boolean startOk = (index == 0) || isWordSeparator(mobPath.charAt(index - 1));

            // Check end boundary: must be at end or followed by word separator
            int endIndex = index + keyword.length();
            boolean endOk = (endIndex == mobPath.length()) || isWordSeparator(mobPath.charAt(endIndex));

            if (startOk && endOk) {
                return true;  // Found valid match at word boundary
            }

            // Move past this occurrence to search for next
            searchStart = index + 1;
        }

        return false;  // No valid word-boundary match found
    }

    /**
     * Checks if a character is a word separator in mob IDs.
     * Supports underscore (standard) and hyphen (some mods).
     */
    private static boolean isWordSeparator(char c) {
        return c == '_' || c == '-';
    }

    // ========== Runtime Diagnostics ==========

    /**
     * Result of biome matching diagnostics for a single mob.
     * Only created for mobs that successfully matched a keyword.
     *
     * @param mobId Full mob ResourceLocation
     * @param matchedKeyword The keyword that matched (always non-null in results list)
     * @param assignedBiome The biome assigned by the matched keyword
     */
    public record BiomeMatchResult(
        @Nonnull ResourceLocation mobId,
        @Nonnull String matchedKeyword,
        @Nonnull ResourceKey<Biome> assignedBiome
    ) {}

    /**
     * Summary of biome matching diagnostics.
     *
     * @param totalMobs Total number of mob entities tested
     * @param matchedMobs Number of mobs with biome matches
     * @param unmatchedMobs Number of mobs that would fall back to plains
     * @param results Per-mob results (only mobs with matches, to avoid spam)
     * @param potentialIssues Keywords that might be too generic (matched many mobs)
     */
    public record DiagnosticSummary(
        int totalMobs,
        int matchedMobs,
        int unmatchedMobs,
        List<BiomeMatchResult> results,
        List<String> potentialIssues
    ) {}

    /**
     * Run biome matching diagnostics against all registered EntityTypes.
     * Tests each mob ID against all keywords in MOB_BIOME_PREFERENCES.
     *
     * <p>This is a runtime diagnostic that works with any modpack,
     * validating keyword matching against actual registered mobs.</p>
     *
     * <p><strong>Limitation:</strong> Only tests entities extending {@code Mob.class}.
     * Some mods may have hostile entities extending {@code LivingEntity} directly
     * (e.g., custom bosses). These would not appear in diagnostics but would still
     * receive biome matching during quest generation via {@link #resolveMobMatch}.</p>
     *
     * @return Diagnostic summary with match statistics
     */
    public static DiagnosticSummary runDiagnostics() {
        var entityRegistry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        List<BiomeMatchResult> matchedResults = new java.util.ArrayList<>();
        java.util.Map<String, Integer> keywordMatchCounts = new java.util.HashMap<>();

        int totalMobs = 0;
        int matchedMobs = 0;

        for (var entityType : entityRegistry) {
            // Only test living entities (potential mob targets)
            // Defensive null check: getBaseClass() should never be null for registered entities,
            // but we guard against corrupted registries or unusual mod behavior
            Class<?> baseClass = entityType.getBaseClass();
            if (baseClass == null || !net.minecraft.world.entity.Mob.class.isAssignableFrom(baseClass)) {
                continue;
            }

            ResourceLocation mobId = entityRegistry.getKey(entityType);
            String mobPath = mobId.getPath();
            totalMobs++;

            // Find longest matching keyword (consistent with resolveMobMatch logic)
            String matchedKeyword = null;
            ResourceKey<Biome> assignedBiome = null;

            for (var entry : MOB_BIOME_PREFERENCES.entrySet()) {
                String keyword = entry.getKey();
                if (matchesWithWordBoundary(mobPath, keyword)) {
                    // Keep the longest matching keyword (most specific)
                    // e.g., "zombie_villager" (16 chars) beats "zombie" (6 chars)
                    // Tie-break by alphabetical order for determinism when lengths equal
                    if (matchedKeyword == null
                            || keyword.length() > matchedKeyword.length()
                            || (keyword.length() == matchedKeyword.length()
                                && keyword.compareTo(matchedKeyword) < 0)) {
                        matchedKeyword = keyword;
                        assignedBiome = entry.getValue();
                    }
                }
            }

            // Track keyword usage after finding best match
            if (matchedKeyword != null) {
                keywordMatchCounts.compute(matchedKeyword, (k, v) -> v == null ? 1 : v + 1);
            }

            if (matchedKeyword != null) {
                matchedMobs++;
                // assignedBiome is guaranteed non-null when matchedKeyword is set
                matchedResults.add(new BiomeMatchResult(mobId, matchedKeyword,
                        Objects.requireNonNull(assignedBiome)));
            }
        }

        // Identify potentially problematic keywords (too many matches)
        List<String> potentialIssues = keywordMatchCounts.entrySet().stream()
            .filter(e -> e.getValue() > KEYWORD_MATCH_WARNING_THRESHOLD)
            .map(e -> e.getKey() + " (" + e.getValue() + " matches)")
            .toList();

        return new DiagnosticSummary(
            totalMobs,
            matchedMobs,
            totalMobs - matchedMobs,
            List.copyOf(matchedResults),  // Immutable snapshot
            potentialIssues               // Already immutable from .toList()
        );
    }

    /**
     * Get all keywords in MOB_BIOME_PREFERENCES.
     * Useful for diagnostics and debugging.
     *
     * <p>The returned set is an unmodifiable view. {@code MOB_BIOME_PREFERENCES}
     * is created via {@code Map.ofEntries()} which guarantees immutability,
     * so this view is safe to expose without defensive copying.</p>
     *
     * @return Unmodifiable set of mob keywords
     */
    public static java.util.Set<String> getRegisteredKeywords() {
        return MOB_BIOME_PREFERENCES.keySet();
    }
}
