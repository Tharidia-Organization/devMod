package com.frenkvs.devmod.ammo;

import com.frenkvs.devmod.RangedComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Custom ammo filtering system for ranged weapons.
 * Provides utility methods for checking valid ammo and retrieving matching items.
 *
 * @see docs/editor-design-system/16-ranged-weapons.md
 */
public final class AmmoSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmmoSystem.class);

    private AmmoSystem() {}

    /**
     * Check if an item is valid ammo for a ranged weapon.
     *
     * @param weapon The ranged weapon to check against
     * @param ammo The potential ammo item
     * @return true if the ammo is valid for this weapon
     */
    public static boolean isValidAmmo(ItemStack weapon, ItemStack ammo) {
        if (weapon.isEmpty() || ammo.isEmpty()) {
            return false;
        }

        // Check for custom ammo filter from DevMod component
        ResourceLocation customFilter = getAmmoFilter(weapon);
        if (customFilter != null) {
            TagKey<Item> ammoTag = TagKey.create(
                Objects.requireNonNull(Registries.ITEM),
                Objects.requireNonNull(customFilter)
            );
            return ammo.is(Objects.requireNonNull(ammoTag));
        }

        // Fallback to vanilla behavior
        Item weaponItem = weapon.getItem();
        if (weaponItem instanceof BowItem) {
            return ammo.is(Objects.requireNonNull(ItemTags.ARROWS));
        } else if (weaponItem instanceof CrossbowItem) {
            return ammo.is(Objects.requireNonNull(ItemTags.ARROWS))
                || ammo.is(Objects.requireNonNull(Items.FIREWORK_ROCKET));
        }

        return false;
    }

    /**
     * Get the custom ammo filter from a weapon, if present.
     *
     * @param weapon The weapon to check
     * @return The ammo tag filter ResourceLocation, or null if not set
     */
    public static ResourceLocation getAmmoFilter(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return null;
        }

        try {
            if (RangedComponents.AMMO_TAG_FILTER.isBound()) {
                return weapon.get(Objects.requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()));
            }
        } catch (Exception e) {
            LOGGER.debug("[AmmoSystem] Failed to get ammo filter: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Set a custom ammo filter on a weapon.
     *
     * @param weapon The weapon to modify
     * @param tagId The tag ResourceLocation (e.g., "minecraft:arrows")
     */
    public static void setAmmoFilter(ItemStack weapon, ResourceLocation tagId) {
        if (weapon.isEmpty()) {
            return;
        }

        try {
            if (RangedComponents.AMMO_TAG_FILTER.isBound()) {
                if (tagId != null) {
                    weapon.set(Objects.requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()), tagId);
                } else {
                    weapon.remove(Objects.requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[AmmoSystem] Failed to set ammo filter: {}", e.getMessage());
        }
    }

    /**
     * Clear the custom ammo filter from a weapon.
     *
     * @param weapon The weapon to modify
     */
    public static void clearAmmoFilter(ItemStack weapon) {
        setAmmoFilter(weapon, null);
    }

    /**
     * Get all items that match the weapon's ammo filter.
     *
     * @param weapon The weapon to check
     * @return List of matching ItemStacks (sample stacks, count 1)
     */
    public static List<ItemStack> getMatchingAmmo(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return List.of();
        }

        ResourceLocation customFilter = getAmmoFilter(weapon);
        if (customFilter != null) {
            return getItemsFromTag(customFilter);
        }

        // Fallback to vanilla ammo
        return getVanillaAmmo(weapon);
    }

    /**
     * Get all items from a tag by ResourceLocation.
     *
     * @param tagId The tag ResourceLocation
     * @return List of ItemStacks matching the tag
     */
    public static List<ItemStack> getItemsFromTag(ResourceLocation tagId) {
        if (tagId == null) {
            return List.of();
        }

        try {
            TagKey<Item> tag = TagKey.create(
                Objects.requireNonNull(Registries.ITEM),
                Objects.requireNonNull(tagId)
            );

            var tagIterable = BuiltInRegistries.ITEM.getTagOrEmpty(Objects.requireNonNull(tag));
            return StreamSupport.stream(tagIterable.spliterator(), false)
                .map(holder -> new ItemStack(Objects.requireNonNull(holder.value())))
                .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.debug("[AmmoSystem] Failed to get items from tag {}: {}", tagId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all items from a tag by string (e.g., "#minecraft:arrows" or "minecraft:arrows").
     *
     * @param tagString The tag string (with or without # prefix)
     * @return List of ItemStacks matching the tag
     */
    public static List<ItemStack> getItemsFromTagString(String tagString) {
        if (tagString == null || tagString.isBlank()) {
            return List.of();
        }

        // Remove # prefix if present
        String cleanTag = tagString.startsWith("#") ? tagString.substring(1) : tagString;
        ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(cleanTag));

        if (tagId == null) {
            LOGGER.debug("[AmmoSystem] Invalid tag string: {}", tagString);
            return List.of();
        }

        return getItemsFromTag(tagId);
    }

    /**
     * Get the default vanilla ammo for a weapon type.
     *
     * @param weapon The weapon to check
     * @return List of vanilla ammo ItemStacks
     */
    public static List<ItemStack> getVanillaAmmo(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return List.of();
        }

        Item weaponItem = weapon.getItem();

        if (weaponItem instanceof BowItem) {
            return List.of(
                new ItemStack(Objects.requireNonNull(Items.ARROW)),
                new ItemStack(Objects.requireNonNull(Items.SPECTRAL_ARROW)),
                new ItemStack(Objects.requireNonNull(Items.TIPPED_ARROW))
            );
        } else if (weaponItem instanceof CrossbowItem) {
            return List.of(
                new ItemStack(Objects.requireNonNull(Items.ARROW)),
                new ItemStack(Objects.requireNonNull(Items.SPECTRAL_ARROW)),
                new ItemStack(Objects.requireNonNull(Items.TIPPED_ARROW)),
                new ItemStack(Objects.requireNonNull(Items.FIREWORK_ROCKET))
            );
        }

        return List.of();
    }

    /**
     * Get a display-friendly list of ammo names for UI display.
     * Limits the list to maxItems for performance.
     *
     * @param weapon The weapon to check
     * @param maxItems Maximum number of items to return
     * @return List of display names
     */
    public static List<String> getMatchingAmmoNames(ItemStack weapon, int maxItems) {
        List<ItemStack> matching = getMatchingAmmo(weapon);
        List<String> names = new ArrayList<>();

        int count = 0;
        for (ItemStack stack : matching) {
            if (count >= maxItems) {
                break;
            }
            try {
                String name = stack.getHoverName().getString();
                names.add(name);
            } catch (Exception e) {
                names.add(stack.getItem().getDescriptionId());
            }
            count++;
        }

        return names;
    }

    /**
     * Count total matching ammo items for a weapon.
     *
     * @param weapon The weapon to check
     * @return Number of matching ammo item types
     */
    public static int countMatchingAmmo(ItemStack weapon) {
        return getMatchingAmmo(weapon).size();
    }

    /**
     * Check if a weapon has a custom ammo filter set.
     *
     * @param weapon The weapon to check
     * @return true if a custom filter is set
     */
    public static boolean hasCustomAmmoFilter(ItemStack weapon) {
        return getAmmoFilter(weapon) != null;
    }

    /**
     * Validate that a tag string is a valid ResourceLocation and exists in the registry.
     *
     * @param tagString The tag string to validate
     * @return true if valid and exists
     */
    public static boolean isValidTagString(String tagString) {
        if (tagString == null || tagString.isBlank()) {
            return false;
        }

        String cleanTag = tagString.startsWith("#") ? tagString.substring(1) : tagString;
        ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(cleanTag));

        if (tagId == null) {
            return false;
        }

        // Check if tag exists (has any items)
        try {
            TagKey<Item> tag = TagKey.create(
                Objects.requireNonNull(Registries.ITEM),
                Objects.requireNonNull(tagId)
            );
            return BuiltInRegistries.ITEM.getTagOrEmpty(Objects.requireNonNull(tag)).iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get suggested ammo tags for autocomplete.
     *
     * @return List of common ammo tag suggestions
     */
    public static List<AmmoSuggestion> getSuggestedTags() {
        return List.of(
            new AmmoSuggestion("#minecraft:arrows", "All Arrows"),
            new AmmoSuggestion("minecraft:arrow", "Arrow"),
            new AmmoSuggestion("minecraft:spectral_arrow", "Spectral Arrow"),
            new AmmoSuggestion("minecraft:tipped_arrow", "Tipped Arrow"),
            new AmmoSuggestion("minecraft:firework_rocket", "Firework Rocket"),
            new AmmoSuggestion("minecraft:trident", "Trident")
        );
    }

    /**
     * Suggestion record for ammo autocomplete.
     */
    public record AmmoSuggestion(String value, String displayName) {
        /**
         * Check if this is a tag (starts with #).
         */
        public boolean isTag() {
            return value != null && value.startsWith("#");
        }

        /**
         * Get the ResourceLocation for this suggestion.
         */
        public ResourceLocation toResourceLocation() {
            if (value == null || value.isBlank()) {
                return null;
            }
            String clean = isTag() ? value.substring(1) : value;
            return ResourceLocation.tryParse(Objects.requireNonNull(clean));
        }
    }
}
