package com.devmod.ammo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import com.devmod.components.RangedComponents;

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

        String customFilter = getAmmoFilterString(weapon);
        if (customFilter != null && !customFilter.isBlank()) {
            return matchesFilter(ammo, customFilter);
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
    @Nullable
    public static ResourceLocation getAmmoFilter(ItemStack weapon) {
        String filter = getAmmoFilterString(weapon);
        if (filter == null || filter.isBlank()) return null;
        String cleanFilter = filter.startsWith("#") ? filter.substring(1) : filter;
        return ResourceLocation.tryParse(Objects.requireNonNull(cleanFilter));
    }

    /**
     * Set a custom ammo filter on a weapon.
     *
     * @param weapon The weapon to modify
     * @param tagId The tag ResourceLocation (e.g., "minecraft:arrows")
     */
    public static void setAmmoFilter(ItemStack weapon, @Nullable ResourceLocation tagId) {
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

        String customFilter = getAmmoFilterString(weapon);
        if (customFilter != null && !customFilter.isBlank()) {
            return getItemsFromTagString(customFilter);
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
     * Get items from a filter string (e.g., "#minecraft:arrows" or "minecraft:arrow").
     *
     * @param tagString The tag or item string (with or without # prefix)
     * @return List of ItemStacks matching the filter
     */
    public static List<ItemStack> getItemsFromTagString(String tagString) {
        if (tagString == null || tagString.isBlank()) {
            return List.of();
        }

        String trimmed = tagString.trim();
        boolean isTag = trimmed.startsWith("#");
        String cleanTag = isTag ? trimmed.substring(1) : trimmed;
        ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(cleanTag));

        if (tagId == null) {
            LOGGER.debug("[AmmoSystem] Invalid ammo filter string: {}", tagString);
            return List.of();
        }

        if (isTag) {
            return getItemsFromTag(tagId);
        }

        if (BuiltInRegistries.ITEM.containsKey(tagId)) {
            return List.of(new ItemStack(Objects.requireNonNull(BuiltInRegistries.ITEM.get(tagId))));
        }

        return List.of();
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
        String filter = getAmmoFilterString(weapon);
        return filter != null && !filter.isBlank();
    }

    @Nullable
    private static String getAmmoFilterString(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return null;
        }

        String nbtFilter = readAmmoFilterFromNbt(weapon);
        ResourceLocation componentFilter = readAmmoFilterFromComponent(weapon);
        if (componentFilter != null) {
            if (nbtFilter != null && !nbtFilter.isBlank()) {
                return nbtFilter.trim();
            }
            return formatAmmoFilter(componentFilter);
        }

        return nbtFilter == null ? null : nbtFilter.trim();
    }

    @Nullable
    private static ResourceLocation readAmmoFilterFromComponent(ItemStack weapon) {
        try {
            if (RangedComponents.AMMO_TAG_FILTER.isBound()) {
                return weapon.get(Objects.requireNonNull(RangedComponents.AMMO_TAG_FILTER.get()));
            }
        } catch (Exception e) {
            LOGGER.debug("[AmmoSystem] Failed to get ammo filter: {}", e.getMessage());
        }
        return null;
    }

    @Nullable
    private static String readAmmoFilterFromNbt(ItemStack weapon) {
        try {
            CustomData custom = weapon.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY));
            CompoundTag tag = custom.copyTag();
            if (tag == null || !tag.contains("RangedStats")) {
                return null;
            }
            CompoundTag ranged = tag.getCompound("RangedStats");
            if (!ranged.contains("ammoFilter")) {
                return null;
            }
            return ranged.getString("ammoFilter");
        } catch (Exception e) {
            LOGGER.debug("[AmmoSystem] Failed to read ammo filter from NBT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a given ammo stack matches a filter string.
     *
     * @param ammo The ammo item stack
     * @param filter The filter string (item id or #tag)
     * @return true if the ammo matches the filter
     */
    public static boolean matchesFilter(ItemStack ammo, @Nullable String filter) {
        if (ammo == null || ammo.isEmpty()) return false;
        if (filter == null) return false;
        String trimmed = filter.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.startsWith("#")) {
            return matchesTag(ammo, trimmed.substring(1));
        }
        return matchesItemId(ammo, trimmed);
    }

    private static boolean matchesTag(ItemStack ammo, String tagIdStr) {
        ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(tagIdStr));
        if (tagId == null) return false;
        TagKey<Item> ammoTag = TagKey.create(
            Objects.requireNonNull(Registries.ITEM),
            Objects.requireNonNull(tagId)
        );
        return ammo.is(Objects.requireNonNull(ammoTag));
    }

    private static boolean matchesItemId(ItemStack ammo, String filter) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(ammo.getItem()));
        String idStr = id.toString().toLowerCase(Locale.ROOT);
        String normalized = filter.trim().toLowerCase(Locale.ROOT);
        return idStr.equals(normalized) || idStr.endsWith(normalized);
    }

    private static boolean isTagDefined(ResourceLocation tagId) {
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
     * Format a ResourceLocation as an ammo filter string, tagging if possible.
     *
     * @param filterId The item or tag id
     * @return Filter string (#tag or item id)
     */
    public static String formatAmmoFilter(ResourceLocation filterId) {
        Objects.requireNonNull(filterId, "filterId");
        return isTagDefined(filterId) ? "#" + filterId : filterId.toString();
    }

    public enum FilterState {
        BLANK,
        INVALID_FORMAT,
        MISSING_ITEM,
        EMPTY_TAG,
        VALID_ITEM,
        VALID_TAG
    }

    /**
     * Resolve a filter string into a typed state for UI/validation.
     *
     * @param filterString The filter string (item id or #tag)
     * @return The resolved filter state
     */
    public static FilterState resolveFilterState(@Nullable String filterString) {
        if (filterString == null || filterString.isBlank()) {
            return FilterState.BLANK;
        }

        String trimmed = filterString.trim();
        boolean isTag = trimmed.startsWith("#");
        String clean = isTag ? trimmed.substring(1) : trimmed;
        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(clean));

        if (id == null) {
            return FilterState.INVALID_FORMAT;
        }

        if (isTag) {
            return isTagDefined(id) ? FilterState.VALID_TAG : FilterState.EMPTY_TAG;
        }

        return BuiltInRegistries.ITEM.containsKey(id)
            ? FilterState.VALID_ITEM
            : FilterState.MISSING_ITEM;
    }

    /**
     * Validate that a filter string is a valid ResourceLocation and exists.
     *
     * @param tagString The tag or item string to validate
     * @return true if valid and exists
     */
    public static boolean isValidTagString(String tagString) {
        return isValidFilterString(tagString);
    }

    /**
     * Validate that a filter string resolves to a tag or item in the registry.
     *
     * @param filterString The filter string to validate
     * @return true if it resolves to a known tag or item
     */
    public static boolean isValidFilterString(String filterString) {
        FilterState state = resolveFilterState(filterString);
        return state == FilterState.VALID_ITEM || state == FilterState.VALID_TAG;
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
        @Nullable
        public ResourceLocation toResourceLocation() {
            if (value == null || value.isBlank()) {
                return null;
            }
            String clean = isTag() ? value.substring(1) : value;
            return ResourceLocation.tryParse(Objects.requireNonNull(clean));
        }
    }
}
