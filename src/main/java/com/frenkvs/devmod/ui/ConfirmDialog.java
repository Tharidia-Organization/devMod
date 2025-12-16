package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.ui.editor.core.Typography;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import java.util.Objects;

public class ConfirmDialog {
    private final String title;
    private final String message;
    private final String confirmText;
    private final String cancelText;
    private final int accentColor;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean visible = false;
    
    public ConfirmDialog(String title, String message, String confirmText, String cancelText,
                         int accentColor, Runnable onConfirm, Runnable onCancel) {
        this.title = Objects.requireNonNull(title, "title");
        this.message = Objects.requireNonNull(message, "message");
        this.confirmText = Objects.requireNonNull(confirmText, "confirmText");
        this.cancelText = Objects.requireNonNull(cancelText, "cancelText");
        this.accentColor = accentColor;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }
    
    public void show() {
        this.visible = true;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;
        Font safeFont = java.util.Objects.requireNonNull(font, "font");
        String safeCancel = Objects.requireNonNull(cancelText, "cancelText");
        String safeConfirm = Objects.requireNonNull(confirmText, "confirmText");
        float titleScale = Typography.sectionHeaderScale();
        float bodyScale = Typography.valueScale();
        float buttonScale = Typography.buttonScale();
        
        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);
        
        // Dialog dimensions
        int dialogWidth = 300;
        int dialogHeight = 120;
        int x = (screenWidth - dialogWidth) / 2;
        int y = (screenHeight - dialogHeight) / 2;
        
        // Dialog background
        graphics.fill(x, y, x + dialogWidth, y + dialogHeight, 0xFF2D2D30);
        
        // Border
        graphics.fill(x, y, x + dialogWidth, y + 1, accentColor); // top
        graphics.fill(x, y + dialogHeight - 1, x + dialogWidth, y + dialogHeight, accentColor); // bottom
        graphics.fill(x, y, x + 1, y + dialogHeight, accentColor); // left
        graphics.fill(x + dialogWidth - 1, y, x + dialogWidth, y + dialogHeight, accentColor); // right
        
        // Title
        Typography.drawText(graphics, safeFont, title, x + 10, y + 10, 0xFFFFFFFF, titleScale);
        
        // Message
        Typography.drawText(graphics, safeFont, message, x + 10, y + 30, 0xFFCCCCCC, bodyScale);
        
        // Buttons
        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonY = y + dialogHeight - 30;
        
        // Cancel button
        int cancelX = x + dialogWidth - buttonWidth - 10;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, 0xFF404040);
        int cancelTextWidth = Math.round(safeFont.width(safeCancel) * buttonScale);
        int cancelTextX = cancelX + (buttonWidth - cancelTextWidth) / 2;
        int cancelTextY = buttonY + (buttonHeight - Math.round(safeFont.lineHeight * buttonScale)) / 2;
        Typography.drawText(graphics, safeFont, safeCancel, cancelTextX, cancelTextY, 0xFFFFFFFF, buttonScale);
        
        // Confirm button
        int confirmX = cancelX - buttonWidth - 10;
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, accentColor);
        int confirmTextWidth = Math.round(safeFont.width(safeConfirm) * buttonScale);
        int confirmTextX = confirmX + (buttonWidth - confirmTextWidth) / 2;
        int confirmTextY = buttonY + (buttonHeight - Math.round(safeFont.lineHeight * buttonScale)) / 2;
        Typography.drawText(graphics, safeFont, safeConfirm, confirmTextX, confirmTextY, 0xFFFFFFFF, buttonScale);
    }
    
    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!visible) return false;
        
        int dialogWidth = 300;
        int dialogHeight = 120;
        int x = (800 - dialogWidth) / 2; // Assume screen width for now
        int y = (600 - dialogHeight) / 2; // Assume screen height for now
        
        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonY = y + dialogHeight - 30;
        
        // Cancel button
        int cancelX = x + dialogWidth - buttonWidth - 10;
        if (mouseX >= cancelX && mouseX <= cancelX + buttonWidth && 
            mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            hide();
            if (onCancel != null) onCancel.run();
            return true;
        }
        
        // Confirm button
        int confirmX = cancelX - buttonWidth - 10;
        if (mouseX >= confirmX && mouseX <= confirmX + buttonWidth && 
            mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            hide();
            if (onConfirm != null) onConfirm.run();
            return true;
        }
        
        return true; // Consume all clicks when visible
    }
    
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            hide();
            if (onConfirm != null) onConfirm.run();
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hide();
            if (onCancel != null) onCancel.run();
            return true;
        }
        
        return true; // Consume all keys when visible
    }
    
    // Static factory methods for common dialogs
    public static ConfirmDialog deleteItem(String itemName, Runnable onConfirm, Runnable onCancel) {
        return new ConfirmDialog(
            "Delete Item",
            "Delete '" + itemName + "'? This cannot be undone.",
            "Delete",
            "Cancel",
            0xFFFF4444,
            onConfirm,
            onCancel
        );
    }
    
    public static ConfirmDialog resetChanges(int changeCount, Runnable onConfirm, Runnable onCancel) {
        return new ConfirmDialog(
            "Reset Changes",
            "Discard " + changeCount + " pending changes?",
            "Reset",
            "Cancel", 
            0xFFFF8800,
            onConfirm,
            onCancel
        );
    }
    
    public static ConfirmDialog unsavedExit(int changeCount, Runnable onConfirm, Runnable onCancel) {
        return new ConfirmDialog(
            "Unsaved Changes",
            "Exit without saving " + changeCount + " changes?",
            "Exit",
            "Cancel",
            0xFFFF8800,
            onConfirm,
            onCancel
        );
    }
}
