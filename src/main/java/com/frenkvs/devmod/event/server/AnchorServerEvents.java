package com.frenkvs.devmod.event.server;

import com.frenkvs.devmod.network.payload.MarkerPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "devmod")
public class AnchorServerEvents {

    @SubscribeEvent
    public static void onServerTick(LevelTickEvent.Post event) {
        // Eseguiamo solo su ServerLevel, ogni 20 tick (1 secondo) per non laggare
        if (event.getLevel().isClientSide || event.getLevel().getGameTime() % 20 != 0) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        if (level.players().isEmpty()) return;

        // Cerca tutti i Marker nel mondo
        List<? extends Marker> markers = level.getEntities(net.minecraft.world.entity.EntityType.MARKER, m -> true);

        List<Vec3> positions = new ArrayList<>();
        for (Marker m : markers) {
            positions.add(m.position());
        }

        // Invia il pacchetto SOLO ai giocatori con OP level 4 o superiore
        if (!positions.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                if (player.hasPermissions(4)) {
                    PacketDistributor.sendToPlayer(player, new MarkerPayload(positions));
                }
            }
        }
    }
}
