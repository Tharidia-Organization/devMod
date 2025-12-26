package com.devmod.client.ui.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.GridPanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.PanelConstants;
import com.devmod.client.ui.testing.panel.PanelContainer;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.client.ui.testing.panel.UIPanel;
import com.devmod.config.Config;
@OnlyIn(Dist.CLIENT)
public class VoxelLabUiTestScreen extends Screen {

    private static final int PADDING = 16;
    private static final int SIDEBAR_WIDTH = 300;

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    private final PanelContainer panelContainer = new PanelContainer();
    private final ImpactHudButtons impactButtons = new ImpactHudButtons();

    // Button showcase
    private final EditorButton showCasesButton = new EditorButton("showcases", "Show Button Variants")
        .style(EditorButton.Style.PRIMARY)
        .toggleable(true)
        .icon("\uD83C\uDFA8")
        .onToggle(v -> showCases = Boolean.TRUE.equals(v));

    private boolean showCases = false;
    private final List<DemoButton> demoButtons = new ArrayList<>();
    private boolean demosBuilt = false;

    // Status message
    private String statusMessage = "";
    private long statusMessageTime = 0;

    public VoxelLabUiTestScreen() {
        super(java.util.Objects.requireNonNull(Component.literal("Voxel Lab"), "title"));
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        demosBuilt = false;

        // Configure impact buttons
        impactButtons
            .onStatus(this::showStatus)
            .onSync(() -> {});

        // Build panel structure
        buildPanels();

        // Position container
        int sidebarX = width - SIDEBAR_WIDTH - PADDING;
        int sidebarY = PADDING;
        int sidebarHeight = height - PADDING * 2;

        panelContainer
            .bounds(sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarHeight)
            .init();

        // Initial sync
        impactButtons.syncAll();
    }

    private void buildPanels() {
        panelContainer.clearPanels();

        // Header
        panelContainer.addPanel(new HeaderPanel("IMPACT HUD SYSTEM"));

        // 2D HUD Section
        panelContainer.addPanel(
            SectionPanel.builder("section-2d", "2D HUD Overlay")
                .addButton(impactButtons.hud2dToggle())
                .addRow(impactButtons.historyToggle(), impactButtons.dpsToggle())
                .build()
        );

        // 3D Panel Section
        panelContainer.addPanel(
            SectionPanel.builder("section-3d", "3D World Panel")
                .addButton(impactButtons.panel3dToggle())
                .build()
        );

        // VFX Section (collapsible)
        UIPanel vfxContent = SectionPanel.builder("vfx-content", "Effects")
            .titleColor(UIConstants.Text.SECONDARY())
            .addButton(impactButtons.vfxMasterToggle())
            .addRow(impactButtons.vfxVortexToggle(), impactButtons.vfxSlashToggle(), impactButtons.vfxLinesToggle())
            .addSpacer(4)
            .addRow(impactButtons.intensityLow(), impactButtons.intensityMed(), impactButtons.intensityHigh(), impactButtons.intensityMax())
            .build();

        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-vfx", "VFX Effects", vfxContent, PanelConstants.COLOR_CYAN)
        );

