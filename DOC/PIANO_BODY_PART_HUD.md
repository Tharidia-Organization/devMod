# Piano Implementazione: Body Part HUD System
## Analisi Immagine Completa + Implementazione Dettagliata

---

## 1. ANALISI COMPLETA DELL'IMMAGINE

### 1.1 Entity Info Bar (Top-Left)
```
┌─────────────────────────────────────────────┐
│ Zombified Piglin Brute                      │  ← Nome entità (getDisplayName())
│ (Influenced by Pehkui, scale: 2.8x)         │  ← Tag mod + scala (ScaleTypes.BASE)
│ Pehkui Scaled Hitbox: 2.0x Multiplier       │  ← Hitbox scale separato (HITBOX_WIDTH/HEIGHT)
└─────────────────────────────────────────────┘
```

### 1.2 Body Part Labels (On Entity)
```
Head [Crit x2.0]      ← Label 3D + moltiplicatore
Torso [x0.9]          ← Verde
Left Arm [x0.9]       ← Giallo
Right Arm [x0.9]      ← Giallo
Legs [x0.75]          ← Rosso
```

### 1.3 Impact Analysis Panel (Right-Top)
```
┌─ Impact Analysis (Multi-Part & Mod Integrated) ─┐
│                                                   │
│ Part Hit: Right Arm (Modifier: x0.9)              │
│ Source: Better Combat 'Greatsword Sweep'          │
│                                                   │
│ Base Weapon Dmg: 12.0                             │
│ Enchant (Sharpness V): +3.0                       │
│ Pehkui Size Bonus (+25% of Base): +3.0            │
│                                                   │
│ Local Part Calc: (12+3+3) * 0.9 = 16.2            │
│ *Final Calc: 16.2**                               │
└───────────────────────────────────────────────────┘
```

### 1.4 Mod Specifics Panel (Right-Bottom)
```
┌─ Mod Specifics ──────────────────────────┐
│ Better Combat: Arc Collision Detected    │
│ Pehkui: Entity Size Modified (Scale 2.0) │
└──────────────────────────────────────────┘
```

### 1.5 Visual Effects
- Energy burst blu/bianco al punto di impatto
- Hitbox colorate con wireframe + fill trasparente
- Labels billboard (sempre verso la camera)

---

## 2. ARCHITETTURA DEL SISTEMA

### 2.1 Struttura Files
```
src/main/java/com/frenkvs/devmod/
├── hud/
│   ├── ImpactHudOverlay.java          # HUD principale (RegisterGuiLayersEvent)
│   ├── ImpactData.java                # Dati ultimo impatto
│   ├── DamageBreakdown.java           # Calcolo breakdown danno
│   └── HudRenderer.java               # Utility rendering
├── integration/
│   ├── PehkuiIntegration.java         # Soft-dep Pehkui
│   ├── BetterCombatIntegration.java   # Soft-dep Better Combat
│   └── ModIntegrationManager.java     # Registry integrazioni
├── rendering/
│   ├── BodyPartRenderer.java          # [GIÀ ESISTE] + labels
│   ├── ImpactVFX.java                 # Effetto visivo impatto
│   └── EntityInfoRenderer.java        # Render info sopra entità
└── DamageHandler.java                 # [MODIFICA] Cattura dati per HUD
```

### 2.2 Flusso Dati
```
LivingIncomingDamageEvent
         ↓
   DamageHandler.java
   (calcola body part, multiplier, enchants)
         ↓
   ImpactData.store(data)  ← Thread-safe storage
         ↓
   HitContext.store()      ← Per telemetry
         ↓
   ┌─────────────────────────────────────┐
   │         CLIENT SIDE                  │
   │                                      │
   │  ImpactHudOverlay.render()          │
   │  (RegisterGuiLayersEvent)            │
   │         ↓                            │
   │  Legge ImpactData                   │
   │         ↓                            │
   │  Renderizza pannelli HUD            │
   └─────────────────────────────────────┘
```

---

## 3. FASE 1: ImpactData + DamageBreakdown

