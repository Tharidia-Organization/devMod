package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.ItemPickerOverlay;
import com.devmod.client.ui.editor.components.RecipeGridComponent;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.sections.InputSectionAdapter;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.SimpleSpacer;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.network.RecipeSyncPayload;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.CraftingType;
import com.devmod.recipe.IngredientData;
import com.devmod.recipe.RecipeCategory;
import com.devmod.recipe.RecipeConfigManager;
import com.devmod.recipe.RecipeValidator;
import com.devmod.recipe.ResultData;
import com.devmod.util.I18n;

public class RecipeModule extends AbstractEditorModule {

    private static final String TAB_GRID = "grid";
    private static final String TAB_RESULT = "result";
    private static final String TAB_SETTINGS = "settings";

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private CraftingType craftingType = CraftingType.SHAPED;
    private String recipeId = "";
    private String recipeGroup = "";
    private RecipeCategory category = RecipeCategory.MISC;
    private int resultQuantity = 1;
    private boolean replaceVanillaRecipe = false;
    private @Nullable ResourceLocation vanillaRecipeToReplace = null;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    private final RecipeGridComponent recipeGrid = new RecipeGridComponent();
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();
    private @Nullable EditorToggle shapedToggle;
    private @Nullable EditorToggle replaceVanillaToggle;
    private @Nullable EditorTextField idField;
    private @Nullable EditorTextField groupField;
    private @Nullable EditorSlider quantitySlider;

    // Validation state
    private @Nullable RecipeValidator.ValidationResult lastValidation;
    private boolean validationDirty = true;

    // Tracks if callbacks have been initialized
    private boolean callbacksInitialized = false;

    // Track which slot is being edited (for item picker callback)
    private int editingSlotIndex = -1;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public RecipeModule() {
        super("recipe", "Recipe Editor");
        // Note: setupCallbacks() moved to onItemSet() to avoid this-escape
    }

    private void setupCallbacks() {
        if (callbacksInitialized) return;
        callbacksInitialized = true;

        // When slot is clicked, open item picker
        recipeGrid.setOnSlotClick(slotIndex -> {
            editingSlotIndex = slotIndex;
            itemPicker.show();
        });

        recipeGrid.setOnSlotChange((index, data) -> {
            markDirty("Changed ingredient at slot " + index);
            validationDirty = true;
        });

        recipeGrid.setOnGridChange(() -> {
            validationDirty = true;
        });

        // Item picker callbacks
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
        // Initialize callbacks (safe here, object is fully constructed)
        setupCallbacks();

        // Create empty recipe with current item as result
        CraftingRecipeData recipe = CraftingRecipeData.empty(item);
        // Initialize state from recipe
        craftingType = recipe.craftingType();
        recipeId = recipe.id().toString();
        recipeGroup = recipe.group() != null ? recipe.group() : "";
        category = recipe.category();
        resultQuantity = recipe.result().count();

        // Load grid
        recipeGrid.loadFromRecipe(recipe);

        // Mark validation as dirty
        validationDirty = true;
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();
        createComponents();

        // Tab 1: Recipe Grid
        addTab(ModuleTab.of(TAB_GRID, "Recipe", this::getGridSections));

        // Tab 2: Result
        addTab(ModuleTab.of(TAB_RESULT, "Result", this::getResultSections));

        // Tab 3: Settings
        addTab(ModuleTab.of(TAB_SETTINGS, "Settings", this::getSettingsSections));
    }

    private void createComponents() {
        // Shaped toggle
        shapedToggle = new EditorToggle("shaped_toggle", "Shaped Recipe", craftingType == CraftingType.SHAPED)
            .tooltip("Shaped = exact item positions required. Shapeless = items can be placed anywhere in the grid.")
            .onChange(value -> {
                craftingType = value ? CraftingType.SHAPED : CraftingType.SHAPELESS;
                markDirty("Changed recipe type to " + craftingType.getId());
                validationDirty = true;
            });

        // Recipe ID field
        idField = new EditorTextField("recipe_id", "Recipe ID")
            .placeholder("devmod:my_recipe")
            .onChange(text -> {
                recipeId = text;
                markDirty("Changed recipe ID");
                validationDirty = true;
            });
        idField.setValue(recipeId);

        // Group field
        groupField = new EditorTextField("recipe_group", "Group")
            .placeholder("(optional)")
            .onChange(text -> {
                recipeGroup = text;
                markDirty("Changed group");
            });
        groupField.setValue(recipeGroup);

        // Quantity slider
        quantitySlider = new EditorSlider("result_quantity", "Quantity", 1, 64, resultQuantity)
            .step(1)
            .format("%.0f")
            .suffix(" items")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .info("Number of items produced per craft. Most recipes produce 1, but some (planks, sticks) produce more.")
            .onChange(value -> {
                resultQuantity = value.intValue();
                markDirty("Changed result quantity");
            });

        // Replace vanilla toggle
        replaceVanillaToggle = new EditorToggle("replace_vanilla", "Replace Vanilla Recipe", replaceVanillaRecipe)
            .tooltip("When enabled, this recipe will replace any existing vanilla recipe for this item.")
            .onChange(value -> {
                replaceVanillaRecipe = value;
                if (value) {
                    // Try to find a vanilla recipe for this item to replace
                    vanillaRecipeToReplace = findVanillaRecipeForItem();
                } else {
                    vanillaRecipeToReplace = null;
                }
                markDirty("Changed replace vanilla setting");
            });
    }

