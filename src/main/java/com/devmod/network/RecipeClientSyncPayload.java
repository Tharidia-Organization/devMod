package com.devmod.network;

import java.nio.charset.StandardCharsets;
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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.RecipeData;
import com.devmod.recipe.SmeltingRecipeData;
import com.devmod.recipe.SmithingRecipeData;
import com.devmod.recipe.StonecuttingRecipeData;

public record RecipeClientSyncPayload(
    @Nonnull List<RecipeData> recipes,
    @Nonnull SyncOperation operation
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final Gson GSON = new GsonBuilder().create();

    // ═══════════════════════════════════════════════════════════════
    // TYPE & CODEC
    // ═══════════════════════════════════════════════════════════════

    public static final Type<RecipeClientSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "recipe_client_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeClientSyncPayload> STREAM_CODEC = StreamCodec.of(
        RecipeClientSyncPayload::encode,
        RecipeClientSyncPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(operation.ordinal());
        size += varIntSize(recipes.size());
        for (RecipeData recipe : recipes) {
            size += estimateRecipeSize(recipe);
        }
        return size;
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    public enum SyncOperation {
        /** Add or update recipes on client */
        ADD,
        /** Delete recipes from client */
        DELETE,
        /** Full sync - replace all client recipes */
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
     * Create payload for adding/updating a single recipe on client.
     */
    public static RecipeClientSyncPayload add(RecipeData recipe) {
        return new RecipeClientSyncPayload(
            Objects.requireNonNull(List.of(recipe), "recipes"),
            Objects.requireNonNull(SyncOperation.ADD, "operation")
        );
    }

    /**
     * Create payload for deleting a recipe from client.
     */
    public static RecipeClientSyncPayload delete(RecipeData recipe) {
        return new RecipeClientSyncPayload(
            Objects.requireNonNull(List.of(recipe), "recipes"),
            Objects.requireNonNull(SyncOperation.DELETE, "operation")
        );
    }

    /**
     * Create payload for syncing all recipes to a client.
     */
    public static RecipeClientSyncPayload syncAll(List<RecipeData> recipes) {
        return new RecipeClientSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
            Objects.requireNonNull(SyncOperation.SYNC_ALL, "operation")
        );
    }

    /**
     * Create payload for multiple operations.
     */
    public static RecipeClientSyncPayload batch(List<RecipeData> recipes, SyncOperation operation) {
        return new RecipeClientSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
            Objects.requireNonNull(operation, "operation")
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // ENCODING
    // ═══════════════════════════════════════════════════════════════

    private static void encode(RegistryFriendlyByteBuf buffer, RecipeClientSyncPayload payload) {
        // Operation
        buffer.writeVarInt(payload.operation.ordinal());

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

        // Serialize to JSON and write as string
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

    private static RecipeClientSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        // Operation
        SyncOperation operation = SyncOperation.fromOrdinal(buffer.readVarInt());

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

        return new RecipeClientSyncPayload(
            Objects.requireNonNull(recipes, "recipes"),
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
        return "RecipeClientSyncPayload{" +
            "operation=" + operation +
            ", recipes=" + recipes.size() +
            '}';
    }

    private static int estimateRecipeSize(RecipeData recipe) {
        if (recipe == null) {
            return 0;
        }
        int size = 0;
        ResourceLocation id = recipe.id();
        size += estimateResourceLocationSize(id);
        String typeDiscriminator = getTypeDiscriminator(recipe);
        size += estimatedUtfSize(typeDiscriminator);
        String json = GSON.toJson(recipe.toJson());
        size += estimatedUtfSize(json);
        return size;
    }

    private static int estimateResourceLocationSize(ResourceLocation id) {
        if (id == null) {
            return varIntSize(0);
        }
        String text = id.toString();
        return estimatedUtfSize(text);
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
