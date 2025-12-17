# UI Components - Recipe Editor

> Componenti UI per editing ricette

## Overview

```
UI Components
├── RecipeGridComponent      # Grid 3x3 interattiva (crafting)
├── IngredientSlotComponent  # Singolo slot ingrediente
├── RecipeResultSlot         # Slot risultato con quantita
└── Editor Modules
    ├── CraftingEditorModule
    ├── SmeltingEditorModule
    ├── SmithingEditorModule
    └── StonecuttingEditorModule
```

---

## RecipeGridComponent

Grid 3x3 interattiva per ricette crafting con drag-drop.

```java
public class RecipeGridComponent {

    // === Constants ===
    private static final int CELL_SIZE = 24;
    private static final int GRID_SIZE = 3;
    private static final int GRID_GAP = 2;

    // === State ===
    private final IngredientSlot[] slots = new IngredientSlot[9];
    private int hoveredSlot = -1;
    private int selectedSlot = -1;
    private ItemStack draggedItem = ItemStack.EMPTY;
    private boolean isDragging = false;

    // Grid position (set during render)
    private int gridX, gridY;
    private int scaledCellSize;

    // === Callbacks ===
    private Consumer<Integer> onSlotClick;
    private BiConsumer<Integer, IngredientData> onSlotChange;

    // === Constructor ===
    public RecipeGridComponent() {
        for (int i = 0; i < 9; i++) {
            slots[i] = IngredientSlot.empty(i);
        }
    }

    // === Rendering ===

    /**
     * Renders the 3x3 grid.
     * @return Total height rendered
     */
    public int render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        scaledCellSize = ScaledCoord.scaleDim(CELL_SIZE);
        int totalSize = scaledCellSize * GRID_SIZE + GRID_GAP * (GRID_SIZE - 1);

        gridX = x;
        gridY = y;

        // Update hover state
        hoveredSlot = getSlotAt(mouseX, mouseY);

        // Render grid background
        int bgColor = UIConstants.Background.PANEL();
        graphics.fill(x - 2, y - 2, x + totalSize + 2, y + totalSize + 2, bgColor);

        // Render slots
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                int cellX = x + col * (scaledCellSize + GRID_GAP);
                int cellY = y + row * (scaledCellSize + GRID_GAP);

                renderSlot(graphics, font, cellX, cellY, index);
            }
        }

        // Render dragged item (follows mouse)
        if (isDragging && !draggedItem.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200); // Above everything
            graphics.renderItem(draggedItem, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }

        return totalSize;
    }

    private void renderSlot(GuiGraphics g, Font font, int x, int y, int index) {
        IngredientSlot slot = slots[index];

        // === Background ===
        int bgColor;
        if (index == selectedSlot) {
            bgColor = UIConstants.Background.ACTIVE();
        } else if (index == hoveredSlot) {
            bgColor = UIConstants.Background.HOVER();
        } else {
            bgColor = UIConstants.Background.INPUT();
        }
        g.fill(x, y, x + scaledCellSize, y + scaledCellSize, bgColor);

        // === Border ===
        int borderColor = (index == selectedSlot)
            ? UIConstants.Border.ACCENT()
            : UIConstants.Border.MUTED();
        AxiomRenderer.drawBorder(g, x, y, scaledCellSize, scaledCellSize, borderColor);

        // === Content ===
        if (!slot.isEmpty()) {
            int pad = (scaledCellSize - 16) / 2;

            if (slot.data().isTag()) {
                // Tag indicator
                g.drawString(font, "#", x + pad, y + pad, UIConstants.Text.ACCENT(), false);

                // Render cycling item from tag
                ItemStack display = slot.data().getDisplayStack(System.currentTimeMillis() / 50);
                if (!display.isEmpty()) {
                    g.renderItem(display, x + pad, y + pad);

                    // Tag overlay
                    g.fill(x + scaledCellSize - 6, y + scaledCellSize - 6,
                           x + scaledCellSize - 2, y + scaledCellSize - 2,
                           0xFFFFAA00); // Orange indicator
                }
            } else {
                // Regular item
                ItemStack display = slot.data().getDisplayStack(0);
                if (!display.isEmpty()) {
                    g.renderItem(display, x + pad, y + pad);
                }
            }
        }

        // === Hover Tooltip ===
        if (index == hoveredSlot && !slot.isEmpty()) {
            // Tooltip rendered separately in renderTooltips()
        }
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredSlot >= 0 && hoveredSlot < 9 && !slots[hoveredSlot].isEmpty()) {
            IngredientSlot slot = slots[hoveredSlot];

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(slot.data().getDisplayName())
                .withStyle(ChatFormatting.WHITE));

            if (slot.data().isTag()) {
                tooltip.add(Component.literal("Tag ingredient")
                    .withStyle(ChatFormatting.GRAY));
            }

            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    // === Input Handling ===

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = getSlotAt((int) mouseX, (int) mouseY);

        if (slot >= 0) {
            if (button == 0) { // Left click - select
                selectedSlot = slot;
                if (onSlotClick != null) {
                    onSlotClick.accept(slot);
                }
                return true;
            } else if (button == 1) { // Right click - clear
                clearSlot(slot);
                return true;
            }
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && selectedSlot >= 0 && !slots[selectedSlot].isEmpty()) {
            isDragging = true;
            draggedItem = slots[selectedSlot].data().getDisplayStack(0);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) {
            int targetSlot = getSlotAt((int) mouseX, (int) mouseY);

            if (targetSlot >= 0 && targetSlot != selectedSlot) {
                // Swap slots
                IngredientSlot temp = slots[targetSlot];
                slots[targetSlot] = slots[selectedSlot].withIndex(targetSlot);
                slots[selectedSlot] = temp.withIndex(selectedSlot);

                notifyChange(targetSlot);
                notifyChange(selectedSlot);
            }

            isDragging = false;
            draggedItem = ItemStack.EMPTY;
            return true;
        }
        return false;
    }

    // === Slot Management ===

    private int getSlotAt(int mouseX, int mouseY) {
        if (mouseX < gridX || mouseY < gridY) return -1;

        int relX = mouseX - gridX;
        int relY = mouseY - gridY;

        int cellWithGap = scaledCellSize + GRID_GAP;
        int col = relX / cellWithGap;
        int row = relY / cellWithGap;

        if (col >= 0 && col < GRID_SIZE && row >= 0 && row < GRID_SIZE) {
            // Check not in gap
            if (relX % cellWithGap < scaledCellSize &&
                relY % cellWithGap < scaledCellSize) {
                return row * GRID_SIZE + col;
            }
        }

        return -1;
    }

    public void setSlot(int index, IngredientData data) {
        if (index < 0 || index >= 9) return;
        slots[index] = new IngredientSlot(index, data);
        notifyChange(index);
    }

    public void clearSlot(int index) {
        setSlot(index, IngredientData.empty());
    }

    public void clear() {
        for (int i = 0; i < 9; i++) {
            slots[i] = IngredientSlot.empty(i);
        }
    }

    public IngredientSlot[] getSlots() {
        return slots.clone();
    }

    public List<IngredientData> getNonEmptyIngredients() {
        return Arrays.stream(slots)
            .filter(s -> !s.isEmpty())
            .map(IngredientSlot::data)
            .toList();
    }

    private void notifyChange(int index) {
        if (onSlotChange != null) {
            onSlotChange.accept(index, slots[index].data());
        }
    }

    // === Callbacks ===

    public void setOnSlotClick(Consumer<Integer> callback) {
        this.onSlotClick = callback;
    }

    public void setOnSlotChange(BiConsumer<Integer, IngredientData> callback) {
        this.onSlotChange = callback;
    }

    // === Load/Save ===

    public void loadFromRecipe(CraftingRecipeData recipe) {
        clear();

        if (recipe.craftingType() == CraftingType.SHAPED && recipe.pattern() != null) {
            // Load from pattern
            String[] pattern = recipe.pattern();
            Map<Character, IngredientData> keyMap = buildKeyMap(recipe);

            for (int row = 0; row < pattern.length && row < 3; row++) {
                String rowStr = pattern[row];
                for (int col = 0; col < rowStr.length() && col < 3; col++) {
                    char c = rowStr.charAt(col);
                    if (c != ' ' && keyMap.containsKey(c)) {
                        int index = row * 3 + col;
                        slots[index] = new IngredientSlot(index, keyMap.get(c));
                    }
                }
            }
        } else {
            // Shapeless - fill sequentially
            List<IngredientData> ingredients = recipe.ingredients();
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                slots[i] = new IngredientSlot(i, ingredients.get(i));
            }
        }
    }

    // === Inner Classes ===

    public record IngredientSlot(int index, IngredientData data) {
        public static IngredientSlot empty(int index) {
            return new IngredientSlot(index, IngredientData.empty());
        }

        public boolean isEmpty() {
            return data.isEmpty();
        }

        public IngredientSlot withIndex(int newIndex) {
            return new IngredientSlot(newIndex, data);
        }
    }
}
```

