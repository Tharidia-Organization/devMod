package com.frenkvs.devmod.event.common;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

@EventBusSubscriber(modid = "devmod")
public class CommonModEvents {

    @SubscribeEvent
    public static void modifyEntityAttributes(EntityAttributeModificationEvent event) {
        System.out.println(">>> [DEBUG] Inizio scansione entità...");
        int successCount = 0;
        int failCount = 0;

        // Scansiona TUTTI i tipi di entità del gioco
        for (EntityType<?> type : event.getTypes()) {

            // Controlla se è un essere vivente
            if (type.getBaseClass() != null && LivingEntity.class.isAssignableFrom(type.getBaseClass())) {

                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) type;

                // --- FORZA BRUTA ---
                // Proviamo ad aggiungere l'attributo SENZA controllare se c'è già.
                // Se c'è già, il gioco lancerà un errore, noi lo catturiamo e andiamo avanti.
                try {
                    event.add(livingType, Attributes.ENTITY_INTERACTION_RANGE, 0.0);
                    successCount++;

                    // Stampiamo i primi 3 per vedere se funziona (es. Zombie, Scheletro)
                    if (successCount <= 3) {
                        System.out.println(">>> [DEBUG] Aggiunto Reach a: " + EntityType.getKey(type));
                    }

                } catch (IllegalArgumentException e) {
                    // Significa che ce l'aveva già.
                    // Ma se ce l'aveva già, perché in gioco dava errore?
                    // Potrebbe essere che ce l'ha ma non è inizializzato.
                    // Non possiamo farci molto se non loggarlo.
                    failCount++;
                }
            }
        }

        System.out.println(">>> [DEBUG] Fine. Aggiunti: " + successCount + " | Già presenti (Skipped): " + failCount);
    }
}