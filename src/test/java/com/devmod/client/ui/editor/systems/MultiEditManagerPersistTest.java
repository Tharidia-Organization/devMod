package com.devmod.client.ui.editor.systems;

import com.devmod.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

public class MultiEditManagerPersistTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void applyPresetToAll_invokesPresetManager_whenPersistTrue() {
        MultiEditManager manager = new MultiEditManager();

        ItemStack a = new ItemStack(requireNonNull(Items.STICK));
        ItemStack b = new ItemStack(requireNonNull(Items.DIRT));
        ItemStack c = new ItemStack(requireNonNull(Items.STONE));

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