---

## RecipeResultSlot

Slot per il risultato della ricetta con quantita editabile.

```java
public class RecipeResultSlot {

    private static final int SLOT_SIZE = 32;

    private ItemStack resultItem = ItemStack.EMPTY;
    private int quantity = 1;
    private boolean hovered = false;

    private Consumer<ItemStack> onChange;
    private Consumer<Integer> onQuantityChange;

    // === Rendering ===

    public int render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int size = ScaledCoord.scaleDim(SLOT_SIZE);

        // Check hover
        hovered = mouseX >= x && mouseX < x + size &&
                  mouseY >= y && mouseY < y + size;

        // Background
        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
        graphics.fill(x, y, x + size, y + size, bgColor);

        // Border
        int borderColor = UIConstants.Border.ACCENT();
        AxiomRenderer.drawBorder(g, x, y, size, size, borderColor);

        // Item
        if (!resultItem.isEmpty()) {
            int pad = (size - 16) / 2;
            graphics.renderItem(resultItem, x + pad, y + pad);

            // Quantity badge
            if (quantity > 1) {
                String qtyStr = String.valueOf(quantity);
                int qtyWidth = font.width(qtyStr);
                graphics.drawString(font, qtyStr,
                    x + size - qtyWidth - 2,
                    y + size - font.lineHeight - 1,
                    UIConstants.Text.PRIMARY(), false);
            }
        } else {
            // Empty indicator
            graphics.drawString(font, "?", x + size/2 - 3, y + size/2 - 4,
                UIConstants.Text.MUTED(), false);
        }

        // Label
        graphics.drawString(font, "Result", x, y - font.lineHeight - 2,
            UIConstants.Text.SECONDARY(), false);

        return size;
    }

    // === Input ===

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hovered) {
            if (button == 0) {
                // Open item selector
                // TODO: Implement item picker popup
                return true;
            } else if (button == 1) {
                // Clear
                setItem(ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hovered && !resultItem.isEmpty()) {
            int newQty = Mth.clamp(quantity + (int) Math.signum(delta), 1, 64);
            if (newQty != quantity) {
                quantity = newQty;
                if (onQuantityChange != null) {
                    onQuantityChange.accept(quantity);
                }
            }
            return true;
        }
        return false;
    }

    // === Getters/Setters ===

    public void setItem(ItemStack item) {
        this.resultItem = item.copy();
        if (onChange != null) {
            onChange.accept(resultItem);
        }
    }

    public ItemStack getItem() {
        return resultItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int qty) {
        this.quantity = Mth.clamp(qty, 1, 64);
    }

    public ResultData toResultData() {
        if (resultItem.isEmpty()) return null;
        return ResultData.of(resultItem).withCount(quantity);
    }

    public void setOnChange(Consumer<ItemStack> callback) {
        this.onChange = callback;
    }

    public void setOnQuantityChange(Consumer<Integer> callback) {
        this.onQuantityChange = callback;
    }
}
```

