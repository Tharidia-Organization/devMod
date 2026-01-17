package com.devmod.template.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.devmod.DevMod;
import com.devmod.template.data.ComponentPlacement;
import com.devmod.template.data.RoomTemplate;
import com.devmod.template.data.TemplateRegistry;
import com.devmod.template.data.TemplateSpacing;
import com.devmod.template.data.ZoneTemplate;

/**
 * Runtime manager for applying room templates.
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum TemplateManager {
    INSTANCE;

    // Track active build tasks to prevent overlapping
    private final Map<BlockPos, Long> activeBuildTasks = new ConcurrentHashMap<>();

    /**
     * Apply a template to a room.
     *
     * @param level The server level
     * @param templateId The template ID to apply
     * @param minX Room bounds minimum X
     * @param minZ Room bounds minimum Z
     * @param maxX Room bounds maximum X
     * @param maxZ Room bounds maximum Z
     * @param floorY Floor Y level
     * @param initiator Player who initiated (for sounds/messages)
     * @return true if template was applied
     */
    public boolean applyTemplate(@Nonnull ServerLevel level, @Nonnull String templateId,
                                 int minX, int minZ, int maxX, int maxZ, int floorY,
                                 @Nullable ServerPlayer initiator) {

        TemplateRegistry registry = TemplateRegistry.get(level);
        Optional<RoomTemplate> templateOpt = registry.getTemplate(templateId);

        if (templateOpt.isEmpty()) {
            DevMod.LOGGER.warn("[Template] Template not found: {}", templateId);
            if (initiator != null) {
                sendMessage(initiator, "message.devmod.template.error.not_found");
            }
            return false;
        }

        RoomTemplate template = templateOpt.get();
        int width = maxX - minX;
        int depth = maxZ - minZ;

        // Check size requirements
        if (!template.fitsRoom(width, depth)) {
            DevMod.LOGGER.warn("[Template] Room too small for template {}: {}x{} < {}x{}",
                templateId, width, depth, template.minWidth(), template.minDepth());
            if (initiator != null) {
                sendMessage(initiator, "message.devmod.template.error.room_too_small");
            }
            return false;
        }

        // Prevent overlapping builds
        BlockPos centerPos = new BlockPos((minX + maxX) / 2, floorY, (minZ + maxZ) / 2);
        if (activeBuildTasks.containsKey(centerPos)) {
            if (initiator != null) {
                sendMessage(initiator, "message.devmod.template.error.build_in_progress");
            }
            return false;
        }

        // Mark as building
        activeBuildTasks.put(centerPos, System.currentTimeMillis());

        // Get zone template for colors
        ZoneTemplate zone = ZoneTemplate.fromString(template.zoneId());

        // Play start sound
        if (initiator != null) {
            level.playSound(null, centerPos, Objects.requireNonNull(SoundEvents.BEACON_POWER_SELECT),
                SoundSource.BLOCKS, 0.8f, 1.0f);
        }

        // Build components
        try {
            buildComponents(level, template, minX, minZ, maxX, maxZ, floorY, zone);

            // Play completion sound
            if (initiator != null) {
                level.playSound(null, centerPos, Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP),
                    SoundSource.BLOCKS, 0.6f, 1.0f);
                sendMessage(initiator, "message.devmod.template.applied", template.displayName());
            }

            DevMod.LOGGER.info("[Template] Applied template {} at ({}, {}, {})",
                templateId, centerPos.getX(), centerPos.getY(), centerPos.getZ());
            return true;

        } catch (Exception e) {
            DevMod.LOGGER.error("[Template] Failed to apply template {}: {}", templateId, e.getMessage());
            if (initiator != null) {
                sendMessage(initiator, "message.devmod.template.error.build_failed");
            }
            return false;

        } finally {
            activeBuildTasks.remove(centerPos);
        }
    }

    /**
     * Apply a template using zone bounds from ZoneDefinition.
     */
    public boolean applyTemplateToZone(@Nonnull ServerLevel level, @Nonnull String templateId,
                                       @Nonnull String zoneId, @Nullable ServerPlayer initiator) {
        // This would integrate with ZoneRegistry to get bounds
        // For now, log a placeholder message
        DevMod.LOGGER.debug("[Template] applyTemplateToZone called for zone: {}", zoneId);
        return false;
    }

    /**
     * Preview a template (without placing blocks).
     */
    public List<BlockPos> previewTemplate(@Nonnull ServerLevel level, @Nonnull String templateId,
                                          int minX, int minZ, int maxX, int maxZ, int floorY) {
        // Returns positions where components would be placed
        List<BlockPos> positions = new java.util.ArrayList<>();

        TemplateRegistry registry = TemplateRegistry.get(level);
        Optional<RoomTemplate> templateOpt = registry.getTemplate(templateId);

        if (templateOpt.isEmpty()) {
            return positions;
        }

        RoomTemplate template = templateOpt.get();
        for (ComponentPlacement placement : template.components()) {
            BlockPos anchor = placement.position().resolve(minX, minZ, maxX, maxZ, floorY);
            positions.add(anchor);
        }

        return positions;
    }

    /**
     * Build all components for a template.
     */
    private void buildComponents(ServerLevel level, RoomTemplate template,
                                 int minX, int minZ, int maxX, int maxZ, int floorY,
                                 ZoneTemplate zone) {

        for (ComponentPlacement placement : template.components()) {
            // Resolve position
            BlockPos anchor = placement.position().resolve(minX, minZ, maxX, maxZ, floorY);

            // Build component
            ComponentBuilder.buildComponent(Objects.requireNonNull(level), Objects.requireNonNull(anchor), placement, Objects.requireNonNull(zone));
        }
    }

    /**
     * Clear decorations in an area (for re-applying templates).
     */
    public void clearArea(@Nonnull ServerLevel level,
                          int minX, int minZ, int maxX, int maxZ,
                          int floorY, int height) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Clear above floor (keep floor intact)
        for (int x = minX + TemplateSpacing.WALL_MARGIN; x < maxX - TemplateSpacing.WALL_MARGIN; x++) {
            for (int z = minZ + TemplateSpacing.WALL_MARGIN; z < maxZ - TemplateSpacing.WALL_MARGIN; z++) {
                for (int y = floorY + 1; y < floorY + height; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    /**
     * Get available templates for a zone.
     */
    public List<RoomTemplate> getAvailableTemplates(@Nonnull ServerLevel level, @Nonnull String zoneId) {
        TemplateRegistry registry = TemplateRegistry.get(level);
        return registry.getTemplatesForZone(zoneId);
    }

    /**
     * Register default template presets.
     */
    public void initializePresets(@Nonnull ServerLevel level) {
        TemplateRegistry registry = TemplateRegistry.get(level);

        // Only initialize if empty
        if (registry.getTemplateCount() > 0) {
            DevMod.LOGGER.debug("[Template] Presets already initialized ({} templates)", registry.getTemplateCount());
            return;
        }

        // Register presets from TemplatePresets class
        TemplatePresets.registerAll(registry);

        DevMod.LOGGER.info("[Template] Initialized {} preset templates", registry.getTemplateCount());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void sendMessage(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args))));
    }
}
