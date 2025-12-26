package com.devmod.client.ui.editor.systems;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class ApplyPresetManagerTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void applyPresetToAll_handlesSuccessesAndFailures() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STICK));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT));

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);

        // Simple Preset that accepts any item
        Preset preset = () -> item -> true;

        // PresetManager that succeeds for first, fails for second
        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                return item == a; // success only for 'a'
            }
        };

        BatchEditResult res = manager.applyPresetToAll(preset, pm);
        assertEquals(1, res.successCount());
        assertEquals(1, res.failureCount());
        List<String> failures = res.failures();
        assertEquals(1, failures.size());
    }

    @Test
    public void applyPresetToAll_respectsScopeAndCollectsFailures() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STICK));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT));
        ItemStack c = new ItemStack(requireNonNull(Items.STONE));

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);
        manager.addToSelection(c, 2);

        // Scope excludes item "B"
        Preset scopedPreset = () -> stack -> stack != b;

        // Only succeeds for item "A"
        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                return item == a;
            }
        };

        BatchEditResult res = manager.applyPresetToAll(scopedPreset, pm);
        assertEquals(1, res.successCount());
        assertEquals(2, res.failureCount());
        assertTrue(res.failureDetails().stream()
            .anyMatch(d -> d.itemId != null && d.itemId.endsWith("dirt") && "Scope mismatch".equals(d.message)));
        assertTrue(res.failureDetails().stream()
            .anyMatch(d -> d.itemId != null && d.itemId.endsWith("stone") && "apply failed".equals(d.message)));
    }
}
