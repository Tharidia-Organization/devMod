# Impact HUD - Roadmap di Upgrade

## Overview

Questo documento definisce il piano di upgrade per l'Impact HUD system, organizzato in fasi incrementali con deliverable chiari.

---

## Fase 0: Stabilizzazione (Quick Wins) - **COMPLETATA**

**Obiettivo:** Correggere bug critici senza modifiche architetturali
**Status:** ✅ DONE

### Task 0.1: Fix Pehkui Bonus - ✅ DONE (BUG-001)

**File:** `damage/DamageBreakdown.java`

Il codice ora usa correttamente `attacker` invece di `target`.

### Task 0.2: Fix True Damage - ⚠️ OPEN (BUG-002)

**File:** `DamageHandler.java`

Ancora da implementare. Opzioni:

- **Opzione A:** Rimuovere la feature (se non usata)
- **Opzione B:** Implementare correttamente con DamageSource custom

### Task 0.3: Fix Enchant Filtering - ✅ DONE (BUG-004)

**File:** `damage/DamageBreakdown.java`

Enchant bonus ora filtrati per target type valido prima di essere aggiunti.

### Task 0.4: Cache Formula String - ✅ DONE (BUG-010)

**File:** `damage/DamageBreakdown.java`

Formula string ora cached nel constructor.

---

## Fase 1: UX Improvements

**Obiettivo:** Migliorare usabilità e configurabilità
**Effort Stimato:** 8-12 ore
**Rischio:** Basso-Medio

### Task 1.1: Posizione HUD Configurabile
**File:** `Config.java`

```java
public enum HudPosition {
    TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER_RIGHT, CENTER_LEFT
}

IMPACT_HUD_POSITION = BUILDER
    .comment("Position of the Impact HUD on screen")
    .defineEnum("impactHudPosition", HudPosition.TOP_RIGHT);

IMPACT_HUD_OFFSET_X = BUILDER
    .comment("Horizontal offset from edge (pixels)")
    .defineInRange("impactHudOffsetX", 10, 0, 200);

IMPACT_HUD_OFFSET_Y = BUILDER
    .comment("Vertical offset from edge (pixels)")
    .defineInRange("impactHudOffsetY", 10, 0, 200);
```

**File:** `ImpactHudOverlay.java`
```java
private static int[] calculatePanelPosition(int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
    HudPosition pos = Config.IMPACT_HUD_POSITION.get();
    int offsetX = Config.IMPACT_HUD_OFFSET_X.get();
    int offsetY = Config.IMPACT_HUD_OFFSET_Y.get();

    int x, y;
    switch (pos) {
        case TOP_LEFT -> { x = offsetX; y = offsetY; }
        case TOP_RIGHT -> { x = screenWidth - panelWidth - offsetX; y = offsetY; }
        case BOTTOM_LEFT -> { x = offsetX; y = screenHeight - panelHeight - offsetY; }
        case BOTTOM_RIGHT -> { x = screenWidth - panelWidth - offsetX; y = screenHeight - panelHeight - offsetY; }
        case CENTER_RIGHT -> { x = screenWidth - panelWidth - offsetX; y = (screenHeight - panelHeight) / 2; }
        case CENTER_LEFT -> { x = offsetX; y = (screenHeight - panelHeight) / 2; }
    }
    return new int[] { x, y };
}
```

### Task 1.2: Observation Timeout - ✅ DONE (BUG-005)

**File:** `ImpactData.java`

Implementato! MAX_OBSERVATION_TIME_MS = 30000 (30 secondi max).

### Task 1.3: Keybind per Dismiss
**File:** `KeyBindings.java` (nuovo)

```java
public class KeyBindings {
    public static final KeyMapping DISMISS_IMPACT_HUD = new KeyMapping(
        "key.devmod.dismiss_impact_hud",
        InputConstants.KEY_H,
        "key.categories.devmod"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (DISMISS_IMPACT_HUD.consumeClick()) {
            ImpactData.clear();
            Impact3DPanelManager.INSTANCE.clear();
        }
    }
}
```

### Task 1.4: Internazionalizzazione
**File:** `en_us.json`

