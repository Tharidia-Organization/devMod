package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight dev-only debug panel used by the editor screen.
 * Shows quick item info, a small session log, and supports copying/exporting logs.
 */
public class DebugPanel {

    private final List<String> entries = new ArrayList<>();
    private List<String> statSources = List.of();
    private ResponsiveLayout.Rect nbtToggleRect;
    private boolean showFullNbt = false;
    private String lastItemDataPayload = null;

    // Buttons using EditorButton component
    private final EditorButton copyButton = new EditorButton("copy", "Copy")
        .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
        .onClick(this::copyLogToClipboard);
    private final EditorButton exportButton = new EditorButton("export", "Export")
        .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
        .onClick(this::handleExport);
    private final EditorButton copyItemButton = new EditorButton("copyItem", "Copy Item")
        .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
        .onClick(this::copyItemDataToClipboard);

    public DebugPanel() {}

    public void log(String entry) {
        if (entry == null) return;
        entries.add(0, entry);
        if (entries.size() > 64) {
            entries.remove(entries.size() - 1);
        }
    }

    public void setStatSources(List<String> sources) {
        this.statSources = sources == null ? List.of() : List.copyOf(sources);
    }

    public void clear() {
        entries.clear();
    }

    // Package-private accessor for tests
    List<String> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY, ItemStack current) {
        Objects.requireNonNull(font, "font cannot be null");
        nbtToggleRect = null;
        lastItemDataPayload = null;

        int pad = 6;
        int lineH = 12;
        graphics.fill(x, y, x + width, y + height, 0xE0101020);
        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.ACCENT);

        int curY = y + pad;
        graphics.drawString(font, "Debug", x + pad, curY, 0xFFFFFF, false);
        curY += lineH;

        CompoundTag resolvedTag = null;

        // Toggle hint for NBT view
        nbtToggleRect = new ResponsiveLayout.Rect(x + pad, curY, 90, lineH);
        graphics.drawString(font, showFullNbt ? "NBT: full" : "NBT: summary", nbtToggleRect.x(), nbtToggleRect.y(), 0x88CCFF, false);
        curY += lineH;

        if (current != null) {
            // Item identification
            String name = current.getHoverName().getString();
            graphics.drawString(font, "Item: " + name, x + pad, curY, 0xDDDDDD, false);
            curY += lineH;

            try {
                graphics.drawString(font, "Count: " + current.getCount(), x + pad, curY, 0xCCCCCC, false);
            } catch (Throwable ignored) {
                graphics.drawString(font, "Count: n/a", x + pad, curY, 0xCCCCCC, false);
            }
            curY += lineH;

            try {
                graphics.drawString(font, "Damage: " + current.getDamageValue() + "/" + current.getMaxDamage(), x + pad, curY, 0xCCCCCC, false);
            } catch (Throwable ignored) {
                graphics.drawString(font, "Damage: n/a", x + pad, curY, 0xCCCCCC, false);
            }
            curY += lineH;

            String tagText = "n/a";
            try {
                // Prefer reading editor custom data when available (safer across mappings)
                try {
                    var customType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
                    CustomData customData = current.get(customType);
                    if (customData != null) {
                        CompoundTag copied = customData.copyTag();
                        if (copied != null && !copied.isEmpty()) {
                            resolvedTag = copied;
                            tagText = String.valueOf(copied.getAllKeys().size());
                        }
                    }
                } catch (Throwable ignoredInner) {
                    // ignore and fall back to reflection below
                }

                // Reflection fallback: try to call ItemStack#getTag() if present
                if (resolvedTag == null) {
                    var cls = current.getClass();
                    var getTag = cls.getMethod("getTag");
                    Object tagObj = getTag.invoke(current);
                    if (tagObj instanceof CompoundTag ct) {
                        resolvedTag = ct;
                        tagText = String.valueOf(ct.getAllKeys().size());
                    } else if (tagObj != null) {
                        try {
                            var sizeM = tagObj.getClass().getMethod("size");
                            Object sizeO = sizeM.invoke(tagObj);
                            tagText = String.valueOf(sizeO);
                        } catch (NoSuchMethodException ns) {
                            tagText = "present";
                        }
                    } else {
                        tagText = "0";
                    }
                }
            } catch (Exception ignored) {
                tagText = "n/a";
            }
            graphics.drawString(font, "NBT tags: " + tagText, x + pad, curY, 0x8899AA, false);
            curY += lineH;

            if (!statSources.isEmpty()) {
                graphics.drawString(font, "Source:", x + pad, curY, 0xFFFFFF, false);
                curY += lineH;
                for (String src : statSources) {
                    graphics.drawString(font, "• " + src, x + pad + 4, curY, 0xCCDDFF, false);
                    curY += lineH;
                }
            }

            // Action buttons using EditorButton components
            int btnW = 56;
            int btnH = EditorButton.Size.SMALL.height();
            int btnGap = 6;
            int by = y + pad;

            // Position buttons from right to left
            int copyX = x + width - btnW - pad;
            int exportX = copyX - btnW - btnGap;
            int copyItemX = exportX - 72 - btnGap;  // Copy Item is wider

            copyButton.render(graphics, copyX, by, btnW, btnH, mouseX, mouseY);
            exportButton.render(graphics, exportX, by, btnW, btnH, mouseX, mouseY);
            copyItemButton.render(graphics, copyItemX, by, 72, btnH, mouseX, mouseY);

            // Build item data payload for the copy button
            populateItemPayload(resolvedTag);
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

        if (showFullNbt) {
            String nbtSummary = buildNbtSummary(resolvedTag);
            graphics.drawString(font, nbtSummary, x + pad, y + height - lineH - 4, 0xFFCCAA, false);
        }

        return height;
    }

    public boolean handleClick(double mouseX, double mouseY) {
        // Delegate to EditorButton components
        if (copyButton.mouseClicked(mouseX, mouseY, 0)) {
            copyButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (exportButton.mouseClicked(mouseX, mouseY, 0)) {
            exportButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (copyItemButton.mouseClicked(mouseX, mouseY, 0)) {
            copyItemButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (nbtToggleRect != null && nbtToggleRect.contains(mouseX, mouseY)) {
            showFullNbt = !showFullNbt;
            return true;
        }
        return false;
    }

    private void handleExport() {
        try {
            var f = exportLogToTempFile();
            log("Exported debug log to: " + f.toString());
        } catch (Exception e) {
            log("Export failed: " + e.getMessage());
        }
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

    // Export only the most recent N entries
    public java.nio.file.Path exportRecentToTempFile(int recent) throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        String fileName = "devmod-debug-recent-" + System.currentTimeMillis() + ".log";
        java.nio.file.Path target = dir.resolve(fileName);
        try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(target)) {
            int limit = Math.min(recent, entries.size());
            for (int i = 0; i < limit; i++) {
                w.write(entries.get(i));
                w.newLine();
            }
        }
        return target;
    }

    private void copyLogToClipboard() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : entries) {
                sb.append(java.util.Objects.requireNonNullElse(s, "")).append("\n");
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(java.util.Objects.requireNonNull(sb.toString(), "clipboard payload cannot be null"));
            }
        } catch (Exception ignored) {
            // best-effort only
        }
    }

    private void copyItemDataToClipboard() {
        try {
            String payload = lastItemDataPayload == null ? "<no item data>" : lastItemDataPayload;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(java.util.Objects.requireNonNull(payload, "payload cannot be null"));
            }
            log("Copied item data to clipboard");
        } catch (Exception ignored) {
            // best-effort only
        }
    }

    private void populateItemPayload(CompoundTag tag) {
        if (tag == null) {
            lastItemDataPayload = null;
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (tag.contains("WeaponModStats")) {
            CompoundTag wtag = tag.getCompound("WeaponModStats");
            WeaponStats ws = WeaponStats.load(wtag);
            sb.append("WeaponStats: ").append(ws).append("\n").append(wtag).append("\n");
        }
        if (tag.contains("ArmorModStats")) {
            CompoundTag atag = tag.getCompound("ArmorModStats");
            ArmorStats as = ArmorStats.load(atag);
            sb.append("ArmorStats: ").append(as).append("\n").append(atag).append("\n");
        }
        if (sb.length() == 0) {
            sb.append(tag);
        }
        lastItemDataPayload = sb.toString();
    }

    private String buildNbtSummary(CompoundTag tag) {
        if (tag == null) return "NBT: <none>";
        String s = tag.toString();
        if (s.length() > 200) s = s.substring(0, 197) + "...";
        return "NBT: " + s;
    }
}
