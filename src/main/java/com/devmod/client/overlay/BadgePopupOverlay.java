package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.endurance.GamificationManager.BadgeRarity;

/**
 * Premium badge unlock popup overlay with advanced animations.
 * Redesigned for a compact horizontal layout (~4-5% screen height).
 *
 * Features:
 * - Compact horizontal layout (320x45px)
 * - Progressive visual effects per rarity
 * - Smooth elastic animations
 * - Entry flash for RARE+
 * - Pulsing glow effects
 * - Floating particles for EPIC/LEGENDARY
 * - Sound effects with debug logging
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class BadgePopupOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "badge_popup");

    // Default timing configuration (ms) - can be overridden by config
    private static final long DEFAULT_POPUP_DURATION_MS = 4000;
    private static final long DEFAULT_SLIDE_IN_MS = 350;
    private static final long DEFAULT_FADE_OUT_MS = 500;
    private static final long BOUNCE_DURATION_MS = 180;
    private static final long FLASH_DURATION_MS = 150;

    // New compact horizontal layout dimensions
    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 45;
    private static final int DEFAULT_FINAL_Y = 12;
    private static final int START_Y = -BOX_HEIGHT - 20;

    // Layout constants
    private static final int ICON_SIZE = 28;
    private static final int TAG_WIDTH = 75;
    private static final int PADDING_H = 10;

    // Glow layers per rarity
    private static final int[] GLOW_LAYERS = {0, 1, 2, 3, 5}; // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY

    // Config getters with safe defaults
    private static long getPopupDuration() {
        try {
            return Config.BADGE_POPUP_DURATION_MS.get();
        } catch (Exception e) {
            return DEFAULT_POPUP_DURATION_MS;
        }
    }

    private static long getSlideInMs() {
        try {
            return Config.BADGE_POPUP_SLIDE_IN_MS.get();
        } catch (Exception e) {
            return DEFAULT_SLIDE_IN_MS;
        }
    }

    private static long getFadeOutMs() {
        try {
            return Config.BADGE_POPUP_FADE_OUT_MS.get();
        } catch (Exception e) {
            return DEFAULT_FADE_OUT_MS;
        }
    }

    private static int getFinalY() {
        try {
            return Config.BADGE_POPUP_Y_POSITION.get();
        } catch (Exception e) {
            return DEFAULT_FINAL_Y;
        }
    }

    private static boolean isEnabled() {
        try {
            return Config.BADGE_POPUP_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isSoundEnabled() {
        try {
            return Config.BADGE_POPUP_SOUND_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static float getSoundVolume() {
        try {
            return Config.BADGE_POPUP_SOUND_VOLUME.get().floatValue();
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private static boolean isParticlesEnabled() {
        try {
            return Config.BADGE_POPUP_PARTICLES_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isGlowEnabled() {
        try {
            return Config.BADGE_POPUP_GLOW_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    // Particle system
    private static final List<Particle> particles = new ArrayList<>();
    private static final Random random = new Random();
    private static final int MAX_PARTICLES = 40;

    private static final Queue<BadgePopup> popupQueue = new LinkedList<>();
    private static boolean soundPlayed = false;
    private static long flashStartTime = 0;

    public record BadgePopup(String name, BadgeRarity rarity, long startTime) {
        public BadgePopup {
            Objects.requireNonNull(name, "Badge name cannot be null");
            Objects.requireNonNull(rarity, "Badge rarity cannot be null");
        }
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        float alpha;
        float decay;
        int color;
        long createdAt;
        ParticleType type;

        enum ParticleType { SPARK, FLOAT, BURST }

        Particle(float x, float y, int color, ParticleType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.color = color;
            this.createdAt = System.currentTimeMillis();

            switch (type) {
                case SPARK -> {
                    this.vx = (random.nextFloat() - 0.5f) * 4f;
                    this.vy = -random.nextFloat() * 1.5f - 0.5f;
                    this.size = random.nextFloat() * 2f + 1.5f;
                    this.alpha = 1f;
                    this.decay = random.nextFloat() * 0.03f + 0.02f;
                }
                case FLOAT -> {
                    this.vx = (random.nextFloat() - 0.5f) * 1f;
                    this.vy = -random.nextFloat() * 0.8f - 0.3f;
                    this.size = random.nextFloat() * 3f + 2f;
                    this.alpha = 0.8f;
                    this.decay = random.nextFloat() * 0.015f + 0.008f;
                }
                case BURST -> {
                    float angle = random.nextFloat() * (float) Math.PI * 2;
                    float speed = random.nextFloat() * 5f + 2f;
                    this.vx = (float) Math.cos(angle) * speed;
                    this.vy = (float) Math.sin(angle) * speed;
                    this.size = random.nextFloat() * 4f + 2f;
                    this.alpha = 1f;
                    this.decay = random.nextFloat() * 0.04f + 0.03f;
                }
            }
        }

        void update() {
            x += vx;
            y += vy;
            if (type == ParticleType.SPARK || type == ParticleType.BURST) {
                vy += 0.08f; // Gravity
                vx *= 0.98f;
            }
            alpha -= decay;
            size *= 0.97f;
        }

        boolean isDead() {
            return alpha <= 0 || System.currentTimeMillis() - createdAt > 1500;
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        ResourceLocation titleLayer = Objects.requireNonNull(VanillaGuiLayers.TITLE, "TITLE layer is null");
        ResourceLocation layerId = Objects.requireNonNull(LAYER_ID, "LAYER_ID is null");
        event.registerAbove(
            titleLayer,
            layerId,
            BadgePopupOverlay::render
        );
    }

    /**
     * Show a badge unlock popup. Called from network handler.
     */
    public static void showBadge(String badgeName, String rarityName) {
        BadgeRarity rarity = parseRarity(rarityName);
        popupQueue.add(new BadgePopup(badgeName, rarity, System.currentTimeMillis()));
        soundPlayed = false;
        flashStartTime = 0;
    }

    /**
     * Show a badge unlock popup with enum rarity.
     */
    public static void showBadge(String badgeName, BadgeRarity rarity) {
        popupQueue.add(new BadgePopup(badgeName, rarity, System.currentTimeMillis()));
        soundPlayed = false;
        flashStartTime = 0;
    }

    private static BadgeRarity parseRarity(String name) {
        if (name == null) return BadgeRarity.COMMON;
        try {
            return BadgeRarity.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BadgeRarity.COMMON;
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Check if popup is enabled
        if (!isEnabled()) {
            popupQueue.clear();
            particles.clear();
            return;
        }

        BadgePopup current = popupQueue.peek();
        if (current == null) {
            particles.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        if (font == null) return;

        long popupDuration = getPopupDuration();
        long fadeOutMs = getFadeOutMs();
        long slideInMs = getSlideInMs();
        int finalY = getFinalY();

        long elapsed = System.currentTimeMillis() - current.startTime();
        if (elapsed > popupDuration) {
            popupQueue.poll();
            soundPlayed = false;
            flashStartTime = 0;
            particles.clear();
            return;
        }

        // Play sound on first frame
        if (!soundPlayed && isSoundEnabled()) {
            playUnlockSound(current.rarity());
            soundPlayed = true;
            // Start flash for RARE+
            if (current.rarity().ordinal() >= BadgeRarity.RARE.ordinal()) {
                flashStartTime = System.currentTimeMillis();
            }
        }

        int screenWidth = graphics.guiWidth();
        int centerX = screenWidth / 2;

        // Calculate animation progress
        float slideProgress = calculateElasticSlideProgress(elapsed, slideInMs);
        float alpha = calculateAlpha(elapsed, slideInMs, fadeOutMs, popupDuration);
        float bounceOffset = calculateBounce(elapsed, slideInMs);

        // Current Y position with slide + bounce
        int currentY = (int) Mth.lerp(slideProgress, START_Y, finalY) + (int) bounceOffset;
        int x = centerX - BOX_WIDTH / 2;

        // Skip if not visible
        if (alpha < 0.01f) return;

        // Entry flash effect for RARE+
        if (flashStartTime > 0) {
            long flashElapsed = System.currentTimeMillis() - flashStartTime;
            if (flashElapsed < FLASH_DURATION_MS) {
                float flashAlpha = 1f - (flashElapsed / (float) FLASH_DURATION_MS);
                flashAlpha = flashAlpha * flashAlpha; // Ease out
                int flashIntensity = (int) (flashAlpha * 180);
                int flashColor = (flashIntensity << 24) | 0xFFFFFF;
                graphics.fill(x - 10, currentY - 10, x + BOX_WIDTH + 10, currentY + BOX_HEIGHT + 10, flashColor);

                // Burst particles on flash start
                if (flashElapsed < 50 && isParticlesEnabled() && current.rarity() == BadgeRarity.LEGENDARY) {
                    for (int i = 0; i < 15; i++) {
                        particles.add(new Particle(centerX, currentY + BOX_HEIGHT / 2f, current.rarity().color, Particle.ParticleType.BURST));
                    }
                }
            }
        }

        // Update and spawn particles
        if (isParticlesEnabled()) {
            updateParticles();
            spawnParticlesForRarity(x, currentY, current.rarity(), elapsed, popupDuration - fadeOutMs);
        }

        // Render particles behind popup
        renderParticles(graphics, alpha);

        // Render glow effect based on rarity
        if (isGlowEnabled()) {
            int glowLayers = GLOW_LAYERS[current.rarity().ordinal()];
            if (glowLayers > 0) {
                renderGlow(graphics, x, currentY, BOX_WIDTH, BOX_HEIGHT, current.rarity().color, alpha, elapsed, glowLayers);
            }
        }

        // Render main popup background
        renderBackground(graphics, x, currentY, BOX_WIDTH, BOX_HEIGHT, alpha, current.rarity(), elapsed);

        // Render border with shimmer
        renderBorder(graphics, x, currentY, BOX_WIDTH, BOX_HEIGHT, current.rarity(), alpha, elapsed);

        // Render content (icon, text, tag)
        renderContent(graphics, font, x, currentY, current, alpha, elapsed);
    }

    private static float calculateElasticSlideProgress(long elapsed, long slideInMs) {
        if (elapsed >= slideInMs) return 1f;
        float t = elapsed / (float) slideInMs;
        // Elastic ease-out: overshoot slightly then settle
        float p = 0.4f;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
    }

    private static float calculateAlpha(long elapsed, long slideInMs, long fadeOutMs, long popupDuration) {
        if (elapsed < slideInMs / 2) {
            // Quick fade in during first half of slide
            float t = elapsed / (float) (slideInMs / 2);
            return t;
        } else if (elapsed > popupDuration - fadeOutMs) {
            // Fade out
            float t = (popupDuration - elapsed) / (float) fadeOutMs;
            return t * t; // Quadratic ease-out
        }
        return 1f;
    }

    private static float calculateBounce(long elapsed, long slideInMs) {
        if (elapsed < slideInMs) return 0;
        long bounceTime = elapsed - slideInMs;
        if (bounceTime > BOUNCE_DURATION_MS) return 0;

        float t = bounceTime / (float) BOUNCE_DURATION_MS;
        // Damped bounce
        return (float) Math.sin(t * Math.PI * 2.5) * (1f - t) * 6f;
    }

    private static void renderGlow(GuiGraphics graphics, int x, int y, int w, int h, int color, float alpha, long elapsed, int layers) {
        // Pulsing glow intensity
        float pulse = (float) (0.6f + 0.4f * Math.sin(elapsed / 180.0));
        int baseGlowAlpha = (int) (alpha * 50 * pulse);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Multi-layer glow
        for (int i = layers; i >= 1; i--) {
            int offset = i * 3;
            int layerAlpha = baseGlowAlpha / i;
            int layerColor = (layerAlpha << 24) | (r << 16) | (g << 8) | b;
            graphics.fill(x - offset, y - offset, x + w + offset, y + h + offset, layerColor);
        }
    }

    private static void renderBackground(GuiGraphics graphics, int x, int y, int w, int h, float alpha, BadgeRarity rarity, long elapsed) {
        int bgAlpha = (int) (alpha * 240);

        // Base dark background
        int baseColor = 0x12121A;
        int topColor = (bgAlpha << 24) | baseColor;

        // Slight gradient toward bottom
        int bottomAlpha = (int) (alpha * 220);
        int bottomColor = (bottomAlpha << 24) | 0x0A0A0F;

        graphics.fillGradient(x, y, x + w, y + h, topColor, bottomColor);

        // Rarity-colored accent stripe at the left
        int accentAlpha = (int) (alpha * 200);
        int accentColor = (accentAlpha << 24) | (rarity.color & 0x00FFFFFF);
        graphics.fill(x, y, x + 3, y + h, accentColor);

        // Subtle inner highlight at top
        int highlightAlpha = (int) (alpha * 30);
        int highlightColor = (highlightAlpha << 24) | 0xFFFFFF;
        graphics.fill(x + 3, y, x + w, y + 1, highlightColor);
    }

    private static void renderBorder(GuiGraphics graphics, int x, int y, int w, int h, BadgeRarity rarity, float alpha, long elapsed) {
        int borderAlpha = (int) (alpha * 180);
        int rarityColor = rarity.color & 0x00FFFFFF;
        int borderColor = (borderAlpha << 24) | rarityColor;

        // 1px border
        // Top
        graphics.fill(x, y, x + w, y + 1, borderColor);
        // Bottom
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        // Left (skip accent stripe area)
        graphics.fill(x, y, x + 1, y + h, borderColor);
        // Right
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Shimmer effect traveling along the top border
        float shimmerPos = (elapsed % 1500) / 1500f;
        int shimmerAlpha = (int) (alpha * 200);
        int shimmerWidth = 50;
        int shimmerX = x + (int) (shimmerPos * (w + shimmerWidth)) - shimmerWidth;

        if (shimmerX + shimmerWidth > x && shimmerX < x + w) {
            int clampedStart = Math.max(x, shimmerX);
            int clampedEnd = Math.min(x + w, shimmerX + shimmerWidth);
            // Gradient shimmer
            for (int sx = clampedStart; sx < clampedEnd; sx++) {
                float shimmerT = (sx - shimmerX) / (float) shimmerWidth;
                float shimmerIntensity = (float) Math.sin(shimmerT * Math.PI);
                int pixelAlpha = (int) (shimmerAlpha * shimmerIntensity);
                int pixelColor = (pixelAlpha << 24) | 0xFFFFFF;
                graphics.fill(sx, y, sx + 1, y + 1, pixelColor);
            }
        }
    }

    private static void renderContent(GuiGraphics graphics, Font font, int x, int y, BadgePopup popup, float alpha, long elapsed) {
        Font safeFont = Objects.requireNonNull(font, "Font cannot be null");
        int textAlpha = (int) (alpha * 255);

        // Icon area (left side after accent stripe)
        int iconX = x + PADDING_H + 3;
        int iconCenterY = y + BOX_HEIGHT / 2;
        renderBadgeIcon(graphics, iconX + ICON_SIZE / 2, iconCenterY, popup.rarity(), alpha, elapsed);

        // Text area (center)
        int textStartX = iconX + ICON_SIZE + 8;
        int tagStartX = x + BOX_WIDTH - TAG_WIDTH - PADDING_H;
        int textAreaWidth = tagStartX - textStartX - 5;

        // "BADGE UNLOCKED" label
        String label = "BADGE UNLOCKED";
        int labelColor = (textAlpha << 24) | 0xB0B0B0;
        graphics.drawString(safeFont, label, textStartX, y + 10, labelColor, false);

        // Badge name (in rarity color, truncated if needed)
        String badgeName = Objects.requireNonNull(popup.name(), "Badge name is null");
        int nameColor = (textAlpha << 24) | (popup.rarity().color & 0x00FFFFFF);

        // Truncate if too long
        if (safeFont.width(badgeName) > textAreaWidth) {
            while (safeFont.width(badgeName + "...") > textAreaWidth && badgeName.length() > 3) {
                badgeName = badgeName.substring(0, badgeName.length() - 1);
            }
            badgeName = badgeName + "...";
        }

        // Shadow for name
        int shadowAlpha = textAlpha / 3;
        int shadowColor = (shadowAlpha << 24) | 0x000000;
        graphics.drawString(safeFont, badgeName, textStartX + 1, y + 24 + 1, shadowColor, false);
        graphics.drawString(safeFont, badgeName, textStartX, y + 24, nameColor, false);

        // Rarity tag (right side)
        renderRarityTag(graphics, safeFont, tagStartX, y + (BOX_HEIGHT - 20) / 2, TAG_WIDTH, 20, popup.rarity(), alpha, elapsed);
    }

    private static void renderBadgeIcon(GuiGraphics graphics, int centerX, int centerY, BadgeRarity rarity, float alpha, long elapsed) {
        // Pulsing effect based on rarity
        float pulseSpeed = switch (rarity) {
            case LEGENDARY -> 100f;
            case EPIC -> 120f;
            case RARE -> 150f;
            default -> 200f;
        };
        float pulse = 1f + 0.15f * (float) Math.sin(elapsed / pulseSpeed);

        int iconAlpha = (int) (alpha * 255);
        int iconColor = (iconAlpha << 24) | (rarity.color & 0x00FFFFFF);

        // Star/diamond shape
        int baseSize = (int) (10 * pulse);

        // Outer glow for RARE+
        if (rarity.ordinal() >= BadgeRarity.RARE.ordinal()) {
            int glowAlpha = (int) (alpha * 80);
            int glowColor = (glowAlpha << 24) | (rarity.color & 0x00FFFFFF);
            int glowSize = baseSize + 4;
            graphics.fill(centerX - glowSize, centerY - 2, centerX + glowSize, centerY + 2, glowColor);
            graphics.fill(centerX - 2, centerY - glowSize, centerX + 2, centerY + glowSize, glowColor);
        }

        // Diamond shape
        graphics.fill(centerX - baseSize, centerY - 1, centerX + baseSize, centerY + 1, iconColor);
        graphics.fill(centerX - 1, centerY - baseSize, centerX + 1, centerY + baseSize, iconColor);

        // Inner diagonal parts
        int halfSize = baseSize / 2;
        graphics.fill(centerX - halfSize, centerY - halfSize / 2, centerX + halfSize, centerY + halfSize / 2, iconColor);
        graphics.fill(centerX - halfSize / 2, centerY - halfSize, centerX + halfSize / 2, centerY + halfSize, iconColor);

        // Bright core
        int coreAlpha = (int) (alpha * 255);
        int coreColor = (coreAlpha << 24) | 0xFFFFFF;
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, coreColor);

        // Extra sparkle for LEGENDARY
        if (rarity == BadgeRarity.LEGENDARY) {
            float rotationAngle = (elapsed / 50f) % 360;
            int sparkleOffset = 6;
            for (int i = 0; i < 4; i++) {
                float angle = (float) Math.toRadians(rotationAngle + i * 90);
                int sparkleX = centerX + (int) (Math.cos(angle) * sparkleOffset);
                int sparkleY = centerY + (int) (Math.sin(angle) * sparkleOffset);
                int sparkleAlpha = (int) (alpha * 200);
                int sparkleColor = (sparkleAlpha << 24) | 0xFFD700;
                graphics.fill(sparkleX - 1, sparkleY - 1, sparkleX + 1, sparkleY + 1, sparkleColor);
            }
        }
    }

    private static void renderRarityTag(GuiGraphics graphics, Font font, int x, int y, int w, int h, BadgeRarity rarity, float alpha, long elapsed) {
        Font safeFont = Objects.requireNonNull(font, "Font cannot be null");
        // Tag background
        int bgAlpha = (int) (alpha * 200);
        int rarityColor = rarity.color & 0x00FFFFFF;
        int bgColor = (bgAlpha << 24) | darkenColor(rarityColor, 0.4f);
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Tag border (same color as rarity)
        int borderAlpha = (int) (alpha * 220);
        int borderColor = (borderAlpha << 24) | rarityColor;
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Rarity text
        String rarityText = Objects.requireNonNull(
            Objects.requireNonNull(rarity.displayName, "Rarity displayName is null").toUpperCase(),
            "Rarity text is null"
        );
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | rarityColor;
        int textWidth = safeFont.width(rarityText);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(safeFont, rarityText, textX, textY, textColor, false);
    }

    private static int darkenColor(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static void spawnParticlesForRarity(int boxX, int boxY, BadgeRarity rarity, long elapsed, long activeEnd) {
        if (elapsed > activeEnd) return;
        if (particles.size() >= MAX_PARTICLES) return;

        float spawnChance = switch (rarity) {
            case LEGENDARY -> 0.4f;
            case EPIC -> 0.25f;
            case RARE -> 0.12f;
            default -> 0f;
        };

        if (random.nextFloat() < spawnChance) {
            Particle.ParticleType type = rarity == BadgeRarity.LEGENDARY ?
                (random.nextBoolean() ? Particle.ParticleType.SPARK : Particle.ParticleType.FLOAT) :
                (rarity == BadgeRarity.EPIC ? Particle.ParticleType.FLOAT : Particle.ParticleType.SPARK);

            // Spawn along box edges
            float x, y;
            if (random.nextBoolean()) {
                x = boxX + random.nextFloat() * BOX_WIDTH;
                y = random.nextBoolean() ? boxY : boxY + BOX_HEIGHT;
            } else {
                x = random.nextBoolean() ? boxX : boxX + BOX_WIDTH;
                y = boxY + random.nextFloat() * BOX_HEIGHT;
            }

            particles.add(new Particle(x, y, rarity.color, type));
        }
    }

    private static void updateParticles() {
        particles.removeIf(Particle::isDead);
        for (Particle p : particles) {
            p.update();
        }
    }

    private static void renderParticles(GuiGraphics graphics, float globalAlpha) {
        for (Particle p : particles) {
            int a = (int) (p.alpha * globalAlpha * 255);
            int r = (p.color >> 16) & 0xFF;
            int g = (p.color >> 8) & 0xFF;
            int b = p.color & 0xFF;
            int color = (a << 24) | (r << 16) | (g << 8) | b;

            int size = Math.max(1, (int) p.size);
            graphics.fill((int) p.x - size / 2, (int) p.y - size / 2,
                (int) p.x + size / 2, (int) p.y + size / 2, color);
        }
    }

    /**
     * Play unlock sound using vanilla Minecraft sounds.
     * Each rarity has a distinct sound combination for clear audio feedback.
     *
     * Sound design per rarity:
     * - COMMON: Simple note pling (subtle)
     * - UNCOMMON: Experience orb + higher pitch
     * - RARE: Level up sound (recognizable achievement)
     * - EPIC: Level up + amethyst chime (magical feel)
     * - LEGENDARY: Challenge complete + totem + chime (epic fanfare)
     */
    private static void playUnlockSound(BadgeRarity rarity) {
        Minecraft mc = Minecraft.getInstance();
        var soundManager = mc.getSoundManager();
        if (soundManager == null) {
            DevMod.LOGGER.warn("[BadgePopup] Sound manager is null, cannot play sound");
            return;
        }

        float vol = getSoundVolume();
        DevMod.LOGGER.debug("[BadgePopup] Playing sound for rarity: {} (volume: {})", rarity, vol);

        try {
            switch (rarity) {
                case COMMON -> {
                    // Simple, subtle pling
                    playSound(soundManager, SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.6f * vol);
                }
                case UNCOMMON -> {
                    // Experience orb pickup - satisfying collection sound
                    playSound(soundManager, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f * vol);
                    playSound(soundManager, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.5f * vol);
                }
                case RARE -> {
                    // Level up - classic achievement sound
                    playSound(soundManager, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.9f * vol);
                }
                case EPIC -> {
                    // Level up + amethyst shimmer for magical feel
                    playSound(soundManager, SoundEvents.PLAYER_LEVELUP, 1.1f, 1.0f * vol);
                    playSound(soundManager, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.7f * vol);
                    playSound(soundManager, SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.4f * vol);
                }
                case LEGENDARY -> {
                    // Full fanfare: challenge complete + totem + multiple chimes
                    playSound(soundManager, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f * vol);
                    playSound(soundManager, SoundEvents.TOTEM_USE, 0.8f, 0.5f * vol);
                    playSound(soundManager, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f * vol);
                    playSound(soundManager, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.7f * vol);
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[BadgePopup] Failed to play sound for {}: {}", rarity, e.getMessage());
            // Ultimate fallback - just play level up
            try {
                playSound(soundManager, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f * vol);
            } catch (Exception fallbackEx) {
                DevMod.LOGGER.error("[BadgePopup] Even fallback sound failed: {}", fallbackEx.getMessage());
            }
        }
    }

    /**
     * Helper to play a sound with null safety.
     */
    private static void playSound(net.minecraft.client.sounds.SoundManager soundManager, SoundEvent sound, float pitch, float volume) {
        if (sound == null || soundManager == null) return;
        SimpleSoundInstance instance = Objects.requireNonNull(
            SimpleSoundInstance.forUI(sound, pitch, volume),
            "Sound instance cannot be null"
        );
        soundManager.play(instance);
    }

    // ===========================================
    // TEST UTILITIES - For QA Testing
    // ===========================================

    /**
     * Test a specific badge rarity popup. For QA testing.
     */
    public static void testBadge(BadgeRarity rarity) {
        String testName = "Test " + rarity.displayName + " Badge";
        showBadge(testName, rarity);
    }

    /**
     * Test all badge rarities in sequence. For QA testing.
     */
    public static void testAllBadges() {
        for (BadgeRarity rarity : BadgeRarity.values()) {
            testBadge(rarity);
        }
    }

    /**
     * Clear the popup queue. For testing purposes.
     */
    public static void clearQueue() {
        popupQueue.clear();
        particles.clear();
        soundPlayed = false;
        flashStartTime = 0;
    }

    /**
     * Get current queue size. For debugging.
     */
    public static int getQueueSize() {
        return popupQueue.size();
    }
}
