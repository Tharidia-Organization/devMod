package com.devmod.hologram.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import com.devmod.config.Config;
import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramRegistry;
import com.devmod.hologram.data.HologramStyle;

/**
 * Manages hologram lifecycle: spawning, updating, and despawning TextDisplay entities.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spawn TextDisplay entities from HologramDefinition</li>
 *   <li>Update hologram content (with placeholder resolution)</li>
 *   <li>Handle tick updates for dynamic content</li>
 *   <li>Despawn and cleanup entities</li>
 * </ul>
 */
public final class HologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramManager.class);

    public static final HologramManager INSTANCE = new HologramManager();

    /**
     * Tag for identifying Hologram Builder entities (distinct from legacy).
     */
    public static final String HOLOGRAM_TAG = "devmod_hologram_v2";

    // NBT keys for TextDisplay
    private static final String NBT_TEXT = "text";
    private static final String NBT_LINE_WIDTH = "line_width";
    private static final String NBT_BACKGROUND = "background";
    private static final String NBT_TEXT_OPACITY = "text_opacity";
    private static final String NBT_BILLBOARD = "billboard";
    private static final String NBT_VIEW_RANGE = "view_range";
    private static final String NBT_SHADOW = "shadow";
    private static final String NBT_SEE_THROUGH = "see_through";

    /** Default line width for TextDisplay (in pixels). Controls text wrapping. */
    private static final int DEFAULT_LINE_WIDTH = 300;

    /** View range multiplier for see-through holograms (multiplied by 64 blocks base). */
    private static final float VIEW_RANGE_SEE_THROUGH = 1.0f;

    /** View range multiplier for normal holograms (multiplied by 64 blocks base). */
    private static final float VIEW_RANGE_NORMAL = 0.75f;

    /**
     * Tick counter for scheduling hologram content refreshes.
     * Masked to positive range to avoid negative modulo results on overflow.
     */
    private int tickCounter = 0;

    private HologramManager() {}

    /**
     * Tick the hologram manager.
     * Updates dynamic content for all holograms in the level.
     *
     * @param level The server level
     */
    public void tick(@Nonnull ServerLevel level) {
        if (!Config.NEXUS_HOLOGRAMS_ENABLED.get()) {
            return;
        }

        // Bitmask keeps tickCounter non-negative, preventing negative modulo on int overflow
        tickCounter = (tickCounter + 1) & Integer.MAX_VALUE;

        MinecraftServer server = Objects.requireNonNull(level.getServer(), "server");

        HologramRegistry registry = HologramRegistry.get(server);
        ResourceLocation dimension = Objects.requireNonNull(level.dimension().location(), "dimension");

        // Get holograms in this dimension
        List<HologramDefinition> holograms = registry.getHologramsInDimension(dimension);

        for (HologramDefinition def : holograms) {
            if (!def.options().enabled()) {
                continue;
            }

            // Check if it's time to update this hologram
            int refreshInterval = def.options().getEffectiveRefreshInterval();
            if (tickCounter % refreshInterval != 0) {
                continue;
            }

            // Only update dynamic holograms
            if (def.type().supportsDynamicContent()) {
                updateHologramContent(level, registry, def);
            }
        }
    }

    /**
     * Spawns a new hologram TextDisplay entity.
     *
     * @param level The server level
     * @param def   The hologram definition
     * @return The spawned entity, or null if failed
     */
    @Nullable
    public Display.TextDisplay spawnHologram(@Nonnull ServerLevel level, @Nonnull HologramDefinition def) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(def, "def");

        try {
            Display.TextDisplay display = new Display.TextDisplay(
                Objects.requireNonNull(EntityType.TEXT_DISPLAY), level);

            Vec3 pos = def.position().getExactPosition();
            display.setPos(pos.x, pos.y, pos.z);
            display.setYRot(def.position().yaw());
            display.setXRot(def.position().pitch());

            // Apply content and style
            applyContent(display, def, level);
            applyStyle(display, Objects.requireNonNull(def.style(), "style"));

            // Add identification tag
            UUID hologramId = Objects.requireNonNull(def.id(), "hologramId");
            display.addTag(HOLOGRAM_TAG);
            display.addTag("devmod_hologram_" + hologramId.toString().replace("-", ""));

            // Spawn entity
            level.addFreshEntity(display);

            // Cache entity reference
            HologramRegistry registry = HologramRegistry.get(Objects.requireNonNull(level.getServer()));
            registry.cacheEntity(hologramId, display);

            // Spawn particles
            HologramParticles.spawnSpawnEffect(level, pos);

            LOGGER.debug("[Hologram] Spawned hologram {} at {}", def.id(), pos);
            return display;

        } catch (Exception e) {
            LOGGER.error("[Hologram] Failed to spawn hologram {}: {}", def.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Updates the content of an existing hologram.
     */
    private void updateHologramContent(
        @Nonnull ServerLevel level,
        @Nonnull HologramRegistry registry,
        @Nonnull HologramDefinition def
    ) {
        UUID hologramId = Objects.requireNonNull(def.id(), "hologramId");
        Optional<Display.TextDisplay> cachedEntity = registry.getCachedEntity(hologramId);

        if (cachedEntity.isPresent()) {
            Display.TextDisplay display = cachedEntity.get();
            if (display.isAlive()) {
                applyContent(display, def, level);
                return;
            }
            registry.removeCachedEntity(hologramId);
        }

        // Entity not found or dead, respawn
        respawnHologram(level, def);
    }

    /**
     * Respawns a hologram entity that was lost.
     */
    private void respawnHologram(
        @Nonnull ServerLevel level,
        @Nonnull HologramDefinition def
    ) {
        LOGGER.debug("[Hologram] Respawning lost hologram {} at {}",
            def.id(), def.position().toShortString());
        spawnHologram(level, def);
    }

    /**
     * Despawns a hologram entity.
     *
     * @param level      The server level
     * @param hologramId The hologram ID
     */
    public void despawnHologram(@Nonnull ServerLevel level, @Nonnull UUID hologramId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hologramId, "hologramId");

        HologramRegistry registry = HologramRegistry.get(Objects.requireNonNull(level.getServer()));
        Optional<Display.TextDisplay> cachedEntity = registry.getCachedEntity(hologramId);

        if (cachedEntity.isPresent()) {
            Display.TextDisplay display = cachedEntity.get();
            if (display.isAlive()) {
                Vec3 pos = display.position();
                HologramParticles.spawnRemoveEffect(level, pos);
                display.discard();
            }
            registry.removeCachedEntity(hologramId);
        }

        LOGGER.debug("[Hologram] Despawned hologram {}", hologramId);
    }

    /**
     * Applies content to a TextDisplay entity.
     */
    private void applyContent(
        @Nonnull Display.TextDisplay display,
        @Nonnull HologramDefinition def,
        @Nonnull ServerLevel level
    ) {
        List<String> lines = def.lines();

        // Resolve placeholders for dynamic types
        if (def.type().supportsDynamicContent()) {
            lines = lines.stream()
                .map(line -> HologramPlaceholderResolver.resolve(
                    Objects.requireNonNull(line, "line"), level, def.position().blockPos()))
                .toList();
        }

        // Build combined text component
        MutableComponent combined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                combined.append("\n");
            }
            combined.append(parseColorCodes(Objects.requireNonNull(lines.get(i), "line")));
        }

        // Apply via NBT
        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);

        String textJson = Objects.requireNonNull(
            Component.Serializer.toJson(
                Objects.requireNonNull(combined), Objects.requireNonNull(level.registryAccess())),
            "textJson");
        nbt.putString(NBT_TEXT, textJson);

        display.load(nbt);
    }

    /**
     * Applies style to a TextDisplay entity.
     */
    private void applyStyle(
        @Nonnull Display.TextDisplay display,
        @Nonnull HologramStyle style
    ) {
        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);

        // Billboard mode
        nbt.putString(NBT_BILLBOARD, Objects.requireNonNull(style.billboard().getNbtValue()));

        // View range (multiplier of 64 blocks)
        float viewRangeMultiplier = style.seeThrough() ? VIEW_RANGE_SEE_THROUGH : VIEW_RANGE_NORMAL;
        nbt.putFloat(NBT_VIEW_RANGE, viewRangeMultiplier);

        // Line width
        nbt.putInt(NBT_LINE_WIDTH, DEFAULT_LINE_WIDTH);

        // Background color
        nbt.putInt(NBT_BACKGROUND, style.backgroundColor());

        // Text opacity (full = -1)
        nbt.putByte(NBT_TEXT_OPACITY, (byte) -1);

        // Shadow
        nbt.putBoolean(NBT_SHADOW, style.shadow());

        // See through
        nbt.putBoolean(NBT_SEE_THROUGH, style.seeThrough());

        display.load(nbt);
    }

    /**
     * Parses Minecraft color codes (section sign format) in a string.
     */
    @Nonnull
    private Component parseColorCodes(@Nonnull String text) {
        // Simple color code parsing - handles section sign format
        if (!text.contains("\u00A7")) {
            return Objects.requireNonNull(Component.literal(text));
        }

        MutableComponent result = Component.empty();
        StringBuilder current = new StringBuilder();
        Style currentStyle = Objects.requireNonNull(Style.EMPTY);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length()) {
                // Flush current text
                if (current.length() > 0) {
                    result.append(Objects.requireNonNull(
                        Component.literal(Objects.requireNonNull(current.toString()))
                            .withStyle(currentStyle)));
                    current.setLength(0);
                }

                char code = text.charAt(++i);
                currentStyle = applyColorCode(currentStyle, code);
            } else {
                current.append(c);
            }
        }

        // Flush remaining text
        if (current.length() > 0) {
            result.append(Objects.requireNonNull(
                Component.literal(Objects.requireNonNull(current.toString()))
                    .withStyle(currentStyle)));
        }

        return Objects.requireNonNull(result);
    }

    /**
     * Applies a color code to a style.
     */
    @Nonnull
    private Style applyColorCode(@Nonnull Style style, char code) {
        return Objects.requireNonNull(switch (code) {
            case '0' -> style.withColor(0x000000);
            case '1' -> style.withColor(0x0000AA);
            case '2' -> style.withColor(0x00AA00);
            case '3' -> style.withColor(0x00AAAA);
            case '4' -> style.withColor(0xAA0000);
            case '5' -> style.withColor(0xAA00AA);
            case '6' -> style.withColor(0xFFAA00);
            case '7' -> style.withColor(0xAAAAAA);
            case '8' -> style.withColor(0x555555);
            case '9' -> style.withColor(0x5555FF);
            case 'a' -> style.withColor(0x55FF55);
            case 'b' -> style.withColor(0x55FFFF);
            case 'c' -> style.withColor(0xFF5555);
            case 'd' -> style.withColor(0xFF55FF);
            case 'e' -> style.withColor(0xFFFF55);
            case 'f' -> style.withColor(0xFFFFFF);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderlined(true);
            case 'o' -> style.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> style;
        });
    }

    /**
     * Initializes all holograms in a level on server start.
     */
    public void initializeLevel(@Nonnull ServerLevel level) {
        MinecraftServer server = Objects.requireNonNull(level.getServer(), "server");

        HologramRegistry registry = HologramRegistry.get(server);
        ResourceLocation dimension = Objects.requireNonNull(level.dimension().location(), "dimension");
        List<HologramDefinition> holograms = registry.getHologramsInDimension(dimension);

        LOGGER.info("[Hologram] Initializing {} holograms in {}", holograms.size(), dimension);

        for (HologramDefinition def : holograms) {
            if (def.options().enabled()) {
                spawnHologram(level, def);
            }
        }
    }

    /**
     * Cleans up all holograms in a level on server stop.
     */
    public void cleanupLevel(@Nonnull ServerLevel level) {
        MinecraftServer server = Objects.requireNonNull(level.getServer(), "server");

        HologramRegistry registry = HologramRegistry.get(server);
        registry.clearEntityCache();

        LOGGER.debug("[Hologram] Cleaned up hologram cache for {}", level.dimension().location());
    }
}
