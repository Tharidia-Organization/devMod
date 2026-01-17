package com.devmod.client.npc.graph;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.npc.DialogEditorScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogSet;

/**
 * Visual graph editor screen for dialog sets.
 * Shows nodes as boxes connected by lines, with pan/zoom navigation.
 */
@OnlyIn(Dist.CLIENT)
public class DialogGraphScreen extends Screen {

    /** Panel dimensions */
    private static final int SIDE_PANEL_WIDTH = 220;
    private static final int TOOLBAR_HEIGHT = 28;

    /** Colors */
    private static final int COLOR_TOOLBAR_BG = DesignTokens.Background.HEADER;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;

    private final DialogSet dialogSet;
    @Nullable
    private final Screen parentScreen;

    // Main components
    private GraphCanvas canvas;
    private NodeEditPanel nodePanel;
    private GraphMinimap minimap;

    // Toolbar buttons
    private final java.util.List<EditorButtonWidget> toolbarButtons = new java.util.ArrayList<>();

    public DialogGraphScreen(@Nonnull DialogSet dialogSet, @Nullable Screen parentScreen) {
        super(Component.literal("Dialog Graph: " + dialogSet.name()));
        this.dialogSet = dialogSet;
        this.parentScreen = parentScreen;
        this.canvas = new GraphCanvas(0, 0, 0, 0);
        this.nodePanel = new NodeEditPanel(0, 0, 0, 0);
        this.minimap = new GraphMinimap(0, 0, 0, 0, canvas);
    }

    @Override
    protected void init() {
        // Calculate dimensions
        int canvasWidth = width - SIDE_PANEL_WIDTH;
        int canvasHeight = height - TOOLBAR_HEIGHT;

        // Create canvas
        canvas = new GraphCanvas(0, TOOLBAR_HEIGHT, canvasWidth, canvasHeight);
        canvas.loadDialogSet(dialogSet);
        canvas.setOnNodeSelected(this::onNodeSelected);
        canvas.setOnNodeDoubleClicked(this::onNodeDoubleClicked);
        addRenderableWidget(canvas);

        // Create side panel
        nodePanel = new NodeEditPanel(canvasWidth, TOOLBAR_HEIGHT, SIDE_PANEL_WIDTH, canvasHeight);
        nodePanel.setOnEditClicked(this::onEditNode);
        nodePanel.setOnSetEntryClicked(this::onSetEntryNode);
        addRenderableWidget(nodePanel);

        // Create minimap
        int minimapSize = 120;
        minimap = new GraphMinimap(
            canvasWidth - minimapSize - 10,
            TOOLBAR_HEIGHT + 10,
            minimapSize,
            minimapSize,
            canvas
        );
        addRenderableWidget(minimap);

        // Create toolbar buttons
        initToolbar();
    }

