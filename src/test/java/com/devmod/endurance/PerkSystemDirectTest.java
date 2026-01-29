package com.devmod.endurance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.TestBootstrap;
import com.devmod.arena.policy.ArenaPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerkSystemDirectTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("Perk choices offer three unique options after a wave")
    void generatePerkChoicesOffersThreeUniqueOptions() {
        PerkSystem system = PerkSystem.INSTANCE;
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        system.startSession(playerId, null);
        try {
            List<PerkSystem.Perk> choices = system.generatePerkChoices(player, 5);
            assertEquals(3, choices.size());
            assertEquals(3, new HashSet<>(choices).size());
        } finally {
            system.endSession(playerId);
        }
    }

    @Test
    @DisplayName("Required perk bindings override normal selection")
    void requiredPerkBindingsOverrideSelection() {
        PerkSystem system = PerkSystem.INSTANCE;
        UUID playerId = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);

        PerkSystem.PerkSession session = system.startSession(playerId, null);
        session.applyBindings(new ArenaPolicy.PerkBindings(Set.of(), Set.of(), Set.of("sharp_blades")));

        try {
            List<PerkSystem.Perk> choices = system.generatePerkChoices(player, 1);
            assertEquals(1, choices.size());
            assertEquals("sharp_blades", choices.get(0).getId());
        } finally {
            system.endSession(playerId);
        }
    }
}
