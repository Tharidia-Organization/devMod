package com.devmod.foundry.item;

import javax.annotation.Nonnull;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.devmod.npc.data.NpcBehavior;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.dialog.DialogPresets;
import com.devmod.npc.item.NeurocellNpcItem;

/**
 * Pre-configured NPC spawner for the Foundry guide.
 */
public class FoundryGuideNpcItem extends NeurocellNpcItem {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(
        @Nonnull Level level,
        @Nonnull Player player,
        @Nonnull InteractionHand hand
    ) {
        if (!level.isClientSide && hasPermission(player)) {
            ensureConfigured(player.getItemInHand(hand), player);
        }
        return super.use(level, player, hand);
    }

    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide && player != null && hasPermission(player)) {
            ensureConfigured(context.getItemInHand(), player);
        }
        return super.useOn(context);
    }

    private static boolean hasPermission(@Nonnull Player player) {
        return player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }

    private static void ensureConfigured(@Nonnull ItemStack stack, @Nonnull Player player) {
        if (NeurocellNpcItem.hasConfig(stack)) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        NpcConfiguration config = NpcConfiguration.createDefault("Guida Foundry", serverPlayer.getUUID())
            .withDialogSetId(DialogPresets.FOUNDRY_GUIDE_ID)
            .withBehavior(NpcBehavior.DEFAULT.withFloating(false));
        NeurocellNpcItem.setConfig(stack, config);
    }
}
