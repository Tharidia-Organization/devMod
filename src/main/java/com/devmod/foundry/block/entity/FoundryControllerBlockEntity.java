package com.devmod.foundry.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.FoundryTags;
import com.devmod.foundry.block.FoundryControllerBlock;
import com.devmod.foundry.entity.FoundryEntityMeltingModule;
import com.devmod.foundry.fluid.FoundryFluidTank;
import com.devmod.foundry.item.FoundryFluxItem;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.progression.FoundrySpecialization;
import com.devmod.foundry.progression.FoundryTier;
import com.devmod.foundry.quality.FoundryFluidQuality;
import com.devmod.foundry.quality.FoundryItemQuality;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.recipe.FoundryAlloyingRecipe;
import com.devmod.foundry.recipe.FoundryFuelRecipe;
import com.devmod.foundry.recipe.FoundryMeltingRecipe;
import com.devmod.foundry.risk.IncidentType;
import com.devmod.foundry.risk.RiskLevel;
import com.devmod.foundry.risk.RiskManager;
import com.devmod.foundry.structure.FoundryStructure;
import com.devmod.foundry.structure.FoundryStructureDetector;
import com.devmod.foundry.structure.FoundryStructureResult;
import com.devmod.foundry.thermal.ThermalManager;
import com.devmod.foundry.tool.material.FoundryMaterialAlloying;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

/**
 * Foundry controller block entity.
 */
