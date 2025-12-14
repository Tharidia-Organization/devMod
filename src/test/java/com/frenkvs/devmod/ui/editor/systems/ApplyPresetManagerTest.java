package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApplyPresetManagerTest {

    @Test
    public void applyPresetToAll_handlesSuccessesAndFailures() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack("A");
        ItemStack b = new ItemStack("B");

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);

        // Simple Preset that accepts any item
        Preset preset = () -> item -> true;

        // PresetManager that succeeds for first, fails for second
        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                return "A".equals(item.getHoverName().getString()); // success only for 'a'
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

        ItemStack a = new ItemStack("A");
        ItemStack b = new ItemStack("B");
        ItemStack c = new ItemStack("C");

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);
        manager.addToSelection(c, 2);

        // Scope excludes item "B"
        Preset scopedPreset = () -> stack -> !"B".equals(stack.getHoverName().getString());

        // Only succeeds for item "A"
        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset preset, ItemStack item, int slotIndex) {
                return "A".equals(item.getHoverName().getString());
            }
        };

        BatchEditResult res = manager.applyPresetToAll(scopedPreset, pm);
        assertEquals(1, res.successCount());
        assertEquals(2, res.failureCount());
        assertTrue(res.failures().stream().anyMatch(s -> s.contains("B") && s.contains("Scope")));
        assertTrue(res.failures().stream().anyMatch(s -> s.contains("C") && s.contains("apply failed")));
    }
}
