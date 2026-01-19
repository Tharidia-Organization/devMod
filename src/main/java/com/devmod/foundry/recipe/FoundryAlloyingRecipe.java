package com.devmod.foundry.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.fluid.FoundryFluidTank;

/**
 * Alloying recipe: fluid inputs -> fluid output.
 */
public class FoundryAlloyingRecipe implements Recipe<FoundryAlloyingRecipe.AlloyingInput> {
    public static final int DEFAULT_TIME = 100;
    private static final float RATIO_EPSILON = 0.0001f;

    private final List<FluidStack> inputs;
    private final List<AlloyComponent> components;
    private final List<RatioTier> ratioTiers;
    @Nullable private final Fluid primaryComponent;
    private final FluidStack output;
    private final int time;
    private final int temperature;
    public FoundryAlloyingRecipe(List<FluidStack> inputs, FluidStack output, int time, int temperature) {
        this(inputs, List.of(), null, List.of(), output, time, temperature);
    }

    public FoundryAlloyingRecipe(
        List<FluidStack> inputs,
        List<AlloyComponent> components,
        @Nullable Fluid primaryComponent,
        List<RatioTier> ratioTiers,
        FluidStack output,
        int time,
        int temperature
    ) {
        this.inputs = List.copyOf(Objects.requireNonNull(inputs));
        this.components = List.copyOf(Objects.requireNonNull(components));
        this.primaryComponent = primaryComponent;
        this.ratioTiers = List.copyOf(Objects.requireNonNull(ratioTiers));
        this.output = Objects.requireNonNull(output);
        this.time = time;
        this.temperature = temperature;
    }

