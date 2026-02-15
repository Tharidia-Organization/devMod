package com.devmod.network.handlers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.endurance.CustomKit;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
import com.devmod.endurance.PerkSynergySystem;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.RequestShopSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopPurchasePayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.WaveDirective;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.WaveDirectiveSelectionPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.TelemetryService;
import com.devmod.util.I18n;

/**
 * Handles kit sync, shop, perk selection, and wave directive payloads
 * for the endurance system. Delegated from {@link EnduranceNetworkHandler}.
 */
final class EnduranceShopKitPerkPacketHandler extends NetworkHandlerBase {

    private static final long PERK_SELECTION_TIMEOUT_MS = 20_000L;
    private static final long DIRECTIVE_SELECTION_TIMEOUT_MS = 15_000L;

    private EnduranceShopKitPerkPacketHandler() {}

    // =================================================================================
    // KIT SYNC (client-side kits for dedicated servers)
    // =================================================================================
    static void handleKitSync(KitSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                long startNanos = System.nanoTime();
                int parseFailures = 0;
                int itemCount = 0;
                try {
                    var security = security();
                    ValidationResult validation = security.validatePacket(player, "kit_sync", true);
                    if (!validation.isSuccess()) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload != null && payload.temporary(),
                            payload != null ? payload.kitId() : "",
                            validation.getErrorMessage()));
                        recordKitSyncTelemetry(player, payload, false, 0, 0,
                            validation.getErrorMessage() != null ? validation.getErrorMessage() : "validation failed", startNanos);
                        return;
                    }

                    if (payload.itemTags().size() > KitSyncPayload.MAX_ITEMS) {
                        String message = "Kit sync rejected (too many items)";
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", message));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            message));
                        recordKitSyncTelemetry(player, payload, false, 0, 0, message, startNanos);
                        return;
                    }

                    if (!payload.temporary()) {
                        String kitId = payload.kitId();
                        if (kitId == null || kitId.isBlank()) {
                            sendPacket(player, KitSyncConfirmPayload.failure(false, "", "Kit ID missing"));
                            recordKitSyncTelemetry(player, payload, false, 0, 0, "Kit ID missing", startNanos);
                            return;
                        }
                    }

                    var registryAccess = Objects.requireNonNull(player.registryAccess());
                    List<ItemStack> items = new ArrayList<>();
                    String stackError = null;
                    boolean normalizedStacks = false;
                    for (var tag : payload.itemTags()) {
                        if (tag == null) {
                            parseFailures++;
                            continue;
                        }
                        var parsed = ItemStack.parse(registryAccess, tag);
                        if (parsed.isPresent() && !parsed.get().isEmpty()) {
                            ItemStack parsedStack = parsed.get();
                            if (parsedStack.getCount() <= 0) {
                                parseFailures++;
                                continue;
                            }
                            int added = addNormalizedStack(items, parsedStack, KitSyncPayload.MAX_ITEMS);
                            if (added < 0) {
                                stackError = "Kit sync rejected (stack size exceeds max items)";
                                break;
                            }
                            if (parsedStack.getCount() > parsedStack.getMaxStackSize()) {
                                normalizedStacks = true;
                            }
                        } else {
                            parseFailures++;
                        }
                    }

                    itemCount = items.size();

                    if (stackError != null) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", stackError));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            stackError));
                        recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, stackError, startNanos);
                        return;
                    }

                    if (parseFailures > 0) {
                        String message = "Kit sync rejected (" + parseFailures + " invalid item"
                            + (parseFailures == 1 ? "" : "s") + "). Check that the server has the same mods/data packs.";
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", message));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            message));
                        recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, message, startNanos);
                        return;
                    }

                    if (items.isEmpty()) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", "Kit sync rejected (empty)"));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            "Kit sync rejected (empty)"));
                        recordKitSyncTelemetry(player, payload, false, 0, parseFailures, "Kit sync rejected (empty)", startNanos);
                        return;
                    }

                    if (payload.temporary()) {
                        KitManager.INSTANCE.setTemporaryKit(player.getUUID(), items, payload.name(), registryAccess);
                        LOGGER.debug("[EnduranceKit] Synced temporary kit '{}' for {} ({} items)",
                            payload.name(), player.getName().getString(), items.size());
                        sendPacket(player, KitSyncConfirmPayload.success(true, payload.kitId(),
                            normalizedStacks ? "Temporary kit synced (stack sizes normalized)" : "Temporary kit synced"));
                        recordKitSyncTelemetry(player, payload, true, itemCount, parseFailures, "", startNanos);
                        return;
                    }

                    String kitId = payload.kitId();
                    if (kitId == null || kitId.isBlank()) {
                        kitId = java.util.UUID.randomUUID().toString().substring(0, 8);
                    }
                    String kitName = payload.name() != null && !payload.name().isBlank()
                        ? payload.name()
                        : "Custom Kit";

                    List<CustomKit.KitItem> kitItems = new ArrayList<>();
                    for (ItemStack stack : items) {
                        if (stack != null && !stack.isEmpty()) {
                            kitItems.add(CustomKit.KitItem.fromItemStack(stack, registryAccess));
                        }
                    }
                    CustomKit kit = new CustomKit(kitId, kitName, payload.description(),
                        payload.color(), kitItems, System.currentTimeMillis(), System.currentTimeMillis());

                    KitManager.INSTANCE.registerSyncedCustomKit(player.getUUID(), kit);
                    LOGGER.debug("[EnduranceKit] Synced custom kit '{}' ({}) for {}",
                        kit.getName(), kit.getId(), player.getName().getString());
                    sendPacket(player, KitSyncConfirmPayload.success(false, kit.getId(),
                        normalizedStacks ? "Custom kit synced (stack sizes normalized)" : "Custom kit synced"));
                    recordKitSyncTelemetry(player, payload, true, itemCount, parseFailures, "", startNanos);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceKit] Failed to sync kit for {}", player.getName().getString(), e);
                    sendPacket(player, KitSyncConfirmPayload.failure(
                        payload != null && payload.temporary(),
                        payload != null ? payload.kitId() : "",
                        "Kit sync failed"));
                    recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, "Kit sync failed", startNanos);
                }
            }
        });
    }

    private static void recordKitSyncTelemetry(ServerPlayer player, KitSyncPayload payload, boolean success,
                                               int itemCount, int parseFailures, String error, long startNanos) {
        long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        String kitId = payload != null ? payload.kitId() : "";
        boolean temporary = payload != null && payload.temporary();
        int tagCount = payload != null && payload.itemTags() != null ? payload.itemTags().size() : 0;
        int estimatedSize = payload != null ? payload.estimatedSize() : -1;

        String playerName = player != null ? player.getName().getString() : "unknown";
        String line = "{\"ts\":\"" + Instant.now() + "\","
            + "\"type\":\"kit_sync\","
            + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
            + "\"playerId\":\"" + (player != null ? player.getUUID() : "") + "\","
            + "\"kitId\":\"" + TelemetryJson.escape(kitId) + "\","
            + "\"temporary\":" + temporary + ","
            + "\"itemCount\":" + itemCount + ","
            + "\"tagCount\":" + tagCount + ","
            + "\"parseFailures\":" + parseFailures + ","
            + "\"estimatedBytes\":" + estimatedSize + ","
            + "\"success\":" + success + ","
            + "\"serverMs\":" + durationMs + ","
            + "\"error\":\"" + TelemetryJson.escape(error) + "\"}";
        TelemetryService.INSTANCE.appendNetworkLine(line);
    }

    private static int addNormalizedStack(List<ItemStack> items, ItemStack stack, int maxItems) {
        int count = stack.getCount();
        if (count <= 0) {
            return 0;
        }
        int max = Math.max(1, stack.getMaxStackSize());
        int added = 0;
        int remaining = count;
        while (remaining > 0) {
            if (items.size() >= maxItems) {
                return -1;
            }
            int size = Math.min(max, remaining);
            ItemStack copy = stack.copy();
            copy.setCount(size);
            items.add(copy);
            remaining -= size;
            added++;
        }
        return added;
    }

    static void handleKitSyncConfirm(KitSyncConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleKitSyncConfirm(payload)));
        }
    }

    // =================================================================================
    // SHOP PURCHASE
    // =================================================================================
    static void handleShopPurchase(ShopPurchasePayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "shop_purchase", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("shop_purchase", player.getName().getString());
                return;
            }

            String itemId = payload.itemId();

            if (itemId == null || itemId.isEmpty()) {
                player.sendSystemMessage(I18n.translate("devmod.network.invalid_item"));
                return;
            }

            RewardSystem.PurchaseResult result = RewardSystem.INSTANCE.purchaseItem(player, itemId);

            if (!result.success()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", result.message()));
            }

            sendShopSync(player);

            LOGGER.info("[Shop] Player {} attempted purchase of {}: {}",
                player.getName().getString(), itemId, result.success() ? "SUCCESS" : result.message());
        });
    }

    // =================================================================================
    // SHOP SYNC (client-side)
    // =================================================================================
    static void handleShopSync(ShopSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleShopSync(payload)));
        }
    }

    static void sendShopSync(ServerPlayer player) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        ShopSyncPayload payload = ShopSyncPayload.fromWallet(wallet);
        sendPacket(player, payload);
    }

    static void handleRequestShopSync(RequestShopSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                sendShopSync(player);
            }
        });
    }

    // =================================================================================
    // PERK CHOICES (client-side)
    // =================================================================================
    static void handlePerkChoices(PerkChoicesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePerkChoices(payload)));
        }
    }

    static void sendPerkChoices(ServerPlayer player, int waveNumber, List<PerkSystem.Perk> perks) {
        List<PerkChoicesPayload.PerkChoice> choices = new ArrayList<>();
        var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());

        Set<String> ownedPerks = sessionOpt
            .map(PerkSystem.PerkSession::getAcquiredPerkIds)
            .orElse(Set.of());

        for (PerkSystem.Perk perk : perks) {
            int currentStacks = sessionOpt.map(s -> s.getPerkStacks(perk.getId())).orElse(0);
            boolean suggested = sessionOpt.map(s -> s.isSuggested(perk.getId())).orElse(false);
            boolean required = sessionOpt.map(s -> s.isRequired(perk.getId()) && !s.hasPerk(perk.getId())).orElse(false);

            PerkSynergySystem.SynergyPreview synergyPreview =
                PerkSynergySystem.INSTANCE.analyzePerk(perk.getId(), ownedPerks);

            choices.add(PerkChoicesPayload.PerkChoice.fromWithSynergy(
                perk, currentStacks, suggested, required, synergyPreview));
        }

        long expiresAt = System.currentTimeMillis() + PERK_SELECTION_TIMEOUT_MS;
        PerkChoicesPayload payload = new PerkChoicesPayload(waveNumber, expiresAt, choices);
        sendPacket(player, payload);
    }

    // =================================================================================
    // PERK SELECTION (server-side)
    // =================================================================================
    static void handlePerkSelection(PerkSelectionPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "perk_selection", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("perk_selection", player.getName().getString());
                return;
            }

            String perkId = payload.perkId();

            if (payload.isSkip()) {
                var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
                if (sessionOpt.isPresent() && sessionOpt.get().hasRequiredPending()) {
                    player.sendSystemMessage(nn(net.minecraft.network.chat.Component.literal(
                        "[DevMod] Required perk must be selected before skipping")
                        .withStyle(SharedColorTokens.Chat.RED)));
                    return;
                }
                player.sendSystemMessage(nn(I18n.translate("devmod.network.perk_skipped")
                    .withStyle(SharedColorTokens.Chat.GRAY)));
                LOGGER.info("[Perk] Player {} skipped perk selection", player.getName().getString());

                PerkSystem.INSTANCE.getSession(player.getUUID())
                    .ifPresent(PerkSystem.PerkSession::clearPendingChoices);
            } else {
                var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
                if (sessionOpt.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.no_perk_session"));
                    return;
                }

                PerkSystem.PerkSession session = sessionOpt.get();
                List<PerkSystem.Perk> pendingChoices = session.getPendingChoices();

                int choiceIndex = -1;
                for (int i = 0; i < pendingChoices.size(); i++) {
                    if (pendingChoices.get(i).getId().equals(perkId)) {
                        choiceIndex = i;
                        break;
                    }
                }

                if (choiceIndex >= 0) {
                    boolean success = PerkSystem.INSTANCE.selectPerk(player, choiceIndex);
                    if (success) {
                        LOGGER.info("[Perk] Player {} selected perk: {}", player.getName().getString(), perkId);
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.network.perk_failed", perkId));
                        LOGGER.warn("[Perk] Failed to apply perk {} for player {}", perkId, player.getName().getString());
                    }
                } else {
                    player.sendSystemMessage(I18n.translate("devmod.network.perk_invalid", perkId));
                    LOGGER.warn("[Perk] Perk {} not found in pending choices for player {}", perkId, player.getName().getString());
                }
            }
        });
    }

    // =================================================================================
    // WAVE DIRECTIVES (risk/reward choices)
    // =================================================================================
    static void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleWaveDirectiveChoices(payload)));
        }
    }

    static void handleWaveDirectiveSelection(WaveDirectiveSelectionPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "wave_directive", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("wave_directive", player.getName().getString());
                return;
            }

            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            if (sessionOpt.isEmpty()) {
                return;
            }
            EnduranceQuestManager.ActiveQuestSession session = sessionOpt.get();
            if (payload.waveNumber() != session.getDirectiveWaveNumber()) {
                return;
            }
            if (!payload.isSkip()) {
                session.selectDirective(payload.directiveId());
            }
        });
    }

    static void sendWaveDirectiveChoices(ServerPlayer player, int waveNumber, List<WaveDirective> directives) {
        List<WaveDirectiveChoicesPayload.DirectiveChoice> choices = new ArrayList<>();
        for (WaveDirective directive : directives) {
            choices.add(WaveDirectiveChoicesPayload.DirectiveChoice.from(directive));
        }
        long expiresAt = System.currentTimeMillis() + DIRECTIVE_SELECTION_TIMEOUT_MS;
        sendPacket(player, new WaveDirectiveChoicesPayload(waveNumber, expiresAt, choices));
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        var future = context.enqueueWork(java.util.Objects.requireNonNull(work));
        if (future.isCancelled()) {
            LOGGER.debug("[EnduranceNetwork] Enqueued work cancelled");
        }
    }
}