    private void initToolbar() {
        toolbarButtons.clear();
        int x = 8;
        int y = 4;
        int btnHeight = EditorButton.Size.MEDIUM.height();
        int spacing = DesignTokens.Spacing.XS;

        // Back button
        EditorButton backButton = EditorButton.builder("graph_back", "\u2190 Back")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Return to dialog editor")
            .onClick(this::onClose)
            .build();
        EditorButtonWidget backWidget = new EditorButtonWidget(backButton, x, y, 60, btnHeight);
        toolbarButtons.add(backWidget);
        addRenderableWidget(backWidget);
        x += 60 + spacing * 2;

        // Separator
        x += 8;

        // Zoom controls
        EditorButton zoomOutButton = EditorButton.builder("graph_zoom_out", "-")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Zoom out")
            .onClick(() -> canvas.zoomOut())
            .build();
        EditorButtonWidget zoomOutWidget = new EditorButtonWidget(zoomOutButton, x, y, 20, btnHeight);
        toolbarButtons.add(zoomOutWidget);
        addRenderableWidget(zoomOutWidget);
        x += 20 + spacing;

        EditorButton zoomInButton = EditorButton.builder("graph_zoom_in", "+")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Zoom in")
            .onClick(() -> canvas.zoomIn())
            .build();
        EditorButtonWidget zoomInWidget = new EditorButtonWidget(zoomInButton, x, y, 20, btnHeight);
        toolbarButtons.add(zoomInWidget);
        addRenderableWidget(zoomInWidget);
        x += 20 + spacing * 2;

        // Fit to screen
        EditorButton fitButton = EditorButton.builder("graph_fit", "Fit")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Fit all nodes in view (F)")
            .onClick(() -> canvas.fitToScreen())
            .build();
        EditorButtonWidget fitWidget = new EditorButtonWidget(fitButton, x, y, 40, btnHeight);
        toolbarButtons.add(fitWidget);
        addRenderableWidget(fitWidget);
        x += 40 + spacing;

        // Center view
        EditorButton centerButton = EditorButton.builder("graph_center", "Center")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Center view on nodes (C)")
            .onClick(() -> canvas.centerView())
            .build();
        EditorButtonWidget centerWidget = new EditorButtonWidget(centerButton, x, y, 50, btnHeight);
        toolbarButtons.add(centerWidget);
        addRenderableWidget(centerWidget);
        x += 50 + spacing * 2;

        // Separator
        x += 8;

        // Layout buttons
        EditorButton layoutButton = EditorButton.builder("graph_layout", "Auto Layout")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Apply force-directed layout (L)")
            .onClick(() -> canvas.startAutoLayout())
            .build();
        EditorButtonWidget layoutWidget = new EditorButtonWidget(layoutButton, x, y, 80, btnHeight);
        toolbarButtons.add(layoutWidget);
        addRenderableWidget(layoutWidget);
        x += 80 + spacing;

        EditorButton hierarchyButton = EditorButton.builder("graph_hierarchy", "Hierarchy")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Apply hierarchical layout")
            .onClick(() -> canvas.hierarchicalLayout())
            .build();
        EditorButtonWidget hierarchyWidget = new EditorButtonWidget(hierarchyButton, x, y, 70, btnHeight);
        toolbarButtons.add(hierarchyWidget);
        addRenderableWidget(hierarchyWidget);
    }

    // ========================================================================
    // Callbacks
    // ========================================================================

    private void onNodeSelected(@Nonnull String nodeId) {
        DialogNode node = dialogSet.getNode(nodeId).orElse(null);
        nodePanel.loadNode(nodeId, node);
    }

    private void onNodeDoubleClicked(@Nonnull String nodeId) {
        onEditNode(nodeId);
    }

    private void onEditNode(@Nonnull String nodeId) {
        // Open full editor screen focused on this node
        // For now, just go back to parent
        if (parentScreen instanceof DialogEditorScreen) {
            onClose();
        }
    }

    private void onSetEntryNode(@Nonnull String nodeId) {
        // Would need to modify DialogSet - for now just visual feedback
        canvas.loadDialogSet(dialogSet.withEntryNode(nodeId));
        canvas.selectNode(nodeId);
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Toolbar background
        graphics.fill(0, 0, width, TOOLBAR_HEIGHT, COLOR_TOOLBAR_BG);

        // Toolbar separator
        graphics.fill(0, TOOLBAR_HEIGHT - 1, width, TOOLBAR_HEIGHT, DesignTokens.Border.DEFAULT);

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Zoom indicator
        String zoomText = String.format("%.0f%%", canvas.getZoom() * 100);
        graphics.drawString(
            font,
            zoomText,
            width - SIDE_PANEL_WIDTH - 50,
            8,
            DesignTokens.Text.SECONDARY,
            false
        );

        // Title in toolbar
        graphics.drawString(
            font,
            dialogSet.name(),
            width / 2 - font.width(dialogSet.name()) / 2,
            8,
            COLOR_TEXT,
            false
        );

        renderToolbarTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DesignTokens.Background.DARKER);
    }

    private void renderToolbarTooltips(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        for (EditorButtonWidget widget : toolbarButtons) {
            String tooltip = widget.getButton().activeTooltip();
            if (tooltip != null) {
                graphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
                break;
            }
        }
    }

    // ========================================================================
    // Input
    // ========================================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape - close
        if (keyCode == 256) {
            onClose();
            return true;
        }

        // Pass to canvas first
        if (canvas.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================================================================
    // Factory
    // ========================================================================

    /**
     * Opens the graph editor for a dialog set.
     */
    public static void open(@Nonnull DialogSet dialogSet, @Nullable Screen parentScreen) {
        Minecraft.getInstance().setScreen(new DialogGraphScreen(dialogSet, parentScreen));
    }
}
