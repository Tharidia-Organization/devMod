package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;

public class DebugPanel {

    private static final int MAX_ENTRIES = 64;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 12;
    private static final int NBT_TOGGLE_WIDTH = 90;
    private static final int NBT_TOGGLE_COLOR = 0x88CCFF;
    private static final int NBT_SUMMARY_COLOR = 0xFFCCAA;
    private static final int HEADER_TEXT_COLOR = 0xFFFFFF;
    private static final int ITEM_TEXT_COLOR = 0xDDDDDD;
    private static final int ITEM_DETAIL_COLOR = 0xCCCCCC;
    private static final int NBT_COUNT_COLOR = 0x8899AA;
    private static final int SOURCE_TEXT_COLOR = 0xCCDDFF;
    private static final int LOG_TEXT_COLOR = 0xAAAAAA;
    private static final int PANEL_BG = 0xE0101020;
    private static final int LOG_TITLE_GAP = 6;
    private static final int LOG_MAX_LINES = 8;
    private static final int LOG_TRUNCATE_LIMIT = 60;
    private static final int NBT_TRUNCATE_LIMIT = 200;
    private static final int NBT_SUMMARY_BOTTOM_PADDING = 4;
    private static final int SUBITEM_INDENT = 4;
    private static final String ELLIPSIS = "...";
    private static final int ACTION_BUTTON_WIDTH = 56;
    private static final int ACTION_BUTTON_WIDE_WIDTH = 72;
    private static final int ACTION_BUTTON_GAP = 6;
    private static final int ACTION_BUTTON_MOUSE_BUTTON = 0;
    private static final int MIN_TAG_COUNT = 0;

    private static final String TITLE_TEXT = "Debug";
    private static final String LOG_TITLE_TEXT = "Log";
    private static final String NBT_FULL_LABEL = "NBT: full";
    private static final String NBT_SUMMARY_LABEL = "NBT: summary";
    private static final String ITEM_PREFIX = "Item: ";
    private static final String COUNT_PREFIX = "Count: ";
    private static final String DAMAGE_PREFIX = "Damage: ";
    private static final String DAMAGE_SEPARATOR = "/";
    private static final String NA_TEXT = "n/a";
    private static final String NBT_COUNT_PREFIX = "NBT tags: ";
    private static final String SOURCE_LABEL = "Source:";
    private static final String SOURCE_BULLET = "• ";
    private static final String NBT_NONE_TEXT = "NBT: <none>";
    private static final String WEAPON_STATS_KEY = "WeaponModStats";
    private static final String ARMOR_STATS_KEY = "ArmorModStats";
    private static final String WEAPON_STATS_PREFIX = "WeaponStats: ";
    private static final String ARMOR_STATS_PREFIX = "ArmorStats: ";
    private static final String NEWLINE = "\n";
    private static final String DEBUG_EXPORT_PREFIX = "devmod-debug-";
    private static final String DEBUG_EXPORT_RECENT_PREFIX = "devmod-debug-recent-";
    private static final String LOG_EXTENSION = ".log";
    private static final String EXPORT_SUCCESS_PREFIX = "Exported debug log to: ";
    private static final String EXPORT_FAIL_PREFIX = "Export failed: ";
    private static final String NO_ITEM_DATA_TEXT = "<no item data>";
    private static final String COPY_ITEM_STATUS = "Copied item data to clipboard";

    private final List<String> entries = new ArrayList<>();
    private List<String> statSources = List.of();
    private List<StatDiff> statDiffs = List.of();
    private ResponsiveLayout.Rect nbtToggleRect;
    private boolean showFullNbt = false;
    private String lastItemDataPayload = null;

    /**
     * Represents a difference between expected (baseline) and actual (current) stat values.
     */
    public record StatDiff(String name, float expected, float actual) {
        public boolean isDifferent() {
            return Math.abs(expected - actual) > 0.001f;
        }
        public String format() {
            if (!isDifferent()) return name + ": " + String.format("%.2f", actual) + " ✓";
            return name + ": " + String.format("%.2f", expected) + " → " + String.format("%.2f", actual) + " ⚠";
        }
    }