### 3.1 ImpactData.java
```java
package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.HitHelper.BodyPart;
import net.minecraft.world.entity.LivingEntity;
import java.util.concurrent.atomic.AtomicReference;

public class ImpactData {
    // Singleton thread-safe per ultimo impatto
    private static final AtomicReference<ImpactData> LAST_IMPACT = new AtomicReference<>();
    private static final long DISPLAY_DURATION_MS = 3000; // 3 secondi

    // Dati impatto
    public final long timestamp;
    public final LivingEntity target;
    public final BodyPart bodyPart;
    public final float bodyPartMultiplier;
    public final DamageBreakdown breakdown;
    public final String attackSource; // "Melee", "Ranged", "Better Combat: Greatsword Sweep"

    // Dati mod integration (nullable)
    public final Float pehkuiScale;        // null se Pehkui non presente
    public final Float pehkuiHitboxScale;  // null se non scalato
    public final String betterCombatAttack; // null se non BC

    public ImpactData(LivingEntity target, BodyPart part, float multiplier,
                      DamageBreakdown breakdown, String source) {
        this.timestamp = System.currentTimeMillis();
        this.target = target;
        this.bodyPart = part;
        this.bodyPartMultiplier = multiplier;
        this.breakdown = breakdown;
        this.attackSource = source;

        // Pehkui integration (soft-dep)
        this.pehkuiScale = ModIntegrationManager.getPehkuiScale(target);
        this.pehkuiHitboxScale = ModIntegrationManager.getPehkuiHitboxScale(target);
        this.betterCombatAttack = null; // TODO: BC integration
    }

    public static void store(ImpactData data) {
        LAST_IMPACT.set(data);
    }

    public static ImpactData get() {
        ImpactData data = LAST_IMPACT.get();
        if (data == null) return null;

        // Scade dopo DISPLAY_DURATION_MS
        if (System.currentTimeMillis() - data.timestamp > DISPLAY_DURATION_MS) {
            LAST_IMPACT.set(null);
            return null;
        }
        return data;
    }

    public float getRemainingAlpha() {
        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed > DISPLAY_DURATION_MS) return 0f;

        // Fade out negli ultimi 500ms
        long fadeStart = DISPLAY_DURATION_MS - 500;
        if (elapsed > fadeStart) {
            return 1.0f - ((elapsed - fadeStart) / 500f);
        }
        return 1.0f;
    }
}
```

### 3.2 DamageBreakdown.java
```java
package com.frenkvs.devmod.hud;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

public class DamageBreakdown {
    public final float baseWeaponDamage;
    public final List<EnchantBonus> enchantBonuses;
    public final float pehkuiSizeBonus;
    public final float bodyPartMultiplier;
    public final float armorPenetrationBonus;
    public final float finalDamage;

    public record EnchantBonus(String name, int level, float bonus) {}

    public DamageBreakdown(ItemStack weapon, LivingEntity target,
                           float baseDmg, float bodyPartMult, float armorPen) {
        this.baseWeaponDamage = baseDmg;
        this.bodyPartMultiplier = bodyPartMult;
        this.enchantBonuses = new ArrayList<>();

        // Calcola bonus enchant
        calculateEnchantBonuses(weapon);

        // Pehkui size bonus (25% of base per ogni 1.0 di scala sopra 1.0)
        Float scale = ModIntegrationManager.getPehkuiScale(target);
        if (scale != null && scale > 1.0f) {
            this.pehkuiSizeBonus = baseDmg * 0.25f * (scale - 1.0f);
        } else {
            this.pehkuiSizeBonus = 0f;
        }

        // Armor penetration bonus
        this.armorPenetrationBonus = armorPen;

        // Calcolo finale
        float subtotal = baseWeaponDamage + getTotalEnchantBonus() + pehkuiSizeBonus;
        this.finalDamage = (subtotal * bodyPartMultiplier) + armorPenetrationBonus;
    }

    private void calculateEnchantBonuses(ItemStack weapon) {
        if (weapon.isEmpty()) return;

        // Sharpness: +1.0 + 0.5 per livello (1-5)
        int sharpness = weapon.getEnchantmentLevel(Enchantments.SHARPNESS);
        if (sharpness > 0) {
            float bonus = 1.0f + (sharpness - 1) * 0.5f;
            enchantBonuses.add(new EnchantBonus("Sharpness " + toRoman(sharpness), sharpness, bonus));
        }

        // Smite: +2.5 per livello vs undead
        int smite = weapon.getEnchantmentLevel(Enchantments.SMITE);
        if (smite > 0) {
            float bonus = smite * 2.5f;
            enchantBonuses.add(new EnchantBonus("Smite " + toRoman(smite), smite, bonus));
        }

        // Bane of Arthropods: +2.5 per livello vs arthropods
        int bane = weapon.getEnchantmentLevel(Enchantments.BANE_OF_ARTHROPODS);
        if (bane > 0) {
            float bonus = bane * 2.5f;
            enchantBonuses.add(new EnchantBonus("Bane " + toRoman(bane), bane, bonus));
        }
    }

    public float getTotalEnchantBonus() {
        return (float) enchantBonuses.stream().mapToDouble(e -> e.bonus).sum();
    }

    private static String toRoman(int num) {
        return switch(num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }

    // Genera stringa formula per HUD
    public String getFormulaString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("(%.1f", baseWeaponDamage));

        for (EnchantBonus eb : enchantBonuses) {
            sb.append(String.format("+%.1f", eb.bonus));
        }

        if (pehkuiSizeBonus > 0) {
            sb.append(String.format("+%.1f", pehkuiSizeBonus));
        }

        sb.append(String.format(") * %.2f", bodyPartMultiplier));

        if (armorPenetrationBonus > 0) {
            sb.append(String.format(" + %.1f", armorPenetrationBonus));
        }

        sb.append(String.format(" = %.1f", finalDamage));
        return sb.toString();
    }
}
```