---

## CraftingEditorModule

Module principale per editing ricette crafting.

```java
public class CraftingEditorModule extends AbstractEditorModule {

    private static final String TAB_GRID = "grid";
    private static final String TAB_RESULT = "result";
    private static final String TAB_SETTINGS = "settings";

    // === Components ===
    private final RecipeGridComponent recipeGrid = new RecipeGridComponent();
    private final RecipeResultSlot resultSlot = new RecipeResultSlot();

    // === State ===
    private CraftingRecipeData currentRecipe;
    private CraftingRecipeData originalRecipe;
    private CraftingType selectedType = CraftingType.SHAPED;
    private String recipeId = "";
    private String recipeGroup = "";
    private RecipeCategory category = RecipeCategory.MISC;

    // === UI Components ===
    private EditorTextField idField;
    private EditorTextField groupField;
    private EditorToggle shapedToggle;

    public CraftingEditorModule(Font font) {
        super(font);
        setupCallbacks();
    }

    private void setupCallbacks() {
        recipeGrid.setOnSlotChange((index, data) -> {
            markDirty("Changed ingredient at slot " + index);
            updateValidation();
        });

        resultSlot.setOnChange(item -> {
            markDirty("Changed result item");
            updateValidation();
        });

        resultSlot.setOnQuantityChange(qty -> {
            markDirty("Changed result quantity");
        });
    }

    // === AbstractEditorModule Implementation ===

    @Override
    public void onItemSet(ItemStack stack) {
        // Create empty recipe with this item as result
        this.currentRecipe = CraftingRecipeData.empty(stack);
        this.originalRecipe = currentRecipe;

        resultSlot.setItem(stack);
        recipeGrid.clear();
        recipeId = generateRecipeId(stack);

        initializeTabs();
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // === Tab 1: Recipe Grid ===
        tabs.add(ModuleTab.of(TAB_GRID, "Recipe", () -> {
            List<EditorSection> sections = new ArrayList<>();

            // Type toggle (Shaped/Shapeless)
            sections.add(new HeaderSection("Recipe Type"));
            sections.add(createTypeToggle());

            // Grid
            sections.add(new HeaderSection("Ingredients"));
            sections.add(new RecipeGridSection(recipeGrid));

            return sections;
        }));

        // === Tab 2: Result ===
        tabs.add(ModuleTab.of(TAB_RESULT, "Result", () -> {
            List<EditorSection> sections = new ArrayList<>();

            sections.add(new HeaderSection("Output Item"));
            sections.add(new RecipeResultSection(resultSlot));

            sections.add(new HeaderSection("Quantity"));
            sections.add(createQuantitySlider());

            return sections;
        }));

        // === Tab 3: Settings ===
        tabs.add(ModuleTab.of(TAB_SETTINGS, "Settings", () -> {
            List<EditorSection> sections = new ArrayList<>();

            sections.add(new HeaderSection("Recipe ID"));
            sections.add(createIdInput());

            sections.add(new HeaderSection("Category"));
            sections.add(createCategorySelector());

            sections.add(new HeaderSection("Group (Optional)"));
            sections.add(createGroupInput());

            sections.add(new SpacerSection(16));
            sections.add(createValidationSection());

            return sections;
        }));
    }

    // === UI Factories ===

    private EditorSection createTypeToggle() {
        shapedToggle = new EditorToggle(
            "Shaped Recipe",
            selectedType == CraftingType.SHAPED,
            value -> {
                selectedType = value ? CraftingType.SHAPED : CraftingType.SHAPELESS;
                markDirty("Changed recipe type");
            }
        );
        shapedToggle.setDescription(
            selectedType == CraftingType.SHAPED
                ? "Items must match exact positions"
                : "Items can be in any position"
        );
        return new ToggleSection(shapedToggle);
    }

    private EditorSection createQuantitySlider() {
        EditorSlider slider = new EditorSlider(
            "Count",
            1, 64, resultSlot.getQuantity(),
            value -> {
                resultSlot.setQuantity((int) value);
                markDirty("Changed quantity");
            }
        );
        slider.setIntegerMode(true);
        return new SliderSection(slider);
    }

    private EditorSection createIdInput() {
        idField = new EditorTextField(
            recipeId,
            text -> {
                recipeId = text;
                markDirty("Changed recipe ID");
                updateValidation();
            }
        );
        idField.setPlaceholder("devmod:my_recipe");
        return new TextFieldSection(idField);
    }

    private EditorSection createCategorySelector() {
        List<String> options = Arrays.stream(RecipeCategory.values())
            .filter(c -> c != RecipeCategory.FOOD && c != RecipeCategory.BLOCKS) // Crafting only
            .map(RecipeCategory::getId)
            .toList();

        return new DropdownSection(
            options,
            category.getId(),
            selected -> {
                category = RecipeCategory.fromString(selected);
                markDirty("Changed category");
            }
        );
    }

    private EditorSection createGroupInput() {
        groupField = new EditorTextField(
            recipeGroup,
            text -> {
                recipeGroup = text;
                markDirty("Changed group");
            }
        );
        groupField.setPlaceholder("(optional)");
        return new TextFieldSection(groupField);
    }

    private EditorSection createValidationSection() {
        return new ValidationSection(() -> {
            CraftingRecipeData recipe = buildCurrentRecipe();
            return RecipeValidator.validate(recipe);
        });
    }

    // === Build Recipe ===

    private CraftingRecipeData buildCurrentRecipe() {
        List<IngredientData> ingredients = recipeGrid.getNonEmptyIngredients();

        String[] pattern = null;
        Map<Character, Integer> keyToSlot = null;

        if (selectedType == CraftingType.SHAPED) {
            // Build pattern from grid
            pattern = buildPatternFromGrid();
            keyToSlot = buildKeyMap();
        }

        return new CraftingRecipeData(
            ResourceLocation.tryParse(recipeId),
            selectedType,
            category,
            recipeGroup.isEmpty() ? null : recipeGroup,
            ingredients,
            pattern,
            keyToSlot,
            resultSlot.toResultData(),
            true, // showNotification
            !equals(originalRecipe), // isModified
            originalRecipe != null ? originalRecipe.id() : null
        );
    }

    private String[] buildPatternFromGrid() {
        StringBuilder[] rows = new StringBuilder[3];
        for (int i = 0; i < 3; i++) {
            rows[i] = new StringBuilder();
        }

        char nextKey = 'A';
        Map<String, Character> ingredientToKey = new HashMap<>();

        RecipeGridComponent.IngredientSlot[] slots = recipeGrid.getSlots();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                RecipeGridComponent.IngredientSlot slot = slots[index];

                if (slot.isEmpty()) {
                    rows[row].append(' ');
                } else {
                    String key = slot.data().getDisplayName();
                    if (!ingredientToKey.containsKey(key)) {
                        ingredientToKey.put(key, nextKey++);
                    }
                    rows[row].append(ingredientToKey.get(key));
                }
            }
        }

        // Trim empty rows
        List<String> result = new ArrayList<>();
        for (StringBuilder row : rows) {
            String s = row.toString();
            if (!s.isBlank()) {
                result.add(s.stripTrailing());
            }
        }

        return result.toArray(new String[0]);
    }

    // === Payload ===

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        CraftingRecipeData recipe = buildCurrentRecipe();
        return new RecipeSyncPayload(List.of(recipe), isGlobal);
    }

    @Override
    public void applyPreview() {
        CraftingRecipeData recipe = buildCurrentRecipe();

        // Validate
        RecipeValidator.ValidationResult result = RecipeValidator.validate(recipe);
        if (!result.valid()) {
            showValidationErrors(result.errors());
            return;
        }

        // Apply locally
        RecipeConfigManager.addRecipe(recipe);

        // Update state
        originalRecipe = recipe;
        clearDirty();
    }

    // === Helpers ===

    private String generateRecipeId(ItemStack stack) {
        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return "devmod:custom_" + itemPath + "_" + System.currentTimeMillis() % 10000;
    }

    private void updateValidation() {
        // Trigger validation UI update
        // TODO: Implement
    }
}
```

