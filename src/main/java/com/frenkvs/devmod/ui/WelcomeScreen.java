package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.testing.TutorialManager;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Premium Welcome screen with cinematic reveal animation.
 *
 * Features:
 * - Dramatic scale + fade entrance
 * - Staggered feature list reveal
 * - Floating particle background
 * - Animated logo/title
 * - Smooth hover effects on buttons
 * - Sound feedback
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class WelcomeScreen extends Screen {

    // === Colors ===
    private static final int COLOR_BG_TOP = 0xF01a1a2e;
    private static final int COLOR_BG_BOTTOM = 0xF00d0d1a;
    private static final int COLOR_BORDER = 0xFF6366f1;      // Indigo accent
    private static final int COLOR_TITLE = 0xFF818cf8;       // Light indigo
    private static final int COLOR_SUBTITLE = 0xFFa5b4fc;    // Lighter indigo
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_KEY = 0xFFfbbf24;         // Yellow for keybinds
    private static final int COLOR_PARTICLE = 0xFF6366f1;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 380;

    // === Animation Timing (ms) ===
    private static final long FADE_IN_DURATION = 500;
    private static final long TITLE_REVEAL_DELAY = 300;
    private static final long FEATURES_REVEAL_DELAY = 700;
    private static final long FEATURES_STAGGER = 180;
    private static final long KEYBINDS_REVEAL_DELAY = 1500;
    private static final long BUTTONS_REVEAL_DELAY = 2000;

    // === State ===
    private boolean dontShowAgain = false;
    private Checkbox dontShowCheckbox;
    private Button tutorialButton;
    private Button skipButton;
    private long openTime;
    private boolean introSoundPlayed = false;

    // === Particles ===
    private final List<FloatingParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    // === Features to display ===
    private static final Feature[] FEATURES = {
        new Feature("Mob Stat Viewer", "See HP, armor, damage, and reach in real-time", 0xFF4ade80),
        new Feature("Debug Overlays", "Light levels, pathfinding, hitboxes, aggro ranges", 0xFF60a5fa),
        new Feature("Endurance Quest", "Wave-based survival with combos & style ranks", 0xFFf472b6),
        new Feature("QA Testing Tools", "Comprehensive testing suite for mod developers", 0xFFfbbf24)
    };

    private static final Keybind[] KEYBINDS = {
        new Keybind("G", "Open Radial Menu (all tools)"),
        new Keybind("F10", "Start Endurance Quest"),
        new Keybind("L", "Toggle Light Levels"),
        new Keybind("H", "Cycle Heatmaps")
    };

    public WelcomeScreen() {
        super(I18n.screenTitle("welcome"));
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Tutorial button
        tutorialButton = Button.builder(I18n.ui("start_tutorial"), btn -> startTutorial())
            .bounds(panelX + 35, panelY + PANEL_HEIGHT - 75, 170, 28)
            .build();
        addRenderableWidget(tutorialButton);
        tutorialButton.visible = false;

        // Skip button
        skipButton = Button.builder(I18n.ui("skip_know_this"), btn -> skip())
            .bounds(panelX + PANEL_WIDTH - 205, panelY + PANEL_HEIGHT - 75, 170, 28)
            .build();
        addRenderableWidget(skipButton);
        skipButton.visible = false;

        // Checkbox
        dontShowCheckbox = Checkbox.builder(I18n.ui("dont_show_again"), font)
            .pos(panelX + 35, panelY + PANEL_HEIGHT - 40)
            .selected(false)
            .onValueChange((checkbox, value) -> dontShowAgain = value)
            .build();
        addRenderableWidget(dontShowCheckbox);
        dontShowCheckbox.visible = false;

        // Initialize background particles
        for (int i = 0; i < 30; i++) {
            particles.add(new FloatingParticle(
                random.nextFloat() * width,
                random.nextFloat() * height,
                random
            ));
        }
    }

    @Override
    public void tick() {
        super.tick();

        long elapsed = System.currentTimeMillis() - openTime;

        // Show widgets after delay
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            tutorialButton.visible = true;
            skipButton.visible = true;
            dontShowCheckbox.visible = true;
        }

        // Update particles
        for (FloatingParticle p : particles) {
            p.update(width, height);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - openTime;

        // Calculate animations
        float fadeProgress = Math.min(1.0f, (float) elapsed / FADE_IN_DURATION);
        float scaleProgress = easeOutBack(fadeProgress);

        // Background with particles
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderParticles(graphics);

        int centerX = width / 2;
        int centerY = height / 2;

        // Apply scale animation to panel
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scaleProgress, scaleProgress, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0);

        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Panel background
        renderPanelWithGradient(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeProgress);

        // === Title Section ===
        if (elapsed > TITLE_REVEAL_DELAY) {
            float titleAlpha = Math.min(1.0f, (elapsed - TITLE_REVEAL_DELAY) / 300.0f);
            renderTitle(graphics, centerX, panelY, titleAlpha, elapsed);

            // Play intro sound once
            if (!introSoundPlayed && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_TOAST_IN, 1.0f, 0.9f));
                introSoundPlayed = true;
            }
        }

        // === Features Section ===
        if (elapsed > FEATURES_REVEAL_DELAY) {
            renderFeatures(graphics, panelX, panelY + 75, elapsed);
        }

        // === Keybinds Section ===
        if (elapsed > KEYBINDS_REVEAL_DELAY) {
            renderKeybinds(graphics, centerX, panelY + 220, elapsed);
        }

        // === Buttons hint ===
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float hintAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            int hintColor = applyAlpha(0xFF444444, hintAlpha);
            graphics.drawCenteredString(font, "Press ESC to skip", centerX, panelY + PANEL_HEIGHT - 15, hintColor);
        }

        graphics.pose().popPose();

        // Render widgets with fade
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float btnAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            tutorialButton.setAlpha(btnAlpha);
            skipButton.setAlpha(btnAlpha);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // === Rendering Methods ===

    private void renderPanelWithGradient(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        // Outer glow (pulsing)
        long elapsed = System.currentTimeMillis() - openTime;
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 600.0);
        int glowAlpha = (int) (0x33 * pulse * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & 0x00FFFFFF);

        g.fill(x - 5, y - 5, x + w + 5, y + h + 5, glowColor);
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, glowColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, borderColor);

        // Gradient background
        int topColor = applyAlpha(COLOR_BG_TOP, alpha);
        int bottomColor = applyAlpha(COLOR_BG_BOTTOM, alpha);

        for (int i = 0; i < h; i++) {
            float gradProgress = (float) i / h;
            int lineColor = lerpColor(topColor, bottomColor, gradProgress);
            g.fill(x, y + i, x + w, y + i + 1, lineColor);
        }

        // Inner highlight (top)
        int highlightColor = applyAlpha(0x22FFFFFF, alpha);
        g.fill(x, y, x + w, y + 1, highlightColor);
    }

    private void renderTitle(GuiGraphics g, int centerX, int panelY, float alpha, long elapsed) {
        // Animated "DevMod" logo text
        float logoScale = 1.0f + 0.03f * (float) Math.sin(elapsed / 400.0);

        g.pose().pushPose();
        g.pose().translate(centerX, panelY + 25, 0);
        g.pose().scale(logoScale * 2.0f, logoScale * 2.0f, 1.0f);

        String title = "DevMod";
        int titleWidth = font.width(title);
        int titleColor = applyAlpha(COLOR_TITLE, alpha);
        g.drawString(font, title, -titleWidth / 2, 0, titleColor, true);

        g.pose().popPose();

        // Subtitle
        int subtitleColor = applyAlpha(COLOR_SUBTITLE, alpha);
        g.drawCenteredString(font, "Welcome!", centerX, panelY + 48, subtitleColor);

        // Separator line
        int sepColor = applyAlpha(COLOR_BORDER & 0x66FFFFFF, alpha);
        g.fill(centerX - 180, panelY + 65, centerX + 180, panelY + 66, sepColor);
    }

    private void renderFeatures(GuiGraphics g, int panelX, int startY, long elapsed) {
        long featureElapsed = elapsed - FEATURES_REVEAL_DELAY;

        int y = startY;
        int x = panelX + 30;

        // "Features:" header
        if (featureElapsed > 0) {
            float headerAlpha = Math.min(1.0f, featureElapsed / 200.0f);
            int headerColor = applyAlpha(COLOR_TEXT_DIM, headerAlpha);
            g.drawString(font, "What you get:", x, y, headerColor, false);
            y += 16;
        }

        // Feature list with staggered reveal
        for (int i = 0; i < FEATURES.length; i++) {
            long featureDelay = (i + 1) * FEATURES_STAGGER;
            if (featureElapsed > featureDelay) {
                float featureAlpha = Math.min(1.0f, (featureElapsed - featureDelay) / 250.0f);
                renderFeatureItem(g, FEATURES[i], x, y, featureAlpha, elapsed);
                y += 26;
            }
        }
    }

    private void renderFeatureItem(GuiGraphics g, Feature feature, int x, int y, float alpha, long elapsed) {
        // Bullet animation
        float bulletPulse = 1.0f + 0.2f * (float) Math.sin(elapsed / 300.0 + feature.name.hashCode());

        // Background highlight on hover effect (subtle)
        int bgColor = applyAlpha(0x11FFFFFF, alpha);
        g.fill(x - 5, y - 2, x + PANEL_WIDTH - 60, y + 20, bgColor);

        // Colored bullet
        int bulletColor = applyAlpha(feature.color, alpha);
        String bullet = "\u25CF"; // Filled circle
        g.pose().pushPose();
        g.pose().translate(x, y + 4, 0);
        g.pose().scale(bulletPulse, bulletPulse, 1.0f);
        g.drawString(font, bullet, 0, 0, bulletColor, false);
        g.pose().popPose();

        // Feature name
        int nameColor = applyAlpha(feature.color, alpha);
        g.drawString(font, feature.name, x + 15, y + 2, nameColor, true);

        // Description
        int descColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        g.drawString(font, feature.description, x + 15, y + 12, descColor, false);
    }

    private void renderKeybinds(GuiGraphics g, int centerX, int startY, long elapsed) {
        long keybindElapsed = elapsed - KEYBINDS_REVEAL_DELAY;
        float alpha = Math.min(1.0f, keybindElapsed / 300.0f);

        // Separator
        int sepColor = applyAlpha(COLOR_BORDER & 0x44FFFFFF, alpha);
        g.fill(centerX - 180, startY, centerX + 180, startY + 1, sepColor);

        // Header
        int headerColor = applyAlpha(COLOR_TEXT, alpha);
        g.drawCenteredString(font, "Quick Start", centerX, startY + 10, headerColor);

        // Keybinds
        int y = startY + 28;
        for (int i = 0; i < KEYBINDS.length; i++) {
            long kbDelay = i * 120;
            if (keybindElapsed > kbDelay) {
                float kbAlpha = Math.min(1.0f, (keybindElapsed - kbDelay) / 200.0f);
                renderKeybindItem(g, KEYBINDS[i], centerX - 120, y, kbAlpha);
                y += 18;
            }
        }
    }

    private void renderKeybindItem(GuiGraphics g, Keybind kb, int x, int y, float alpha) {
        // Key box background
        String keyText = "[" + kb.key + "]";
        int keyWidth = font.width(keyText);

        int boxColor = applyAlpha(0x44000000, alpha);
        g.fill(x - 2, y - 1, x + keyWidth + 4, y + 10, boxColor);

        // Key text
        int keyColor = applyAlpha(COLOR_KEY, alpha);
        g.drawString(font, keyText, x, y, keyColor, true);

        // Action text
        int actionColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        g.drawString(font, kb.action, x + keyWidth + 10, y, actionColor, false);
    }

    private void renderParticles(GuiGraphics g) {
        for (FloatingParticle p : particles) {
            p.render(g);
        }
    }

    // === Helper Methods ===

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    // === Actions ===

    private void startTutorial() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f));
        }

        savePreference();
        TutorialManager.INSTANCE.setPhase(TutorialManager.TutorialPhase.WELCOME);
        TutorialManager.INSTANCE.setOnboardingCompleted(false);

        if (minecraft != null) {
            minecraft.setScreen(null);

            // Start the interactive onboarding overlay on next tick
            // This ensures the screen has fully closed before the overlay checks mc.screen
            minecraft.execute(() -> {
                com.frenkvs.devmod.hud.OnboardingOverlay.start();
            });
        }
    }

    private void skip() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.8f));
        }

        savePreference();
        TutorialManager.INSTANCE.setOnboardingCompleted(true);

        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void savePreference() {
        // Only mark as seen if user explicitly checked "Don't show again"
        if (dontShowAgain) {
            SettingsManager.INSTANCE.getSettings().onboarding.hasSeenWelcome = true;
            SettingsManager.INSTANCE.getSettings().onboarding.tutorialCompleted = true;
        }
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            skip();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Inner Classes ===

    private record Feature(String name, String description, int color) {}
    private record Keybind(String key, String action) {}

    private static class FloatingParticle {
        float x, y;
        float vx, vy;
        float size;
        float alpha;
        float alphaSpeed;

        FloatingParticle(float x, float y, Random random) {
            this.x = x;
            this.y = y;
            this.vx = (random.nextFloat() - 0.5f) * 0.3f;
            this.vy = (random.nextFloat() - 0.5f) * 0.3f;
            this.size = 1 + random.nextFloat() * 2;
            this.alpha = random.nextFloat() * 0.3f;
            this.alphaSpeed = 0.005f + random.nextFloat() * 0.01f;
        }

        void update(int screenWidth, int screenHeight) {
            x += vx;
            y += vy;

            // Wrap around
            if (x < 0) x = screenWidth;
            if (x > screenWidth) x = 0;
            if (y < 0) y = screenHeight;
            if (y > screenHeight) y = 0;

            // Pulse alpha
            alpha += alphaSpeed;
            if (alpha > 0.4f || alpha < 0.05f) {
                alphaSpeed = -alphaSpeed;
            }
        }

        void render(GuiGraphics g) {
            int a = (int) (255 * alpha);
            int color = (a << 24) | (COLOR_PARTICLE & 0x00FFFFFF);
            int s = (int) size;
            g.fill((int)x - s, (int)y - s, (int)x + s, (int)y + s, color);
        }
    }
}
