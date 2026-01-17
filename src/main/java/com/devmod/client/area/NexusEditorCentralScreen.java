package com.devmod.client.area;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.network.OpenEditorCentralPayload.AreaSummary;
import com.devmod.area.network.PromoteMainHubPayload;
import com.devmod.area.network.RequestOpenAreaBuilderPayload;
import com.devmod.area.network.SnapshotListPayload.SnapshotSummary;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;

/**
 * Nexus Editor Central screen - the main hub for area management.
 * Shows a welcome message, list of existing areas, and options to create new areas.
 */
@OnlyIn(Dist.CLIENT)
public class NexusEditorCentralScreen extends BaseDevModScreen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 260;
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 44;
    private static final int PANEL_PADDING = 12;

    private final List<AreaSummary> areas;
    private final boolean canCreateAreas;

    @Nullable
    private AreaSummary selectedArea;
    private int scrollOffset = 0;

    // Layout
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int visibleItems;

    // Buttons
    @Nullable private EditorButtonWidget openButton;
    @Nullable private EditorButtonWidget createButton;
    @Nullable private EditorButtonWidget cloneButton;
    @Nullable private EditorButtonWidget promoteButton;
    @Nullable private EditorButtonWidget deleteButton;

    // Dialog overlays
    @Nullable private CloneAreaDialog cloneDialog;
    @Nullable private DeleteAreaConfirmDialog deleteDialog;
    @Nullable private SnapshotManagerDialog snapshotDialog;

    public NexusEditorCentralScreen(
            List<AreaSummary> areas,
            @Nullable UUID mainHubId,
            boolean canCreateAreas
    ) {
        super(Component.translatable("area.editor_central.title"), "nexus_editor_central");
        this.areas = areas;
        this.canCreateAreas = canCreateAreas;
        selectDefaultArea();
    }

    @Override
    protected void initContent() {
        int availableWidth = Math.max(0, this.width - PANEL_PADDING * 2);
        int availableHeight = Math.max(0, this.height - PANEL_PADDING * 2);
        panelWidth = Math.min(PANEL_WIDTH, availableWidth);
        panelHeight = Math.min(PANEL_HEIGHT, availableHeight);
        if (panelWidth == 0) {
            panelWidth = this.width;
        }
        if (panelHeight == 0) {
            panelHeight = this.height;
        }

        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        listLeft = panelLeft + PANEL_PADDING;
        listTop = panelTop + HEADER_HEIGHT;
        listWidth = Math.max(0, panelWidth - PANEL_PADDING * 2);

        int availableListHeight = Math.max(0, panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        visibleItems = Math.max(1, availableListHeight / AreaBuilderGuiConstants.LIST_ITEM_HEIGHT);
        listHeight = Math.min(availableListHeight, visibleItems * AreaBuilderGuiConstants.LIST_ITEM_HEIGHT);
        ensureSelectionVisible();

        int buttonY = panelTop + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - AreaBuilderGuiConstants.BUTTON_HEIGHT) / 2;
        int buttonWidth = 70;
        int spacing = AreaBuilderGuiConstants.BUTTON_SPACING;
        int buttonCount = canCreateAreas ? 6 : 5; // Create, Open, Clone, Promote, Delete, Close
        int totalWidth = buttonCount * buttonWidth + (buttonCount - 1) * spacing;
        int startX = panelLeft + (panelWidth - totalWidth) / 2;

        if (canCreateAreas) {
            EditorButton create = EditorButton.builder("area-central-create",
                    Component.translatable("area.editor_central.create_area").getString())
                .style(EditorButton.Style.PRIMARY)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> requestOpenBuilder(null))
                .build();
            createButton = new EditorButtonWidget(create, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
            this.addRenderableWidget(createButton);
            startX += buttonWidth + spacing;
        }

        EditorButton open = EditorButton.builder("area-central-open",
                Component.translatable("area.editor_central.open_selected").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::openSelectedArea)
            .build();
        openButton = new EditorButtonWidget(open, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(openButton);
        startX += buttonWidth + spacing;

        // Clone button
        EditorButton clone = EditorButton.builder("area-central-clone",
                Component.translatable("area.editor_central.clone").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::showCloneDialog)
            .build();
        cloneButton = new EditorButtonWidget(clone, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(cloneButton);
        startX += buttonWidth + spacing;

        // Promote button
        EditorButton promote = EditorButton.builder("area-central-promote",
                Component.translatable("area.editor_central.promote").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::promoteSelectedToMainHub)
            .build();
        promoteButton = new EditorButtonWidget(promote, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(promoteButton);
        startX += buttonWidth + spacing;

        // Delete button
        EditorButton delete = EditorButton.builder("area-central-delete",
                Component.translatable("area.editor_central.delete").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::showDeleteDialog)
            .build();
        deleteButton = new EditorButtonWidget(delete, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(deleteButton);
        startX += buttonWidth + spacing;

        EditorButton close = EditorButton.builder("area-central-close",
                Component.translatable("gui.close").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(close, startX, buttonY, buttonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT));

        updateButtons();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
            AreaBuilderGuiConstants.COLOR_BACKGROUND);
        graphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Title
        graphics.drawCenteredString(
            Objects.requireNonNull(this.font),
            Objects.requireNonNull(Component.translatable("area.editor_central.title")),
            centerX, panelTop + 12,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Welcome message
        graphics.drawCenteredString(
            Objects.requireNonNull(this.font),
            Objects.requireNonNull(Component.translatable("area.editor_central.welcome")),
            centerX, panelTop + 30,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // List background
        graphics.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight,
            AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(listLeft, listTop, listWidth, listHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        if (areas.isEmpty()) {
            graphics.drawCenteredString(
                Objects.requireNonNull(this.font),
                Objects.requireNonNull(Component.translatable("area.editor_central.empty_list")),
                centerX, listTop + listHeight / 2 - 4,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
            return;
        }

        int visibleCount = Math.min(visibleItems, areas.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int areaIndex = scrollOffset + i;
            AreaSummary area = areas.get(areaIndex);
            int itemY = listTop + i * AreaBuilderGuiConstants.LIST_ITEM_HEIGHT;

            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= itemY && mouseY < itemY + AreaBuilderGuiConstants.LIST_ITEM_HEIGHT;

            renderAreaRow(graphics, area, listLeft + 2, itemY + 1, listWidth - 4,
                AreaBuilderGuiConstants.LIST_ITEM_HEIGHT - 2,
                area.equals(selectedArea), hovered);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(Objects.requireNonNull(this.font),
                Objects.requireNonNull(Component.literal("^")), centerX, listTop - 8,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
        }
        if (scrollOffset + visibleItems < areas.size()) {
            graphics.drawCenteredString(Objects.requireNonNull(this.font),
                Objects.requireNonNull(Component.literal("v")), centerX, listTop + listHeight + 2,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
        }

        // Area count
        graphics.drawCenteredString(
            Objects.requireNonNull(this.font),
            Objects.requireNonNull(Component.translatable("area.editor_central.area_count", areas.size())),
            centerX, panelTop + panelHeight - FOOTER_HEIGHT - 10,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Render clone dialog overlay if visible
        CloneAreaDialog cDialog = cloneDialog;
        if (cDialog != null && cDialog.isVisible()) {
            cDialog.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
        }

        // Render delete dialog overlay if visible
        DeleteAreaConfirmDialog dDialog = deleteDialog;
        if (dDialog != null && dDialog.isVisible()) {
            dDialog.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
        }

        // Render snapshot dialog overlay if visible
        SnapshotManagerDialog sDialog = snapshotDialog;
        if (sDialog != null && sDialog.isVisible()) {
            sDialog.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
        }
    }

    private void renderAreaRow(GuiGraphics graphics, AreaSummary area, int x, int y, int width, int height,
                               boolean selected, boolean hovered) {
        int bgColor;
        if (selected) {
            bgColor = AreaBuilderGuiConstants.COLOR_TAB_ACTIVE;
        } else if (hovered) {
            bgColor = AreaBuilderGuiConstants.COLOR_HOVER;
        } else {
            bgColor = AreaBuilderGuiConstants.COLOR_PANEL;
        }
        graphics.fill(x, y, x + width, y + height, bgColor);

        String label = area.name();
        if (area.isMainHub()) {
            label = label + " [" + Component.translatable("area.editor_central.main_hub_label").getString() + "]";
        }
        graphics.drawString(Objects.requireNonNull(this.font), label, x + 6, y + 6,
            selected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        String dims = Objects.requireNonNull(String.format("%dx%dx%d", area.width(), area.length(), area.height()));
        int dimsWidth = Objects.requireNonNull(this.font).width(dims);
        graphics.drawString(Objects.requireNonNull(this.font), dims, x + width - dimsWidth - 6, y + 6,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle snapshot dialog first if visible
        SnapshotManagerDialog sDialog = snapshotDialog;
        if (sDialog != null && sDialog.isVisible()) {
            if (sDialog.mouseClicked(mouseX, mouseY, this.width, this.height)) {
                return true;
            }
        }

        // Handle delete dialog if visible
        DeleteAreaConfirmDialog dDialog = deleteDialog;
        if (dDialog != null && dDialog.isVisible()) {
            if (dDialog.mouseClicked(mouseX, mouseY, this.width, this.height)) {
                return true;
            }
        }

        // Handle clone dialog if visible
        CloneAreaDialog cDialog = cloneDialog;
        if (cDialog != null && cDialog.isVisible()) {
            if (cDialog.mouseClicked(mouseX, mouseY, this.width, this.height)) {
                return true;
            }
        }
        if (button == 0 && mouseX >= listLeft && mouseX < listLeft + listWidth
            && mouseY >= listTop && mouseY < listTop + listHeight) {
            int index = (int) ((mouseY - listTop) / AreaBuilderGuiConstants.LIST_ITEM_HEIGHT) + scrollOffset;
            if (index >= 0 && index < areas.size()) {
                selectedArea = areas.get(index);
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        SnapshotManagerDialog sDialog = snapshotDialog;
        if (sDialog != null && sDialog.isVisible()) {
            if (sDialog.keyPressed(keyCode)) {
                return true;
            }
        }
        DeleteAreaConfirmDialog dDialog = deleteDialog;
        if (dDialog != null && dDialog.isVisible()) {
            if (dDialog.keyPressed(keyCode)) {
                return true;
            }
        }
        CloneAreaDialog cDialog = cloneDialog;
        if (cDialog != null && cDialog.isVisible()) {
            if (cDialog.keyPressed(keyCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        SnapshotManagerDialog sDialog = snapshotDialog;
        if (sDialog != null && sDialog.isVisible()) {
            if (sDialog.charTyped(chr, modifiers)) {
                return true;
            }
        }
        DeleteAreaConfirmDialog dDialog = deleteDialog;
        if (dDialog != null && dDialog.isVisible()) {
            if (dDialog.charTyped(chr, modifiers)) {
                return true;
            }
        }
        CloneAreaDialog cDialog = cloneDialog;
        if (cDialog != null && cDialog.isVisible()) {
            if (cDialog.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle snapshot dialog scroll
        SnapshotManagerDialog sDialog = snapshotDialog;
        if (sDialog != null && sDialog.isVisible()) {
            if (sDialog.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
                return true;
            }
        }

        if (mouseX >= listLeft && mouseX < listLeft + listWidth
            && mouseY >= listTop && mouseY < listTop + listHeight) {
            int maxScroll = Math.max(0, areas.size() - visibleItems);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void selectDefaultArea() {
        if (areas.isEmpty()) {
            selectedArea = null;
            return;
        }

        for (AreaSummary area : areas) {
            if (area.isMainHub()) {
                selectedArea = area;
                return;
            }
        }
        selectedArea = areas.get(0);
    }

    private void ensureSelectionVisible() {
        if (selectedArea == null || areas.isEmpty()) {
            scrollOffset = 0;
            return;
        }

        int index = areas.indexOf(selectedArea);
        if (index < 0) {
            scrollOffset = 0;
            return;
        }

        int maxScroll = Math.max(0, areas.size() - visibleItems);
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + visibleItems) {
            scrollOffset = Math.min(maxScroll, index - visibleItems + 1);
        }
    }

    private void openSelectedArea() {
        if (selectedArea == null) {
            return;
        }
        requestOpenBuilder(selectedArea.id());
    }

    private void requestOpenBuilder(@Nullable UUID areaId) {
        PacketDistributor.sendToServer(new RequestOpenAreaBuilderPayload(areaId));
        this.onClose();
    }

    private void updateButtons() {
        boolean hasSelection = selectedArea != null;
        boolean isMainHub = hasSelection && selectedArea != null && selectedArea.isMainHub();
        boolean canDelete = hasSelection && !isMainHub;
        boolean canPromote = hasSelection && !isMainHub;
        if (openButton != null) {
            openButton.getButton().setEnabled(hasSelection);
        }
        if (cloneButton != null) {
            cloneButton.getButton().setEnabled(hasSelection);
        }
        if (promoteButton != null) {
            promoteButton.getButton().setEnabled(canPromote);
        }
        if (deleteButton != null) {
            deleteButton.getButton().setEnabled(canDelete);
        }
    }

    private void promoteSelectedToMainHub() {
        AreaSummary area = selectedArea;
        if (area == null || area.isMainHub()) {
            return;
        }
        PacketDistributor.sendToServer(new PromoteMainHubPayload(area.id()));
        this.onClose();
    }

    private void showCloneDialog() {
        AreaSummary area = selectedArea;
        if (area == null) {
            return;
        }
        cloneDialog = new CloneAreaDialog(
            area.id(),
            area.name(),
            () -> {
                // HIGH-09 fix: Close screen to refresh area list after clone
                this.onClose();
            }
        );
        cloneDialog.show();
    }

    private void showDeleteDialog() {
        AreaSummary area = selectedArea;
        if (area == null || area.isMainHub()) {
            return;
        }
        deleteDialog = new DeleteAreaConfirmDialog(
            area.id(),
            area.name(),
            () -> {
                // On complete - close the screen to refresh
                this.onClose();
            }
        );
        deleteDialog.show();
    }

    /**
     * Shows the snapshot manager dialog for an area.
     * Called by AreaClientHooks when SnapshotListPayload is received.
     *
     * @param areaId    The area ID
     * @param snapshots The list of snapshots for the area
     */
    public void showSnapshotDialog(UUID areaId, List<SnapshotSummary> snapshots) {
        snapshotDialog = new SnapshotManagerDialog(
            areaId,
            snapshots,
            () -> {
                // On complete - could refresh the screen
            }
        );
        snapshotDialog.show();
    }
}