---

## 4. FASE 2: HUD Overlay (RegisterGuiLayersEvent)

### 4.1 ImpactHudOverlay.java
```java
package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.HitHelper.BodyPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ImpactHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "impact_analysis");

    // Colori pannello
    private static final int PANEL_BG = 0xCC1A1A2E;      // Blu scuro semi-trasparente
    private static final int PANEL_BORDER = 0xFF3D5AFE;  // Blu acceso
    private static final int TEXT_TITLE = 0xFF00FFFF;    // Cyan
    private static final int TEXT_NORMAL = 0xFFFFFFFF;   // Bianco
    private static final int TEXT_VALUE = 0xFF00FF00;    // Verde
    private static final int TEXT_FORMULA = 0xFFFFD700;  // Oro

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            LAYER_ID,
            ImpactHudOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, float partialTick) {
        ImpactData data = ImpactData.get();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Calcola alpha per fade out
        float alpha = data.getRemainingAlpha();
        if (alpha <= 0) return;

        // Posizione pannello (angolo destro)
        int panelWidth = 280;
        int panelHeight = calculatePanelHeight(data);
        int panelX = screenWidth - panelWidth - 10;
        int panelY = 10;

        // Applica alpha a tutti i colori
        int bgColor = applyAlpha(PANEL_BG, alpha);
        int borderColor = applyAlpha(PANEL_BORDER, alpha);

        // Sfondo pannello
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, bgColor);

        // Bordo
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, borderColor);

        // Contenuto
        int y = panelY + 8;
        int textX = panelX + 10;

        // Titolo
        graphics.drawString(mc.font,
            Component.literal("Impact Analysis (Multi-Part & Mod Integrated)"),
            textX, y, applyAlpha(TEXT_TITLE, alpha), false);
        y += 14;

        // Linea separatore
        graphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 1, borderColor);
        y += 8;

        // Part Hit
        String partColor = getBodyPartColorCode(data.bodyPart);
        graphics.drawString(mc.font,
            Component.literal("Part Hit: " + partColor + data.bodyPart.name() +
                " §f(Modifier: §a" + String.format("x%.2f", data.bodyPartMultiplier) + "§f)"),
            textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
        y += 12;

        // Source
        graphics.drawString(mc.font,
            Component.literal("Source: §7" + data.attackSource),
            textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
        y += 16;

        // Breakdown
        DamageBreakdown bd = data.breakdown;

        graphics.drawString(mc.font,
            Component.literal("Base Weapon Dmg: §a" + String.format("%.1f", bd.baseWeaponDamage)),
            textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
        y += 10;

        // Enchants
        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            graphics.drawString(mc.font,
                Component.literal("Enchant (" + eb.name() + "): §a+" + String.format("%.1f", eb.bonus())),
                textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
            y += 10;
        }

        // Pehkui bonus
        if (bd.pehkuiSizeBonus > 0) {
            graphics.drawString(mc.font,
                Component.literal("Pehkui Size Bonus: §a+" + String.format("%.1f", bd.pehkuiSizeBonus)),
                textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
            y += 10;
        }

        y += 6;

        // Formula
        graphics.drawString(mc.font,
            Component.literal("Local Part Calc: §6" + bd.getFormulaString()),
            textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
        y += 12;

        // Final
        graphics.drawString(mc.font,
            Component.literal("§l*Final Calc: §a§l" + String.format("%.1f", bd.finalDamage) + "**"),
            textX, y, applyAlpha(TEXT_FORMULA, alpha), false);
        y += 16;

        // Mod Specifics (se presenti)
        if (data.pehkuiScale != null || data.betterCombatAttack != null) {
            y += 4;
            graphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 1, borderColor);
            y += 8;

            graphics.drawString(mc.font,
                Component.literal("§bMod Specifics"),
                textX, y, applyAlpha(TEXT_TITLE, alpha), false);
            y += 12;

            if (data.betterCombatAttack != null) {
                graphics.drawString(mc.font,
                    Component.literal("Better Combat: §7Arc Collision Detected"),
                    textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
                y += 10;
            }

            if (data.pehkuiScale != null) {
                graphics.drawString(mc.font,
                    Component.literal("Pehkui: §7Entity Size Modified (Scale " +
                        String.format("%.1f", data.pehkuiScale) + ")"),
                    textX, y, applyAlpha(TEXT_NORMAL, alpha), false);
            }
        }
    }

    private static int calculatePanelHeight(ImpactData data) {
        int base = 120;
        base += data.breakdown.enchantBonuses.size() * 10;
        if (data.breakdown.pehkuiSizeBonus > 0) base += 10;
        if (data.pehkuiScale != null || data.betterCombatAttack != null) base += 40;
        return base;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);         // Top
        g.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        g.fill(x, y, x + 1, y + h, color);         // Left
        g.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static String getBodyPartColorCode(BodyPart part) {
        return switch (part) {
            case HEAD -> "§c";  // Red
            case BODY -> "§a";  // Green
            case ARMS -> "§e";  // Yellow
            case LEGS -> "§b";  // Cyan
        };
    }
}
```

