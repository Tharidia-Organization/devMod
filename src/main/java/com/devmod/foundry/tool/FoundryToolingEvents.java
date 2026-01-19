package com.devmod.foundry.tool;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.devmod.DevMod;

/**
 * Event hooks for foundry tool leveling.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class FoundryToolingEvents {
    private FoundryToolingEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null) {
            return;
        }
        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return;
        }
        FoundryToolLeveling.addXp(stack, definition, data, 1);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null) {
            return;
        }
        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return;
        }
        FoundryToolLeveling.addXp(stack, definition, data, 2);
    }

    /**
     * Slime armor set bonus: bounce on landing and negate fall damage.
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!hasFullSlimeArmor(entity)) {
            return;
        }
        // Cancel fall damage
        event.setCanceled(true);
        // Bounce: reverse and reduce vertical velocity
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y < -0.1) {
            double bounceY = -motion.y * 0.8; // 80% bounce efficiency
            entity.setDeltaMovement(motion.x, bounceY, motion.z);
            entity.hurtMarked = true; // Sync to client
        }
    }

    private static boolean hasFullSlimeArmor(LivingEntity entity) {
        Item helmet = entity.getItemBySlot(EquipmentSlot.HEAD).getItem();
        Item chest = entity.getItemBySlot(EquipmentSlot.CHEST).getItem();
        Item legs = entity.getItemBySlot(EquipmentSlot.LEGS).getItem();
        Item boots = entity.getItemBySlot(EquipmentSlot.FEET).getItem();
        return helmet == FoundryToolItems.SLIME_HELMET.get()
            && chest == FoundryToolItems.SLIME_CHESTPLATE.get()
            && legs == FoundryToolItems.SLIME_LEGGINGS.get()
            && boots == FoundryToolItems.SLIME_BOOTS.get();
    }
}