    // Buttons using EditorButton component (lazy init to avoid this-escape)
    private EditorButton copyButton;
    private EditorButton exportButton;
    private EditorButton copyItemButton;
    private boolean buttonsInitialized = false;

    public DebugPanel() {
        // Buttons initialized lazily to avoid this-escape warning
    }

    /** Ensures buttons are initialized (lazy init to avoid this-escape). */
    private void ensureButtons() {
        if (!buttonsInitialized) {
            copyButton = new EditorButton("copy", "Copy")
                .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
                .onClick(this::copyLogToClipboard);
            exportButton = new EditorButton("export", "Export")
                .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
                .onClick(this::handleExport);
            copyItemButton = new EditorButton("copyItem", "Copy Item")
                .style(EditorButton.Style.GHOST).size(EditorButton.Size.SMALL)
                .onClick(this::copyItemDataToClipboard);
            buttonsInitialized = true;
        }
    }

    public void log(String entry) {
        if (entry == null) return;
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public void setStatSources(List<String> sources) {
        this.statSources = sources == null ? List.of() : List.copyOf(sources);
    }

    /**
     * Set stat differences for debug display (expected vs actual values).
     * Call this from modules to show baseline comparison.
     */
    public void setStatDiffs(List<StatDiff> diffs) {
        this.statDiffs = diffs == null ? List.of() : List.copyOf(diffs);
    }

    /**
     * Build stat diffs from WeaponStats comparing current vs baseline.
     */
    public static List<StatDiff> buildWeaponDiffs(WeaponStats current, WeaponStats baseline) {
        if (current == null || baseline == null) return List.of();
        List<StatDiff> diffs = new ArrayList<>();
        diffs.add(new StatDiff("attackDamage", baseline.attackDamage, current.attackDamage));
        diffs.add(new StatDiff("attackSpeed", baseline.attackSpeed, current.attackSpeed));
        diffs.add(new StatDiff("attackReach", baseline.attackReach, current.attackReach));
        diffs.add(new StatDiff("attackKnockback", baseline.attackKnockback, current.attackKnockback));
        diffs.add(new StatDiff("armorPenetration", baseline.armorPenetration, current.armorPenetration));
        diffs.add(new StatDiff("critChance", baseline.critChance, current.critChance));
        diffs.add(new StatDiff("critDamage", baseline.critDamage, current.critDamage));
        diffs.add(new StatDiff("lifesteal", baseline.lifesteal, current.lifesteal));
        diffs.add(new StatDiff("headMult", baseline.headMult, current.headMult));
        diffs.add(new StatDiff("bodyMult", baseline.bodyMult, current.bodyMult));
        return diffs;
    }

    /**
     * Build stat diffs from ArmorStats comparing current vs baseline.
     */
    public static List<StatDiff> buildArmorDiffs(ArmorStats current, ArmorStats baseline) {
        if (current == null || baseline == null) return List.of();
        List<StatDiff> diffs = new ArrayList<>();
        diffs.add(new StatDiff("physicalReduction", baseline.physicalReduction, current.physicalReduction));
        diffs.add(new StatDiff("fireReduction", baseline.fireReduction, current.fireReduction));
        diffs.add(new StatDiff("magicReduction", baseline.magicReduction, current.magicReduction));
        diffs.add(new StatDiff("explosionReduction", baseline.explosionReduction, current.explosionReduction));
        diffs.add(new StatDiff("projectileReduction", baseline.projectileReduction, current.projectileReduction));
        diffs.add(new StatDiff("armorBonus", baseline.armorBonus, current.armorBonus));
        diffs.add(new StatDiff("toughnessBonus", baseline.toughnessBonus, current.toughnessBonus));
        diffs.add(new StatDiff("knockbackResistance", baseline.knockbackResistance, current.knockbackResistance));
        diffs.add(new StatDiff("thornsPercent", baseline.thornsPercent, current.thornsPercent));
        return diffs;
    }

    public void clear() {
        entries.clear();
    }

    // Package-private accessor for tests
    List<String> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY, ItemStack current) {
        ensureButtons();
        Objects.requireNonNull(font, "font cannot be null");
        nbtToggleRect = null;
        lastItemDataPayload = null;

        int pad = PANEL_PADDING;
        int lineH = LINE_HEIGHT;
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.ACCENT());

