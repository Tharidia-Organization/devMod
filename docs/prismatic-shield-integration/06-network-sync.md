# 06 - Network Synchronization

## Obiettivo

Sincronizzare lo stato dello scudo energetico tra server e client in multiplayer:
- Stato scudo (attivo/inattivo/in rigenerazione)
- Impatti (per VFX su altri client)
- Shatter events
- Deflection events

## Architettura Network

```
┌─────────────────────────────────────────────────────────────┐
│                        SERVER                               │
│  ┌─────────────────┐    ┌──────────────────┐               │
│  │ DamageHandler   │───>│ ShieldStateSync  │               │
│  │ (shield logic)  │    │ (packet sender)  │               │
│  └─────────────────┘    └────────┬─────────┘               │
└─────────────────────────────────────────────────────────────┘
                                   │
                          ─────────┼─────────  Network
                                   │
┌─────────────────────────────────────────────────────────────┐
│                        CLIENTS                              │
│  ┌──────────────────┐    ┌─────────────────┐               │
│  │ ShieldStateSync  │───>│ ShieldRenderer  │               │
│  │ (packet handler) │    │ (VFX display)   │               │
│  └──────────────────┘    └─────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

## Packet Definitions

### 1. ShieldStatePacket

Sincronizza lo stato base dello scudo.

```java
package com.devmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Syncs shield state from server to all tracking clients.
 */
public record ShieldStatePacket(
    int entityId,
    boolean shieldActive,
    float shieldHealth,     // Current health (0 = broken)
    float maxShieldHealth,  // Max health
    float regenProgress     // 0-1 progress toward full regen
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "shield_state");

    public static final Type<ShieldStatePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShieldStatePacket> STREAM_CODEC =
        StreamCodec.of(ShieldStatePacket::write, ShieldStatePacket::read);

    public static ShieldStatePacket read(FriendlyByteBuf buf) {
        return new ShieldStatePacket(
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat()
        );
    }

    public static void write(FriendlyByteBuf buf, ShieldStatePacket packet) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.shieldActive);
        buf.writeFloat(packet.shieldHealth);
        buf.writeFloat(packet.maxShieldHealth);
        buf.writeFloat(packet.regenProgress);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

### 2. ShieldImpactPacket

Notifica un impatto per triggerare VFX su tutti i client.

```java
package com.devmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Notifies all tracking clients of a shield impact (for VFX).
 */
public record ShieldImpactPacket(
    int entityId,
    double hitX,
    double hitY,
    double hitZ,
    float damageBlocked,
    boolean isDeflection  // True if projectile was deflected
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "shield_impact");

    public static final Type<ShieldImpactPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShieldImpactPacket> STREAM_CODEC =
        StreamCodec.of(ShieldImpactPacket::write, ShieldImpactPacket::read);

    public static ShieldImpactPacket read(FriendlyByteBuf buf) {
        return new ShieldImpactPacket(
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readBoolean()
        );
    }

    public static void write(FriendlyByteBuf buf, ShieldImpactPacket packet) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.hitX);
        buf.writeDouble(packet.hitY);
        buf.writeDouble(packet.hitZ);
        buf.writeFloat(packet.damageBlocked);
        buf.writeBoolean(packet.isDeflection);
    }

    public Vec3 getHitPoint() {
        return new Vec3(hitX, hitY, hitZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

### 3. ShieldShatterPacket

Notifica che uno scudo si è rotto.

```java
package com.devmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Notifies all tracking clients that a shield has shattered.
 */
public record ShieldShatterPacket(
    int entityId,
    double centerX,
    double centerY,
    double centerZ,
    float radius,
    int shieldColor
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "shield_shatter");

    public static final Type<ShieldShatterPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShieldShatterPacket> STREAM_CODEC =
        StreamCodec.of(ShieldShatterPacket::write, ShieldShatterPacket::read);

    public static ShieldShatterPacket read(FriendlyByteBuf buf) {
        return new ShieldShatterPacket(
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readInt()
        );
    }

    public static void write(FriendlyByteBuf buf, ShieldShatterPacket packet) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.centerX);
        buf.writeDouble(packet.centerY);
        buf.writeDouble(packet.centerZ);
        buf.writeFloat(packet.radius);
        buf.writeInt(packet.shieldColor);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

