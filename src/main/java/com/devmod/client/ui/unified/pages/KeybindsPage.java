package com.devmod.client.ui.unified.pages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.input.KeybindConflictDetector;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.SettingsPage;

public class KeybindsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 12;
    private static final int KEY_BADGE_WIDTH = 40;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int HINT_HEIGHT = 24; // Reserved space for bottom hint

    // Keybind entries organized by category
    private record KeybindEntry(@Nonnull String name, @Nonnull String key, @Nonnull String description,
                                 @Nonnull KeyMapping keyMapping) {}
    private record KeybindSection(String title, List<KeybindEntry> entries) {}

    // Conflict cache (refreshed on init)
    private Map<KeyMapping, List<KeyMapping>> conflictCache = new HashMap<>();

    private final List<KeybindSection> sections = new ArrayList<>();

    // Scroll state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;

    // Cached dimensions for scroll calculations
    private int lastContentY, lastContentHeight;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.KEYBINDS;
    }

    @Override
    public String getTitle() {
        return "Keyboard Shortcuts";
    }

    @Override
    public void init() {
        sections.clear();
        scrollOffset = 0; // Reset scroll on init

        // Build conflict cache
        conflictCache.clear();
        for (KeybindConflictDetector.KeybindConflict conflict : KeybindConflictDetector.detectConflicts()) {
            conflictCache.computeIfAbsent(conflict.devModKey(), k -> new ArrayList<>()).add(conflict.otherKey());
        }

        // Core Controls
        List<KeybindEntry> core = new ArrayList<>();
        core.add(entry("Settings Panel", KeyInputHandler.OPEN_SETTINGS_KEY, "Open this settings menu"));
        core.add(entry("Notification Center", KeyInputHandler.OPEN_NOTIFICATION_CENTER_KEY, "Open notifications and history"));
        core.add(entry("Mailbox", KeyInputHandler.OPEN_MAILBOX_KEY, "Open your mailbox messages"));
        core.add(entry("Weapon Editor", KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Edit held weapon stats"));
        core.add(entry("Telemetry Dashboard", KeyInputHandler.OPEN_DASHBOARD_KEY, "View analytics and stats"));
        core.add(entry("QA Testing", KeyInputHandler.OPEN_QA_TESTING_KEY, "Open QA testing screen"));
        core.add(entry("Testing Hub", KeyInputHandler.OPEN_TESTING_HUB_KEY, "Open unified testing hub"));
        sections.add(new KeybindSection("Core Controls", core));

        // Debug Overlays
        List<KeybindEntry> debug = new ArrayList<>();
        debug.add(entry("Debug Renderer", KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, "Toggle debug (Shift: Body Parts)"));
        debug.add(entry("Light Levels", KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY, "Show spawn-safe light levels"));
        debug.add(entry("Heatmaps", KeyInputHandler.TOGGLE_HEATMAP_KEY, "Cycle (Ctrl: Clear, Shift: Clear All)"));
        debug.add(entry("Chunk Performance", KeyInputHandler.TOGGLE_CHUNK_PERF_KEY, "Toggle chunk performance view"));
        sections.add(new KeybindSection("Debug Overlays", debug));

        // Spatial Analysis
        List<KeybindEntry> spatial = new ArrayList<>();
        spatial.add(entry("Room Bounds", KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "(Shift: Editor, Ctrl: Reload)"));
        spatial.add(entry("Vertical Levels", KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY, "Display height zone analysis"));
        spatial.add(entry("Safe Spots", KeyInputHandler.TOGGLE_SAFE_SPOT_KEY, "Highlight camping positions"));
        spatial.add(entry("Spawnability Map", KeyInputHandler.TOGGLE_SPAWNABILITY_KEY, "Show hostile spawn zones"));
        sections.add(new KeybindSection("Spatial Analysis", spatial));

        // AI & Pathfinding
        List<KeybindEntry> ai = new ArrayList<>();
        ai.add(entry("Line of Sight", KeyInputHandler.TOGGLE_LOS_KEY, "Visualize mob vision rays"));
        ai.add(entry("Pathfinding", KeyInputHandler.TOGGLE_PATHFINDING_KEY, "Show AI navigation paths"));
        sections.add(new KeybindSection("AI & Pathfinding", ai));

        // Performance
        List<KeybindEntry> perf = new ArrayList<>();
        perf.add(entry("Attribute Monitor", KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY, "Monitor entity attributes"));
        perf.add(entry("FPS Tracker", KeyInputHandler.TOGGLE_FPS_TRACKER_KEY, "Toggle FPS graph overlay"));
        perf.add(entry("Profiler", KeyInputHandler.TOGGLE_PROFILER_KEY, "Advanced performance profiler"));
        sections.add(new KeybindSection("Performance", perf));

        // Combat & Boss
        List<KeybindEntry> combat = new ArrayList<>();
        combat.add(entry("Boss Phase", KeyInputHandler.TOGGLE_BOSS_PHASE_KEY, "Show boss phase overlay"));
        combat.add(entry("Entity Density", KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY, "Toggle entity density map"));
        combat.add(entry("Skill Efficacy", KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY, "Show skill effectiveness"));
        sections.add(new KeybindSection("Combat & Boss", combat));

        // Quest System
        List<KeybindEntry> quest = new ArrayList<>();
        quest.add(entry("Quest HUD", KeyInputHandler.TOGGLE_QUEST_HUD_KEY, "Toggle quest progress HUD"));
        quest.add(entry("Quest Editor", KeyInputHandler.OPEN_QUEST_EDITOR_KEY, "Open quest editor screen"));
        quest.add(entry("Complete Task", KeyInputHandler.QUEST_COMPLETE_TASK_KEY, "Mark current task complete"));
        quest.add(entry("Endurance Quest", KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "(Shift: HUD, Ctrl: Details)"));
        quest.add(entry("Quest Continue", KeyInputHandler.QUEST_CONTINUE_KEY, "Continue after death"));
        quest.add(entry("Quest Exit", KeyInputHandler.QUEST_EXIT_KEY, "Exit/give up quest"));
        sections.add(new KeybindSection("Quest System", quest));

        // Economy
        List<KeybindEntry> economy = new ArrayList<>();
        economy.add(entry("Economy Overlay", KeyInputHandler.TOGGLE_ECONOMY_KEY, "(Shift: View, Ctrl: Sort)"));
        sections.add(new KeybindSection("Economy", economy));

        // Help
        List<KeybindEntry> help = new ArrayList<>();
        help.add(entry("Quick Help", KeyInputHandler.TOGGLE_HELP_KEY, "Show this quick reference"));
        sections.add(new KeybindSection("Help", help));
    }

    /**
     * Helper to create KeybindEntry with KeyMapping reference.
     * Validates all inputs are non-null before constructing the entry.
     */
    private KeybindEntry entry(String name, KeyMapping keyMapping, String description) {
        KeyMapping validatedMapping = Objects.requireNonNull(keyMapping, "keyMapping");
        return new KeybindEntry(
            Objects.requireNonNull(name, "name"),
            getKeyName(validatedMapping),
            Objects.requireNonNull(description, "description"),
            validatedMapping
        );
    }

    @Nonnull
    private String getKeyName(KeyMapping keyMapping) {
        String name = keyMapping.getTranslatedKeyMessage().getString();
        return name != null ? name : "?";
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Cache dimensions for scroll calculations
        lastContentY = y;
        lastContentHeight = height;

        // Calculate scroll bounds
        visibleHeight = height - HINT_HEIGHT; // Reserve space for hint
        totalContentHeight = calculateTotalContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);

        // Clamp scroll offset
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Determine effective content width (accounting for scrollbar)
        int effectiveWidth = width;
        boolean needsScrollbar = totalContentHeight > visibleHeight;
        if (needsScrollbar) {
            effectiveWidth = width - SCROLLBAR_WIDTH - 4;
        }

        // Enable scissoring to clip content
        graphics.enableScissor(x, y, x + width, y + visibleHeight);

        int currentY = y - scrollOffset;

        for (KeybindSection section : sections) {
            // Skip if section is above visible area
            if (currentY + ROW_HEIGHT * (section.entries.size() + 1) < y) {
                currentY += ROW_HEIGHT + section.entries.size() * ROW_HEIGHT + SECTION_SPACING;
                continue;
            }

            // Stop if below visible area
            if (currentY > y + visibleHeight) {
                break;
            }

            // Section header
            if (currentY >= y - ROW_HEIGHT) {
                graphics.drawString(font, section.title, x, currentY + 2, UIConstants.Text.PRIMARY(), false);
            }
            currentY += ROW_HEIGHT;

            // Keybind entries
            for (KeybindEntry entry : section.entries) {
                if (currentY >= y - ROW_HEIGHT && currentY <= y + visibleHeight) {
                    renderKeybindRow(graphics, font, x, currentY, effectiveWidth, entry, mouseX, mouseY);
                }
                currentY += ROW_HEIGHT;
            }

            currentY += SECTION_SPACING;
        }

        // Disable scissoring
        graphics.disableScissor();

        // Render scrollbar if needed
        if (needsScrollbar) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, visibleHeight);
        }

        // Hint at bottom - fixed position below scrollable content
        int hintY = y + height - HINT_HEIGHT + 4;
        // Draw background for better visibility
        graphics.fill(x - 4, hintY - 4, x + width + 4, hintY + 14, UIConstants.Background.HEADER());
        graphics.drawString(font, "ℹ To rebind keys: ESC → Options → Controls → DevMod", x, hintY, UIConstants.Text.SECONDARY(), false);
    }

    /**
     * Render the scrollbar track and thumb.
     */
    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        // Track background
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT());

        // Calculate thumb size and position
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));

        int thumbY;
        if (maxScrollOffset > 0) {
            float scrollRatio = (float) scrollOffset / maxScrollOffset;
            thumbY = y + (int) ((height - thumbHeight) * scrollRatio);
        } else {
            thumbY = y;
        }

        // Thumb
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    private void renderKeybindRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                   KeybindEntry entry, int mouseX, int mouseY) {
        int rowWidth = width - 10;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // Check for conflicts
        List<KeyMapping> conflicts = conflictCache.get(entry.keyMapping());
        boolean hasConflict = conflicts != null && !conflicts.isEmpty();

        // Hover background (tinted red if conflict)
        if (hovered) {
            int bgColor = hasConflict ? 0x30FF4444 : UIConstants.Background.HOVER();
            graphics.fill(x - 4, y - 1, x + rowWidth + 4, y + ROW_HEIGHT - 3, bgColor);
        } else if (hasConflict) {
            graphics.fill(x - 4, y - 1, x + rowWidth + 4, y + ROW_HEIGHT - 3, 0x18FF4444);
        }

        // Key badge
        int badgeX = x;
        int badgeY = y + 1;
        int badgeHeight = ROW_HEIGHT - 4;
        int textWidth = font.width(entry.key);
        int actualBadgeWidth = Math.max(KEY_BADGE_WIDTH, textWidth + 8);

        int badgeBorderColor = hasConflict ? 0xFFFF6666 : UIConstants.Border.ACCENT();
        graphics.fill(badgeX, badgeY, badgeX + actualBadgeWidth, badgeY + badgeHeight, UIConstants.Background.INPUT());
        AxiomRenderer.drawBorder(graphics, badgeX, badgeY, actualBadgeWidth, badgeHeight, badgeBorderColor);

        int keyTextX = badgeX + (actualBadgeWidth - textWidth) / 2;
        int keyTextColor = hasConflict ? 0xFFFF8888 : UIConstants.Text.TITLE();
        graphics.drawString(font, entry.key, keyTextX, badgeY + (badgeHeight - 8) / 2, keyTextColor, false);

        // Conflict warning icon
        int nameX = badgeX + actualBadgeWidth + 10;
        if (hasConflict) {
            graphics.drawString(font, "⚠", nameX - 2, y + 2, 0xFFFF6666, false);
            nameX += 12;
        }

        // Action name
        graphics.drawString(font, entry.name, nameX, y + 2, UIConstants.Text.PRIMARY(), false);

        // Description (muted, to the right) - only if space allows
        int descX = nameX + font.width(entry.name) + 16;
        int availableWidth = x + rowWidth - descX;
        if (availableWidth > 50) {
            String desc = entry.description;
            // Truncate description if too long (keep at least 6 chars for readability)
            if (font.width(desc) > availableWidth) {
                String ellipsis = "...";
                int minChars = Math.min(6, desc.length());
                while (font.width(desc + ellipsis) > availableWidth && desc.length() > minChars) {
                    desc = desc.substring(0, desc.length() - 1);
                }
                desc = desc + ellipsis;
            }
            graphics.drawString(font, desc, descX, y + 2, UIConstants.Text.MUTED(), false);
        }
    }

    /**
     * Calculate total height of all content.
     */
    private int calculateTotalContentHeight() {
        int total = 0;
        for (KeybindSection section : sections) {
            total += ROW_HEIGHT; // Section header
            total += section.entries.size() * ROW_HEIGHT; // Entries
            total += SECTION_SPACING;
        }
        return total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        // Check if click is within content area
        if (mouseX < contentX || mouseX > contentX + contentWidth ||
            mouseY < contentY || mouseY > contentY + lastContentHeight) {
            return false;
        }

        // Check scrollbar click
        if (totalContentHeight > visibleHeight) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click position
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (visibleHeight * visibleRatio));
                int trackHeight = visibleHeight - thumbHeight;

                // Jump to clicked position (centering thumb on click)
                if (trackHeight > 0) {
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2.0) / trackHeight;
                    clickRatio = Math.max(0f, Math.min(1f, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            // Calculate thumb dimensions for accurate drag
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (visibleHeight * visibleRatio));
            int trackHeight = visibleHeight - thumbHeight;

            if (trackHeight > 0) {
                // Center thumb on mouse position
                float dragRatio = (float)(mouseY - lastContentY - thumbHeight / 2.0) / trackHeight;
                dragRatio = Math.max(0f, Math.min(1f, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getContentHeight() {
        return calculateTotalContentHeight() + HINT_HEIGHT;
    }
}