---

## SmeltingEditorModule

Module per ricette smelting/blasting/smoking/campfire.

```java
public class SmeltingEditorModule extends AbstractEditorModule {

    // === State ===
    private SmeltingType smeltingType = SmeltingType.SMELTING;
    private IngredientData inputIngredient = IngredientData.empty();
    private ResultData result;
    private float experience = 0.0f;
    private int cookingTime = 200;

    // === Components ===
    private IngredientSlotComponent inputSlot;
    private RecipeResultSlot resultSlot;

    public SmeltingEditorModule(Font font) {
        super(font);
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        tabs.add(ModuleTab.of("recipe", "Recipe", () -> List.of(
            new HeaderSection("Smelting Type"),
            createTypeSelector(),
            new HeaderSection("Input"),
            new SlotSection(inputSlot),
            new HeaderSection("Output"),
            new RecipeResultSection(resultSlot)
        )));

        tabs.add(ModuleTab.of("settings", "Settings", () -> List.of(
            new HeaderSection("Experience"),
            createExperienceSlider(),
            new HeaderSection("Cooking Time"),
            createCookingTimeSlider(),
            new SpacerSection(8),
            createCookingTimeInfo()
        )));
    }

    private EditorSection createTypeSelector() {
        List<String> options = Arrays.stream(SmeltingType.values())
            .map(t -> t.name().toLowerCase())
            .toList();

        return new DropdownSection(
            options,
            smeltingType.name().toLowerCase(),
            selected -> {
                smeltingType = SmeltingType.valueOf(selected.toUpperCase());
                cookingTime = SmeltingRecipeData.defaultCookingTime(smeltingType);
                markDirty("Changed smelting type");
            }
        );
    }

    private EditorSection createExperienceSlider() {
        return new SliderSection(new EditorSlider(
            "XP",
            0.0f, 10.0f, experience,
            value -> {
                experience = value;
                markDirty("Changed experience");
            }
        ).setStep(0.1f).setSuffix(" XP"));
    }

    private EditorSection createCookingTimeSlider() {
        return new SliderSection(new EditorSlider(
            "Time",
            20, 1200, cookingTime,
            value -> {
                cookingTime = (int) value;
                markDirty("Changed cooking time");
            }
        ).setIntegerMode(true).setSuffix(" ticks"));
    }

    private EditorSection createCookingTimeInfo() {
        float seconds = cookingTime / 20.0f;
        return new InfoSection(String.format("%.1f seconds", seconds));
    }
}
```

