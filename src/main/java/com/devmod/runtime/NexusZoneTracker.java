package com.devmod.runtime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.devmod.config.Config;

public final class NexusZoneTracker {
    private static final Map<UUID, ZoneState> STATES = new HashMap<>();

    private NexusZoneTracker() {}

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!Config.NEXUS_ZONE_CUES.get() && !Config.NEXUS_ZONE_HINTS.get()) {
            return;
        }

        long now = level.getGameTime();
        int cooldown = Math.max(0, Config.NEXUS_ZONE_CUE_COOLDOWN.get());
        BlockPos origin = NexusDimensionManager.getHubOrigin();
        Set<UUID> seen = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            seen.add(id);
            NexusZoneLayout.Zone zone = NexusZoneLayout.resolveZone(player.blockPosition(), origin);
            ZoneState state = STATES.computeIfAbsent(id, ignored -> new ZoneState());
            if (zone != state.zone) {
                state.zone = zone;
                if (zone != NexusZoneLayout.Zone.OUTSIDE && now - state.lastCueTick >= cooldown) {
                    if (Config.NEXUS_ZONE_CUES.get()) {
                        playCue(player, zone);
                    }
                    if (Config.NEXUS_ZONE_HINTS.get()) {
                        showHint(player, zone);
                    }
                    state.lastCueTick = now;
                }
            }
        }

        STATES.keySet().retainAll(seen);
    }

    public static void clear(UUID playerId) {
        STATES.remove(playerId);
    }

    private static void playCue(ServerPlayer player, NexusZoneLayout.Zone zone) {
        SoundEvent sound = switch (zone) {
            case COMBAT -> SoundEvents.NOTE_BLOCK_PLING.value();
            case ARENA -> SoundEvents.NOTE_BLOCK_BELL.value();
            case UI -> SoundEvents.NOTE_BLOCK_BIT.value();
            case TELEMETRY -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case SHOWCASE -> SoundEvents.NOTE_BLOCK_HARP.value();
            case INTEGRATION -> SoundEvents.NOTE_BLOCK_BASS.value();
            case SANDBOX -> SoundEvents.NOTE_BLOCK_COW_BELL.value();
            case MECHANICS -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
            case AFK -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case QUIET -> SoundEvents.NOTE_BLOCK_HAT.value();
            case PERFORMANCE -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case OVERVIEW -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case HUB -> SoundEvents.NOTE_BLOCK_CHIME.value();
            default -> SoundEvents.NOTE_BLOCK_CHIME.value();
        };
        player.level().playSound(null, nn(player.blockPosition(), "player.blockPosition"), nn(sound, "sound"), SoundSource.PLAYERS, 0.45f, 1.1f);
    }

    private static void showHint(ServerPlayer player, NexusZoneLayout.Zone zone) {
        String message = switch (zone) {
            case COMBAT -> "Combat zone: range + melee lanes, dummies, target wall.";
            case ARENA -> "Arena zone: staging, briefing wall, wave bowl.";
            case UI -> "UI lab: wall screens, console pods, photo bay.";
            case TELEMETRY -> "Telemetry lab: heatmap, live dashboard, log desk.";
            case SHOWCASE -> "Showcase: test pads, XL cases, item plinths.";
            case INTEGRATION -> "Integration: pods show active mods (gray = missing).";
            case SANDBOX -> "Sandbox: build area + performance bay.";
            case MECHANICS -> "Mechanics: redstone rigs and extended benches.";
            case AFK -> "AFK alcove: safe idle spot.";
            case QUIET -> "Quiet bay: isolated testing booth.";
            case PERFORMANCE -> "Performance bay: stress/lag tests.";
            case OVERVIEW -> "Overview deck: full layout scan.";
            case HUB -> "Hub: workflow nodes, tools, /devmod nexus hub.";
            default -> null;
        };
        if (message != null) {
            player.displayClientMessage(nn(Component.literal(message), "zoneMessage"), true);
        }
    }

    private static final class ZoneState {
        private NexusZoneLayout.Zone zone = NexusZoneLayout.Zone.OUTSIDE;
        private long lastCueTick;
    }

    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
