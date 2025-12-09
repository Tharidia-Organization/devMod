package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

public class WeaponStats {
    // Moltiplicatori con default da Config (fallback a valori hardcoded se config non caricato)
    public float headMult = getDefaultHeadMult();
    public float bodyMult = getDefaultBodyMult();
    public float armsMult = getDefaultArmsMult();
    public float legsMult = getDefaultLegsMult();
    public float armorPenetration = 0.0f; // Percentuale di armatura ignorata (0.0 - 1.0)
    public float baseDamageBonus = 0.0f;  // Danno piatto aggiunto

    // Config-driven defaults con fallback sicuro
    private static float getDefaultHeadMult() {
        try { return Config.HEAD_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Exception e) { return 1.5f; }
    }

    private static float getDefaultBodyMult() {
        try { return Config.BODY_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Exception e) { return 1.0f; }
    }

    private static float getDefaultArmsMult() {
        try { return Config.ARMS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Exception e) { return 0.8f; }
    }

    private static float getDefaultLegsMult() {
        try { return Config.LEGS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Exception e) { return 0.7f; }
    }

    // Salva i dati nell'oggetto (NBT)
    public void save(CompoundTag tag) {
        tag.putFloat("HeadMult", headMult);
        tag.putFloat("BodyMult", bodyMult);
        tag.putFloat("ArmsMult", armsMult);
        tag.putFloat("LegsMult", legsMult);
        tag.putFloat("ArmorPen", armorPenetration);
        tag.putFloat("BaseDmg", baseDamageBonus);
    }

    // Carica i dati
    public static WeaponStats load(CompoundTag tag) {
        WeaponStats stats = new WeaponStats();
        if (tag.contains("HeadMult")) stats.headMult = tag.getFloat("HeadMult");
        if (tag.contains("BodyMult")) stats.bodyMult = tag.getFloat("BodyMult");
        if (tag.contains("ArmsMult")) stats.armsMult = tag.getFloat("ArmsMult");
        if (tag.contains("LegsMult")) stats.legsMult = tag.getFloat("LegsMult");
        if (tag.contains("ArmorPen")) stats.armorPenetration = tag.getFloat("ArmorPen");
        if (tag.contains("BaseDmg")) stats.baseDamageBonus = tag.getFloat("BaseDmg");
        return stats;
    }
}