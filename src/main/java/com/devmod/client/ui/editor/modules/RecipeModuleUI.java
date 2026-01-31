package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.RecipeGridComponent;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.sections.InputSectionAdapter;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.SimpleSpacer;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.recipe.CraftingType;
import com.devmod.recipe.RecipeCategory;
import com.devmod.recipe.RecipeValidator;

/**
 * UI component management for RecipeModule.
 * Handles component creation and section building.
 */
public class RecipeModuleUI {

    private final RecipeModule module;
    private final RecipeModuleCore core;
    private final RecipeGridComponent recipeGrid;

    // UI Components
    private @Nullable EditorToggle shapedToggle;
    private @Nullable EditorToggle replaceVanillaToggle;
    private @Nullable EditorTextField idField;
    private @Nullable EditorTextField groupField;
    private @Nullable EditorSlider quantitySlider;

    public RecipeModuleUI(RecipeModule module, RecipeModuleCore core, RecipeGridComponent recipeGrid) {
        this.module = Objects.requireNonNull(module, "module cannot be null");
        this.core = Objects.requireNonNull(core, "core cannot be null");
        this.recipeGrid = Objects.requireNonNull(recipeGrid, "recipeGrid cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT CREATION
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents() {
        createShapedToggle();
        createIdField();
        createGroupField();
        createQuantitySlider();
        createReplaceVanillaToggle();
    }

    public void syncFromCore() {
        if (shapedToggle != null) {
            shapedToggle.setValue(core.getCraftingType() == CraftingType.SHAPED);
        }
        if (idField != null) {
            idField.setValue(core.getRecipeId());
        }
        if (groupField != null) {
            groupField.setValue(core.getRecipeGroup());
        }
        if (quantitySlider != null) {
            quantitySlider.setValue(core.getResultQuantity());
        }
        if (replaceVanillaToggle != null) {
            replaceVanillaToggle.setValue(core.isReplaceVanillaRecipe());
        }
    }

    private void createShapedToggle() {
        shapedToggle = new EditorToggle("shaped_toggle", "Shaped Recipe",
                core.getCraftingType() == CraftingType.SHAPED)
            .tooltip("Shaped = exact item positions required. Shapeless = items can be placed anywhere in the grid.")
            .onChange(value -> {
                core.setCraftingType(value ? CraftingType.SHAPED : CraftingType.SHAPELESS);
                module.markDirty("Changed recipe type to " + core.getCraftingType().getId());
            });
    }

    private void createIdField() {
        EditorTextField idLocal = new EditorTextField("recipe_id", "Recipe ID")
            .placeholder("devmod:my_recipe")
            .onChange(text -> {
                core.setRecipeId(text);
                module.markDirty("Changed recipe ID");
            });
        idLocal.setValue(core.getRecipeId());
        this.idField = idLocal;
    }

    private void createGroupField() {
        EditorTextField groupLocal = new EditorTextField("recipe_group", "Group")
            .placeholder("(optional)")
            .onChange(text -> {
                core.setRecipeGroup(text);
                module.markDirty("Changed group");
            });
        groupLocal.setValue(core.getRecipeGroup());
        this.groupField = groupLocal;
    }

    private void createQuantitySlider() {
        quantitySlider = new EditorSlider("result_quantity", "Quantity", 1, 64, core.getResultQuantity())
            .step(1)
            .format("%.0f")
            .suffix(" items")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .info("Number of items produced per craft. Most recipes produce 1, but some (planks, sticks) produce more.")
            .onChange(value -> {
                core.setResultQuantity(value.intValue());
                module.markDirty("Changed result quantity");
            });
    }

    private void createReplaceVanillaToggle() {
        replaceVanillaToggle = new EditorToggle("replace_vanilla", "Replace Vanilla Recipe",
                core.isReplaceVanillaRecipe())
            .tooltip("When enabled, this recipe will replace any existing vanilla recipe for this item.")
            .onChange(value -> {
                core.setReplaceVanillaRecipe(value);
                if (value) {
                    ResourceLocation found = core.findVanillaRecipeForItem(module.getItem());
                    core.setVanillaRecipeToReplace(found);
                } else {
                    core.setVanillaRecipeToReplace(null);
                }
                module.markDirty("Changed replace vanilla setting");
            });
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getGridSections() {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new SimpleHeaderSection("type_header", "Recipe Type"));
        sections.add(new ToggleSectionAdapter(Objects.requireNonNull(shapedToggle, "shapedToggle")));

        String typeInfo = core.getCraftingType() == CraftingType.SHAPED
            ? "Items must match exact positions"
            : "Items can be in any position";
        sections.add(new TextNoteSection("type_info", typeInfo));
        sections.add(new SimpleSpacer("spacer1", 8));

        sections.add(new SimpleHeaderSection("grid_header", "Ingredients"));
        sections.add(new RecipeGridSection("grid", recipeGrid));

        return sections;
    }

    public List<EditorSection> getResultSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new SimpleHeaderSection("result_header", "Output Item"));
        sections.add(new ResultItemSection("result_item", item));

        sections.add(new SimpleHeaderSection("qty_header", "Quantity"));
        sections.add(new SliderSectionAdapter(Objects.requireNonNull(quantitySlider, "quantitySlider")));

        return sections;
    }