    @Override
    public boolean matches(@Nonnull AlloyingInput input, @Nonnull Level level) {
        return false;
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull AlloyingInput input, @Nonnull HolderLookup.Provider registries) {
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
    public NonNullList<net.minecraft.world.item.crafting.Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FoundryRecipeTypes.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoundryRecipeTypes.ALLOYING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public boolean isDynamic() {
        return !components.isEmpty();
    }

    public List<FluidStack> getInputs() {
        return inputs;
    }

    public List<AlloyComponent> getComponents() {
        return components;
    }

    public List<RatioTier> getRatioTiers() {
        return ratioTiers;
    }

    @Nullable
    public Fluid getPrimaryComponent() {
        return primaryComponent;
    }

    public List<Fluid> getComponentFluids() {
        List<Fluid> fluids = new ArrayList<>();
        if (isDynamic()) {
            for (AlloyComponent component : components) {
                fluids.add(component.fluid());
            }
        } else {
            for (FluidStack stack : inputs) {
                fluids.add(stack.getFluid());
            }
        }
        return Collections.unmodifiableList(fluids);
    }

    public FluidStack getOutput() {
        return output.copy();
    }

    public int getTime() {
        return time;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getInputAmount() {
        if (isDynamic()) {
            return output.getAmount();
        }
        int total = 0;
        for (FluidStack stack : inputs) {
            total += stack.getAmount();
        }
        return total;
    }

    public boolean canApply(FoundryFluidTank tank) {
        if (isDynamic()) {
            return canApplyDynamic(tank);
        }
        if (inputs.isEmpty()) {
            return false;
        }
        for (FluidStack stack : inputs) {
            if (!tank.contains(stack)) {
                return false;
            }
        }
        return true;
    }

    public int consumeInputs(FoundryFluidTank tank) {
        if (!isDynamic()) {
            int total = 0;
            for (FluidStack stack : inputs) {
                tank.drain(stack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                total += stack.getAmount();
            }
            return total;
        }
        int totalAmount = tank.getTotalAmountForFluids(getComponentFluids());
        if (totalAmount <= 0) {
            return 0;
        }
        int outputAmount = output.getAmount();
        if (outputAmount > totalAmount) {
            return 0;
        }
        int remaining = outputAmount;
        int drainedTotal = 0;
        for (int i = 0; i < components.size(); i++) {
            AlloyComponent component = components.get(i);
            int amount = tank.getAmountForFluid(component.fluid());
            if (amount <= 0) {
                continue;
            }
            float ratio = (float) amount / totalAmount;
            int toDrain = (i == components.size() - 1)
                ? remaining
                : Math.round(outputAmount * ratio);
            toDrain = Math.min(toDrain, amount);
            toDrain = Math.min(toDrain, remaining);
            if (toDrain > 0) {
                tank.drain(new FluidStack(component.fluid(), toDrain), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                drainedTotal += toDrain;
                remaining -= toDrain;
            }
        }
        return drainedTotal;
    }

    @Nullable
    public AlloyRatioState getRatioState(FoundryFluidTank tank) {
        if (!isDynamic() || components.isEmpty()) {
            return null;
        }
        Fluid primary = primaryComponent != null ? primaryComponent : components.get(0).fluid();
        int total = tank.getTotalAmountForFluids(getComponentFluids());
        if (total <= 0) {
            return null;
        }
        int primaryAmount = tank.getAmountForFluid(primary);
        float ratio = clampRatio((float) primaryAmount / total);
        RatioTier tier = getTierForRatio(ratio);
        return new AlloyRatioState(ratio, tier);
    }

    private boolean canApplyDynamic(FoundryFluidTank tank) {
        if (components.isEmpty()) {
            return false;
        }
        int totalAmount = tank.getTotalAmountForFluids(getComponentFluids());
        if (totalAmount < output.getAmount()) {
            return false;
        }
        for (AlloyComponent component : components) {
            int amount = tank.getAmountForFluid(component.fluid());
            float ratio = totalAmount > 0 ? (float) amount / totalAmount : 0f;
            if (!component.matchesRatio(ratio)) {
                return false;
            }
        }
        return true;
    }

    private RatioTier getTierForRatio(float ratio) {
        if (ratioTiers.isEmpty()) {
            return RatioTier.DEFAULT;
        }
        for (RatioTier tier : ratioTiers) {
            if (tier.matches(ratio)) {
                return tier;
            }
        }
        return RatioTier.DEFAULT;
    }

    private static float clampRatio(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public record AlloyRatioState(float ratio, RatioTier tier) {}

    public record AlloyComponent(Fluid fluid, float minRatio, float maxRatio) {
        public static final MapCodec<AlloyComponent> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("fluid")
                    .forGetter(component -> Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(component.fluid()))),
                Codec.FLOAT.optionalFieldOf("min_ratio", 0f).forGetter(AlloyComponent::minRatio),
                Codec.FLOAT.optionalFieldOf("max_ratio", 1f).forGetter(AlloyComponent::maxRatio)
            ).apply(instance, (id, minRatio, maxRatio) ->
                new AlloyComponent(Objects.requireNonNull(BuiltInRegistries.FLUID.get(id)), minRatio, maxRatio))
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, AlloyComponent> STREAM_CODEC =
            StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                component -> Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(component.fluid())),
                ByteBufCodecs.FLOAT, AlloyComponent::minRatio,
                ByteBufCodecs.FLOAT, AlloyComponent::maxRatio,
                (id, minRatio, maxRatio) ->
                    new AlloyComponent(Objects.requireNonNull(BuiltInRegistries.FLUID.get(id)), minRatio, maxRatio)
            );

        public AlloyComponent {
            minRatio = clampRatio(minRatio);
            maxRatio = clampRatio(maxRatio);
        }

        public boolean matchesRatio(float ratio) {
            return ratio + RATIO_EPSILON >= minRatio && ratio - RATIO_EPSILON <= maxRatio;
        }
    }

    public record RatioTier(String id, float minRatio, float maxRatio, int qualityShift, float purityBonus) {
        public static final RatioTier DEFAULT = new RatioTier("base", 0f, 1f, 0, 0f);

        public static final MapCodec<RatioTier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("id", "base").forGetter(RatioTier::id),
                Codec.FLOAT.optionalFieldOf("min_ratio", 0f).forGetter(RatioTier::minRatio),
                Codec.FLOAT.optionalFieldOf("max_ratio", 1f).forGetter(RatioTier::maxRatio),
                Codec.INT.optionalFieldOf("quality_shift", 0).forGetter(RatioTier::qualityShift),
                Codec.FLOAT.optionalFieldOf("purity_bonus", 0f).forGetter(RatioTier::purityBonus)
            ).apply(instance, RatioTier::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, RatioTier> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RatioTier::id,
                ByteBufCodecs.FLOAT, RatioTier::minRatio,
                ByteBufCodecs.FLOAT, RatioTier::maxRatio,
                ByteBufCodecs.INT, RatioTier::qualityShift,
                ByteBufCodecs.FLOAT, RatioTier::purityBonus,
                RatioTier::new
            );

        public RatioTier {
            minRatio = clampRatio(minRatio);
            maxRatio = clampRatio(maxRatio);
            qualityShift = Math.max(-2, Math.min(2, qualityShift));
        }

        public boolean matches(float ratio) {
            return ratio + RATIO_EPSILON >= minRatio && ratio - RATIO_EPSILON <= maxRatio;
        }
    }

