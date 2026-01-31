package com.devmod.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.actions.client.OnboardingActionPayload;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)

public class WelcomeScreen extends Screen {

    // === Colors (Indigo theme for welcome screen) ===
    private static final int COLOR_BG_TOP = DesignTokens.Welcome.BG_TOP;
    private static final int COLOR_BG_BOTTOM = DesignTokens.Welcome.BG_BOTTOM;
    private static final int COLOR_BORDER = DesignTokens.Welcome.BORDER;
    private static final int COLOR_TITLE = DesignTokens.Welcome.TITLE;
    private static final int COLOR_SUBTITLE = DesignTokens.Welcome.SUBTITLE;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_KEY = DesignTokens.Accent.YELLOW;  // Yellow for keybinds
    private static final int COLOR_PARTICLE = DesignTokens.Welcome.PARTICLE;

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
    @Nullable
    private EditorToggle dontShowToggle;
    @Nullable
    private EditorButtonWidget tutorialButtonWidget;
    @Nullable
    private EditorButtonWidget skipButtonWidget;
    private int dontShowToggleX;
    private int dontShowToggleY;
    private int dontShowToggleWidth;
    private long openTime;
    private boolean introSoundPlayed = false;

    // === Particles ===
    private final List<FloatingParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    // === Features to display ===
    private static final Feature[] FEATURES = {
        new Feature("Mob Stat Viewer", "See HP, armor, damage, and reach in real-time", DesignTokens.Welcome.FEATURE_MOB),
        new Feature("Debug Overlays", "Light levels, pathfinding, hitboxes, aggro ranges", DesignTokens.Welcome.FEATURE_DEBUG),
        new Feature("Endurance Quest", "Wave-based survival with combos & style ranks", DesignTokens.Welcome.FEATURE_ENDURANCE),
        new Feature("QA Testing Tools", "Comprehensive testing suite for mod developers", DesignTokens.Welcome.FEATURE_TESTING)
    };

    private static final Keybind[] KEYBINDS = {
        new Keybind("G", "Open Radial Menu (default)"),
        new Keybind("F10", "Start Endurance Quest (assign in Controls)"),
        new Keybind("L", "Light Levels (assign in Controls)"),
        new Keybind("H", "Heatmap Cycle (assign in Controls)")
    };

    public WelcomeScreen() {
        super(I18n.screenTitle("welcome"));
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        UIScaleManager.update();

        // Responsive panel dimensions with scaling
        int scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int actualPanelWidth = Math.min(scaledPanelWidth, width - UIScaleManager.scale(20));
        int actualPanelHeight = Math.min(scaledPanelHeight, height - UIScaleManager.scale(20));
        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - actualPanelWidth / 2;
        int panelY = centerY - actualPanelHeight / 2;

        // Tutorial button - positioned relative to actual panel width
        int margin = UIScaleManager.scale(35);
        int buttonWidth = Math.min(UIScaleManager.scale(170), (actualPanelWidth - UIScaleManager.scale(50)) / 2);
        int buttonHeight = UIScaleManager.scale(28);
        int buttonY = panelY + actualPanelHeight - UIScaleManager.scale(75);
        String tutorialLabel = Objects.requireNonNull(I18n.ui("start_tutorial").getString(), "tutorialLabel");
        EditorButton localTutorialButton = new EditorButton("welcome-start-tutorial", tutorialLabel)
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(this::startTutorial);
        EditorButtonWidget safeTutorialButtonWidget = new EditorButtonWidget(localTutorialButton,
            panelX + margin, buttonY, buttonWidth, buttonHeight);
        tutorialButtonWidget = safeTutorialButtonWidget;
        safeTutorialButtonWidget.visible = false;
        addRenderableWidget(safeTutorialButtonWidget);

        // Skip button - positioned relative to actual panel width
        String skipLabel = Objects.requireNonNull(I18n.ui("skip_know_this").getString(), "skipLabel");
        EditorButton localSkipButton = new EditorButton("welcome-skip", skipLabel)
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.LARGE)
            .onClick(this::skip);
        EditorButtonWidget safeSkipButtonWidget = new EditorButtonWidget(localSkipButton,
            panelX + actualPanelWidth - buttonWidth - margin, buttonY, buttonWidth, buttonHeight);
        skipButtonWidget = safeSkipButtonWidget;
        safeSkipButtonWidget.visible = false;
        addRenderableWidget(safeSkipButtonWidget);

