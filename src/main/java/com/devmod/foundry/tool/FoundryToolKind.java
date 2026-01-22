package com.devmod.foundry.tool;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Supported foundry tool kinds for mining and actions.
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum FoundryToolKind {
    PICKAXE(BlockTags.MINEABLE_WITH_PICKAXE, ItemAbilities.DEFAULT_PICKAXE_ACTIONS, true, EquipmentSlotGroup.MAINHAND, false),
    AXE(BlockTags.MINEABLE_WITH_AXE, ItemAbilities.DEFAULT_AXE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    SHOVEL(BlockTags.MINEABLE_WITH_SHOVEL, ItemAbilities.DEFAULT_SHOVEL_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    SWORD(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    HOE(BlockTags.MINEABLE_WITH_HOE, ItemAbilities.DEFAULT_HOE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    HAMMER(BlockTags.MINEABLE_WITH_PICKAXE, ItemAbilities.DEFAULT_PICKAXE_ACTIONS, true, EquipmentSlotGroup.MAINHAND, false),
    EXCAVATOR(BlockTags.MINEABLE_WITH_SHOVEL, ItemAbilities.DEFAULT_SHOVEL_ACTIONS, true, EquipmentSlotGroup.MAINHAND, false),
    SCYTHE(BlockTags.MINEABLE_WITH_HOE, ItemAbilities.DEFAULT_HOE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    DAGGER(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    SPEAR(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    CLEAVER(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    LONGSWORD(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    BATTLEAXE(BlockTags.MINEABLE_WITH_AXE, ItemAbilities.DEFAULT_AXE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    MATTOCK(BlockTags.MINEABLE_WITH_AXE, ItemAbilities.DEFAULT_AXE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    KAMA(BlockTags.MINEABLE_WITH_HOE, ItemAbilities.DEFAULT_HOE_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    BOW(null, Set.of(), false, EquipmentSlotGroup.MAINHAND, false),
    CROSSBOW(null, Set.of(), false, EquipmentSlotGroup.MAINHAND, false),
    LONGBOW(null, Set.of(), false, EquipmentSlotGroup.MAINHAND, false),
    SHIELD(null, ItemAbilities.DEFAULT_SHIELD_ACTIONS, false, EquipmentSlotGroup.OFFHAND, false),
    WRENCH(null, Set.of(), false, EquipmentSlotGroup.MAINHAND, false),
    FISHING_ROD(null, ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS, false, EquipmentSlotGroup.MAINHAND, false),
    STAFF(null, Set.of(), false, EquipmentSlotGroup.MAINHAND, false),
    ARMOR_HELMET(null, Set.of(), false, EquipmentSlotGroup.HEAD, true),
    ARMOR_CHESTPLATE(null, Set.of(), false, EquipmentSlotGroup.CHEST, true),
    ARMOR_LEGGINGS(null, Set.of(), false, EquipmentSlotGroup.LEGS, true),
    ARMOR_BOOTS(null, Set.of(), false, EquipmentSlotGroup.FEET, true);

    @Nullable
    private final TagKey<Block> mineableTag;
    private final Set<ItemAbility> abilities;
    private final boolean usesMiningLevel;
    private final EquipmentSlotGroup slotGroup;
    private final boolean armor;

    FoundryToolKind(
        @Nullable TagKey<Block> mineableTag,
        Set<ItemAbility> abilities,
        boolean usesMiningLevel,
        EquipmentSlotGroup slotGroup,
        boolean armor
    ) {
        this.mineableTag = mineableTag;
        this.abilities = abilities;
        this.usesMiningLevel = usesMiningLevel;
        this.slotGroup = slotGroup;
        this.armor = armor;
    }

    @Nullable
    public TagKey<Block> getMineableTag() {
        return mineableTag;
    }

    public Set<ItemAbility> getAbilities() {
        return abilities;
    }

    public boolean usesMiningLevel() {
        return usesMiningLevel;
    }

    public EquipmentSlotGroup getSlotGroup() {
        return slotGroup;
    }

    public boolean isArmor() {
        return armor;
    }
}
