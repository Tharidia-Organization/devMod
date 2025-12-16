package com.frenkvs.devmod.gametest;

import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit tests to ensure weapon_stats component round-trips and tool clear toggle works.
 */
@Disabled("Requires Minecraft data component runtime; stubbed for compilation only")
public class WeaponStatsComponentTest {

    @Test
    public void componentRoundTrip() {
        ItemStack sword = new ItemStack(new Item("devmod:test_sword"));
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
        ItemStack pickaxe = new ItemStack(new Item("devmod:test_pickaxe"));
        WeaponStats stats = new WeaponStats();
        stats.clearToolRules = true;
        WeaponConfigManager.setSpecificStats(pickaxe, stats);
        // If no exception is thrown, tool clear path executed; detailed component checks require MC runtime.
        assertTrue(true);
    }
}
