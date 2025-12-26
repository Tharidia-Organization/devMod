package com.devmod.client.ui.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.function.BiConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.client.ui.editor.core.EditorConstants;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
public abstract class AbstractEditorModule implements EditorModule {

    // ═══════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════

    protected final String id;
    protected final String title;
    protected ItemStack item = ItemStack.EMPTY;
    protected ItemStack originalItem = ItemStack.EMPTY;
    protected ResponsiveLayout layout;

    // Tabs
    protected final List<ModuleTab> tabs = new ArrayList<>();
    protected int activeTabIndex = 0;

    // Dirty tracking
    protected final List<String> pendingChanges = new ArrayList<>();
    protected final List<String> historyEntries = new ArrayList<>();
    private BiConsumer<String, Integer> statusConsumer = (msg, color) -> {};

    // Undo/Redo stacks
    protected final Stack<UndoState> undoStack = new Stack<>();
    protected final Stack<UndoState> redoStack = new Stack<>();
    private static final int MAX_UNDO_STATES = 50;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    protected AbstractEditorModule(String id, String title) {
        this.id = id;
        this.title = title;
    }

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<ModuleTab> getTabs() {
        return tabs;
    }

    @Override
    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    @Override
    public void setActiveTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            this.activeTabIndex = index;
            EditorCache.INSTANCE.invalidateType(EditorCache.Types.PREVIEW);
        }
    }

    protected void addTab(ModuleTab tab) {
        tabs.add(tab);
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void setItem(ItemStack item) {
        this.item = item.copy();
        this.originalItem = item.copy();
        onItemSet();
    }

    @Override
    public ItemStack getItem() {
        return item;
    }

    /**
     * Called when the item is set. Override to initialize module state.
     */
    protected abstract void onItemSet();

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void init(ResponsiveLayout layout) {
        this.layout = layout;
        initializeTabs();
    }

    /**
     * Initialize tabs for this module. Override to add custom tabs.
     */
    protected abstract void initializeTabs();

    // ═══════════════════════════════════════════════════════════════
    // SECTIONS (Default implementation)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<EditorSection> getSections() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            return tabs.get(activeTabIndex).getSections();
        }
        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING (Default implementation)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int y = contentBounds.y() + UIConstants.Spacing.MD;

        // Render sections
        List<EditorSection> sections = getSections();
        for (EditorSection section : sections) {
            ResponsiveLayout.Rect sectionBounds = new ResponsiveLayout.Rect(
                contentBounds.x(),
                y,
                contentBounds.width(),
                section.getHeight()
            );

            // Render section header label if it's a HeaderSection
            if (section instanceof EditorSection.HeaderSection headerSection) {
                graphics.drawString(Objects.requireNonNull(font, "font cannot be null"), headerSection.getLabel(),
                    contentBounds.x() + UIConstants.Spacing.SM,
                    y + (section.getHeight() - 8) / 2,
                    UIConstants.Text.TITLE(), false);
            }

            section.render(graphics, sectionBounds, mouseX, mouseY);
            y += section.getHeight() + UIConstants.Spacing.SM;
        }
    }

    @Override
    public int calculateContentHeight() {
        int height = UIConstants.Spacing.MD;
        for (EditorSection section : getSections()) {
            height += section.getHeight() + UIConstants.Spacing.SM;
        }
        return height + UIConstants.Spacing.MD;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING (Default pass-through)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditorSection section : getSections()) {
            if (section.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EditorSection section : getSections()) {
            if (section.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (EditorSection section : getSections()) {
            if (section.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditorSection section : getSections()) {
            if (section.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (EditorSection section : getSections()) {
            if (section.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // DIRTY TRACKING
    // ═══════════════════════════════════════════════════════════════

    private boolean dirtyEnabled = true;

    @Override
    public boolean hasUnsavedChanges() {
        return !pendingChanges.isEmpty();
    }

    @Override
    public List<String> getPendingChanges() {
        return new ArrayList<>(pendingChanges);
    }

    @Override
    public void markDirty(String changeDescription) {
        if (!dirtyEnabled) {
            return;
        }
        if (pendingChanges.isEmpty()) {
            // Capture undo state at the moment the first change occurs
            saveUndoState();
        }
        if (!pendingChanges.contains(changeDescription)) {
            pendingChanges.add(changeDescription);
        }
        addHistoryEntry(changeDescription);
        EditorCache.INSTANCE.invalidateItem(item.toString());
    }

    @Override
    public void clearDirty() {
        pendingChanges.clear();
    }

    @Override
    public void setDirtyTrackingEnabled(boolean enabled) {
        this.dirtyEnabled = enabled;
    }

    @Override
    public boolean hasPendingDiff() {
        return hasUnsavedChanges();
    }

    @Override
    public List<String> getHistoryEntries() {
        return new ArrayList<>(historyEntries);
    }

    @Override
    public void logEvent(String description) {
        addHistoryEntry(description);
    }

    protected void addHistoryEntry(String description) {
        String safeDesc = description == null ? "Change" : description;
        String timestamp = java.time.LocalTime.now().withNano(0).toString();
        historyEntries.add(timestamp + "  " + safeDesc);
        while (historyEntries.size() > EditorConstants.MAX_HISTORY_ENTRIES) {
            historyEntries.remove(0);
        }
    }

    @Override
    public void clearHistory() {
        historyEntries.clear();
    }

    protected List<String> getRecentHistoryEntries(int maxEntries) {
        List<String> history = getHistoryEntries();
        if (history.isEmpty()) return List.of();
        int start = Math.max(0, history.size() - maxEntries);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    @Override
    public void setStatusConsumer(BiConsumer<String, Integer> statusConsumer) {
        this.statusConsumer = statusConsumer == null ? (msg, color) -> {} : statusConsumer;
    }

    protected void reportStatus(String message, int color) {
        statusConsumer.accept(message, color);
    }

    // ═══════════════════════════════════════════════════════════════
    // UNDO/REDO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Undo state record.
     */
    protected record UndoState(
        String description,
        byte[] itemData,
        int tabIndex
    ) {}

    @Override
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @Override
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Override
    public void saveUndoState() {
        // Save current state to undo stack
        UndoState state = createCurrentState("Edit");
        undoStack.push(state);

        // Limit stack size
        while (undoStack.size() > MAX_UNDO_STATES) {
            undoStack.remove(0);
        }

        // Clear redo stack on new action
        redoStack.clear();
    }

    @Override
    public void undo() {
        if (!canUndo()) return;

        // Save current state to redo
        redoStack.push(createCurrentState("Redo"));

        // Restore previous state
        UndoState state = undoStack.pop();
        restoreState(state);
    }

    @Override
    public void redo() {
        if (!canRedo()) return;

        // Save current state to undo
        undoStack.push(createCurrentState("Undo"));

        // Restore next state
        UndoState state = redoStack.pop();
        restoreState(state);
    }

    /**
     * Create an undo state from current module state.
     */
    protected UndoState createCurrentState(String description) {
        byte[] data = serializeItem(item);
        return new UndoState(description, data, activeTabIndex);
    }

    /**
     * Restore module state from undo state.
     */
    protected void restoreState(UndoState state) {
        item = deserializeItem(state.itemData());
        activeTabIndex = state.tabIndex();
        onItemSet();
        EditorCache.INSTANCE.invalidateAll();
        pendingChanges.clear();
        historyEntries.clear();
    }

    /**
     * Serialize item to byte array. Override for custom serialization.
     */
    protected byte[] serializeItem(ItemStack stack) {
        try {
            CompoundTag encoded = Objects.requireNonNull(
                ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                    .result()
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .orElseGet(CompoundTag::new)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(encoded, out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * Deserialize item from byte array. Override for custom deserialization.
     */
    protected ItemStack deserializeItem(byte[] data) {
        if (data == null || data.length == 0) {
            return originalItem.copy();
        }
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            CompoundTag tag = Objects.requireNonNull(
                NbtIo.readCompressed(in, Objects.requireNonNull(NbtAccounter.unlimitedHeap()))
            );
            return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag)
                .result()
                .orElse(originalItem.copy());
        } catch (Exception e) {
            return originalItem.copy();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void resetToOriginal() {
        saveUndoState();
        this.item = originalItem.copy();
        onItemSet();
        clearDirty();
        EditorCache.INSTANCE.invalidateAll();
    }

    // Preview item: used to hold a client-side preview copy so preview does not mutate the real item
    private ItemStack previewItem = null;

    /**
     * Returns a preview ItemStack if a preview is active, otherwise null.
     */
    public ItemStack getPreviewItem() { return previewItem; }

    /**
     * Sets the preview item (client-side only).
     */
    protected void setPreviewItem(ItemStack stack) { this.previewItem = stack; }

    /**
     * Clear any active preview.
     */
    public void clearPreview() { this.previewItem = null; }

    @Override
    public void applyPreview() {
        // Default implementation does nothing
        // Override in subclasses to apply preview changes
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        // Default implementation does nothing
    }

    @Override
    public void onClose() {
        // Default implementation does nothing
    }
}
