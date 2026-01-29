package com.devmod.clone.network;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.devmod.clone.block.entity.NeurocellBlockEntity;
import com.devmod.clone.block.entity.NeurocellItemBlockEntity;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;

import static com.devmod.network.PayloadValidation.PayloadLimits;
import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific network handler for clone module payloads.
 * Handles telepad configuration and mannequin/neurocell rotation and skin.
 */
public final class CloneNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final CloneNetworkHandler INSTANCE = new CloneNetworkHandler();

    private CloneNetworkHandler() {}

    @Override
    public String getDomainName() {
        return "clone";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // TELEPAD_CONFIG: Client -> Server telepad name configuration
        event.registrar(ChannelId.TELEPAD_CONFIG.asString()).playToServer(
            nn(TelepadConfigPayload.TYPE),
            nn(TelepadConfigPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleTelepadConfig(player, payload);
                }
            }, PayloadLimits.SMALL)
        );

        // TELEPAD_OPEN_SCREEN: Server -> Client open telepad screen
        event.registrar(ChannelId.TELEPAD_OPEN_SCREEN.asString()).playToClient(
            nn(TelepadOpenScreenPayload.TYPE),
            nn(TelepadOpenScreenPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleTelepadOpenScreen(payload))), "telepad open screen");
                }
            }, PayloadLimits.SMALL)
        );

        // MANNEQUIN_ROTATION: Client -> Server rotation update for all neurocell types
        event.registrar(ChannelId.MANNEQUIN_ROTATION.asString()).playToServer(
            nn(MannequinRotationPayload.TYPE),
            nn(MannequinRotationPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleMannequinRotation(player, payload);
                }
            }, PayloadLimits.SMALL)
        );

        // MANNEQUIN_SKIN: Client -> Server skin UUID update
        event.registrar(ChannelId.MANNEQUIN_SKIN.asString()).playToServer(
            nn(MannequinSkinPayload.TYPE),
            nn(MannequinSkinPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleMannequinSkin(player, payload);
                }
            }, PayloadLimits.SMALL)
        );
    }

    /**
     * Send telepad open screen payload to a player.
     */
    public static void sendTelepadOpenScreen(ServerPlayer player, TelepadOpenScreenPayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Handle telepad configuration from client.
     */
    private static void handleTelepadConfig(ServerPlayer player, TelepadConfigPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TelepadBlockEntity telepad) {
            telepad.setTelepadName(payload.telepadName());
        }
    }

    /**
     * Handle neurocell rotation from client.
     * Updates the rotation angle when player scrolls while looking at a Neurocell in MANUAL mode.
     * Supports all Neurocell types: Mannequin, Standard, Large, and Item.
     */
    private static void handleMannequinRotation(ServerPlayer player, MannequinRotationPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);

        // Handle NeurocellMannequin
        if (blockEntity instanceof NeurocellMannequinBlockEntity mannequin) {
            if (mannequin.getRotationMode() == NeurocellMannequinBlockEntity.RotationMode.MANUAL) {
                float newAngle = normalizeAngle(mannequin.getRotationAngle() + payload.rotationDelta());
                mannequin.setRotationAngle(newAngle);
            }
            return;
        }

        // Handle standard Neurocell
        if (blockEntity instanceof NeurocellBlockEntity neurocell) {
            if (neurocell.getRotationMode() == NeurocellBlockEntity.RotationMode.MANUAL) {
                float newAngle = normalizeAngle(neurocell.getRotationAngle() + payload.rotationDelta());
                neurocell.setRotationAngle(newAngle);
            }
            return;
        }

        // Handle large Neurocell (NeurocellL)
        if (blockEntity instanceof NeurocellLBlockEntity neurocellL) {
            if (neurocellL.getRotationMode() == NeurocellLBlockEntity.RotationMode.MANUAL) {
                float newAngle = normalizeAngle(neurocellL.getRotationAngle() + payload.rotationDelta());
                neurocellL.setRotationAngle(newAngle);
            }
            return;
        }

        // Handle NeurocellItem
        if (blockEntity instanceof NeurocellItemBlockEntity neurocellItem) {
            if (neurocellItem.getRotationMode() == NeurocellItemBlockEntity.RotationMode.MANUAL) {
                float newAngle = normalizeAngle(neurocellItem.getRotationAngle() + payload.rotationDelta());
                neurocellItem.setRotationAngle(newAngle);
            }
        }
    }

    /**
     * Normalize angle to 0-360 range.
     */
    private static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle < 0) {
            angle += 360.0f;
        }
        return angle;
    }

    /**
     * Handle mannequin skin update from client.
     * Updates the skin UUID for the mannequin.
     */
    private static void handleMannequinSkin(ServerPlayer player, MannequinSkinPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof NeurocellMannequinBlockEntity mannequin) {
            mannequin.setSkinUUID(payload.skinUUID());
        }
    }
}