```json
{
    "devmod.hud.impact_analysis_title": "Impact Analysis",
    "devmod.hud.part_hit": "Part Hit",
    "devmod.hud.modifier": "Modifier",
    "devmod.hud.source": "Source",
    "devmod.hud.base_weapon_damage": "Base Weapon Dmg",
    "devmod.hud.enchant_bonus": "Enchant (%s)",
    "devmod.hud.pehkui_bonus": "Pehkui Size Bonus (+25%% of Base)",
    "devmod.hud.formula": "Local Part Calc",
    "devmod.hud.actual_damage": "ACTUAL DAMAGE",
    "devmod.hud.health_change": "HP: %.1f -> %.1f",
    "devmod.hud.armor_reduced": "Armor/Effects reduced",
    "devmod.hud.damage_amplified": "Damage amplified",
    "devmod.hud.calculated_damage": "*Calculated Dmg*",
    "devmod.hud.mod_specifics": "Mod Specifics",
    "devmod.hud.better_combat_arc": "Better Combat: Arc Collision Detected",
    "devmod.hud.pehkui_scale": "Pehkui: Entity Size Modified (Scale %.2f)"
}
```

---

## Fase 2: Architettura Migliorata - **PARZIALMENTE COMPLETATA**

**Obiettivo:** Ridurre duplicazione, migliorare manutenibilità
**Status:** ⚡ IN PROGRESS

### Task 2.1: Estrarre HUD Content Builder - ✅ DONE (OPT-001)

**File:** `hud/ImpactHudContentBuilder.java` (CREATO)

```java
public class ImpactHudContentBuilder {

    public record HudLine(String text, int color, LineType type) {
        public enum LineType { TITLE, NORMAL, VALUE, FORMULA, MUTED, HIGHLIGHT }
    }

    public record HudSection(String title, List<HudLine> lines) {}

    /**
     * Builds the content for the Impact HUD.
     * Used by both 2D overlay and 3D renderer.
     */
    public static List<HudSection> buildContent(ImpactData data, float alpha) {
        List<HudSection> sections = new ArrayList<>();

        // Main section
        List<HudLine> mainLines = new ArrayList<>();
        mainLines.add(new HudLine(
            I18n.get("devmod.hud.part_hit") + ": " + data.bodyPart.name() +
            " (" + I18n.get("devmod.hud.modifier") + ": x" + String.format("%.2f", data.bodyPartMultiplier) + ")",
            data.getBodyPartColor(),
            HudLine.LineType.NORMAL
        ));

        mainLines.add(new HudLine(
            I18n.get("devmod.hud.source") + ": " + data.getFormattedAttackSource(),
            Colors.MUTED,
            HudLine.LineType.MUTED
        ));

        // ... etc for all lines

        sections.add(new HudSection(I18n.get("devmod.hud.impact_analysis_title"), mainLines));

        // Mod specifics section (if applicable)
        if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
            List<HudLine> modLines = buildModSpecificsLines(data);
            sections.add(new HudSection(I18n.get("devmod.hud.mod_specifics"), modLines));
        }

        return sections;
    }
}
```

### Task 2.2: Refactor ImpactHudOverlay
**File:** `ImpactHudOverlay.java`

```java
private static void renderImpactPanel(GuiGraphics g, Font font, ImpactData data,
                                       int x, int y, int width, int height, float alpha) {
    renderPanelBackground(g, x, y, width, height, alpha);

    List<HudSection> sections = ImpactHudContentBuilder.buildContent(data, alpha);

    int textY = y + PANEL_PADDING;
    for (HudSection section : sections) {
        textY = renderSection(g, font, section, x + PANEL_PADDING, textY, alpha);
        textY += SECTION_SPACING;
    }
}

private static int renderSection(GuiGraphics g, Font font, HudSection section,
                                  int x, int y, float alpha) {
    // Title
    g.drawString(font, section.title(), x, y, applyAlpha(Colors.TITLE, alpha), false);
    y += LINE_HEIGHT + 2;

    // Lines
    for (HudLine line : section.lines()) {
        g.drawString(font, line.text(), x, y, applyAlpha(line.color(), alpha), false);
        y += LINE_HEIGHT;
    }

    return y;
}
```