---

## 5. FASE 3: Integrazione Pehkui (Soft Dependency)

### 5.1 ModIntegrationManager.java
```java
package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIntegrationManager.class);

    private static boolean pehkuiLoaded = false;
    private static boolean betterCombatLoaded = false;

    public static void init() {
        pehkuiLoaded = ModList.get().isLoaded("pehkui");
        betterCombatLoaded = ModList.get().isLoaded("bettercombat");

        LOGGER.info("[DevMod] Pehkui integration: {}", pehkuiLoaded ? "ENABLED" : "disabled");
        LOGGER.info("[DevMod] Better Combat integration: {}", betterCombatLoaded ? "ENABLED" : "disabled");
    }

    public static Float getPehkuiScale(LivingEntity entity) {
        if (!pehkuiLoaded) return null;
        return PehkuiIntegration.getScale(entity);
    }

    public static Float getPehkuiHitboxScale(LivingEntity entity) {
        if (!pehkuiLoaded) return null;
        return PehkuiIntegration.getHitboxScale(entity);
    }

    public static boolean isPehkuiLoaded() { return pehkuiLoaded; }
    public static boolean isBetterCombatLoaded() { return betterCombatLoaded; }
}
```

### 5.2 PehkuiIntegration.java
```java
package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.LivingEntity;

/**
 * Soft dependency wrapper per Pehkui API.
 *
 * NOTA: Per usare questa classe, aggiungere in build.gradle:
 *
 * repositories {
 *     maven { url "https://jitpack.io" }
 * }
 *
 * dependencies {
 *     compileOnly "com.github.Virtuoel:Pehkui:${pehkui_version}"
 * }
 *
 * E in gradle.properties:
 * pehkui_version=3.8.3+1.21-neoforge
 */
public class PehkuiIntegration {

    /**
     * Ottiene la scala base dell'entità.
     *
     * Pehkui API: ScaleTypes.BASE.getScaleData(entity).getScale()
     */
    public static Float getScale(LivingEntity entity) {
        try {
            // Reflection per evitare hard dependency
            Class<?> scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Object baseType = scaleTypesClass.getField("BASE").get(null);

            java.lang.reflect.Method getScaleData = baseType.getClass()
                .getMethod("getScaleData", net.minecraft.world.entity.Entity.class);
            Object scaleData = getScaleData.invoke(baseType, entity);

            java.lang.reflect.Method getScale = scaleData.getClass().getMethod("getScale");
            return (Float) getScale.invoke(scaleData);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ottiene la scala hitbox (può essere diversa dalla scala visiva).
     */
    public static Float getHitboxScale(LivingEntity entity) {
        try {
            Class<?> scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");

            // Prova HITBOX_WIDTH prima
            Object hitboxType = null;
            try {
                hitboxType = scaleTypesClass.getField("HITBOX_WIDTH").get(null);
            } catch (NoSuchFieldException e) {
                // Fallback a WIDTH
                hitboxType = scaleTypesClass.getField("WIDTH").get(null);
            }

            java.lang.reflect.Method getScaleData = hitboxType.getClass()
                .getMethod("getScaleData", net.minecraft.world.entity.Entity.class);
            Object scaleData = getScaleData.invoke(hitboxType, entity);

            java.lang.reflect.Method getScale = scaleData.getClass().getMethod("getScale");
            return (Float) getScale.invoke(scaleData);

        } catch (Exception e) {
            return null;
        }
    }
}
```

