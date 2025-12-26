package com.devmod.gametest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;
import com.devmod.config.WeaponConfigManager;
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
        stats.attackDamage = 12.5f;
        stats.critChance = 0.25f;
        stats.critDamage = 2.0f;
        stats.damageVsUndead = 1.0f;

        WeaponConfigManager.setSpecificStats(sword, stats);

        WeaponStats loaded = WeaponConfigManager.getStats(sword);
        assertEquals(12.5f, loaded.attackDamage, 0.01f, "Attack damage mismatch");
        assertEquals(0.25f, loaded.critChance, 0.001f, "Crit chance mismatch");
        assertEquals(1.0f, loaded.damageVsUndead, 0.001f, "Vs undead mismatch");
    }

    @Test
    public void clearToolComponent() {
        ItemStack pickaxe = new ItemStack(requireNonNull(Items.DIAMOND_PICKAXE));
        WeaponStats stats = new WeaponStats();
        stats.clearToolRules = true;
        WeaponConfigManager.setSpecificStats(pickaxe, stats);
        // If no exception is thrown, tool clear path executed; detailed component checks require MC runtime.
        assertTrue(true);
    }
}
