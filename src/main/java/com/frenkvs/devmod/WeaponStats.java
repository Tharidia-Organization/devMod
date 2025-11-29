package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

public class WeaponStats {
    public float headMult = 1.0f; // Moltiplicatore Testa (es. 1.5 = +50%)
    public float bodyMult = 1.0f;
    public float legsMult = 1.0f;
    public float armorPenetration = 0.0f; // Percentuale di armatura ignorata (0.0 - 1.0)
    public float baseDamageBonus = 0.0f;  // Danno piatto aggiunto

    // Salva i dati nell'oggetto (NBT)
    public void save(CompoundTag tag) {
        tag.putFloat("HeadMult", headMult);
        tag.putFloat("BodyMult", bodyMult);
        tag.putFloat("LegsMult", legsMult);
        tag.putFloat("ArmorPen", armorPenetration);
        tag.putFloat("BaseDmg", baseDamageBonus);
    }

    // Carica i dati
    public static WeaponStats load(CompoundTag tag) {
        WeaponStats stats = new WeaponStats();
        if (tag.contains("HeadMult")) stats.headMult = tag.getFloat("HeadMult");
        if (tag.contains("BodyMult")) stats.bodyMult = tag.getFloat("BodyMult");
        if (tag.contains("LegsMult")) stats.legsMult = tag.getFloat("LegsMult");
        if (tag.contains("ArmorPen")) stats.armorPenetration = tag.getFloat("ArmorPen");
        if (tag.contains("BaseDmg")) stats.baseDamageBonus = tag.getFloat("BaseDmg");
        return stats;
    }
}