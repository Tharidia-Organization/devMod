package com.devmod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.RecipeData;
import com.devmod.recipe.SmeltingRecipeData;
import com.devmod.recipe.SmithingRecipeData;
import com.devmod.recipe.StonecuttingRecipeData;

public record RecipeSyncPayload(
    @Nonnull List<RecipeData> recipes,
    boolean isGlobal,
    @Nonnull SyncOperation operation
) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    // ═══════════════════════════════════════════════════════════════
    // TYPE & CODEC
    // ═══════════════════════════════════════════════════════════════

    public static final Type<RecipeSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "recipe_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncPayload> STREAM_CODEC = StreamCodec.of(
        RecipeSyncPayload::encode,
        RecipeSyncPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    public enum SyncOperation {
        ADD,
        UPDATE,
        DELETE,
        SYNC_ALL;

        public static SyncOperation fromOrdinal(int ordinal) {
            SyncOperation[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ADD;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create payload for adding/updating a single recipe.
     */
    public static RecipeSyncPayload add(RecipeData recipe, boolean isGlobal) {
        return new RecipeSyncPayload(
            Objects.requireNonNull(List.of(recipe), "recipes"),
            isGlobal,
            Objects.requireNonNull(SyncOperation.ADD, "operation")
        );
    }

    /**
     * Create payload for deleting a recipe.
     */
    public static RecipeSyncPayload delete(RecipeData recipe, boolean isGlobal) {
        return new RecipeSyncPayload(
            Objects.requireNonNull(List.of(recipe), "recipes"),
            isGlobal,
            Objects.requireNonNull(SyncOperation.DELETE, "operation")
        );
    }

    /**
     * Create payload for syncing all recipes to a client.
     */
    public static RecipeSyncPayload syncAll(List<RecipeData> recipes) {
        return new RecipeSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
            true,
            Objects.requireNonNull(SyncOperation.SYNC_ALL, "operation")
        );
    }

    /**
     * Create payload for multiple operations.
     */
    public static RecipeSyncPayload batch(List<RecipeData> recipes, boolean isGlobal, SyncOperation operation) {
        return new RecipeSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
            isGlobal,
            Objects.requireNonNull(operation, "operation")
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // ENCODING
    // ═══════════════════════════════════════════════════════════════

    private static void encode(RegistryFriendlyByteBuf buffer, RecipeSyncPayload payload) {
        // Operation
        buffer.writeVarInt(payload.operation.ordinal());

        // Flags
        ByteBufCodecs.BOOL.encode(buffer, payload.isGlobal);

        // Recipe count
        buffer.writeVarInt(payload.recipes.size());

        // Each recipe
        for (RecipeData recipe : payload.recipes) {
            encodeRecipe(buffer, recipe);
        }
    }

    private static void encodeRecipe(FriendlyByteBuf buffer, RecipeData recipe) {
        // ID
        buffer.writeResourceLocation(Objects.requireNonNull(recipe.id(), "recipeId"));

        // Type discriminator
        String typeDiscriminator = getTypeDiscriminator(recipe);
        buffer.writeUtf(Objects.requireNonNull(typeDiscriminator, "typeDiscriminator"));

        // Serialize to JSON and write as string (simple but effective)
        JsonObject json = recipe.toJson();
        buffer.writeUtf(Objects.requireNonNull(GSON.toJson(json), "recipeJson"));
    }

    private static String getTypeDiscriminator(RecipeData recipe) {
        return switch (recipe) {
            case CraftingRecipeData c -> "crafting:" + c.craftingType().getId();
            case SmeltingRecipeData s -> "smelting:" + s.smeltingType().getId();
            case SmithingRecipeData sm -> "smithing:" + sm.smithingType().getId();
            case StonecuttingRecipeData ignored -> "stonecutting";
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // DECODING
    // ═══════════════════════════════════════════════════════════════

    private static RecipeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        // Operation
        SyncOperation operation = SyncOperation.fromOrdinal(buffer.readVarInt());

        // Flags
        boolean isGlobal = ByteBufCodecs.BOOL.decode(buffer);

        // Recipe count
        int count = buffer.readVarInt();

        // Each recipe
        List<RecipeData> recipes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RecipeData recipe = decodeRecipe(buffer);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        return new RecipeSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
            isGlobal,
            Objects.requireNonNull(operation, "operation")
        );
    }

    @Nullable
    private static RecipeData decodeRecipe(FriendlyByteBuf buffer) {
        try {
            // ID
            ResourceLocation id = buffer.readResourceLocation();

            // Type discriminator (read but not strictly needed since JSON has type)
            buffer.readUtf();

            // JSON
            String jsonStr = buffer.readUtf();
            JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
            if (json == null) {
                return null;
            }

            // Parse based on JSON type field
            return RecipeData.fromJson(id, json);
        } catch (Exception e) {
            // Log and skip malformed recipes
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if this payload contains any recipes.
     */
    public boolean isEmpty() {
        return recipes.isEmpty();
    }

    /**
     * Get recipe count.
     */
    public int getRecipeCount() {
        return recipes.size();
    }

    @Override
    public String toString() {
        return "RecipeSyncPayload{" +
            "operation=" + operation +
            ", isGlobal=" + isGlobal +
            ", recipes=" + recipes.size() +
            '}';
    }
}
