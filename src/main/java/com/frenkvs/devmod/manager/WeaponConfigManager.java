package com.frenkvs.devmod.manager;

import com.frenkvs.devmod.config.WeaponStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Map;

public class WeaponConfigManager {

    // Mappa per le impostazioni GLOBALI (Per tipo di oggetto)
    private static final Map<Item, WeaponStats> globalStats = new HashMap<>();

    // Ottiene le statistiche finali per un'arma specifica
    public static WeaponStats getStats(ItemStack stack) {
        // 1. Controlla se l'arma ha modifiche SPECIFICHE (NBT)
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("WeaponModStats")) {
            return WeaponStats.load(customData.copyTag().getCompound("WeaponModStats"));
        }

        // 2. Se non ha modifiche specifiche, usa quelle GLOBALI
        if (globalStats.containsKey(stack.getItem())) {
            return globalStats.get(stack.getItem());
        }

        // 3. Altrimenti ritorna statistiche base
        return new WeaponStats();
    }

    public static void setGlobalStats(Item item, WeaponStats stats) {
        globalStats.put(item, stats);
    }

    public static void setSpecificStats(ItemStack stack, WeaponStats stats) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            stats.save(statsTag);
            tag.put("WeaponModStats", statsTag);
        });
    }
}