        // Toggle (using EditorToggle for consistent theming)
        String dontShowLabel = Objects.requireNonNull(I18n.ui("dont_show_again").getString(), "dontShowLabel");
        dontShowToggle = new EditorToggle("welcome-dont-show", dontShowLabel, false)
            .onChange(value -> dontShowAgain = value);
        dontShowToggleX = panelX + margin;
        dontShowToggleY = panelY + actualPanelHeight - UIScaleManager.scale(40);
        dontShowToggleWidth = buttonWidth * 2;

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
            EditorButtonWidget localTutorialWidget = tutorialButtonWidget;
            if (localTutorialWidget != null) {
                localTutorialWidget.visible = true;
            }
            EditorButtonWidget localSkipWidget = skipButtonWidget;
            if (localSkipWidget != null) {
                localSkipWidget.visible = true;
            }
        }

        // Update particles
        for (FloatingParticle p : particles) {
            p.update(width, height);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        @Nonnull GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        @Nonnull Font safeFont = safeFont();
        long elapsed = System.currentTimeMillis() - openTime;

        // Calculate animations
        float fadeProgress = Math.min(1.0f, (float) elapsed / FADE_IN_DURATION);
        float scaleProgress = easeOutBack(fadeProgress);

        // Background with particles
        renderBackground(safeGraphics, mouseX, mouseY, partialTick);
        renderParticles(safeGraphics);

        int centerX = UIScaleManager.getCenterX();
        int centerY = UIScaleManager.getCenterY();

        // Apply scale animation to panel
        safeGraphics.pose().pushPose();
        safeGraphics.pose().translate(centerX, centerY, 0);
        safeGraphics.pose().scale(scaleProgress, scaleProgress, 1.0f);
        safeGraphics.pose().translate(-centerX, -centerY, 0);

        int scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int panelX = centerX - scaledPanelWidth / 2;
        int panelY = centerY - scaledPanelHeight / 2;

        // Panel background
        renderPanelWithGradient(safeGraphics, panelX, panelY, scaledPanelWidth, scaledPanelHeight, fadeProgress);

        // === Title Section ===
        if (elapsed > TITLE_REVEAL_DELAY) {
            float titleAlpha = Math.min(1.0f, (elapsed - TITLE_REVEAL_DELAY) / 300.0f);
            renderTitle(safeGraphics, centerX, panelY, titleAlpha, elapsed);

            // Play intro sound once
            if (!introSoundPlayed) {
                Minecraft mc = Minecraft.getInstance();
                @Nonnull SoundEvent soundEvent = Objects.requireNonNull(SoundEvents.UI_TOAST_IN, "toastIn");
                @Nonnull SoundInstance soundInstance = Objects.requireNonNull(
                    SimpleSoundInstance.forUI(soundEvent, 1.0f, 0.9f), "toastInSound");
                mc.getSoundManager().play(soundInstance);
                introSoundPlayed = true;
            }
        }

        // === Features Section ===
        if (elapsed > FEATURES_REVEAL_DELAY) {
            renderFeatures(safeGraphics, panelX, panelY + UIScaleManager.scale(75), scaledPanelWidth, elapsed);
        }

        // === Keybinds Section ===
        if (elapsed > KEYBINDS_REVEAL_DELAY) {
            renderKeybinds(safeGraphics, centerX, panelY + UIScaleManager.scale(220), elapsed);
        }

        // === Buttons hint ===
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float hintAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            int hintColor = applyAlpha(DesignTokens.Welcome.HINT, hintAlpha);
            UIScaleManager.drawScaledCenteredString(safeGraphics, safeFont, "Press ESC to skip", centerX, panelY + scaledPanelHeight - 15, hintColor);
        }

        safeGraphics.pose().popPose();

        // Render widgets with fade
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            float btnAlpha = Math.min(1.0f, (elapsed - BUTTONS_REVEAL_DELAY) / 300.0f);
            // Capture nullable fields in local variables for thread safety
            EditorToggle toggle = dontShowToggle;
            EditorButtonWidget tutorialBtn = tutorialButtonWidget;
            EditorButtonWidget skipBtn = skipButtonWidget;
            if (toggle != null) {
                toggle.setAlpha(btnAlpha);
                toggle.render(safeGraphics, dontShowToggleX, dontShowToggleY, dontShowToggleWidth, mouseX, mouseY);
            }
            if (tutorialBtn != null) {
                tutorialBtn.setAlpha(btnAlpha);
            }
            if (skipBtn != null) {
                skipBtn.setAlpha(btnAlpha);
            }
        }

        super.render(safeGraphics, mouseX, mouseY, partialTick);
    }

    // === Rendering Methods ===

    private void renderPanelWithGradient(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        // Outer glow (pulsing)
        long elapsed = System.currentTimeMillis() - openTime;
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 600.0);
        int glowAlpha = (int) (DesignTokens.Alpha.A20 * pulse * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & DesignTokens.Mask.RGB);

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
        int highlightColor = applyAlpha(DesignTokens.Welcome.HIGHLIGHT, alpha);
        g.fill(x, y, x + w, y + 1, highlightColor);
    }

    private void renderTitle(GuiGraphics g, int centerX, int panelY, float alpha, long elapsed) {
        @Nonnull Font safeFont = safeFont();
        // Animated "DevMod" logo text
        float logoScale = 1.0f + 0.03f * (float) Math.sin(elapsed / 400.0);
        float scaledLogoSize = UIScaleManager.scaleF(2.0f);

        g.pose().pushPose();
        g.pose().translate(centerX, panelY + UIScaleManager.scale(25), 0);
        g.pose().scale(logoScale * scaledLogoSize, logoScale * scaledLogoSize, 1.0f);

        String title = "DevMod";
        int titleWidth = safeFont.width(title);
        int titleColor = applyAlpha(COLOR_TITLE, alpha);
        g.drawString(safeFont, title, -titleWidth / 2, 0, titleColor, true);

        g.pose().popPose();

        // Subtitle
        int subtitleColor = applyAlpha(COLOR_SUBTITLE, alpha);
        UIScaleManager.drawScaledCenteredString(g, safeFont, "Welcome!", centerX, panelY + UIScaleManager.scale(48), subtitleColor);

        // Separator line
        int sepWidth = UIScaleManager.scale(180);
        int sepColor = applyAlpha(DesignTokens.withAlpha(DesignTokens.Welcome.BORDER, DesignTokens.Alpha.A40), alpha);
        g.fill(centerX - sepWidth, panelY + UIScaleManager.scale(65), centerX + sepWidth, panelY + UIScaleManager.scale(66), sepColor);
    }

    private void renderFeatures(GuiGraphics g, int panelX, int startY, int scaledPanelWidth, long elapsed) {
        @Nonnull Font safeFont = safeFont();
        long featureElapsed = elapsed - FEATURES_REVEAL_DELAY;

        int y = startY;
        int margin = UIScaleManager.scale(30);
        int x = panelX + margin;

        // "Features:" header
        if (featureElapsed > 0) {
            float headerAlpha = Math.min(1.0f, featureElapsed / 200.0f);
            int headerColor = applyAlpha(COLOR_TEXT_DIM, headerAlpha);
            UIScaleManager.drawScaledString(g, safeFont, "What you get:", x, y, headerColor, false);
            y += UIScaleManager.scale(16);
        }

        // Feature list with staggered reveal
        for (int i = 0; i < FEATURES.length; i++) {
            long featureDelay = (i + 1) * FEATURES_STAGGER;
            if (featureElapsed > featureDelay) {
                float featureAlpha = Math.min(1.0f, (featureElapsed - featureDelay) / 250.0f);
                renderFeatureItem(g, FEATURES[i], x, y, scaledPanelWidth - margin * 2, featureAlpha, elapsed);
                y += UIScaleManager.scale(26);
            }
        }
    }

    private void renderFeatureItem(GuiGraphics g, Feature feature, int x, int y, int featureWidth, float alpha, long elapsed) {
        @Nonnull Font safeFont = safeFont();
        // Bullet animation
        float bulletPulse = 1.0f + 0.2f * (float) Math.sin(elapsed / 300.0 + feature.name.hashCode());

        // Background highlight on hover effect (subtle)
        int bgColor = applyAlpha(DesignTokens.Welcome.SUBTLE, alpha);
        int rowHeight = UIScaleManager.scale(20);
        g.fill(x - UIScaleManager.scale(5), y - UIScaleManager.scale(2), x + featureWidth, y + rowHeight, bgColor);

        // Colored bullet
        int bulletColor = applyAlpha(feature.color, alpha);
        String bullet = "\u25CF"; // Filled circle
        g.pose().pushPose();
        g.pose().translate(x, y + UIScaleManager.scale(4), 0);
        g.pose().scale(bulletPulse, bulletPulse, 1.0f);
        g.drawString(safeFont, bullet, 0, 0, bulletColor, false);
        g.pose().popPose();

        // Feature name
        int textOffset = UIScaleManager.scale(15);
        int nameColor = applyAlpha(feature.color, alpha);
        UIScaleManager.drawScaledString(g, safeFont, feature.name, x + textOffset, y + UIScaleManager.scale(2), nameColor, true);

        // Description
        int descColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        UIScaleManager.drawScaledString(g, safeFont, feature.description, x + textOffset, y + UIScaleManager.scale(12), descColor, false);
    }

    private void renderKeybinds(GuiGraphics g, int centerX, int startY, long elapsed) {
        @Nonnull Font safeFont = safeFont();
        long keybindElapsed = elapsed - KEYBINDS_REVEAL_DELAY;
        float alpha = Math.min(1.0f, keybindElapsed / 300.0f);

        // Separator
        int sepWidth = UIScaleManager.scale(180);
        int sepColor = applyAlpha(DesignTokens.withAlpha(DesignTokens.Welcome.BORDER, DesignTokens.Alpha.A27), alpha);
        g.fill(centerX - sepWidth, startY, centerX + sepWidth, startY + 1, sepColor);

        // Header
        int headerColor = applyAlpha(COLOR_TEXT, alpha);
        UIScaleManager.drawScaledCenteredString(g, safeFont, "Suggested Keybinds", centerX, startY + UIScaleManager.scale(10), headerColor);

        // Keybinds
        int y = startY + UIScaleManager.scale(28);
        int keybindOffset = UIScaleManager.scale(120);
        int lineHeight = UIScaleManager.scale(18);
        for (int i = 0; i < KEYBINDS.length; i++) {
            long kbDelay = i * 120L;
            if (keybindElapsed > kbDelay) {
                float kbAlpha = Math.min(1.0f, (keybindElapsed - kbDelay) / 200.0f);
                renderKeybindItem(g, KEYBINDS[i], centerX - keybindOffset, y, kbAlpha);
                y += lineHeight;
            }
        }
    }

    private void renderKeybindItem(GuiGraphics g, Keybind kb, int x, int y, float alpha) {
        @Nonnull Font safeFont = safeFont();
        // Key box background
        String keyText = "[" + kb.key + "]";
        int keyWidth = UIScaleManager.getScaledStringWidth(safeFont, keyText);

        int padding = UIScaleManager.scale(2);
        int boxHeight = UIScaleManager.scale(10);
        int boxColor = applyAlpha(DesignTokens.Welcome.SHADOW, alpha);
        g.fill(x - padding, y - 1, x + keyWidth + padding * 2, y + boxHeight, boxColor);

        // Key text
        int keyColor = applyAlpha(COLOR_KEY, alpha);
        UIScaleManager.drawScaledString(g, safeFont, keyText, x, y, keyColor, true);

        // Action text
        int actionColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        UIScaleManager.drawScaledString(g, safeFont, kb.action, x + keyWidth + UIScaleManager.scale(10), y, actionColor, false);
    }

    private void renderParticles(GuiGraphics g) {
        for (FloatingParticle p : particles) {
            p.render(g);
        }
    }

    // === Helper Methods ===

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
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
        ActionRegistry.invoke(ActionIds.UI_ONBOARDING_START,
            ClientActionContexts.forClient(ActionOrigin.UI, new OnboardingActionPayload(dontShowAgain)));
    }

    private void skip() {
        ActionRegistry.invoke(ActionIds.UI_ONBOARDING_SKIP,
            ClientActionContexts.forClient(ActionOrigin.UI, new OnboardingActionPayload(dontShowAgain)));
    }

    @Nonnull
    private Font safeFont() {
        return Objects.requireNonNull(font, "font");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long elapsed = System.currentTimeMillis() - openTime;
        if (elapsed > BUTTONS_REVEAL_DELAY) {
            EditorToggle toggle = dontShowToggle;
            if (toggle != null && toggle.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            int color = (a << 24) | (COLOR_PARTICLE & DesignTokens.Mask.RGB);
            int s = (int) size;
            g.fill((int)x - s, (int)y - s, (int)x + s, (int)y + s, color);
        }
    }
}
