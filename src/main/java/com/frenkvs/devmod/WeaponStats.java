package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

public class WeaponStats {
    public float armorPenetration = 0.0f; // Percentuale di armatura ignorata (0.0 - 1.0)
    public float baseDamageBonus = 0.0f;  // Danno piatto aggiunto

    // Salva i dati nell'oggetto (NBT)
    public void save(CompoundTag tag) {
        tag.putFloat("ArmorPen", armorPenetration);
        tag.putFloat("BaseDmg", baseDamageBonus);
    }

    // Carica i dati
    public static WeaponStats load(CompoundTag tag) {
        WeaponStats stats = new WeaponStats();
        if (tag.contains("ArmorPen")) stats.armorPenetration = tag.getFloat("ArmorPen");
        if (tag.contains("BaseDmg")) stats.baseDamageBonus = tag.getFloat("BaseDmg");
        return stats;
    }
}