---

## 6. FASE 4: Labels 3D sui Hitbox

### 6.1 Modifica BodyPartRenderer.java
Aggiungere labels con moltiplicatori visibili direttamente sulle hitbox:

```java
// In renderBodyPartBox(), dopo il rendering del box:

if (showLabels) {
    // Ottieni moltiplicatore per questa parte
    WeaponStats stats = WeaponConfigManager.getGlobalStats();
    float mult = getMultiplierForPart(part, stats);

    String labelText = part.name() + " [x" + String.format("%.2f", mult) + "]";

    // Special label per HEAD
    if (part == BodyPart.HEAD && mult >= 2.0f) {
        labelText = "Head [Crit x" + String.format("%.1f", mult) + "]";
    }

    Vec3 labelPos = box.getCenter().add(0, box.getYsize() / 2 + 0.3, 0);

    // Renderizza label come billboard
    renderBillboardLabel(poseStack, bufferSource, labelPos, labelText,
                         getColorForBodyPart(part), cameraPos);
}

private static float getMultiplierForPart(BodyPart part, WeaponStats stats) {
    return switch (part) {
        case HEAD -> stats.headMult;
        case BODY -> stats.bodyMult;
        case ARMS -> stats.armsMult;
        case LEGS -> stats.legsMult;
    };
}
```

---

## 7. FASE 5: Modifica DamageHandler per Cattura Dati

### 7.1 DamageHandler.java modificato
```java
@SubscribeEvent(priority = EventPriority.HIGH)
public static void onDamage(LivingIncomingDamageEvent event) {
    if (event.getEntity() instanceof LivingEntity victim &&
        event.getSource().getEntity() instanceof LivingEntity attacker) {

        ItemStack weapon = attacker.getMainHandItem();
        HitHelper.BodyPart part;
        boolean isRanged = false;
        String attackSource = "Melee Attack";

        // Identifica parte e tipo attacco
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
            part = HitHelper.getBodyPart(victim, arrow.getY());
            isRanged = true;
            attackSource = "Ranged Attack";
        } else {
            part = HitHelper.rayTraceBodyPartAABB(attacker, victim);
        }

        // Store per telemetry
        HitContext.store(victim, part, isRanged);

        // Recupera stats
        WeaponStats stats = WeaponConfigManager.getStats(weapon);

        // Calcola moltiplicatore
        float multiplier = switch (part) {
            case HEAD -> stats.headMult;
            case BODY -> stats.bodyMult;
            case ARMS -> stats.armsMult;
            case LEGS -> stats.legsMult;
        };

        // Calcola danno
        float originalDamage = event.getAmount();
        float baseDamage = originalDamage + stats.baseDamageBonus;

        // Armor pen bonus
        float armorPenBonus = 0;
        if (stats.armorPenetration > 0) {
            float armorVal = victim.getArmorValue();
            armorPenBonus = armorVal * stats.armorPenetration * 0.5f;
        }

        float newDamage = baseDamage * multiplier + armorPenBonus;
        event.setAmount(newDamage);

        // === NUOVO: Crea e salva ImpactData per HUD ===
        if (attacker instanceof ServerPlayer) {
            DamageBreakdown breakdown = new DamageBreakdown(
                weapon, victim, originalDamage, multiplier, armorPenBonus
            );

            ImpactData impactData = new ImpactData(
                victim, part, multiplier, breakdown, attackSource
            );

            // Invia al client (packet)
            NetworkHandler.sendToPlayer(
                (ServerPlayer) attacker,
                new ImpactDataPayload(impactData)
            );
        }

        // Feedback esistente...
    }
}
```

