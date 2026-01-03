package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CustomKit {
    private final String id;
    private String name;
    private String description;
    private int color;
    private final List<KitItem> items;
    private long createdAt;
    private long lastModified;

    /**
     * Creates a new empty custom kit.
     */
    public CustomKit(String name) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = Objects.requireNonNull(name);
        this.description = "";
        this.color = EnduranceColors.Kit.CUSTOM;
        this.items = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }

    /**
     * Creates a custom kit from serialized data.
     */
    public CustomKit(String id, String name, String description, int color,
                     List<KitItem> items, long createdAt, long lastModified) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description != null ? description : "";
        this.color = color;
        this.items = new ArrayList<>(items);
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    /**
     * Creates a custom kit from a preset (for copying/editing).
     */
    public static CustomKit fromPreset(KitPreset preset) {
        CustomKit kit = new CustomKit(preset.getDisplayName() + " (Custom)");
        kit.description = preset.getDescription();
        kit.color = preset.getColor();

        for (ItemStack stack : preset.getPreviewItems()) {
            if (!stack.isEmpty()) {
                kit.addItem(stack);
            }
        }

        return kit;
    }

    /**
     * Creates a custom kit from a player's current inventory.
     */
    public static CustomKit fromInventory(net.minecraft.world.entity.player.Inventory inventory, String name) {
        return fromInventory(inventory, name, null);
    }

    public static CustomKit fromInventory(net.minecraft.world.entity.player.Inventory inventory, String name,
                                          @javax.annotation.Nullable RegistryAccess registryAccess) {
        CustomKit kit = new CustomKit(name);
        kit.description = "Saved from player inventory";

        // Add armor
        for (int i = 0; i < 4; i++) {
            ItemStack armor = inventory.getArmor(i);
            if (!armor.isEmpty()) {
                kit.addItem(armor.copy(), registryAccess);
            }
        }

        // Add hotbar and main inventory
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (!item.isEmpty()) {
                kit.addItem(item.copy(), registryAccess);
            }
        }

        // Add offhand
        ItemStack offhand = inventory.offhand.get(0);
        if (!offhand.isEmpty()) {
            kit.addItem(offhand.copy(), registryAccess);
        }

        return kit;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
        this.lastModified = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
        this.lastModified = System.currentTimeMillis();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        this.lastModified = System.currentTimeMillis();
    }

    public List<KitItem> getKitItems() {
        return new ArrayList<>(items);
    }

    public void addItem(ItemStack stack) {
        addItem(stack, null);
    }

    public void addItem(ItemStack stack, @javax.annotation.Nullable RegistryAccess registryAccess) {
        if (stack != null && !stack.isEmpty()) {
            items.add(KitItem.fromItemStack(stack, registryAccess));
            this.lastModified = System.currentTimeMillis();
        }
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            this.lastModified = System.currentTimeMillis();
        }
    }

    public void clearItems() {
        items.clear();
        this.lastModified = System.currentTimeMillis();
    }

    public int getItemCount() {
        return items.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * Convert kit items back to ItemStacks for use.
     * This basic version only restores item type and count.
     * For full restoration including attributes and NBT, use {@link #toItemStacks(net.minecraft.core.RegistryAccess)}.
     */
    public List<ItemStack> toItemStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (KitItem item : items) {
            ItemStack stack = item.toItemStack();
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /**
     * Convert kit items back to ItemStacks with full data restoration.
     * This restores all serialized data including attributes, durability, NBT, enchantments, etc.
     * @param registryAccess Registry access for deserialization
     * @return List of fully restored ItemStacks
     */
    public List<ItemStack> toItemStacks(net.minecraft.core.RegistryAccess registryAccess) {
        if (registryAccess == null) {
            return toItemStacks(); // Fallback to basic version
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (KitItem item : items) {
            ItemStack stack = item.toItemStackWithEnchantments(registryAccess);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /**
     * Serializable item reference for persistence.
     * Now supports enchantments and potion effects.
     */
    public record KitItem(
        String itemId,
        int count,
        String nbtTag, // Optional NBT as string for complex items
        java.util.List<EnchantmentData> enchantments,
        String potionId // For potion items
    ) {
        // Constructor for backwards compatibility
        public KitItem(String itemId, int count, String nbtTag) {
            this(itemId, count, nbtTag, new java.util.ArrayList<>(), null);
        }

        /**
         * Creates a KitItem from an ItemStack without full serialization.
         * Use {@link #fromItemStack(ItemStack, RegistryAccess)} for full serialization with NBT.
         */
        public static KitItem fromItemStack(ItemStack stack) {
            return fromItemStack(stack, null);
        }

        /**
         * Creates a KitItem from an ItemStack with optional full serialization.
         *
         * @param stack The item stack to convert
         * @param registryAccess Registry access for full NBT serialization (can be null for partial serialization)
         */
        public static KitItem fromItemStack(ItemStack stack, @javax.annotation.Nullable net.minecraft.core.RegistryAccess registryAccess) {
            if (stack == null || stack.isEmpty()) {
                return new KitItem("minecraft:air", 0, null, new java.util.ArrayList<>(), null);
            }

            net.minecraft.world.item.Item itemObj = Objects.requireNonNull(stack.getItem());
            ResourceLocation itemKey = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(itemObj));
            String id = Objects.requireNonNull(itemKey.toString());

            // Serialize full ItemStack as SNBT to preserve all components (attributes, durability, custom data, etc.)
            String serializedNbt = null;
            try {
                // Use provided registry access for serialization
                if (registryAccess != null) {
                    net.minecraft.nbt.Tag saved = stack.save(registryAccess);
                    if (saved != null) {
                        serializedNbt = saved.toString();
                    }
                }
            } catch (Exception e) {
                // Fall back to partial serialization if full serialization fails
            }

            // Extract enchantments (kept for backwards compatibility and quick access)
            java.util.List<EnchantmentData> enchants = new java.util.ArrayList<>();
            var enchantsComponent = Objects.requireNonNull(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            net.minecraft.world.item.enchantment.ItemEnchantments itemEnchants =
                stack.getOrDefault(enchantsComponent,
                    Objects.requireNonNull(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));

            for (var entry : itemEnchants.entrySet()) {
                var enchantHolder = entry.getKey();
                int level = entry.getIntValue();
                enchantHolder.unwrapKey().ifPresent(key ->
                    enchants.add(new EnchantmentData(Objects.requireNonNull(key.location().toString()), level))
                );
            }

            // Extract potion ID if applicable using AtomicReference
            final java.util.concurrent.atomic.AtomicReference<String> potionIdHolder =
                new java.util.concurrent.atomic.AtomicReference<>(null);

            if (stack.getItem() instanceof net.minecraft.world.item.PotionItem ||
                stack.getItem() == net.minecraft.world.item.Items.SPLASH_POTION ||
                stack.getItem() == net.minecraft.world.item.Items.LINGERING_POTION ||
                stack.getItem() == net.minecraft.world.item.Items.TIPPED_ARROW) {

                var potionComponent = Objects.requireNonNull(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                var potionContents = stack.get(potionComponent);
                if (potionContents != null) {
                    potionContents.potion()
                        .flatMap(p -> p.unwrapKey())
                        .ifPresent(key -> potionIdHolder.set(Objects.requireNonNull(key.location().toString())));
                }
            }

            return new KitItem(id, stack.getCount(), serializedNbt, enchants, potionIdHolder.get());
        }

        public ItemStack toItemStack() {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(Objects.requireNonNull(itemId));
                if (loc == null) {
                    return ItemStack.EMPTY;
                }

                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(loc);
                if (item == net.minecraft.world.item.Items.AIR) {
                    return ItemStack.EMPTY;
                }

                return new ItemStack(Objects.requireNonNull(item), Math.max(1, count));
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        /**
         * Reconstruct the full ItemStack using registry access.
         * If SNBT data is available, deserializes the complete item with all components.
         * Otherwise falls back to basic item + enchantments + potions.
         */
        public ItemStack toItemStackWithEnchantments(net.minecraft.core.RegistryAccess registryAccess) {
            // Try to deserialize from full SNBT first (preserves attributes, durability, custom data, etc.)
            if (nbtTag != null && !nbtTag.isEmpty() && registryAccess != null) {
                try {
                    net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(Objects.requireNonNull(nbtTag));
                    java.util.Optional<ItemStack> parsed = ItemStack.parse(Objects.requireNonNull(registryAccess), Objects.requireNonNull(tag));
                    if (parsed.isPresent() && !parsed.get().isEmpty()) {
                        return parsed.get();
                    }
                } catch (Exception e) {
                    // Fall back to legacy deserialization if SNBT parsing fails
                }
            }

            // Legacy fallback: basic item + enchantments + potions
            ItemStack stack = toItemStack();
            if (stack.isEmpty()) {
                return stack;
            }

            // Apply enchantments
            if (enchantments != null && !enchantments.isEmpty() && registryAccess != null) {
                try {
                    var enchantRegKey = Objects.requireNonNull(net.minecraft.core.registries.Registries.ENCHANTMENT);
                    var enchantmentRegistry = registryAccess.registryOrThrow(enchantRegKey);

                    for (EnchantmentData data : enchantments) {
                        String enchantId = Objects.requireNonNull(data.enchantmentId());
                        ResourceLocation enchantLoc = ResourceLocation.tryParse(enchantId);
                        if (enchantLoc != null) {
                            var enchantKey = Objects.requireNonNull(net.minecraft.resources.ResourceKey.create(
                                enchantRegKey, enchantLoc));
                            enchantmentRegistry.getHolder(enchantKey).ifPresent(holder ->
                                stack.enchant(Objects.requireNonNull(holder), data.level())
                            );
                        }
                    }
                } catch (Exception e) {
                    // Log but don't fail
                }
            }

            // Apply potion if applicable
            if (potionId != null && !potionId.isEmpty() && registryAccess != null) {
                try {
                    ResourceLocation potionLoc = ResourceLocation.tryParse(Objects.requireNonNull(potionId));
                    if (potionLoc != null) {
                        var potionRegKey = Objects.requireNonNull(net.minecraft.core.registries.Registries.POTION);
                        var potionRegistry = registryAccess.registryOrThrow(potionRegKey);
                        var potionKey = Objects.requireNonNull(net.minecraft.resources.ResourceKey.create(
                            potionRegKey, potionLoc));
                        potionRegistry.getHolder(potionKey).ifPresent(holder -> {
                            var contents = new net.minecraft.world.item.alchemy.PotionContents(Objects.requireNonNull(holder));
                            var potionComponent = Objects.requireNonNull(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                            stack.set(potionComponent, contents);
                        });
                    }
                } catch (Exception e) {
                    // Log but don't fail
                }
            }

            return stack;
        }
    }

    /**
     * Enchantment data for serialization.
     */
    public record EnchantmentData(String enchantmentId, int level) {}
}
