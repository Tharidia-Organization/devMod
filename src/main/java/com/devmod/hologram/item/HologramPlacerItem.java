package com.devmod.hologram.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramPosition;
import com.devmod.hologram.data.HologramRegistry;
import com.devmod.hologram.data.HologramState;
import com.devmod.hologram.data.HologramStyle;
import com.devmod.hologram.data.HologramType;
import com.devmod.hologram.network.OpenHologramEditorPayload;
import com.devmod.hologram.runtime.HologramManager;
import com.devmod.hologram.runtime.HologramSounds;

/**
 * Item for creating and editing holograms.
 *
 * <p>Interactions:
 * <ul>
 *   <li>Right-click on block: Create new hologram or edit existing</li>
 *   <li>Shift+Right-click on hologram: Move hologram</li>
 *   <li>Shift+Left-click on hologram: Remove hologram</li>
 * </ul>
 *
 * <p>Requires OP level 2+ to use.
 */
public class HologramPlacerItem extends Item {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    public HologramPlacerItem() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
        );
    }

    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        if (player == null) {
            return InteractionResult.PASS;
        }

        // Permission check
        if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("message.devmod.hologram.error.no_permission"),
                    true
                );
            }
            return InteractionResult.FAIL;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // Position for hologram (one block above clicked position)
        BlockPos hologramPos = clickedPos.above();

        HologramRegistry registry = HologramRegistry.get(Objects.requireNonNull(serverLevel.getServer()));
        Optional<HologramDefinition> existingHologram = registry.getHologramAt(hologramPos);

        if (existingHologram.isPresent()) {
            HologramDefinition def = existingHologram.get();

            if (player.isShiftKeyDown()) {
                // Shift+click on existing hologram = remove
                removeHologram(serverLevel, serverPlayer, registry, def);
            } else {
                // Normal click on existing hologram = open editor
                openEditor(serverPlayer, def);
            }
        } else {
            // No hologram exists, create new one
            createHologram(serverLevel, serverPlayer, registry, hologramPos);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Creates a new hologram at the specified position.
     */
    private void createHologram(
        @Nonnull ServerLevel level,
        @Nonnull ServerPlayer player,
        @Nonnull HologramRegistry registry,
        @Nonnull BlockPos pos
    ) {
        // Build new hologram definition
        HologramDefinition def = HologramDefinition.builder()
            .label("New Hologram")
            .type(HologramType.STATIC)
            .lines(List.of("Edit this hologram"))
            .style(HologramStyle.DEFAULT)
            .position(new HologramPosition(
                pos,
                Vec3.ZERO,
                level.dimension().location(),
                0f, 0f
            ))
            .options(HologramOptions.DEFAULT)
            .creator(player.getUUID())
            .build();

        // Register hologram
        if (registry.createHologram(def).isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.hologram.error.dimension_limit"),
                true
            );
            return;
        }

        // Spawn TextDisplay entity
        HologramManager.INSTANCE.spawnHologram(level, def);

        // Feedback
        player.displayClientMessage(
            Component.translatable("message.devmod.hologram.created", pos.toShortString()),
            true
        );
        HologramSounds.playPhase(level, Vec3.atCenterOf(pos), HologramSounds.Phase.SPAWN);

        // Auto-open editor for new hologram
        openEditor(player, def);

        DevMod.LOGGER.info("[Hologram] Player {} created hologram at {}", player.getName().getString(), pos.toShortString());
    }

    /**
     * Opens the hologram editor for the player.
     */
    private void openEditor(@Nonnull ServerPlayer player, @Nonnull HologramDefinition def) {
        // Check if hologram is locked
        if (def.options().locked()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.hologram.error.locked"),
                true
            );
            return;
        }

        // Send packet to open editor
        PacketDistributor.sendToPlayer(player, new OpenHologramEditorPayload(def));
        HologramSounds.playPhase(
            player.serverLevel(),
            def.position().getExactPosition(),
            HologramSounds.Phase.EDITOR_OPEN
        );
    }

    /**
     * Removes a hologram.
     */
    private void removeHologram(
        @Nonnull ServerLevel level,
        @Nonnull ServerPlayer player,
        @Nonnull HologramRegistry registry,
        @Nonnull HologramDefinition def
    ) {
        // Check if hologram is locked
        if (def.options().locked()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.hologram.error.locked"),
                true
            );
            return;
        }

        // Despawn entity
        HologramManager.INSTANCE.despawnHologram(level, def.id());

        // Remove from registry
        registry.deleteHologram(def.id());

        // Feedback
        player.displayClientMessage(
            Component.translatable("message.devmod.hologram.removed"),
            true
        );
        HologramSounds.playPhase(level, def.position().getExactPosition(), HologramSounds.Phase.REMOVE);

        DevMod.LOGGER.info("[Hologram] Player {} removed hologram {} at {}",
            player.getName().getString(), def.id(), def.position().toShortString());
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltipComponents,
        @Nonnull TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(Component.translatable("item.devmod.hologram_placer.tooltip1")
            .withStyle(style -> style.withColor(HologramState.IDLE.getPrimary())));
        tooltipComponents.add(Component.translatable("item.devmod.hologram_placer.tooltip2")
            .withStyle(style -> style.withColor(0x888888)));
        tooltipComponents.add(Component.translatable("item.devmod.hologram_placer.tooltip3")
            .withStyle(style -> style.withColor(0x888888)));
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        // Enchanted glow effect
        return true;
    }
}