        // Position Section (collapsible with grid)
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-pos", "HUD Position",
                GridPanel.of("grid-position", "Select Position", impactButtons.positionButtons(), 2),
                0xFFFFAA00)
        );

        // Offset Section
        panelContainer.addPanel(
            SectionPanel.builder("section-offset", "Offset")
                .addRow(impactButtons.offsetXMinus(), impactButtons.offsetXPlus(),
                       impactButtons.offsetYMinus(), impactButtons.offsetYPlus())
                .build()
        );

        // Presets Section
        panelContainer.addPanel(
            SectionPanel.builder("section-presets", "Presets")
                .withSeparator()
                .addRow(impactButtons.exportPreset(), impactButtons.importPreset())
                .addButton(impactButtons.resetDefaults())
                .build()
        );

        // Status Panel
        panelContainer.addPanel(
            StatusPanel.builder("status-panel")
                .addStatus("2D", ImpactHudOverlay::isEnabled)
                .addStatus("3D", () -> Impact3DPanelManager.INSTANCE.isEnabled())
                .addStatus("VFX", () -> getConfigBool(Config.IMPACT_VFX_ENABLED))
                .messageSupplier(() -> statusMessage)
                .build()
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // TICK & RENDER
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        super.tick();
        panelContainer.tick();
        impactButtons.syncAll();

        // Clear status message after timeout
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime > 2000) {
            statusMessage = "";
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, UIConstants.Background.CONTENT());

        var font = Objects.requireNonNull(this.font, "font");

        // Header
        graphics.drawString(font, "Voxel Lab", PADDING, PADDING, UIConstants.Text.TITLE(), false);
        graphics.drawString(font, "UI Component Showcase & Impact HUD Manager", PADDING, PADDING + 12, UIConstants.Text.SECONDARY(), false);

        // Show cases button
        showCasesButton.render(graphics, PADDING, PADDING + 30, 180, 22, mouseX, mouseY);

        // Button showcase area
        if (showCases) {
            ensureDemoButtons();
            renderDemoGrid(graphics, mouseX, mouseY);
        }

        // Impact HUD panel container
        panelContainer.render(graphics, mouseX, mouseY);

        // Offset display
        int offsetX = ImpactHudButtons.getOffsetX();
        int offsetY = ImpactHudButtons.getOffsetY();
        String offsetStr = Objects.requireNonNull(String.format("Offset: X=%d Y=%d", offsetX, offsetY), "offset string");
        int offsetStrX = width - SIDEBAR_WIDTH - PADDING - font.width(offsetStr) - 20;
        graphics.drawString(font, offsetStr, offsetStrX, PADDING + 12, 0xFFAAAAAA, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ═══════════════════════════════════════════════════════════════
    // DEMO BUTTON SHOWCASE
    // ═══════════════════════════════════════════════════════════════

    private void ensureDemoButtons() {
        if (demosBuilt) return;
        demoButtons.clear();

        int demoWidth = 180;
        demoButtons.add(new DemoButton(new EditorButton("normal", "Normal"), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("primary", "Primary").style(EditorButton.Style.PRIMARY), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("danger", "Danger").style(EditorButton.Style.DANGER), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("success", "Success").style(EditorButton.Style.SUCCESS), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("ghost", "Ghost").style(EditorButton.Style.GHOST), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("disabled", "Disabled").enabled(false), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("icon", "With Icon").icon("\u25B6").hotkeyHint("[G]"), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("toggle-off", "Toggle Off").toggleable(true).style(EditorButton.Style.PRIMARY), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("toggle-on", "Toggle On").toggleable(true).toggled(true).style(EditorButton.Style.SUCCESS), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("small", "Small").size(EditorButton.Size.SMALL), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("large", "Large").size(EditorButton.Size.LARGE).style(EditorButton.Style.PRIMARY).icon("\u2605"), demoWidth));
        demoButtons.add(new DemoButton(new EditorButton("hotkey", "Hotkey").hotkeyHint("[CTRL+H]"), demoWidth));

        layoutDemoGrid();
        demosBuilt = true;
    }

    private void layoutDemoGrid() {
        int startX = PADDING;
        int startY = PADDING + 60;
        int columns = 2;
        int spacing = 10;
        int col = 0;
        int rowY = startY;
        int rowHeight = 0;

        for (DemoButton demo : demoButtons) {
            int btnHeight = demo.button.getSize().height();
            demo.x = startX + col * (demo.width + spacing);
            demo.y = rowY;
            rowHeight = Math.max(rowHeight, btnHeight);

            col++;
            if (col >= columns) {
                col = 0;
                rowY += rowHeight + spacing;
                rowHeight = 0;
            }
        }
    }

    private void renderDemoGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int areaTop = PADDING + 56;
        int areaRight = width - SIDEBAR_WIDTH - PADDING - 20;
        int areaBottom = height - PADDING;

        graphics.fill(PADDING - 4, areaTop, areaRight, areaBottom, UIConstants.Background.PANEL());

        var font = Objects.requireNonNull(this.font, "font");
        graphics.drawString(font, "EditorButton Variants", PADDING, areaTop + 6, UIConstants.Text.PRIMARY(), false);

        for (DemoButton demo : demoButtons) {
            demo.button.render(graphics, demo.x, demo.y, demo.width, demo.button.getSize().height(), mouseX, mouseY);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showCasesButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (panelContainer.mouseClicked(mouseX, mouseY, button)) return true;

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showCasesButton.mouseReleased(mouseX, mouseY, button)) return true;
        if (panelContainer.mouseReleased(mouseX, mouseY, button)) return true;

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseReleased(mouseX, mouseY, button)) return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (panelContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panelContainer.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void resize(@Nonnull Minecraft mc, int newWidth, int newHeight) {
        super.resize(mc, newWidth, newHeight);
        demosBuilt = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private void showStatus(String message) {
        statusMessage = message;
        statusMessageTime = System.currentTimeMillis();
    }

    private static boolean getConfigBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue config) {
        try { return config.get(); } catch (Exception e) { return false; }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER CLASS
    // ═══════════════════════════════════════════════════════════════

    private static final class DemoButton {
        final EditorButton button;
        final int width;
        int x;
        int y;

        DemoButton(EditorButton button, int width) {
            this.button = button;
            this.width = width;
        }
    }
}
