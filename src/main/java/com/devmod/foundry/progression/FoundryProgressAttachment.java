package com.devmod.foundry.progression;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.devmod.DevMod;

/**
 * Attachment registration for foundry player progress.
 */
public final class FoundryProgressAttachment {
    private FoundryProgressAttachment() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(Objects.requireNonNull(NeoForgeRegistries.ATTACHMENT_TYPES), DevMod.MODID);

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_progress");

    public static final Supplier<AttachmentType<FoundryPlayerProgress>> FOUNDRY_PROGRESS =
        ATTACHMENT_TYPES.register("foundry_progress", () ->
            AttachmentType.builder(FoundryPlayerProgress::new)
                .serialize(Objects.requireNonNull(FoundryPlayerProgress.CODEC))
                .copyOnDeath()
                .sync(new AttachmentSyncHandler<>() {
                    @Override
                    public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
                        return holder == to;
                    }

                    @Override
                    public void write(RegistryFriendlyByteBuf buf, FoundryPlayerProgress attachment, boolean initialSync) {
                        CompoundTag tag = attachment.serializeNBT(buf.registryAccess());
                        buf.writeNbt(tag);
                    }

                    @Override
                    public FoundryPlayerProgress read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf,
                        FoundryPlayerProgress previousValue) {
                        CompoundTag tag = buf.readNbt();
                        FoundryPlayerProgress progress = previousValue != null ? previousValue : new FoundryPlayerProgress();
                        if (tag != null) {
                            progress.deserializeNBT(buf.registryAccess(), tag);
                        }
                        return progress;
                    }
                })
                .build()
        );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }

    public static FoundryPlayerProgress get(Player player) {
        return player.getData(Objects.requireNonNull(FOUNDRY_PROGRESS.get()));
    }

    public static void sync(Player player) {
        if (!player.level().isClientSide) {
            player.syncData(Objects.requireNonNull(FOUNDRY_PROGRESS.get()));
        }
    }
}
