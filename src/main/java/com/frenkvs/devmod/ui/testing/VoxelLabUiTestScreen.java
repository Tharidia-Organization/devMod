package com.frenkvs.devmod.ui.testing;

import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sandbox UI per visualizzare rapidamente tutte le varianti di EditorButton.
 * Pensato per la sezione Voxel Lab / QA visuale.
 */
public class VoxelLabUiTestScreen extends Screen {

    private static final int PADDING = 16;
    private static final int GRID_COLUMNS = 2;
    private static final int GRID_SPACING = 12;
    private static final int DEMO_WIDTH = 200;

    private final EditorButton showCasesButton = new EditorButton("showcases", "Mostra tipologie")
        .style(EditorButton.Style.PRIMARY)
        .toggleable(true)
        .hotkeyHint("[Click]")
        .onToggle(v -> showCases = v);

    private boolean showCases = false;
    private final List<DemoButton> demoButtons = new ArrayList<>();
    private boolean demosBuilt = false;

    public VoxelLabUiTestScreen() {
        super(Component.literal("Voxel Lab - UI Test"));
    }

    @Override
    protected void init() {
        demosBuilt = false; // Rebuild on resize
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics);

        // Header
        var safeFont = Objects.requireNonNull(font, "font");
        graphics.drawString(safeFont, "Voxel Lab / UI Showcase", PADDING, PADDING, UIConstants.Text.TITLE, false);
        graphics.drawString(safeFont, "Click il bottone per mostrare le varianti di EditorButton", PADDING, PADDING + 14, UIConstants.Text.SECONDARY, false);

        int buttonY = PADDING + 32;
        showCasesButton.render(graphics, PADDING, buttonY, 160, showCasesButton.getSize().height(), mouseX, mouseY);

        if (showCases) {
            ensureDemoButtons();
            renderDemoGrid(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBackgroundLayer(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, UIConstants.Background.CONTENT);
    }

    private void ensureDemoButtons() {
        if (demosBuilt) return;
        demoButtons.clear();

        // Define demo buttons (variants)
        demoButtons.add(new DemoButton(new EditorButton("normal", "Normal"), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("primary", "Primary").style(EditorButton.Style.PRIMARY), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("danger", "Danger").style(EditorButton.Style.DANGER), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("success", "Success").style(EditorButton.Style.SUCCESS), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("ghost", "Ghost").style(EditorButton.Style.GHOST), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("disabled", "Disabled").enabled(false), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("icon", "Icona + Label")
            .icon("\u25B6")
            .hotkeyHint("[G]"), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("toggle-off", "Toggle Off")
            .toggleable(true)
            .style(EditorButton.Style.PRIMARY), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("toggle-on", "Toggle On")
            .toggleable(true)
            .toggled(true)
            .style(EditorButton.Style.SUCCESS), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("small", "Small Size")
            .size(EditorButton.Size.SMALL), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("large", "Large Size")
            .size(EditorButton.Size.LARGE)
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2605"), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("hotkey", "Hotkey Hint")
            .hotkeyHint("[CTRL+H]"), 0, 0, DEMO_WIDTH));

        layoutGrid();
        demosBuilt = true;
    }

    private void layoutGrid() {
        int currentY = PADDING + 72;
        int col = 0;
        int rowHeight = 0;

        for (DemoButton demo : demoButtons) {
            int btnHeight = demo.button.getSize().height();
            int x = PADDING + col * (DEMO_WIDTH + GRID_SPACING);
            int y = currentY;

            demo.x = x;
            demo.y = y;
            rowHeight = Math.max(rowHeight, btnHeight);

            col++;
            if (col >= GRID_COLUMNS) {
                col = 0;
                currentY += rowHeight + GRID_SPACING;
                rowHeight = 0;
            }
        }
    }

    private void renderDemoGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int areaTop = PADDING + 56;
        graphics.fill(PADDING - 6, areaTop, width - PADDING + 6, height - PADDING, UIConstants.Background.PANEL);
        graphics.drawString(Objects.requireNonNull(font, "font"),
            "Varianti EditorButton", PADDING, areaTop + 8, UIConstants.Text.PRIMARY, false);
        graphics.drawString(Objects.requireNonNull(font, "font"),
            "Tip: icona a sx, hotkey a dx, toggle cambia stato/colore", PADDING, areaTop + 20, UIConstants.Text.SECONDARY, false);

        for (DemoButton demo : demoButtons) {
            demo.button.render(graphics, demo.x, demo.y, demo.width, demo.button.getSize().height(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = showCasesButton.mouseClicked(mouseX, mouseY, button);
        if (handled) return true;

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = showCasesButton.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void resize(@Nonnull Minecraft mc, int newWidth, int newHeight) {
        super.resize(mc, newWidth, newHeight);
        demosBuilt = false;
    }

    private static final class DemoButton {
        final EditorButton button;
        final int width;
        int x;
        int y;

        DemoButton(EditorButton button, int x, int y, int width) {
            this.button = button;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }
}
