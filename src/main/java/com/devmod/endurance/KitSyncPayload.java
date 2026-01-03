package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload to sync custom/temporary kits for dedicated servers.
 */
public record KitSyncPayload(
    @Nonnull String kitId,
    @Nonnull String name,
    @Nonnull String description,
    int color,
    boolean temporary,
    @Nonnull List<CompoundTag> itemTags
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<KitSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "kit_sync"))
    );

    private static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_ITEMS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, KitSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public KitSyncPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            String kitId = buf.readUtf(MAX_STRING_LENGTH);
            String name = buf.readUtf(MAX_STRING_LENGTH);
            String description = buf.readUtf(MAX_STRING_LENGTH);
            int color = buf.readInt();
            boolean temporary = buf.readBoolean();

            int declaredCount = buf.readVarInt();
            List<CompoundTag> items = new ArrayList<>(Math.min(declaredCount, MAX_ITEMS));
            for (int i = 0; i < declaredCount; i++) {
                CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
                if (tag != null) {
                    items.add(tag);
                }
            }

            return new KitSyncPayload(kitId, name, description, color, temporary, items);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull KitSyncPayload payload) {
            buf.writeUtf(Objects.requireNonNull(payload.kitId()), MAX_STRING_LENGTH);
            buf.writeUtf(Objects.requireNonNull(payload.name()), MAX_STRING_LENGTH);
            buf.writeUtf(Objects.requireNonNull(payload.description()), MAX_STRING_LENGTH);
            buf.writeInt(payload.color());
            buf.writeBoolean(payload.temporary());

            List<CompoundTag> items = Objects.requireNonNull(payload.itemTags());
            int count = Math.min(items.size(), MAX_ITEMS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                ByteBufCodecs.COMPOUND_TAG.encode(buf, Objects.requireNonNull(items.get(i)));
            }
        }
    };

    public KitSyncPayload {
        itemTags = itemTags != null ? itemTags : new ArrayList<>();
        name = name != null ? name : "";
        description = description != null ? description : "";
        kitId = kitId != null ? kitId : "";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += varIntSize(kitId.length()) + kitId.length();
        size += varIntSize(name.length()) + name.length();
        size += varIntSize(description.length()) + description.length();
        size += 4; // color
        size += 1; // temporary
        size += varIntSize(itemTags.size());
        for (CompoundTag tag : itemTags) {
            size += tag != null ? tag.sizeInBytes() : 0;
        }
        return size;
    }

    public static KitSyncPayload fromTemporary(String name, List<ItemStack> items, RegistryAccess registryAccess) {
        return new KitSyncPayload("TEMPORARY", name, "", 0, true, encodeItems(items, registryAccess));
    }

    public static KitSyncPayload fromCustomKit(CustomKit kit, RegistryAccess registryAccess) {
        List<ItemStack> stacks = kit != null ? kit.toItemStacks(registryAccess) : List.of();
        return new KitSyncPayload(
            kit != null ? kit.getId() : "",
            kit != null ? kit.getName() : "",
            kit != null ? kit.getDescription() : "",
            kit != null ? kit.getColor() : 0,
            false,
            encodeItems(stacks, registryAccess)
        );
    }

    private static List<CompoundTag> encodeItems(List<ItemStack> items, RegistryAccess registryAccess) {
        List<CompoundTag> tags = new ArrayList<>();
        if (items == null) {
            return tags;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag tag = null;
            try {
                if (registryAccess != null) {
                    Tag saved = stack.save(registryAccess);
                    if (saved instanceof CompoundTag compound) {
                        tag = compound;
                    }
                } else {
                    Tag saved = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                        .result()
                        .orElse(null);
                    if (saved instanceof CompoundTag compound) {
                        tag = compound;
                    }
                }
            } catch (Exception ignored) {
            }
            if (tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static int varIntSize(int value) {
        int size = 1;
        int v = value;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