        int curY = y + pad;
        graphics.drawString(font, TITLE_TEXT, x + pad, curY, HEADER_TEXT_COLOR, false);
        curY += lineH;

        CompoundTag resolvedTag = null;

        // Toggle hint for NBT view
        nbtToggleRect = new ResponsiveLayout.Rect(x + pad, curY, NBT_TOGGLE_WIDTH, lineH);
        graphics.drawString(font, showFullNbt ? NBT_FULL_LABEL : NBT_SUMMARY_LABEL, nbtToggleRect.x(),
            nbtToggleRect.y(),
            NBT_TOGGLE_COLOR, false);
        curY += lineH;

        if (current != null) {
            // Item identification
            String name = current.getHoverName().getString();
            graphics.drawString(font, ITEM_PREFIX + name, x + pad, curY, ITEM_TEXT_COLOR, false);
            curY += lineH;

            try {
                graphics.drawString(font, COUNT_PREFIX + current.getCount(), x + pad, curY, ITEM_DETAIL_COLOR, false);
            } catch (Throwable ignored) {
                graphics.drawString(font, COUNT_PREFIX + NA_TEXT, x + pad, curY, ITEM_DETAIL_COLOR, false);
            }
            curY += lineH;

            try {
                graphics.drawString(font, DAMAGE_PREFIX + current.getDamageValue() + DAMAGE_SEPARATOR
                        + current.getMaxDamage(),
                    x + pad, curY, ITEM_DETAIL_COLOR, false);
            } catch (Throwable ignored) {
                graphics.drawString(font, DAMAGE_PREFIX + NA_TEXT, x + pad, curY, ITEM_DETAIL_COLOR, false);
            }
            curY += lineH;

            String tagText = NA_TEXT;
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
                        tagText = String.valueOf(MIN_TAG_COUNT);
                    }
                }
            } catch (Exception ignored) {
                tagText = NA_TEXT;
            }
            graphics.drawString(font, NBT_COUNT_PREFIX + tagText, x + pad, curY, NBT_COUNT_COLOR, false);
            curY += lineH;

            if (!statSources.isEmpty()) {
                graphics.drawString(font, SOURCE_LABEL, x + pad, curY, HEADER_TEXT_COLOR, false);
                curY += lineH;
                for (String src : statSources) {
                    graphics.drawString(font, SOURCE_BULLET + src, x + pad + SUBITEM_INDENT, curY, SOURCE_TEXT_COLOR, false);
                    curY += lineH;
                }
            }

            // Show stat diffs (expected vs actual) if available
            if (!statDiffs.isEmpty()) {
                curY += LOG_TITLE_GAP;
                graphics.drawString(font, "Diff (baseline → current):", x + pad, curY, HEADER_TEXT_COLOR, false);
                curY += lineH;
                int diffCount = 0;
                for (StatDiff diff : statDiffs) {
                    if (diffCount >= 6) break; // Limit display to avoid overflow
                    int color = diff.isDifferent() ? 0xFFCC66 : 0x88FF88; // Yellow for diff, green for match
                    graphics.drawString(font, SOURCE_BULLET + diff.format(), x + pad + SUBITEM_INDENT, curY, color, false);
                    curY += lineH;
                    diffCount++;
                }
                if (statDiffs.size() > 6) {
                    graphics.drawString(font, "  +" + (statDiffs.size() - 6) + " more...", x + pad + SUBITEM_INDENT, curY, LOG_TEXT_COLOR, false);
                    curY += lineH;
                }
            }

            // Action buttons using EditorButton components
            int btnW = ACTION_BUTTON_WIDTH;
            int btnH = EditorButton.Size.SMALL.height();
            int btnGap = ACTION_BUTTON_GAP;
            int by = y + pad;

            // Position buttons from right to left
            int copyX = x + width - btnW - pad;
            int exportX = copyX - btnW - btnGap;
            int copyItemX = exportX - ACTION_BUTTON_WIDE_WIDTH - btnGap;  // Copy Item is wider

            copyButton.render(graphics, copyX, by, btnW, btnH, mouseX, mouseY);
            exportButton.render(graphics, exportX, by, btnW, btnH, mouseX, mouseY);
            copyItemButton.render(graphics, copyItemX, by, ACTION_BUTTON_WIDE_WIDTH, btnH, mouseX, mouseY);

            // Build item data payload for the copy button
            populateItemPayload(resolvedTag);
        }

        // Session log (show up to 8 lines)
        curY += LOG_TITLE_GAP;
        graphics.drawString(font, LOG_TITLE_TEXT, x + pad, curY, HEADER_TEXT_COLOR, false);
        curY += lineH;
        int maxLines = Math.min(LOG_MAX_LINES, entries.size());
        for (int i = 0; i < maxLines; i++) {
            String e = entries.get(i);
            if (e.length() > LOG_TRUNCATE_LIMIT) {
                e = e.substring(0, LOG_TRUNCATE_LIMIT - ELLIPSIS.length()) + ELLIPSIS;
            }
            graphics.drawString(font, e, x + pad, curY + i * lineH, LOG_TEXT_COLOR, false);
        }

        if (showFullNbt) {
            String nbtSummary = buildNbtSummary(resolvedTag);
            graphics.drawString(font, nbtSummary, x + pad, y + height - lineH - NBT_SUMMARY_BOTTOM_PADDING,
                NBT_SUMMARY_COLOR, false);
        }

        return height;
    }

    public boolean handleClick(double mouseX, double mouseY) {
        // Delegate to EditorButton components
        if (copyButton.mouseClicked(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON)) {
            copyButton.mouseReleased(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON);
            return true;
        }
        if (exportButton.mouseClicked(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON)) {
            exportButton.mouseReleased(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON);
            return true;
        }
        if (copyItemButton.mouseClicked(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON)) {
            copyItemButton.mouseReleased(mouseX, mouseY, ACTION_BUTTON_MOUSE_BUTTON);
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
            log(EXPORT_SUCCESS_PREFIX + f.toString());
        } catch (Exception e) {
            log(EXPORT_FAIL_PREFIX + e.getMessage());
        }
    }

    // Package-private export helper for tests: writes to a temp file and returns its path
    java.nio.file.Path exportLogToTempFile() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        String fileName = DEBUG_EXPORT_PREFIX + System.currentTimeMillis() + LOG_EXTENSION;
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
        String fileName = DEBUG_EXPORT_RECENT_PREFIX + System.currentTimeMillis() + LOG_EXTENSION;
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
                sb.append(java.util.Objects.requireNonNullElse(s, "")).append(NEWLINE);
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
            String payload = lastItemDataPayload == null ? NO_ITEM_DATA_TEXT : lastItemDataPayload;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(java.util.Objects.requireNonNull(payload, "payload cannot be null"));
            }
            log(COPY_ITEM_STATUS);
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
        if (tag.contains(WEAPON_STATS_KEY)) {
            CompoundTag wtag = tag.getCompound(WEAPON_STATS_KEY);
            WeaponStats ws = WeaponStats.load(wtag);
            sb.append(WEAPON_STATS_PREFIX).append(ws).append(NEWLINE).append(wtag).append(NEWLINE);
        }
        if (tag.contains(ARMOR_STATS_KEY)) {
            CompoundTag atag = tag.getCompound(ARMOR_STATS_KEY);
            ArmorStats as = ArmorStats.load(atag);
            sb.append(ARMOR_STATS_PREFIX).append(as).append(NEWLINE).append(atag).append(NEWLINE);
        }
        if (sb.length() == 0) {
            sb.append(tag);
        }
        lastItemDataPayload = sb.toString();
    }

    private String buildNbtSummary(CompoundTag tag) {
        if (tag == null) return NBT_NONE_TEXT;
        String s = tag.toString();
        if (s.length() > NBT_TRUNCATE_LIMIT) {
            s = s.substring(0, NBT_TRUNCATE_LIMIT - ELLIPSIS.length()) + ELLIPSIS;
        }
        return "NBT: " + s;
    }
}
