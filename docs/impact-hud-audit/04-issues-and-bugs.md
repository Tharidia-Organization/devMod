# Impact HUD - Problemi e Bug Identificati

## Classificazione Severità

| Livello | Descrizione |
|---------|-------------|
| 🔴 CRITICO | Funzionalità rotta, comportamento errato |
| 🟠 ALTO | Bug visibile, impatto significativo UX |
| 🟡 MEDIO | Problema minore, workaround possibile |
| 🟢 BASSO | Ottimizzazione, code quality |

---

## 1. Bug Critici

### 🔴 BUG-001: Pehkui Bonus Calcolato su Target invece di Attacker

**File:** `DamageBreakdown.java:41`

**Codice Attuale:**
```java
Float scale = ModIntegrationManager.getPehkuiScale(target);  // ❌ SBAGLIATO
```

**Comportamento Attuale:**
- Se colpisci un gigante (scale 2.0), ottieni +25% danno
- Se sei un gigante e colpisci un mob normale, NON ottieni bonus

**Comportamento Atteso:**
- Il bonus dovrebbe basarsi sulla scala dell'ATTACCANTE
- Un attaccante grande dovrebbe fare più danno
- Un target grande NON dovrebbe aumentare il danno ricevuto

**Impatto:**
- Logica di gioco completamente invertita
- Confusione utente su come funziona Pehkui

**Fix Proposto:**
```java
// Passare attacker al costruttore
public DamageBreakdown(ItemStack weapon, LivingEntity attacker, LivingEntity target,
                       float baseDmg, float bodyPartMult, float armorPenBonus) {
    // ...
    Float scale = ModIntegrationManager.getPehkuiScale(attacker);  // ✅ CORRETTO
}
```

---

### 🔴 BUG-002: True Damage Percent è un No-Op

**File:** `DamageHandler.java:204-208`

**Codice Attuale:**
```java
if (stats.trueDamagePercent > 0) {
    float truePortion = newDamage * stats.trueDamagePercent;
    float normalPortion = newDamage * (1f - stats.trueDamagePercent);
    newDamage = truePortion + normalPortion;  // ❌ = newDamage (no change)
}
```

**Analisi Matematica:**
```
truePortion = D × P
normalPortion = D × (1 - P)
newDamage = D × P + D × (1 - P) = D × (P + 1 - P) = D × 1 = D
```

**Impatto:**
- La statistica `trueDamagePercent` non ha alcun effetto
- Utenti che configurano questa stat non vedono risultati

**Fix Proposto:**
Il true damage dovrebbe bypassare l'armor. Richiede modifica della damage source o applicazione separata.

---

### 🔴 BUG-003: Discrepanza Danno Calcolato vs Reale Confonde

**File:** `ImpactHudOverlay.java:209-246`

**Problema:**
L'HUD mostra due valori che possono differire drasticamente:
- "Calculated Dmg: 25.0" (teorico)
- "ACTUAL DAMAGE: 8.0" (reale)

**Causa:**
`DamageBreakdown` non considera:
- Armor reduction
- Protection enchantments
- Resistance effects
- Absorption
- Shield blocking

**Impatto:**
- Utente vede "danno 25" ma fa solo "danno 8"
- Sembra un bug quando è by design
- Nessuna spiegazione di cosa riduce il danno

**Mitigazione Proposta:**
1. Aggiungere tooltip che spiega la differenza
2. Oppure: mostrare breakdown della riduzione
3. Oppure: calcolare danno post-armor nel breakdown

---

## 2. Bug Alti

### 🟠 BUG-004: Enchant Mostrati Anche Quando Bonus = 0

**File:** `DamageBreakdown.java:93-96`

**Codice Attuale:**
```java
else if (enchName.contains("fire_aspect")) {
    enchantBonuses.add(new EnchantBonus("Fire Aspect " + toRoman(level), level, 0f));  // bonus = 0
}
```

**E nel rendering:**
```java
for (EnchantBonus eb : bd.enchantBonuses) {
    if (eb.bonus() > 0) {  // Fire Aspect NON viene mostrato
        // ...
    }
}
```

**Problema:**
Il filtro `eb.bonus() > 0` funziona per Fire Aspect, ma Smite/Bane vengono aggiunti con bonus > 0 anche quando il target NON è del tipo giusto, causando:
- Smite mostrato vs non-undead (bonus applicato ma inutile)

**Fix Proposto:**
Non aggiungere EnchantBonus se non applicabile:
```java
if (enchName.contains("smite") && target.isInvertedHealAndHarm()) {
    // Solo se undead
}
```

---

### 🟠 BUG-005: Observation Lock Può Bloccare HUD Indefinitamente

**File:** `ImpactData.java:229-238`

