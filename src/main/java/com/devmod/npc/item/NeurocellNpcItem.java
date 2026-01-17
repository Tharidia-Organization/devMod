package com.devmod.npc.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.entity.PlayerCloneEntity;
import com.devmod.entity.ModEntities;
import com.devmod.npc.component.NpcComponents;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.data.NpcRegistry;
import com.devmod.npc.network.NpcNetworkHandler;

/**
 * Neurocell NPC item for spawning and configuring NPCs.
 *
 * <p>Usage:
 * <ul>
 *   <li>Right-click in air: Opens NPC configuration GUI (if not configured)</li>
 *   <li>Right-click on block: Spawns configured NPC at clicked location</li>
 *   <li>Shift+right-click on existing NPC: Opens edit GUI for that NPC</li>
 * </ul>
 *
 * <p>Requires operator permissions (level 2+) to use.
 */
public class NeurocellNpcItem extends Item {

    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    public NeurocellNpcItem() {
        super(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC));
    }

    /**
     * Right-click in air: Opens config GUI if not configured.
     */
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(
        @Nonnull Level level,
        @Nonnull Player player,
        @Nonnull InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        // Check permissions
        if (!hasPermission(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.no_permission")
                        .withStyle(ChatFormatting.RED)),
                    true
                );
            }
            return Objects.requireNonNull(InteractionResultHolder.fail(Objects.requireNonNull(stack)));
        }

        // Server-side only
        if (level.isClientSide) {
            return Objects.requireNonNull(InteractionResultHolder.success(Objects.requireNonNull(stack)));
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // Check if item has config
        NpcConfiguration existingConfig = getConfig(Objects.requireNonNull(stack));
        if (existingConfig == null) {
            // Open config GUI for new NPC
            NpcConfiguration defaultConfig = NpcConfiguration.createDefault(
                "New NPC",
                Objects.requireNonNull(serverPlayer.getUUID())
            );
            NpcNetworkHandler.openConfigScreen(serverPlayer, defaultConfig, hand, null);
        } else {
            // Item already configured - inform user to click on block
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.use_on_block")
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
        }

        return Objects.requireNonNull(InteractionResultHolder.success(Objects.requireNonNull(stack)));
    }

    /**
     * Right-click on block: Spawns NPC at location.
     */
    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();

        // Check permissions
        if (!hasPermission(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.no_permission")
                        .withStyle(ChatFormatting.RED)),
                    true
                );
            }
            return InteractionResult.FAIL;
        }

        // Server-side only
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) level;

        // Check if item has config
        NpcConfiguration config = getConfig(Objects.requireNonNull(stack));
        if (config == null) {
            // No config - open GUI to configure first
            NpcConfiguration defaultConfig = NpcConfiguration.createDefault(
                "New NPC",
                Objects.requireNonNull(serverPlayer.getUUID())
            );
            NpcNetworkHandler.openConfigScreen(serverPlayer, defaultConfig, context.getHand(), null);
            return InteractionResult.SUCCESS;
        }

        // Spawn NPC at clicked position
        BlockPos clickedPos = context.getClickedPos();
        BlockPos spawnPos = clickedPos.relative(Objects.requireNonNull(context.getClickedFace()));
        Vec3 spawnVec = Vec3.atBottomCenterOf(Objects.requireNonNull(spawnPos));

        // Create the NPC entity
        PlayerCloneEntity npc = new PlayerCloneEntity(ModEntities.PLAYER_CLONE.get(), serverLevel);
        npc.setPos(spawnVec.x, spawnVec.y, spawnVec.z);

        // Apply NPC configuration
        npc.setNpcMode(config);

        // Add to world
        serverLevel.addFreshEntity(npc);

        // Register in NpcRegistry
        NpcRegistry registry = NpcRegistry.get(Objects.requireNonNull(serverPlayer.getServer()));
        NpcConfiguration registeredConfig = config.withSpawnLocation(
            spawnPos,
            serverLevel.dimension()
        );
        registry.createNpc(registeredConfig);

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.spawned", config.displayName())
                .withStyle(ChatFormatting.GREEN)),
            true
        );

        // Effects
        serverLevel.playSound(
            null,
            spawnVec.x, spawnVec.y, spawnVec.z,
            Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP),
            SoundSource.PLAYERS,
            0.5f, 1.2f
        );

        // Consume item (or clear config based on preference)
        if (!player.getAbilities().instabuild) {
            // In survival, clear the config instead of consuming
            clearConfig(Objects.requireNonNull(stack));
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Shift+right-click on existing NPC: Opens edit GUI.
     */
    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(
        @Nonnull ItemStack stack,
        @Nonnull Player player,
        @Nonnull LivingEntity target,
        @Nonnull InteractionHand hand
    ) {
        // Only handle PlayerCloneEntity in NPC mode
        if (!(target instanceof PlayerCloneEntity npc) || !npc.isNpc()) {
            return InteractionResult.PASS;
        }

        // Check permissions
        if (!hasPermission(player)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.no_permission")
                        .withStyle(ChatFormatting.RED)),
                    true
                );
            }
            return InteractionResult.FAIL;
        }

        // Server-side only
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // Only shift-click to edit
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // Get NPC's config from registry
        UUID npcConfigId = npc.getNpcConfigId();
        if (npcConfigId == null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.not_registered")
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
            return InteractionResult.FAIL;
        }

        NpcRegistry registry = NpcRegistry.get(Objects.requireNonNull(serverPlayer.getServer()));
        NpcConfiguration existingConfig = registry.getNpc(npcConfigId).orElse(null);
        if (existingConfig == null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("item.devmod.neurocell_npc.config_not_found")
                    .withStyle(ChatFormatting.RED)),
                true
            );
            return InteractionResult.FAIL;
        }

        // Open edit GUI
        NpcNetworkHandler.openConfigScreen(serverPlayer, existingConfig, null, npcConfigId);

        return InteractionResult.SUCCESS;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Checks if the player has permission to use this item.
     */
    private boolean hasPermission(@Nonnull Player player) {
        return player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }

    /**
     * Gets the NPC configuration from the item stack.
     */
    @Nullable
    public static NpcConfiguration getConfig(@Nonnull ItemStack stack) {
        return stack.get(Objects.requireNonNull(NpcComponents.NPC_CONFIG.get()));
    }

    /**
     * Sets the NPC configuration on the item stack.
     */
    public static void setConfig(@Nonnull ItemStack stack, @Nonnull NpcConfiguration config) {
        stack.set(Objects.requireNonNull(NpcComponents.NPC_CONFIG.get()), config);
    }

    /**
     * Clears the NPC configuration from the item stack.
     */
    public static void clearConfig(@Nonnull ItemStack stack) {
        stack.remove(Objects.requireNonNull(NpcComponents.NPC_CONFIG.get()));
    }

    /**
     * Checks if the item has NPC configuration.
     */
    public static boolean hasConfig(@Nonnull ItemStack stack) {
        return stack.has(Objects.requireNonNull(NpcComponents.NPC_CONFIG.get()));
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return hasConfig(stack);
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        NpcConfiguration config = getConfig(stack);

        if (config != null) {
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.configured")
                .withStyle(ChatFormatting.GREEN)));
            tooltip.add(Objects.requireNonNull(Component.literal("  ")
                .append(Objects.requireNonNull(Component.literal(config.displayName())
                    .withStyle(ChatFormatting.AQUA)))));

            String dialogSetId = config.dialogSetId();
            if (dialogSetId != null && !dialogSetId.isEmpty()) {
                tooltip.add(Objects.requireNonNull(Component.literal("  ")
                    .append(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.has_dialog")
                        .withStyle(ChatFormatting.YELLOW)))));
            }

            tooltip.add(Objects.requireNonNull(Component.empty()));
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.use_spawn")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        } else {
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.not_configured")
                .withStyle(ChatFormatting.GRAY)));
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.use_configure")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        tooltip.add(Objects.requireNonNull(Component.empty()));
        tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.neurocell_npc.requires_op")
            .withStyle(ChatFormatting.DARK_RED)));
    }
}
