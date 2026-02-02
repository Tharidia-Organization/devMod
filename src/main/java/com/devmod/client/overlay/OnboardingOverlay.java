package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class OnboardingOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "onboarding_hud");

    // Colors (delegating to OverlayTheme)
    private static final int BG_COLOR = OverlayTheme.Panel.BG_HEAVY;
    private static final int BORDER_COLOR = OverlayTheme.Border.SUCCESS;
    private static final int TITLE_COLOR = OverlayTheme.Help.TITLE;  // Light green
    private static final int TEXT_COLOR = OverlayTheme.Text.PRIMARY;
    private static final int HINT_COLOR = OverlayTheme.Text.GOLD;
    private static final int MUTED_COLOR = OverlayTheme.Text.HINT;
    private static final int PROGRESS_BG = OverlayTheme.Progress.BG;
    private static final int PROGRESS_FILL = OverlayTheme.Progress.FILL_GREEN;

    // Layout
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_PADDING = OverlayTheme.Dimension.PADDING_COMFORTABLE;
    private static final int LINE_HEIGHT = OverlayTheme.Dimension.LINE_HEIGHT_READABLE;

    // State
    private static boolean active = false;
    private static int currentStep = 0;
    private static long stepStartTime = 0;
    private static float pulseAnimation = 0;

    // Tutorial steps - using i18n keys
    // NOTE: Key placeholders like [KEY] are replaced at runtime with actual bindings
    private static final TutorialStep[] STEPS = {
        new TutorialStep(
            "devmod.tutorial.step1.title",
            "devmod.tutorial.step1.desc",
            "devmod.tutorial.step1.instruction",
            KeyInputHandler.OPEN_RADIAL_MENU_KEY,
            TutorialAction.OPEN_RADIAL_MENU
        ),
        new TutorialStep(
            "devmod.tutorial.step2.title",
            "devmod.tutorial.step2.desc",
            "devmod.tutorial.step2.instruction",
            null,
            TutorialAction.SELECT_CATEGORY
        ),
        new TutorialStep(
            "devmod.tutorial.step3.title",
            "devmod.tutorial.step3.desc",
            "devmod.tutorial.step3.instruction",
            null,
            TutorialAction.TOGGLE_OVERLAY
        ),
        new TutorialStep(
            "devmod.tutorial.step4.title",
            "devmod.tutorial.step4.desc",
            "devmod.tutorial.step4.instruction",
            KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY,
            TutorialAction.OPEN_ENDURANCE_QUEST
        ),
        new TutorialStep(
            "devmod.tutorial.step5.title",
            "devmod.tutorial.step5.desc",
            "devmod.tutorial.step5.instruction",
            null,
            TutorialAction.COMPLETE
        )
    };

    public enum TutorialAction {
        OPEN_RADIAL_MENU,
        SELECT_CATEGORY,
        TOGGLE_OVERLAY,
        OPEN_HELP,
        OPEN_ENDURANCE_QUEST,
        COMPLETE
    }

    public static class TutorialStep {
        final String title;
        final String description;
        final String instruction;
        @Nullable
        final KeyMapping keyHint;
        final TutorialAction requiredAction;

        TutorialStep(String title, String description, String instruction,
                     @Nullable KeyMapping keyHint, TutorialAction requiredAction) {
            this.title = title;
            this.description = description;
            this.instruction = instruction;
            this.keyHint = keyHint;
            this.requiredAction = requiredAction;
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            OnboardingOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // Don't show when in a screen

        if (currentStep >= STEPS.length) {
            complete();
            return;
        }

        Font font = mc.font;
        TutorialStep step = STEPS[currentStep];

        // Update animation
        pulseAnimation += deltaTracker.getGameTimeDeltaTicks() * 0.1f;

        // Scale dimensions
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);

        // Calculate panel size
        int panelHeight = calculatePanelHeight(sPanelPadding, sLineHeight);

        // Position: top-center of screen
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = UIScaleManager.scale(10);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        // Draw panel background with pulse effect
        float pulse = (float) (Math.sin(pulseAnimation) * 0.5 + 0.5);
        int borderPulse = DesignTokens.lerp(BORDER_COLOR, OverlayTheme.Utility.WHITE, pulse * 0.3f);

        int sBorder = UIScaleManager.scale(2);
        graphics.fill(panelX - sBorder, panelY - sBorder, panelX + panelWidth + sBorder, panelY + panelHeight + sBorder, borderPulse);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BG_COLOR);

        int y = panelY + sPanelPadding;
        int contentX = panelX + sPanelPadding;
        int contentWidth = panelWidth - sPanelPadding * 2;

        // Progress bar
        float progress = (float) currentStep / STEPS.length;
        int progressBarWidth = contentWidth;
        int progressBarHeight = UIScaleManager.scale(4);
        graphics.fill(contentX, y, contentX + progressBarWidth, y + progressBarHeight, PROGRESS_BG);
        int filledWidth = (int) (progressBarWidth * progress);
        if (filledWidth > 0) {
            graphics.fill(contentX, y, contentX + filledWidth, y + progressBarHeight, PROGRESS_FILL);
        }
        y += progressBarHeight + UIScaleManager.scale(8);

        // Step counter
        String stepText = I18n.translate("devmod.tutorial.step_counter", currentStep + 1, STEPS.length).getString();
        var safeFont = Objects.requireNonNull(font);
        stepText = truncateToWidth(safeFont, stepText, contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, stepText, contentX, y, MUTED_COLOR, false);
        y += sLineHeight + UIScaleManager.scale(4);

        // Title (translate from i18n key)
        String title = I18n.translate(step.title).getString();
        title = truncateToWidth(safeFont, title, contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, "\u00A7l" + title, contentX, y, TITLE_COLOR, false);
        y += sLineHeight + UIScaleManager.scale(2);

        // Description (translate from i18n key)
        String description = I18n.translate(step.description).getString();
        description = truncateToWidth(safeFont, description, contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, description, contentX, y, TEXT_COLOR, false);
        y += sLineHeight + UIScaleManager.scale(8);

        // Instruction with pulsing highlight (translate from i18n key)
        String instruction = I18n.translate(step.instruction).getString();

        // Replace [KEY] placeholder with actual keybind name
        var keyHint = step.keyHint;
        if (keyHint != null && !keyHint.isUnbound()) {
            String keyName = keyHint.getTranslatedKeyMessage().getString();
            instruction = instruction.replace("[KEY]", "[" + keyName + "]");
        } else if (keyHint != null) {
            // Key is unbound - show "UNBOUND" to prompt user to set it
            String unboundText = I18n.translate("devmod.tutorial.key_unbound").getString();
            instruction = instruction.replace("[KEY]", "[" + unboundText + "]");
        }

        // Truncate instruction if too long
        String displayInstruction = "▶ " + instruction;
        displayInstruction = truncateToWidth(safeFont, displayInstruction, contentWidth);

        // Draw instruction with highlight
        int highlightAlpha = (int) (150 + pulse * 100);
        int highlightColor = OverlayTheme.withAlpha(HINT_COLOR, highlightAlpha);
        int sHighlightPad = UIScaleManager.scale(4);
        graphics.fill(contentX - sHighlightPad, y - UIScaleManager.scale(2), contentX + contentWidth + sHighlightPad, y + sLineHeight + UIScaleManager.scale(2), OverlayTheme.Utility.SHADOW);
        UIScaleManager.drawScaledString(graphics, font, displayInstruction, contentX, y, highlightColor, false);
        y += sLineHeight + UIScaleManager.scale(10);

        // Skip/Done button hint
        String skipHint = I18n.translate("devmod.tutorial.skip_hint").getString();
        skipHint = truncateToWidth(safeFont, skipHint, contentWidth);
        UIScaleManager.drawScaledString(graphics, font, "\u00A78" + skipHint, contentX, y, MUTED_COLOR, false);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    private static int calculatePanelHeight(int sPanelPadding, int sLineHeight) {
        return sPanelPadding * 2
            + UIScaleManager.scale(4) + UIScaleManager.scale(8)      // Progress bar + gap
            + sLineHeight + UIScaleManager.scale(4)  // Step counter
            + sLineHeight + UIScaleManager.scale(2)  // Title
            + sLineHeight + UIScaleManager.scale(8)  // Description
            + sLineHeight + UIScaleManager.scale(10) // Instruction
            + sLineHeight;     // Skip hint
    }

    // === Action Detection ===

    /**
     * Called when user opens the radial menu.
     */
    public static void onRadialMenuOpened() {
        if (active && currentStep < STEPS.length) {
            TutorialStep step = STEPS[currentStep];
            if (step.requiredAction == TutorialAction.OPEN_RADIAL_MENU) {
                advanceStep();
            }
        }
    }

    /**
     * Called when user selects a category in radial menu.
     */
    public static void onCategorySelected() {
        if (active && currentStep < STEPS.length) {
            TutorialStep step = STEPS[currentStep];
            if (step.requiredAction == TutorialAction.SELECT_CATEGORY) {
                advanceStep();
            }
        }
    }

    /**
     * Called when user toggles any overlay.
     */
    public static void onOverlayToggled() {
        if (active && currentStep < STEPS.length) {
            TutorialStep step = STEPS[currentStep];
            if (step.requiredAction == TutorialAction.TOGGLE_OVERLAY) {
                advanceStep();
            }
        }
    }

    /**
     * Called when user opens help overlay.
     */
    public static void onHelpOpened() {
        if (active && currentStep < STEPS.length) {
            TutorialStep step = STEPS[currentStep];
            if (step.requiredAction == TutorialAction.OPEN_HELP) {
                advanceStep();
            }
        }
    }

    /**
     * Called when user opens the Endurance Quest screen.
     */
    public static void onEnduranceQuestOpened() {
        if (active && currentStep < STEPS.length) {
            TutorialStep step = STEPS[currentStep];
            if (step.requiredAction == TutorialAction.OPEN_ENDURANCE_QUEST) {
                advanceStep();
            }
        }
    }

    /**
     * Advance to next tutorial step.
     */
    public static void advanceStep() {
        long now = System.currentTimeMillis();
        // Debounce repeated triggers for the same step
        if (now - stepStartTime < 200) {
            return;
        }

        currentStep++;
        stepStartTime = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.EXPERIENCE_ORB_PICKUP), 1.2f, 0.8f)));
        }

        LOGGER.info("[OnboardingOverlay] Advanced to step {}", currentStep);

        if (currentStep >= STEPS.length) {
            complete();
        }
    }

    /**
     * Complete the tutorial.
     */
    public static void complete() {
        active = false;
        currentStep = 0;

        // Mark as completed in settings
        SettingsManager.INSTANCE.getSettings().onboarding.tutorialCompleted = true;
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc != null && player != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                Objects.requireNonNull(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), 1.0f, 1.0f)));
            String keyName = KeyInputHandler.OPEN_RADIAL_MENU_KEY.getTranslatedKeyMessage().getString();
            player.displayClientMessage(
                I18n.translate("devmod.onboarding.tutorial_complete", keyName),
                false
            );
        }

        LOGGER.info("[OnboardingOverlay] Tutorial completed!");
    }

    /**
     * Skip the tutorial without completing steps.
     */
    public static void skip() {
        active = false;
        currentStep = 0;

        // Mark as seen but not necessarily completed
        SettingsManager.INSTANCE.getSettings().onboarding.tutorialCompleted = true;
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc != null && player != null) {
            String keyName = KeyInputHandler.OPEN_RADIAL_MENU_KEY.getTranslatedKeyMessage().getString();
            player.displayClientMessage(
                I18n.translate("devmod.onboarding.tutorial_skipped", keyName),
                true
            );
        }

        LOGGER.info("[OnboardingOverlay] Tutorial skipped");
    }

    // === Public API ===

    public static void start() {
        active = true;
        currentStep = 0;
        stepStartTime = System.currentTimeMillis();
        pulseAnimation = 0;
        LOGGER.info("[OnboardingOverlay] Tutorial started");
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        if (active) {
            currentStep = 0;
            stepStartTime = System.currentTimeMillis();
        }
    }

    public static int getCurrentStep() {
        return currentStep;
    }

    /**
     * Check if ESC should skip the tutorial.
     * Returns true if handled (should cancel default ESC behavior).
     */
    public static boolean handleEscape() {
        if (active) {
            skip();
            return true;
        }
        return false;
    }
}
