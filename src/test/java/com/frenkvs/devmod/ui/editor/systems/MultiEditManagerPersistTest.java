package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MultiEditManagerPersistTest {

    @Test
    public void applyPresetToAll_invokesPresetManager_whenPersistTrue() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack("A");
        ItemStack b = new ItemStack("B");
        ItemStack c = new ItemStack("C");

        manager.addToSelection(a, 0);
        manager.addToSelection(b, 1);
        manager.addToSelection(c, 2);

        AtomicInteger called = new AtomicInteger(0);

        Preset preset = new Preset() {
            @Override public java.util.function.Predicate<ItemStack> scope() { return s -> true; }
        };

        PresetManager pm = new PresetManager() {
            @Override
            public boolean applyPreset(Preset p, ItemStack item, int slotIndex) {
                called.incrementAndGet();
                return true;
            }
        };

        BatchEditResult res = manager.applyPresetToAll(preset, pm, true);

        assertEquals(3, called.get(), "PresetManager should be invoked for each selected item");
        assertEquals(3, res.successCount());
        assertEquals(0, res.failureCount());
    }
}
