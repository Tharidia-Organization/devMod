package com.devmod.client.ui.editor.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.stats.FoodStats;

/**
 * Section for editing food effect list with add/remove/edit capabilities.
 */
public final class EffectListSection implements EditorSection.CustomSection {

    private static final int HEADER_HEIGHT = EditorDimensions.SECTION_HEADER_HEIGHT;
    private static final int ENTRY_HEIGHT = 72;
    private static final int TEXT_INSET_X = 8;
    private static final int BOTTOM_PADDING = 8;
    private static final int ADD_BUTTON_HEIGHT = 24;
    private static final int EMPTY_STATE_HEIGHT = 24;

    private final String id;
    private final String title;
    private final FoodStats stats;
    private final List<EffectEntry> entries = new ArrayList<>();
    @Nullable
    private final Consumer<String> onModify;
    private final EditorButton addButton;

    /**
     * Creates a new effect list section.
     *
     * @param id       Section identifier
     * @param title    Section title
     * @param stats    FoodStats to read/write effects from
     * @param onModify Callback when an effect is modified
     */
    public EffectListSection(String id, String title, FoodStats stats, @Nullable Consumer<String> onModify) {
        this.id = id;
        this.title = title;
        this.stats = stats;
        this.onModify = onModify;
        this.addButton = EditorButton.builder("add-effect", "+ Add Effect")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNewEffect)
            .build();
        loadEffects();
    }

    private void loadEffects() {
        entries.clear();
        List<FoodStats.FoodEffect> effects = stats.getEffects();
        for (int i = 0; i < effects.size(); i++) {
            FoodStats.FoodEffect effect = effects.get(i);
            addEffectEntry(i, effect);
        }
    }

    private void addEffectEntry(int index, FoodStats.FoodEffect effect) {
        String baseId = id + "_effect_" + index;

        EditorTextField effectIdField = new EditorTextField(baseId + "_id", "Effect ID")
            .placeholder("minecraft:regeneration")
            .maxLength(128)
            .onChange(value -> {
                effect.effectId = value;
                notifyModify("Effect ID");
            });
        effectIdField.setValue(effect.effectId);

        EditorSlider durationSlider = new EditorSlider(baseId + "_duration", "Duration", 1, 12000, effect.duration)
            .step(20)
            .format("%.0f")
            .suffix(" ticks")
            .info("20 ticks = 1 second")
            .onChange(value -> {
                effect.duration = Math.round(value);
                notifyModify("Duration");
            });

        EditorSlider amplifierSlider = new EditorSlider(baseId + "_amp", "Level", 0, 9, effect.amplifier)
            .step(1)
            .format("%.0f")
            .info("0 = Level I, 1 = Level II, etc.")
            .onChange(value -> {
                effect.amplifier = Math.round(value);
                notifyModify("Level");
            });

        EditorSlider probabilitySlider = new EditorSlider(baseId + "_prob", "Chance", 0, 100, effect.probability * 100)
            .step(5)
            .format("%.0f")
            .suffix("%")
            .onChange(value -> {
                effect.probability = value / 100f;
                notifyModify("Probability");
            });

        EditorButton removeButton = EditorButton.builder(baseId + "_remove", "×")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> removeEffect(index))
            .build();

        entries.add(new EffectEntry(index, effect, effectIdField, durationSlider, amplifierSlider, probabilitySlider, removeButton));
    }

    private void addNewEffect() {
        FoodStats.FoodEffect newEffect = new FoodStats.FoodEffect("minecraft:regeneration", 200, 0, 1.0f);
        stats.getEffects().add(newEffect);
        loadEffects();
        notifyModify("Add effect");
    }

    private void removeEffect(int index) {
        if (index >= 0 && index < stats.getEffects().size()) {
            stats.getEffects().remove(index);
            loadEffects();
            notifyModify("Remove effect");
        }
    }

    private void notifyModify(String what) {
        if (onModify != null) {
            onModify.accept(what);
        }
    }

    @Override
    @Nonnull
    public String getId() {
        return id;
    }

    @Override
    @Nonnull
    public String getLabel() {
        return title;
    }

    @Override
    public int getHeight() {
        int contentHeight = entries.isEmpty() ? EMPTY_STATE_HEIGHT : entries.size() * ENTRY_HEIGHT;
        return HEADER_HEIGHT + contentHeight + ADD_BUTTON_HEIGHT + BOTTOM_PADDING;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        int y = bounds.y();
        int x = bounds.x();
        int width = bounds.width();

        // Header
        graphics.fill(x, y, x + width, y + HEADER_HEIGHT, DesignTokens.Background.HEADER());
        UIScaleManager.drawScaledString(graphics, font, title, x + TEXT_INSET_X, y + (HEADER_HEIGHT - 8) / 2,
            DesignTokens.Text.TITLE(), false);

        // Effect count
        String countText = "(" + entries.size() + " effects)";
        int countWidth = UIScaleManager.getScaledStringWidth(font, countText);
        UIScaleManager.drawScaledString(graphics, font, countText, x + width - countWidth - TEXT_INSET_X,
            y + (HEADER_HEIGHT - 8) / 2, DesignTokens.Text.MUTED(), false);

        y += HEADER_HEIGHT;

        int contentX = x + TEXT_INSET_X;
        int contentWidth = width - TEXT_INSET_X * 2;

        if (entries.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, font, "No effects. Click '+ Add Effect' to add one.",
                contentX, y + 6, DesignTokens.Text.MUTED(), false);
            y += EMPTY_STATE_HEIGHT;
        } else {
            for (EffectEntry entry : entries) {
                y = renderEffectEntry(graphics, font, entry, contentX, y, contentWidth, mouseX, mouseY);
            }
        }

        // Add button
        addButton.render(graphics, contentX, y + 4, 100, ADD_BUTTON_HEIGHT - 8, mouseX, mouseY);
    }

    private int renderEffectEntry(@Nonnull GuiGraphics graphics, @Nonnull Font font, EffectEntry entry,
                                   int x, int y, int width, int mouseX, int mouseY) {
        // Entry background
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 4, DesignTokens.Panel.ELEVATED);

        // Effect index label + remove button
        String label = "#" + (entry.index + 1);
        UIScaleManager.drawScaledString(graphics, font, label, x + 4, y + 6, DesignTokens.Text.MUTED(), false);
        entry.removeButton.render(graphics, x + width - 22, y + 2, 18, 16, mouseX, mouseY);

        int fieldY = y + 4;

        // Effect ID field
        entry.effectIdField.render(graphics, x + 20, fieldY, width - 48, mouseX, mouseY);
        fieldY += 20;

        // Duration and Amplifier
        int halfWidth = (width - 12) / 2;
        entry.durationSlider.render(graphics, x + 4, fieldY, halfWidth, mouseX, mouseY);
        entry.amplifierSlider.render(graphics, x + halfWidth + 8, fieldY, halfWidth, mouseX, mouseY);
        fieldY += 22;

        // Probability
        entry.probabilitySlider.render(graphics, x + 4, fieldY, width - 8, mouseX, mouseY);

        return y + ENTRY_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (addButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (EffectEntry entry : entries) {
            if (entry.effectIdField.mouseClicked(mouseX, mouseY, button)) return true;
            if (entry.durationSlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (entry.amplifierSlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (entry.probabilitySlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (entry.removeButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        addButton.mouseReleased(mouseX, mouseY, button);
        for (EffectEntry entry : entries) {
            entry.durationSlider.mouseReleased(mouseX, mouseY, button);
            entry.amplifierSlider.mouseReleased(mouseX, mouseY, button);
            entry.probabilitySlider.mouseReleased(mouseX, mouseY, button);
            entry.removeButton.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (EffectEntry entry : entries) {
            if (entry.durationSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (entry.amplifierSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (entry.probabilitySlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EffectEntry entry : entries) {
            if (entry.effectIdField.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EffectEntry entry : entries) {
            if (entry.effectIdField.charTyped(codePoint, modifiers)) return true;
        }
        return false;
    }

    /**
     * Internal entry for tracking effect UI components.
     */
    private record EffectEntry(
        int index,
        FoodStats.FoodEffect effect,
        EditorTextField effectIdField,
        EditorSlider durationSlider,
        EditorSlider amplifierSlider,
        EditorSlider probabilitySlider,
        EditorButton removeButton
    ) {}
}
