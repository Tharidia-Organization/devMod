package com.devmod.area.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import com.devmod.DevMod;

/**
 * Carica dinamicamente TUTTI i biomi registrati (vanilla + moddati).
 * Supporta: Terralith, Biomes O' Plenty, Oh The Biomes You'll Go, etc.
 */
public final class BiomeRegistry {

    private static final Map<String, List<BiomeEntry>> BIOME_CATEGORIES = new LinkedHashMap<>();
    private static boolean initialized = false;

    private BiomeRegistry() {}

    /**
     * Entry representing a single biome.
     */
    public record BiomeEntry(
        ResourceLocation id,
        String displayName,
        String modId,
        boolean isVanilla
    ) {
        /**
         * Gets a shortened display name for compact UI.
         */
        public String getShortName() {
            if (displayName.length() > 20) {
                return displayName.substring(0, 17) + "...";
            }
            return displayName;
        }
    }

    /**
     * Inizializza il registro caricando tutti i biomi dal BuiltInRegistries.
     * Deve essere chiamato dopo che tutti i mod hanno registrato i loro biomi.
     */
    public static void initialize(@Nonnull RegistryAccess registryAccess) {
        if (initialized) {
            return;
        }

        BIOME_CATEGORIES.clear();

        try {
            Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Objects.requireNonNull(Registries.BIOME));

            // Raggruppa per mod
            Map<String, List<BiomeEntry>> byMod = new LinkedHashMap<>();

            biomeRegistry.entrySet().forEach(entry -> {
                ResourceLocation biomeId = entry.getKey().location();
                String modId = biomeId.getNamespace();
                boolean isVanilla = modId.equals("minecraft");

                String displayName = formatBiomeName(biomeId);

                BiomeEntry biomeEntry = new BiomeEntry(biomeId, displayName, modId, isVanilla);

                byMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(biomeEntry);
            });

            // Ordina: Minecraft prima, poi mod in ordine alfabetico
            if (byMod.containsKey("minecraft")) {
                List<BiomeEntry> vanillaBiomes = byMod.remove("minecraft");
                vanillaBiomes.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
                BIOME_CATEGORIES.put("Minecraft (Vanilla)", vanillaBiomes);
            }

            byMod.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String modName = formatModName(Objects.requireNonNull(e.getKey()));
                    List<BiomeEntry> biomes = e.getValue();
                    biomes.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
                    BIOME_CATEGORIES.put(modName, biomes);
                });

            initialized = true;
            DevMod.LOGGER.info("[Area] BiomeRegistry initialized with {} categories and {} total biomes",
                BIOME_CATEGORIES.size(), getAllBiomes().size());

        } catch (Exception e) {
            DevMod.LOGGER.error("[Area] Failed to initialize BiomeRegistry", e);
        }
    }

    /**
     * Resets the registry (for reloading).
     */
    public static void reset() {
        BIOME_CATEGORIES.clear();
        initialized = false;
    }

    /**
     * Returns true if the registry has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Restituisce tutte le categorie di biomi con i loro biomi.
     */
    @Nonnull
    public static Map<String, List<BiomeEntry>> getAllCategories() {
        return Objects.requireNonNull(Collections.unmodifiableMap(BIOME_CATEGORIES));
    }

    /**
     * Restituisce tutti i biomi come lista flat.
     */
    @Nonnull
    public static List<BiomeEntry> getAllBiomes() {
        return Objects.requireNonNull(BIOME_CATEGORIES.values().stream()
            .flatMap(List::stream)
            .toList());
    }

    /**
     * Restituisce solo i biomi vanilla.
     */
    @Nonnull
    public static List<BiomeEntry> getVanillaBiomes() {
        return Objects.requireNonNull(getAllBiomes().stream()
            .filter(BiomeEntry::isVanilla)
            .toList());
    }

    /**
     * Restituisce i biomi di un mod specifico.
     */
    @Nonnull
    public static List<BiomeEntry> getBiomesByMod(@Nonnull String modId) {
        return Objects.requireNonNull(getAllBiomes().stream()
            .filter(b -> b.modId().equals(modId))
            .toList());
    }

    /**
     * Cerca biomi per nome (case-insensitive, partial match).
     */
    @Nonnull
    public static List<BiomeEntry> searchBiomes(@Nonnull String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return Objects.requireNonNull(getAllBiomes().stream()
            .filter(b -> b.displayName().toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                         b.id().toString().toLowerCase(Locale.ROOT).contains(lowerQuery))
            .toList());
    }

    /**
     * Verifica se un biome ID esiste nel registry.
     */
    public static boolean biomeExists(@Nonnull ResourceLocation biomeId, @Nonnull RegistryAccess registryAccess) {
        try {
            Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Objects.requireNonNull(Registries.BIOME));
            return biomeRegistry.containsKey(biomeId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets a BiomeEntry by ID.
     */
    @Nonnull
    public static BiomeEntry getBiomeEntry(@Nonnull ResourceLocation biomeId) {
        return Objects.requireNonNull(getAllBiomes().stream()
            .filter(b -> b.id().equals(biomeId))
            .findFirst()
            .orElse(new BiomeEntry(biomeId, formatBiomeName(biomeId), biomeId.getNamespace(),
                biomeId.getNamespace().equals("minecraft"))));
    }

    /**
     * Gets the display name for a biome ID.
     */
    @Nonnull
    public static String getBiomeDisplayName(@Nonnull ResourceLocation biomeId) {
        return Objects.requireNonNull(getAllBiomes().stream()
            .filter(b -> b.id().equals(biomeId))
            .map(BiomeEntry::displayName)
            .findFirst()
            .orElse(formatBiomeName(biomeId)));
    }

    /**
     * Gets the category name for a biome.
     */
    @Nonnull
    public static String getCategoryForBiome(@Nonnull ResourceLocation biomeId) {
        String modId = biomeId.getNamespace();
        if (modId.equals("minecraft")) {
            return "Minecraft (Vanilla)";
        }
        return formatModName(modId);
    }

    /**
     * Formats a biome ResourceLocation path to a display name.
     * Example: "minecraft:deep_dark" -> "Deep Dark"
     */
    @Nonnull
    private static String formatBiomeName(@Nonnull ResourceLocation biomeId) {
        String path = biomeId.getPath();

        // Replace underscores with spaces and capitalize each word
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                sb.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }

        return Objects.requireNonNull(sb.toString());
    }

    /**
     * Formats a mod ID to a display name.
     * Example: "biomes_o_plenty" -> "Biomes O Plenty"
     */
    @Nonnull
    private static String formatModName(@Nonnull String modId) {
        // Handle common mod name patterns
        return Objects.requireNonNull(switch (modId) {
            case "minecraft" -> "Minecraft (Vanilla)";
            case "terralith" -> "Terralith";
            case "biomesoplenty" -> "Biomes O' Plenty";
            case "byg" -> "Oh The Biomes You'll Go";
            case "regions_unexplored" -> "Regions Unexplored";
            case "wilderwild" -> "Wilder Wild";
            default -> {
                // Default: capitalize words
                StringBuilder sb = new StringBuilder();
                boolean capitalizeNext = true;

                for (int i = 0; i < modId.length(); i++) {
                    char c = modId.charAt(i);
                    if (c == '_' || c == '-') {
                        sb.append(' ');
                        capitalizeNext = true;
                    } else if (capitalizeNext) {
                        sb.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        sb.append(c);
                    }
                }

                yield sb.toString();
            }
        });
    }

    /**
     * Gets the total number of biomes registered.
     */
    public static int getTotalBiomeCount() {
        return getAllBiomes().size();
    }

    /**
     * Gets the number of categories.
     */
    public static int getCategoryCount() {
        return BIOME_CATEGORIES.size();
    }

    /**
     * Gets category names in order.
     */
    @Nonnull
    public static List<String> getCategoryNames() {
        return new ArrayList<>(BIOME_CATEGORIES.keySet());
    }
}
