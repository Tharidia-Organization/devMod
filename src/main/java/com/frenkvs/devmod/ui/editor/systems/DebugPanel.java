package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple debug panel that displays lightweight item info and a small session log.
 */
public class DebugPanel {

    private final List<String> entries = new ArrayList<>();
    private ResponsiveLayout.Rect copyRect;
    private ResponsiveLayout.Rect exportRect;

    public DebugPanel() {
    }

    public void log(String entry) {
        if (entry == null) return;
        entries.add(0, entry);
        if (entries.size() > 64) entries.remove(entries.size() - 1);
    }

    // Package-private accessor for tests
    java.util.List<String> getEntries() { return java.util.Collections.unmodifiableList(entries); }

    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY, ItemStack current) {
        Objects.requireNonNull(font);
        int pad = 6;
        int lineH = 12;
        graphics.fill(x, y, x + width, y + height, 0xE0101020);
        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.ACCENT);

        int curY = y + pad;
        graphics.drawString(font, "Debug", x + pad, curY, 0xFFFFFF, false);
        curY += lineH;

        if (current != null) {
            String name = current.getHoverName().getString();
            graphics.drawString(font, "Item: " + name, x + pad, curY, 0xDDDDDD, false);
            curY += lineH;
            graphics.drawString(font, "Count: " + current.getCount(), x + pad, curY, 0xCCCCCC, false);
            curY += lineH;
            try {
                int dmg = current.getDamageValue();
                graphics.drawString(font, "Damage: " + dmg + "/" + current.getMaxDamage(), x + pad, curY, 0xCCCCCC, false);
            } catch (Exception ignored) {
                graphics.drawString(font, "Damage: n/a", x + pad, curY, 0xCCCCCC, false);
            }
            curY += lineH;
            // Attempt to read NBT reflectively (safe for test stubs without NBT APIs)
            String tagText = "n/a";
            try {
                var cls = current.getClass();
                try {
                    var getTag = cls.getMethod("getTag");
                    Object tag = getTag.invoke(current);
                    if (tag != null) {
                        try {
                            var sizeM = tag.getClass().getMethod("size");
                            Object sizeO = sizeM.invoke(tag);
                            tagText = String.valueOf(sizeO);
                        } catch (NoSuchMethodException ns) {
                            tagText = "present";
                        }
                    } else {
                        tagText = "0";
                    }
                } catch (NoSuchMethodException ns) {
                    tagText = "n/a";
                }
            } catch (Exception ignored) {
                tagText = "n/a";
            }
            graphics.drawString(font, "NBT tags: " + tagText, x + pad, curY, 0x8899AA, false);
            curY += lineH;

            // Copy button
            int btnW = 56;
            int btnH = 14;
            int bx = x + width - btnW - pad;
            int by = y + pad;
            copyRect = new ResponsiveLayout.Rect(bx, by, btnW, btnH);
            graphics.fill(bx, by, bx + btnW, by + btnH, 0xFF222222);
            AxiomRenderer.drawBorder(graphics, bx, by, btnW, btnH, UIConstants.Border.DEFAULT);
            graphics.drawString(font, "Copy", bx + 10, by + 3, 0xFFFFFF, false);
            
            // Export button (left of Copy)
            int exW = 56;
            int exBx = bx - exW - 6;
            exportRect = new ResponsiveLayout.Rect(exBx, by, exW, btnH);
            graphics.fill(exBx, by, exBx + exW, by + btnH, 0xFF222222);
            AxiomRenderer.drawBorder(graphics, exBx, by, exW, btnH, UIConstants.Border.DEFAULT);
            graphics.drawString(font, "Export", exBx + 8, by + 3, 0xFFFFFF, false);
        }

        // Session log (show up to 8 lines)
        curY += 6;
        graphics.drawString(font, "Log", x + pad, curY, 0xFFFFFF, false);
        curY += lineH;
        int maxLines = Math.min(8, entries.size());
        for (int i = 0; i < maxLines; i++) {
            String e = entries.get(i);
            if (e.length() > 60) e = e.substring(0, 57) + "...";
            graphics.drawString(font, e, x + pad, curY + i * lineH, 0xAAAAAA, false);
        }

        return height;
    }

    public boolean handleClick(double mouseX, double mouseY) {
        if (copyRect == null) return false;
        if (copyRect.contains(mouseX, mouseY)) {
            copyLogToClipboard();
            return true;
        }
        if (exportRect != null && exportRect.contains(mouseX, mouseY)) {
            try {
                var f = exportLogToTempFile();
                // Log a small entry indicating where it's written
                log("Exported debug log to: " + f.toString());
            } catch (Exception e) {
                log("Export failed: " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    // Package-private export helper for tests: writes to a temp file and returns its path
    java.nio.file.Path exportLogToTempFile() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        String fileName = "devmod-debug-" + System.currentTimeMillis() + ".log";
        java.nio.file.Path target = dir.resolve(fileName);
        try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(target)) {
            for (String s : entries) {
                w.write(s);
                w.newLine();
            }
        }
        return target;
    }

    private void copyLogToClipboard() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : entries) {
                sb.append(s).append("\n");
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(sb.toString());
            }
        } catch (Exception ignored) {}
    }
}