    public record AlloyingInput(List<FluidStack> fluids) implements net.minecraft.world.item.crafting.RecipeInput {
        @Override
        public int size() {
            return fluids.size();
        }

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }
    }

    private Optional<ResourceLocation> getPrimaryComponentId() {
        if (primaryComponent == null) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(primaryComponent)));
    }

    @Nullable
    private static Fluid resolvePrimaryComponent(List<AlloyComponent> components, Optional<ResourceLocation> primaryId) {
        if (components.isEmpty()) {
            return null;
        }
        ResourceLocation id = primaryId.orElseGet(() ->
            Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(components.get(0).fluid())));
        return BuiltInRegistries.FLUID.get(id);
    }

    public static class Serializer implements RecipeSerializer<FoundryAlloyingRecipe> {
        public static final MapCodec<FoundryAlloyingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                FoundryCodecs.FLUID_STACK_CODEC.listOf().optionalFieldOf("inputs", List.of())
                    .forGetter(FoundryAlloyingRecipe::getInputs),
                AlloyComponent.CODEC.codec().listOf().optionalFieldOf("components", List.of())
                    .forGetter(FoundryAlloyingRecipe::getComponents),
                ResourceLocation.CODEC.optionalFieldOf("primary_component")
                    .forGetter(FoundryAlloyingRecipe::getPrimaryComponentId),
                RatioTier.CODEC.codec().listOf().optionalFieldOf("ratio_tiers", List.of())
                    .forGetter(FoundryAlloyingRecipe::getRatioTiers),
                FoundryCodecs.FLUID_STACK_CODEC.fieldOf("result").forGetter(FoundryAlloyingRecipe::getOutput),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(FoundryAlloyingRecipe::getTime),
                Codec.INT.optionalFieldOf("temperature", 0).forGetter(FoundryAlloyingRecipe::getTemperature)
            ).apply(instance, (inputs, components, primaryId, ratioTiers, output, time, temperature) ->
                new FoundryAlloyingRecipe(
                    inputs,
                    components,
                    resolvePrimaryComponent(components, primaryId),
                    ratioTiers,
                    output,
                    time,
                    temperature
                ))
        );

        private static final StreamCodec<RegistryFriendlyByteBuf, List<FluidStack>> FLUID_LIST_STREAM_CODEC =
            FoundryCodecs.FLUID_STACK_STREAM_CODEC.apply(ByteBufCodecs.list());
        private static final StreamCodec<RegistryFriendlyByteBuf, List<AlloyComponent>> COMPONENT_LIST_STREAM_CODEC =
            AlloyComponent.STREAM_CODEC.apply(ByteBufCodecs.list());
        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ResourceLocation>> OPTIONAL_PRIMARY_STREAM_CODEC =
            StreamCodec.of(Serializer::encodeOptionalPrimary, Serializer::decodeOptionalPrimary);
        private static final StreamCodec<RegistryFriendlyByteBuf, List<RatioTier>> RATIO_TIER_LIST_STREAM_CODEC =
            RatioTier.STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<RegistryFriendlyByteBuf, FoundryAlloyingRecipe> STREAM_CODEC =
            StreamCodec.of(Serializer::encode, Serializer::decode);

        private static void encode(RegistryFriendlyByteBuf buf, FoundryAlloyingRecipe recipe) {
            FLUID_LIST_STREAM_CODEC.encode(buf, recipe.getInputs());
            COMPONENT_LIST_STREAM_CODEC.encode(buf, recipe.getComponents());
            OPTIONAL_PRIMARY_STREAM_CODEC.encode(buf, recipe.getPrimaryComponentId());
            RATIO_TIER_LIST_STREAM_CODEC.encode(buf, recipe.getRatioTiers());
            FoundryCodecs.FLUID_STACK_STREAM_CODEC.encode(buf, recipe.getOutput());
            ByteBufCodecs.INT.encode(buf, recipe.getTime());
            ByteBufCodecs.INT.encode(buf, recipe.getTemperature());
        }

        private static FoundryAlloyingRecipe decode(RegistryFriendlyByteBuf buf) {
            List<FluidStack> inputs = FLUID_LIST_STREAM_CODEC.decode(buf);
            List<AlloyComponent> components = COMPONENT_LIST_STREAM_CODEC.decode(buf);
            Optional<ResourceLocation> primaryId = OPTIONAL_PRIMARY_STREAM_CODEC.decode(buf);
            List<RatioTier> ratioTiers = RATIO_TIER_LIST_STREAM_CODEC.decode(buf);
            FluidStack output = FoundryCodecs.FLUID_STACK_STREAM_CODEC.decode(buf);
            int time = ByteBufCodecs.INT.decode(buf);
            int temperature = ByteBufCodecs.INT.decode(buf);
            return new FoundryAlloyingRecipe(
                inputs,
                components,
                resolvePrimaryComponent(components, primaryId),
                ratioTiers,
                output,
                time,
                temperature
            );
        }

        private static void encodeOptionalPrimary(RegistryFriendlyByteBuf buf, Optional<ResourceLocation> value) {
            buf.writeBoolean(value.isPresent());
            value.ifPresent(id -> ResourceLocation.STREAM_CODEC.encode(buf, id));
        }

        private static Optional<ResourceLocation> decodeOptionalPrimary(RegistryFriendlyByteBuf buf) {
            return buf.readBoolean()
                ? Optional.of(ResourceLocation.STREAM_CODEC.decode(buf))
                : Optional.empty();
        }

        @Override
        public MapCodec<FoundryAlloyingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryAlloyingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