**Codice:**
```java
public void setObserved(boolean observed) {
    if (this.isBeingObserved && !observed) {
        this.stoppedLookingTimestamp = System.currentTimeMillis();
    } else if (observed) {
        this.stoppedLookingTimestamp = -1;  // Reset timer
    }
    this.isBeingObserved = observed;
}
```

**Problema:**
Se il crosshair rimane sul pannello (es. durante AFK), l'HUD non scompare MAI.

**Impatto:**
- HUD permanente può coprire elementi importanti
- Nessun modo di chiudere manualmente

**Fix Proposto:**
1. Aggiungere timeout massimo (es. 30s)
2. Oppure: keybind per chiudere
3. Oppure: click per dismissare

---

### 🟠 BUG-006: Hit Detection Imprecisa per Entità Alte

**File:** `HitHelper.java:392-438`

**Codice:**
```java
private static HitResult rayTraceTallBodyWithHitPoint(...) {
    double headHeight = height * 0.15;  // Solo 15% per entità alte
    // ...
}
```

**Problema:**
Per entità molto alte (Enderman, Iron Golem), la zona HEAD è solo 15% dell'altezza, rendendo headshot molto difficili.

**Esempio:**
- Enderman: ~2.9 blocks tall
- Head zone: ~0.43 blocks (15%)
- Molto più difficile di mob normali

**Fix Proposto:**
Usare altezza assoluta minima invece di percentuale:
```java
double headHeight = Math.max(height * 0.15, 0.5);  // Min 0.5 blocks
```

---

## 3. Bug Medi

### 🟡 BUG-007: Stringhe Hardcoded Non Traducibili

**File:** `ImpactHudOverlay.java` e `Impact3DRenderer.java`

**Esempi:**
```java
String title = "Impact Analysis (Multi-Part & Mod Integrated)";
g.drawString(font, "Source: " + data.getFormattedAttackSource(), ...);
g.drawString(font, "Base Weapon Dmg: %.2f", ...);
g.drawString(font, "ACTUAL DAMAGE: %.2f", ...);
```

**Impatto:**
- Non supporta lingue diverse dall'inglese
- Inconsistente con altre parti della mod che usano i18n

**Fix Proposto:**
Usare `I18n.translate()` per tutte le stringhe:
```java
Component title = I18n.translate("devmod.hud.impact_analysis_title");
```

---

### 🟡 BUG-008: Posizione HUD Fissa

**File:** `ImpactHudOverlay.java:94-96`

**Codice:**
```java
int panelX = screenWidth - panelWidth - 10;
int panelY = 10;
```

**Problema:**
- Nessuna opzione per spostare l'HUD
- Può sovrapporsi ad altri mod HUD
- Non accessibile per utenti con necessità diverse

**Fix Proposto:**
Aggiungere config:
```java
public static final ModConfigSpec.EnumValue<HudPosition> IMPACT_HUD_POSITION;
public enum HudPosition { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER_RIGHT }
```

---

### 🟡 BUG-009: Panel 3D Può Occludere Vista

**File:** `Impact3DRenderer.java:30-31`

**Codice:**
```java
private static final float PANEL_OFFSET_SIDE = 4.5f;
private static final float PANEL_OFFSET_UP = 1.0f;
```

**Problema:**
L'offset fisso può posizionare il pannello:
- Dentro muri/terreno
- Davanti al target
- Fuori dalla vista

**Fix Proposto:**
1. Raycast per verificare visibilità
2. Offset dinamico basato su ambiente
3. Flip automatico se occlusione

---

### 🟡 BUG-010: Formula String Ricalcolata Ogni Frame

**File:** `DamageBreakdown.java:132-154`

**Problema:**
`getFormulaString()` usa StringBuilder ogni volta:
```java
public String getFormulaString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("(%.1f", baseWeaponDamage));
    // ... costruzione stringa
    return sb.toString();
}
```

Chiamato ogni frame da `renderImpactPanel()`.

**Impatto:**
- Allocazioni inutili
- GC pressure
- ~60 StringBuilder/secondo per pannello

**Fix Proposto:**
Cache la stringa:
```java
private final String cachedFormulaString;

public DamageBreakdown(...) {
    // ...
    this.cachedFormulaString = buildFormulaString();
}

public String getFormulaString() {
    return cachedFormulaString;
}
```

---

## 4. Bug Bassi / Ottimizzazioni

### 🟢 OPT-001: Duplicazione Codice Rendering 2D/3D

**File:** `ImpactHudOverlay.java` e `Impact3DRenderer.java`

**Problema:**
Lo stesso contenuto viene renderizzato con codice quasi identico in entrambi i file.

**Fix Proposto:**
Estrarre in `ImpactHudContent`:
```java
public class ImpactHudContent {
    public static List<HudLine> buildContent(ImpactData data) {
        // Logica condivisa
    }
}
```

---

