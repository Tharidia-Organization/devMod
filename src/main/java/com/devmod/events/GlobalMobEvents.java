package com.devmod.events;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.endurance.ArenaContext;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.telemetry.DeferredEntityProcessor;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID)
public class GlobalMobEvents {

    private static final EnumSet<MobSpawnType> BLOCKED_SPAWN_TYPES = EnumSet.of(
        MobSpawnType.NATURAL,
        MobSpawnType.CHUNK_GENERATION,
        MobSpawnType.PATROL,
        MobSpawnType.STRUCTURE,
        MobSpawnType.EVENT,
        MobSpawnType.REINFORCEMENT,
        MobSpawnType.JOCKEY,
        MobSpawnType.SPAWNER,
        MobSpawnType.TRIAL_SPAWNER
    );

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Server-side only
        if (event.getLevel().isClientSide) return;

        // Only process LivingEntity (includes Mob)
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // PERFORMANCE FIX: Queue for deferred processing instead of immediate
        // This prevents TPS drops when villages/structures spawn many entities
        DeferredEntityProcessor.INSTANCE.queueSpawn(serverLevel, living, "join");
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        // Server-side only, dynamic dimensions only
        if (event.getLevel().isClientSide) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        String dimId = serverLevel.dimension().location().toString();
        if (!dimId.startsWith("devmod:instance_")) return;

        // Reserved for future entity leave hooks (telemetry, cleanup, etc.)
        // Currently no-op after debug logging removed
    }

    @SubscribeEvent
    public static void onMobSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!BLOCKED_SPAWN_TYPES.contains(event.getSpawnType())) return;

        if (isInstanceDimension(serverLevel) || isInsideActiveArena(serverLevel, event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static boolean isInstanceDimension(ServerLevel level) {
        String dimId = level.dimension().location().toString();
        return dimId.startsWith("devmod:instance_");
    }

    private static boolean isInsideActiveArena(ServerLevel level, BlockPos pos) {
        var sessions = EnduranceQuestManager.INSTANCE.getActiveSessions();
        if (sessions.isEmpty()) {
            return false;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        for (EnduranceQuestManager.ActiveQuestSession session : sessions.values()) {
            ArenaContext arena = session.getArena();
            if (arena == null || arena.getLevel() != level) {
                continue;
            }
            AABB bounds = resolveArenaBounds(session);
            if (bounds != null && bounds.contains(x, y, z)) {
                return true;
            }
        }

        return false;
    }

    private static @javax.annotation.Nullable AABB resolveArenaBounds(EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        ArenaHandle handle = session.getArenaHandle();
        if (handle != null && handle.bounds() != null) {
            ArenaHandle.AABB bounds = handle.bounds();
            return new AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0
            );
        }
        ArenaContext arena = session.getArena();
        return arena != null ? arena.getBounds() : null;
    }
}
