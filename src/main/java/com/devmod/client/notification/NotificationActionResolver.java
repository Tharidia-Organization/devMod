package com.devmod.client.notification;

import javax.annotation.Nullable;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.notification.ui.NotificationCenterScreen;
import com.devmod.client.party.PartyUiCache;
import com.devmod.endurance.QuestType;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCenterActionData;
import com.devmod.notification.PartyInviteActionData;
import com.devmod.party.PartyNotificationPayload;

/**
 * Resolves and invokes actions attached to unified notifications.
 */
public final class NotificationActionResolver {

    private NotificationActionResolver() {}

    public static boolean invoke(Notification notification, ActionOrigin origin) {
        if (notification == null) {
            return false;
        }

        String actionId = notification.actionId();
        if (actionId == null || actionId.isBlank()) {
            return false;
        }

        if (ActionIds.UI_NOTIFICATION_CENTER_OPEN.equals(actionId)) {
            NotificationCenterActionData data = NotificationCenterActionData.fromJson(notification.actionDataJson());
            if (data != null) {
                NotificationCenterScreen.open(data.tab(), data.entityId());
                return true;
            }
        }

        Object payload = buildPayload(actionId, notification);
        if (payload instanceof PartyNotificationPayload partyPayload) {
            PartyUiCache.setLastInvite(partyPayload);
        }

        if (payload != null) {
            return ActionRegistry.invoke(actionId, ClientActionContexts.forClient(origin, payload));
        }
        return ActionRegistry.invoke(actionId, ClientActionContexts.forClient(origin));
    }

    @Nullable
    private static Object buildPayload(String actionId, Notification notification) {
        if (ActionIds.UI_PARTY_INVITE_POPUP_OPEN.equals(actionId)) {
            PartyInviteActionData data = PartyInviteActionData.fromJson(notification.actionDataJson());
            if (data == null) {
                return null;
            }
            QuestType questType = QuestType.fromOrdinal(data.questTypeOrdinal());
            return PartyNotificationPayload.inviteReceived(
                data.inviteId(),
                data.senderName(),
                questType,
                data.expiresAt()
            );
        }

        return null;
    }
}
