package com.devmod.mailbox.client;

import com.devmod.mailbox.network.payload.MailboxAccessPayload;
/**
 * Client-side access flags for mailbox features.
 */
public final class ClientMailboxAccess {

    private static volatile boolean isAdmin;
    private static volatile boolean isTester;

    private ClientMailboxAccess() {}

    public static void update(MailboxAccessPayload payload) {
        if (payload == null) {
            return;
        }
        isAdmin = payload.isAdmin();
        isTester = payload.isTester();
        if (!isTester) {
            ClientTaskCache.clear();
        }
    }

    public static boolean isAdmin() {
        return isAdmin;
    }

    public static boolean isTester() {
        return isTester;
    }

    public static void clear() {
        isAdmin = false;
        isTester = false;
        ClientTaskCache.clear();
    }
}
