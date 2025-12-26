package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Predefined kit configurations for Endurance Quest testing and gameplay.
 * Each kit provides a balanced loadout for different playstyles.
 */
public enum KitPreset {
    STARTER("Starter Kit", "Basic iron gear for beginners", 0xAAAAAA),
    WARRIOR("Warrior Kit", "Full diamond melee build", 0x55FFFF),
    RANGER("Ranger Kit", "Bow focus with speed", 0x55FF55),
    TANK("Tank Kit", "Heavy armor, shield focus", 0x5555FF),
    MAGE("Mage Kit", "Light armor, potions", 0xAA00AA),
    BERSERKER("Berserker Kit", "High damage, low defense", 0xFF5555),
    CUSTOM("Current Inventory", "Use your current inventory", 0xFFAA00);

    private final String displayName;
    private final String description;
    private final int color;

    KitPreset(String displayName, String description, int color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getColor() {
        return color;
    }

    /**
     * Get the items for this kit preset.
     * @param level The server level for enchantment lookup (null for client-side preview)
     * @return List of ItemStacks to give to the player
     */
    public List<ItemStack> getItems(@javax.annotation.Nullable net.minecraft.world.level.Level level) {
        List<ItemStack> items = new ArrayList<>();

        switch (this) {
            case STARTER -> {
                // Iron armor
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_HELMET)));
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE)));
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_LEGGINGS)));
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_BOOTS)));
                // Iron sword
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_SWORD)));
                // Shield
                items.add(new ItemStack(Objects.requireNonNull(Items.SHIELD)));
                // Bow and arrows
                items.add(new ItemStack(Objects.requireNonNull(Items.BOW)));
                items.add(new ItemStack(Objects.requireNonNull(Items.ARROW), 64));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case WARRIOR -> {
                // Diamond armor
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_HELMET), level, Enchantments.PROTECTION, 3));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_CHESTPLATE), level, Enchantments.PROTECTION, 3));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_LEGGINGS), level, Enchantments.PROTECTION, 3));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_BOOTS), level, Enchantments.PROTECTION, 3));
                // Diamond sword with sharpness
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_SWORD), level, Enchantments.SHARPNESS, 4));
                // Shield
                items.add(new ItemStack(Objects.requireNonNull(Items.SHIELD)));
                // Golden apples
                items.add(new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 8));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case RANGER -> {
                // Leather/chain armor (light)
                items.add(createEnchantedItem(Objects.requireNonNull(Items.LEATHER_HELMET), level, Enchantments.PROTECTION, 2));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.CHAINMAIL_CHESTPLATE), level, Enchantments.PROTECTION, 2));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.LEATHER_LEGGINGS), level, Enchantments.PROTECTION, 2));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.LEATHER_BOOTS), level, Enchantments.SWIFT_SNEAK, 2));
                // Iron sword backup
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_SWORD)));
                // Power bow
                items.add(createEnchantedItem(Objects.requireNonNull(Items.BOW), level, Enchantments.POWER, 4));
                // Lots of arrows
                items.add(new ItemStack(Objects.requireNonNull(Items.ARROW), 128));
                items.add(new ItemStack(Objects.requireNonNull(Items.SPECTRAL_ARROW), 32));
                // Speed potions
                items.add(createPotion(Potions.SWIFTNESS));
                items.add(createPotion(Potions.SWIFTNESS));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case TANK -> {
                // Netherite armor (heavy)
                items.add(createEnchantedItem(Objects.requireNonNull(Items.NETHERITE_HELMET), level, Enchantments.PROTECTION, 4));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.NETHERITE_CHESTPLATE), level, Enchantments.PROTECTION, 4));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.NETHERITE_LEGGINGS), level, Enchantments.PROTECTION, 4));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.NETHERITE_BOOTS), level, Enchantments.PROTECTION, 4));
                // Iron sword (focus on defense)
                items.add(new ItemStack(Objects.requireNonNull(Items.IRON_SWORD)));
                // Shield with durability
                items.add(createEnchantedItem(Objects.requireNonNull(Items.SHIELD), level, Enchantments.UNBREAKING, 3));
                // Totem of undying
                items.add(new ItemStack(Objects.requireNonNull(Items.TOTEM_OF_UNDYING)));
                // Healing potions
                items.add(createPotion(Potions.STRONG_HEALING));
                items.add(createPotion(Potions.STRONG_HEALING));
                items.add(createPotion(Potions.STRONG_HEALING));
                // Golden apples
                items.add(new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 16));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case MAGE -> {
                // Light armor
                items.add(new ItemStack(Objects.requireNonNull(Items.LEATHER_HELMET)));
                items.add(new ItemStack(Objects.requireNonNull(Items.LEATHER_CHESTPLATE)));
                items.add(new ItemStack(Objects.requireNonNull(Items.LEATHER_LEGGINGS)));
                items.add(new ItemStack(Objects.requireNonNull(Items.LEATHER_BOOTS)));
                // Trident with loyalty
                items.add(createEnchantedItem(Objects.requireNonNull(Items.TRIDENT), level, Enchantments.LOYALTY, 3));
                // Various potions
                items.add(createPotion(Potions.STRONG_HEALING));
                items.add(createPotion(Potions.STRONG_HEALING));
                items.add(createPotion(Potions.STRONG_HEALING));
                items.add(createPotion(Potions.STRONG_REGENERATION));
                items.add(createPotion(Potions.STRONG_REGENERATION));
                items.add(createPotion(Potions.STRENGTH));
                items.add(createPotion(Potions.STRENGTH));
                items.add(createPotion(Potions.SWIFTNESS));
                items.add(createPotion(Potions.INVISIBILITY));
                // Ender pearls
                items.add(new ItemStack(Objects.requireNonNull(Items.ENDER_PEARL), 16));
                // Golden apples
                items.add(new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 8));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case BERSERKER -> {
                // No helmet for style
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_CHESTPLATE), level, Enchantments.PROTECTION, 2));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_LEGGINGS), level, Enchantments.PROTECTION, 2));
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_BOOTS), level, Enchantments.PROTECTION, 2));
                // Netherite axe with high damage
                items.add(createEnchantedItem(Objects.requireNonNull(Items.NETHERITE_AXE), level, Enchantments.SHARPNESS, 5));
                // Secondary diamond sword
                items.add(createEnchantedItem(Objects.requireNonNull(Items.DIAMOND_SWORD), level, Enchantments.SHARPNESS, 4));
                // Strength potions
                items.add(createPotion(Potions.STRONG_STRENGTH));
                items.add(createPotion(Potions.STRONG_STRENGTH));
                items.add(createPotion(Potions.STRONG_STRENGTH));
                // Speed for closing distance
                items.add(createPotion(Potions.STRONG_SWIFTNESS));
                // Golden apples
                items.add(new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 16));
                // Food
                items.add(new ItemStack(Objects.requireNonNull(Items.COOKED_BEEF), 32));
            }
            case CUSTOM -> {
                // No items - uses current inventory
            }
        }

        return items;
    }

    /**
     * Create an enchanted item stack.
     */
    private ItemStack createEnchantedItem(net.minecraft.world.item.Item item,
                                          @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                          net.minecraft.resources.ResourceKey<Enchantment> enchantmentKey,
                                          int enchantLevel) {
        ItemStack stack = new ItemStack(Objects.requireNonNull(item));
        if (level != null) {
            net.minecraft.core.RegistryAccess registryAccess = Objects.requireNonNull(level.registryAccess());
            net.minecraft.core.Registry<Enchantment> enchantmentRegistry = registryAccess.registryOrThrow(
                Objects.requireNonNull(Registries.ENCHANTMENT));
            Optional<Holder.Reference<Enchantment>> enchantmentHolder = enchantmentRegistry.getHolder(Objects.requireNonNull(enchantmentKey));
            enchantmentHolder.ifPresent(holder -> {
                Holder<Enchantment> enchantHolder = Objects.requireNonNull(holder);
                stack.enchant(enchantHolder, enchantLevel);
            });
        }
        return stack;
    }

    /**
     * Create a potion item stack.
     */
    private ItemStack createPotion(Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Objects.requireNonNull(Items.POTION));
        stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.POTION_CONTENTS),
                  new PotionContents(Objects.requireNonNull(potion)));
        return stack;
    }

    /**
     * Get display items for UI preview (without enchantments applied).
     */
    public List<ItemStack> getPreviewItems() {
        return getItems(null);
    }

    /**
     * Check if this kit uses the player's current inventory.
     */
    public boolean isCustom() {
        return this == CUSTOM;
    }
}
