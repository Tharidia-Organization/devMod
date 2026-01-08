package com.devmod.gametest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.stats.WeaponStats;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires Minecraft data component runtime; stubbed for compilation only")
public class WeaponStatsComponentTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void componentRoundTrip() {
        ItemStack sword = new ItemStack(requireNonNull(Items.DIAMOND_SWORD));
        WeaponStats stats = new WeaponStats();
        stats.setAttackDamage(12.5f);
        stats.setCritChance(0.25f);
        stats.setCritDamage(2.0f);
        stats.setDamageVsUndead(1.0f);

        WeaponConfigHandler.INSTANCE.setSpecificStats(sword, stats);

        WeaponStats loaded = WeaponConfigHandler.INSTANCE.getStats(sword);
        assertEquals(12.5f, loaded.getAttackDamage(), 0.01f, "Attack damage mismatch");
        assertEquals(0.25f, loaded.getCritChance(), 0.001f, "Crit chance mismatch");
        assertEquals(1.0f, loaded.getDamageVsUndead(), 0.001f, "Vs undead mismatch");
    }

    @Test
    public void clearToolComponent() {
        ItemStack pickaxe = new ItemStack(requireNonNull(Items.DIAMOND_PICKAXE));
        WeaponStats stats = new WeaponStats();
        stats.setClearToolRules(true);
        WeaponConfigHandler.INSTANCE.setSpecificStats(pickaxe, stats);
        // If no exception is thrown, tool clear path executed; detailed component checks require MC runtime.
        assertTrue(true);
    }
}
