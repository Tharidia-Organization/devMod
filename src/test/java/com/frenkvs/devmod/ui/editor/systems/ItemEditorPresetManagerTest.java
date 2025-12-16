package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

public class ItemEditorPresetManagerTest {
    
    private ItemEditorPresetManager manager;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }
    
    @BeforeEach
    void setUp() {
        manager = new ItemEditorPresetManager();
    }
    
    @Test
    void applyPreset_weaponStats_appliesCorrectly() {
        ItemStack sword = new ItemStack(requireNonNull(Items.DIAMOND_SWORD));
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("test_weapon");
        data.statValues = new ArrayList<>();
        // 15 weapon stats expected by manager
        float[] vals = {1,2,3,4,10,2,0,0,0,0,0.2f,1.5f,0,0,0};
        for (float v : vals) data.statValues.add(v);
        DataPreset preset = new DataPreset(data);
        
        boolean result = manager.applyPreset(preset, sword, 0);
        assertTrue(result, "Preset should apply successfully");
    }
    
    @Test
    void applyPreset_armorStats_appliesCorrectly() {
        ItemStack helmet = new ItemStack(requireNonNull(Items.DIAMOND_HELMET));
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("test_armor");
        data.statValues = new ArrayList<>();
        float[] vals = {0.3f,0.2f,0,0,0,5f,0,0,0,1};
        for (float v : vals) data.statValues.add(v);
        DataPreset preset = new DataPreset(data);
        
        boolean result = manager.applyPreset(preset, helmet, 0);
        assertTrue(result, "Preset should apply successfully");
    }
    
    @Test
    void applyPreset_invalidItem_returnsFalse() {
        ItemStack empty = ItemStack.EMPTY;
        
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("test");
        data.statValues = new ArrayList<>();
        DataPreset preset = new DataPreset(data);
        
        boolean result = manager.applyPreset(preset, empty, 0);
        assertFalse(result, "Should fail for empty item");
    }
    
    @Test
    void scopePriority_modpackHigherThanCategory() {
        PresetScope modpack = new PresetScope.Modpack("rlcraft", "sword");
        PresetScope category = new PresetScope.Category("sword");
        PresetScope global = new PresetScope.Global();
        
        assertTrue(modpack.priority() > category.priority());
        assertTrue(category.priority() > global.priority());
    }
}
