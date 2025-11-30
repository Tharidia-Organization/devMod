package com.frenkvs.devmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.text.DecimalFormat;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class ClientModEvents {

    private static final DecimalFormat df = new DecimalFormat("#.##");

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("devmod", "mob_stats"), new MobStatsLayer());
    }

    public static class MobStatsLayer implements LayeredDraw.Layer {

        @Override
        public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
            if (!ModConfig.showOverlay) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            HitResult hitResult = mc.hitResult;

            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof LivingEntity mob) {
                    renderMobStats(guiGraphics, mob, mc);
                }
            }
        }

        private void renderMobStats(GuiGraphics gui, LivingEntity entity, Minecraft mc) {
            int x = mc.getWindow().getGuiScaledWidth() / 2 + 10;
            int y = mc.getWindow().getGuiScaledHeight() / 2 - 10;
            int lineHeight = 10;

            // --- DATI ---
            float hp = entity.getHealth();
            float maxHp = entity.getMaxHealth();
            int armor = entity.getArmorValue();

            // --- Reach ---
            double rawReach = 0;
            // Ora .getAttribute NON dovrebbe mai ritornare null grazie a CommonModEvents,
            // ma lasciamo il controllo per sicurezza assoluta.
            if (entity.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) {
                rawReach = entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            }

            // Testo da mostrare
            String reachText;
            int reachColor;

            if (rawReach > 0) {
                // Se è > 0, significa che l'abbiamo modificato noi
                reachText = "Reach (MOD): " + df.format(rawReach);
                reachColor = 0xFFFF00; // Giallo
            } else {
                // Se è 0, è il valore vanilla. Calcoliamolo per mostrarlo all'utente.
                double estimated = entity.getBbWidth() * 2.0 + 1.0;
                reachText = "Reach (Vanilla): " + df.format(estimated);
                reachColor = 0xAAAAAA; // Grigio
            }

            // --- DISEGNO ---
            gui.drawString(mc.font, "Nome: " + entity.getName().getString(), x, y, 0xFFFF00);
            y += lineHeight;

            gui.drawString(mc.font, "HP: " + df.format(hp) + " / " + df.format(maxHp), x, y, 0xFF5555);
            y += lineHeight;

            // Armor
            float damageReduction = armor * 4.0f;
            if (damageReduction > 80.0f) damageReduction = 80.0f;
            gui.drawString(mc.font, "Armor: " + armor + " (-" + (int)damageReduction + "%)", x, y, 0x5555FF);
            y += lineHeight;

            // Danno
            double dmg = 0;
            if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) dmg = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (dmg > 0) {
                gui.drawString(mc.font, "Danno: " + df.format(dmg) + " (" + df.format(dmg/2.0) + " Cuori)", x, y, 0xFFAAAA);
                y += lineHeight;
            }

            // Vista (Follow Range)
            double follow = 0;
            if (entity.getAttribute(Attributes.FOLLOW_RANGE) != null) follow = entity.getAttributeValue(Attributes.FOLLOW_RANGE);
            gui.drawString(mc.font, "Vista: " + df.format(follow), x, y, 0x00FF00);
            y += lineHeight;

            // REACH (Finalmente visibile!)
            gui.drawString(mc.font, reachText, x, y, reachColor);
            y += lineHeight;

            // Target
            String target = (entity instanceof Mob mob && mob.getTarget() != null) ? mob.getTarget().getName().getString() : "Nessuno";
            gui.drawString(mc.font, "Target: " + target, x, y, 0xFFA500);
        }
    }
}