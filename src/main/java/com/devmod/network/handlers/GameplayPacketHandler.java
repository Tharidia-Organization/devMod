package com.devmod.network.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PayloadValidation.PayloadLimits;

import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific handler for gameplay payloads:
 * contracts, challenges, and season pass.
 */
public final class GameplayPacketHandler extends NetworkHandlerBase implements PayloadRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameplayPacketHandler.class);

    public static final GameplayPacketHandler INSTANCE = new GameplayPacketHandler();

    private GameplayPacketHandler() {}

    @Override
    public String getDomainName() {
        return "gameplay";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Contract sync
        event.registrar(ChannelId.CONTRACT_SYNC.asString()).playToClient(
            nn(ContractSyncPayload.TYPE),
            nn(ContractSyncPayload.STREAM_CODEC),
            validated(GameplayPacketHandler::handleContractSync, PayloadLimits.SMALL)
        );

        // Challenge sync
        event.registrar(ChannelId.CHALLENGE_SYNC.asString()).playToClient(
            nn(ChallengeSyncPayload.TYPE),
            nn(ChallengeSyncPayload.STREAM_CODEC),
            validated(GameplayPacketHandler::handleChallengeSync, PayloadLimits.MEDIUM)
        );

        // Season pass
        event.registrar(ChannelId.SEASON_PASS_SYNC.asString()).playToClient(
            nn(com.devmod.endurance.season.SeasonPassPayload.TYPE),
            nn(com.devmod.endurance.season.SeasonPassPayload.STREAM_CODEC),
            validated(GameplayPacketHandler::handleSeasonPassSync, PayloadLimits.MEDIUM)
        );
        event.registrar(ChannelId.REQUEST_SEASON_PASS.asString()).playToServer(
            nn(com.devmod.endurance.season.RequestSeasonPassPayload.TYPE),
            nn(com.devmod.endurance.season.RequestSeasonPassPayload.STREAM_CODEC),
            validated(GameplayPacketHandler::handleRequestSeasonPass, PayloadLimits.SMALL)
        );

        // Claim season reward
        event.registrar(ChannelId.CLAIM_SEASON_REWARD.asString()).playToServer(
            nn(com.devmod.endurance.season.ClaimSeasonRewardPayload.TYPE),
            nn(com.devmod.endurance.season.ClaimSeasonRewardPayload.STREAM_CODEC),
            validated(GameplayPacketHandler::handleClaimSeasonReward, PayloadLimits.SMALL)
        );
    }

    // =================================================================================
    // CONTRACT SYNC (client-side)
    // =================================================================================
    private static void handleContractSync(ContractSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleContractSync(payload))), "contract sync");
        }
    }

    // =================================================================================
    // CHALLENGE SYNC (client-side)
    // =================================================================================
    private static void handleChallengeSync(ChallengeSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleChallengeSync(payload))), "challenge sync");
        }
    }

    // =================================================================================
    // SEASON PASS SYNC (client-side)
    // =================================================================================
    private static void handleSeasonPassSync(
            com.devmod.endurance.season.SeasonPassPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleSeasonPassSync(payload))), "season pass sync");
        }
    }

    // =================================================================================
    // REQUEST SEASON PASS (server-side)
    // =================================================================================
    private static void handleRequestSeasonPass(
            com.devmod.endurance.season.RequestSeasonPassPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendSeasonPassSync(serverPlayer);
            }
        }), "request season pass");
    }

    // =================================================================================
    // CLAIM SEASON REWARD (server-side)
    // =================================================================================
    private static void handleClaimSeasonReward(
            com.devmod.endurance.season.ClaimSeasonRewardPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var system = com.devmod.endurance.season.SeasonPassSystem.INSTANCE;
                var reward = system.claimReward(serverPlayer.getUUID(), payload.tier(), payload.premium());
                reward.ifPresent(r -> {
                    // Grant reward items based on type
                    grantSeasonReward(serverPlayer, r, payload.tier());
                });
                // Sync updated state to client
                NetworkHandler.sendSeasonPassSync(serverPlayer);
            }
        }), "claim season reward");
    }

    /**
     * Grant actual reward items/effects to the player based on the season reward.
     */
    private static void grantSeasonReward(ServerPlayer player,
            com.devmod.endurance.season.SeasonPassSystem.SeasonReward reward, int tier) {
        var rewardSystem = com.devmod.endurance.RewardSystem.INSTANCE;
        java.util.UUID playerId = player.getUUID();

        switch (reward.type()) {
            case TOKENS -> rewardSystem.getWallet(playerId)
                .addCurrency(com.devmod.endurance.RewardSystem.Currency.TOKENS, reward.amount());
            case PRESTIGE -> rewardSystem.getWallet(playerId)
                .addCurrency(com.devmod.endurance.RewardSystem.Currency.PRESTIGE, reward.amount());
            case XP_BOOST -> {
                var progress = com.devmod.endurance.season.SeasonPassSystem.INSTANCE
                    .getOrCreateProgress(playerId);
                progress.applyXPBoost(1.5f, reward.amount());
            }
            case EXCLUSIVE_ITEM, EXCLUSIVE_COSMETIC, EXCLUSIVE_PERK, EXCLUSIVE_TITLE -> {
                // Notify player of exclusive unlock
                com.devmod.notification.NotificationService.INSTANCE.notifySeasonTierUp(
                    playerId, tier, reward.getDisplayName(), "");
            }
        }

        LOGGER.info("[SeasonPass] Player {} claimed tier {} reward: {} ({})",
            player.getName().getString(), tier, reward.getDisplayName(),
            reward.premium() ? "premium" : "free");
    }
}