## Packet Registration

```java
package com.devmod.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ShieldNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("devmod").versioned("1.0");

        // Shield State - Server to Client only
        registrar.playToClient(
            ShieldStatePacket.TYPE,
            ShieldStatePacket.STREAM_CODEC,
            ShieldNetworkHandler::handleShieldState
        );

        // Shield Impact - Server to Client only
        registrar.playToClient(
            ShieldImpactPacket.TYPE,
            ShieldImpactPacket.STREAM_CODEC,
            ShieldNetworkHandler::handleShieldImpact
        );

        // Shield Shatter - Server to Client only
        registrar.playToClient(
            ShieldShatterPacket.TYPE,
            ShieldShatterPacket.STREAM_CODEC,
            ShieldNetworkHandler::handleShieldShatter
        );
    }

    // === Client Handlers ===

    private static void handleShieldState(ShieldStatePacket packet,
                                          net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            var entity = mc.level.getEntity(packet.entityId());
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                // Update client-side shield state cache
                ClientShieldStateCache.update(living, packet);
            }
        });
    }

    private static void handleShieldImpact(ShieldImpactPacket packet,
                                           net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            var entity = mc.level.getEntity(packet.entityId());
            if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                // Trigger impact VFX
                net.minecraft.world.phys.Vec3 hitPoint = packet.getHitPoint();
                net.minecraft.world.phys.Vec3 localHit = hitPoint.subtract(entity.position());

                com.devmod.client.vfx.ShieldImpactManager.registerImpact(
                    packet.entityId(),
                    localHit,
                    packet.damageBlocked()
                );

                // Play sound
                mc.level.playLocalSound(
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f,
                    packet.isDeflection() ? 1.3f : 1.0f,
                    false
                );
            }
        });
    }

    private static void handleShieldShatter(ShieldShatterPacket packet,
                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            // Trigger shatter VFX
            com.devmod.client.vfx.ShieldShatterEffect.trigger(
                packet.entityId(),
                new net.minecraft.world.phys.Vec3(packet.centerX(), packet.centerY(), packet.centerZ()),
                packet.radius(),
                packet.shieldColor()
            );

            // Play shatter sound
            mc.level.playLocalSound(
                packet.centerX(), packet.centerY(), packet.centerZ(),
                net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0f, 0.8f, false
            );
        });
    }
}
```

## Client-Side State Cache

Per rendere lo scudo senza aspettare il server ogni frame:

```java
package com.devmod.client;

import com.devmod.network.ShieldStatePacket;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of shield states.
 * Updated by network packets, used by renderer.
 */
public class ClientShieldStateCache {

    private static final Map<Integer, ShieldState> cache = new ConcurrentHashMap<>();

    public record ShieldState(
        boolean active,
        float health,
        float maxHealth,
        float regenProgress,
        long lastUpdate
    ) {
        public float getHealthPercent() {
            return maxHealth > 0 ? health / maxHealth : 0;
        }

        public boolean isStale() {
            // Consider stale after 5 seconds without update
            return System.currentTimeMillis() - lastUpdate > 5000;
        }
    }

    public static void update(LivingEntity entity, ShieldStatePacket packet) {
        cache.put(entity.getId(), new ShieldState(
            packet.shieldActive(),
            packet.shieldHealth(),
            packet.maxShieldHealth(),
            packet.regenProgress(),
            System.currentTimeMillis()
        ));
    }

    public static ShieldState get(LivingEntity entity) {
        ShieldState state = cache.get(entity.getId());
        if (state != null && state.isStale()) {
            cache.remove(entity.getId());
            return null;
        }
        return state;
    }

    public static boolean isShieldActive(LivingEntity entity) {
        ShieldState state = get(entity);
        return state != null && state.active();
    }

    public static void clear() {
        cache.clear();
    }

    public static void removeEntity(int entityId) {
        cache.remove(entityId);
    }
}
```

## Server-Side Sync Logic

Chiamare dal DamageHandler quando lo scudo cambia stato:

