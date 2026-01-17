package com.devmod.hologram.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramPosition;
import com.devmod.hologram.data.HologramPreset;
import com.devmod.hologram.data.HologramRegistry;
import com.devmod.hologram.data.HologramStyle;
import com.devmod.hologram.data.HologramType;
import com.devmod.runtime.NexusDimensionManager;

/**
 * Handles migration from the legacy NexusHologramManager to the new Hologram Builder system.
 *
 * <p>Migration phases:
 * <ol>
 *   <li>Coexistence: Both systems run in parallel</li>
 *   <li>Auto-Migration: Legacy entities are converted on first load</li>
 *   <li>Delegation: NexusHologramManager APIs delegate to new system</li>
 *   <li>Deprecation: Legacy system marked for removal</li>
 * </ol>
 */
public final class HologramMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramMigration.class);

    /**
     * Tag used by the legacy NexusHologramManager.
     */
    public static final String LEGACY_TAG = "devmod_nexus_hologram";

    /**
     * Tag prefix for hologram type in legacy system.
     */
    public static final String LEGACY_TYPE_PREFIX = "devmod_holo_";

    /**
     * Tag added after migration.
     */
    public static final String MIGRATED_TAG = "devmod_hologram_migrated";

    private HologramMigration() {}

    /**
     * Migrates legacy holograms to the new registry system.
     * Call this during server initialization after the Nexus dimension loads.
     *
     * @param server The Minecraft server
     */
    public static void migrateFromLegacy(@Nonnull MinecraftServer server) {
        // Get Nexus dimension
        ServerLevel nexusLevel = server.getLevel(
            Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (nexusLevel == null) {
            LOGGER.debug("[Hologram] Nexus dimension not available, skipping migration");
            return;
        }

        HologramRegistry registry = HologramRegistry.get(server);

        // Find legacy TextDisplay entities
        List<Display.TextDisplay> legacyHolograms = new ArrayList<>();
        for (Entity entity : nexusLevel.getEntities().getAll()) {
            if (entity instanceof Display.TextDisplay textDisplay) {
                if (entity.getTags().contains(LEGACY_TAG) && !entity.getTags().contains(MIGRATED_TAG)) {
                    legacyHolograms.add(textDisplay);
                }
            }
        }

        if (legacyHolograms.isEmpty()) {
            LOGGER.debug("[Hologram] No legacy holograms to migrate");
            return;
        }

        LOGGER.info("[Hologram] Migrating {} legacy holograms", legacyHolograms.size());

        int migrated = 0;
        for (Display.TextDisplay entity : legacyHolograms) {
            try {
                HologramDefinition def = convertLegacyEntity(
                    Objects.requireNonNull(entity, "entity"), nexusLevel);
                registry.importLegacyHologram(def);

                // Mark as migrated
                entity.addTag(MIGRATED_TAG);
                entity.addTag(HologramManager.HOLOGRAM_TAG);

                // Cache the existing entity
                registry.cacheEntity(Objects.requireNonNull(def.id(), "def.id"), entity);

                migrated++;
                LOGGER.debug("[Hologram] Migrated legacy hologram: {} at {}",
                    def.label(), def.position().toShortString());

            } catch (Exception e) {
                LOGGER.error("[Hologram] Failed to migrate legacy hologram at {}: {}",
                    entity.blockPosition(), e.getMessage());
            }
        }

        LOGGER.info("[Hologram] Migration complete: {}/{} holograms migrated", migrated, legacyHolograms.size());
    }

    /**
     * Converts a legacy TextDisplay entity to a HologramDefinition.
     */
    @Nonnull
    private static HologramDefinition convertLegacyEntity(
        @Nonnull Display.TextDisplay entity,
        @Nonnull ServerLevel level
    ) {
        // Determine type from tags
        HologramType type = HologramType.STATIC;
        String label = "Migrated Hologram";

        for (String tag : entity.getTags()) {
            if (tag.startsWith(LEGACY_TYPE_PREFIX)) {
                String typeName = tag.substring(LEGACY_TYPE_PREFIX.length());
                switch (typeName) {
                    case "leaderboard" -> {
                        type = HologramType.LEADERBOARD;
                        label = "Leaderboard";
                    }
                    case "zone_stats" -> {
                        type = HologramType.DYNAMIC;
                        label = "Zone Stats";
                    }
                    case "announcements" -> {
                        type = HologramType.ANNOUNCEMENT;
                        label = "Announcements";
                    }
                    case "welcome" -> {
                        type = HologramType.DYNAMIC;
                        label = "Welcome";
                    }
                    default -> LOGGER.debug("[Hologram] Unknown legacy hologram type '{}'", typeName);
                }
            }
        }

        // Extract content from TextDisplay entity
        List<String> lines = extractTextLines(entity, level);

        // Position
        BlockPos blockPos = Objects.requireNonNull(entity.blockPosition(), "blockPosition");
        Vec3 entityPos = Objects.requireNonNull(entity.position(), "position");
        Vec3 center = Objects.requireNonNull(Vec3.atCenterOf(blockPos), "atCenterOf");
        Vec3 offset = Objects.requireNonNull(entityPos.subtract(center), "offset");
        HologramPosition position = new HologramPosition(
            blockPos,
            offset,
            level.dimension().location(),
            entity.getYRot(),
            entity.getXRot()
        );

        // Build definition
        return Objects.requireNonNull(HologramDefinition.builder()
            .id(UUID.randomUUID())
            .label(label)
            .type(type)
            .lines(lines)
            .style(HologramStyle.DEFAULT)
            .position(position)
            .options(HologramOptions.DEFAULT)
            .build(), "HologramDefinition");
    }

    /**
     * Creates preset holograms at the Nexus hub origin.
     * Call this when building a new Nexus hub.
     *
     * @param server    The Minecraft server
     * @param hubOrigin The hub origin position
     */
    public static void createPresetHolograms(@Nonnull MinecraftServer server, @Nonnull BlockPos hubOrigin) {
        ServerLevel nexusLevel = server.getLevel(
            Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (nexusLevel == null) {
            return;
        }

        HologramRegistry registry = HologramRegistry.get(server);

        LOGGER.info("[Hologram] Creating preset holograms at hub origin {}", hubOrigin.toShortString());

        for (HologramPreset preset : HologramPreset.values()) {
            HologramDefinition def = Objects.requireNonNull(HologramDefinition.builder()
                .fromPreset(preset, hubOrigin, nexusLevel.dimension().location())
                .build(), "HologramDefinition");

            registry.createHologram(Objects.requireNonNull(def, "def"));
            HologramManager.INSTANCE.spawnHologram(nexusLevel, Objects.requireNonNull(def, "def"));

            LOGGER.debug("[Hologram] Created preset hologram: {} at {}",
                preset.getLabel(), def.position().toShortString());
        }
    }

    /**
     * NBT key for text content in TextDisplay entities.
     */
    private static final String NBT_TEXT = "text";

    /**
     * Extracts text lines from a TextDisplay entity.
     * Reads the text from NBT and deserializes it as a Component.
     *
     * @param entity The TextDisplay entity
     * @param level  The server level (needed for registry access)
     * @return List of text lines, or a fallback if extraction fails
     */
    @Nonnull
    private static List<String> extractTextLines(@Nonnull Display.TextDisplay entity, @Nonnull ServerLevel level) {
        final List<String> fallback = Objects.requireNonNull(
            List.of("Migrated from legacy system"), "fallback");
        try {
            // Extract text from entity NBT
            CompoundTag nbt = new CompoundTag();
            entity.saveWithoutId(nbt);

            if (!nbt.contains(NBT_TEXT)) {
                return fallback;
            }

            String textJson = nbt.getString(NBT_TEXT);
            if (textJson.isEmpty()) {
                return fallback;
            }

            // Deserialize the Component from JSON
            Component text = Component.Serializer.fromJson(textJson,
                Objects.requireNonNull(level.registryAccess(), "registryAccess"));
            if (text == null) {
                return fallback;
            }

            String content = text.getString();
            if (content.isEmpty()) {
                return fallback;
            }

            // Split by newlines if present
            if (content.contains("\n")) {
                List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(content);
                return lines.isEmpty() ? fallback : new ArrayList<>(lines);
            }

            return Objects.requireNonNull(List.of(content), "singleLine");
        } catch (Exception e) {
            LOGGER.warn("[Hologram] Failed to extract text from legacy hologram: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Checks if there are any legacy holograms that need migration.
     */
    public static boolean hasLegacyHolograms(@Nonnull MinecraftServer server) {
        ServerLevel nexusLevel = server.getLevel(
            Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (nexusLevel == null) {
            return false;
        }

        for (Entity entity : nexusLevel.getEntities().getAll()) {
            if (entity.getTags().contains(LEGACY_TAG) && !entity.getTags().contains(MIGRATED_TAG)) {
                return true;
            }
        }
        return false;
    }
}
