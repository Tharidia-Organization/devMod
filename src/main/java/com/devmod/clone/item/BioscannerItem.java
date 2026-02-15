package com.devmod.clone.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Bioscanner item for scanning living entities.
 * Right-click on an entity to capture its data for cloning.
 * Uses CUSTOM_DATA component like Hologenica for compatibility.
 */
public class BioscannerItem extends Item {

    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_ENTITY_NBT = "EntityNBT";
    private static final String TAG_ENTITY_WIDTH = "EntityWidth";
    private static final String TAG_ENTITY_HEIGHT = "EntityHeight";

    public BioscannerItem() {
        super(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON));
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(
        @Nonnull ItemStack stack,
        @Nonnull Player player,
        @Nonnull LivingEntity target,
        @Nonnull InteractionHand hand
    ) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Check if already contains data
        if (hasData(stack)) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.bioscanner.already_scanned")
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
            return InteractionResult.FAIL;
        }

        // Create scan data using update() like Hologenica does
        ResourceLocation entityTypeId = EntityType.getKey(Objects.requireNonNull(target.getType()));
        String entityName = target.getName().getString();
        boolean isPlayerTarget = target instanceof Player;
        UUID targetUUID = isPlayerTarget ? ((Player) target).getUUID() : null;

        // Store entity NBT for non-players
        CompoundTag entityNbt = null;
        if (!isPlayerTarget) {
            entityNbt = new CompoundTag();
            target.save(entityNbt);
            entityNbt.remove("Pos");
            entityNbt.remove("Motion");
            entityNbt.remove("Rotation");
            entityNbt.remove("UUID");
        }

        final String finalEntityType = entityTypeId.toString();
        final String finalEntityName = entityName;
        final UUID finalUUID = targetUUID;
        final CompoundTag finalNbt = entityNbt;
        final float finalWidth = target.getBbWidth();
        final float finalHeight = target.getBbHeight();

        // Get the actual stack from player inventory and modify it directly
        int slot = hand == InteractionHand.MAIN_HAND ?
            player.getInventory().selected :
            40; // Offhand slot

        ItemStack inventoryStack = player.getInventory().getItem(slot);

        // Use update() like Hologenica
        inventoryStack.update(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(CustomData.EMPTY),
            customData -> {
                CompoundTag tag = customData.copyTag();
                tag.putString(TAG_ENTITY_TYPE, Objects.requireNonNull(finalEntityType));
                tag.putString(TAG_ENTITY_NAME, Objects.requireNonNull(finalEntityName));
                if (finalUUID != null) {
                    tag.putUUID(TAG_PLAYER_UUID, finalUUID);
                }
                if (finalNbt != null) {
                    tag.put(TAG_ENTITY_NBT, finalNbt);
                }
                tag.putFloat(TAG_ENTITY_WIDTH, finalWidth);
                tag.putFloat(TAG_ENTITY_HEIGHT, finalHeight);
                return CustomData.of(tag);
            });

        // Force inventory sync
        player.getInventory().setItem(slot, inventoryStack);

        com.devmod.DevMod.LOGGER.info("[Bioscanner] Scanned {}, hasData now: {}",
            entityTypeId, hasData(inventoryStack));

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(Component.translatable("message.devmod.bioscanner.scanned", target.getName())
                .withStyle(ChatFormatting.GREEN)),
            true
        );

        // Effects
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                Objects.requireNonNull(SoundEvents.BEACON_ACTIVATE),
                SoundSource.PLAYERS,
                0.5f, 1.8f
            );

            // Scanning particles around target
            var random = serverLevel.getRandom();
            for (int i = 0; i < 20; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = 0.5 + random.nextDouble() * 0.3;
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;
                serverLevel.sendParticles(
                    Objects.requireNonNull(ParticleTypes.END_ROD),
                    target.getX() + offsetX,
                    target.getY() + random.nextDouble() * target.getBbHeight(),
                    target.getZ() + offsetZ,
                    1, 0, 0, 0, 0.02
                );
            }
        }

        // Cooldown
        player.getCooldowns().addCooldown(this, 20);

        return InteractionResult.SUCCESS;
    }

    /**
     * Shift+right-click in air to clear bioscanner data.
     */
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown() && hasData(Objects.requireNonNull(stack))) {
            if (!level.isClientSide) {
                clearData(stack);
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("message.devmod.bioscanner.cleared")
                        .withStyle(ChatFormatting.YELLOW)),
                    true
                );
                level.playSound(null, Objects.requireNonNull(player.blockPosition()),
                    Objects.requireNonNull(SoundEvents.BEACON_DEACTIVATE), SoundSource.PLAYERS, 0.5f, 1.2f);
            }
            return Objects.requireNonNull(InteractionResultHolder.success(Objects.requireNonNull(stack)));
        }
        return Objects.requireNonNull(InteractionResultHolder.pass(Objects.requireNonNull(stack)));
    }

    /**
     * Gets the custom data tag from the bioscanner.
     */
    @Nullable
    public static CompoundTag getDataTag(@Nonnull ItemStack stack) {
        CustomData customData = stack.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(CustomData.EMPTY));
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_ENTITY_TYPE) ? tag : null;
    }

    /**
     * Checks if the bioscanner contains scan data.
     */
    public static boolean hasData(@Nonnull ItemStack stack) {
        CustomData customData = stack.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA),
            Objects.requireNonNull(CustomData.EMPTY));
        return customData.copyTag().contains(TAG_ENTITY_TYPE);
    }

    /**
     * Gets the entity type string from the bioscanner.
     */
    @Nullable
    public static String getEntityType(@Nonnull ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        return tag != null ? tag.getString(TAG_ENTITY_TYPE) : null;
    }

    /**
     * Gets the entity name from the bioscanner.
     */
    @Nullable
    public static String getEntityName(@Nonnull ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        return tag != null ? tag.getString(TAG_ENTITY_NAME) : null;
    }

    /**
     * Calculate the maximum dimension (width or height) of the scanned entity.
     * Uses stored scan dimensions when present; falls back to EntityType defaults.
     * @return max of width and height, or 0 if no entity data
     */
    public static float getEntityMaxDimension(@Nonnull ItemStack stack) {
        if (!hasData(stack)) return 0f;

        CompoundTag tag = getDataTag(stack);
        if (tag != null && tag.contains(TAG_ENTITY_WIDTH) && tag.contains(TAG_ENTITY_HEIGHT)) {
            float width = tag.getFloat(TAG_ENTITY_WIDTH);
            float height = tag.getFloat(TAG_ENTITY_HEIGHT);
            if (width > 0f || height > 0f) {
                return Math.max(width, height);
            }
        }

        String entityTypeStr = getEntityType(stack);
        if (entityTypeStr == null) return 0f;

        Optional<EntityType<?>> optType = EntityType.byString(entityTypeStr);
        if (optType.isEmpty()) return 0f;

        EntityType<?> type = optType.get();
        float width = type.getDimensions().width();
        float height = type.getDimensions().height();
        return Math.max(width, height);
    }

    /**
     * Clears the bioscan data from the item.
     */
    public static void clearData(@Nonnull ItemStack stack) {
        stack.remove(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return hasData(stack);
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        CompoundTag tag = getDataTag(stack);

        if (tag != null) {
            String entityName = Objects.requireNonNull(tag.getString(TAG_ENTITY_NAME));
            String entityType = tag.getString(TAG_ENTITY_TYPE);

            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.bioscanner.contains")
                .withStyle(ChatFormatting.GRAY)));

            tooltip.add(Objects.requireNonNull(Component.literal("  ")
                .append(Objects.requireNonNull(Component.literal(entityName)
                    .withStyle(ChatFormatting.AQUA)))));

            // Extract path from resource location
            String typePath = entityType.contains(":") ?
                entityType.substring(entityType.indexOf(":") + 1) : entityType;
            tooltip.add(Objects.requireNonNull(Component.literal("  ")
                .append(Objects.requireNonNull(Component.translatable("tooltip.devmod.bioscanner.type", typePath)
                    .withStyle(ChatFormatting.DARK_GRAY)))));

            if (tag.hasUUID(TAG_PLAYER_UUID)) {
                tooltip.add(Objects.requireNonNull(Component.literal("  ")
                    .append(Objects.requireNonNull(Component.translatable("tooltip.devmod.bioscanner.player")
                        .withStyle(ChatFormatting.GOLD)))));
            }
        } else {
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.bioscanner.empty")
                .withStyle(ChatFormatting.GRAY)));
            tooltip.add(Objects.requireNonNull(Component.translatable("tooltip.devmod.bioscanner.usage")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }
    }
}
