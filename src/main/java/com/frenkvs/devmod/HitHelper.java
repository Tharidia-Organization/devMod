package com.frenkvs.devmod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitHelper {

    public enum BodyPart { HEAD, BODY, LEGS }

    // Calcola la parte del corpo basandosi sul punto di impatto Y
    public static BodyPart getBodyPart(LivingEntity target, double hitY) {
        double feetY = target.getY();
        double height = target.getBbHeight();

        if (hitY >= feetY + (height * 0.85)) return BodyPart.HEAD;
        if (hitY <= feetY + (height * 0.4)) return BodyPart.LEGS;
        return BodyPart.BODY;
    }

    // Per le armi MELEE: Simula dove il giocatore sta guardando
    public static BodyPart rayTraceBodyPart(LivingEntity attacker, LivingEntity target) {
        Vec3 eyePos = attacker.getEyePosition();
        Vec3 lookDir = attacker.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookDir.scale(5.0)); // 5 blocchi di reach

        AABB box = target.getBoundingBox();
        // Controllo semplice: Intersezione del raggio con la bounding box
        // (Nota: Per precisione assoluta servirebbe matematica vettoriale complessa,
        // qui facciamo una stima basata sull'altezza relativa degli occhi)

        // Se l'attaccante guarda in basso rispetto al target -> Gambe
        // Se guarda in alto -> Testa
        // Questa è una approssimazione lato server necessaria

        // Calcoliamo l'intersezione approssimativa sulla Y
        double targetY = target.getY();
        double yDiff = eyePos.y - targetY;

        // Se sono alla stessa altezza e guardo dritto:
        double pitch = attacker.getXRot(); // Rotazione verticale (-90 su, +90 giù)

        if (pitch < -15) return BodyPart.HEAD; // Guardo su
        if (pitch > 25) return BodyPart.LEGS;  // Guardo giù

        // Fallback: se colpisco la parte alta della hitbox
        if (eyePos.y > targetY + target.getBbHeight() * 0.8) return BodyPart.HEAD;

        return BodyPart.BODY;
    }
}