    /**
     * Find a vanilla or modded recipe that produces this item.
     * Searches all recipe types: crafting, smelting, blasting, smoking, campfire, smithing, stonecutting.
     */
    private @Nullable ResourceLocation findVanillaRecipeForItem() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return null;

        @Nonnull var safeLevel = Objects.requireNonNull(level, "level");
        var recipeManager = safeLevel.getRecipeManager();
        @Nonnull var baseItem = Objects.requireNonNull(item.getItem(), "item");
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(baseItem);
        @Nonnull var registryAccess = Objects.requireNonNull(safeLevel.registryAccess(), "registry access");

        // Search crafting recipes
        @Nonnull var craftingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING,
            "crafting recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(craftingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smelting recipes
        @Nonnull var smeltingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMELTING,
            "smelting recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(smeltingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search blasting recipes
        @Nonnull var blastingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.BLASTING,
            "blasting recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(blastingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smoking recipes
        @Nonnull var smokingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMOKING,
            "smoking recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(smokingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search campfire cooking recipes
        @Nonnull var campfireType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING,
            "campfire recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(campfireType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smithing recipes
        @Nonnull var smithingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMITHING,
            "smithing recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(smithingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search stonecutting recipes
        @Nonnull var stonecuttingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.STONECUTTING,
            "stonecutting recipe type"
        );
        for (var holder : recipeManager.getAllRecipesFor(stonecuttingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTIONS
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getGridSections() {
        List<EditorSection> sections = new ArrayList<>();

        // Type toggle section
        sections.add(new SimpleHeaderSection("type_header", "Recipe Type"));
        sections.add(new ToggleSectionAdapter(Objects.requireNonNull(shapedToggle, "shapedToggle")));

        // Type info
        String typeInfo = craftingType == CraftingType.SHAPED
            ? "Items must match exact positions"
            : "Items can be in any position";
        sections.add(new TextNoteSection("type_info", typeInfo));

        // Spacer
        sections.add(new SimpleSpacer("spacer1", 8));

        // Grid section
        sections.add(new SimpleHeaderSection("grid_header", "Ingredients"));
        sections.add(new RecipeGridSection("grid", recipeGrid));

        return sections;
    }

    private List<EditorSection> getResultSections() {
        List<EditorSection> sections = new ArrayList<>();

        // Result item display
        sections.add(new SimpleHeaderSection("result_header", "Output Item"));
        sections.add(new ResultItemSection("result_item", item));

        // Quantity slider
        sections.add(new SimpleHeaderSection("qty_header", "Quantity"));
        sections.add(new SliderSectionAdapter(Objects.requireNonNull(quantitySlider, "quantitySlider")));

        return sections;
    }

    private List<EditorSection> getSettingsSections() {
        List<EditorSection> sections = new ArrayList<>();

        // Recipe ID
        sections.add(new SimpleHeaderSection("id_header", "Recipe ID"));
        sections.add(new InputSectionAdapter(Objects.requireNonNull(idField, "idField")));

        // Category
        sections.add(new SimpleHeaderSection("cat_header", "Category"));
        sections.add(new CategorySection("category", category, cat -> {
            category = cat;
            markDirty("Changed category");
        }));

        // Group
        sections.add(new SimpleHeaderSection("group_header", "Group (Optional)"));
        sections.add(new InputSectionAdapter(Objects.requireNonNull(groupField, "groupField")));

        // Spacer
        sections.add(new SimpleSpacer("spacer2", 16));

        // Replace vanilla toggle
        sections.add(new SimpleHeaderSection("replace_header", "Override"));
        sections.add(new ToggleSectionAdapter(Objects.requireNonNull(replaceVanillaToggle, "replaceVanillaToggle")));

        // Show which recipe will be replaced (if any)
        ResourceLocation replaceTarget = vanillaRecipeToReplace;
        if (replaceVanillaRecipe && replaceTarget != null) {
            sections.add(new TextNoteSection("replace_info",
                "Will replace: " + replaceTarget.toString()));
        } else if (replaceVanillaRecipe) {
            sections.add(new TextNoteSection("replace_info",
                "No vanilla recipe found for this item"));
        }

        // Spacer
        sections.add(new SimpleSpacer("spacer3", 16));

        // Validation
        sections.add(new SimpleHeaderSection("valid_header", "Validation"));
        sections.add(new ValidationSection("validation", this::getValidation));

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    private RecipeValidator.ValidationResult getValidation() {
        if (validationDirty || lastValidation == null) {
            CraftingRecipeData recipe = buildCurrentRecipe();
            lastValidation = RecipeValidator.validateCrafting(recipe);
            validationDirty = false;
        }
        return Objects.requireNonNull(lastValidation, "lastValidation");
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILD RECIPE
    // ═══════════════════════════════════════════════════════════════

    private CraftingRecipeData buildCurrentRecipe() {
        List<IngredientData> grid = recipeGrid.getIngredientList();

        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(recipeId, "recipeId"));
        if (id == null) {
            id = ResourceLocation.fromNamespaceAndPath("devmod", "invalid_" + System.currentTimeMillis());
        }

        ResultData result = ResultData.of(item).withCount(resultQuantity);

        CraftingRecipeData recipe;
        if (craftingType == CraftingType.SHAPED) {
            // Build as shaped with grid
            recipe = CraftingRecipeData.empty(item)
                .withId(id)
                .withGrid(grid)
                .withResult(result)
                .withCategory(category);
        } else {
            // Build as shapeless
            List<IngredientData> nonEmpty = grid.stream()
                .filter(i -> !i.isEmpty())
                .toList();

            recipe = CraftingRecipeData.shapeless(id, nonEmpty, result)
                .withCategory(category);
        }

        // If replacing vanilla recipe, set the originalId
        ResourceLocation replaceTarget = vanillaRecipeToReplace;
        if (replaceVanillaRecipe && replaceTarget != null) {
            recipe = recipe.withOriginalId(replaceTarget);
        }

        return recipe;
    }

    // ═══════════════════════════════════════════════════════════════
    // PAYLOAD & APPLY
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        CraftingRecipeData recipe = buildCurrentRecipe();
        return RecipeSyncPayload.add(recipe, isGlobal);
    }

    @Override
    public void applyPreview() {
        CraftingRecipeData recipe = buildCurrentRecipe();

        // Null safety check
        if (recipe == null) {
            reportStatus(I18n.translate("devmod.recipe.error.build_failed").getString(), DesignTokens.Accent.RED());
            return;
        }

        // Validate
        RecipeValidator.ValidationResult result = RecipeValidator.validateCrafting(recipe);
        if (result == null || !result.valid()) {
            String error = result != null ? result.getFirstError() : "Unknown validation error";
            String errorMsg = I18n.translate("devmod.recipe.validation_failed", error).getString();
            reportStatus(errorMsg, DesignTokens.Accent.RED());
            return;
        }

        try {
            // Store locally on client for immediate use
            RecipeConfigManager.addRecipeClientOnly(recipe);

            // Send to server for persistence and broadcast
            // The server will handle saving and syncing to all clients
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                (CustomPacketPayload) Objects.requireNonNull(RecipeSyncPayload.add(recipe, true), "recipe sync payload")
            );

            // Update state
            clearDirty();

            String successMsg = I18n.translate("devmod.recipe.saved_with_id", recipe.id().toString()).getString();
            reportStatus(successMsg, DesignTokens.Accent.GREEN());
        } catch (Exception e) {
            String errorMsg = I18n.translate("devmod.recipe.error.save_failed", e.getMessage()).getString();
            reportStatus(errorMsg, DesignTokens.Accent.RED());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING OVERRIDE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        // Let parent render sections
        super.renderContent(graphics, contentBounds, mouseX, mouseY);

        // Render grid tooltip if on grid tab (only if picker is not visible)
        if (activeTabIndex == 0 && !itemPicker.isVisible()) {
            var mc = Minecraft.getInstance();
            recipeGrid.renderTooltip(
                graphics,
                Objects.requireNonNull(mc.font, "font"),
                mouseX,
                mouseY
            );
        }
        // Note: ItemPicker overlay is now rendered via renderOverlay() from ItemEditorScreen
    }

    // ═══════════════════════════════════════════════════════════════
    // OVERLAY METHODS (for ItemPickerOverlay)
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
        // Item picker input is now handled by ItemEditorScreen via overlayMouseClicked

        // Grid handles its own clicks on grid tab
        if (activeTabIndex == 0 && recipeGrid.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Item picker consumes drag when visible
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
        // Item picker consumes release when visible
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
        // Item picker scroll is now handled by ItemEditorScreen via overlayMouseScrolled
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Item picker key handling is now handled by ItemEditorScreen via overlayKeyPressed
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Item picker char handling is now handled by ItemEditorScreen via overlayCharTyped
        return super.charTyped(chr, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER SECTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Section that renders the recipe grid.
     */
    private record RecipeGridSection(String id, RecipeGridComponent grid) implements EditorSection.CustomSection {
        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 88; } // 3x24 + 2x2 gaps + padding

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            grid.render(graphics, Objects.requireNonNull(Minecraft.getInstance().font, "font"),
                bounds.x() + 8, bounds.y() + 4, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return grid.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return grid.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return grid.mouseReleased(mouseX, mouseY, button);
        }
    }

    /**
     * Result item display section.
     */
    private record ResultItemSection(String id, ItemStack item) implements EditorSection.CustomSection {
        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 32; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            // Background
            graphics.fill(bounds.x() + 4, bounds.y(), bounds.x() + 36, bounds.y() + 32, DesignTokens.Background.INPUT());

            // Item
            graphics.renderItem(Objects.requireNonNull(item, "item"), bounds.x() + 12, bounds.y() + 8);

            // Name
            Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
            String name = Objects.requireNonNull(item.getHoverName().getString(), "itemName");
            graphics.drawString(font, name, bounds.x() + 44, bounds.y() + 12, DesignTokens.Text.PRIMARY(), false);
        }
    }

    /**
     * Category selector section.
     */
    private static class CategorySection implements EditorSection.ListSection {
        private final String id;
        private RecipeCategory selected;
        private final java.util.function.Consumer<RecipeCategory> onChange;
        private final List<String> options;

        CategorySection(String id, RecipeCategory initial, java.util.function.Consumer<RecipeCategory> onChange) {
            this.id = id;
            this.selected = initial;
            this.onChange = onChange;
            this.options = new ArrayList<>();
            for (RecipeCategory cat : RecipeCategory.craftingCategories()) {
                options.add(cat.getId());
            }
        }

        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 24; }

        @Override
        public List<String> getOptions() { return options; }

        @Override
        public int getSelectedIndex() {
            return options.indexOf(selected.getId());
        }

        @Override
        public void setSelectedIndex(int index) {
            if (index >= 0 && index < options.size()) {
                selected = RecipeCategory.fromString(options.get(index));
                onChange.accept(selected);
            }
        }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
            // Simple text display for now
            graphics.fill(bounds.x() + 4, bounds.y(), bounds.x() + bounds.width() - 4, bounds.y() + 20,
                DesignTokens.Background.INPUT());
            graphics.drawString(font, Objects.requireNonNull(selected.getId(), "categoryId"), bounds.x() + 8, bounds.y() + 6,
                DesignTokens.Text.PRIMARY(), false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Cycle through options on click
            if (button == 0) {
                int next = (getSelectedIndex() + 1) % options.size();
                setSelectedIndex(next);
                return true;
            }
            return false;
        }
    }

    /**
     * Validation display section.
     */
    private record ValidationSection(String id, java.util.function.Supplier<RecipeValidator.ValidationResult> resultSupplier)
        implements EditorSection.CustomSection {

        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 40; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
            RecipeValidator.ValidationResult result = resultSupplier.get();

            int y = bounds.y() + 4;

            if (result.valid()) {
                int color = result.hasWarnings() ? DesignTokens.Text.WARNING() : DesignTokens.Accent.GREEN();
                graphics.drawString(font, Objects.requireNonNull(result.getSummary(), "summary"), bounds.x() + 8, y, color, false);

                // Show warnings
                y += 12;
                for (String warning : result.warnings()) {
                    graphics.drawString(font, Objects.requireNonNull("- " + warning, "warning"),
                        bounds.x() + 12, y, DesignTokens.Text.MUTED(), false);
                    y += 10;
                }
            } else {
                graphics.drawString(font, Objects.requireNonNull(result.getSummary(), "summary"),
                    bounds.x() + 8, y, DesignTokens.Accent.RED(), false);

                // Show errors
                y += 12;
                for (String error : result.errors()) {
                    graphics.drawString(font, Objects.requireNonNull("- " + error, "error"),
                        bounds.x() + 12, y, DesignTokens.Accent.RED(), false);
                    y += 10;
                }
            }
        }
    }

}
