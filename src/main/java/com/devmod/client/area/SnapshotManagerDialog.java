package com.devmod.client.area;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.area.network.CaptureSnapshotPayload;
import com.devmod.area.network.DeleteSnapshotPayload;
import com.devmod.area.network.RestoreSnapshotPayload;
import com.devmod.area.network.SnapshotListPayload.SnapshotSummary;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ScaledCoord;

/**
 * Dialog for managing area snapshots.
 * Displays list of snapshots with options to create, restore, and delete.
 */
@OnlyIn(Dist.CLIENT)
public final class SnapshotManagerDialog extends BaseOverlay {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 320;
    private static final int LIST_ITEM_HEIGHT = 28;
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int BUTTON_HEIGHT = 20;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final UUID areaId;
    private final List<SnapshotSummary> snapshots;
    private final Runnable onComplete;

    @Nullable private SnapshotSummary selectedSnapshot;
    private int scrollOffset = 0;

    // Create mode
    private boolean isCreateMode = false;
    @Nullable private EditBox descriptionField;

    // Confirm delete mode
    private boolean isConfirmDeleteMode = false;

    private final EditorButton createButton;
    private final EditorButton restoreButton;
    private final EditorButton deleteButton;
    private final EditorButton closeButton;

    // Create mode buttons
    private final EditorButton confirmCreateButton;
    private final EditorButton cancelCreateButton;

    // Confirm delete buttons
    private final EditorButton confirmDeleteButton;
    private final EditorButton cancelDeleteButton;

