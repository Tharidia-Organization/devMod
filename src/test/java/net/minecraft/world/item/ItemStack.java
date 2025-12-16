package net.minecraft.world.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import java.util.Objects;

/**
 * Minimal test stub for ItemStack to allow JVM unit tests without full Minecraft mappings.
 * This class intentionally contains minimal behavior to support registry/tag-based tests.
 */
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(new Item("test:empty"), "empty", true, 0, 0, 0);
    private final String name;
    private final boolean empty;
    private final Item item;
    private int count;
    private int damage;
    private int maxDamage;
    private final java.util.Map<Object, Object> components = new java.util.HashMap<>();

    public ItemStack() {
        this(new Item("test:default_item"), "Test Item", false, 1, 0, 0);
    }

    public ItemStack(String name) {
        this(new Item("test:default_item"), name, false, 1, 0, 0);
    }

    public ItemStack(Item item, String name, boolean empty) {
        this(item, name, empty, 1, 0, 0);
    }

    public ItemStack(Item item, String name, boolean empty, int count, int damage, int maxDamage) {
        this.item = item;
        this.name = name == null ? "Item" : name;
        this.empty = empty;
        this.count = count;
        this.damage = damage;
        this.maxDamage = maxDamage;
    }

    public ItemStack(Item item) {
        this(item == null ? new Item("test:default_item") : item,
            item == null ? "Item" : java.util.Objects.requireNonNull(item.toString(), "item name cannot be null"),
            false, 1, 0, 0);
    }

    public boolean isEmpty() {
        return empty;
    }

    public ItemStack copy() {
        Item safeItem = Objects.requireNonNullElse(item, new Item("test:default_item"));
        String safeName = Objects.requireNonNullElse(name, "Item");
        return new ItemStack(Objects.requireNonNull(safeItem, "item cannot be null"),
            Objects.requireNonNull(safeName, "name cannot be null"),
            empty, count, damage, maxDamage);
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getDamageValue() {
        return damage;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    // Minimal component API to satisfy tests
    public <T> void set(Object type, T value) {
        components.put(type, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object type) {
        return (T) components.get(type);
    }

    public boolean has(Object type) {
        return components.containsKey(type);
    }

    public boolean is(TagKey<Item> tag) {
        // Very small tag membership simulation: check if item's registry id path equals tag id path
        if (tag == null || item == null) return false;
        ResourceLocation itemId = item.getRegistryName();
        if (itemId == null) return false;
        ResourceLocation tagId = tag.id();
        if (tagId == null) return false;
        return itemId.getPath().equals(tagId.getPath());
    }

    public static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.requireNonNullElse(a.name, "").equals(Objects.requireNonNullElse(b.name, ""));
    }

    public net.minecraft.network.chat.Component getHoverName() {
        String displayName = Objects.requireNonNullElse(name, "Item");
        return net.minecraft.network.chat.Component.literal(Objects.requireNonNull(displayName, "displayName cannot be null"));
    }
}