---

## SmithingEditorModule

```java
public class SmithingEditorModule extends AbstractEditorModule {

    private SmithingType smithingType = SmithingType.TRANSFORM;
    private IngredientData template = IngredientData.empty();
    private IngredientData base = IngredientData.empty();
    private IngredientData addition = IngredientData.empty();
    private ResultData result;

    @Override
    protected void initializeTabs() {
        tabs.add(ModuleTab.of("recipe", "Smithing", () -> List.of(
            new HeaderSection("Type"),
            createTypeToggle(),
            new HeaderSection("Template"),
            new SlotSection(templateSlot),
            new HeaderSection("Base Item"),
            new SlotSection(baseSlot),
            new HeaderSection("Addition"),
            new SlotSection(additionSlot),
            smithingType == SmithingType.TRANSFORM
                ? new HeaderSection("Result")
                : null,
            smithingType == SmithingType.TRANSFORM
                ? new RecipeResultSection(resultSlot)
                : null
        ).stream().filter(Objects::nonNull).toList()));
    }
}
```

---

## StonecuttingEditorModule

```java
public class StonecuttingEditorModule extends AbstractEditorModule {

    private IngredientData input = IngredientData.empty();
    private ResultData result;

    @Override
    protected void initializeTabs() {
        tabs.add(ModuleTab.of("recipe", "Stonecutting", () -> List.of(
            new HeaderSection("Input Block"),
            new SlotSection(inputSlot),
            new HeaderSection("Output"),
            new RecipeResultSection(resultSlot)
        )));
    }
}
```