    public List<EditorSection> getSettingsSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new SimpleHeaderSection("id_header", "Recipe ID"));
        sections.add(new InputSectionAdapter(Objects.requireNonNull(idField, "idField")));

        sections.add(new SimpleHeaderSection("cat_header", "Category"));
        sections.add(new CategorySection("category", core.getCategory(), cat -> {
            core.setCategory(cat);
            module.markDirty("Changed category");
        }));

        sections.add(new SimpleHeaderSection("group_header", "Group (Optional)"));
        sections.add(new InputSectionAdapter(Objects.requireNonNull(groupField, "groupField")));

        sections.add(new SimpleSpacer("spacer2", 16));

        sections.add(new SimpleHeaderSection("replace_header", "Override"));
        sections.add(new ToggleSectionAdapter(Objects.requireNonNull(replaceVanillaToggle, "replaceVanillaToggle")));

        ResourceLocation replaceTarget = core.getVanillaRecipeToReplace();
        if (core.isReplaceVanillaRecipe() && replaceTarget != null) {
            sections.add(new TextNoteSection("replace_info",
                "Will replace: " + replaceTarget.toString()));
        } else if (core.isReplaceVanillaRecipe()) {
            sections.add(new TextNoteSection("replace_info",
                "No vanilla recipe found for this item"));
        }

        sections.add(new SimpleSpacer("spacer3", 16));

        sections.add(new SimpleHeaderSection("valid_header", "Validation"));
        sections.add(new ValidationSection("validation", () -> core.getValidation(item, recipeGrid)));

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER SECTION CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Section that renders the recipe grid.
     */
    public record RecipeGridSection(String id, RecipeGridComponent grid) implements EditorSection.CustomSection {
        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 88; }

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
    public record ResultItemSection(String id, ItemStack item) implements EditorSection.CustomSection {
        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return ""; }

        @Override
        public int getHeight() { return 32; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            graphics.fill(bounds.x() + 4, bounds.y(), bounds.x() + 36, bounds.y() + 32, DesignTokens.Background.INPUT());
            graphics.renderItem(Objects.requireNonNull(item, "item"), bounds.x() + 12, bounds.y() + 8);

            Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
            String name = Objects.requireNonNull(item.getHoverName().getString(), "itemName");
            UIScaleManager.drawScaledString(graphics, font, name, bounds.x() + 44, bounds.y() + 12, DesignTokens.Text.PRIMARY(), false);
        }
    }

    /**
     * Category selector section.
     */
    public static class CategorySection implements EditorSection.ListSection {
        private final String id;
        private RecipeCategory selected;
        private final java.util.function.Consumer<RecipeCategory> onChange;
        private final List<String> options;

        public CategorySection(String id, RecipeCategory initial, java.util.function.Consumer<RecipeCategory> onChange) {
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
            graphics.fill(bounds.x() + 4, bounds.y(), bounds.x() + bounds.width() - 4, bounds.y() + 20,
                DesignTokens.Background.INPUT());
            UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(selected.getId(), "categoryId"), bounds.x() + 8, bounds.y() + 6,
                DesignTokens.Text.PRIMARY(), false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    public record ValidationSection(String id, java.util.function.Supplier<RecipeValidator.ValidationResult> resultSupplier)
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
                UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(result.getSummary(), "summary"), bounds.x() + 8, y, color, false);

                y += 12;
                for (String warning : result.warnings()) {
                    UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull("- " + warning, "warning"),
                        bounds.x() + 12, y, DesignTokens.Text.MUTED(), false);
                    y += 10;
                }
            } else {
                UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(result.getSummary(), "summary"),
                    bounds.x() + 8, y, DesignTokens.Accent.RED(), false);

                y += 12;
                for (String error : result.errors()) {
                    UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull("- " + error, "error"),
                        bounds.x() + 12, y, DesignTokens.Accent.RED(), false);
                    y += 10;
                }
            }
        }
    }
}
