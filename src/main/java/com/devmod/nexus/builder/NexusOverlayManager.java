package com.devmod.nexus.builder;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import com.devmod.config.Config;

/**
 * Handles structure overlay application for the Nexus hub.
 * Extracted from NexusHubBuilder to preserve overlay functionality.
 *
 * <p>Allows applying a custom structure template on top of the base hub,
 * enabling server operators to customize the hub appearance via data packs.
 */
public final class NexusOverlayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusOverlayManager.class);

    /** Singleton instance. */
    public static final NexusOverlayManager INSTANCE = new NexusOverlayManager();

    /** Block placement flags: NOTIFY_CLIENTS | NO_NEIGHBOR_DROPS | PREVENT_REBUILD_RENDER */
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    private NexusOverlayManager() {}

    /**
     * Apply optional structure overlay from config.
     * Uses the template specified in Config.NEXUS_LAYOUT_OVERLAY_TEMPLATE.
     *
     * @param level the server level
     * @param origin the hub origin position
     */
    public void applyOptionalOverlay(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        String templateId = Config.NEXUS_LAYOUT_OVERLAY_TEMPLATE.get();
        if (templateId == null || templateId.isBlank()) {
            return;
        }

        ResourceLocation location = ResourceLocation.tryParse(Objects.requireNonNull(templateId.trim()));
        if (location == null) {
            LOGGER.warn("[Nexus] Invalid overlay template id: {}", templateId);
            return;
        }

        StructureTemplate template = resolveTemplate(level, location);
        if (template == null) {
            LOGGER.warn("[Nexus] Overlay template not found: {}", location);
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(true);

        template.placeInWorld(
            level,
            origin,
            origin,
            settings,
            Objects.requireNonNull(level.getRandom()),
            PLACEMENT_FLAGS
        );

        LOGGER.info("[Nexus] Applied overlay template {}", location);
    }

    /**
     * Apply a specific overlay template.
     *
     * @param level the server level
     * @param origin the placement origin
     * @param templateLocation the template resource location
     * @return true if applied successfully
     */
    public boolean applyOverlay(@Nonnull ServerLevel level, @Nonnull BlockPos origin,
                                 @Nonnull ResourceLocation templateLocation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(templateLocation, "templateLocation");

        StructureTemplate template = resolveTemplate(level, templateLocation);
        if (template == null) {
            LOGGER.warn("[Nexus] Overlay template not found: {}", templateLocation);
            return false;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(true);

        template.placeInWorld(
            level,
            origin,
            origin,
            settings,
            Objects.requireNonNull(level.getRandom()),
            PLACEMENT_FLAGS
        );

        LOGGER.info("[Nexus] Applied overlay template {}", templateLocation);
        return true;
    }

    /**
     * Resolve a structure template from the template manager.
     *
     * @param level the server level
     * @param location the template resource location
     * @return the template, or null if not found
     */
    @Nullable
    private StructureTemplate resolveTemplate(@Nonnull ServerLevel level, @Nonnull ResourceLocation location) {
        StructureTemplateManager manager = level.getStructureManager();
        return manager.get(location).orElse(null);
    }
}