```java
package com.devmod.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side methods to send shield packets.
 */
public class ShieldPacketSender {

    /**
     * Sends shield state to all tracking players.
     */
    public static void sendShieldState(LivingEntity entity, boolean active,
                                       float health, float maxHealth, float regenProgress) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ShieldStatePacket packet = new ShieldStatePacket(
                entity.getId(), active, health, maxHealth, regenProgress
            );

            // Send to all players tracking this entity
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);

            // Also send to self if entity is a player
            if (entity instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /**
     * Sends shield impact to all tracking players.
     */
    public static void sendShieldImpact(LivingEntity entity, Vec3 hitPoint,
                                        float damageBlocked, boolean isDeflection) {
        if (entity.level() instanceof ServerLevel) {
            ShieldImpactPacket packet = new ShieldImpactPacket(
                entity.getId(),
                hitPoint.x, hitPoint.y, hitPoint.z,
                damageBlocked,
                isDeflection
            );

            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);

            if (entity instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /**
     * Sends shield shatter to all tracking players.
     */
    public static void sendShieldShatter(LivingEntity entity, Vec3 center,
                                         float radius, int color) {
        if (entity.level() instanceof ServerLevel) {
            ShieldShatterPacket packet = new ShieldShatterPacket(
                entity.getId(),
                center.x, center.y, center.z,
                radius,
                color
            );

            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);

            if (entity instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
```

## Integration in DamageHandler

```java
// DamageHandler.java - aggiungere chiamate ai packet

private static void applyShieldBlock(Player player, ArmorStats stats,
                                     DamageSource source, float damage) {

    float damageBlocked = damage * stats.shieldBlockStrength;

    // Update shield health (server-side tracking)
    float currentHealth = getShieldHealth(player);
    float newHealth = currentHealth - damageBlocked;
    setShieldHealth(player, Math.max(0, newHealth));

    // === NETWORK: Send impact packet ===
    Vec3 hitPoint = calculateHitPoint(player, source);
    boolean isDeflection = stats.shieldReflectProjectiles &&
                          source.getDirectEntity() instanceof Projectile;

    ShieldPacketSender.sendShieldImpact(player, hitPoint, damageBlocked, isDeflection);

    // === NETWORK: Send state update ===
    ShieldPacketSender.sendShieldState(
        player,
        newHealth > 0,
        newHealth,
        stats.shieldShatterThreshold,
        0f // regen progress
    );

    // Check if shield broke
    if (newHealth <= 0) {
        // === NETWORK: Send shatter packet ===
        float radius = player.getBbWidth() * 0.8f + 0.5f;
        Vec3 center = player.position().add(0, player.getBbHeight() / 2, 0);
        ShieldPacketSender.sendShieldShatter(player, center, radius, stats.shieldColor);

        // Start regen timer if enabled
        if (stats.shieldAutoRegenerate) {
            scheduleShieldRegen(player, stats.shieldRegenDelay);
        }
    }

    // ... rest of existing logic ...
}
```

## Performance Considerations

| Packet | Size | Frequency | Optimization |
|--------|------|-----------|--------------|
| ShieldState | ~17 bytes | On change only | Throttle to max 10/sec |
| ShieldImpact | ~25 bytes | Per impact | Natural throttle (combat rate) |
| ShieldShatter | ~29 bytes | Rare | No throttle needed |

### Throttling

```java
// Throttle state updates to avoid packet spam

private static final Map<Integer, Long> lastStateSent = new ConcurrentHashMap<>();
private static final long STATE_THROTTLE_MS = 100; // Max 10 updates/sec

public static void sendShieldStateThrottled(LivingEntity entity, ...) {
    long now = System.currentTimeMillis();
    Long last = lastStateSent.get(entity.getId());

    if (last == null || now - last >= STATE_THROTTLE_MS) {
        lastStateSent.put(entity.getId(), now);
        sendShieldState(entity, ...);
    }
}
```

## Testing Multiplayer

1. **Test 2 players**
   - Player A attacca Player B con scudo
   - Verificare che Player A veda l'effetto impatto
   - Verificare che spettatori vedano l'effetto

2. **Test latenza**
   - Simulare lag (tc netem o simile)
   - Verificare che VFX rimanga sincronizzato

3. **Test reconnection**
   - Player si disconnette e riconnette
   - Verificare che stato scudo sia corretto

4. **Test many players**
   - 10+ giocatori in combattimento
   - Verificare performance e bandwidth
