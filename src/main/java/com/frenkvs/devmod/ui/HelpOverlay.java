package com.frenkvs.devmod.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class HelpOverlay {
    private boolean visible = false;
    private int scrollOffset = 0;
    
    private static final String[] HELP_CONTENT = {
        "§l§9DevMod Editor Help",
        "",
        "§l§eKeyboard Shortcuts:",
        "§7F1§f - Toggle this help",
        "§7F5§f - Toggle Preview/Apply mode",
        "§7F9§f - Performance debug overlay",
        "§7F10§f - Memory debug overlay", 
        "§7F11§f - Network debug overlay",
        "§7Ctrl+Enter§f - Quick apply changes",
        "§7Escape§f - Close editor",
        "§7M§f - Toggle multi-edit panel",
        "",
        "§l§eEditor Modes:",
        "§7Preview Mode§f - Changes shown instantly, not saved",
        "§7Apply Mode§f - Changes must be applied to take effect",
        "",
        "§l§eScope Settings:",
        "§7Specific§f - Changes apply only to this item",
        "§7Global§f - Changes apply to all items of this type",
        "",
        "§l§eDebug Overlays:",
        "§7Performance§f - FPS, entities, chunks",
        "§7Memory§f - RAM usage statistics",
        "§7Network§f - Connection and ping info",
        "",
        "§l§ePresets & Templates:",
        "§7Save§f - Store current settings as preset",
        "§7Load§f - Apply saved preset to item",
        "§7Templates§f - Apply predefined configurations",
        "",
        "§l§eMulti-Edit:",
        "§7Select§f - Choose multiple items in inventory",
        "§7Apply§f - Batch apply changes to selection",
        "",
        "§7Press F1 again to close help"
    };
    
    public void toggle() {
        visible = !visible;
        if (visible) {
            scrollOffset = 0;
        }
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;
        GuiGraphics safeGraphics = java.util.Objects.requireNonNull(graphics, "graphics");
        Font safeFont = java.util.Objects.requireNonNull(font, "font");
        
        // Semi-transparent background
        safeGraphics.fill(0, 0, screenWidth, screenHeight, 0xC0000000);
        
        // Help panel
        int panelWidth = 400;
        int panelHeight = 300;
        int x = (screenWidth - panelWidth) / 2;
        int y = (screenHeight - panelHeight) / 2;
        
        // Panel background
        safeGraphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF1E1E1E);
        
        // Border
        safeGraphics.fill(x, y, x + panelWidth, y + 2, 0xFF0078D4); // top
        safeGraphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF0078D4); // bottom
        safeGraphics.fill(x, y, x + 2, y + panelHeight, 0xFF0078D4); // left
        safeGraphics.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF0078D4); // right
        
        // Content area
        int contentX = x + 10;
        int contentY = y + 10;
        int contentHeight = panelHeight - 20;
        
        // Render help text with scrolling
        int lineHeight = 12;
        int maxLines = contentHeight / lineHeight;
        int startLine = scrollOffset / lineHeight;
        int endLine = Math.min(HELP_CONTENT.length, startLine + maxLines);
        
        for (int i = startLine; i < endLine; i++) {
            String line = HELP_CONTENT[i];
            int lineY = contentY + (i - startLine) * lineHeight;
            safeGraphics.drawString(safeFont, line, contentX, lineY, 0xFFFFFFFF, false);
        }
        
        // Scroll indicator
        if (HELP_CONTENT.length * lineHeight > contentHeight) {
            int scrollBarX = x + panelWidth - 8;
            int scrollBarHeight = contentHeight;
            int thumbHeight = Math.max(20, (contentHeight * contentHeight) / (HELP_CONTENT.length * lineHeight));
            int thumbY = y + 10 + (scrollOffset * (scrollBarHeight - thumbHeight)) / 
                        Math.max(1, HELP_CONTENT.length * lineHeight - contentHeight);
            
            // Scroll track
            safeGraphics.fill(scrollBarX, y + 10, scrollBarX + 6, y + 10 + scrollBarHeight, 0xFF404040);
            
            // Scroll thumb
            safeGraphics.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, 0xFF808080);
        }
    }
    
    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!visible) return false;
        
        // Click outside closes help
        int panelWidth = 400;
        int panelHeight = 300;
        int x = (800 - panelWidth) / 2; // Assume screen dimensions
        int y = (600 - panelHeight) / 2;
        
        if (mouseX < x || mouseX > x + panelWidth || mouseY < y || mouseY > y + panelHeight) {
            visible = false;
            return true;
        }
        
        return true; // Consume clicks inside panel
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible) return false;
        
        int lineHeight = 12;
        int maxScroll = Math.max(0, HELP_CONTENT.length * lineHeight - 280);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * lineHeight * 3)));
        
        return true;
    }
    
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        
        if (keyCode == GLFW.GLFW_KEY_F1 || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            visible = false;
            return true;
        }
        
        // Arrow key scrolling
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollOffset = Math.max(0, scrollOffset - 12);
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            int maxScroll = Math.max(0, HELP_CONTENT.length * 12 - 280);
            scrollOffset = Math.min(maxScroll, scrollOffset + 12);
            return true;
        }
        
        return true; // Consume all keys when visible
    }
}