---

## 8. FASE 6: Networking (Server → Client)

### 8.1 ImpactDataPayload.java
```java
package com.frenkvs.devmod.network;

import com.frenkvs.devmod.HitHelper.BodyPart;
import com.frenkvs.devmod.hud.DamageBreakdown;
import com.frenkvs.devmod.hud.ImpactData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImpactDataPayload(
    int targetId,
    BodyPart bodyPart,
    float multiplier,
    float baseDamage,
    float finalDamage,
    String attackSource,
    float pehkuiScale
) implements CustomPacketPayload {

    public static final Type<ImpactDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "impact_data"));

    public static final StreamCodec<FriendlyByteBuf, ImpactDataPayload> CODEC =
        StreamCodec.ofMember(ImpactDataPayload::write, ImpactDataPayload::read);

    public static ImpactDataPayload read(FriendlyByteBuf buf) {
        return new ImpactDataPayload(
            buf.readInt(),
            BodyPart.values()[buf.readByte()],
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readUtf(),
            buf.readFloat()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(targetId);
        buf.writeByte(bodyPart.ordinal());
        buf.writeFloat(multiplier);
        buf.writeFloat(baseDamage);
        buf.writeFloat(finalDamage);
        buf.writeUtf(attackSource);
        buf.writeFloat(pehkuiScale);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

---

## 9. TIMELINE E PRIORITÀ

### Fase 1 (Core - 2 ore)
1. [ ] Creare `ImpactData.java`
2. [ ] Creare `DamageBreakdown.java`
3. [ ] Modificare `DamageHandler.java` per popolamento

### Fase 2 (HUD - 3 ore)
4. [ ] Creare `ImpactHudOverlay.java` con `RegisterGuiLayersEvent`
5. [ ] Implementare rendering pannello
6. [ ] Test fade-out e timing

### Fase 3 (Labels 3D - 1 ora)
7. [ ] Modificare `BodyPartRenderer.java` per labels
8. [ ] Aggiungere moltiplicatori visibili

### Fase 4 (Networking - 2 ore)
9. [ ] Creare `ImpactDataPayload.java`
10. [ ] Registrare in `NetworkHandler.java`
11. [ ] Handler client-side

### Fase 5 (Pehkui Integration - 1 ora)
12. [ ] Creare `ModIntegrationManager.java`
13. [ ] Creare `PehkuiIntegration.java`
14. [ ] Aggiungere dependency opzionale in build.gradle

### Fase 6 (Polish - 1 ora)
15. [ ] Entity Info bar sopra mob
16. [ ] Effetto impatto (opzionale)
17. [ ] Config per toggle HUD

---

## 10. RISORSE E RIFERIMENTI

### API Documentation
- [NeoForge GUI Screens](https://docs.neoforged.net/docs/1.21.1/gui/screens/)
- [NeoForge Events](https://docs.neoforged.net/docs/concepts/events/)
- [Pehkui GitHub](https://github.com/Virtuoel/Pehkui)
- [Better Combat GitHub](https://github.com/ZsoltMolnarrr/BetterCombat)

### NeoForge 1.21 Specifici
- `RegisterGuiLayersEvent` per overlay HUD
- `VanillaGuiLayers.CROSSHAIR` per posizionamento
- `GuiGraphics` per rendering (drawString, fill, blit)

### Enchantment API
- `ItemStack.getEnchantmentLevel(Enchantment)` - NeoForge 1.21
- `EnchantmentHelper.runIterationOnItem()` - per iterare enchants

---

## 11. COMPATIBILITÀ

| Mod | Stato | Note |
|-----|-------|------|
| Pehkui | Soft-dep | Reflection API, nessuna dipendenza build |
| Better Combat | Soft-dep | Rilevamento nome attacco da evento |
| Epic Fight | Non testato | Potrebbe sovrascrivere damage events |
| First Person Model | Compatibile | Non interferisce |

---

**NOTA FINALE**: Questo piano è eseguibile step-by-step. Ogni fase può essere testata indipendentemente. La priorità è il sistema core (Fase 1-2), il resto è enhancement.
