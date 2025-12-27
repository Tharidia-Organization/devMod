package com.devmod.client.party;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.notification.PartyInviteActionData;

@OnlyIn(Dist.CLIENT)
public final class PartyUiCache {
    @Nullable
    private static volatile PartyInviteActionData lastInvite;

    private PartyUiCache() {}

    public static void setLastInvite(@Nullable PartyInviteActionData payload) {
        lastInvite = payload;
    }

    @Nullable
    public static PartyInviteActionData getActiveInvite() {
        PartyInviteActionData invite = lastInvite;
        if (invite == null) {
            return null;
        }
        if (invite.expiresAt() <= System.currentTimeMillis()) {
            lastInvite = null;
            return null;
        }
        return invite;
    }

    public static void clear() {
        lastInvite = null;
    }
}