### Task 2.3: Refactor Impact3DRenderer
Stessa logica, usando `ImpactHudContentBuilder.buildContent()`.

### Task 2.4: Estrarre DamageCalculator - ✅ DONE

**File:** `damage/DamageCalculator.java` (CREATO)

Calcolo centralizzato del danno separato dalla gestione eventi.

---

## Fase 3: Features Avanzate

**Obiettivo:** Aggiungere funzionalità richieste dalla community
**Effort Stimato:** 20-30 ore
**Rischio:** Medio-Alto

### Task 3.1: Breakdown Riduzione Danno

Mostrare COME il danno è stato ridotto:

```
┌─────────────────────────────────────────────┐
│ Damage Reduction Breakdown                   │
├─────────────────────────────────────────────┤
│ Calculated: 25.0                            │
│ - Armor (Diamond): -8.5                     │
│ - Protection IV: -4.2                       │
│ - Resistance II: -2.3                       │
│ = Actual: 10.0                              │
└─────────────────────────────────────────────┘
```

**Implementazione:**
Catturare le riduzioni da `LivingDamageEvent.Post`:
```java
float armorReduction = event.getReduction(DamageContainer.Reduction.ARMOR);
float enchantReduction = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
float effectReduction = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
float absorptionReduction = event.getReduction(DamageContainer.Reduction.ABSORPTION);
```

### Task 3.2: History Panel

Mantenere storico degli ultimi N impatti:

```java
public class ImpactHistory {
    private static final int MAX_HISTORY = 10;
    private static final Deque<ImpactData> history = new ArrayDeque<>(MAX_HISTORY);

    public static void record(ImpactData data) {
        if (history.size() >= MAX_HISTORY) {
            history.removeLast();
        }
        history.addFirst(data);
    }

    public static List<ImpactData> getRecent(int count) {
        return history.stream().limit(count).toList();
    }
}
```

### Task 3.3: DPS Meter

Calcolare DPS in tempo reale:

```java
public class DPSTracker {
    private static final long WINDOW_MS = 5000; // 5 second window
    private static final Deque<DamageRecord> damages = new ArrayDeque<>();

    private record DamageRecord(long timestamp, float damage) {}

    public static void recordDamage(float damage) {
        long now = System.currentTimeMillis();
        damages.addLast(new DamageRecord(now, damage));

        // Cleanup old entries
        while (!damages.isEmpty() && now - damages.peekFirst().timestamp > WINDOW_MS) {
            damages.removeFirst();
        }
    }

    public static float getCurrentDPS() {
        if (damages.isEmpty()) return 0;

        long now = System.currentTimeMillis();
        float totalDamage = 0;
        long oldestTimestamp = now;

        for (DamageRecord record : damages) {
            if (now - record.timestamp <= WINDOW_MS) {
                totalDamage += record.damage;
                oldestTimestamp = Math.min(oldestTimestamp, record.timestamp);
            }
        }

        long windowDuration = now - oldestTimestamp;
        if (windowDuration <= 0) return totalDamage; // Single hit

        return totalDamage / (windowDuration / 1000f);
    }
}
```

### Task 3.4: Configurazione VFX Granulare

```java
// Config.java
IMPACT_VFX_ENABLED = BUILDER.define("impactVfxEnabled", true);
IMPACT_VFX_VORTEX_ENABLED = BUILDER.define("impactVfxVortexEnabled", true);
IMPACT_VFX_SLASH_ENABLED = BUILDER.define("impactVfxSlashEnabled", true);
IMPACT_VFX_LINES_ENABLED = BUILDER.define("impactVfxLinesEnabled", true);
IMPACT_VFX_INTENSITY = BUILDER.defineInRange("impactVfxIntensity", 1.0, 0.1, 2.0);
```

### Task 3.5: Export/Import Configurazioni

Permettere agli utenti di salvare/caricare preset:

