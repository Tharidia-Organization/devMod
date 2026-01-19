package com.devmod.hologram.runtime;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.runtime.ZoneResolver;

/**
 * Resolves placeholders in hologram content (Regola 8 - Bibbia Estetica).
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{player} - Name of nearest player (refresh: 20 ticks)</li>
 *   <li>{online} - Players online (refresh: 100 ticks)</li>
 *   <li>{tps} - Server TPS (refresh: 100 ticks)</li>
 *   <li>{time} - World time HH:MM (refresh: 20 ticks)</li>
 *   <li>{date} - Real date DD/MM/YY (refresh: 1200 ticks)</li>
 *   <li>{dimension} - Dimension name (refresh: never)</li>
 *   <li>{zone} - Nexus zone name (refresh: 20 ticks)</li>
 *   <li>{pos} - Hologram position (refresh: never)</li>
 * </ul>
 */
public final class HologramPlaceholderResolver {
    private HologramPlaceholderResolver() {}

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z_]+)}");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy");

    // Cached values for expensive operations
    private static final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    private static final int CACHE_TTL_MEDIUM = 100;  // 5 seconds

    /**
     * Resolves all placeholders in a line of text.
     *
     * @param line  The line containing placeholders
     * @param level The level for context
     * @param pos   The hologram position
     * @return The line with placeholders resolved
     */
    @Nonnull
    public static String resolve(@Nonnull String line, @Nullable Level level, @Nullable BlockPos pos) {
        Objects.requireNonNull(line, "line");

        if (!line.contains("{")) {
            return line;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(line);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolvePlaceholder(placeholder, level, pos);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a single placeholder.
     */
    @Nonnull
    private static String resolvePlaceholder(
        @Nonnull String placeholder,
        @Nullable Level level,
        @Nullable BlockPos pos
    ) {
        return switch (placeholder) {
            case "player" -> resolveNearestPlayer(level, pos);
            case "online" -> resolveOnlinePlayers(level);
            case "tps" -> resolveTps(level);
            case "time" -> resolveWorldTime(level);
            case "date" -> resolveRealDate();
            case "dimension" -> resolveDimension(level);
            case "zone" -> resolveZone(level, pos);
            case "pos" -> resolvePosition(pos);
            default -> "{" + placeholder + "}"; // Keep unknown placeholders
        };
    }

    /**
     * Resolves {player} - nearest player name.
     */
    @Nonnull
    private static String resolveNearestPlayer(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return "---";
        }

        Vec3 center = Vec3.atCenterOf(pos);
        AABB searchBox = new AABB(center.subtract(32, 32, 32), center.add(32, 32, 32));

        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Player player : level.players()) {
            double distSq = player.distanceToSqr(center);
            if (distSq < nearestDistSq && searchBox.contains(player.position())) {
                nearest = player;
                nearestDistSq = distSq;
            }
        }

        return nearest != null ? nearest.getName().getString() : "---";
    }

    /**
     * Resolves {online} - online player count.
     */
    @Nonnull
    private static String resolveOnlinePlayers(@Nullable Level level) {
        if (level == null) {
            return "0";
        }

        String cacheKey = "online";
        CachedValue cached = cache.get(cacheKey);
        long gameTime = level.getGameTime();

        if (cached != null && gameTime - cached.timestamp < CACHE_TTL_MEDIUM) {
            return cached.value;
        }

        int count = 0;
        if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            count = server.getPlayerCount();
        }

        String value = String.valueOf(count);
        cache.put(cacheKey, new CachedValue(value, gameTime));
        return value;
    }

    /**
     * Resolves {tps} - server TPS.
     */
    @Nonnull
    private static String resolveTps(@Nullable Level level) {
        if (level == null) {
            return "20.0";
        }

        String cacheKey = "tps";
        CachedValue cached = cache.get(cacheKey);
        long gameTime = level.getGameTime();

        if (cached != null && gameTime - cached.timestamp < CACHE_TTL_MEDIUM) {
            return cached.value;
        }

        double tps = 20.0;
        if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            // Calculate TPS from average tick time
            double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
            tps = Math.min(20.0, 1000.0 / Math.max(mspt, 50.0));
        }

        String value = String.format("%.1f", tps);
        cache.put(cacheKey, new CachedValue(value, gameTime));
        return value;
    }

    /**
     * Resolves {time} - world time in HH:MM format.
     */
    @Nonnull
    private static String resolveWorldTime(@Nullable Level level) {
        if (level == null) {
            return "00:00";
        }

        long dayTime = level.getDayTime() % 24000;
        int hours = (int) ((dayTime / 1000 + 6) % 24);
        int minutes = (int) ((dayTime % 1000) * 60 / 1000);

        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Resolves {date} - real world date.
     */
    @Nonnull
    private static String resolveRealDate() {
        String cacheKey = "date";
        CachedValue cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();

        // Cache for 1 minute
        if (cached != null && now - cached.realTimestamp < 60_000) {
            return cached.value;
        }

        String value = LocalDateTime.now(ZoneId.systemDefault()).format(DATE_FORMATTER);
        cache.put(cacheKey, new CachedValue(value, 0, now));
        return value;
    }

    /**
     * Resolves {dimension} - dimension name.
     */
    @Nonnull
    private static String resolveDimension(@Nullable Level level) {
        if (level == null) {
            return "unknown";
        }

        String dimId = level.dimension().location().toString();
        // Extract readable name
        if (dimId.contains(":")) {
            dimId = dimId.substring(dimId.lastIndexOf(':') + 1);
        }
        // Convert underscores to spaces and capitalize
        return dimId.replace('_', ' ');
    }

    /**
     * Resolves {zone} - Nexus zone name.
     */
    @Nonnull
    private static String resolveZone(@Nullable Level level, @Nullable BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return "Hub";
        }
        if (!serverLevel.dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return "Hub";
        }

        MinecraftServer server = serverLevel.getServer();
        if (server != null) {
            NexusHubManager.INSTANCE.initialize(server);
        }

        return ZoneResolver.INSTANCE.resolve(serverLevel, pos)
            .map(ZoneDefinition::displayName)
            .orElse("Hub");
    }

    /**
     * Resolves {pos} - hologram position.
     */
    @Nonnull
    private static String resolvePosition(@Nullable BlockPos pos) {
        if (pos == null) {
            return "0, 0, 0";
        }
        return pos.toShortString();
    }

    /**
     * Clears the placeholder cache.
     * Call this when the server shuts down or world changes.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Cached value with timestamp.
     */
    private record CachedValue(String value, long timestamp, long realTimestamp) {
        CachedValue(String value, long timestamp) {
            this(value, timestamp, 0);
        }
    }
}
