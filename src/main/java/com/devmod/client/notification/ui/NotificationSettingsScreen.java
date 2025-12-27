package com.devmod.client.notification.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.notification.NotificationSoundManager;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

/**
 * Settings screen for notification preferences.
 *
 * <p>Allows players to configure:
 * <ul>
 *   <li>Global mute toggle</li>
 *   <li>Master volume</li>
 *   <li>Per-category overlay/sound toggles</li>
 *   <li>Minimum priority threshold</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class NotificationSettingsScreen extends Screen {

    // ============================================================================
    // CONSTANTS
    // ============================================================================

    private static final int HEADER_HEIGHT = 40;
    private static final int PADDING = 16;
    private static final int ROW_HEIGHT = 28;
    private static final int TOGGLE_WIDTH = 40;
    private static final int SLIDER_WIDTH = 100;

    // ============================================================================
    // STATE
    // ============================================================================

    @Nullable
    private final Screen parent;
    private final NotificationSoundManager.ClientNotificationPreferences soundPrefs =
            NotificationSoundManager.ClientNotificationPreferences.INSTANCE;
    private final List<CategoryRow> categoryRows = new ArrayList<>();

    private boolean globalMute = false;
    private float masterVolume = 1.0f;
    private NotificationPriority minPriority = NotificationPriority.LOW;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    // ============================================================================
    // CONSTRUCTION
    // ============================================================================

    public NotificationSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("devmod.notification.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Back button
        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> onClose()
        ).bounds(PADDING, height - 30, 80, 20).build());

        // Reset button
        addRenderableWidget(Button.builder(
                Component.translatable("controls.reset"),
                btn -> resetToDefaults()
        ).bounds(width - PADDING - 80, height - 30, 80, 20).build());

        // Initialize category rows
        initCategoryRows();
        // Load current preferences
        loadPreferences();
        calculateMaxScroll();
    }

    private void loadPreferences() {
        // Load from NotificationSoundManager preferences
        globalMute = soundPrefs.isGlobalMute();
        masterVolume = soundPrefs.getMasterVolume();
        minPriority = NotificationPriority.LOW;

        for (CategoryRow row : categoryRows) {
            row.soundEnabled = soundPrefs.isSoundEnabled(row.category);
            row.volume = soundPrefs.getCategoryVolume(row.category);
        }
    }

    private void initCategoryRows() {
        categoryRows.clear();

        for (NotificationCategory category : NotificationCategory.values()) {
            categoryRows.add(new CategoryRow(category));
        }
    }

    private void calculateMaxScroll() {
        int contentHeight = HEADER_HEIGHT + (categoryRows.size() * ROW_HEIGHT) + 100;
        int visibleHeight = height - 60;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
    }

    // ============================================================================
    // RENDERING
    // ============================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Header
        renderHeader(graphics);

        // Global settings section
        int y = HEADER_HEIGHT + PADDING - scrollOffset;
        y = renderGlobalSettings(graphics, mouseX, mouseY, y);

        // Category settings
        y += PADDING;
        renderCategoryHeader(graphics, y);
        y += ROW_HEIGHT;

        for (CategoryRow row : categoryRows) {
            if (y + ROW_HEIGHT > HEADER_HEIGHT && y < height - 60) {
                row.render(graphics, PADDING, y, width - PADDING * 2, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        // Header background
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xCC1A1A1A);

        // Title
        Component title = Component.translatable("devmod.notification.settings.title");
        graphics.drawCenteredString(Objects.requireNonNull(font), Objects.requireNonNull(title), width / 2, 14, 0xFFFFFF);

        // Separator line
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF333333);
    }

    private int renderGlobalSettings(GuiGraphics graphics, int mouseX, int mouseY, int startY) {
        int y = startY;
        int labelX = PADDING;
        int controlX = width - PADDING - TOGGLE_WIDTH;

        // Section header
        graphics.drawString(font, Component.translatable("devmod.notification.settings.global"),
                labelX, y, 0xFFD700, true);
        y += ROW_HEIGHT;

        // Global mute toggle
        graphics.drawString(font, Component.translatable("devmod.notification.settings.global_mute"),
                labelX + 10, y + 6, 0xAAAAAA, false);
        renderToggle(graphics, controlX, y, TOGGLE_WIDTH, 20, globalMute, mouseX, mouseY);
        y += ROW_HEIGHT;

        // Master volume slider
        graphics.drawString(font, Component.translatable("devmod.notification.settings.master_volume"),
                labelX + 10, y + 6, 0xAAAAAA, false);
        renderSlider(graphics, controlX - SLIDER_WIDTH + TOGGLE_WIDTH, y, SLIDER_WIDTH, 20, masterVolume);
        y += ROW_HEIGHT;

        // Min priority dropdown
        graphics.drawString(font, Component.translatable("devmod.notification.settings.min_priority"),
                labelX + 10, y + 6, 0xAAAAAA, false);
        renderPrioritySelector(graphics, controlX - 60, y, 100, 20, minPriority);
        y += ROW_HEIGHT;

        return y;
    }

    private void renderCategoryHeader(GuiGraphics graphics, int y) {
        int labelX = PADDING;
        graphics.drawString(font, Component.translatable("devmod.notification.settings.categories"),
                labelX, y + 6, 0xFFD700, true);

        // Column headers
        int overlayX = width - PADDING - TOGGLE_WIDTH * 2 - 10;
        int soundX = width - PADDING - TOGGLE_WIDTH;

        graphics.drawString(font, Component.translatable("devmod.notification.settings.overlay"),
                overlayX, y + 6, 0x888888, false);
        graphics.drawString(font, Component.translatable("devmod.notification.settings.sound"),
                soundX, y + 6, 0x888888, false);
    }

    private void renderToggle(GuiGraphics graphics, int x, int y, int width, int height,
                               boolean value, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        int bgColor = value ? 0xFF2E7D32 : 0xFF424242;
        if (hovered) {
            bgColor = value ? 0xFF388E3C : 0xFF616161;
        }
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Toggle indicator
        int indicatorX = value ? x + width - height + 2 : x + 2;
        graphics.fill(indicatorX, y + 2, indicatorX + height - 4, y + height - 2, 0xFFFFFFFF);
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int width, int height, float value) {
        // Track
        graphics.fill(x, y + height / 2 - 2, x + width, y + height / 2 + 2, 0xFF424242);

        // Filled portion
        int filledWidth = (int) (width * value);
        graphics.fill(x, y + height / 2 - 2, x + filledWidth, y + height / 2 + 2, 0xFF2196F3);

        // Handle
        int handleX = x + filledWidth - 4;
        graphics.fill(handleX, y + 2, handleX + 8, y + height - 2, 0xFFFFFFFF);

        // Value label
        String label = String.format("%.0f%%", value * 100);
        graphics.drawString(font, label, x + width + 5, y + 6, 0xAAAAAA, false);
    }

    private void renderPrioritySelector(GuiGraphics graphics, int x, int y, int width, int height,
                                         NotificationPriority priority) {
        // Background
        graphics.fill(x, y, x + width, y + height, 0xFF424242);

        // Current value
        String label = priority.name();
        graphics.drawString(font, label, x + 5, y + 6, 0xFFFFFF, false);
    }

    // ============================================================================
    // INPUT HANDLING
    // ============================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int y = HEADER_HEIGHT + PADDING - scrollOffset;

        // Skip global section header
        y += ROW_HEIGHT;

        // Global mute toggle
        int controlX = width - PADDING - TOGGLE_WIDTH;
        if (isInBounds(mouseX, mouseY, controlX, y, TOGGLE_WIDTH, 20)) {
            globalMute = !globalMute;
            soundPrefs.setGlobalMute(globalMute);
            return true;
        }
        y += ROW_HEIGHT;

        // Master volume slider click
        int sliderX = controlX - SLIDER_WIDTH + TOGGLE_WIDTH;
        if (isInBounds(mouseX, mouseY, sliderX, y, SLIDER_WIDTH, 20)) {
            masterVolume = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / SLIDER_WIDTH));
            soundPrefs.setMasterVolume(masterVolume);
            return true;
        }
        y += ROW_HEIGHT;

        // Priority selector (cycle through)
        if (isInBounds(mouseX, mouseY, controlX - 60, y, 100, 20)) {
            int nextOrdinal = (minPriority.ordinal() + 1) % NotificationPriority.values().length;
            minPriority = NotificationPriority.values()[nextOrdinal];
            return true;
        }
        y += ROW_HEIGHT;

        // Category rows
        y += PADDING + ROW_HEIGHT; // Skip category header

        for (CategoryRow row : categoryRows) {
            if (row.handleClick(mouseX, mouseY, PADDING, y, width - PADDING * 2)) {
                return true;
            }
            y += ROW_HEIGHT;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 20));
        return true;
    }

    private boolean isInBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ============================================================================
    // ACTIONS
    // ============================================================================

    private void resetToDefaults() {
        globalMute = false;
        masterVolume = 1.0f;
        minPriority = NotificationPriority.LOW;

        soundPrefs.setGlobalMute(false);
        soundPrefs.setMasterVolume(1.0f);

        for (NotificationCategory category : NotificationCategory.values()) {
            soundPrefs.setCategoryVolume(category, 1.0f);
        }

        for (CategoryRow row : categoryRows) {
            row.overlayEnabled = row.category.isDefaultOverlayEnabled();
            row.soundEnabled = true;
            row.volume = 1.0f;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // ============================================================================
    // CATEGORY ROW
    // ============================================================================

    private class CategoryRow {
        final NotificationCategory category;
        boolean overlayEnabled;
        boolean soundEnabled;
        float volume;

        CategoryRow(NotificationCategory category) {
            this.category = category;
            this.overlayEnabled = category.isDefaultOverlayEnabled();
            this.soundEnabled = true;
            this.volume = 1.0f;
        }

        void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            // Category name
            Component name = Component.translatable(category.getTranslationKey());
            graphics.drawString(font, name, x + 10, y + 6, 0xCCCCCC, false);

            // Overlay toggle
            int overlayX = x + width - TOGGLE_WIDTH * 2 - 10;
            renderToggle(graphics, overlayX, y, TOGGLE_WIDTH, 20, overlayEnabled, mouseX, mouseY);

            // Sound toggle
            int soundX = x + width - TOGGLE_WIDTH;
            renderToggle(graphics, soundX, y, TOGGLE_WIDTH, 20, soundEnabled, mouseX, mouseY);
        }

        boolean handleClick(double mouseX, double mouseY, int x, int y, int width) {
            // Check overlay toggle
            int overlayX = x + width - TOGGLE_WIDTH * 2 - 10;
            if (isInBounds(mouseX, mouseY, overlayX, y, TOGGLE_WIDTH, 20)) {
                overlayEnabled = !overlayEnabled;
                return true;
            }

            // Check sound toggle
            int soundX = x + width - TOGGLE_WIDTH;
            if (isInBounds(mouseX, mouseY, soundX, y, TOGGLE_WIDTH, 20)) {
                soundEnabled = !soundEnabled;
                soundPrefs.setCategoryVolume(category, soundEnabled ? volume : 0f);
                return true;
            }

            return false;
        }
    }
}
