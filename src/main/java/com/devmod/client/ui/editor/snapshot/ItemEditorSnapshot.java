package com.devmod.client.ui.editor.snapshot;

import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

import com.devmod.recipe.CraftingRecipeData;

public class ItemEditorSnapshot {

    public static final String VERSION = "2.0";

    public String version = VERSION;
    @Nullable
    public JsonElement item;
    @Nullable
    public RecipeSnapshot recipe;
    public SnapshotMeta meta = new SnapshotMeta();

    public static ItemEditorSnapshot capture(ItemStack itemStack, SnapshotMeta meta,
                                             @Nullable CraftingRecipeData recipeData) {
        ItemEditorSnapshot snapshot = new ItemEditorSnapshot();
        snapshot.meta = meta == null ? new SnapshotMeta() : meta;
        snapshot.item = encodeItem(itemStack);
        if (recipeData != null) {
            snapshot.recipe = RecipeSnapshot.fromRecipe(recipeData);
        }
        return snapshot;
    }

    public ItemStack toItemStack() {
        if (item == null) return ItemStack.EMPTY;
        DataResult<ItemStack> parsed = ItemStack.CODEC.parse(JsonOps.INSTANCE, item);
        return parsed.result().orElse(ItemStack.EMPTY);
    }

    @Nullable
    public CraftingRecipeData toRecipe() {
        if (recipe == null) return null;
        return recipe.toRecipe();
    }

    public CompoundTag toItemTag() {
        if (item == null) return new CompoundTag();
        DataResult<CompoundTag> parsed = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, toItemStack())
            .map(CompoundTag.class::cast);
        return parsed.result().orElseGet(CompoundTag::new);
    }

    public static JsonElement encodeItem(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, itemStack)
            .result()
            .orElseGet(JsonObject::new);
    }

    public static ItemStack decodeItem(JsonElement element) {
        if (element == null) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(JsonOps.INSTANCE, element)
            .result()
            .orElse(ItemStack.EMPTY);
    }

    public static boolean looksLikeSnapshot(JsonObject root) {
        if (root == null) return false;
        return root.has("item") || root.has("meta") || root.has("version");
    }

    public static class SnapshotMeta {
        public long timestamp = System.currentTimeMillis();
        public String action = "snapshot";
        public String itemId = "";
        public String itemName = "";
        public String moduleId = "";
        public String tabId = "";
        public int tabIndex = -1;
        public boolean previewMode = true;
        @Nullable
        public String slotId = null;
        @Nullable
        public String note = null;
    }

    public static class RecipeSnapshot {
        public String id = "";
        public JsonObject json = new JsonObject();

        public static RecipeSnapshot fromRecipe(CraftingRecipeData recipe) {
            RecipeSnapshot snapshot = new RecipeSnapshot();
            snapshot.id = recipe.id().toString();
            snapshot.json = recipe.toJson();
            return snapshot;
        }

        @Nullable
        public CraftingRecipeData toRecipe() {
            if (id == null || id.isBlank()) return null;
            return CraftingRecipeData.fromJson(
                Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryParse(id)),
                Objects.requireNonNull(json, "recipe json")
            );
        }
    }
}
