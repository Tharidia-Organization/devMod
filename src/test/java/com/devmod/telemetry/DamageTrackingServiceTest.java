package com.devmod.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;
import com.devmod.telemetry.damage.DamageTrackingService;

@DisplayName("DamageTrackingService")
class DamageTrackingServiceTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @BeforeEach
    void reset() {
        DamageTrackingService.INSTANCE.clearAll();
    }

    @Test
    @DisplayName("registerWeaponHit uses weapon override when provided")
    void registerWeaponHitUsesOverride() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));

        DamageTrackingService.INSTANCE.registerWeaponHit(player, Items.DIAMOND_SWORD, 5.0, false);

        var stats = DamageTrackingService.INSTANCE.getWeaponStats(Items.DIAMOND_SWORD);
        assertTrue(stats.isPresent());
        assertEquals(1, stats.get().getHits());
        assertEquals(5.0, stats.get().getTotalDamage(), 0.0001);
    }

    @Test
    @DisplayName("registerWeaponMiss increments miss count for mainhand item")
    void registerWeaponMissIncrementsMisses() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));

        DamageTrackingService.INSTANCE.registerWeaponMiss(player);

        var stats = DamageTrackingService.INSTANCE.getWeaponStats(Items.STICK);
        assertTrue(stats.isPresent());
        assertEquals(1, stats.get().getMisses());
    }
}