```java
public class ImpactHudPresets {
    public static void exportToFile(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("hudPosition", Config.IMPACT_HUD_POSITION.get().name());
        json.addProperty("hudOffsetX", Config.IMPACT_HUD_OFFSET_X.get());
        // ... etc

        Files.writeString(path, GSON.toJson(json));
    }

    public static void importFromFile(Path path) {
        String content = Files.readString(path);
        JsonObject json = GSON.fromJson(content, JsonObject.class);
        // Apply to config...
    }
}
```

---

## Fase 4: Performance & Polish

**Obiettivo:** Ottimizzare performance, migliorare feedback visivo
**Effort Stimato:** 12-16 ore
**Rischio:** Basso

### Task 4.1: Object Pooling per VFX

```java
public class VFXPool {
    private static final Queue<ImpactEffect> pool = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOL_SIZE = 20;

    public static ImpactEffect acquire(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
        ImpactEffect effect = pool.poll();
        if (effect != null) {
            effect.reset(hitPoint, slashDirection, data);
            return effect;
        }
        return new ImpactEffect(hitPoint, slashDirection, data);
    }

    public static void release(ImpactEffect effect) {
        if (pool.size() < MAX_POOL_SIZE) {
            pool.offer(effect);
        }
    }
}
```

### Task 4.2: Batch Rendering per Pannelli 3D

Invece di renderizzare ogni pannello separatamente, batch quelli visibili:

```java
public void renderAllPanelsBatched(PoseStack poseStack, MultiBufferSource bufferSource, ...) {
    // Collect all visible panels
    List<Impact3DPanel> visible = activePanels.stream()
        .filter(p -> !p.isExpired())
        .filter(p -> p.getDistanceFromCamera(cameraPos) <= MAX_RENDER_DISTANCE)
        .sorted(Comparator.comparingDouble(p -> -p.getDistanceFromCamera(cameraPos))) // Back to front
        .toList();

    // Single buffer acquisition
    VertexConsumer bgConsumer = bufferSource.getBuffer(RenderType.debugQuads());
    VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

    for (Impact3DPanel panel : visible) {
        panel.renderGeometry(poseStack, bgConsumer, lineConsumer, cameraPos);
    }

    // Text rendering (separate pass for font batching)
    for (Impact3DPanel panel : visible) {
        panel.renderText(poseStack, bufferSource, mc.font, cameraPos);
    }
}
```

### Task 4.3: Animazioni Smooth

Aggiungere easing functions per transizioni più fluide:

```java
public class Easing {
    public static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    public static float easeInOutCubic(float t) {
        return t < 0.5
            ? 4 * t * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}

// In Impact3DPanel.calculateAlpha()
private float calculateAlpha() {
    // ...
    if (elapsed < FADE_IN_MS) {
        return Easing.easeOutQuad((float) elapsed / FADE_IN_MS);
    }
    if (elapsed > FADE_OUT_START) {
        float fadeProgress = (float) (elapsed - FADE_OUT_START) / FADE_OUT_MS;
        return Easing.easeInOutCubic(1.0f - fadeProgress);
    }
    // ...
}
```

---

## Riepilogo Stato Attuale

| Fase | Task Totali | Completati | Status |
| ---- | ----------- | ---------- | ------ |
| Fase 0 | 4 | 3 | 75% (BUG-002 open) |
| Fase 1 | 4 | 1 | 25% |
| Fase 2 | 4 | 2 | 50% |
| Fase 3 | 5 | 0 | 0% |
| Fase 4 | 3 | 0 | 0% |

### Prossimi Passi Prioritari

1. **BUG-002**: True Damage implementation
2. **BUG-003**: Breakdown riduzione danno
3. **Task 1.1**: Posizione HUD configurabile
4. **Task 1.3**: Keybind dismiss
5. **Task 1.4**: Internazionalizzazione

---

## Metriche di Successo

| Metrica | Obiettivo | Misurazione |
|---------|-----------|-------------|
| Bug critici | 0 | Issue tracker |
| Code coverage | >70% | JaCoCo |
| Duplicazione codice | <10% | SonarQube |
| Stringhe tradotte | 100% | lang file audit |
| Feedback utenti | Positivo | Discord/GitHub |
