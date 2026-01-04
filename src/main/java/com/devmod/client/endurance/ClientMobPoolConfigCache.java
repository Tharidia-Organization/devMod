package com.devmod.client.endurance;

import javax.annotation.Nullable;

import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.config.ConfigScope;

public final class ClientMobPoolConfigCache {

    private static volatile @Nullable MobPoolConfigSyncPayload globalConfig;
    private static volatile @Nullable MobPoolConfigSyncPayload sessionConfig;

    private ClientMobPoolConfigCache() {}

    public static void update(MobPoolConfigSyncPayload payload) {
        if (payload == null || payload.data() == null) {
            return;
        }
        switch (payload.data().scope()) {
            case GLOBAL -> globalConfig = payload;
            case SESSION -> sessionConfig = payload;
            case PROPOSAL -> globalConfig = payload;
        }
    }

    public static @Nullable MobPoolConfigSyncPayload get(ConfigScope scope) {
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case GLOBAL, PROPOSAL -> globalConfig;
            case SESSION -> sessionConfig;
        };
    }

    public static void clear() {
        globalConfig = null;
        sessionConfig = null;
    }
}
