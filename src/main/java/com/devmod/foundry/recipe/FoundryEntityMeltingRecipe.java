package com.devmod.foundry.recipe;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryRecipeTypes;

/**
 * Entity melting recipe: living entity -> molten fluid.
 * Entities inside the foundry take damage and produce fluid based on damage dealt.
 */
public class FoundryEntityMeltingRecipe implements Recipe<FoundryEntityMeltingRecipe.EntityInput> {
    public static final int DEFAULT_DAMAGE = 4;
    public static final int DEFAULT_COOLDOWN = 20;

    private final Optional<EntityType<?>> entityType;
    private final Optional<TagKey<EntityType<?>>> entityTag;
    private final FluidStack output;
    private final int damage;
    private final int cooldown;

    public FoundryEntityMeltingRecipe(
        Optional<EntityType<?>> entityType,
        Optional<TagKey<EntityType<?>>> entityTag,
        FluidStack output,
        int damage,
        int cooldown
    ) {
        this.entityType = entityType;
        this.entityTag = entityTag;
        this.output = output;
        this.damage = damage;
        this.cooldown = cooldown;
    }

    /**
     * Convenience constructor for single entity type.
     */
    public static FoundryEntityMeltingRecipe forEntity(EntityType<?> type, FluidStack output, int damage) {
        return new FoundryEntityMeltingRecipe(
            Optional.of(type),
            Optional.empty(),
            output,
            damage,
            DEFAULT_COOLDOWN
        );
    }

    /**
     * Convenience constructor for entity tag.
     */
    public static FoundryEntityMeltingRecipe forTag(TagKey<EntityType<?>> tag, FluidStack output, int damage) {
        return new FoundryEntityMeltingRecipe(
            Optional.empty(),
            Optional.of(tag),
            output,
            damage,
            DEFAULT_COOLDOWN
        );
    }

    /**
     * Check if this recipe matches the given entity.
     */
    public boolean matchesEntity(Entity entity) {
        if (entityType.isPresent()) {
            return entity.getType() == entityType.get();
        }
        if (entityTag.isPresent()) {
            return entity.getType().is(entityTag.get());
        }
        return false;
    }

    @Override
    public boolean matches(@Nonnull EntityInput input, @Nonnull Level level) {
        return matchesEntity(input.entity());
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull EntityInput input, @Nonnull HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    @Nonnull
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FoundryRecipeTypes.ENTITY_MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoundryRecipeTypes.ENTITY_MELTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Optional<EntityType<?>> getEntityType() {
        return entityType;
    }

    public Optional<TagKey<EntityType<?>>> getEntityTag() {
        return entityTag;
    }

    public FluidStack getOutput() {
        return output.copy();
    }

    /**
     * Gets output scaled by actual damage dealt.
     * @param actualDamage The damage actually dealt to the entity
     * @return Scaled fluid output
     */
    public FluidStack getScaledOutput(float actualDamage) {
        if (damage <= 0) return output.copy();
        float ratio = Math.min(1.0f, actualDamage / damage);
        FluidStack scaled = output.copy();
        scaled.setAmount((int)(scaled.getAmount() * ratio));
        return scaled;
    }

    public int getDamage() {
        return damage;
    }

    public int getCooldown() {
        return cooldown;
    }

    /**
     * Input wrapper for entity melting recipes.
     */
    public record EntityInput(Entity entity) implements RecipeInput {
        @Override
        public ItemStack getItem(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public static class Serializer implements RecipeSerializer<FoundryEntityMeltingRecipe> {
        private static final Codec<EntityType<?>> ENTITY_TYPE_CODEC =
            ResourceLocation.CODEC.xmap(
                id -> BuiltInRegistries.ENTITY_TYPE.get(id),
                type -> BuiltInRegistries.ENTITY_TYPE.getKey(type)
            );

        private static final Codec<TagKey<EntityType<?>>> ENTITY_TAG_CODEC =
            ResourceLocation.CODEC.xmap(
                id -> TagKey.create(Registries.ENTITY_TYPE, id),
                TagKey::location
            );

        public static final MapCodec<FoundryEntityMeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                ENTITY_TYPE_CODEC.optionalFieldOf("entity").forGetter(FoundryEntityMeltingRecipe::getEntityType),
                ENTITY_TAG_CODEC.optionalFieldOf("entity_tag").forGetter(FoundryEntityMeltingRecipe::getEntityTag),
                Objects.requireNonNull(FoundryCodecs.FLUID_STACK_CODEC).fieldOf("result").forGetter(r -> r.output),
                Codec.INT.optionalFieldOf("damage", DEFAULT_DAMAGE).forGetter(FoundryEntityMeltingRecipe::getDamage),
                Codec.INT.optionalFieldOf("cooldown", DEFAULT_COOLDOWN).forGetter(FoundryEntityMeltingRecipe::getCooldown)
            ).apply(instance, FoundryEntityMeltingRecipe::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, FoundryEntityMeltingRecipe> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FoundryEntityMeltingRecipe decode(RegistryFriendlyByteBuf buf) {
                    boolean hasEntityType = buf.readBoolean();
                    Optional<EntityType<?>> entityType = hasEntityType
                        ? Optional.of(BuiltInRegistries.ENTITY_TYPE.get(buf.readResourceLocation()))
                        : Optional.empty();

                    boolean hasTag = buf.readBoolean();
                    Optional<TagKey<EntityType<?>>> entityTag = hasTag
                        ? Optional.of(TagKey.create(Registries.ENTITY_TYPE, buf.readResourceLocation()))
                        : Optional.empty();

                    FluidStack output = FoundryCodecs.FLUID_STACK_STREAM_CODEC.decode(buf);
                    int damage = buf.readInt();
                    int cooldown = buf.readInt();

                    return new FoundryEntityMeltingRecipe(entityType, entityTag, output, damage, cooldown);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FoundryEntityMeltingRecipe recipe) {
                    buf.writeBoolean(recipe.entityType.isPresent());
                    recipe.entityType.ifPresent(type ->
                        buf.writeResourceLocation(BuiltInRegistries.ENTITY_TYPE.getKey(type)));

                    buf.writeBoolean(recipe.entityTag.isPresent());
                    recipe.entityTag.ifPresent(tag -> buf.writeResourceLocation(tag.location()));

                    FoundryCodecs.FLUID_STACK_STREAM_CODEC.encode(buf, recipe.output);
                    buf.writeInt(recipe.damage);
                    buf.writeInt(recipe.cooldown);
                }
            };

        @Override
        public MapCodec<FoundryEntityMeltingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryEntityMeltingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
