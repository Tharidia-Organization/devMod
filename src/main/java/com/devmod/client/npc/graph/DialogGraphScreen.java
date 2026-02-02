package com.devmod.client.npc.graph;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.npc.DialogEditorScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorApplyFeedbackRouter;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.network.SaveDialogPayload;
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

    private DialogSet dialogSet;
    private final DialogSet originalDialogSet;
    private final int originalRevision;
    private final com.devmod.client.ui.testing.ToastMessage toast =
        new com.devmod.client.ui.testing.ToastMessage(1800, 250);
    private final EditorApplyFeedbackRouter.Listener feedbackListener = this::handleEditorConfirm;
    @Nullable
    private final Screen parentScreen;

    // Main components
    private GraphCanvas canvas;
    private NodeEditPanel nodePanel;
    private GraphMinimap minimap;

    // Toolbar buttons
    private final java.util.List<EditorButtonWidget> toolbarButtons = new java.util.ArrayList<>();

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }

    public DialogGraphScreen(@Nonnull DialogSet dialogSet, @Nullable Screen parentScreen) {
        super(Component.literal("Dialog Graph: " + dialogSet.name()));
        this.dialogSet = dialogSet;
        this.originalDialogSet = dialogSet;
        this.originalRevision = dialogSet.revision();
        this.parentScreen = parentScreen;
        this.canvas = new GraphCanvas(0, 0, 0, 0);
        this.nodePanel = new NodeEditPanel(0, 0, 0, 0);
        this.minimap = new GraphMinimap(0, 0, 0, 0, canvas);
    }

    @Override
    protected void init() {
        EditorApplyFeedbackRouter.register(feedbackListener);
        UIScaleManager.update();
        // Calculate dimensions
        int canvasWidth = width - s(SIDE_PANEL_WIDTH);
        int canvasHeight = height - s(TOOLBAR_HEIGHT);

        // Create canvas
        canvas = new GraphCanvas(0, s(TOOLBAR_HEIGHT), canvasWidth, canvasHeight);
        canvas.loadDialogSet(dialogSet);
        canvas.setOnNodeSelected(this::onNodeSelected);
        canvas.setOnNodeDoubleClicked(this::onNodeDoubleClicked);
        canvas.setOnSelectionChanged(this::onSelectionChanged);
        canvas.setOnConnectionCreate(this::onConnectionCreate);
        canvas.setOnNodesDelete(this::onDeleteNodes);
        addRenderableWidget(canvas);

        // Create side panel
        nodePanel = new NodeEditPanel(canvasWidth, s(TOOLBAR_HEIGHT), s(SIDE_PANEL_WIDTH), canvasHeight);
        nodePanel.setOnEditClicked(this::onEditNode);
        nodePanel.setOnSetEntryClicked(this::onSetEntryNode);
        addRenderableWidget(nodePanel);

        // Create minimap
        int minimapSize = s(120);
        minimap = new GraphMinimap(
            canvasWidth - minimapSize - s(10),
            s(TOOLBAR_HEIGHT) + s(10),
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
        int x = s(8);
        int y = s(4);
        int btnHeight = s(EditorButton.Size.MEDIUM.height());
        int spacing = s(DesignTokens.Spacing.XS);

        // Back button
        EditorButton backButton = EditorButton.builder("graph_back", "\u2190 Back")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Return to dialog editor")
            .onClick(this::onClose)
            .build();
        EditorButtonWidget backWidget = new EditorButtonWidget(backButton, x, y, s(60), btnHeight);
        toolbarButtons.add(backWidget);
        addRenderableWidget(backWidget);
        x += s(60) + spacing * 2;

        // Separator
        x += s(8);

        // Zoom controls
        EditorButton zoomOutButton = EditorButton.builder("graph_zoom_out", "-")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Zoom out")
            .onClick(() -> canvas.zoomOut())
            .build();
        EditorButtonWidget zoomOutWidget = new EditorButtonWidget(zoomOutButton, x, y, s(20), btnHeight);
        toolbarButtons.add(zoomOutWidget);
        addRenderableWidget(zoomOutWidget);
        x += s(20) + spacing;

        EditorButton zoomInButton = EditorButton.builder("graph_zoom_in", "+")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Zoom in")
            .onClick(() -> canvas.zoomIn())
            .build();
        EditorButtonWidget zoomInWidget = new EditorButtonWidget(zoomInButton, x, y, s(20), btnHeight);
        toolbarButtons.add(zoomInWidget);
        addRenderableWidget(zoomInWidget);
        x += s(20) + spacing * 2;

        // Fit to screen
        EditorButton fitButton = EditorButton.builder("graph_fit", "Fit")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Fit all nodes in view (F)")
            .onClick(() -> canvas.fitToScreen())
            .build();
        EditorButtonWidget fitWidget = new EditorButtonWidget(fitButton, x, y, s(40), btnHeight);
        toolbarButtons.add(fitWidget);
        addRenderableWidget(fitWidget);
        x += s(40) + spacing;

        // Center view
        EditorButton centerButton = EditorButton.builder("graph_center", "Center")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Center view on nodes (C)")
            .onClick(() -> canvas.centerView())
            .build();
        EditorButtonWidget centerWidget = new EditorButtonWidget(centerButton, x, y, s(50), btnHeight);
        toolbarButtons.add(centerWidget);
        addRenderableWidget(centerWidget);
        x += s(50) + spacing * 2;

        // Separator
        x += s(8);

        // Layout buttons
        EditorButton layoutButton = EditorButton.builder("graph_layout", "Auto Layout")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Apply force-directed layout (L)")
            .onClick(() -> canvas.startAutoLayout())
            .build();
        EditorButtonWidget layoutWidget = new EditorButtonWidget(layoutButton, x, y, s(80), btnHeight);
        toolbarButtons.add(layoutWidget);
        addRenderableWidget(layoutWidget);
        x += s(80) + spacing;

        EditorButton hierarchyButton = EditorButton.builder("graph_hierarchy", "Hierarchy")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .tooltip("Apply hierarchical layout")
            .onClick(() -> canvas.hierarchicalLayout())
            .build();
        EditorButtonWidget hierarchyWidget = new EditorButtonWidget(hierarchyButton, x, y, s(70), btnHeight);
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

    private void onSelectionChanged() {
        if (canvas.getSingleSelectedNode() == null) {
            nodePanel.clear();
        }
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
        dialogSet = dialogSet.withEntryNode(nodeId);
        refreshCanvasWithSelection(java.util.Set.of(nodeId));
    }

    private boolean onConnectionCreate(
            String sourceNodeId,
            int optionIndex,
            String optionId,
            String targetNodeId) {
        DialogNode node = dialogSet.getNode(sourceNodeId).orElse(null);
        if (node == null) {
            return false;
        }
        if (optionIndex < 0 || optionIndex >= node.options().size()) {
            return false;
        }

        java.util.List<DialogOption> options = new java.util.ArrayList<>(node.options());
        DialogOption option = options.get(optionIndex);
        if (!option.id().equals(optionId)) {
            return false;
        }

        DialogOption updated = option.withAction(new DialogAction.GoToNode(targetNodeId));
        options.set(optionIndex, updated);
        dialogSet = dialogSet.withNode(node.withOptions(java.util.List.copyOf(options)));
        refreshCanvasWithSelection(java.util.Set.of(sourceNodeId, targetNodeId));
        return true;
    }

    private boolean onDeleteNodes(java.util.Set<String> nodeIds) {
        if (nodeIds.isEmpty()) {
            return false;
        }

        java.util.Map<String, DialogNode> updatedNodes = new java.util.HashMap<>(dialogSet.nodes());
        for (String id : nodeIds) {
            updatedNodes.remove(id);
        }

        java.util.Map<String, DialogNode> cleanedNodes = new java.util.HashMap<>();
        for (var entry : updatedNodes.entrySet()) {
            DialogNode node = entry.getValue();
            java.util.List<DialogOption> filtered = new java.util.ArrayList<>();
            for (DialogOption option : node.options()) {
                if (option.action() instanceof DialogAction.GoToNode goTo
                    && nodeIds.contains(goTo.nodeId())) {
                    continue;
                }
                filtered.add(option);
            }
            if (filtered.size() != node.options().size()) {
                node = node.withOptions(java.util.List.copyOf(filtered));
            }
            cleanedNodes.put(entry.getKey(), node);
        }

        String entryId = dialogSet.entryNodeId();
        if (nodeIds.contains(entryId)) {
            entryId = cleanedNodes.keySet().stream().sorted().findFirst().orElse("");
        }

        dialogSet = new DialogSet(
            dialogSet.id(),
            dialogSet.name(),
            java.util.Map.copyOf(cleanedNodes),
            entryId,
            System.currentTimeMillis(),
            dialogSet.revision() + 1,
            dialogSet.isPreset(),
            dialogSet.ownerUUID(),
            dialogSet.schedule()
        );

        refreshCanvasWithSelection(java.util.Set.of());
        return true;
    }

    private void refreshCanvasWithSelection(java.util.Set<String> preferredSelection) {
        java.util.Set<String> previousSelection = canvas.getSelectedNodes();
        canvas.loadDialogSet(dialogSet);
        java.util.Set<String> selection = !preferredSelection.isEmpty()
            ? preferredSelection
            : previousSelection;
        canvas.restoreSelection(selection);
        if (canvas.getSingleSelectedNode() == null) {
            nodePanel.clear();
        }
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Dark background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Toolbar background
        graphics.fill(0, 0, width, s(TOOLBAR_HEIGHT), COLOR_TOOLBAR_BG);

        // Toolbar separator
        graphics.fill(0, s(TOOLBAR_HEIGHT) - 1, width, s(TOOLBAR_HEIGHT), DesignTokens.Border.DEFAULT);

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Zoom indicator
        String zoomText = String.format("%.0f%%", canvas.getZoom() * 100);
        UIScaleManager.drawScaledString(
            graphics,
            font,
            zoomText,
            width - s(SIDE_PANEL_WIDTH) - s(50),
            s(8),
            DesignTokens.Text.SECONDARY
        );

        // Title in toolbar
        UIScaleManager.drawScaledString(
            graphics,
            font,
            dialogSet.name(),
            width / 2 - UIScaleManager.getScaledStringWidth(font, dialogSet.name()) / 2,
            s(8),
            COLOR_TEXT
        );

        renderToolbarTooltips(graphics, mouseX, mouseY);

        var font = this.font;
        if (font != null) {
            toast.renderInBounds(graphics, font, 0, 0, width, s(TOOLBAR_HEIGHT),
                com.devmod.client.ui.testing.ToastMessage.Position.TOP_RIGHT);
        }
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
        EditorApplyFeedbackRouter.unregister(feedbackListener);
        persistChangesIfNeeded();
        if (parentScreen != null) {
            minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    private void persistChangesIfNeeded() {
        if (originalDialogSet != null && originalDialogSet.isPreset()) {
            return;
        }
        if (!isDirty()) {
            return;
        }
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        PacketDistributor.sendToServer(new SaveDialogPayload(dialogSet, originalRevision));
    }

    private boolean isDirty() {
        if (originalDialogSet == null) {
            return true;
        }
        if (!dialogSet.entryNodeId().equals(originalDialogSet.entryNodeId())) {
            return true;
        }
        if (!dialogSet.nodes().equals(originalDialogSet.nodes())) {
            return true;
        }
        return !dialogSet.name().equals(originalDialogSet.name());
    }

    private boolean handleEditorConfirm(EditorApplyConfirmPayload payload) {
        if (!"dialog".equalsIgnoreCase(payload.scope())) {
            return false;
        }
        String msg = payload.success()
            ? "Dialog saved"
            : "Dialog save failed: " + payload.message();
        toast.show(msg);
        return true;
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
        com.devmod.client.ui.ScreenSafety.openSafe(
            "dialog_graph",
            parentScreen,
            () -> new DialogGraphScreen(dialogSet, parentScreen));
    }
}
