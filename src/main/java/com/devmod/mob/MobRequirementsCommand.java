package com.devmod.mob;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Command handler for MobRequirements system.
 * Provides commands for reloading JSON overrides, listing mobs, and querying requirements.
 *
 * Commands:
 * - /devmod mobrequirements reload - Reloads JSON overrides from config
 * - /devmod mobrequirements list - Lists all mobs with overrides
 * - /devmod mobrequirements info <mobId> - Shows requirements for a specific mob
 * - /devmod mobrequirements cache - Shows cache statistics
 * - /devmod mobrequirements generate - Generates example JSON files
 */
public class MobRequirementsCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobRequirementsCommand.class);

    private MobRequirementsCommand() {} // Utility class

    /**
     * Registers the command with the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mobrequirements")
                    .then(Commands.literal("reload")
                        .executes(MobRequirementsCommand::reload)
                        .then(Commands.argument("mobId", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(MobRequirementsCommand::suggestMobIds)
                            .executes(MobRequirementsCommand::reloadSingle)))
                    .then(Commands.literal("list")
                        .executes(MobRequirementsCommand::listOverrides))
                    .then(Commands.literal("info")
                        .then(Commands.argument("mobId", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(MobRequirementsCommand::suggestMobIds)
                            .executes(MobRequirementsCommand::showInfo)))
                    .then(Commands.literal("test")
                        .then(Commands.argument("mobId", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(MobRequirementsCommand::suggestMobIds)
                            .executes(MobRequirementsCommand::testSpawn)))
                    .then(Commands.literal("cache")
                        .executes(MobRequirementsCommand::showCacheStats))
                    .then(Commands.literal("generate")
                        .executes(MobRequirementsCommand::generateExamples)))
        );
    }

    /**
     * Provides tab completion for mob IDs.
     */
    private static CompletableFuture<Suggestions> suggestMobIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Include all registered entity types
        return SharedSuggestionProvider.suggest(
            Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(ResourceLocation::toString)),
            Objects.requireNonNull(builder)
        );
    }

    /**
     * Reloads JSON overrides from config directory.
     */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;

        if (!registry.isInitialized()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Registry not initialized. Wait for server start.")));
            return 0;
        }

        try {
            int previousCount = registry.getOverrides().size();
            registry.reload();
            int newCount = registry.getOverrides().size();

            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("[MobRequirements] Reloaded! %d overrides loaded (was %d).",
                    newCount, previousCount)))), true);

            LOGGER.info("MobRequirements reloaded via command: {} overrides", newCount);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to reload MobRequirements", e);
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Reload failed: " + e.getMessage())));
            return 0;
        }
    }

    /**
     * Reloads a single mob's JSON override.
     */
    private static int reloadSingle(CommandContext<CommandSourceStack> ctx) {
        String mobIdStr = StringArgumentType.getString(ctx, "mobId");
        ResourceLocation mobId;

        try {
            mobId = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(mobIdStr)));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Invalid mob ID: " + mobIdStr)));
            return 0;
        }

        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;

        if (!registry.isInitialized()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Registry not initialized. Wait for server start.")));
            return 0;
        }

        boolean hadOverride = registry.hasOverride(mobId);
        boolean reloaded = registry.reloadSingle(mobId);

        if (reloaded) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("[MobRequirements] Reloaded override for: %s", mobId)))), true);
        } else if (hadOverride) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("[MobRequirements] Override removed for: %s (file deleted)", mobId)))), true);
        } else {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("[MobRequirements] No override file for: %s (cache cleared)", mobId)))), false);
        }

        return 1;
    }

    /**
     * Tests spawn conditions for a mob at the player's current location.
     */
    private static int testSpawn(CommandContext<CommandSourceStack> ctx) {
        String mobIdStr = StringArgumentType.getString(ctx, "mobId");
        ResourceLocation mobId;

        try {
            mobId = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(mobIdStr)));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Invalid mob ID: " + mobIdStr)));
            return 0;
        }

        // Check if entity type exists
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(Objects.requireNonNull(mobId))) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Unknown mob: " + mobId)));
            return 0;
        }

        // Get player position
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] This command must be run by a player.")));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = Objects.requireNonNull(player.blockPosition());
        BlockPos below = Objects.requireNonNull(pos.below());

        // Get requirements
        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;
        MobRequirements reqs = registry.get(mobId);

        // Get current conditions
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int combinedLight = level.getMaxLocalRawBrightness(pos);
        BlockState floorBlock = level.getBlockState(below);
        long dayTime = level.getDayTime() % 24000;
        String timeOfDay = dayTime < 12000 ? "DAY" : "NIGHT";

        // Header
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("=== Spawn Test: %s ===", mobId)))), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Position: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ())))), false);

        // Light check
        var light = reqs.light();
        boolean lightOk = combinedLight >= light.minLight() && combinedLight <= light.maxLight();
        String lightStatus = lightOk ? "✓" : "✗";
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("%s Light: %d (sky=%d, block=%d) | Required: %d-%d",
                lightStatus, combinedLight, skyLight, blockLight,
                light.minLight(), light.maxLight())))), false);

        // Time check
        var timeReq = reqs.time();
        boolean timeOk = timeReq == MobRequirements.TimeRequirement.ANY ||
            (timeReq == MobRequirements.TimeRequirement.DAY && dayTime < 12000) ||
            (timeReq == MobRequirements.TimeRequirement.NIGHT && dayTime >= 12000);
        String timeStatus = timeOk ? "✓" : "✗";
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("%s Time: %s (tick %d) | Required: %s",
                timeStatus, timeOfDay, dayTime, timeReq.name())))), false);

        // Floor check
        var floor = reqs.floor();
        boolean floorOk = true;
        if (!floor.validBlocks().isEmpty()) {
            ResourceLocation blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(floorBlock.getBlock())));
            floorOk = floor.validBlocks().contains(blockId);
        }
        boolean isSolidFloor = floorBlock.isFaceSturdy(level, below, Direction.UP);
        if (floor.requiresSolid() && !isSolidFloor) {
            floorOk = false;
        }
        String floorStatus = floorOk ? "✓" : "✗";
        ResourceLocation floorBlockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(floorBlock.getBlock())));
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("%s Floor: %s (solid=%s)",
                floorStatus, floorBlockId, isSolidFloor)))), false);

        // Space check
        var space = reqs.space();
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);
        float width = entityType != null ? entityType.getWidth() : space.width();
        float height = entityType != null ? entityType.getHeight() : space.height();
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("  Space needed: %.1fx%.1f (clearance %.1f)",
                width, height, space.clearanceAbove())))), false);

        // Overall result
        boolean canSpawn = lightOk && timeOk && floorOk;
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("=== Result: %s ===",
                canSpawn ? "CAN SPAWN ✓" : "CANNOT SPAWN ✗")))), false);

        return 1;
    }

    /**
     * Lists all mobs with JSON overrides.
     */
    private static int listOverrides(CommandContext<CommandSourceStack> ctx) {
        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;

        var overrides = registry.getOverrides();
        if (overrides.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "[MobRequirements] No JSON overrides loaded.")), false);
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "  Use '/devmod mobrequirements generate' to create examples.")), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("[MobRequirements] %d override(s) loaded:", overrides.size())))), false);

        for (var entry : overrides.entrySet()) {
            ResourceLocation mobId = entry.getKey();
            MobRequirements reqs = entry.getValue();
            String source = reqs.source().name();

            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("  - %s [%s]", mobId.toString(), source)))), false);
        }

        return 1;
    }

    /**
     * Shows detailed requirements for a specific mob.
     */
    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        String mobIdStr = StringArgumentType.getString(ctx, "mobId");
        ResourceLocation mobId;

        try {
            mobId = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(mobIdStr)));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Invalid mob ID: " + mobIdStr)));
            return 0;
        }

        // Check if the entity type exists
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(Objects.requireNonNull(mobId))) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Unknown mob: " + mobId)));
            return 0;
        }

        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;
        MobRequirements reqs = registry.get(mobId);
        boolean hasOverride = registry.hasOverride(mobId);

        // Header
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("=== MobRequirements: %s ===", mobId)))), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Source: %s | Confidence: %.0f%% | Override: %s",
                reqs.source().name(),
                reqs.confidence() * 100,
                hasOverride ? "YES" : "no")))), false);

        // Biome requirements
        var biome = reqs.biome();
        if (biome.preferredBiome().isPresent() || !biome.validBiomes().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("Biome: preferred=%s, valid=%d biomes",
                    biome.preferredBiome().map(ResourceLocation::toString).orElse("none"),
                    biome.validBiomes().size())))), false);
        }

        // Light requirements
        var light = reqs.light();
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Light: min=%d, max=%d, prefersDark=%s",
                light.minLight(), light.maxLight(), light.prefersDark())))), false);

        // Floor requirements
        var floor = reqs.floor();
        if (!floor.validBlocks().isEmpty() || floor.canSpawnOnLiquid()) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("Floor: blocks=%d, liquid=%s, solid=%s",
                    floor.validBlocks().size(),
                    floor.canSpawnOnLiquid(),
                    floor.requiresSolid())))), false);
        }

        // Space requirements
        var space = reqs.space();
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Space: %.1fx%.1f, clearance=%.1f",
                space.width(), space.height(), space.clearanceAbove())))), false);

        // Time requirements
        var time = reqs.time();
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Time: %s", time.name())))), false);

        // Boss info
        var boss = reqs.boss();
        if (boss.isBoss()) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("Boss: YES, minPlayers=%d",
                    boss.minPlayers())))), false);
        }

        return 1;
    }

    /**
     * Shows cache statistics.
     */
    private static int showCacheStats(CommandContext<CommandSourceStack> ctx) {
        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;

        var cache = registry.getCache();
        var overrides = registry.getOverrides();

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "[MobRequirements] Cache Statistics:")), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("  Cached entries: %d", cache.size())))), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("  JSON overrides: %d", overrides.size())))), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("  Initialized: %s", registry.isInitialized())))), false);

        // Count by source
        long autoDetected = cache.values().stream()
            .filter(r -> r.source() == MobRequirements.RequirementSource.AUTO_DETECTED)
            .count();
        long merged = cache.values().stream()
            .filter(r -> r.source() == MobRequirements.RequirementSource.MERGED)
            .count();
        long jsonOnly = cache.values().stream()
            .filter(r -> r.source() == MobRequirements.RequirementSource.JSON_OVERRIDE)
            .count();

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("  By source: auto=%d, merged=%d, json=%d",
                autoDetected, merged, jsonOnly)))), false);

        return 1;
    }

    /**
     * Generates example JSON override files.
     */
    private static int generateExamples(CommandContext<CommandSourceStack> ctx) {
        MobRequirementsRegistry registry = MobRequirementsRegistry.INSTANCE;

        if (!registry.isInitialized()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Registry not initialized. Wait for server start.")));
            return 0;
        }

        try {
            registry.generateExampleOverrides();
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "[MobRequirements] Example JSON files generated in config/devmod/mob_requirements/")), true);
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "  Use '/devmod mobrequirements reload' after editing.")), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to generate example overrides", e);
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[MobRequirements] Failed to generate examples: " + e.getMessage())));
            return 0;
        }
    }
}