public class FoundryControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_MAX_PROGRESS = "MaxProgress";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_MOLTEN = "MoltenTank";
    private static final String TAG_FUEL = "FuelTank";
    private static final String TAG_FUEL_TICKS = "FuelTicks";
    private static final String TAG_FUEL_TICKS_MAX = "FuelTicksMax";
    private static final String TAG_FUEL_TEMP = "FuelTemp";
    private static final String TAG_THERMAL = "Thermal";
    private static final String TAG_RISK = "Risk";
    private static final String TAG_STRUCTURE_DAMAGE = "StructureDamage";
    private static final String TAG_CURRENT_PURITY = "CurrentPurity";
    private static final String TAG_HAD_INCIDENT = "HadIncident";
    private static final String TAG_ALLOY_PROGRESS = "AlloyProgress";
    private static final String TAG_ALLOY_MAX_PROGRESS = "AlloyMaxProgress";
    private static final String TAG_ALLOY_RECIPE = "AlloyRecipe";
    private static final String TAG_TIER_LIMIT = "TierLimit";
    private static final String TAG_MELT_OPTIMAL_LOW = "MeltOptimalLow";
    private static final String TAG_MELT_OPTIMAL_HIGH = "MeltOptimalHigh";
    private static final String TAG_MELT_INITIALIZED = "MeltInitialized";

    private static final float FLUX_PURITY_BONUS = 0.08f;
    private static final int MAX_FLUX_PER_MELT = 4;
    private static final float ORE_IMPURITY = 0.15f;
    private static final float RAW_IMPURITY = 0.08f;
    private static final float ORE_RICH_BONUS = -0.05f;
    private static final float ORE_POOR_PENALTY = 0.05f;
    private static final float RAW_RICH_BONUS = -0.04f;
    private static final float RAW_POOR_PENALTY = 0.04f;
    private static final float PURITY_SIZZLE_THRESHOLD = 0.8f;
    private static final float PURITY_SIZZLE_TEMP = 500f;
    private static final int PURITY_SIZZLE_INTERVAL = 80;
    private static final float PURITY_SIZZLE_CHANCE = 0.35f;

    private final SimpleContainer inventory = new SimpleContainer(FoundryControllerMenu.CONTAINER_SIZE);
    private final FoundryFluidTank moltenTank;
    private final FluidTank fuelTank;

    // New systems
    private final ThermalManager thermalManager = new ThermalManager();
    private final RiskManager riskManager = new RiskManager();
    @Nullable private FoundryEntityMeltingModule entityMeltingModule;

    private boolean formed = false;
    private boolean structureDirty = true;
    @Nullable private FoundryStructure structure;
    @Nullable private Component lastError;

    private int progress = 0;
    private int maxProgress = 200;
    private int activeSlot = -1;
    @Nullable private FoundryMeltingRecipe currentRecipe;
    private int alloyProgress = 0;
    private int alloyMaxProgress = 0;
    @Nullable private FoundryAlloyingRecipe currentAlloyRecipe;
    @Nullable private ResourceLocation currentAlloyRecipeId;
    private int alloyPreviewFluidId = -1;
    private int alloyPreviewRatio = 0;
    private FoundryTier tierLimit = FoundryTier.PRIMITIVE;
    private boolean tierBlocked = false;
    private boolean tierErrorActive = false;
    private int fuelTicks = 0;
    private int fuelTicksMax = 0;
    private int fuelTemperature = 0;
    @Nullable private UUID lastOperator;

    // Quality tracking
    private int structureDamage = 0;
    private float currentMeltPurity = 1.0f;
    private boolean hadIncidentThisCycle = false;
    private int meltOptimalLow = 0;
    private int meltOptimalHigh = 0;
    private boolean meltInitialized = false;
    private int tickCounter = 0;

    // Client-side cached data for rendering
    @Nullable private BlockPos clientMinPos;
    @Nullable private BlockPos clientMaxPos;
    private int lastSyncedTankAmount = -1;
    private static final int TANK_SYNC_INTERVAL = 20; // Sync every second when tank changes

    public FoundryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CONTROLLER.get()), pos, state);
        moltenTank = new FoundryFluidTank(Config.FOUNDRY_CAPACITY_PER_BLOCK.get());
        fuelTank = new FluidTank(Config.FOUNDRY_FUEL_CAPACITY.get());
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        tickCounter++;

        // Lazy initialization of entity melting module (needs level)
        if (entityMeltingModule == null) {
            entityMeltingModule = new FoundryEntityMeltingModule(
                level,
                moltenTank,
                this::canMeltEntities,
                this::getInteriorBounds
            );
        }

        // Debug log every 100 ticks
        if (tickCounter % 100 == 0) {
            DevMod.LOGGER.info("[Foundry] Tick #{} at {} - formed={}, structureDirty={}", tickCounter, worldPosition, formed, structureDirty);
        }

        if (structureDirty) {
            validateStructure(level);
        }

        if (!formed) {
            alloyPreviewFluidId = -1;
            alloyPreviewRatio = 0;
            // Thermal still ticks when not formed (cooling down)
            thermalManager.tick(0, false);
            moltenTank.tick(0, false);
            return;
        }

        // Periodic sync of tank data to clients for rendering
        if (tickCounter % TANK_SYNC_INTERVAL == 0) {
            int currentAmount = moltenTank.getUsed();
            if (currentAmount != lastSyncedTankAmount) {
                DevMod.LOGGER.info("[Foundry] Tank changed: {} -> {}, syncing to clients", lastSyncedTankAmount, currentAmount);
                lastSyncedTankAmount = currentAmount;
                syncToClients();
            }
        }

        consumeFuelItem(level);

        int requiredTemp = findRequiredTemperature(level);
        if (requiredTemp < 0) {
            alloyPreviewFluidId = -1;
            alloyPreviewRatio = 0;
            updateActiveState(level, false);
            thermalManager.tick(fuelTemperature, false);
            moltenTank.tick(thermalManager.getStructureHeat(), false);
            progress = 0;
            if (tierBlocked) {
                lastError = Component.translatable("devmod.foundry.error.tier_temp", tierLimit.getDisplayName());
                tierErrorActive = true;
            }
            return;
        }
        if (tierErrorActive) {
            lastError = null;
            tierErrorActive = false;
        }

        ensureFuel(level, requiredTemp);
        int currentTemp = getCurrentTemperature();

        // Update thermal system every tick
        thermalManager.tick(currentTemp, fuelTicks > 0);

        // Get effective temperature (accounts for structure warmup)
        float effectiveTemp = thermalManager.getEffectiveTemperature(currentTemp);
        moltenTank.tick(thermalManager.getStructureHeat(), false);
        maybePlayPuritySizzle(level, effectiveTemp);
        updateAlloyPreview(level, (int) effectiveTemp);

        // Debug: log fuel and temperature status
        if (tickCounter % 100 == 0) {
            DevMod.LOGGER.info("[Foundry] Status at {}: fuelTicks={}, effectiveTemp={}, requiredTemp={}, fuelTank={}/{}",
                worldPosition, fuelTicks, (int)effectiveTemp, requiredTemp, fuelTank.getFluidAmount(), fuelTank.getCapacity());
        }

        if (fuelTicks > 0 && effectiveTemp >= requiredTemp) {
            fuelTicks--;

            // Apply thermal efficiency to processing speed
            float thermalEfficiency = thermalManager.getEfficiencyMultiplier(requiredTemp);

            // Evaluate risk every 20 ticks
            if (tickCounter % 20 == 0) {
                float tankFill = (float) moltenTank.getUsed() / moltenTank.getCapacity();
                riskManager.evaluate(currentTemp, requiredTemp, tankFill, thermalManager, structureDamage);

                // Roll for incidents
                if (level instanceof ServerLevel serverLevel) {
                    IncidentType incident = riskManager.rollForIncident(level, worldPosition);
                    if (incident != null) {
                        handleIncident(incident, serverLevel);
                    }
                }
            }

            float riskMultiplier = riskManager.getEfficiencyMultiplier();
            float totalEfficiency = thermalEfficiency * riskMultiplier;

            processMelting(level, (int) effectiveTemp, totalEfficiency, riskMultiplier);
            processAlloying(level, (int) effectiveTemp, totalEfficiency, riskMultiplier);

            // Process entity melting (entities falling into molten metal)
            if (entityMeltingModule != null) {
                entityMeltingModule.tick();
            }

            updateActiveState(level, true);

            // Check for thermal damage
            if (thermalManager.shouldApplyDamage()) {
                applyStructureDamage();
                thermalManager.onDamageApplied();
            }
        } else {
            updateActiveState(level, false);
            progress = 0;
        }
    }

    private void maybePlayPuritySizzle(Level level, float effectiveTemp) {
        if (tickCounter % PURITY_SIZZLE_INTERVAL != 0) {
            return;
        }
        if (moltenTank.isEmpty() || effectiveTemp < PURITY_SIZZLE_TEMP) {
            return;
        }
        float lowestPurity = moltenTank.getLowestPurity();
        if (lowestPurity >= PURITY_SIZZLE_THRESHOLD) {
            return;
        }
        if (level.random.nextFloat() > PURITY_SIZZLE_CHANCE) {
            return;
        }
        float volume = 0.15f + ((1.0f - lowestPurity) * 0.45f);
        float pitch = 0.85f + (level.random.nextFloat() * 0.3f);
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, volume, pitch);
    }

    private void handleIncident(IncidentType incident, ServerLevel level) {
        hadIncidentThisCycle = true;
        DevMod.LOGGER.info("[Foundry] Incident occurred at {}: {}", worldPosition, incident.getName());

        riskManager.applyIncident(
            incident,
            level,
            worldPosition,
            thermalManager,
            this::applyStructureDamage,
            this::loseFluid,
            this::reducePurity
        );
        Player operator = resolveLastOperator(level);
        if (operator != null) {
            FoundryPlayerProgress progress = FoundryProgressAttachment.get(operator);
            progress.recordIncidentSurvived();
            progress.tryAdvanceTier();
            FoundryProgressAttachment.sync(operator);
        }
    }

    private void applyStructureDamage() {
        structureDamage++;
        FoundryStructure currentStructure = structure;
        if (level instanceof ServerLevel serverLevel && currentStructure != null) {
            BlockPos cracked = findRandomStructureBlock(serverLevel, currentStructure, FoundryBlocks.FOUNDRY_BRICKS.get());
            if (cracked != null) {
                BlockState crackedState = Objects.requireNonNull(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CRACKED_BRICKS.get()).defaultBlockState());
                serverLevel.setBlock(cracked, crackedState, 3);
            }
        }
        setChanged();
    }

    private void loseFluid(int amount) {
        moltenTank.drain(amount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        setChanged();
    }

    private void reducePurity(double amount) {
        currentMeltPurity = (float) Math.max(0.1, currentMeltPurity - amount);
        setChanged();
    }

    private void validateStructure(Level level) {
        structureDirty = false;
        FoundryStructureResult result = FoundryStructureDetector.detect(Objects.requireNonNull(level), Objects.requireNonNull(worldPosition));
        if (!result.isValid()) {
            boolean wasFormed = formed;
            structure = null;
            formed = false;
            lastError = result.error();
            DevMod.LOGGER.warn("[Foundry] Structure validation failed at {}: {}", worldPosition, lastError != null ? lastError.getString() : "unknown error");
            updateActiveState(level, false);
            if (wasFormed) {
                syncToClients();
            }
            return;
        }
        DevMod.LOGGER.info("[Foundry] Structure validated successfully at {}", worldPosition);

        FoundryStructure detectedStructure = Objects.requireNonNull(result.structure());
        structure = detectedStructure;
        int maxSize = Math.max(detectedStructure.innerWidth(), detectedStructure.innerLength());
        if (!tierLimit.allowsSmelterySize(maxSize) || !tierLimit.allowsSmelteryHeight(detectedStructure.innerHeight())) {
            structure = null;
            formed = false;
            lastError = Component.translatable("devmod.foundry.error.tier_size", tierLimit.getDisplayName());
            updateActiveState(level, false);
            return;
        }
        formed = true;
        lastError = null;
        int capacity = detectedStructure.interiorVolume() * Config.FOUNDRY_CAPACITY_PER_BLOCK.get();
        moltenTank.setCapacity(capacity);
        linkComponents(level, detectedStructure);
        updateActiveState(level, false);
        syncToClients();
    }

    private void linkComponents(@Nonnull Level level, @Nonnull FoundryStructure structure) {
        for (BlockPos pos : structure.tankPositions()) {
            var be = level.getBlockEntity(Objects.requireNonNull(pos));
            if (be instanceof FoundryTankBlockEntity tank) {
                tank.setControllerPos(worldPosition);
            }
        }
        for (BlockPos pos : structure.drainPositions()) {
            var be = level.getBlockEntity(Objects.requireNonNull(pos));
            if (be instanceof FoundryDrainBlockEntity drain) {
                drain.setControllerPos(worldPosition);
            }
        }
        for (BlockPos pos : structure.ductPositions()) {
            var be = level.getBlockEntity(Objects.requireNonNull(pos));
            if (be instanceof FoundryChannelBlockEntity channel) {
                channel.setControllerPos(worldPosition);
            }
        }
        for (BlockPos pos : structure.chutePositions()) {
            var be = level.getBlockEntity(Objects.requireNonNull(pos));
            if (be instanceof FoundryChuteBlockEntity chute) {
                chute.setControllerPos(worldPosition);
            }
        }
    }

    private void consumeFuelItem(@Nonnull Level level) {
        ItemStack fuelStack = inventory.getItem(FoundryControllerMenu.SLOT_FUEL);
        if (fuelStack.isEmpty()) {
            return;
        }
        if (fuelStack.getItem() instanceof net.minecraft.world.item.BucketItem bucket && bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
            FluidStack toFill = new FluidStack(Objects.requireNonNull(bucket.content), 1000);
            FoundryFuelRecipe recipe = findFuelRecipe(level, toFill);
            if (recipe == null) {
                if (tickCounter % 100 == 0) {
                    DevMod.LOGGER.warn("[Foundry] No fuel recipe found for fluid: {}", toFill.getFluid());
                }
                return;
            }
            int filled = fuelTank.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            DevMod.LOGGER.info("[Foundry] Filled fuel tank with {} mB, total now: {}", filled, fuelTank.getFluidAmount());
            if (filled > 0 && !level.isClientSide) {
                inventory.setItem(FoundryControllerMenu.SLOT_FUEL, new ItemStack(Objects.requireNonNull(Items.BUCKET)));
            }
        }
    }

    private void processMelting(@Nonnull Level level, int temperature, float efficiency, float riskMultiplier) {
        ItemStack input = ItemStack.EMPTY;
        FoundryMeltingRecipe recipe = currentRecipe;
        if (recipe == null || activeSlot < 0 || !recipe.getIngredient().test(inventory.getItem(activeSlot))) {
            recipe = null;
            activeSlot = -1;
            resetMeltState();
            for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
                ItemStack candidate = inventory.getItem(i);
                if (candidate.isEmpty()) {
                    continue;
                }
                if (isFluxItem(candidate)) {
                    continue;
                }
                var found = findMeltingRecipe(level, candidate);
                if (found != null) {
                    recipe = found;
                    activeSlot = i;
                    input = candidate;
                    DevMod.LOGGER.info("[Foundry] Found recipe for {} at slot {}", candidate.getItem(), i);
                    break;
                } else if (tickCounter % 100 == 0) {
                    DevMod.LOGGER.warn("[Foundry] No recipe found for: {}", candidate.getItem());
                }
            }
            currentRecipe = recipe;
            if (recipe == null) {
                progress = 0;
                hadIncidentThisCycle = false; // Reset for new item
                return;
            }
        } else {
            input = inventory.getItem(activeSlot);
        }

        if (input.isEmpty() || recipe == null) {
            progress = 0;
            resetMeltState();
            return;
        }

        FluidStack output = recipe.getOutput();
        int byproductTotal = recipe.getByproductsTotal();
        if (moltenTank.getFree() < output.getAmount() + byproductTotal) {
            if (tickCounter % 100 == 0) {
                DevMod.LOGGER.warn("[Foundry] Tank full - free: {}, needed: {}", moltenTank.getFree(), output.getAmount() + byproductTotal);
            }
            return;
        }

        if (temperature < recipe.getTemperature()) {
            if (tickCounter % 100 == 0) {
                DevMod.LOGGER.warn("[Foundry] Temp too low: {} < {}", temperature, recipe.getTemperature());
            }
            return;
        }

        initMeltState(input, recipe);
        maxProgress = recipe.getTime();
        // Apply efficiency to progress (higher efficiency = faster)
        int progressGain = Math.max(1, Math.round(efficiency));
        progress += progressGain;

        // Debug log progress
        if (tickCounter % 40 == 0) {
            DevMod.LOGGER.info("[Foundry] Melting progress: {}/{}, item={}", progress, maxProgress, input.getItem());
        }

        if (progress >= maxProgress) {
            DevMod.LOGGER.info("[Foundry] Melting COMPLETE for {}, outputting {} mB", input.getItem(), recipe.getOutput().getAmount());
            FoundryMaterialDefinition meltedMaterial = FoundryMaterialRegistry.findMaterial(input).orElse(null);
            input.shrink(1);

            // Calculate quality based on conditions
            MaterialQuality quality = calculateMeltQuality(recipe, temperature);

            // Apply purity-based output scaling, then risk-based yield bonus.
            int baseOutputAmount = (int) (output.getAmount() * currentMeltPurity);
            int maxOutput = Math.max(0, moltenTank.getFree() - byproductTotal);
            int outputAmount = Math.min(Math.max(baseOutputAmount, Math.round(baseOutputAmount * riskMultiplier)), maxOutput);
            FluidStack actualOutput = new FluidStack(Objects.requireNonNull(output.getFluid()), outputAmount);
            FoundryFluidQuality.applyMoltenState(actualOutput, quality, currentMeltPurity, 0, temperature);

            moltenTank.fill(actualOutput, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            recipe.fillByproducts(moltenTank);
            Player operator = resolveLastOperator(level);
            if (operator != null) {
                FoundryPlayerProgress progressData = FoundryProgressAttachment.get(operator);
                progressData.recordMetalMelted(actualOutput.getAmount());
                if (meltedMaterial != null) {
                    progressData.unlockMaterial(meltedMaterial.id());
                }
                progressData.tryAdvanceTier();
                FoundryProgressAttachment.sync(operator);
            }

            // Reset state for next item
            progress = 0;
            hadIncidentThisCycle = false;
            resetMeltState();
            setChanged();
        }
    }

    private void initMeltState(@Nonnull ItemStack input, FoundryMeltingRecipe recipe) {
        if (meltInitialized) {
            return;
        }
        int optimalLow = recipe.getTemperature();
        int optimalHigh = optimalLow + 100;
        float basePurity = 1.0f;
        FoundryMaterialDefinition material = FoundryMaterialRegistry.findMaterial(input).orElse(null);
        if (material != null) {
            basePurity = material.getBasePurity();
            optimalLow = material.getOptimalLow(optimalLow);
            optimalHigh = material.getOptimalHigh(optimalHigh);
        }
        basePurity = Math.min(basePurity, FoundryItemQuality.getPurity(input));
        basePurity = clampPurity(basePurity - getInputImpurity(input, recipe));
        if (hadIncidentThisCycle && currentMeltPurity < 1.0f) {
            float incidentPenalty = 1.0f - currentMeltPurity;
            currentMeltPurity = clampPurity(basePurity - incidentPenalty);
        } else {
            currentMeltPurity = basePurity;
        }
        currentMeltPurity = applyFluxFromInventory(currentMeltPurity);
        meltOptimalLow = optimalLow;
        meltOptimalHigh = optimalHigh;
        meltInitialized = true;
    }

    private void resetMeltState() {
        currentMeltPurity = 1.0f;
        meltOptimalLow = 0;
        meltOptimalHigh = 0;
        meltInitialized = false;
    }

    private MaterialQuality calculateMeltQuality(FoundryMeltingRecipe recipe, int actualTemp) {
        int optimalLow = meltOptimalLow > 0 ? meltOptimalLow : recipe.getTemperature();
        int optimalHigh = meltOptimalHigh > 0 ? meltOptimalHigh : optimalLow + 100;

        float tempAccuracy = com.devmod.foundry.quality.QualityCalculator.calculateTempAccuracy(
            actualTemp, optimalLow, optimalHigh);

        return com.devmod.foundry.quality.QualityCalculator.calculateMeltingQuality(
            currentMeltPurity, tempAccuracy, hadIncidentThisCycle);
    }

    private float getInputImpurity(@Nonnull ItemStack input, FoundryMeltingRecipe recipe) {
        float impurity = recipe.getImpurity();
        if (input.is(Objects.requireNonNull(Tags.Items.ORES))) {
            impurity += ORE_IMPURITY;
        }
        if (input.is(Objects.requireNonNull(Tags.Items.RAW_MATERIALS))) {
            impurity += RAW_IMPURITY;
        }
        if (input.is(Objects.requireNonNull(FoundryTags.Items.ORE_RICH))) {
            impurity += ORE_RICH_BONUS;
        } else if (input.is(Objects.requireNonNull(FoundryTags.Items.ORE_POOR))) {
            impurity += ORE_POOR_PENALTY;
        }
        if (input.is(Objects.requireNonNull(FoundryTags.Items.RAW_RICH))) {
            impurity += RAW_RICH_BONUS;
        } else if (input.is(Objects.requireNonNull(FoundryTags.Items.RAW_POOR))) {
            impurity += RAW_POOR_PENALTY;
        }
        return Math.max(0.0f, impurity);
    }

    private float applyFluxFromInventory(float purity) {
        if (purity >= 1.0f || MAX_FLUX_PER_MELT <= 0) {
            return purity;
        }
        int used = 0;
        for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT && purity < 1.0f; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !isFluxItem(stack)) {
                continue;
            }
            float cap = getFluxPurityCap(stack);
            if (purity >= cap) {
                continue;
            }
            int remaining = MAX_FLUX_PER_MELT - used;
            if (remaining <= 0) {
                break;
            }
            float bonus = getFluxPurityBonus(stack);
            if (bonus <= 0.0f) {
                continue;
            }
            float toCap = Math.max(0.0f, cap - purity);
            int needed = (int) Math.ceil(toCap / bonus);
            int toUse = Math.min(stack.getCount(), Math.min(remaining, needed));
            if (toUse <= 0) {
                continue;
            }
            stack.shrink(toUse);
            used += toUse;
            purity = clampPurity(purity + (bonus * toUse));
            if (purity > cap) {
                purity = cap;
            }
            if (stack.isEmpty()) {
                inventory.setItem(i, Objects.requireNonNull(ItemStack.EMPTY));
            }
        }
        if (used > 0) {
            setChanged();
        }
        return purity;
    }

    private boolean isFluxItem(@Nonnull ItemStack stack) {
        return stack.is(Objects.requireNonNull(FoundryTags.Items.FOUNDRY_FLUX));
    }

    private float getFluxPurityBonus(ItemStack stack) {
        if (stack.getItem() instanceof FoundryFluxItem fluxItem) {
            return fluxItem.getPurityBonus();
        }
        return FLUX_PURITY_BONUS;
    }

    private float getFluxPurityCap(ItemStack stack) {
        if (stack.getItem() instanceof FoundryFluxItem fluxItem) {
            return fluxItem.getPurityCap();
        }
        return 1.0f;
    }

    private static float clampPurity(float value) {
        return Math.max(0.1f, Math.min(1.0f, value));
    }

    private void updateAlloyPreview(Level level, int temperature) {
        alloyPreviewFluidId = -1;
        alloyPreviewRatio = 0;
        Player operator = resolveLastOperator(level);
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FoundryAlloyingRecipe>> recipes = recipeManager.getAllRecipesFor(Objects.requireNonNull(FoundryRecipeTypes.ALLOYING.get()));
        for (RecipeHolder<FoundryAlloyingRecipe> holder : recipes) {
            FoundryAlloyingRecipe recipe = holder.value();
            if (!recipe.isDynamic()) {
                continue;
            }
            if (!canUseAlloyRecipe(recipe, operator)) {
                continue;
            }
            if (!recipe.canApply(moltenTank)) {
                continue;
            }
            if (temperature < recipe.getTemperature()) {
                continue;
            }
            int inputTotal = recipe.getInputAmount();
            int freeAfterDrain = moltenTank.getFree() + inputTotal;
            if (freeAfterDrain < recipe.getOutput().getAmount()) {
                continue;
            }
            FoundryAlloyingRecipe.AlloyRatioState state = recipe.getRatioState(moltenTank);
            if (state == null) {
                continue;
            }
            alloyPreviewFluidId = BuiltInRegistries.FLUID.getId(recipe.getOutput().getFluid());
            alloyPreviewRatio = Math.round(state.ratio() * 100f);
            return;
        }
    }

    private void processAlloying(Level level, int temperature, float efficiency, float riskMultiplier) {
        RecipeManager recipeManager = level.getRecipeManager();
        FoundryAlloyingRecipe recipe = currentAlloyRecipe;
        Player operator = resolveLastOperator(level);

        if (recipe == null && currentAlloyRecipeId != null) {
            recipe = recipeManager.byKey(currentAlloyRecipeId)
                .map(RecipeHolder::value)
                .filter(found -> found instanceof FoundryAlloyingRecipe)
                .map(found -> (FoundryAlloyingRecipe) found)
                .orElse(null);
            currentAlloyRecipe = recipe;
            if (recipe == null) {
                currentAlloyRecipeId = null;
            }
        }

        if (recipe != null && !canUseAlloyRecipe(recipe, operator)) {
            recipe = null;
            currentAlloyRecipe = null;
            currentAlloyRecipeId = null;
            alloyProgress = 0;
            alloyMaxProgress = 0;
        }

        if (recipe == null || !recipe.canApply(moltenTank)) {
            recipe = null;
            List<RecipeHolder<FoundryAlloyingRecipe>> recipes = recipeManager.getAllRecipesFor(Objects.requireNonNull(FoundryRecipeTypes.ALLOYING.get()));
            for (RecipeHolder<FoundryAlloyingRecipe> holder : recipes) {
                FoundryAlloyingRecipe candidate = holder.value();
                if (!canUseAlloyRecipe(candidate, operator)) {
                    continue;
                }
                if (!candidate.canApply(moltenTank)) {
                    continue;
                }
                if (temperature < candidate.getTemperature()) {
                    continue;
                }
                int inputTotal = candidate.getInputAmount();
                int freeAfterDrain = moltenTank.getFree() + inputTotal;
                if (freeAfterDrain < candidate.getOutput().getAmount()) {
                    continue;
                }
                recipe = candidate;
                currentAlloyRecipeId = holder.id();
                break;
            }
            currentAlloyRecipe = recipe;
            alloyProgress = 0;
            alloyMaxProgress = 0;
            if (recipe == null) {
                currentAlloyRecipeId = null;
                return;
            }
        }

        if (temperature < recipe.getTemperature()) {
            return;
        }

        int inputTotal = recipe.getInputAmount();
        int freeAfterDrain = moltenTank.getFree() + inputTotal;
        if (freeAfterDrain < recipe.getOutput().getAmount()) {
            return;
        }

        // Calculate alloying bonuses from materials in the recipe
        FoundryMaterialAlloying alloyingBonus = calculateAlloyingBonuses(recipe.getComponentFluids());

        alloyMaxProgress = recipe.getTime();
        // Apply speed multiplier from material alloying bonuses
        float effectiveSpeed = efficiency * (1f / alloyingBonus.speedMultiplier());
        int progressGain = Math.max(1, Math.round(effectiveSpeed));
        alloyProgress += progressGain;

        if (alloyProgress >= alloyMaxProgress) {
            MaterialQuality outputQuality = MaterialQuality.MASTERWORK;
            float outputPurity = 1.0f;
            for (Fluid fluid : recipe.getComponentFluids()) {
                outputQuality = selectLowerQuality(outputQuality, moltenTank.getLowestQualityForFluid(fluid));
                outputPurity = Math.min(outputPurity, moltenTank.getLowestPurityForFluid(fluid));
            }
            FoundryAlloyingRecipe.AlloyRatioState ratioState = recipe.getRatioState(moltenTank);
            if (ratioState != null) {
                FoundryAlloyingRecipe.RatioTier tier = ratioState.tier();
                outputPurity = clampPurity(outputPurity + tier.purityBonus());
                outputQuality = applyQualityShift(outputQuality, tier.qualityShift());
            }
            // Apply material alloying bonuses
            if (alloyingBonus.hasBonus()) {
                outputPurity = clampPurity(outputPurity + alloyingBonus.purityBonus());
            }
            int drained = recipe.consumeInputs(moltenTank);
            if (drained <= 0) {
                alloyProgress = 0;
                alloyMaxProgress = 0;
                currentAlloyRecipe = null;
                setChanged();
                return;
            }
            FluidStack output = recipe.getOutput();
            int baseOutputAmount = output.getAmount();
            if (recipe.isDynamic()) {
                baseOutputAmount = Math.min(baseOutputAmount, drained);
            }
            FoundryPlayerProgress progressData = operator != null ? FoundryProgressAttachment.get(operator) : null;
            float yieldMultiplier = riskMultiplier;
            if (progressData != null) {
                FoundrySpecialization specialization = FoundrySpecialization.fromId(progressData.getSpecialization());
                if (specialization != null) {
                    yieldMultiplier *= specialization.getAlloyYieldMultiplier();
                }
            }
            // Apply material alloying efficiency bonus
            if (alloyingBonus.hasBonus()) {
                yieldMultiplier *= alloyingBonus.efficiency();
            }
            int outputAmount = Math.min(Math.max(baseOutputAmount, Math.round(baseOutputAmount * yieldMultiplier)), freeAfterDrain);
            output.setAmount(outputAmount);
            FoundryFluidQuality.applyMoltenState(output, outputQuality, outputPurity, 0, temperature);
            moltenTank.fill(output, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (progressData != null) {
                progressData.recordAlloyCreated();
                progressData.tryAdvanceTier();
                if (operator != null) {
                    FoundryProgressAttachment.sync(operator);
                }
            }
            alloyProgress = 0;
            alloyMaxProgress = 0;
            currentAlloyRecipe = null;
            setChanged();
        }
    }

    private static MaterialQuality selectLowerQuality(MaterialQuality a, MaterialQuality b) {
        return a.getTier() <= b.getTier() ? a : b;
    }

    private static MaterialQuality applyQualityShift(MaterialQuality quality, int shift) {
        MaterialQuality result = quality;
        if (shift > 0) {
            for (int i = 0; i < shift; i++) {
                result = result.upgrade();
            }
        } else if (shift < 0) {
            for (int i = 0; i < -shift; i++) {
                result = result.downgrade();
            }
        }
        return result;
    }

    private int findRequiredTemperature(@Nonnull Level level) {
        int required = Integer.MAX_VALUE;
        tierBlocked = false;
        Player operator = resolveLastOperator(level);
        for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate.isEmpty()) {
                continue;
            }
            if (isFluxItem(candidate)) {
                continue;
            }
            FoundryMeltingRecipe recipe = findMeltingRecipe(level, candidate);
            if (recipe == null) {
                continue;
            }
            if (!tierLimit.allowsTemperature(recipe.getTemperature())) {
                tierBlocked = true;
                continue;
            }
            int needed = recipe.getOutput().getAmount() + recipe.getByproductsTotal();
            if (moltenTank.getFree() < needed) {
                continue;
            }
            required = Math.min(required, recipe.getTemperature());
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FoundryAlloyingRecipe>> recipes = recipeManager.getAllRecipesFor(Objects.requireNonNull(FoundryRecipeTypes.ALLOYING.get()));
        for (RecipeHolder<FoundryAlloyingRecipe> holder : recipes) {
            FoundryAlloyingRecipe alloyRecipe = holder.value();
            if (!canUseAlloyRecipe(alloyRecipe, operator)) {
                continue;
            }
            if (!alloyRecipe.canApply(moltenTank)) {
                continue;
            }
            if (!tierLimit.allowsTemperature(alloyRecipe.getTemperature())) {
                tierBlocked = true;
                continue;
            }
            int inputTotal = alloyRecipe.getInputAmount();
            int freeAfterDrain = moltenTank.getFree() + inputTotal;
            if (freeAfterDrain < alloyRecipe.getOutput().getAmount()) {
                continue;
            }
            required = Math.min(required, alloyRecipe.getTemperature());
        }

        return required == Integer.MAX_VALUE ? -1 : required;
    }

    private boolean canUseAlloyRecipe(FoundryAlloyingRecipe recipe, @Nullable Player operator) {
        ResourceLocation required = recipe.getRequiredSpecialization();
        if (required == null) {
            return true;
        }
        if (operator == null) {
            return false;
        }
        FoundryPlayerProgress progress = FoundryProgressAttachment.get(operator);
        return required.equals(progress.getSpecialization());
    }

    private void ensureFuel(@Nonnull Level level, int requiredTemp) {
        if (fuelTicks > 0 && fuelTemperature >= requiredTemp) {
            return;
        }
        FluidStack available = fuelTank.getFluid();
        if (available.isEmpty()) {
            fuelTicks = 0;
            fuelTicksMax = 0;
            fuelTemperature = 0;
            return;
        }
        FoundryFuelRecipe recipe = findFuelRecipe(level, available);
        if (recipe == null || recipe.getTemperature() < requiredTemp) {
            fuelTicks = 0;
            fuelTicksMax = 0;
            fuelTemperature = 0;
            return;
        }
        FluidStack required = recipe.getFluid();
        if (available.getAmount() < required.getAmount()) {
            return;
        }
        fuelTank.drain(required.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        fuelTicks = recipe.getBurnTime();
        fuelTicksMax = fuelTicks;
        fuelTemperature = recipe.getTemperature();
        setChanged();
    }

    private int getCurrentTemperature() {
        return fuelTicks > 0 ? fuelTemperature : 0;
    }

    @Nullable
    private FoundryMeltingRecipe findMeltingRecipe(@Nonnull Level level, @Nonnull ItemStack stack) {
        RecipeManager recipeManager = level.getRecipeManager();
        SingleRecipeInput input = new SingleRecipeInput(Objects.requireNonNull(stack));
        return recipeManager.getRecipeFor(Objects.requireNonNull(FoundryRecipeTypes.MELTING.get()), input, level)
            .map(RecipeHolder::value)
            .orElse(null);
    }

    @Nullable
    private FoundryFuelRecipe findFuelRecipe(@Nonnull Level level, @Nonnull FluidStack stack) {
        RecipeManager recipeManager = level.getRecipeManager();

        // Manual iteration to find matching fuel recipe
        var allFuelRecipes = recipeManager.getAllRecipesFor(Objects.requireNonNull(FoundryRecipeTypes.FUEL.get()));
        for (var holder : allFuelRecipes) {
            FoundryFuelRecipe recipe = holder.value();
            if (recipe.getFluid().getFluid() == stack.getFluid()) {
                DevMod.LOGGER.info("[Foundry] Found matching fuel recipe: {} for {}", holder.id(), stack.getFluid());
                return recipe;
            }
        }

        if (tickCounter % 200 == 1) {
            DevMod.LOGGER.warn("[Foundry] No fuel recipe found for {} (checked {} recipes)", stack.getFluid(), allFuelRecipes.size());
        }
        return null;
    }

    private void updateActiveState(@Nonnull Level level, boolean active) {
        BlockPos pos = Objects.requireNonNull(worldPosition);
        BlockState state = level.getBlockState(pos);
        boolean needsUpdate = false;
        BlockState newState = state;

        // Update ACTIVE property
        if (state.hasProperty(Objects.requireNonNull(FoundryControllerBlock.ACTIVE)) && state.getValue(Objects.requireNonNull(FoundryControllerBlock.ACTIVE)) != active) {
            newState = Objects.requireNonNull(newState.setValue(Objects.requireNonNull(FoundryControllerBlock.ACTIVE), active));
            needsUpdate = true;
        }

        // Update IN_STRUCTURE property
        if (state.hasProperty(Objects.requireNonNull(FoundryControllerBlock.IN_STRUCTURE)) && state.getValue(Objects.requireNonNull(FoundryControllerBlock.IN_STRUCTURE)) != formed) {
            newState = Objects.requireNonNull(newState.setValue(Objects.requireNonNull(FoundryControllerBlock.IN_STRUCTURE), formed));
            needsUpdate = true;
        }

        if (needsUpdate) {
            level.setBlock(pos, newState, 3);
        }
    }

    /**
     * Calculates combined alloying bonuses from materials present in the tank.
     * Bonuses are averaged across all fluids with alloying properties.
     */
    private FoundryMaterialAlloying calculateAlloyingBonuses(List<Fluid> componentFluids) {
        float totalRatioTolerance = 0f;
        float totalPurityBonus = 0f;
        float totalEfficiency = 0f;
        float totalSpeedMultiplier = 0f;
        int count = 0;

        for (Fluid fluid : componentFluids) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null) continue;

            // Try to find a material that produces this fluid (convention: molten_<material>)
            String fluidPath = fluidId.getPath();
            String materialName = fluidPath.startsWith("molten_") ? fluidPath.substring(7) : fluidPath;
            ResourceLocation materialId = ResourceLocation.fromNamespaceAndPath(fluidId.getNamespace(), materialName);

            FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
            if (material == null) {
                // Try devmod namespace
                materialId = ResourceLocation.fromNamespaceAndPath("devmod", materialName);
                material = FoundryMaterialRegistry.get(materialId);
            }

            if (material != null && material.alloying().hasBonus()) {
                FoundryMaterialAlloying alloying = material.alloying();
                totalRatioTolerance += alloying.ratioTolerance();
                totalPurityBonus += alloying.purityBonus();
                totalEfficiency += alloying.efficiency();
                totalSpeedMultiplier += alloying.speedMultiplier();
                count++;
            }
        }

        if (count == 0) {
            return FoundryMaterialAlloying.NONE;
        }

        return new FoundryMaterialAlloying(
            totalRatioTolerance / count,
            totalPurityBonus / count,
            totalEfficiency / count,
            totalSpeedMultiplier / count
        );
    }

    public void markStructureDirty() {
        structureDirty = true;
    }

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public FoundryStructure getStructure() {
        return structure;
    }

    public FoundryFluidTank getMoltenTank() {
        return moltenTank;
    }

    @Nullable
    public Component getLastError() {
        return lastError;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public int getFuelTicks() {
        return fuelTicks;
    }

    public int getFuelTicksMax() {
        return fuelTicksMax;
    }

    public int getFuelTemperature() {
        return fuelTemperature;
    }

    public int getMoltenAmount() {
        return moltenTank.getUsed();
    }

    public int getMoltenCapacity() {
        return moltenTank.getCapacity();
    }

    public FluidStack getPrimaryMoltenFluid() {
        List<FluidStack> fluids = moltenTank.getFluids();
        if (fluids.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return fluids.get(0).copy();
    }

    public int getMoltenAmountForFluid(Fluid fluid) {
        return moltenTank.getAmountForFluid(fluid);
    }

    /**
     * Gets the registry ID of the fluid at the specified index in the tank.
     * Used for GUI fluid composition display.
     * @param index The fluid index (0-3)
     * @return The fluid registry ID, or -1 if no fluid at that index
     */
    public int getFluidIdAt(int index) {
        List<FluidStack> fluids = moltenTank.getFluids();
        if (index < 0 || index >= fluids.size()) {
            return -1;
        }
        return BuiltInRegistries.FLUID.getId(fluids.get(index).getFluid());
    }

    /**
     * Gets the amount of fluid at the specified index in the tank.
     * Used for GUI fluid composition display.
     * @param index The fluid index (0-3)
     * @return The fluid amount in mB, or 0 if no fluid at that index
     */
    public int getFluidAmountAt(int index) {
        List<FluidStack> fluids = moltenTank.getFluids();
        if (index < 0 || index >= fluids.size()) {
            return 0;
        }
        return fluids.get(index).getAmount();
    }

    /**
     * Gets the number of distinct fluid types currently in the tank.
     * Used for GUI fluid composition display.
     * @return The number of fluid types (0-n)
     */
    public int getFluidTypeCount() {
        return moltenTank.getFluids().size();
    }

    public int getFuelAmount() {
        return fuelTank.getFluidAmount();
    }

    public int getFuelCapacity() {
        return fuelTank.getCapacity();
    }

    public FluidStack getFuelFluid() {
        FluidStack stack = fuelTank.getFluid();
        return stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
    }

    public int getMoltenQualityTier() {
        return moltenTank.getLowestQuality().getTier();
    }

    public int getAlloyPreviewFluidId() {
        return alloyPreviewFluidId;
    }

    public int getAlloyPreviewRatio() {
        return alloyPreviewRatio;
    }

    public int fillMolten(@Nonnull FluidStack stack, boolean execute) {
        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action = execute
            ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
            : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        int filled = moltenTank.fill(Objects.requireNonNull(stack), action);
        if (execute && filled > 0) {
            setChanged();
        }
        return filled;
    }

    @Nonnull
    public FluidStack drainMolten(int amount, boolean execute) {
        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action = execute
            ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
            : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        FluidStack drained = moltenTank.drain(amount, action);
        if (execute && !drained.isEmpty()) {
            setChanged();
        }
        return drained;
    }

    @Nonnull
    public FluidStack drainMolten(@Nonnull FluidStack request, boolean execute) {
        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action = execute
            ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
            : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        FluidStack drained = moltenTank.drain(Objects.requireNonNull(request), action);
        if (execute && !drained.isEmpty()) {
            setChanged();
        }
        return drained;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    // New system getters

    public ThermalManager getThermalManager() {
        return thermalManager;
    }

    public RiskManager getRiskManager() {
        return riskManager;
    }

    public RiskLevel getCurrentRiskLevel() {
        return riskManager.getCurrentLevel();
    }

    public float getStructureHeat() {
        return thermalManager.getStructureHeat();
    }

    public float getThermalStressPercent() {
        return thermalManager.getStressPercent();
    }

    public int getStructureDamage() {
        return structureDamage;
    }

    public float getCurrentPurity() {
        return currentMeltPurity;
    }

    public float getDisplayedPurity() {
        if (!moltenTank.isEmpty()) {
            return moltenTank.getLowestPurity();
        }
        return currentMeltPurity;
    }

    public float getOxidationPercent() {
        return moltenTank.getHighestOxidationPercent();
    }

    public void repairStructure() {
        if (structureDamage <= 0) {
            return;
        }
        FoundryStructure currentStructure = structure;
        if (level instanceof ServerLevel serverLevel && currentStructure != null) {
            BlockPos cracked = findRandomStructureBlock(serverLevel, currentStructure, FoundryBlocks.FOUNDRY_CRACKED_BRICKS.get());
            if (cracked != null) {
                BlockState repairedState = Objects.requireNonNull(Objects.requireNonNull(FoundryBlocks.FOUNDRY_BRICKS.get()).defaultBlockState());
                serverLevel.setBlock(cracked, repairedState, 3);
            }
        }
        structureDamage = Math.max(0, structureDamage - 1);
        thermalManager.resetStress();
        riskManager.resetRisk();
        setChanged();
    }

    @Nullable
    private static BlockPos findRandomStructureBlock(
        @Nonnull Level level,
        @Nonnull FoundryStructure structure,
        @Nullable net.minecraft.world.level.block.Block target
    ) {
        if (target == null) {
            return null;
        }
        BlockPos min = structure.minPos();
        BlockPos max = structure.maxPos();
        int minX = min.getX();
        int maxX = max.getX();
        int minY = min.getY();
        int maxY = max.getY();
        int minZ = min.getZ();
        int maxZ = max.getZ();
        List<BlockPos> candidates = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean boundary = y == minY || x == minX || x == maxX || z == minZ || z == maxZ;
                    if (!boundary) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(Objects.requireNonNull(target))) {
                        candidates.add(pos);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    public void applyTierLimit(FoundryTier tier) {
        if (tier.getLevel() > tierLimit.getLevel()) {
            tierLimit = tier;
            tierBlocked = false;
            structureDirty = true;
            Level level = getLevel();
            if (level != null && !level.isClientSide) {
                validateStructure(level);
            }
            setChanged();
        }
    }

    public void setLastOperator(@Nullable Player player) {
        if (player != null) {
            lastOperator = player.getUUID();
        }
    }

    @Nullable
    private Player resolveLastOperator(Level level) {
        UUID operatorId = lastOperator;
        if (operatorId == null) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getPlayerByUUID(Objects.requireNonNull(operatorId));
    }

    public FoundryTier getTierLimit() {
        return tierLimit;
    }

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Objects.requireNonNull(Component.translatable("block.devmod.foundry_controller"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryControllerMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, Objects.requireNonNull(stack.save(registries)));
            }
        }
        tag.put(TAG_INVENTORY, invTag);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_MAX_PROGRESS, maxProgress);
        tag.putBoolean(TAG_FORMED, formed);
        tag.put(TAG_MOLTEN, Objects.requireNonNull(moltenTank.save(registries)));
        CompoundTag fuelTag = new CompoundTag();
        fuelTank.writeToNBT(registries, fuelTag);
        tag.put(TAG_FUEL, fuelTag);
        tag.putInt(TAG_FUEL_TICKS, fuelTicks);
        tag.putInt(TAG_FUEL_TICKS_MAX, fuelTicksMax);
        tag.putInt(TAG_FUEL_TEMP, fuelTemperature);

        // Save new systems
        tag.put(TAG_THERMAL, Objects.requireNonNull(thermalManager.save()));
        tag.put(TAG_RISK, Objects.requireNonNull(riskManager.save()));
        tag.putInt(TAG_STRUCTURE_DAMAGE, structureDamage);
        tag.putFloat(TAG_CURRENT_PURITY, currentMeltPurity);
        tag.putBoolean(TAG_HAD_INCIDENT, hadIncidentThisCycle);
        tag.putInt(TAG_MELT_OPTIMAL_LOW, meltOptimalLow);
        tag.putInt(TAG_MELT_OPTIMAL_HIGH, meltOptimalHigh);
        tag.putBoolean(TAG_MELT_INITIALIZED, meltInitialized);
        tag.putInt(TAG_ALLOY_PROGRESS, alloyProgress);
        tag.putInt(TAG_ALLOY_MAX_PROGRESS, alloyMaxProgress);
        tag.putString(TAG_TIER_LIMIT, Objects.requireNonNull(tierLimit.getName()));
        ResourceLocation alloyRecipeId = currentAlloyRecipeId;
        if (alloyRecipeId != null) {
            tag.putString(TAG_ALLOY_RECIPE, Objects.requireNonNull(alloyRecipeId.toString()));
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, Objects.requireNonNull(ItemStack.parse(registries, Objects.requireNonNull(invTag.getCompound(key))).orElse(ItemStack.EMPTY)));
                }
            }
        }
        progress = tag.getInt(TAG_PROGRESS);
        maxProgress = tag.getInt(TAG_MAX_PROGRESS);
        formed = tag.getBoolean(TAG_FORMED);
        if (tag.contains(TAG_MOLTEN)) {
            moltenTank.load(registries, Objects.requireNonNull(tag.getCompound(TAG_MOLTEN)));
        }
        if (tag.contains(TAG_FUEL)) {
            fuelTank.readFromNBT(registries, Objects.requireNonNull(tag.getCompound(TAG_FUEL)));
        }
        fuelTicks = tag.getInt(TAG_FUEL_TICKS);
        fuelTicksMax = tag.getInt(TAG_FUEL_TICKS_MAX);
        fuelTemperature = tag.getInt(TAG_FUEL_TEMP);

        // Load new systems
        if (tag.contains(TAG_THERMAL)) {
            thermalManager.load(tag.getCompound(TAG_THERMAL));
        }
        if (tag.contains(TAG_RISK)) {
            riskManager.load(tag.getCompound(TAG_RISK));
        }
        structureDamage = tag.getInt(TAG_STRUCTURE_DAMAGE);
        currentMeltPurity = tag.contains(TAG_CURRENT_PURITY) ? tag.getFloat(TAG_CURRENT_PURITY) : 1.0f;
        hadIncidentThisCycle = tag.getBoolean(TAG_HAD_INCIDENT);
        meltOptimalLow = tag.contains(TAG_MELT_OPTIMAL_LOW) ? tag.getInt(TAG_MELT_OPTIMAL_LOW) : 0;
        meltOptimalHigh = tag.contains(TAG_MELT_OPTIMAL_HIGH) ? tag.getInt(TAG_MELT_OPTIMAL_HIGH) : 0;
        meltInitialized = tag.getBoolean(TAG_MELT_INITIALIZED);
        alloyProgress = tag.getInt(TAG_ALLOY_PROGRESS);
        alloyMaxProgress = tag.getInt(TAG_ALLOY_MAX_PROGRESS);
        currentAlloyRecipe = null;
        currentAlloyRecipeId = tag.contains(TAG_ALLOY_RECIPE)
            ? ResourceLocation.tryParse(Objects.requireNonNull(tag.getString(TAG_ALLOY_RECIPE)))
            : null;
        tierLimit = tag.contains(TAG_TIER_LIMIT)
            ? FoundryTier.fromName(Objects.requireNonNull(tag.getString(TAG_TIER_LIMIT)))
            : FoundryTier.PRIMITIVE;

        structureDirty = true;
    }

    // ==================== CLIENT SYNC ====================

    private static final String TAG_CLIENT_MIN_X = "ClientMinX";
    private static final String TAG_CLIENT_MIN_Y = "ClientMinY";
    private static final String TAG_CLIENT_MIN_Z = "ClientMinZ";
    private static final String TAG_CLIENT_MAX_X = "ClientMaxX";
    private static final String TAG_CLIENT_MAX_Y = "ClientMaxY";
    private static final String TAG_CLIENT_MAX_Z = "ClientMaxZ";

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean(TAG_FORMED, formed);

        // Save structure bounds for client rendering
        FoundryStructure currentStructure = structure;
        if (currentStructure != null) {
            BlockPos min = currentStructure.minPos();
            BlockPos max = currentStructure.maxPos();
            tag.putInt(TAG_CLIENT_MIN_X, min.getX());
            tag.putInt(TAG_CLIENT_MIN_Y, min.getY());
            tag.putInt(TAG_CLIENT_MIN_Z, min.getZ());
            tag.putInt(TAG_CLIENT_MAX_X, max.getX());
            tag.putInt(TAG_CLIENT_MAX_Y, max.getY());
            tag.putInt(TAG_CLIENT_MAX_Z, max.getZ());
        }

        // Save molten tank data for client rendering
        tag.put(TAG_MOLTEN, Objects.requireNonNull(moltenTank.save(registries)));
        System.out.println("[Foundry-Server] getUpdateTag() called, tankUsed=" + moltenTank.getUsed() + ", formed=" + formed);

        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
        @Nonnull net.minecraft.network.Connection connection,
        @Nonnull ClientboundBlockEntityDataPacket packet,
        @Nonnull HolderLookup.Provider registries
    ) {
        System.out.println("[Foundry-Client] onDataPacket received at " + worldPosition);
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        formed = tag.getBoolean(TAG_FORMED);
        System.out.println("[Foundry-Client] handleUpdateTag called, formed=" + formed + ", hasMoltenTag=" + tag.contains(TAG_MOLTEN));

        // Load structure bounds for client rendering
        if (tag.contains(TAG_CLIENT_MIN_X)) {
            clientMinPos = new BlockPos(
                tag.getInt(TAG_CLIENT_MIN_X),
                tag.getInt(TAG_CLIENT_MIN_Y),
                tag.getInt(TAG_CLIENT_MIN_Z)
            );
            clientMaxPos = new BlockPos(
                tag.getInt(TAG_CLIENT_MAX_X),
                tag.getInt(TAG_CLIENT_MAX_Y),
                tag.getInt(TAG_CLIENT_MAX_Z)
            );
            System.out.println("[Foundry-Client] Structure bounds loaded: min=" + clientMinPos + ", max=" + clientMaxPos);
        } else {
            clientMinPos = null;
            clientMaxPos = null;
        }

        // Load molten tank data for client rendering
        if (tag.contains(TAG_MOLTEN)) {
            int beforeUsed = moltenTank.getUsed();
            moltenTank.load(registries, Objects.requireNonNull(tag.getCompound(TAG_MOLTEN)));
            int afterUsed = moltenTank.getUsed();
            System.out.println("[Foundry-Client] Tank loaded: before=" + beforeUsed + ", after=" + afterUsed + ", capacity=" + moltenTank.getCapacity());
        }
    }

    /**
     * Sends a block update to sync client data.
     * Call this when structure or tank data changes that affects rendering.
     */
    public void syncToClients() {
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            System.out.println("[Foundry-Server] syncToClients() called, tankUsed=" + moltenTank.getUsed() + ", pos=" + worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Gets the structure bounds for client-side rendering.
     * Uses cached client data if on client, or actual structure if on server.
     */
    @Nullable
    public BlockPos getClientMinPos() {
        if (structure != null) {
            return structure.minPos();
        }
        return clientMinPos;
    }

    /**
     * Gets the structure bounds for client-side rendering.
     * Uses cached client data if on client, or actual structure if on server.
     */
    @Nullable
    public BlockPos getClientMaxPos() {
        if (structure != null) {
            return structure.maxPos();
        }
        return clientMaxPos;
    }

    /**
     * Check if the foundry can melt entities.
     * Requires: formed structure, fuel burning, and molten metal in tank.
     */
    public boolean canMeltEntities() {
        return formed && fuelTicks > 0 && !moltenTank.isEmpty();
    }

    /**
     * Get the interior bounds of the foundry for entity detection.
     * @return AABB of the interior space, or null if not formed
     */
    @Nullable
    public net.minecraft.world.phys.AABB getInteriorBounds() {
        if (structure == null) {
            return null;
        }
        return structure.getInteriorBounds();
    }
}
