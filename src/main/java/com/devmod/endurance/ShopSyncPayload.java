package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record ShopSyncPayload(
    int tokens,
    int coins,
    int prestige,
    int gems,
    int bloodGems,
    Map<String, Integer> purchases
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ShopSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "shop_sync"))
    );

    // Security limits to prevent DoS attacks
    private static final int MAX_PURCHASES = 1000;
    private static final int MAX_STRING_LENGTH = 256;

    public static final StreamCodec<ByteBuf, ShopSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ShopSyncPayload decode(@Nonnull ByteBuf buf) {
            int tokens = buf.readInt();
            int coins = buf.readInt();
            int prestige = buf.readInt();
            int gems = buf.readInt();
            int bloodGems = buf.readInt();

            int purchaseCount = buf.readInt();
            // SECURITY FIX: Validate count is non-negative and bounded
            if (purchaseCount < 0 || purchaseCount > MAX_PURCHASES) {
                purchaseCount = 0; // Reject malformed packet
            }
            Map<String, Integer> purchases = new HashMap<>();
            for (int i = 0; i < purchaseCount; i++) {
                String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                // SECURITY FIX: Validate string length
                if (key != null && key.length() <= MAX_STRING_LENGTH) {
                    int value = buf.readInt();
                    purchases.put(key, value);
                } else {
                    buf.readInt(); // Skip the value for invalid key
                }
            }

            return new ShopSyncPayload(tokens, coins, prestige, gems, bloodGems, purchases);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull ShopSyncPayload payload) {
            buf.writeInt(payload.tokens);
            buf.writeInt(payload.coins);
            buf.writeInt(payload.prestige);
            buf.writeInt(payload.gems);
            buf.writeInt(payload.bloodGems);

            buf.writeInt(payload.purchases.size());
            for (Map.Entry<String, Integer> entry : payload.purchases.entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(entry.getKey()));
                buf.writeInt(entry.getValue());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 5 * 4; // tokens, coins, prestige, gems, bloodGems
        int count = Math.min(purchases.size(), MAX_PURCHASES);
        size += 4; // purchase count
        int written = 0;
        for (Map.Entry<String, Integer> entry : purchases.entrySet()) {
            if (written >= count) {
                break;
            }
            size += estimatedUtfSize(entry.getKey());
            size += 4; // value
            written++;
        }
        return size;
    }

    /**
     * Create a sync payload from a player wallet.
     */
    public static ShopSyncPayload fromWallet(RewardSystem.PlayerWallet wallet) {
        return new ShopSyncPayload(
            wallet.getCurrency(RewardSystem.Currency.TOKENS),
            wallet.getCurrency(RewardSystem.Currency.COINS),
            wallet.getCurrency(RewardSystem.Currency.PRESTIGE),
            wallet.getCurrency(RewardSystem.Currency.GEMS),
            wallet.getCurrency(RewardSystem.Currency.BLOOD_GEMS),
            new HashMap<>(wallet.getPurchases())
        );
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
