package com.devmod.foundry.util;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import com.devmod.DevMod;

/**
 * Utility helpers to handle NBT for retexturable blocks.
 */
public final class FoundryRetexturedHelper {
    /** Translation key for the texture ID in advanced tooltips. */
    public static final String KEY_ID = makeDescriptionId("block", "retextured.id");
    /** Tag name for texture blocks. */
    public static final String TAG_TEXTURE = "texture";
    /** Property for tile entities containing a texture block. */
    public static final ModelProperty<Block> BLOCK_PROPERTY = new ModelProperty<>(block -> block != Blocks.AIR);

    private FoundryRetexturedHelper() {}

    private static String makeDescriptionId(String type, String name) {
        return type + "." + DevMod.MODID + "." + name;
    }

    /* Texture name */

    public static String getTextureName(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return "";
        }
        return nbt.getString(TAG_TEXTURE);
    }

    public static String getTextureName(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? getTextureName(customData.copyTag()) : "";
    }

    public static String getTextureName(Block block) {
        if (block == Blocks.AIR) {
            return "";
        }
        return Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block)).toString();
    }

    /* Texture */

    public static Block getBlock(String name) {
        if (!name.isEmpty()) {
            ResourceLocation location = ResourceLocation.tryParse(name);
            if (location != null) {
                return BuiltInRegistries.BLOCK.get(location);
            }
        }
        return Blocks.AIR;
    }

    public static Block getTexture(ItemStack stack) {
        return getBlock(getTextureName(stack));
    }

    /* Setting */

    public static void setTexture(@Nullable CompoundTag nbt, String texture) {
        if (nbt != null) {
            if (texture.isEmpty()) {
                nbt.remove(TAG_TEXTURE);
            } else {
                nbt.putString(TAG_TEXTURE, texture);
            }
        }
    }

    public static ItemStack setTexture(ItemStack stack, String name) {
        if (!name.isEmpty()) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> {
                CompoundTag tag = customData.copyTag();
                tag.putString(TAG_TEXTURE, name);
                return CustomData.of(tag);
            });
        } else if (stack.has(DataComponents.CUSTOM_DATA)) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> {
                CompoundTag tag = customData.copyTag();
                tag.remove(TAG_TEXTURE);
                return tag.isEmpty() ? CustomData.EMPTY : CustomData.of(tag);
            });
        }
        return stack;
    }

    public static ItemStack setTexture(ItemStack stack, @Nullable Block block) {
        if (block == null || block == Blocks.AIR) {
            return setTexture(stack, "");
        }
        return setTexture(stack, BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /* Block entity */

    public static void onTextureUpdated(BlockEntity self) {
        Level level = self.getLevel();
        if (level != null && level.isClientSide) {
            self.requestModelDataUpdate();
            BlockState state = self.getBlockState();
            level.sendBlockUpdated(self.getBlockPos(), state, state, 0);
        }
    }

    public static ModelData.Builder getModelDataBuilder(Block block) {
        if (block == Blocks.AIR) {
            block = null;
        }
        return ModelData.builder().with(BLOCK_PROPERTY, block);
    }

    public static ModelData getModelData(Block block) {
        return getModelDataBuilder(block).build();
    }

    /* Block */

    public static void addTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        Block block = getTexture(stack);
        if (block != Blocks.AIR) {
            tooltip.add(block.getName().withStyle(ChatFormatting.GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable(KEY_ID, BuiltInRegistries.BLOCK.getKey(block)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @SuppressWarnings("InlineMeSuggester")
    @Deprecated(forRemoval = true)
    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        addTooltip(stack, tooltip, TooltipFlag.NORMAL);
    }

    @SuppressWarnings("deprecation")
    public static boolean addTagVariants(Predicate<ItemStack> tab, ItemLike block, TagKey<Item> tag) {
        boolean added = false;

        for (Holder<Item> candidate : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            if (!candidate.isBound()) {
                continue;
            }
            Item item = candidate.value();
            if (item == block.asItem()) {
                continue;
            }
            if (!(item instanceof BlockItem blockItem)) {
                continue;
            }
            added = true;
            if (tab.test(FoundryRetexturedHelper.setTexture(new ItemStack(block), blockItem.getBlock()))) {
                break;
            }
        }
        return added;
    }
}
