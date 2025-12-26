package com.devmod.client.ui.editor.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;

public final class EnchantmentListSection implements EditorSection.CustomSection {

    private static final int HEADER_HEIGHT = EditorDimensions.SECTION_HEADER_HEIGHT;
    private static final int ENTRY_HEIGHT = 44;
    private static final int TEXT_INSET_X = 8;
    private static final int BOTTOM_PADDING = 8;
    private static final int MAX_ENCHANT_LEVEL = 10; // Allow beyond vanilla max for editing

    private final String id;
    private final String title;
    private final ItemStack item;
    private final List<EnchantmentEntry> entries = new ArrayList<>();
    @Nullable
    private final Consumer<String> onModify;

    /**
     * Creates a new enchantment list section.
     *
     * @param id       Section identifier
     * @param title    Section title
     * @param item     Item to read/write enchantments from
     * @param onModify Callback when an enchantment is modified
     */
    public EnchantmentListSection(String id, String title, ItemStack item, @Nullable Consumer<String> onModify) {
        this.id = id;
        this.title = title;
        this.item = item;
        this.onModify = onModify;
        loadEnchantments();
    }

    private void loadEnchantments() {
        entries.clear();

        ItemEnchantments enchants = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ENCHANTMENTS),
            Objects.requireNonNull(ItemEnchantments.EMPTY)
        );

        // Load existing enchantments
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            addEnchantmentEntry(holder, level);
        }

        // Add common enchantments that aren't on the item (level 0 = not applied)
        addCommonEnchantments(enchants);
    }

    private void addCommonEnchantments(ItemEnchantments existing) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        RegistryAccess registries = level.registryAccess();
        var enchantRegistry = registries.registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT));

        // List of common enchantment IDs to show
        List<String> commonIds = List.of(
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "unbreaking", "mending", "efficiency",
            "silk_touch", "fortune", "power", "punch", "flame", "infinity",
            "protection", "fire_protection", "blast_protection", "projectile_protection",
            "feather_falling", "respiration", "aqua_affinity", "thorns", "depth_strider",
            "frost_walker", "soul_speed", "swift_sneak"
        );

        for (String enchantId : commonIds) {
            ResourceLocation loc = Objects.requireNonNull(
                ResourceLocation.withDefaultNamespace(Objects.requireNonNull(enchantId)));
            ResourceKey<Enchantment> key = ResourceKey.create(
                Objects.requireNonNull(Registries.ENCHANTMENT), Objects.requireNonNull(loc));
            Optional<Holder.Reference<Enchantment>> optHolder = enchantRegistry.getHolder(Objects.requireNonNull(key));

            if (optHolder.isPresent()) {
                Holder<Enchantment> holder = optHolder.get();
                // Only add if not already in the list
                boolean alreadyAdded = entries.stream()
                    .anyMatch(e -> e.holder.equals(holder));

                if (!alreadyAdded && existing.getLevel(Objects.requireNonNull(holder)) == 0) {
                    // Check if enchantment is applicable to this item
                    if (canApplyTo(holder, item)) {
                        addEnchantmentEntry(holder, 0);
                    }
                }
            }
        }
    }

    private boolean canApplyTo(Holder<Enchantment> enchantment, ItemStack stack) {
        // Basic check - in real implementation would check enchantment tags
        // For now, allow all common enchantments to be shown
        return true;
    }

    private void addEnchantmentEntry(Holder<Enchantment> holder, int currentLevel) {
        String displayName = getEnchantmentName(holder);
        int maxLevel = getMaxLevel(holder);

        String sliderId = "ench_" + getEnchantmentKey(holder);
        EditorSlider slider = new EditorSlider(sliderId, displayName, 0, MAX_ENCHANT_LEVEL, currentLevel)
            .step(1)
            .format("%.0f")
            .suffix(" lvl")
            .trackColor(getColorForEnchantment(holder))
            .showInput(true)
            .info(getInfoForEnchantment(holder, maxLevel))
            .onChange(value -> {
                int newLevel = Math.round(value);
                updateEnchantment(holder, newLevel);
                if (onModify != null) {
                    onModify.accept(displayName);
                }
            });

        entries.add(new EnchantmentEntry(holder, displayName, slider, currentLevel));
    }

    private String getEnchantmentName(Holder<Enchantment> holder) {
        // Get translation key from registry
        ResourceLocation key = holder.unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);

        if (key != null) {
            // Convert to display name (capitalize, replace underscores)
            String path = key.getPath();
            String[] words = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
                }
            }
            return sb.toString().trim();
        }
        return "Unknown";
    }

    private String getEnchantmentKey(Holder<Enchantment> holder) {
        return holder.unwrapKey()
            .map(k -> k.location().getPath())
            .orElse("unknown");
    }

    private int getMaxLevel(Holder<Enchantment> holder) {
        return holder.value().getMaxLevel();
    }

    private int getColorForEnchantment(Holder<Enchantment> holder) {
        String key = getEnchantmentKey(holder);

        // Damage enchantments
        if (key.contains("sharpness") || key.contains("smite") || key.contains("bane") ||
            key.contains("power") || key.contains("impaling")) {
            return UIConstants.SliderColors.DAMAGE;
        }

        // Protection enchantments
        if (key.contains("protection") || key.contains("feather") || key.contains("respiration")) {
            return UIConstants.SliderColors.DEFENSE;
        }

        // Speed/utility enchantments
        if (key.contains("efficiency") || key.contains("swift") || key.contains("soul_speed") ||
            key.contains("depth_strider") || key.contains("frost_walker")) {
            return UIConstants.SliderColors.SPEED;
        }

        // Durability enchantments
        if (key.contains("unbreaking") || key.contains("mending")) {
            return UIConstants.SliderColors.DURABILITY;
        }

        // Special enchantments
        if (key.contains("silk") || key.contains("fortune") || key.contains("looting") ||
            key.contains("infinity") || key.contains("flame") || key.contains("fire_aspect")) {
            return UIConstants.SliderColors.SPECIAL;
        }

        return UIConstants.SliderColors.NEUTRAL;
    }

    private String getInfoForEnchantment(Holder<Enchantment> holder, int maxLevel) {
        String key = getEnchantmentKey(holder);
        String maxInfo = "Vanilla max: " + maxLevel + ". Editor allows up to " + MAX_ENCHANT_LEVEL + ".";

        if (key.equals("sharpness")) return "Increases melee damage. +1 damage at level 1, +0.5 per level after. " + maxInfo;
        if (key.equals("smite")) return "Extra damage vs undead (zombies, skeletons, phantoms). +2.5 damage per level. " + maxInfo;
        if (key.equals("bane_of_arthropods")) return "Extra damage vs arthropods (spiders, silverfish, bees). +2.5 damage per level. " + maxInfo;
        if (key.equals("knockback")) return "Increases knockback dealt. Each level adds ~3 blocks of knockback. " + maxInfo;
        if (key.equals("fire_aspect")) return "Sets targets on fire. Level 1 = 4 seconds, Level 2 = 8 seconds. " + maxInfo;
        if (key.equals("looting")) return "Increases mob drops. +1 max drops per level, increased rare drop chance. " + maxInfo;
        if (key.equals("unbreaking")) return "Chance to not consume durability. Level 3 = 75% ignore chance for tools. " + maxInfo;
        if (key.equals("mending")) return "XP orbs repair item instead of giving XP. 2 durability per XP point. " + maxInfo;
        if (key.equals("efficiency")) return "Increases mining speed. Each level adds significant speed boost. " + maxInfo;
        if (key.equals("protection")) return "Reduces all damage. Each level = 4% damage reduction. " + maxInfo;
        if (key.equals("power")) return "Increases arrow damage. +25% damage per level (Power V = +150%). " + maxInfo;
        if (key.equals("infinity")) return "Shooting consumes no arrows (requires 1 arrow in inventory). " + maxInfo;

        return "Level 0 = not applied. " + maxInfo;
    }

    private void updateEnchantment(Holder<Enchantment> holder, int newLevel) {
        ItemEnchantments existing = Objects.requireNonNull(
            item.getOrDefault(
                Objects.requireNonNull(DataComponents.ENCHANTMENTS),
                Objects.requireNonNull(ItemEnchantments.EMPTY)
            )
        );
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);

        if (newLevel <= 0) {
            // Remove enchantment
            mutable.removeIf(h -> h.equals(holder));
        } else {
            // Set enchantment level
            mutable.set(Objects.requireNonNull(holder), newLevel);
        }

        item.set(
            Objects.requireNonNull(DataComponents.ENCHANTMENTS),
            mutable.toImmutable()
        );
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return title;
    }

    @Override
    public int getHeight() {
        return HEADER_HEIGHT + entries.size() * ENTRY_HEIGHT + BOTTOM_PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        int y = bounds.y();

        // Header
        graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + HEADER_HEIGHT,
            UIConstants.Background.HEADER());
        graphics.drawString(font, title, bounds.x() + TEXT_INSET_X,
            y + (HEADER_HEIGHT - 8) / 2, UIConstants.Text.TITLE(), false);

        // Count active enchantments
        long activeCount = entries.stream().filter(e -> e.currentLevel > 0).count();
        String countText = "(" + activeCount + " active)";
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, bounds.x() + bounds.width() - countWidth - TEXT_INSET_X,
            y + (HEADER_HEIGHT - 8) / 2, UIConstants.Text.MUTED(), false);

        y += HEADER_HEIGHT;

        // Render each enchantment slider
        for (EnchantmentEntry entry : entries) {
            entry.slider.render(graphics, bounds.x() + 4, y + 4, bounds.width() - 8, mouseX, mouseY);
            y += ENTRY_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EnchantmentEntry entry : entries) {
            if (entry.slider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EnchantmentEntry entry : entries) {
            if (entry.slider.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (EnchantmentEntry entry : entries) {
            if (entry.slider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EnchantmentEntry entry : entries) {
            if (entry.slider.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (EnchantmentEntry entry : entries) {
            if (entry.slider.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Internal entry representing one enchantment.
     */
    private static class EnchantmentEntry {
        final Holder<Enchantment> holder;
        final String displayName;
        final EditorSlider slider;
        int currentLevel;

        EnchantmentEntry(Holder<Enchantment> holder, String displayName, EditorSlider slider, int currentLevel) {
            this.holder = holder;
            this.displayName = displayName;
            this.slider = slider;
            this.currentLevel = currentLevel;
        }

        @Override
        public String toString() {
            return displayName + " (level " + currentLevel + ")";
        }
    }
}
