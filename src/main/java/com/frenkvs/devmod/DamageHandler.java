package com.frenkvs.devmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.GAME)
public class DamageHandler {

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity victim && event.getSource().getEntity() instanceof LivingEntity attacker) {

            ItemStack weapon = ItemStack.EMPTY;
            HitHelper.BodyPart part = HitHelper.BodyPart.BODY;
            boolean isRanged = false;

            // 1. Identifichiamo l'arma e la parte colpita
            if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
                // RANGED
                isRanged = true;
                // Le frecce non hanno un "itemstack" facile, ma possiamo stimare dal proprietario o usare default
                // Per semplicità qui usiamo l'arma in mano all'attaccante se è un arco
                weapon = attacker.getMainHandItem();
                part = HitHelper.getBodyPart(victim, arrow.getY());
            } else {
                // MELEE
                weapon = attacker.getMainHandItem();
                part = HitHelper.rayTraceBodyPart(attacker, victim);
            }

            // 2. Recuperiamo le Statistiche (Globali o Specifiche)
            WeaponStats stats = WeaponConfigManager.getStats(weapon);

            // 3. Calcolo Moltiplicatore
            float multiplier = 1.0f;
            String partName = "CORPO";
            int color = 0xFFFFFF;

            switch (part) {
                case HEAD -> { multiplier = stats.headMult; partName = "TESTA"; color = 0xFF5555; }
                case BODY -> { multiplier = stats.bodyMult; partName = "TORSO"; color = 0x55FF55; }
                case LEGS -> { multiplier = stats.legsMult; partName = "GAMBE"; color = 0x55FFFF; }
            }

            // 4. Calcolo Danni Finali
            float originalDamage = event.getAmount();
            float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;

            // 5. Penetrazione Armatura (Simulata aumentando il danno)
            // Se ArmorPen è 0.5 (50%), aumentiamo il danno per compensare l'armatura del bersaglio
            if (stats.armorPenetration > 0) {
                float armorVal = victim.getArmorValue();
                // Stima grezza: aggiungiamo danno extra pari a quanto l'armatura avrebbe bloccato
                // (È complesso farlo perfetto, questo è un boost "True Damage")
                float ignoredArmor = armorVal * stats.armorPenetration;
                // Aggiungiamo un bonus basato sull'armatura ignorata
                newDamage += (ignoredArmor * 0.5f);
            }

            // 6. Applica
            event.setAmount(newDamage);

            // 7. Feedback Visivo
            if (attacker instanceof ServerPlayer player) {
                String dmgText = String.format("%.1f", newDamage);
                String penText = stats.armorPenetration > 0 ? " [Pen]" : "";

                player.displayClientMessage(Component.literal("§7Hit: §" + getChar(color) + partName + " §fDmg: " + dmgText + penText), true);
            }
        }
    }

    private static char getChar(int color) {
        if (color == 0xFF5555) return 'c';
        if (color == 0x55FFFF) return 'b';
        return 'a';
    }
}