### 🟢 OPT-002: VFX Sempre Attivi Anche Se Pannelli 3D Disabilitati

**File:** `ImpactVFX.java:43-54`

**Problema:**
Se l'utente disabilita i pannelli 3D, i VFX vengono comunque creati e renderizzati.

**Fix Proposto:**
Check enabled state prima di spawn:
```java
public static void addImpact(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
    if (!Config.IMPACT_3D_VFX_ENABLED.get()) return;
    // ...
}
```

---

### 🟢 OPT-003: CopyOnWriteArrayList Overhead

**File:** `Impact3DPanelManager.java:37`, `ImpactVFX.java:25`

**Problema:**
`CopyOnWriteArrayList` ha overhead alto per modifiche frequenti:
```java
private final List<Impact3DPanel> activePanels = new CopyOnWriteArrayList<>();
```

**Analisi:**
- Modifiche: spawn panel, remove expired (~2-5/secondo)
- Letture: ogni frame (~60/secondo)
- Ratio 12:1 reads/writes - CopyOnWriteArrayList è appropriato

**Conclusione:**
Non è un vero bug, CopyOnWriteArrayList è la scelta giusta per questo use case.

---

### 🟢 OPT-004: Cleanup Non Deterministico

**File:** `ImpactData.java:212-220`

**Codice:**
```java
private static void maybeCleanup() {
    long now = System.currentTimeMillis();
    if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;  // 10 seconds
    lastCleanup = now;
    IMPACTS_BY_PLAYER.entrySet().removeIf(...);
}
```

**Problema:**
Cleanup dipende dalla frequenza di chiamate a `store()`. Se nessun impatto per 30 secondi, entries scadute rimangono in memoria.

**Fix Proposto:**
Aggiungere cleanup periodico tramite scheduled task o client tick event.

---

## 5. Problemi di Design

### 🟡 DESIGN-001: Accoppiamento Stretto DamageHandler ↔ ImpactData

`DamageHandler` crea direttamente `ImpactData` invece di usare un factory/builder pattern, rendendo difficile:
- Unit testing
- Estensione
- Mock per test

---

### 🟡 DESIGN-002: Responsabilità Miste in DamageHandler

`DamageHandler.java` (713 LOC) gestisce:
- Damage calculation
- Body part detection
- Armor penetration
- Custom armor reduction
- Shield blocking
- Lifesteal
- Evasion detection
- Environmental damage
- ImpactData creation
- VFX triggering

**Dovrebbe essere splittato in:**
- `DamageCalculator`
- `BodyPartDetector`
- `ImpactDataFactory`
- `EvasionTracker`

---

### 🟡 DESIGN-003: Mancanza di Event System

Gli altri componenti non possono reagire agli impatti. Un event system permetterebbe:
```java
// Altri mod/componenti potrebbero ascoltare
ModEventBus.post(new ImpactEvent(data));
```

---

## 6. Tabella Riepilogativa

| ID | Severità | Componente | Descrizione | Effort Fix |
|----|----------|------------|-------------|------------|
| BUG-001 | 🔴 | DamageBreakdown | Pehkui bonus su target | Basso |
| BUG-002 | 🔴 | DamageHandler | True damage no-op | Medio |
| BUG-003 | 🔴 | ImpactHudOverlay | Discrepanza danno | Alto |
| BUG-004 | 🟠 | DamageBreakdown | Enchant mostrati erroneamente | Basso |
| BUG-005 | 🟠 | ImpactData | Observation lock infinito | Basso |
| BUG-006 | 🟠 | HitHelper | Head zone troppo piccola | Basso |
| BUG-007 | 🟡 | Multiple | Stringhe hardcoded | Medio |
| BUG-008 | 🟡 | ImpactHudOverlay | Posizione fissa | Basso |
| BUG-009 | 🟡 | Impact3DRenderer | Offset fisso | Medio |
| BUG-010 | 🟡 | DamageBreakdown | Formula non cached | Basso |
| OPT-001 | 🟢 | Multiple | Duplicazione codice | Alto |
| OPT-002 | 🟢 | ImpactVFX | VFX sempre attivi | Basso |
| OPT-004 | 🟢 | ImpactData | Cleanup non deterministico | Basso |

---

## 7. Priorità di Intervento Suggerita

### Fase 1: Fix Critici (Effort: ~4h)
1. BUG-001: Pehkui bonus
2. BUG-002: True damage
3. BUG-004: Enchant filtering

### Fase 2: UX Improvements (Effort: ~8h)
4. BUG-005: Observation timeout
5. BUG-007: Internazionalizzazione
6. BUG-008: Posizione configurabile

### Fase 3: Refactoring (Effort: ~16h)
7. OPT-001: Unificare rendering
8. DESIGN-002: Splittare DamageHandler
9. BUG-003: Breakdown riduzione danno
