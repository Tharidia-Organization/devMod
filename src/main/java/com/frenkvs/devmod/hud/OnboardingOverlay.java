package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.client.input.KeyInputHandler;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Onboarding Tutorial Overlay - Guides new users through DevMod features.
 *
 * This overlay:
 * - Shows step-by-step instructions
 * - Detects when user performs required actions
 * - Automatically advances to next step
 * - Can be dismissed or skipped
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class OnboardingOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "onboarding_hud");

    // Colors
    private static final int BG_COLOR = 0xEE1A1A2E;
    private static final int BORDER_COLOR = 0xFF4CAF50;
    private static final int TITLE_COLOR = 0xFF81C784;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFFFD54F;
    private static final int MUTED_COLOR = 0xFF888888;
    private static final int PROGRESS_BG = 0xFF333333;
    private static final int PROGRESS_FILL = 0xFF4CAF50;

    // Layout
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_PADDING = 12;
    private static final int LINE_HEIGHT = 14;

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
        final KeyMapping keyHint;
        final TutorialAction requiredAction;

        TutorialStep(String title, String description, String instruction,
                     KeyMapping keyHint, TutorialAction requiredAction) {
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

        // Calculate panel size
        int panelHeight = calculatePanelHeight(font, step);

        // Position: top-center of screen
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = 10;

        // Draw panel background with pulse effect
        float pulse = (float) (Math.sin(pulseAnimation) * 0.5 + 0.5);
        int borderPulse = UIConstants.lerp(BORDER_COLOR, 0xFFFFFFFF, pulse * 0.3f);

        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, borderPulse);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BG_COLOR);

        int y = panelY + PANEL_PADDING;
        int contentX = panelX + PANEL_PADDING;
        int contentWidth = PANEL_WIDTH - PANEL_PADDING * 2;

        // Progress bar
        float progress = (float) currentStep / STEPS.length;
        int progressBarWidth = contentWidth;
        int progressBarHeight = 4;
        graphics.fill(contentX, y, contentX + progressBarWidth, y + progressBarHeight, PROGRESS_BG);
        int filledWidth = (int) (progressBarWidth * progress);
        if (filledWidth > 0) {
            graphics.fill(contentX, y, contentX + filledWidth, y + progressBarHeight, PROGRESS_FILL);
        }
        y += progressBarHeight + 8;

        // Step counter
        String stepText = I18n.translate("devmod.tutorial.step_counter", currentStep + 1, STEPS.length).getString();
        var safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, stepText, contentX, y, MUTED_COLOR, false);
        y += LINE_HEIGHT + 4;

        // Title (translate from i18n key)
        String title = I18n.translate(step.title).getString();
        graphics.drawString(safeFont, "§l" + title, contentX, y, TITLE_COLOR, false);
        y += LINE_HEIGHT + 2;

        // Description (translate from i18n key)
        String description = I18n.translate(step.description).getString();
        graphics.drawString(safeFont, description, contentX, y, TEXT_COLOR, false);
        y += LINE_HEIGHT + 8;

        // Instruction with pulsing highlight (translate from i18n key)
        String instruction = I18n.translate(step.instruction).getString();

        // Replace [KEY] placeholder with actual keybind name
        if (step.keyHint != null && !step.keyHint.isUnbound()) {
            String keyName = step.keyHint.getTranslatedKeyMessage().getString();
            instruction = instruction.replace("[KEY]", "[" + keyName + "]");
        } else if (step.keyHint != null) {
            // Key is unbound - show "UNBOUND" to prompt user to set it
            String unboundText = I18n.translate("devmod.tutorial.key_unbound").getString();
            instruction = instruction.replace("[KEY]", "[" + unboundText + "]");
        }

        // Truncate instruction if too long (keep at least 20 chars for readability)
        String displayInstruction = "▶ " + instruction;
        int maxInstructionWidth = contentWidth;
        if (font.width(displayInstruction) > maxInstructionWidth) {
            int minChars = Math.min(20, instruction.length());
            while (font.width("▶ " + instruction + "...") > maxInstructionWidth && instruction.length() > minChars) {
                instruction = instruction.substring(0, instruction.length() - 1);
            }
            displayInstruction = "▶ " + instruction + "...";
        }

        // Draw instruction with highlight
        int highlightAlpha = (int) (150 + pulse * 100);
        int highlightColor = (highlightAlpha << 24) | (HINT_COLOR & 0x00FFFFFF);
        graphics.fill(contentX - 4, y - 2, contentX + contentWidth + 4, y + LINE_HEIGHT + 2, 0x40000000);
        graphics.drawString(font, displayInstruction, contentX, y, highlightColor, false);
        y += LINE_HEIGHT + 10;

        // Skip/Done button hint
        String skipHint = I18n.translate("devmod.tutorial.skip_hint").getString();
        graphics.drawString(font, "§8" + skipHint, contentX, y, MUTED_COLOR, false);
    }

    private static int calculatePanelHeight(Font font, TutorialStep step) {
        return PANEL_PADDING * 2
            + 4 + 8      // Progress bar + gap
            + LINE_HEIGHT + 4  // Step counter
            + LINE_HEIGHT + 2  // Title
            + LINE_HEIGHT + 8  // Description
            + LINE_HEIGHT + 10 // Instruction
            + LINE_HEIGHT;     // Skip hint
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
