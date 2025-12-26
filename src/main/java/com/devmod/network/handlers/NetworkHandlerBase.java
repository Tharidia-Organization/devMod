package com.devmod.network.handlers;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.PacketValidator;

import com.mojang.logging.LogUtils;

/**
 * Base class with shared utilities for all network handlers.
 * Extracted from NetworkHandler for single responsibility.
 */
public abstract class NetworkHandlerBase {
    protected static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Get the PacketValidator instance for validation.
     */
    protected static PacketValidator security() {
        return PacketValidator.INSTANCE;
    }

    /**
     * Non-null assertion helper. Returns the value after null check.
     * Accepts potentially null value and guarantees non-null return.
     */
    @Nonnull
    protected static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }

    /**
     * Send packet to player with null-safety.
     */
    protected static <T extends CustomPacketPayload> void sendPacket(ServerPlayer player, T payload) {
        PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player, "player"),
            Objects.requireNonNull(payload, "payload")
        );
    }

    /**
     * Get a string identifier for an ItemStack.
     */
    protected static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "<empty>";
        var key = BuiltInRegistries.ITEM.getKey(nn(stack.getItem()));
        return key == null ? stack.getHoverName().getString() : key.toString();
    }

    /**
     * Send editor confirmation to player.
     */
    protected static void sendEditorConfirm(ServerPlayer player, boolean success, boolean global,
            String scope, String itemId, String message) {
        try {
            EditorApplyConfirmPayload payload = new EditorApplyConfirmPayload(success, global,
                scope == null ? "<unknown>" : scope,
                itemId == null ? "<unknown>" : itemId,
                message == null ? "" : message);
            sendPacket(player, payload);
        } catch (Exception e) {
            LOGGER.warn("Failed to send editor confirm to {}: {}", player.getName().getString(), e.getMessage());
        }
    }
}
