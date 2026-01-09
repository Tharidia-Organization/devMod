package com.devmod.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.errorprone.annotations.Immutable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.config.Config;

/**
 * Manages holographic text displays in the Nexus hub.
 * Includes leaderboards, zone stats, and server announcements.
 * Uses TextDisplay entities when available, with ArmorStand fallback.
 */
public final class NexusHologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusHologramManager.class);

    public static final NexusHologramManager INSTANCE = new NexusHologramManager();

    public static final String HOLOGRAM_TAG = "devmod_nexus_hologram";
    private static final String HOLOGRAM_TYPE_PREFIX = "devmod_holo_";
    private static final int MAX_LEADERBOARD_ENTRIES = 100;

    // NBT keys for TextDisplay configuration
    private static final String NBT_TEXT = "text";
    private static final String NBT_LINE_WIDTH = "line_width";
    private static final String NBT_BACKGROUND = "background";
    private static final String NBT_TEXT_OPACITY = "text_opacity";
    private static final String NBT_BILLBOARD = "billboard";
    private static final String NBT_VIEW_RANGE = "view_range";

    // TTL for leaderboard cache entries (1 hour) to prevent memory leaks
    private static final long LEADERBOARD_CACHE_TTL_MS = 60 * 60 * 1000L;
    private static final int CACHE_EVICTION_INTERVAL = 1200; // 1 minute in ticks

    // Hologram types
    public enum HologramType {
        LEADERBOARD("leaderboard", new Offset(0, 4, 20)),
        ZONE_STATS("zone_stats", new Offset(-15, 3, 0)),
        ANNOUNCEMENTS("announcements", new Offset(0, 5, -15)),
        WELCOME("welcome", new Offset(0, 3, 30));

        private final String id;
        private final Offset offset;

        HologramType(String id, Offset offset) {
            this.id = id;
            this.offset = offset;
        }

        public String id() { return id; }
        public Offset offset() { return offset; }
    }

    // Active holograms by type
    private final Map<HologramType, List<UUID>> holograms = new EnumMap<>(HologramType.class);

    // Cached data for updates
    private final Map<String, LeaderboardEntry> leaderboardCache = new ConcurrentHashMap<>();
    private final List<String> announcements = new ArrayList<>();
    private int updateTick = 0;
    private int evictionTick = 0;
    private boolean useTextDisplay = true; // Will be set based on entity creation success

    private NexusHologramManager() {
        for (HologramType type : HologramType.values()) {
            holograms.put(type, new ArrayList<>());
        }
    }

    @Immutable
    private record Offset(int x, int y, int z) {}

    /**
     * Initialize holograms in the Nexus hub.
     */
    public void initialize(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hubOrigin, "hubOrigin");

        if (!Config.NEXUS_HOLOGRAMS_ENABLED.get()) {
            return;
        }

        LOGGER.info("[NexusHolograms] Initializing holographic displays");

        // Clear existing
        cleanup(level);

        // Create each hologram type
        createWelcomeHologram(level, hubOrigin);
        createLeaderboardHologram(level, hubOrigin);
        createZoneStatsHologram(level, hubOrigin);
        createAnnouncementsHologram(level, hubOrigin);
    }

    /**
     * Create the welcome hologram at the spawn area.
     */
    private void createWelcomeHologram(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        BlockPos pos = nn(offset(hubOrigin, HologramType.WELCOME.offset()), "welcomePos");
        Vec3 spawnPos = nn(Vec3.atCenterOf(pos), "welcomeSpawnPos");

        List<Component> lines = nn(List.of(
            styled("NEXUS HUB", DesignTokens.Nexus.TITLE_GOLD, true),
            styled("Development Testing Center", DesignTokens.Nexus.SUBTITLE_GRAY, false),
            Component.empty(),
            styled("Right-click portals to teleport", DesignTokens.Nexus.HINT_BLUE, false),
            styled("Right-click NEXA for help", DesignTokens.Nexus.HINT_GREEN, false)
        ), "welcomeLines");

        createHologram(level, nn(spawnPos, "spawnPos"), nn(lines, "lines"), HologramType.WELCOME);
    }

    /**
     * Create the leaderboard hologram.
     */
    private void createLeaderboardHologram(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        BlockPos pos = nn(offset(hubOrigin, HologramType.LEADERBOARD.offset()), "leaderboardPos");
        Vec3 spawnPos = nn(Vec3.atCenterOf(pos), "leaderboardSpawnPos");

        List<Component> lines = new ArrayList<>();
        lines.add(styled("TOP WAVE RECORDS", DesignTokens.Nexus.LEADERBOARD_GOLD, true));
        lines.add(Component.empty());

        // Placeholder entries - will be updated dynamically
        for (int i = 1; i <= 5; i++) {
            lines.add(styled("#" + i + " ---", DesignTokens.Nexus.PLACEHOLDER_GRAY, false));
        }

        lines.add(Component.empty());
        lines.add(styled("Updated every 30s", DesignTokens.Nexus.FOOTER_GRAY, false));

        createHologram(level, nn(spawnPos, "spawnPos"), lines, HologramType.LEADERBOARD);
    }

    /**
     * Create the zone stats hologram.
     */
    private void createZoneStatsHologram(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        BlockPos pos = nn(offset(hubOrigin, HologramType.ZONE_STATS.offset()), "zoneStatsPos");
        Vec3 spawnPos = nn(Vec3.atCenterOf(pos), "zoneStatsSpawnPos");

        List<Component> lines = nn(List.of(
            styled("ZONE STATUS", DesignTokens.Nexus.ZONE_TITLE, true),
            Component.empty(),
            zoneStatus("Combat", DesignTokens.Nexus.ZONE_COMBAT, "Active"),
            zoneStatus("Arena", DesignTokens.Nexus.ZONE_ARENA, "Ready"),
            zoneStatus("UI Lab", DesignTokens.Nexus.ZONE_UI, "Testing"),
            zoneStatus("Telemetry", DesignTokens.Nexus.ZONE_TELEMETRY, "Logging"),
            zoneStatus("Showcase", DesignTokens.Nexus.ZONE_SHOWCASE, "Open"),
            zoneStatus("Integration", DesignTokens.Nexus.ZONE_INTEGRATION, "Stable"),
            zoneStatus("Sandbox", DesignTokens.Nexus.ZONE_SANDBOX, "Free Build"),
            zoneStatus("Mechanics", DesignTokens.Nexus.ZONE_MECHANICS, "Active")
        ), "zoneStatsLines");

        createHologram(level, nn(spawnPos, "spawnPos"), nn(lines, "lines"), HologramType.ZONE_STATS);
    }

    /**
     * Create the announcements hologram.
     */
    private void createAnnouncementsHologram(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        BlockPos pos = nn(offset(hubOrigin, HologramType.ANNOUNCEMENTS.offset()), "announcementsPos");
        Vec3 spawnPos = nn(Vec3.atCenterOf(pos), "announcementsSpawnPos");

        List<Component> lines = nn(List.of(
            styled("SERVER ANNOUNCEMENTS", DesignTokens.Nexus.ANNOUNCEMENT_TITLE, true),
            Component.empty(),
            styled("Welcome to DevMod testing!", DesignTokens.Nexus.SUBTITLE_GRAY, false),
            styled("Report bugs: /devmod nexus bug", DesignTokens.Nexus.HINT_BLUE, false),
            Component.empty(),
            styled("Latest: Portal network active", DesignTokens.Nexus.HINT_GREEN, false)
        ), "announcementsLines");

        createHologram(level, nn(spawnPos, "spawnPos"), nn(lines, "lines"), HologramType.ANNOUNCEMENTS);
    }

    /**
     * Create a hologram using TextDisplay or ArmorStand fallback.
     */
    private void createHologram(@Nonnull ServerLevel level, @Nonnull Vec3 pos,
                                 @Nonnull List<Component> lines, @Nonnull HologramType type) {
        if (useTextDisplay) {
            try {
                createTextDisplayHologram(level, pos, lines, type);
                return;
            } catch (Exception e) {
                LOGGER.debug("[NexusHolograms] TextDisplay creation failed, using ArmorStand fallback: {}", e.getMessage());
                useTextDisplay = false;
            }
        }

        createArmorStandHologram(level, pos, lines, type);
    }

    /**
     * Create hologram using TextDisplay entity with NBT configuration.
     */
    private void createTextDisplayHologram(@Nonnull ServerLevel level, @Nonnull Vec3 pos,
                                            @Nonnull List<Component> lines, @Nonnull HologramType type) {
        // Build combined text with newlines
        MutableComponent combined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                combined.append("\n");
            }
            combined.append(nn(lines.get(i), "line"));
        }

        // Create TextDisplay entity
        Display.TextDisplay display = new Display.TextDisplay(nn(EntityType.TEXT_DISPLAY, "EntityType.TEXT_DISPLAY"), level);
        display.setPos(pos.x, pos.y, pos.z);

        // Configure everything via NBT since setters are private
        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);

        // Set text as JSON string
        String textJson = nn(Component.Serializer.toJson(nn(combined, "combined"), nn(level.registryAccess(), "registryAccess")), "textJson");
        nbt.putString(NBT_TEXT, nn(textJson, "textJson"));

        // Billboard: "center" = faces player always
        nbt.putString(NBT_BILLBOARD, "center");
        // View range (multiplier of 64 blocks)
        nbt.putFloat(NBT_VIEW_RANGE, 0.75f); // 48 blocks
        // Line width
        nbt.putInt(NBT_LINE_WIDTH, 300);
        // Semi-transparent dark background
        nbt.putInt(NBT_BACKGROUND, DesignTokens.Nexus.HOLOGRAM_BG);
        // Full opacity text
        nbt.putByte(NBT_TEXT_OPACITY, (byte) -1);

        display.load(nbt);

        // Tags for identification
        display.addTag(HOLOGRAM_TAG);
        display.addTag(HOLOGRAM_TYPE_PREFIX + type.id());

        level.addFreshEntity(display);
        holograms.get(type).add(display.getUUID());

        LOGGER.debug("[NexusHolograms] Created TextDisplay {} hologram at {}", type.id(), pos);
    }

    /**
     * Fallback hologram creation using armor stands (one per line).
     */
    private void createArmorStandHologram(@Nonnull ServerLevel level, @Nonnull Vec3 pos,
                                           @Nonnull List<Component> lines, @Nonnull HologramType type) {
        double y = pos.y;
        double lineHeight = 0.28;

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getString().isEmpty()) {
                continue; // Skip empty lines but keep spacing
            }

            ArmorStand stand = new ArmorStand(nn(EntityType.ARMOR_STAND, "EntityType.ARMOR_STAND"), level);
            stand.setPos(pos.x, y - (i * lineHeight), pos.z);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setCustomName(line);
            stand.setCustomNameVisible(true);
            stand.setSilent(true);
            stand.setNoBasePlate(true);

            // Make it a marker (small hitbox, no collisions)
            CompoundTag nbt = new CompoundTag();
            stand.saveWithoutId(nbt);
            nbt.putBoolean("Marker", true);
            nbt.putBoolean("Small", true);
            stand.load(nbt);

            stand.addTag(HOLOGRAM_TAG);
            stand.addTag(HOLOGRAM_TYPE_PREFIX + type.id());

            level.addFreshEntity(stand);
            holograms.get(type).add(stand.getUUID());
        }

        LOGGER.debug("[NexusHolograms] Created ArmorStand {} hologram at {} ({} lines)",
            type.id(), pos, lines.size());
    }

    /**
     * Tick the hologram system - update dynamic content.
     */
    public void tick(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        if (!Config.NEXUS_HOLOGRAMS_ENABLED.get()) {
            return;
        }

        // Periodic cache eviction to prevent memory leaks
        evictionTick++;
        if (evictionTick >= CACHE_EVICTION_INTERVAL) {
            evictionTick = 0;
            evictExpiredCacheEntries();
        }

        updateTick++;
        int updateInterval = Config.NEXUS_HOLOGRAM_UPDATE_INTERVAL.get();
        if (updateTick < updateInterval) {
            return;
        }
        updateTick = 0;

        // Update leaderboard
        updateLeaderboard(level, hubOrigin);
    }

    /**
     * Evict expired entries from leaderboard cache to prevent memory leaks.
     */
    private void evictExpiredCacheEntries() {
        int before = leaderboardCache.size();
        leaderboardCache.entrySet().removeIf(entry -> entry.getValue().isExpired(LEADERBOARD_CACHE_TTL_MS));
        int evicted = before - leaderboardCache.size();
        if (evicted > 0) {
            LOGGER.debug("[NexusHolograms] Evicted {} expired leaderboard cache entries", evicted);
        }
    }

    /**
     * Update the leaderboard with latest data.
     */
    private void updateLeaderboard(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        // Get leaderboard data from telemetry
        List<LeaderboardEntry> entries = fetchLeaderboardData();

        if (entries.isEmpty()) {
            return;
        }

        // Build new lines
        List<Component> lines = new ArrayList<>();
        lines.add(styled("TOP WAVE RECORDS", DesignTokens.Nexus.LEADERBOARD_GOLD, true));
        lines.add(Component.empty());

        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            LeaderboardEntry entry = entries.get(i);
            int color = i == 0 ? DesignTokens.Nexus.MEDAL_GOLD
                : (i == 1 ? DesignTokens.Nexus.MEDAL_SILVER
                    : (i == 2 ? DesignTokens.Nexus.MEDAL_BRONZE : DesignTokens.Nexus.SUBTITLE_GRAY));
            lines.add(styled("#" + (i + 1) + " " + entry.name + " - Wave " + entry.wave, color, false));
        }

        lines.add(Component.empty());
        lines.add(styled("Updated every 30s", DesignTokens.Nexus.FOOTER_GRAY, false));

        // Update the display - recreate for simplicity
        updateHologramContent(level, hubOrigin, HologramType.LEADERBOARD, lines);
    }

    /**
     * Fetch leaderboard data from telemetry service.
     */
    @Nonnull
    private List<LeaderboardEntry> fetchLeaderboardData() {
        List<LeaderboardEntry> entries = new ArrayList<>();

        // Return cached data sorted by wave
        entries.addAll(leaderboardCache.values());
        entries.sort((a, b) -> Integer.compare(b.wave, a.wave));

        return entries;
    }

    /**
     * Update hologram content by recreating entities.
     */
    private void updateHologramContent(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin,
                                        @Nonnull HologramType type, @Nonnull List<Component> lines) {
        List<UUID> entityIds = holograms.get(type);
        if (entityIds == null) {
            return;
        }

        // Remove old entities
        for (UUID entityId : entityIds) {
            Entity entity = level.getEntity(nn(entityId, "entityId"));
            if (entity != null) {
                entity.discard();
            }
        }
        entityIds.clear();

        // Create new hologram with updated content
        BlockPos pos = nn(offset(hubOrigin, type.offset()), "pos");
        Vec3 spawnPos = nn(Vec3.atCenterOf(pos), "spawnPos");
        createHologram(level, nn(spawnPos, "spawnPos"), lines, type);
    }

    /**
     * Add a server announcement.
     */
    public void addAnnouncement(@Nonnull String message) {
        Objects.requireNonNull(message, "message");
        announcements.add(0, message); // Add to front
        if (announcements.size() > 5) {
            announcements.remove(announcements.size() - 1);
        }
    }

    /**
     * Update leaderboard entry.
     */
    public void updateLeaderboardEntry(@Nonnull String playerName, int wave) {
        LeaderboardEntry existing = leaderboardCache.get(playerName);
        if (existing == null || existing.wave < wave) {
            leaderboardCache.put(playerName, new LeaderboardEntry(playerName, wave));

            // Evict lowest-scoring entries if over limit
            if (leaderboardCache.size() > MAX_LEADERBOARD_ENTRIES) {
                trimLeaderboardCache();
            }
        }
    }

    /**
     * Trim leaderboard cache to MAX_LEADERBOARD_ENTRIES, keeping highest wave entries.
     */
    private void trimLeaderboardCache() {
        List<Map.Entry<String, LeaderboardEntry>> entries = new ArrayList<>(leaderboardCache.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().wave(), a.getValue().wave()));

        Set<String> toKeep = new HashSet<>();
        for (int i = 0; i < MAX_LEADERBOARD_ENTRIES && i < entries.size(); i++) {
            toKeep.add(entries.get(i).getKey());
        }
        leaderboardCache.keySet().retainAll(toKeep);
        LOGGER.debug("[NexusHolograms] Trimmed leaderboard cache to {} entries", leaderboardCache.size());
    }

    /**
     * Clean up all hologram entities.
     */
    public void cleanup(@Nonnull ServerLevel level) {
        for (List<UUID> entityIds : holograms.values()) {
            for (UUID entityId : entityIds) {
                Entity entity = level.getEntity(nn(entityId, "entityId"));
                if (entity != null) {
                    entity.discard();
                }
            }
            entityIds.clear();
        }

        // Also scan for any orphaned holograms
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains(HOLOGRAM_TAG)) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            entity.discard();
        }

        // Clear caches to prevent memory leaks
        leaderboardCache.clear();
        announcements.clear();

        LOGGER.debug("[NexusHolograms] Cleaned up hologram entities and caches");
    }

    // Helper methods for styled text

    private static Component styled(String text, int color, boolean bold) {
        Style style = nn(nn(Style.EMPTY, "Style.EMPTY").withColor(color), "style");
        if (bold) {
            style = nn(style.withBold(true), "boldStyle");
        }
        return nn(nn(Component.literal(nn(text, "text")), "component").withStyle(nn(style, "style")), "styledComponent");
    }

    private static Component zoneStatus(String zoneName, int zoneColor, String status) {
        return nn(nn(Component.literal(""), "base")
            .append(nn(nn(Component.literal(nn(zoneName, "zoneName")), "zoneNameComponent").withStyle(nn(nn(Style.EMPTY, "Style.EMPTY").withColor(zoneColor), "zoneStyle")), "zoneAppend"))
            .append(nn(nn(Component.literal(": "), "colonComponent").withStyle(nn(nn(Style.EMPTY, "Style.EMPTY").withColor(DesignTokens.Nexus.PLACEHOLDER_GRAY), "colonStyle")), "colonAppend"))
            .append(nn(nn(Component.literal(nn(status, "status")), "statusComponent").withStyle(nn(nn(Style.EMPTY, "Style.EMPTY").withColor(DesignTokens.Nexus.HINT_GREEN), "statusStyle")), "statusAppend")), "zoneStatusComponent");
    }

    /**
     * Leaderboard entry record with timestamp for TTL expiration.
     */
    public record LeaderboardEntry(String name, int wave, long timestamp) {
        public LeaderboardEntry(String name, int wave) {
            this(name, wave, System.currentTimeMillis());
        }

        /**
         * Check if this entry has expired based on TTL.
         */
        public boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    private static BlockPos offset(BlockPos origin, Offset offset) {
        return origin.offset(offset.x(), offset.y(), offset.z());
    }

    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
