package com.devmod.foundry.tool;

import java.util.Set;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Supported foundry tool kinds for mining and actions.
 */
public enum FoundryToolKind {
    PICKAXE(BlockTags.MINEABLE_WITH_PICKAXE, ItemAbilities.DEFAULT_PICKAXE_ACTIONS, true),
    AXE(BlockTags.MINEABLE_WITH_AXE, ItemAbilities.DEFAULT_AXE_ACTIONS, false),
    SHOVEL(BlockTags.MINEABLE_WITH_SHOVEL, ItemAbilities.DEFAULT_SHOVEL_ACTIONS, false),
    SWORD(null, ItemAbilities.DEFAULT_SWORD_ACTIONS, false);

    private final TagKey<Block> mineableTag;
    private final Set<ItemAbility> abilities;
    private final boolean usesMiningLevel;

    FoundryToolKind(TagKey<Block> mineableTag, Set<ItemAbility> abilities, boolean usesMiningLevel) {
        this.mineableTag = mineableTag;
        this.abilities = abilities;
        this.usesMiningLevel = usesMiningLevel;
    }

    public TagKey<Block> getMineableTag() {
        return mineableTag;
    }

    public Set<ItemAbility> getAbilities() {
        return abilities;
    }

    public boolean usesMiningLevel() {
        return usesMiningLevel;
    }
}
