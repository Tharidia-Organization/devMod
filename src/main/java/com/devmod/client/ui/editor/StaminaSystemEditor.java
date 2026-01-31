package com.devmod.client.ui.editor;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.abilities.StaminaSystem;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

@OnlyIn(Dist.CLIENT)
public class StaminaSystemEditor extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaminaSystemEditor.class);
    private static final int DEFAULT_SELECTED_FIELD = 0;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;
    private static final int PANEL_PADDING = 10;
    private static final int FIELD_START_Y = 30;
    private static final int FIELD_LINE_HEIGHT = 20;
    private static final int INSTRUCTIONS_OFFSET_Y = 20;

    private static final int OVERLAY_BG = DesignTokens.StaminaEditor.OVERLAY_BG;
    private static final int PANEL_BG = DesignTokens.StaminaEditor.PANEL_BG;
    private static final int TITLE_TEXT_COLOR = DesignTokens.StaminaEditor.TITLE_TEXT;
    private static final int SELECTED_FIELD_COLOR = DesignTokens.StaminaEditor.SELECTED_FIELD;
    private static final int NORMAL_FIELD_COLOR = DesignTokens.StaminaEditor.NORMAL_FIELD;
    private static final int INSTRUCTIONS_COLOR = DesignTokens.StaminaEditor.INSTRUCTIONS;

    private static final float ADJUST_STEP = 0.1f;
    private static final float VALUE_SCALE = 10.0f;
    private static final float MAX_STAMINA_MIN = 10.0f;
    private static final float REGEN_RATE_MIN = 1.0f;
    private static final float REGEN_DELAY_MIN = 0.1f;
    private static final float MULTIPLIER_MIN = 0.1f;

    private static final String TITLE_TEXT = "Stamina Configuration";
    private static final String INSTRUCTIONS_TEXT = "↑↓ Navigate, ←→ Adjust, Enter Save";
    private static final String[] FIELD_NAMES = {
        "Max Stamina", "Regen Rate", "Regen Delay",
        "Max Multiplier", "Regen Multiplier", "Consumption Multiplier"
    };

    private final StaminaSystem.StaminaData data;
    private int selectedField = DEFAULT_SELECTED_FIELD;
    
    public StaminaSystemEditor() {
        super(java.util.Objects.requireNonNull(Component.literal("Stamina System Editor"), "title"));
        this.data = new StaminaSystem.StaminaData();
    }
    
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        graphics.fill(0, 0, width, height, OVERLAY_BG);

        var font = Objects.requireNonNull(this.font, "font");
        
        int panelWidth = PANEL_WIDTH;
        int panelHeight = PANEL_HEIGHT;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);
        UIScaleManager.drawScaledString(graphics, font, TITLE_TEXT, x + PANEL_PADDING, y + PANEL_PADDING, TITLE_TEXT_COLOR, false);
        
        // Render fields
        for (int i = 0; i < FIELD_NAMES.length; i++) {
            int fieldY = y + FIELD_START_Y + i * FIELD_LINE_HEIGHT;
            int color = selectedField == i ? SELECTED_FIELD_COLOR : NORMAL_FIELD_COLOR;
            
            String value = switch (i) {
                case 0 -> String.format("%.1f", data.maxStamina);
                case 1 -> String.format("%.1f", data.regenRate);
                case 2 -> String.format("%.1f", data.regenDelay);
                case 3 -> String.format("%.2f", data.maxStaminaMultiplier);
                case 4 -> String.format("%.2f", data.regenRateMultiplier);
                case 5 -> String.format("%.2f", data.consumptionMultiplier);
                default -> "0";
            };
            
            UIScaleManager.drawScaledString(graphics, font, FIELD_NAMES[i] + ": " + value, x + PANEL_PADDING, fieldY, color, false);
        }
        
        // Instructions
        UIScaleManager.drawScaledString(graphics, font, INSTRUCTIONS_TEXT, x + PANEL_PADDING, y + panelHeight - INSTRUCTIONS_OFFSET_Y,
            INSTRUCTIONS_COLOR, false);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                selectedField = Math.max(0, selectedField - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                selectedField = Math.min(FIELD_NAMES.length - 1, selectedField + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                adjustValue(-ADJUST_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                adjustValue(ADJUST_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                saveConfiguration();
                onClose();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                onClose();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private void adjustValue(float delta) {
        switch (selectedField) {
            case 0 -> data.maxStamina = Math.max(MAX_STAMINA_MIN, data.maxStamina + delta * VALUE_SCALE);
            case 1 -> data.regenRate = Math.max(REGEN_RATE_MIN, data.regenRate + delta * VALUE_SCALE);
            case 2 -> data.regenDelay = Math.max(REGEN_DELAY_MIN, data.regenDelay + delta);
            case 3 -> data.maxStaminaMultiplier = Math.max(MULTIPLIER_MIN, data.maxStaminaMultiplier + delta);
            case 4 -> data.regenRateMultiplier = Math.max(MULTIPLIER_MIN, data.regenRateMultiplier + delta);
            case 5 -> data.consumptionMultiplier = Math.max(MULTIPLIER_MIN, data.consumptionMultiplier + delta);
        }
    }
    
    private void saveConfiguration() {
        // In real implementation, save to config or send to server
        LOGGER.info("[StaminaEditor] Config saved: max={}, regen={}, delay={}",
            data.maxStamina, data.regenRate, data.regenDelay);
    }
}
