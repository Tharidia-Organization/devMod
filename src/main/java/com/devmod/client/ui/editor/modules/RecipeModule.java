package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.components.ItemPickerOverlay;
import com.devmod.client.ui.editor.components.RecipeGridComponent;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.network.RecipeSyncPayload;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.IngredientData;
import com.devmod.recipe.RecipeConfigManager;
import com.devmod.recipe.RecipeValidator;
import com.devmod.util.I18n;

/**
 * Recipe editor module.
 * Orchestrates Core (data) and UI (components) delegates.
 */
public class RecipeModule extends AbstractEditorModule {

    private static final String TAB_GRID = "grid";
    private static final String TAB_RESULT = "result";
    private static final String TAB_SETTINGS = "settings";

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS (owned by module, shared with delegates)
    // ═══════════════════════════════════════════════════════════════

    private final RecipeGridComponent recipeGrid = new RecipeGridComponent();
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    // Track which slot is being edited (for item picker callback)
    private int editingSlotIndex = -1;
    private boolean callbacksInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // DELEGATES (lazy initialized to avoid this-escape)
    // ═══════════════════════════════════════════════════════════════

    private @Nullable RecipeModuleCore core;
    private @Nullable RecipeModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public RecipeModule() {
        super("recipe", "Recipe Editor");
    }

    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new RecipeModuleCore();
            this.ui = new RecipeModuleUI(this, core, recipeGrid);
            delegatesInitialized = true;
        }
    }

    private RecipeModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private RecipeModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    private void setupCallbacks() {
        if (callbacksInitialized) return;
        callbacksInitialized = true;

        recipeGrid.setOnSlotClick(slotIndex -> {
            editingSlotIndex = slotIndex;
            itemPicker.show();
        });

        recipeGrid.setOnSlotChange((index, data) -> {
            markDirty("Changed ingredient at slot " + index);
            requireCore().invalidateValidation();
        });

        recipeGrid.setOnGridChange(() -> requireCore().invalidateValidation());

        itemPicker.onItemSelected(stack -> {
            if (editingSlotIndex >= 0) {
                recipeGrid.setSlotFromItem(editingSlotIndex, stack);
                editingSlotIndex = -1;
            }
        });

        itemPicker.onTagSelected(tag -> {
            if (editingSlotIndex >= 0) {
                recipeGrid.setSlot(editingSlotIndex, IngredientData.ofTag(tag));
                editingSlotIndex = -1;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        setupCallbacks();
        requireCore().initFromItem(item, recipeGrid);
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();
        requireUi().createAllComponents();

        addTab(ModuleTab.of(TAB_GRID, "Recipe", requireUi()::getGridSections));
        addTab(ModuleTab.of(TAB_RESULT, "Result", () -> requireUi().getResultSections(item)));
        addTab(ModuleTab.of(TAB_SETTINGS, "Settings", () -> requireUi().getSettingsSections(item)));
    }

    // ═══════════════════════════════════════════════════════════════
    // PAYLOAD & APPLY
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        CraftingRecipeData recipe = requireCore().buildCurrentRecipe(item, recipeGrid);
        return RecipeSyncPayload.add(recipe, isGlobal);
    }

    @Nullable
    public CraftingRecipeData buildCurrentRecipeSnapshot() {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return requireCore().buildCurrentRecipe(item, recipeGrid);
    }

    public void applyRecipeSnapshot(@Nullable CraftingRecipeData recipe) {
        if (recipe == null) {
            return;
        }
        ensureDelegates();
        recipeGrid.loadFromRecipe(recipe);
        requireCore().loadFromRecipe(recipe);
        RecipeModuleUI uiRef = ui;
        if (uiRef != null) {
            uiRef.syncFromCore();
        }
    }

    @Override
    public void applyPreview() {
        RecipeModuleCore coreRef = requireCore();
        CraftingRecipeData recipe = coreRef.buildCurrentRecipe(item, recipeGrid);

        RecipeValidator.ValidationResult result = RecipeValidator.validateCrafting(recipe);
        if (!result.valid()) {
            String error = result.getFirstError();
            String errorMsg = I18n.translate("devmod.recipe.validation_failed", error).getString();
            reportStatus(errorMsg, DesignTokens.Accent.RED());
            return;
        }

        try {
            RecipeConfigManager.addRecipeClientOnly(recipe);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                (CustomPacketPayload) Objects.requireNonNull(RecipeSyncPayload.add(recipe, true), "recipe sync payload")
            );
            clearDirty();
            String successMsg = I18n.translate("devmod.recipe.saved_with_id", recipe.id().toString()).getString();
            reportStatus(successMsg, DesignTokens.Accent.GREEN());
        } catch (Exception e) {
            String errorMsg = I18n.translate("devmod.recipe.error.save_failed", e.getMessage()).getString();
            reportStatus(errorMsg, DesignTokens.Accent.RED());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        super.renderContent(graphics, contentBounds, mouseX, mouseY);

        if (activeTabIndex == 0 && !itemPicker.isVisible()) {
            var mc = Minecraft.getInstance();
            recipeGrid.renderTooltip(graphics, Objects.requireNonNull(mc.font, "font"), mouseX, mouseY);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OVERLAY METHODS (ItemPickerOverlay)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean hasActiveOverlay() {
        return itemPicker.isVisible();
    }

    @Override
    public void renderOverlay(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                              int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (itemPicker.isVisible()) {
            itemPicker.render(graphics, font, screenWidth, screenHeight, mouseX, mouseY);
        }
    }

    @Override
    public boolean overlayMouseClicked(double mouseX, double mouseY, int button,
                                       int screenWidth, int screenHeight) {
        if (itemPicker.isVisible()) {
            return itemPicker.mouseClicked(mouseX, mouseY, screenWidth, screenHeight);
        }
        return false;
    }

    @Override
    public boolean overlayMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                        int screenWidth, int screenHeight) {
        if (itemPicker.isVisible()) {
            return itemPicker.mouseScrolled(mouseX, mouseY, scrollDelta, screenWidth, screenHeight);
        }
        return false;
    }

    @Override
    public boolean overlayKeyPressed(int keyCode) {
        if (itemPicker.isVisible()) {
            return itemPicker.keyPressed(keyCode);
        }
        return false;
    }

    @Override
    public boolean overlayCharTyped(char chr, int modifiers) {
        if (itemPicker.isVisible()) {
            return itemPicker.charTyped(chr, modifiers);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTabIndex == 0 && recipeGrid.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (itemPicker.isVisible()) {
            return true;
        }
        if (activeTabIndex == 0 && recipeGrid.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (itemPicker.isVisible()) {
            return true;
        }
        if (activeTabIndex == 0 && recipeGrid.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return super.charTyped(chr, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the current item (exposed for UI delegate).
     */
    @Override
    public ItemStack getItem() {
        return item;
    }
}
