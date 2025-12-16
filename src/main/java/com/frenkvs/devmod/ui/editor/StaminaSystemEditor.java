package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.abilities.StaminaSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import javax.annotation.Nonnull;
import java.util.Objects;

public class StaminaSystemEditor extends Screen {
    private final StaminaSystem.StaminaData data;
    private int selectedField = 0;
    private final String[] fieldNames = {
        "Max Stamina", "Regen Rate", "Regen Delay", 
        "Max Multiplier", "Regen Multiplier", "Consumption Multiplier"
    };
    
    public StaminaSystemEditor() {
        super(Component.literal("Stamina System Editor"));
        this.data = new StaminaSystem.StaminaData();
    }
    
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x80000000);

        var font = Objects.requireNonNull(this.font, "font");
        
        int panelWidth = 300;
        int panelHeight = 200;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF2D2D30);
        graphics.drawString(font, "Stamina Configuration", x + 10, y + 10, 0xFFFFFFFF, false);
        
        // Render fields
        for (int i = 0; i < fieldNames.length; i++) {
            int fieldY = y + 30 + i * 20;
            int color = selectedField == i ? 0xFFFFFF00 : 0xFFCCCCCC;
            
            String value = switch (i) {
                case 0 -> String.format("%.1f", data.maxStamina);
                case 1 -> String.format("%.1f", data.regenRate);
                case 2 -> String.format("%.1f", data.regenDelay);
                case 3 -> String.format("%.2f", data.maxStaminaMultiplier);
                case 4 -> String.format("%.2f", data.regenRateMultiplier);
                case 5 -> String.format("%.2f", data.consumptionMultiplier);
                default -> "0";
            };
            
            graphics.drawString(font, fieldNames[i] + ": " + value, x + 10, fieldY, color, false);
        }
        
        // Instructions
        graphics.drawString(font, "↑↓ Navigate, ←→ Adjust, Enter Save", x + 10, y + panelHeight - 20, 0xFF888888, false);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                selectedField = Math.max(0, selectedField - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                selectedField = Math.min(fieldNames.length - 1, selectedField + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                adjustValue(-0.1f);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                adjustValue(0.1f);
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
            case 0 -> data.maxStamina = Math.max(10, data.maxStamina + delta * 10);
            case 1 -> data.regenRate = Math.max(1, data.regenRate + delta * 10);
            case 2 -> data.regenDelay = Math.max(0.1f, data.regenDelay + delta);
            case 3 -> data.maxStaminaMultiplier = Math.max(0.1f, data.maxStaminaMultiplier + delta);
            case 4 -> data.regenRateMultiplier = Math.max(0.1f, data.regenRateMultiplier + delta);
            case 5 -> data.consumptionMultiplier = Math.max(0.1f, data.consumptionMultiplier + delta);
        }
    }
    
    private void saveConfiguration() {
        // In real implementation, save to config or send to server
        System.out.println("Stamina config saved: " + 
            "max=" + data.maxStamina + 
            ", regen=" + data.regenRate + 
            ", delay=" + data.regenDelay);
    }
}
