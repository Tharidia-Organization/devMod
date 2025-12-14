package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Objects;

/**
 * General/fallback editor module for items that don't match specific types.
 * Provides basic editing capabilities for common item properties.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.2
 */
public class GeneralModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - General Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider stackSizeSlider;
    private EditorToggle unbreakableToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Durability Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider durabilitySlider;
    private EditorSlider repairCostSlider;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public GeneralModule() {
        super("general", "Item Editor");
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Load basic item properties
        updateSlidersFromItem();
    }

    private void updateSlidersFromItem() {
        if (stackSizeSlider != null) {
            stackSizeSlider.setValue(item.getMaxStackSize());
        }
        if (durabilitySlider != null && item.isDamageableItem()) {
            durabilitySlider.setValue(item.getMaxDamage() - item.getDamageValue());
            durabilitySlider.setMax(item.getMaxDamage());
        }
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // Create UI components
        createGeneralComponents();
        createDurabilityComponents();

        // Add tabs
        addTab(ModuleTab.of("general", "General", this::getGeneralSections));

        // Only add durability tab if item is damageable
        if (item.isDamageableItem()) {
            addTab(ModuleTab.of("durability", "Durability", this::getDurabilitySections));
        }

        // Add info tab
        addTab(ModuleTab.of("info", "Info", this::getInfoSections));

        // Sync UI with current item values
        updateSlidersFromItem();
    }

    // ═══════════════════════════════════════════════════════════════
    // GENERAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createGeneralComponents() {
        stackSizeSlider = new EditorSlider("stackSize", "Stack Size", 1, 64, item.getMaxStackSize())
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(value -> {
                // Stack size is read-only in vanilla, but could be modded
                markDirty("stack size");
            });

        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", false)
            .onChange(value -> markDirty("unbreakable"));
    }

    private List<EditorSection> getGeneralSections() {
        return List.of(
            new SliderSectionAdapter(stackSizeSlider),
            new ToggleSectionAdapter(unbreakableToggle)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DURABILITY TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDurabilityComponents() {
        int maxDurability = item.isDamageableItem() ? item.getMaxDamage() : 100;
        int currentDurability = item.isDamageableItem() ? (item.getMaxDamage() - item.getDamageValue()) : 100;

        durabilitySlider = new EditorSlider("durability", "Current Durability", 0, maxDurability, currentDurability)
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(value -> markDirty("durability"));

        repairCostSlider = new EditorSlider("repairCost", "Repair Cost", 0, 40, 0)
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .onChange(value -> markDirty("repair cost"));
    }

    private List<EditorSection> getDurabilitySections() {
        return List.of(
            new SliderSectionAdapter(durabilitySlider),
            new SliderSectionAdapter(repairCostSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // INFO TAB
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getInfoSections() {
        // Info tab shows read-only information about the item
        return List.of(new InfoSection(
            "info",
            "Item Information",
            List.of(
                "Item: " + item.getHoverName().getString(),
                "Type: " + item.getItem().getClass().getSimpleName(),
                "Max Stack: " + item.getMaxStackSize(),
                item.isDamageableItem() ? "Max Durability: " + item.getMaxDamage() : "Not Damageable",
                "Rarity: " + item.getRarity().name()
            )
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        // GeneralModule doesn't have a specific payload type
        // It uses generic item modification
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // ADAPTERS
    // ═══════════════════════════════════════════════════════════════

    private static class SliderSectionAdapter implements EditorSection.SliderSection {
        private final EditorSlider slider;

        SliderSectionAdapter(EditorSlider slider) {
            this.slider = slider;
        }

        @Override
        public String getId() { return slider.getId(); }

        @Override
        public String getLabel() { return slider.getLabel(); }

        @Override
        public int getHeight() { return slider.calculateHeight(); }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            slider.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return slider.mouseClicked(mouseX, mouseY, button); }
        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return slider.mouseReleased(mouseX, mouseY, button); }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return slider.keyPressed(keyCode, scanCode, modifiers); }
        @Override public float getValue() { return slider.getValue(); }
        @Override public void setValue(float value) { slider.setValue(value); }
        @Override public float getMin() { return slider.getMin(); }
        @Override public float getMax() { return slider.getMax(); }
        @Override public float getStep() { return slider.getStep(); }
        @Override public String getFormat() { return "%.2f"; }
        @Override public int getColor() { return UIConstants.SliderColors.NEUTRAL; }
        @Override public boolean isDragging() { return slider.isDragging(); }
        @Override public void setDragging(boolean dragging) { /* handled internally */ }
    }

    private static class ToggleSectionAdapter implements EditorSection.ToggleSection {
        private final EditorToggle toggle;

        ToggleSectionAdapter(EditorToggle toggle) {
            this.toggle = toggle;
        }

        @Override public String getId() { return toggle.getId(); }
        @Override public String getLabel() { return toggle.getLabel(); }
        @Override public int getHeight() { return EditorDimensions.TOGGLE_HEIGHT; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            toggle.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return toggle.mouseClicked(mouseX, mouseY, button); }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return toggle.keyPressed(keyCode, scanCode, modifiers); }
        @Override public boolean getValue() { return toggle.getValue(); }
        @Override public void setValue(boolean value) { toggle.setValue(value); }
    }

    private static class InfoSection implements EditorSection.CustomSection {
        private static final int LINE_HEIGHT = 12;
        private final String id;
        private final String title;
        private final List<String> lines;

        InfoSection(String id, String title, List<String> lines) {
            this.id = id;
            this.title = title;
            this.lines = lines;
        }

        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return title; }

        @Override
        public int getHeight() {
            return EditorDimensions.SECTION_HEADER_HEIGHT + lines.size() * LINE_HEIGHT + 8;
        }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            int y = bounds.y();
            // Header bar
            graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + EditorDimensions.SECTION_HEADER_HEIGHT, UIConstants.Background.HEADER);
            graphics.drawString(font, title, bounds.x() + 8, y + (EditorDimensions.SECTION_HEADER_HEIGHT - 8) / 2, UIConstants.Text.TITLE, false);
            y += EditorDimensions.SECTION_HEADER_HEIGHT;
            // Lines
            for (String line : lines) {
                graphics.drawString(font, line, bounds.x() + 8, y, UIConstants.Text.SECONDARY, false);
                y += LINE_HEIGHT;
            }
        }
    }
}