    public SnapshotManagerDialog(UUID areaId, List<SnapshotSummary> snapshots, Runnable onComplete) {
        this.areaId = Objects.requireNonNull(areaId);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.onComplete = Objects.requireNonNull(onComplete);

        // Action buttons
        this.createButton = new EditorButton("create",
            Component.translatable("area.snapshot.create").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::enterCreateMode);

        this.restoreButton = new EditorButton("restore",
            Component.translatable("area.snapshot.restore").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::doRestore);

        this.deleteButton = new EditorButton("delete",
            Component.translatable("area.snapshot.delete").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::enterConfirmDeleteMode);

        this.closeButton = new EditorButton("close",
            Component.translatable("gui.close").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::hide);

        // Create mode buttons
        this.confirmCreateButton = new EditorButton("confirm_create",
            Component.translatable("gui.ok").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::doCreate);

        this.cancelCreateButton = new EditorButton("cancel_create",
            Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::exitCreateMode);

        // Confirm delete buttons
        this.confirmDeleteButton = new EditorButton("confirm_delete",
            Component.translatable("gui.yes").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::doDelete);

        this.cancelDeleteButton = new EditorButton("cancel_delete",
            Component.translatable("gui.no").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::exitConfirmDeleteMode);

        updateButtonStates();
    }

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int panelX, int panelY,
                                  int panelWidth, int panelHeight, int mouseX, int mouseY) {
        int padding = ScaledCoord.scaleDim(12);
        int contentX = panelX + padding;
        int contentY = panelY + padding;
        int contentWidth = panelWidth - padding * 2;

        // Title
        String title = Component.translatable("area.snapshot.title").getString();
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), title, contentX, contentY, DesignTokens.Text.PRIMARY());
        contentY += ScaledCoord.scaleDim(24);

        if (isConfirmDeleteMode) {
            renderConfirmDeleteMode(graphics, font, contentX, contentY, contentWidth, mouseX, mouseY);
        } else if (isCreateMode) {
            renderCreateMode(graphics, font, contentX, contentY, contentWidth, mouseX, mouseY);
        } else {
            renderListMode(graphics, font, contentX, contentY, contentWidth, panelY, panelHeight, mouseX, mouseY);
        }
    }

    private void renderListMode(GuiGraphics graphics, Font font, int contentX, int contentY,
                                 int contentWidth, int panelY, int panelHeight, int mouseX, int mouseY) {
        int sListItemHeight = ScaledCoord.scaleDim(LIST_ITEM_HEIGHT);
        int sButtonHeight = ScaledCoord.scaleDim(BUTTON_HEIGHT);

        // Snapshot count
        String countText = snapshots.isEmpty()
            ? Component.translatable("area.snapshot.empty").getString()
            : snapshots.size() + " snapshot(s)";
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), countText, contentX, contentY, DesignTokens.Text.MUTED());
        contentY += ScaledCoord.scaleDim(16);

        // Snapshot list
        int listHeight = sListItemHeight * MAX_VISIBLE_ITEMS;
        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + listHeight, DesignTokens.Background.PANEL());
        graphics.renderOutline(contentX, contentY, contentWidth, listHeight, DesignTokens.Border.DEFAULT());

        int visibleCount = Math.min(MAX_VISIBLE_ITEMS, snapshots.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int index = scrollOffset + i;
            SnapshotSummary snapshot = snapshots.get(index);
            int itemY = contentY + i * sListItemHeight;

            boolean selected = snapshot.equals(selectedSnapshot);
            boolean hovered = mouseX >= contentX && mouseX < contentX + contentWidth
                && mouseY >= itemY && mouseY < itemY + sListItemHeight;

            renderSnapshotItem(graphics, font, snapshot, contentX + ScaledCoord.scaleDim(2), itemY + ScaledCoord.scaleDim(1),
                contentWidth - ScaledCoord.scaleDim(4), sListItemHeight - ScaledCoord.scaleDim(2), selected, hovered);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font), "^",
                contentX + contentWidth / 2, contentY - ScaledCoord.scaleDim(8), DesignTokens.Text.MUTED());
        }
        if (scrollOffset + MAX_VISIBLE_ITEMS < snapshots.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font), "v",
                contentX + contentWidth / 2, contentY + listHeight + ScaledCoord.scaleDim(2), DesignTokens.Text.MUTED());
        }

        // Action buttons at bottom
        int btnY = panelY + panelHeight - ScaledCoord.scaleDim(44);
        int btnWidth = ScaledCoord.scaleDim(80);
        int btnSpacing = ScaledCoord.scaleDim(8);
        int totalBtnWidth = btnWidth * 4 + btnSpacing * 3;
        int btnX = contentX + (contentWidth - totalBtnWidth) / 2;

        createButton.render(graphics, btnX, btnY, btnWidth, sButtonHeight, mouseX, mouseY);
        restoreButton.render(graphics, btnX + btnWidth + btnSpacing, btnY, btnWidth, sButtonHeight, mouseX, mouseY);
        deleteButton.render(graphics, btnX + (btnWidth + btnSpacing) * 2, btnY, btnWidth, sButtonHeight, mouseX, mouseY);
        closeButton.render(graphics, btnX + (btnWidth + btnSpacing) * 3, btnY, btnWidth, sButtonHeight, mouseX, mouseY);
    }

    private void renderSnapshotItem(GuiGraphics graphics, Font font, SnapshotSummary snapshot,
                                     int x, int y, int width, int height, boolean selected, boolean hovered) {
        int bgColor;
        if (selected) {
            bgColor = DesignTokens.Background.ACTIVE();
        } else if (hovered) {
            bgColor = DesignTokens.Background.HOVER();
        } else {
            bgColor = DesignTokens.Background.PANEL();
        }
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Description (or "No description")
        String desc = snapshot.description().isEmpty() ? "(No description)" : snapshot.description();
        if (desc.length() > 30) {
            desc = desc.substring(0, 27) + "...";
        }
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), desc, x + 4, y + 4, DesignTokens.Text.PRIMARY());

        // Date and size on second line
        String dateStr = DATE_FORMAT.format(Instant.ofEpochMilli(snapshot.createdAt()));
        String infoStr = dateStr + " | " + snapshot.blockCount() + " blocks | " + snapshot.getFormattedFileSize();
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), infoStr, x + 4, y + 14, DesignTokens.Text.MUTED());
    }

    private void renderCreateMode(GuiGraphics graphics, Font font, int contentX, int contentY,
                                   int contentWidth, int mouseX, int mouseY) {
        int sFieldHeight = ScaledCoord.scaleDim(20);
        int sButtonHeight = ScaledCoord.scaleDim(BUTTON_HEIGHT);

        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), Component.translatable("area.snapshot.description").getString() + ":",
            contentX, contentY, DesignTokens.Text.PRIMARY());
        contentY += ScaledCoord.scaleDim(14);

        EditBox field = descriptionField;
        if (field == null) {
            field = new EditBox(Objects.requireNonNull(font), contentX, contentY, contentWidth, sFieldHeight,
                Objects.requireNonNull(Component.translatable("area.snapshot.description")));
            field.setMaxLength(128);
            descriptionField = field;
        }
        field.setX(contentX);
        field.setY(contentY);
        field.setWidth(contentWidth);
        field.render(graphics, mouseX, mouseY, 0);
        contentY += ScaledCoord.scaleDim(40);

        // Buttons
        int btnWidth = ScaledCoord.scaleDim(100);
        int btnSpacing = ScaledCoord.scaleDim(20);
        int btnX = contentX + (contentWidth - btnWidth * 2 - btnSpacing) / 2;

        confirmCreateButton.render(graphics, btnX, contentY, btnWidth, sButtonHeight, mouseX, mouseY);
        cancelCreateButton.render(graphics, btnX + btnWidth + btnSpacing, contentY, btnWidth, sButtonHeight, mouseX, mouseY);
    }

    private void renderConfirmDeleteMode(GuiGraphics graphics, Font font, int contentX, int contentY,
                                          int contentWidth, int mouseX, int mouseY) {
        int sButtonHeight = ScaledCoord.scaleDim(BUTTON_HEIGHT);

        String confirmMsg = Objects.requireNonNull(Component.translatable("area.snapshot.confirm_delete").getString());
        graphics.drawWordWrap(Objects.requireNonNull(font), Objects.requireNonNull(Component.literal(confirmMsg)),
            contentX, contentY, contentWidth, DesignTokens.Text.PRIMARY());
        contentY += ScaledCoord.scaleDim(50);

        SnapshotSummary selected = selectedSnapshot;
        if (selected != null) {
            String desc = selected.description().isEmpty() ? "(No description)" : selected.description();
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font), "\"" + desc + "\"",
                contentX + contentWidth / 2, contentY, DesignTokens.Text.MUTED());
        }
        contentY += ScaledCoord.scaleDim(30);

        // Buttons
        int btnWidth = ScaledCoord.scaleDim(100);
        int btnSpacing = ScaledCoord.scaleDim(20);
        int btnX = contentX + (contentWidth - btnWidth * 2 - btnSpacing) / 2;

        confirmDeleteButton.render(graphics, btnX, contentY, btnWidth, sButtonHeight, mouseX, mouseY);
        cancelDeleteButton.render(graphics, btnX + btnWidth + btnSpacing, contentY, btnWidth, sButtonHeight, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!isVisible()) return false;

        int sPanelWidth = ScaledCoord.scaleDim(getPanelWidth());
        int sPanelHeight = ScaledCoord.scaleDim(getPanelHeight());
        int panelX = (screenWidth - sPanelWidth) / 2;
        int panelY = (screenHeight - sPanelHeight) / 2;
        int padding = ScaledCoord.scaleDim(12);
        int contentX = panelX + padding;
        int contentY = panelY + padding + ScaledCoord.scaleDim(24); // After title
        int contentWidth = sPanelWidth - padding * 2;
        int sListItemHeight = ScaledCoord.scaleDim(LIST_ITEM_HEIGHT);

        if (isConfirmDeleteMode) {
            if (confirmDeleteButton.mouseClicked(mouseX, mouseY, 0)) return true;
            if (cancelDeleteButton.mouseClicked(mouseX, mouseY, 0)) return true;
        } else if (isCreateMode) {
            if (descriptionField != null) descriptionField.mouseClicked(mouseX, mouseY, 0);
            if (confirmCreateButton.mouseClicked(mouseX, mouseY, 0)) return true;
            if (cancelCreateButton.mouseClicked(mouseX, mouseY, 0)) return true;
        } else {
            // List mode - handle item selection
            int listY = contentY + ScaledCoord.scaleDim(16);
            int listHeight = sListItemHeight * MAX_VISIBLE_ITEMS;

            if (mouseX >= contentX && mouseX < contentX + contentWidth
                && mouseY >= listY && mouseY < listY + listHeight) {
                int index = (int) ((mouseY - listY) / sListItemHeight) + scrollOffset;
                if (index >= 0 && index < snapshots.size()) {
                    selectedSnapshot = snapshots.get(index);
                    updateButtonStates();
                    return true;
                }
            }

            // Handle buttons
            if (createButton.mouseClicked(mouseX, mouseY, 0)) return true;
            if (restoreButton.mouseClicked(mouseX, mouseY, 0)) return true;
            if (deleteButton.mouseClicked(mouseX, mouseY, 0)) return true;
            if (closeButton.mouseClicked(mouseX, mouseY, 0)) return true;
        }

        return super.mouseClicked(mouseX, mouseY, screenWidth, screenHeight);
    }

    /**
     * Handles mouse release events for button activation.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;

        if (isConfirmDeleteMode) {
            if (confirmDeleteButton.mouseReleased(mouseX, mouseY, button)) return true;
            if (cancelDeleteButton.mouseReleased(mouseX, mouseY, button)) return true;
        } else if (isCreateMode) {
            if (confirmCreateButton.mouseReleased(mouseX, mouseY, button)) return true;
            if (cancelCreateButton.mouseReleased(mouseX, mouseY, button)) return true;
        } else {
            if (createButton.mouseReleased(mouseX, mouseY, button)) return true;
            if (restoreButton.mouseReleased(mouseX, mouseY, button)) return true;
            if (deleteButton.mouseReleased(mouseX, mouseY, button)) return true;
            if (closeButton.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        if (isCreateMode || isConfirmDeleteMode) return false;

        int maxScroll = Math.max(0, snapshots.size() - MAX_VISIBLE_ITEMS);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollDelta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!isVisible()) return false;

        if (isCreateMode) {
            if (keyCode == 256) { // Escape
                exitCreateMode();
                return true;
            }
            EditBox field = descriptionField;
            if (field != null && field.isFocused()) {
                field.keyPressed(keyCode, 0, 0);
                return true;
            }
        } else if (isConfirmDeleteMode) {
            if (keyCode == 256) { // Escape
                exitConfirmDeleteMode();
                return true;
            }
        }

        return super.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isVisible() || !isCreateMode) return false;

        EditBox field = descriptionField;
        if (field != null && field.isFocused()) {
            field.charTyped(chr, modifiers);
            return true;
        }
        return false;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedSnapshot != null;
        restoreButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    private void enterCreateMode() {
        isCreateMode = true;
        descriptionField = null;
    }

    private void exitCreateMode() {
        isCreateMode = false;
    }

    private void enterConfirmDeleteMode() {
        if (selectedSnapshot == null) return;
        isConfirmDeleteMode = true;
    }

    private void exitConfirmDeleteMode() {
        isConfirmDeleteMode = false;
    }

    private void doCreate() {
        String desc = descriptionField != null ? descriptionField.getValue().trim() : "";

        // CaptureSnapshotPayload takes (areaId, description)
        PacketDistributor.sendToServer(new CaptureSnapshotPayload(areaId, desc));
        hide();
        onComplete.run();
    }

    private void doRestore() {
        if (selectedSnapshot == null) return;
        // RestoreSnapshotPayload takes only snapshotId
        PacketDistributor.sendToServer(new RestoreSnapshotPayload(selectedSnapshot.id()));
        hide();
        onComplete.run();
    }

    private void doDelete() {
        if (selectedSnapshot == null) return;
        // DeleteSnapshotPayload takes only snapshotId
        PacketDistributor.sendToServer(new DeleteSnapshotPayload(selectedSnapshot.id()));
        hide();
        onComplete.run();
    }
}
