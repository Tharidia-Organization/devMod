package com.devmod.client.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;

public class PlaceholderModule implements EditorModule {

    private final String id;
    private final String title;
    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    private ResponsiveLayout layout;
    private int activeTabIndex = 0;
    private final List<String> pendingChanges = new ArrayList<>();

    public PlaceholderModule(String id, String title) {
        this.id = id;
        this.title = title;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<ModuleTab> getTabs() {
        // Single, explicit tab to avoid suggesting unavailable functionality
        return List.of(ModuleTab.of("unsupported", "Not Supported", List::of));
    }

    @Override
    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    @Override
    public void setActiveTab(int index) {
        this.activeTabIndex = index;
    }

    @Override
    public void setItem(ItemStack item) {
        this.item = item;
    }

    @Override
    public ItemStack getItem() {
        return item;
    }

    @Override
    public void init(ResponsiveLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout cannot be null");
    }

    @Override
    public List<EditorSection> getSections() {
        return List.of();
    }

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        // Placeholder content
        int y = contentBounds.y() + 20;
        int x = contentBounds.x() + 10;

        // Show layout info if available
        String layoutInfo = layout != null
            ? "Layout: " + layout.getScreenSize().name()
            : "Layout: not initialized";

        graphics.drawString(font, "Editor state: Not supported for this item", x, y, UIConstants.Text.SECONDARY(), false);
        y += 15;

        graphics.drawString(font, "Item: " + (item.isEmpty() ? "None" : item.getHoverName().getString()),
                           x, y, UIConstants.Text.PRIMARY(), false);
        y += 15;

        graphics.drawString(font, layoutInfo, x, y, UIConstants.Text.MUTED(), false);
        y += 20;

        graphics.drawString(font, "Why: item is not in editable tags or supported module not yet implemented.",
                           x, y, UIConstants.Text.MUTED(), false);
        y += 15;
        graphics.drawString(font, "Add to editable tags (melee/ranged/shields) or whitelist to enable editing.",
                           x, y, UIConstants.Text.MUTED(), false);
    }

    @Override
    public int calculateContentHeight() {
        return 200;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return !pendingChanges.isEmpty();
    }

    @Override
    public List<String> getPendingChanges() {
        return pendingChanges;
    }

    @Override
    public void markDirty(String changeDescription) {
        pendingChanges.add(changeDescription);
    }

    @Override
    public void clearDirty() {
        pendingChanges.clear();
    }

    @Override
    public @Nullable CustomPacketPayload buildPayload(boolean isGlobal) {
        return null; // Placeholder returns null
    }

    @Override
    public void applyPreview() {
        // No-op for placeholder
    }

    @Override
    public void resetToOriginal() {
        pendingChanges.clear();
    }

    @Override
    public List<String> getHistoryEntries() {
        return List.of();
    }

    @Override
    public void clearHistory() {
        // No-op
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public boolean canRedo() {
        return false;
    }

    @Override
    public void undo() {
        // No-op for placeholder
    }

    @Override
    public void redo() {
        // No-op for placeholder
    }

    @Override
    public void saveUndoState() {
        // No-op for placeholder
    }
}
