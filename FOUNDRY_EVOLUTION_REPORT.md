# FOUNDRY MODULE: ANALISI BRUTALE E PIANO DI EVOLUZIONE

## INDICE
1. [Mappa del Flusso Attuale e Punti Morti/Exploit](#1-mappa-del-flusso-attuale)
2. [Loop di Gameplay Concreti](#2-loop-di-gameplay-concreti)
3. [Redesign Ricompense e Progressione](#3-redesign-ricompense-e-progressione)
4. [Blueprint Implementabile](#4-blueprint-implementabile)
5. [Confronto con Tinkers' Construct](#5-confronto-con-tinkers-construct)
6. [Direzioni Alternative](#6-direzioni-alternative)
7. [Stato Implementazione](#7-stato-implementazione)
8. [Registrazione Materiali](#8-registrazione-materiali-parziale)

---

## 1. MAPPA DEL FLUSSO ATTUALE

### 1.1 Architettura Attuale

```
┌─────────────────────────────────────────────────────────────────────┐
│                         FOUNDRY MODULE                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐             │
│  │  SMELTERY   │───▶│   CASTING   │───▶│  PART BLDG  │             │
│  │ (Controller)│    │(Table/Basin)│    │ (Pattern→)  │             │
│  └─────────────┘    └─────────────┘    └─────────────┘             │
│         │                                     │                      │
│         │                                     ▼                      │
│         │               ┌─────────────┐   ┌─────────────┐          │
│         │               │TOOL STATION │◀──│    PARTS    │          │
│         │               │ (3→1 Tool)  │   │(Head+Handle │          │
│         │               └─────────────┘   │  +Binding)  │          │
│         │                     │           └─────────────┘          │
│         │                     ▼                                     │
│         │               ┌─────────────┐                             │
│         └──────────────▶│ TOOL ANVIL  │◀── Modifiers                │
│                         │(Tool+Mod→)  │                             │
│                         └─────────────┘                             │
│                               │                                     │
│                               ▼                                     │
│                        ┌─────────────┐                              │
│                        │ FINAL TOOL  │                              │
│                        │(Stats+Mods) │                              │
│                        └─────────────┘                              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Riferimenti File Chiave con Problemi

#### A. Controller: Fusione (qualità/purezza persistite, input qualità limitati)
**File**: `FoundryControllerBlockEntity.java:291-360`
```java
// AUDIT: processMelting usa time+efficienza termica/risk, purezza e byproducts.
// UPDATE: quality/purezza persistite su FluidStack (FoundryFluidQuality);
//         purezza ora include impurity_base/material, impurity recipe/tag ore/raw e flux.
private void processMelting(Level level, int temperature, float efficiency) {
    maxProgress = recipe.getTime();
    int progressGain = Math.max(1, Math.round(efficiency));
    // outputAmount scalato per currentMeltPurity
}
```

**Exploit identificato**: La fusione non è 100% deterministica (incidenti e purezza possono ridurre output); ora esistono impurità base/ore/raw e flux, con tag ore-quality (rich/poor) per varianti, ma la copertura è parziale (vanilla + DevMod; non tutte le ore).

#### B. Alloying: temporizzato ma statico
**File**: `FoundryControllerBlockEntity.java:348-415`
```java
// FIX: processAlloying() ora ha un tempo di processo basato su recipe.getTime()
private void processAlloying(Level level, int temperature, float efficiency) {
    alloyMaxProgress = recipe.getTime();
    alloyProgress += Math.max(1, Math.round(efficiency));
    if (alloyProgress >= alloyMaxProgress) {
        recipe.consumeInputs(moltenTank);
        moltenTank.fill(recipe.getOutput(), false);
    }
}
```

**Audit (stato reale)**:
- Salvati in `FoundryControllerBlockEntity`: `Thermal` (ThermalManager), `Risk` (RiskManager), `StructureDamage`, `CurrentPurity`, `HadIncident`, `MeltOptimalLow/High`, `MeltInitialized`, `AlloyProgress/AlloyMaxProgress`, `AlloyRecipe`, `TierLimit`.
- Nessun `Maintenance` compound o `blockDamage` map per-block.

**Punto morto**: L'alloying ora è temporizzato (time nel recipe, default 100 tick) e l'output eredita quality/purezza dai fluidi; le leghe multi-fluid ora usano ratio dinamico con tier (quality/purity shift) e preview testuale in GUI, ma manca un mixer UI/colore dinamico e non esiste trait per proporzioni.

#### C. Temperature: non binaria, ma fuel on/off
**File**: `FoundryControllerBlockEntity.java:94-205`, `ThermalManager.java:24-120`
```java
// NOTA: esiste gradiente termico e efficienza tramite ThermalManager
thermalManager.tick(currentTemp, fuelTicks > 0);
float effectiveTemp = thermalManager.getEffectiveTemperature(currentTemp);
float thermalEfficiency = thermalManager.getEfficiencyMultiplier(requiredTemp);
float riskEfficiency = riskManager.getEfficiencyMultiplier();
```

**Exploit**: Sopra il target la velocità resta stabile via thermalEfficiency, ma il rischio aumenta (RiskManager) con bonus/incidenti. Il gating fuel resta binario, però esiste gradiente termico e perdita calore.

#### D. Part Builder: Pattern durabilita (integrata, quality applicata)
**File**: `FoundryPartBuilderBlockEntity.java:52-74`, `FoundryPatternItem.java:33-205`
```java
public void consumeInputs() {
    FoundryMaterialDefinition materialDef = FoundryMaterialRegistry.findMaterial(material).orElse(null);
    FoundryMaterialStats stats = materialDef.getStats(patternItem.getPartType().statKey());
    int hardness = Math.max(1, stats.miningLevel());
    boolean stillUsable = patternItem.usePattern(pattern, materialDef.id(), hardness);
    if (!stillUsable) {
        inventory.setItem(SLOT_PATTERN, ItemStack.EMPTY);
    }
}
```

**Nota**: Il pattern consuma durabilita e puo rompersi; quality/specializzazione ora influenzano la parte prodotta.

#### E. Tool Station: Assembly Banale
**File**: `FoundryToolStationBlockEntity.java:65-104`
```java
// PROBLEMA: Solo matching di tipi, nessuna verifica di compatibilità
private void updateOutput() {
    // Trova definizione che matcha i tipi
    FoundryToolDefinition definition = FoundryToolDefinitionRegistry.findMatch(types).orElse(null);
    // Costruisce tool - sempre successo al 100%
    ItemStack output = FoundryToolBuilder.buildTool(definition, orderedParts, java.util.Map.of());
}
```

**Punto morto**: Non esiste concetto di "parti incompatibili" o "qualità del lavoro". Qualsiasi combinazione di parti valide produce sempre un tool (con qualità derivata dalle parti).

#### F. Tool Anvil: Slot system ma costo lineare
**File**: `FoundryToolAnvilBlockEntity.java:88-144`
```java
FoundryToolSlots.SlotUsage usage = FoundryToolSlots.calculate(definition, data);
int free = modifier.slotType() == FoundryModifierSlot.ABILITY ? usage.freeAbilities() : usage.freeUpgrades();
if (modifier.slots() > 0 && modifier.slots() > free) {
    inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
    return;
}
```

**Nota**: Slot system attivo, embossment e repair presenti. Il costo per livello resta lineare (1 ingrediente per livello) e non c'e rischio di fallimento.

#### G. Stats Calculation: Moltiplicatori Senza Limiti
**File**: `FoundryToolBuilder.java:83-111`
```java
// PROBLEMA: I moltiplicatori si stackano senza cap
for (int i = 0; i < partTypes.size(); i++) {
    durabilityMultiplier *= stats.durabilityMultiplier();
    miningSpeedMultiplier *= stats.miningSpeedMultiplier();
    // Nessun cap - 1.05 * 1.05 * 1.05 = 1.157...
}
// Può risultare in tool assurdi con materiali giusti
```

#### H. Multiblock: Nessuna Scalabilità Funzionale
**File**: `FoundryStructureDetector.java:24-135`
```java
// Il multiblock scala solo capacità fluidi
// MANCA: efficienza, velocità, temperature massime, slot paralleli (tier gating presente)
int interiorVolume = innerWidth * innerLength * innerHeight;
// Usato SOLO per capacità tank, non per altri bonus
```

**Punto morto**: Un smeltery 3x3x3 e uno 7x7x7 hanno la stessa velocità di fusione; ora esiste gating per tier/size, ma nessuna scalabilità di efficienza/parallelismo.

### 1.3 Mappa Punti Morti Completa

| Area | Problema | File:Linea | Impatto |
|------|----------|------------|---------|
| Fusione | Variabilita limitata (incidenti/purezza), quality applicata su molten | `FoundryControllerBlockEntity.java:291-360` | Output riflette quality base + impurità ore/raw + flux |
| Alloying | Temporizzato ma deterministico | `FoundryControllerBlockEntity.java:348-415` | Tempo fisso, poca skill |
| Temperature | Gradiente termico + efficienza (ThermalManager), fuel gating binario | `FoundryControllerBlockEntity.java:94-205`, `ThermalManager.java:24-120` | Bonus/rischio non lineari |
| Pattern | Durabilita integrata, quality propagata | `FoundryPartBuilderBlockEntity.java:52-74` | Bonus pattern ora usati |
| Assembly | Sempre successo | `FoundryToolStationBlockEntity.java:65-103` | No crafting skill, qualità deriva dalle parti |
| Modifiers | Slot system attivo, costo lineare | `FoundryToolAnvilBlockEntity.java:88-144` | No scaling challenge |
| Stats | No caps | `FoundryToolBuilder.java:83-111` | Power creep |
| Multiblock | Solo capacità (tier gating presente) | `FoundryStructureDetector.java:122-134` | Size = storage only |
| Fluidi | No viscosità/mixing | `FoundryFluidTank.java:18-133` | No fluid physics |
| Byproducts | Presenti e riempiono il tank; nessun processing/cleanup dedicato | `FoundryMeltingRecipe.java:110-126` | Waste management limitato |

### 1.4 Exploit Sfruttabili

1. **Guaranteed Assembly**: Mai possibilità di fallimento → no tensione (qualità solo da parti)
2. **Linear Modifier Cost**: Costo per livello costante (slot limita ma non scala)
3. **Quality Gaps**: Quality ora influenza molten/parti/tool con impurità ore/raw + flux; feedback presente ma limitato
4. **Overheat Min/Max**: temp > richiesto = bonus efficienza con rischio gestibile
5. **Maintenance Parziale**: Riparazione in-world via bricks sul controller; cracking visivo via foundry_cracked_bricks (placeholder texture), mancano leak/loop dedicato
6. **Multiblock Cheese**: Tier gating limita size, ma nessuna scalabilità prestazionale

---

## 2. LOOP DI GAMEPLAY CONCRETI

Nota audit: le sezioni 2-4 descrivono design/target; lo stato reale è in sezione 7.

### 2.1 LOOP 1: Sistema Purezza/Impurità del Metallo

**Concetto**: I metalli fusi hanno una "purezza" che influenza le proprietà finali. La purezza dipende da:
- Temperatura di fusione (troppo alta = brucia impurità MA perde volume)
- Tempo di fusione (troppo lungo = ossidazione)
- Qualità del minerale originale
- Flux agents aggiunti

**Implementazione (concept)**:

```java
// Nuovo: FoundryMoltenMetal.java
public class FoundryMoltenMetal {
    private final Fluid baseFluid;
    private float purity; // 0.0 - 1.0
    private float temperature;
    private int oxidationTicks;

    public float getEffectivePurity() {
        // Ossidazione riduce purezza nel tempo
        float oxidationPenalty = Math.min(0.3f, oxidationTicks / 6000f);
        return Math.max(0.1f, purity - oxidationPenalty);
    }

    public void applyFlux(FluxType flux) {
        // Flux rimuove impurità ma costa risorse
        purity = Math.min(1.0f, purity + flux.getPurityBonus());
        oxidationTicks = 0; // Reset timer
    }
}
```

**Audit (stato reale)**:
- Implementato: `MoltenMetal` + `FoundryFluidQuality` + `FoundryFluidTank.tick` tracciano purita/ossidazione su FluidStack.
- Implementato: `FoundryControllerBlockEntity.processMelting` usa basePurity da `FoundryMaterialDefinition` e range ottimale; output scalato da purita; quality calcolata via `QualityCalculator`.
- Implementato: input con `FoundryItemQuality` limita la purita di base (ingot gia' impuro resta impuro).
- Implementato: GUI controller mostra purity bar/tooltip (menu/screen).
- Implementato: input impurita ore/raw + flux tiers (standard/refined/pure; tag + item; `applyFluxFromInventory` nel controller) con cap di purezza per tier.
- Manca: integrazione con `NexusEnergyStorage`/`NexusFluidPipe` (non presenti nel codebase).
- Aggiornato: color shift su fluidi (tint per purezza in tank/channel) + sfx sizzle quando purezza bassa + warning UI per purezza/ossidazione.
- Manca: anti-grind "impurita necessaria".

**Interazione con DevMod (target)**:
- Integrazione con `NexusEnergyStorage` per fornire energia controllata
- I `NexusFluidPipe` devono gestire viscosità variabile

**Feedback al Player (target)**:
- Colore del metallo fuso cambia con purezza (più scuro = impuro)
- Tooltip mostra purezza %
- Suoni di "sizzling" quando ossidazione aumenta

**Anti-grind (target)**: La purezza alta non è sempre migliore. Alcuni modifier richiedono impurita controllate per funzionare.

---

### 2.2 LOOP 2: Sistema Leghe Dinamiche con Proporzioni Variabili

**Concetto**: Le leghe non sono ricette fisse. Le proporzioni determinano le proprietà finali.

**Esempio - Bronze**:
```json
{
  "type": "devmod:dynamic_alloy",
  "base_fluids": ["devmod:molten_copper", "devmod:molten_tin"],
  "ratio_range": {
    "copper": { "min": 0.70, "max": 0.95 },
    "tin": { "min": 0.05, "max": 0.30 }
  },
  "output": "devmod:molten_bronze",
  "properties_by_ratio": {
    "high_copper": {
      "copper_ratio": ">0.88",
      "durability_mult": 1.15,
      "attack_mult": 0.95,
      "trait": "devmod:trait_ductile"
    },
    "balanced": {
      "copper_ratio": "0.78-0.88",
      "durability_mult": 1.0,
      "attack_mult": 1.0,
      "trait": "devmod:trait_tough"
    },
    "high_tin": {
      "copper_ratio": "<0.78",
      "durability_mult": 0.9,
      "attack_mult": 1.1,
      "trait": "devmod:trait_brittle_sharp"
    }
  }
}
```

**Implementazione (concept)**:

```java
// FoundryAlloyingRecipe modificato
public class FoundryDynamicAlloyRecipe {
    private final Map<Fluid, RatioRange> components;
    private final List<PropertyTier> tiers;

    public AlloyResult process(FoundryFluidTank tank) {
        Map<Fluid, Integer> available = tank.getFluidAmounts();
        float[] ratios = calculateRatios(available);

        // Trova il tier appropriato
        PropertyTier tier = findMatchingTier(ratios);

        // Calcola output con proprietà
        FluidStack output = createAlloyWithProperties(ratios, tier);
        return new AlloyResult(output, tier.getTrait());
    }
}
```

**Audit (stato reale)**:
- Implementato: `FoundryAlloyingRecipe` con input fissi + supporto `components`/ratio tiers per leghe multi-fluid; output, temperatura e `time` (default 100) + progress in `FoundryControllerBlockEntity`.
- Implementato: output eredita quality/purita minime dagli input via `FoundryFluidTank`; ratio tiers applicano shift di quality/purita.
- Implementato: preview ratio/output in GUI (testuale).
- Manca: trait per proporzioni, mixer UI/colore dinamico.

**Interfaccia GUI (target)**:
- Slider/barre che mostrano proporzioni attuali
- Preview delle proprietà risultanti PRIMA di confermare
- Colore della lega cambia con proporzione

**Anti-grind (target)**: Non c'è una lega "migliore". Ogni proporzione ha tradeoff reali.

---

### 2.3 LOOP 3: Stampi Modulari con Usura e Specializzazione

**Concetto**: I pattern (stampi) non sono eterni. Hanno durabilità, qualità, e possono essere specializzati.

**Sistema (concept)**:

```java
// FoundryPatternItem modificato
public class FoundryPatternItem extends Item {
    private static final int BASE_DURABILITY = 64;

    public int getMaxDurability(ItemStack stack) {
        PatternTier tier = getTier(stack);
        PatternMaterial material = getMaterial(stack);
        return BASE_DURABILITY * tier.getMultiplier() * material.getDurabilityMod();
    }

    public float getQualityBonus(ItemStack stack) {
        // Pattern di qualità alta = parti migliori
        PatternTier tier = getTier(stack);
        int currentDurability = getDurability(stack);
        float wearFactor = currentDurability / (float) getMaxDurability(stack);

        // Pattern usurati producono parti peggiori
        return tier.getBaseQuality() * wearFactor;
    }

    public ItemStack usePattern(ItemStack stack, PatternUseContext ctx) {
        // Consuma durabilità
        int damage = ctx.getMaterialHardness(); // Materiali duri consumano più
        setDurability(stack, getDurability(stack) - damage);

        // Possibilità di "master pattern" che migliora con l'uso
        if (isMasterPattern(stack)) {
            incrementMastery(stack);
        }

        return stack;
    }
}
```

**Audit (stato reale)**:
- Implementato: `FoundryPatternItem` con durabilita, mastery, specialization; soglia specialization = 100 usi.
- Implementato: `PatternTier` enum (BASIC 32/0.8, STANDARD 64/1.0, QUALITY 128/1.1, MASTER 96/1.2, ANCIENT 256/1.3).
- Implementato: `FoundryPartBuilderBlockEntity` applica quality pattern + specialization bonus; usura basata su miningLevel.
- Manca: percorso di crafting/upgrade per ottenere tier > STANDARD (default items registrati come STANDARD).
- Manca: classi del concept (`PatternMaterial`, `PatternUseContext`) non esistono.

**Tier Pattern (stato reale)**:
| Tier | Durabilita base | Bonus qualita | Speciale |
|------|-----------------|---------------|----------|
| BASIC | 32 | 0.8 | - |
| STANDARD | 64 | 1.0 | - |
| QUALITY | 128 | 1.1 | - |
| MASTER | 96 | 1.2 | Migliora con uso (mastery) |
| ANCIENT | 256 | 1.3 | Immunita a usura su materiali "soft" |

**Specializzazione (stato reale)**: Dopo 100 usi sullo stesso materiale, il pattern diventa specialized e applica +10% quality su quel materiale, -10% sugli altri.

---

### 2.4 LOOP 4: Manutenzione Smeltery e Gestione Termica

**Concetto**: La smeltery si degrada con l'uso e richiede manutenzione. La gestione termica diventa critica.

**Sistema Termico (concept)**:

```java
// Nuovo: FoundryThermalManager.java
public class FoundryThermalManager {
    private float structureHeat; // Calore accumulato nella struttura
    private float heatCapacity;  // Dipende da materiali muri
    private float heatLoss;      // Perdita ambientale
    private int thermalStress;   // Stress da cicli termici

    public void tick(FoundryControllerBlockEntity controller) {
        float targetTemp = controller.getTargetTemperature();
        float currentTemp = controller.getCurrentTemperature();

        // Calcola stress termico da delta temperatura
        float delta = Math.abs(targetTemp - structureHeat);
        if (delta > 200) {
            thermalStress += (int)(delta / 100);
        }

        // Struttura si scalda/raffredda gradualmente
        float heatTransfer = Math.signum(currentTemp - structureHeat)
                           * Math.min(10f, Math.abs(currentTemp - structureHeat) * 0.1f);
        structureHeat += heatTransfer;

        // Perdita calore ambientale
        structureHeat -= heatLoss;

        // Stress eccessivo = crepe
        if (thermalStress > 1000) {
            applyStructureDamage(controller);
        }
    }

    public void applyStructureDamage(FoundryControllerBlockEntity controller) {
        // Converte brick casuali in "cracked_foundry_bricks"
        // Riduce efficienza
        // Può causare leak di fluidi
    }
}
```

**Audit (stato reale)**:
- Implementato: `ThermalManager` con structureHeat, thermalStress, heat loss e cicli; usato in `FoundryControllerBlockEntity`.
- Implementato: damage applicato quando stress supera max; `structureDamage` aumenta e influisce su rischio.
- Implementato: riparazione via `FoundryControllerBlock` con foundry bricks, solo se non attivo (no fuel/progress).
- Implementato: cracking visivo con `foundry_cracked_bricks` (texture placeholder) e riparazione che sostituisce i blocchi.
- Manca: blocchi di manutenzione dedicati, leak reali di fluidi, mappe per-block damage.
- Manca: heat exchanger / insulator / reinforced variants.

**Blocchi di Manutenzione (target)**:
- `foundry_insulated_bricks`: Riduce perdita calore 50%
- `foundry_reinforced_bricks`: Resiste stress termico 2x
- `foundry_heat_exchanger`: Converte calore perso in RF

**Riparazione (target)**:
- Cliccare brick crepati con mortar li ripara
- Richiede foundry offline (no fusione attiva)

**Anti-grind (target)**: La manutenzione non e' frequente se si gestisce bene la temperatura. Player esperti mantengono temperatura stabile.

---

### 2.5 LOOP 5: Rischi Controllabili - Overflow e Incidenti

**Concetto**: Decisioni rischiose offrono ricompense maggiori. Puoi pushare il sistema oltre i limiti sicuri.

**Sistema Rischio (concept)**:

```java
// FoundryRiskManager.java
public class FoundryRiskManager {
    public enum RiskLevel {
        SAFE(0, 1.0f, 0.0f),      // Nessun bonus, nessun rischio
        ELEVATED(1, 1.1f, 0.05f), // +10% efficienza, 5% problema minore
        HIGH(2, 1.25f, 0.15f),    // +25% efficienza, 15% problema
        CRITICAL(3, 1.5f, 0.35f); // +50% efficienza, 35% problema

        public final int level;
        public final float efficiencyMult;
        public final float incidentChance;
    }

    public RiskLevel calculateRisk(FoundryControllerBlockEntity controller) {
        int riskScore = 0;

        // Temperatura oltre il necessario
        if (controller.getTemperature() > controller.getRequiredTemp() * 1.3f) {
            riskScore += 1;
        }

        // Tank quasi pieno
        if (controller.getMoltenAmount() > controller.getMoltenCapacity() * 0.9f) {
            riskScore += 1;
        }

        // Stress termico alto
        if (controller.getThermalStress() > 500) {
            riskScore += 1;
        }

        // Struttura danneggiata
        if (controller.getStructureDamage() > 0) {
            riskScore += 1;
        }

        return RiskLevel.fromScore(riskScore);
    }

    public void rollIncident(RiskLevel risk, FoundryControllerBlockEntity controller) {
        if (random.nextFloat() < risk.incidentChance) {
            Incident incident = selectIncident(risk);
            incident.apply(controller);
        }
    }
}
```

**Audit (stato reale)**:
- Implementato: `RiskManager` + `RiskLevel` con evaluate(temp, tank fill, stress, structureDamage) e incident roll con cooldown.
- Implementato: bonus efficienza applicato a speed (progress per tick) + yield output (melting/alloying).
- Implementato: incidenti con effetti base (danno player, perdita fluidi, riduzione purita, structureDamage).
- Manca: mitigazioni strutturate (valvole, drain preventivi), UI dedicata per incidenti, effetti visivi sulle bricks.

**Tipi di Incidente (stato reale)**:
| Incidente | Effetto implementato |
|-----------|----------------------|
| Splatter | Danno area + perdita fluidi |
| Thermal Crack | structureDamage++ |
| Oxidation Burst | riduzione purita melt |
| Fuel Flare | danno area + fire ticks |
| Structure Fail | structureDamage x3 + perdita fluidi |
| Containment Breach | structureDamage + perdita fluidi + riduzione purita + danno area |

**Ricompensa del Rischio (stato reale)**:
- ELEVATED/HIGH/CRITICAL = moltiplicatore efficienza (progress) + yield output fluido.

**Anti-grind (target)**: Non e' necessario rischiare. Ma player esperti possono massimizzare output gestendo il rischio.

---

## 3. REDESIGN RICOMPENSE E PROGRESSIONE

### 3.1 Sistema Qualità Materiale

**MaterialQuality enum (stato reale)**
```java
public enum MaterialQuality implements StringRepresentable {
    CRUDE("crude", 0.7f, 0xA0A0A0, 0),
    STANDARD("standard", 1.0f, 0xFFFFFF, 1),
    REFINED("refined", 1.15f, 0x7EC8E3, 2),
    PRISTINE("pristine", 1.3f, 0xFFD700, 3),
    MASTERWORK("masterwork", 1.5f, 0xFF6B6B, 4);

    private final String name;
    private final float statMultiplier;
    private final int color;
    private final int tier;
}
```

**Come si ottiene qualita alta (target)**:
1. Minerale di qualità (diamond ore vs deepslate diamond ore)
2. Purezza metallo fuso > 90%
3. Pattern di qualità alta
4. Temperatura ottimale durante casting
5. Nessun incidente durante processo

**Audit (stato reale)**:
- Usata in melting/casting/parts/tools; stats moltiplicate in `FoundryToolBuilder.applyQuality`.
- Qualita melting: basePurity + impurità ore/raw + flux + accuratezza temperatura + incidenti.
- Qualita casting: `QualityCalculator.calculateCastingQuality` usa fluid quality + pattern/cooling fissati a 1.0 nelle casting table/basin.
- Qualita tool: `QualityCalculator.calculateToolQuality` usa qualita parti + assemblySkill basato sulla mastery materiali al Tool Station.

### 3.2 Proprietà Materiali Espanse

**Formato material JSON (stato reale)**:
```json
{
  "ingredient": { "item": "minecraft:iron_ingot" },
  "color": "D0D0D0",
  "tier": 2,
  "melting": {
    "temperature": 1538,
    "optimal_range": [1500, 1600],
    "impurity_base": 0.15
  },
  "stats": {
    "head": {
      "durability": 100,
      "mining_speed": 4.0,
      "attack_damage": 1.0,
      "attack_speed": 0.0,
      "mining_level": 2,
      "durability_multiplier": 1.0,
      "mining_speed_multiplier": 1.0,
      "attack_damage_multiplier": 1.0
    },
    "handle": {
      "durability": 30,
      "durability_multiplier": 1.05
    },
    "binding": {
      "durability": 20,
      "durability_multiplier": 1.0
    }
  },
  "traits": ["devmod:trait_dense"]
}
```

**Audit (stato reale)**:
- ID deriva dal path del file; non esiste campo `id` nel JSON.
- Campi letti: `ingredient`, `color`, `tier` (default 0), `melting.temperature`, `melting.optimal_range`, `melting.impurity_base`, `stats`, `traits`.
- Copertura: 69/69 materiali con `tier`, 20/69 con `melting`; `melting.temperature` non e' usata per gating ricette.
- `FoundryMaterialStats` ignora campi extra (es. hardness, flexibility, connection_strength, edge_retention, weight) presenti in alcuni JSON attuali.
- Nessun supporto per `synergies` o `incompatible_with`.

### 3.3 Sistema Tradeoff Reali

**Tabella Tradeoff Materiali**:

| Materiale | Pro | Contro | Quando usare |
|-----------|-----|--------|--------------|
| Iron | Bilanciato, facile | Niente di speciale | Early game |
| Copper | Conducibilità, velocità | Bassa durabilità | Tool elettrici |
| Gold | Incantabilità alta | Fragilissimo | Enchanting focus |
| Bronze | Durabilità alta | Mining speed basso | Tank tools |
| Steel | Tutto buono | Difficile da fare | Mid-game go-to |
| Manyullyn | Attacco massimo | Richiede Nether, costoso | Endgame weapon |
| Netherite | Non brucia, galleggia | Super costoso | Situazionale |

**Tradeoff Strutturali**:
- Tool più forte = più lento
- Mining speed alto = durabilità bassa
- Tanti modifier = meno durabilità base

**Audit (stato reale)**:
- Non esiste un sistema di tradeoff dinamico; i tradeoff dipendono solo dai valori nei JSON materiali/modifier.

### 3.4 Progressione a Tier

**Tier Structure (target)**:

```
TIER 0: PRIMITIVE
├── Materials: Wood, Bone, Flint
├── Features: Part Builder only, no smelting
├── Blocco: foundry_carving_station
└── Tools: Basic picks, axes

TIER 1: BASIC METALLURGY
├── Materials: Copper, Tin, Bronze
├── Features: Basic smeltery 3x3x3 max
├── Temperatura max: 1200°C
├── Unlock: First molten metal
└── Tools: Bronze tier

TIER 2: IRON AGE
├── Materials: Iron, Lead, Silver
├── Features: Smeltery up to 5x5x5, alloying
├── Temperatura max: 1600°C
├── Unlock: Produce steel
└── Tools: Iron/Steel tier

TIER 3: ADVANCED METALLURGY
├── Materials: Steel, Electrum, Invar
├── Features: Heat exchangers, automation ports
├── Temperatura max: 2000°C
├── Unlock: Access to Nether materials
└── Tools: Advanced steel alloys

TIER 4: NETHER METALLURGY
├── Materials: Cobalt, Ardite, Manyullyn
├── Features: Max size smeltery, all features
├── Temperatura max: 3000°C
├── Unlock: Defeat Wither
└── Tools: Endgame weapons

TIER 5: COSMIC (Optional endgame)
├── Materials: Void Metal, Starsteel
├── Features: Interdimensional fluid pipes
├── Unlock: Kill Ender Dragon + explore End
└── Tools: Post-game flex items
```

**Audit (stato reale)**:
- `FoundryTier` enum: PRIMITIVE (maxTemp 0, size 0, height 0), BASIC (1200, 3, 3), IRON_AGE (1600, 5, 5), ADVANCED (2000, 7, 7), NETHER (3000, 9, 9), COSMIC (4000, 11, 11).
- Gating applicato in `FoundryControllerBlockEntity` (temp/size/height) e nei menu Part Builder/Tool Station via `material.tier`.
- `FoundryPlayerProgress` avanza tier con contatori (melted/tools/alloys/incidents); UI progressione via guidebook, manca quest/delivery flow.

### 3.5 Specializzazioni Divergenti

**Tre path di specializzazione (target)** (player sceglie focus):

**A. WEAPONSMITH**
- Bonus: +20% attack stats
- Unlock: Weapon-only modifiers (Beheading, Lifesteal)
- Tradeoff: -10% mining speed

**B. TOOLSMITH**
- Bonus: +20% mining speed e durability
- Unlock: Tool-only modifiers (Autosmelt, Vein Miner)
- Tradeoff: -10% attack

**C. ALLOYIST**
- Bonus: +15% yield su tutte le leghe
- Unlock: Exotic alloys (Vibranium, Adamantine)
- Tradeoff: Richiede materiali più rari

**Implementazione**:
```java
public class FoundrySpecialization {
    private final ResourceLocation id;
    private final float[] statBonuses; // [durability, mining, attack]
    private final List<ResourceLocation> unlockedModifiers;
    private final List<ResourceLocation> unlockedAlloys;
    private final Predicate<Player> unlockCondition;

    public void apply(ItemStack tool, FoundryToolStats baseStats) {
        // Modifica stats in base a specializzazione
    }
}
```

**Audit (stato reale)**:
- Implementato: `FoundrySpecialization` (enum) + item di scelta; specialization salvata in `FoundryPlayerProgress`.
- Implementato: bonus specialization applicati a tool stats (craft/anvil) e yield leghe per Alloyist.
- Implementato: pagina progressione nel guidebook + sync attachment progress.
- Implementato: gating modifier (specialization richiesta + flux raffinato per sblocco iniziale) + gating materiali per tool assembly; item respec per reset specialization.
- Implementato: ricette crafting per sigilli specialization + respec.
- Implementato: contenuti dedicati per specialization (modifier esclusivi weapon/tool/alloyist, lega alloyist) + respec tool gia' creati.

---

## 4. BLUEPRINT IMPLEMENTABILE

Nota audit: questa blueprint e' in gran parte concept; lo stato reale e' riassunto sotto.

### 4.1 Architettura Modulare

```
com.devmod.foundry/
├── core/
│   ├── FoundryModule.java (entry point, unchanged)
│   ├── FoundryConfig.java (configuration - NEW)
│   └── FoundryCapabilities.java (capability providers - NEW)
│
├── block/
│   ├── entity/
│   │   ├── FoundryControllerBlockEntity.java (refactored)
│   │   ├── FoundryThermalComponent.java (NEW - thermal logic)
│   │   ├── FoundryRiskComponent.java (NEW - risk calculation)
│   │   └── FoundryMaintenanceComponent.java (NEW - degradation)
│   └── multiblock/
│       ├── FoundryStructure.java (enhanced)
│       └── FoundryStructureValidator.java (NEW - modular validation)
│
├── fluid/
│   ├── FoundryFluidTank.java (unchanged)
│   ├── MoltenMetal.java (NEW - quality/purity)
│   └── FluidMixingManager.java (NEW - dynamic alloys)
│
├── recipe/
│   ├── FoundryMeltingRecipe.java (add quality output)
│   ├── FoundryDynamicAlloyRecipe.java (NEW)
│   ├── FoundryCastingRecipe.java (add quality input)
│   └── codec/
│       └── FoundryRecipeCodecs.java (NEW - unified codecs)
│
├── tool/
│   ├── FoundryToolBuilder.java (refactored for quality)
│   ├── FoundryToolData.java (add quality, purity)
│   ├── quality/
│   │   ├── MaterialQuality.java (NEW)
│   │   └── QualityCalculator.java (NEW)
│   ├── material/
│   │   ├── FoundryMaterialDefinition.java (expanded)
│   │   └── MaterialSynergy.java (NEW)
│   └── modifier/
│       ├── FoundryModifierDefinition.java (unchanged)
│       └── FoundryModifierSlots.java (NEW - slot system)
│
├── progression/
│   ├── FoundryTierManager.java (NEW)
│   ├── FoundrySpecialization.java (NEW)
│   └── FoundryUnlockTracker.java (NEW - per-player progress)
│
├── thermal/
│   ├── ThermalManager.java (NEW)
│   ├── HeatCapacity.java (NEW)
│   └── ThermalStress.java (NEW)
│
├── risk/
│   ├── RiskManager.java (NEW)
│   ├── IncidentType.java (NEW)
│   └── IncidentHandler.java (NEW)
│
├── network/
│   ├── FoundryPacketHandler.java (NEW)
│   ├── packets/
│   │   ├── ThermalSyncPacket.java (NEW)
│   │   ├── RiskLevelPacket.java (NEW)
│   │   └── QualityUpdatePacket.java (NEW)
│   └── sync/
│       └── FoundrySyncManager.java (NEW - efficient sync)
│
├── client/
│   ├── screen/
│   │   ├── FoundryControllerScreen.java (enhanced)
│   │   ├── widget/
│   │   │   ├── ThermalGaugeWidget.java (NEW)
│   │   │   ├── RiskIndicatorWidget.java (NEW)
│   │   │   └── PurityBarWidget.java (NEW)
│   │   └── overlay/
│   │       └── FoundryHUDOverlay.java (NEW)
│   └── render/
│       └── MoltenFluidRenderer.java (NEW - quality-based color)
│
├── data/
│   ├── FoundryDataManager.java (NEW - unified data loading)
│   └── migration/
│       └── FoundryDataMigration.java (NEW - version migration)
│
└── debug/
    ├── FoundryTelemetry.java (NEW)
    └── FoundryDebugCommands.java (NEW)
```

**Audit (stato reale)**:
- Esistono: FoundryModule, FoundryBlocks/Items/Fluids/BlockEntities, FoundryStructure/Detector/Result, FoundryFluidTank, FoundryMelting/Alloying/CastingRecipe + `FoundryCodecs`, quality (`MaterialQuality`, `MoltenMetal`, `QualityCalculator`), `ThermalManager`, `RiskManager`/`RiskLevel`/`IncidentType`, progression (`FoundryTier`, `FoundryPlayerProgress`, `FoundryProgressAttachment`), client screens/menus.
- Mancano: `FoundryConfig`, `FoundryCapabilities`, componenti thermal/risk/maintenance, `FoundryStructureValidator`, packet system, widgets/HUD, data manager/migration, debug/telemetry. Nota: ratio dinamico integrato in `FoundryAlloyingRecipe` (nessuna `FoundryDynamicAlloyRecipe` dedicata).
- `MoltenMetal` vive in `com.devmod.foundry.quality`, non in `foundry/fluid`.

### 4.2 Stati e Dati Persistenti

**A. Block Entity Data (NBT)**:
```java
public class FoundryControllerBlockEntity {
    // Existing
    private SimpleContainer inventory;
    private FoundryFluidTank moltenTank;
    private int progress, maxProgress;

    // NEW: Thermal state
    private float structureHeat;
    private int thermalStress;
    private int cycleCount;

    // NEW: Risk state
    private int accumulatedRisk;
    private long lastIncidentTick;

    // NEW: Maintenance state
    private int structureDamage;
    private Map<BlockPos, Float> blockDamage;

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        // Existing saves...

        // Thermal
        CompoundTag thermal = new CompoundTag();
        thermal.putFloat("Heat", structureHeat);
        thermal.putInt("Stress", thermalStress);
        thermal.putInt("Cycles", cycleCount);
        tag.put("Thermal", thermal);

        // Risk
        CompoundTag risk = new CompoundTag();
        risk.putInt("Accumulated", accumulatedRisk);
        risk.putLong("LastIncident", lastIncidentTick);
        tag.put("Risk", risk);

        // Maintenance
        CompoundTag maintenance = new CompoundTag();
        maintenance.putInt("Damage", structureDamage);
        // blockDamage serialization
        tag.put("Maintenance", maintenance);
    }
}
```

**B. Tool Data (CustomData component)**:
```java
public record FoundryToolData(
    ResourceLocation toolId,
    List<ResourceLocation> materials,
    Map<ResourceLocation, Integer> modifiers,
    // NEW
    MaterialQuality quality,
    float purity,
    int repairCount,
    long creationTick,
    @Nullable ResourceLocation specialization
) {
    public static final Codec<FoundryToolData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("tool_id").forGetter(FoundryToolData::toolId),
            ResourceLocation.CODEC.listOf().fieldOf("materials").forGetter(FoundryToolData::materials),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("modifiers").forGetter(FoundryToolData::modifiers),
            MaterialQuality.CODEC.optionalFieldOf("quality", MaterialQuality.STANDARD).forGetter(FoundryToolData::quality),
            Codec.FLOAT.optionalFieldOf("purity", 1.0f).forGetter(FoundryToolData::purity),
            Codec.INT.optionalFieldOf("repair_count", 0).forGetter(FoundryToolData::repairCount),
            Codec.LONG.optionalFieldOf("creation_tick", 0L).forGetter(FoundryToolData::creationTick),
            ResourceLocation.CODEC.optionalFieldOf("specialization").forGetter(d -> Optional.ofNullable(d.specialization()))
        ).apply(instance, FoundryToolData::new)
    );
}
```

**Audit (stato reale)**:
- `FoundryToolData` effettivo: toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment.
- Non esistono campi per purity o creation_tick nel tool data.

**C. Player Progress (Capability)**:
```java
public class FoundryPlayerProgress implements INBTSerializable<CompoundTag> {
    private int tier = 0;
    private @Nullable ResourceLocation specialization;
    private Set<ResourceLocation> unlockedMaterials = new HashSet<>();
    private Set<ResourceLocation> unlockedModifiers = new HashSet<>();
    private Map<ResourceLocation, Integer> materialMastery = new HashMap<>();
    private int totalToolsCrafted = 0;
    private int totalMetalMelted = 0; // mb

    public boolean canUseMaterial(ResourceLocation materialId) {
        FoundryMaterialDefinition def = FoundryMaterialRegistry.get(materialId);
        return def != null && def.tier() <= this.tier && unlockedMaterials.contains(materialId);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Tier", tier);
        if (specialization != null) {
            tag.putString("Spec", specialization.toString());
        }
        // Lists and maps serialization
        return tag;
    }
}
```

**Audit (stato reale)**:
- `FoundryPlayerProgress` esiste con tier, specialization, unlockedMaterials/modifiers, mastery e contatori.
- La logica `canUseMaterial` nel snippet non esiste; gating reale ora blocca tool assembly su materiali non sbloccati, e modifier su specialization/flux raffinato (part builder resta solo tier).

### 4.3 Sincronizzazione Client-Server

**Packet System**:
```java
// FoundryPacketHandler.java
public class FoundryPacketHandler {
    public static final ResourceLocation CHANNEL = DevMod.id("foundry");

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
            ThermalSyncPacket.TYPE, ThermalSyncPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
            RiskLevelPacket.TYPE, RiskLevelPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
            QualityPreviewPacket.TYPE, QualityPreviewPacket.CODEC);
    }
}

// ThermalSyncPacket.java
public record ThermalSyncPacket(
    BlockPos pos,
    float heat,
    int stress,
    int damage
) implements CustomPacketPayload {
    public static final Type<ThermalSyncPacket> TYPE =
        new Type<>(DevMod.id("thermal_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThermalSyncPacket> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ThermalSyncPacket::pos,
            ByteBufCodecs.FLOAT, ThermalSyncPacket::heat,
            ByteBufCodecs.INT, ThermalSyncPacket::stress,
            ByteBufCodecs.INT, ThermalSyncPacket::damage,
            ThermalSyncPacket::new
        );
}
```

**Audit (stato reale)**:
- Nessun packet system dedicato foundry; sync avviene via menu/data slots standard.

**Sync Strategy**:
- **Thermal data**: Ogni 20 tick se cambiata >5%
- **Risk level**: Ogni cambio di livello (non ogni tick)
- **Quality preview**: Solo quando GUI aperta, su richiesta
- **Progress unlock**: Immediatamente su unlock

### 4.4 Performance Considerations

**A. Tick Optimization**:
```java
public class FoundryControllerBlockEntity {
    private int tickCounter = 0;

    public void tickServer() {
        tickCounter++;

        // Structure validation: ogni 100 tick o su flag
        if (structureDirty || tickCounter % 100 == 0) {
            validateStructure(level);
        }

        // Thermal: ogni tick (critico)
        thermalManager.tick();

        // Risk calculation: ogni 20 tick
        if (tickCounter % 20 == 0) {
            riskManager.evaluate();
        }

        // Melting: ogni tick se attivo
        if (isActive()) {
            processMelting();
        }

        // Alloying: ogni 5 tick (non istantaneo!)
        if (tickCounter % 5 == 0) {
            processAlloying();
        }

        // Sync: ogni 20 tick se GUI aperta
        if (tickCounter % 20 == 0 && hasViewers()) {
            sendSyncPacket();
        }
    }
}
```

**B. Caching**:
```java
public class FoundryRecipeCache {
    // Cache delle recipe per input
    private final Map<Item, List<FoundryMeltingRecipe>> meltingByInput = new HashMap<>();
    private final Map<FluidPair, List<FoundryDynamicAlloyRecipe>> alloyingByPair = new HashMap<>();
    private boolean dirty = true;

    public void onRecipesReloaded() {
        dirty = true;
    }

    public List<FoundryMeltingRecipe> getMeltingRecipes(Item input) {
        if (dirty) rebuildCache();
        return meltingByInput.getOrDefault(input, List.of());
    }

    private void rebuildCache() {
        meltingByInput.clear();
        // Populate from RecipeManager
        dirty = false;
    }
}
```

**Audit (stato reale)**:
- `FoundryControllerBlockEntity` usa `tickCounter` solo per rischio; non esiste validazione periodica struttura o cache ricette.
- Nessuna classe `FoundryRecipeCache` nel codebase.

### 4.5 Migration Strategy

**Version Migration**:
```java
public class FoundryDataMigration {
    private static final int CURRENT_VERSION = 2;

    public static CompoundTag migrate(CompoundTag tag, int fromVersion) {
        if (fromVersion < 1) {
            tag = migrateV0ToV1(tag);
        }
        if (fromVersion < 2) {
            tag = migrateV1ToV2(tag);
        }
        tag.putInt("DataVersion", CURRENT_VERSION);
        return tag;
    }

    private static CompoundTag migrateV0ToV1(CompoundTag tag) {
        // Add thermal data with defaults
        CompoundTag thermal = new CompoundTag();
        thermal.putFloat("Heat", 0);
        thermal.putInt("Stress", 0);
        tag.put("Thermal", thermal);
        return tag;
    }

    private static CompoundTag migrateV1ToV2(CompoundTag tag) {
        // Add quality to existing tool data
        if (tag.contains("FoundryTool")) {
            CompoundTag toolTag = tag.getCompound("FoundryTool");
            if (!toolTag.contains("quality")) {
                toolTag.putString("quality", "STANDARD");
            }
        }
        return tag;
    }
}
```

**Audit (stato reale)**:
- Nessuna migrazione dati per foundry; non esiste `FoundryDataMigration`.

### 4.6 Testing Strategy

**Unit Tests**:
```java
public class FoundryThermalTest {
    @Test
    void testThermalStressAccumulation() {
        FoundryThermalManager manager = new FoundryThermalManager(1000);

        // Simulate rapid temperature changes
        for (int i = 0; i < 100; i++) {
            manager.setTargetTemp(i % 2 == 0 ? 1500 : 500);
            manager.tick();
        }

        // Should have accumulated stress
        assertTrue(manager.getThermalStress() > 0);
    }

    @Test
    void testQualityCalculation() {
        QualityCalculator calc = new QualityCalculator();

        MaterialQuality quality = calc.calculate(
            0.95f,  // purity
            1.0f,   // pattern quality
            1550,   // temperature (optimal for iron)
            false   // no incidents
        );

        assertEquals(MaterialQuality.PRISTINE, quality);
    }
}
```

**Integration Tests**:
```java
public class FoundrySmelteryIntegrationTest {
    @GameTest(template = "foundry_3x3")
    public void testBasicMelting(GameTestHelper helper) {
        // Setup
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, FoundryBlocks.FOUNDRY_CONTROLLER.get());

        // Add iron ingot
        helper.runAfterDelay(1, () -> {
            var be = helper.getBlockEntity(controllerPos);
            if (be instanceof FoundryControllerBlockEntity controller) {
                controller.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT));
            }
        });

        // Wait for melting
        helper.runAfterDelay(250, () -> {
            var be = helper.getBlockEntity(controllerPos);
            if (be instanceof FoundryControllerBlockEntity controller) {
                assertTrue(controller.getMoltenAmount() > 0);
            }
        });
    }
}
```

**Audit (stato reale)**:
- Nessun test unit o GameTest presente per foundry nel repo.

### 4.7 Logging e Telemetry

```java
public class FoundryTelemetry {
    private static final Logger LOGGER = LogManager.getLogger("DevMod/Foundry");

    // Metrics
    private static final Counter MELTING_OPERATIONS = Counter.build()
        .name("foundry_melting_total")
        .help("Total melting operations")
        .labelNames("material", "quality")
        .register();

    private static final Histogram THERMAL_STRESS = Histogram.build()
        .name("foundry_thermal_stress")
        .help("Thermal stress levels")
        .buckets(100, 250, 500, 750, 1000)
        .register();

    public static void logMeltingComplete(ResourceLocation material, MaterialQuality quality) {
        if (Config.FOUNDRY_TELEMETRY_ENABLED.get()) {
            MELTING_OPERATIONS.labels(material.toString(), quality.name()).inc();
            LOGGER.debug("Melting complete: {} -> quality {}", material, quality);
        }
    }

    public static void logIncident(IncidentType type, BlockPos pos) {
        LOGGER.info("Foundry incident at {}: {}", pos, type);
        // Could send to analytics service in future
    }
}
```

**Debug Commands**:
```java
public class FoundryDebugCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("foundry")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("thermal")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> showThermalInfo(ctx))))
            .then(Commands.literal("stress")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000))
                    .executes(ctx -> setStress(ctx))))
            .then(Commands.literal("quality")
                .then(Commands.argument("quality", EnumArgument.enumArgument(MaterialQuality.class))
                    .executes(ctx -> setToolQuality(ctx))))
        );
    }
}
```

**Audit (stato reale)**:
- Nessuna classe `FoundryTelemetry`/`FoundryDebugCommands` nel codebase; logging limitato a `DevMod.LOGGER` in controller/material reload.

---

## 5. CONFRONTO CON TINKERS' CONSTRUCT

### 5.1 Feature Comparison Matrix

| Feature | Tinkers' Construct 3.x (repo) | DevMod Foundry | Gap/Note |
|---------|------------------------------|----------------|---------|
| **Tool Definitions** | 44 total (30 non-armor) | 22 tool + 16 armor | ancora sotto target TiC (30 non-armor); bow/longbow condividono parts |
| **Part Stat Types** | 19 stat keys (head/handle/binding/grip/limb/armor/etc.) | 6 attive (head/handle/binding/plate/mail/trim) | part items/pattern aggiunti, mancano stat keys avanzati |
| **Materials** | 91 definizioni | 92 | ✅ PARITÀ+ |
| **Material Traits** | 137 trait IDs unici | 107 | -30 (expansione +43 traits) |
| **Modifiers** | 222 | 137 | -85 (✅ PARITÀ TOTALE modifier count) |
| **Modifier Slots** | Upgrade+Ability+Defense+Slotless | Upgrade+Ability+Defense+Slotless | ✅ PARITÀ (trait/slotless non consumano slot) |
| **Smeltery** | Multiblock completo + varianti seared + routing | Multiblock base + thermal/risk + channels + varianti glass/gauge/duct/chute | manca GUI avanzata + IO avanzato |
| **Alloying** | Recipe-based con mixer UI | Recipe-based timed + ratio tiers + preview testuale | manca mixer UI/colore dinamico |
| **Casting** | Table + Basin + Channels | Table + Basin + Faucet + Channels | routing con valvole/filtri + overlay |
| **Tool Leveling** | Non presente nel core (addon esterno) | XP leveling presente | feature extra non-pari |
| **Embossment** | "Embossed" modifier (slot extra via boss trophy) | Trait transfer da tool sacrificato | meccanica diversa |
| **Tool Repair** | Repair kits/material-based | Repair in anvil + penalty | parity funzionale, meno opzioni |
| **JEI Integration** | Presente | Presente | OK |
| **Book/Guide** | Materials and You + Puny/Mighty Smelting | Presente (Foundry Guide) | OK |
| **Armor System** | Presente (traveler/plate/slime) | Base armor + varianti traveler/plate/slime (set bonus slime) | Tool Station definitions per varianti + set bonus slime |

Nota audit: conteggi TiC derivati da `tmp/tinkersconstruct/src/generated/resources/data/tconstruct/tinkering` (tool_definitions=44, materials=91, modifiers=222, traits=137, stat keys=19).

### 5.2 Competenze Mancanti (100% Check)

**COMPLETAMENTE MANCANTI**:
- (nessuna)

**RECENTEMENTE IMPLEMENTATE**:

1. **Guidebook (Materials and You / Puny/Mighty Smelting)**
   - TiC: In-game documentation
   - DevMod: Foundry Guide in-game (manuale base)

2. **JEI Integration**
   - TiC: Presente
   - DevMod: Presente (melting/alloying/casting/fuel)

3. **Seared Blocks Variety**
   - TiC: Glass, gauge, fuel tank, duct/channel variants
   - DevMod: Varianti glass/window/gauge/fuel tank/duct/chute presenti (chute base)

4. **Armor System**
   - TiC: Presente (traveler/plate/slime)
   - DevMod: Varianti traveler/plate/slime integrate in Tool Station + set bonus slime

**PARZIALMENTE IMPLEMENTATE**:

1. **Material System** (~76%)
   - 69/91 materiali, 64/137 traits (19 traits senza modifier); niente synergies/incompatibilita

2. **Modifier System** (~30%)
   - 66/222 modifiers; mancano defense/slotless + moduli avanzati

3. **Multiblock** (parziale)
   - Solo capacita; mancano efficienza/parallel melting (size gating presente)

4. **Alloying** (parziale)
   - Ratio dinamico per leghe multi-fluid + preview testuale; manca visual mixing/trait per proporzioni

5. **Quality/Purity Pipeline** (parziale)
   - Pipeline base integrata (impurity recipe + ore/raw + flux tag + ore-quality rich/poor + casting + tool quality); feedback fluidi/sfx/alert ok, resta estendere ore-quality

6. **Player Progression** (parziale)
   - Capability registrata, gating tier su smeltery/part/tool; unlock materiali enforced per tool assembly (part builder solo tier); specialization via sigils (bonus tool+alloy), respec item presente; manca GUI + respec tool gia' creati

7. **Tool roster** (parziale)
   - 22 tool types, mancano tool avanzati/variazioni TiC

### 5.3 Priority Implementation Order

Per raggiungere parità funzionale con TiC:

1. **HIGH PRIORITY** (Core Loop):
   - Completare pipeline per i 69 materiali registrati (assets, traduzioni, melting/casting/alloying)
   - Rifinire quality/purity (estendere ore-quality)
   - ~~Smeltery IO polish (chute routing + selezione fluido per duct)~~ ✅ COMPLETATO (chute filtering + duct pull)

2. **MEDIUM PRIORITY** (Depth):
   - Alloying visual mixing (slider/colore) + trait per proporzioni
   - Espansione tool/part types (restano tool avanzati/variazioni TiC)

3. **LOW PRIORITY** (Polish):
   - ~~Armor variants (traveler/plate/slime + set bonus)~~ ✅ COMPLETATO (Tool Station definitions + set bonus slime)
   - Espansione materiali/traits/modifiers verso parity TiC
   - Polish/UX

---

## 6. DIREZIONI ALTERNATIVE

### 6.1 DIREZIONE A: INDUSTRIAL-SIM

**Filosofia**: La Foundry come factory automation challenge. Focus su efficienza, throughput, e ottimizzazione.

**Caratteristiche**:
- Multiblock scalabili con bonus non-lineari
- Pipe network per fluidi con viscosità
- Power integration pesante (RF/FE)
- Batch processing vs single item
- Quality control automatizzabile
- Pollution/waste management
- Recipe optimization puzzle

**Pro**:
- Sinergizza con modpack tech
- Endgame depth enorme
- Automazione complessa = replay value
- Factory builder appeal

**Contro**:
- Alta barriera d'ingresso
- Può sentirsi "freddo" e meccanico
- Meno accessibile a casual players
- Richiede più risorse grafiche (GUI complesse)

**Esempio Gameplay**:
> Player costruisce smeltery array 5x5x7 connesso a ore processing setup. Configura temperature zones per parallel melting di 4 materiali diversi. Usa fluid routers per alloying automatico. Monitora efficienza su dashboard. Ottimizza per throughput/tick.

### 6.2 DIREZIONE B: RPG-FORGE

**Filosofia**: La Foundry come sistema di crafting RPG. Focus su scoperta, mastery personale, e tools unici.

**Caratteristiche**:
- Player mastery levels per material
- Named/legendary tools con storia
- Discovery-based recipes (non libro)
- Tool personality (traits emergono con uso)
- Crafting mini-game skill-based
- NPC smiths per commissioni
- Lore integration profonda

**Pro**:
- Emotionally engaging
- Ogni tool è speciale
- Progression personale, non just gear
- Adatto a server multiplayer (economia)
- Narrative hooks

**Contro**:
- Meno sinergico con tech mods
- Automation più difficile
- Può frustrare min-maxers
- Richiede più content (lore, NPCs)

**Esempio Gameplay**:
> Player scopre che combinando copper e tin in proporzioni 85:15 sotto luna piena crea "Moonbronze" con trait unico. Dopo 1000 blocchi minati con il pickaxe, emerge il trait "Faithful" che aumenta fortune. Il tool viene nominato e diventa leggendario sul server.

### 6.3 SCELTA RACCOMANDATA: HYBRID-FORGE

**Raccomando un approccio ibrido** che prende il meglio di entrambe le direzioni.

**Motivazione**:

1. **DevMod context**: Guardando il codebase (NexusHub, energy systems, machines), DevMod ha già elementi tech. Ma non è Create o Mekanism - ha spazio per personality.

2. **Tinkers' gap**: TiC è già il reference per industrial tool crafting. Copiarlo al 100% non aggiunge valore.

3. **Market differentiation**: Un sistema che combina la profondità meccanica dell'industrial-sim con il reward emotivo dell'RPG-forge è unico.

**Hybrid-Forge Implementation**:

```
FOUNDRY HYBRID-FORGE
│
├── BASE LAYER (Industrial-Sim)
│   ├── Thermal management reale
│   ├── Purity/quality system oggettivo
│   ├── Multiblock scaling con math
│   └── Automazione possibile ma richiede setup
│
├── MIDDLE LAYER (Bridge)
│   ├── Material mastery per player
│   ├── Risk/reward per efficiency
│   ├── Tool data persistente e significativo
│   └── Tradeoff che forzano scelte
│
└── TOP LAYER (RPG-Forge)
    ├── Tool naming e legacy
    ├── Emergent traits con uso
    ├── Discovery recipes (non solo libro)
    └── Qualità influenza outcomes
```

**Esempio di come i layer interagiscono**:

> Player (nuovo) vuole fare un pickaxe:
> 1. [Industrial] Deve capire temperatura e purezza - c'è depth
> 2. [Bridge] Primi tentativi producono CRUDE quality - feedback chiaro
> 3. [RPG] Dopo 10 tools, sblocca "Apprentice Metalworker" title
> 4. [Industrial] Ora può vedere optimal temp ranges in GUI
> 5. [Bridge] Rischia HIGH risk per +25% output
> 6. [RPG] Successo! Tool nasce con bonus trait "Forged in Fire"
> 7. [Industrial] Può automatizzare processo base
> 8. [RPG] Ma legendary tools richiedono manual crafting

**Timeline Implementazione Hybrid**:

| Phase | Focus | Duration | Deliverables |
|-------|-------|----------|--------------|
| 1 | Industrial Base | 2 weeks | Thermal, Purity, Risk systems |
| 2 | Quality Loop | 1 week | Quality calculation, visual feedback |
| 3 | RPG Layer | 2 weeks | Mastery, naming, emergent traits |
| 4 | Content | 2 weeks | 20 materials, 20 traits, 15 modifiers |
| 5 | Polish | 1 week | GUI updates, guidebook, testing |

---

## APPENDIX A: Quick Reference Tables

### Material Tier Table (Proposed)

| Tier | Materials | Unlock | Max Temp |
|------|-----------|--------|----------|
| 0 | Wood, Bone, Flint | Start | N/A |
| 1 | Copper, Tin, Bronze | First smelt | 1200°C |
| 2 | Iron, Lead, Silver | Bronze tools | 1600°C |
| 3 | Steel, Electrum | Iron tools | 2000°C |
| 4 | Cobalt, Ardite, Manyullyn | Nether | 3000°C |
| 5 | Void Metal, Starsteel | End | 4000°C |

### Quality Thresholds (target)

| Quality | Purity | Temp Control | Pattern | Process |
|---------|--------|--------------|---------|---------|
| CRUDE | <60% | Any | Any | Any |
| STANDARD | 60-79% | ±200°C | Standard+ | Any |
| REFINED | 80-89% | ±100°C | Quality+ | No incidents |
| PRISTINE | 90-97% | ±50°C | Master+ | No incidents |
| MASTERWORK | 98%+ | ±20°C | Ancient | Perfect run |

Nota audit: le soglie reali sono in `QualityCalculator` (score-based), non in questa tabella.

### Risk Level Effects

| Level | Efficiency | Incident Chance | Trigger |
|-------|------------|-----------------|---------|
| SAFE | 1.0x | 0% | Default |
| ELEVATED | 1.1x | 5% | 1 factor |
| HIGH | 1.25x | 15% | 2 factors |
| CRITICAL | 1.5x | 35% | 3+ factors |

---

## APPENDIX B: Tinkers' Construct Feature Checklist

### Tools
- [x] Pickaxe (have)
- [x] Axe (have)
- [x] Shovel (have)
- [x] Sword (have)
- [x] Mattock
- [x] Kama
- [x] Excavator
- [x] Hammer
- [x] Cleaver
- [x] Scythe
- [x] Battleaxe
- [x] Crossbow
- [x] Longbow

### Parts
- [x] Head
- [x] Handle
- [x] Binding
- [x] Extra/Guard
- [x] Bowstring
- [x] Large Head
- [x] Tough Handle

### Smeltery Blocks
- [x] Controller
- [x] Bricks
- [x] Tank
- [x] Drain
- [x] Faucet
- [x] Casting Table
- [x] Casting Basin
- [x] Seared Glass
- [x] Seared Window
- [x] Fuel Gauge
- [x] Chute
- [x] Duct
- [x] Channel

### Systems
- [x] Melting
- [x] Alloying
- [x] Casting
- [x] Tool Leveling (addon in TiC; DevMod-only)
- [x] Modifier Slots
- [x] Embossment (diversa da TiC: "embossed" slot)
- [x] Tool Repair
- [x] Material Traits (partial)
- [x] Guidebook (basic)
- [x] JEI Integration

---

---

## 7. STATO IMPLEMENTAZIONE

### 7.1 Sistemi Implementati ✅

| Sistema | File | Stato | Note |
|---------|------|-------|------|
| **ThermalManager** | `thermal/ThermalManager.java` | ✅ COMPLETO | Heat tracking, stress accumulation, efficiency curves |
| **MaterialQuality** | `quality/MaterialQuality.java` | ✅ INTEGRATO | Multiplier applicati a stats tool/armor |
| **QualityCalculator** | `quality/QualityCalculator.java` | ✅ INTEGRATO | Usato per melting, casting e tool quality |
| **MoltenMetal** | `quality/MoltenMetal.java` | PARZIALE | Ossidazione/purezza tick; calculateQuality non usata |
| **RiskLevel** | `risk/RiskLevel.java` | ✅ COMPLETO | 4 livelli (SAFE→CRITICAL), efficiency bonuses |
| **IncidentType** | `risk/IncidentType.java` | ✅ COMPLETO | 6 tipi incidente con effetti |
| **RiskManager** | `risk/RiskManager.java` | ✅ COMPLETO | Risk evaluation, incident rolling |
| **PatternTier** | `tool/PatternTier.java` | ✅ COMPLETO | 5 tier pattern con durability/quality |
| **FoundryPatternItem** | `tool/FoundryPatternItem.java` | ✅ INTEGRATO | Durabilita/specializzazione + consumo in PartBuilder |
| **FoundryTier** | `progression/FoundryTier.java` | ✅ INTEGRATO | Gating tier su size/temp smeltery |
| **FoundryPlayerProgress** | `progression/FoundryPlayerProgress.java` | PARZIALE | Attachment registrata + sync; UI progressione in guidebook; unlock materiali/modificatori tracciati + gating su tool assembly/modifier |
| **Controller Integration** | `block/entity/FoundryControllerBlockEntity.java` | PARZIALE | Thermal+Risk ok; impurita ore/raw + ore-quality rich/poor + flux; quality/purezza applicate a output; cracking bricks su damage + repair |
| **Foundry Channels** | `block/entity/FoundryChannelBlockEntity.java` | ✅ INTEGRATO | Routing con valvole/filtri + duct pull + overlay fluidi |
| **Foundry Chute** | `block/entity/FoundryChuteBlockEntity.java` | ✅ INTEGRATO | Input verso controller + filtro item (shift+click) |
| **Foundry Tank Render** | `client/renderer/FoundryTankRenderer.java` | ✅ INTEGRATO | Overlay fluidi per tank/gauge/fuel |
| **GUI Widgets** | `client/screen/FoundryControllerScreen.java` | ✅ COMPLETO | Heat/Stress/Risk/Purity bars + tooltips |
| **Menu Data Sync** | `menu/FoundryControllerMenu.java` | ✅ COMPLETO | 15 data slots (include molten quality tier + alloy preview) |

### 7.2 Materiali (totale 92)

Audit: 92 JSON in `data/devmod/foundry/materials`. Stat keys: `head/handle/binding/plate/mail/trim` + traits.
92 definiscono `tier`; 20+ definiscono `melting` con `temperature/optimal_range/impurity_base`.
Nota: `melting.temperature` non e' usata per gating ricette; `optimal_range` e `impurity_base` alimentano purity/quality.
✅ PARITÀ+ raggiunta (TiC: 91 materiali).

### 7.3 Modifiers (totale 137)

Audit: 137 JSON in `data/devmod/foundry/modifiers`. Slot types: 107 `trait`, 16 `upgrade`, 5 `ability`, 10 `defense`, 2 `slotless`.
Nota: `slot_type` e' la fonte di verita per il gating; trait/slotless non consumano slot.

### 7.4 Traduzioni Aggiunte

- Qualità materiale (CRUDE→MASTERWORK)
- Pattern tier (BASIC→ANCIENT)
- Risk levels (SAFE→CRITICAL)
- 6 tipi incidente + descrizioni
- Tier progressione (PRIMITIVE→COSMIC)
- GUI tooltips (structure_heat, thermal_stress, risk_level, purity, etc.)
- Errori tier (temp/size) + messaggi repair smeltery

### 7.5 Sistemi Implementati (Verificati)

| Sistema | File | Stato | Descrizione |
|---------|------|-------|-------------|
| **Tool Repair** | `FoundryToolRepair.java` + `FoundryToolAnvilBlockEntity.java:133-144` | ✅ PRESENTE | Riparazione con materiali, penalty per repair count |
| **Smeltery Repair** | `FoundryControllerBlock.java` + `FoundryControllerBlockEntity.java` | ✅ PRESENTE | Riparazione in-world via foundry bricks (offline) + sostituzione cracked bricks |
| **Modifier Slots** | `FoundryToolSlots.java` + `FoundryToolAnvilBlockEntity.java:95-100` | ✅ COMPLETO | Sistema upgrade/ability/defense/slotless con limiti |
| **Tool Leveling** | `FoundryToolLeveling.java` + `FoundryToolingEvents.java` | ✅ PRESENTE | XP da mining/combat, bonus slots al level up |
| **Embossment** | `FoundryToolAnvilBlockEntity.java:111-131` | ✅ PRESENTE | Free trait da tool sacrificato |

### 7.6 Prossimi Passi (Priorità Rimanente)

| Feature | Priorità | Complessità | Descrizione |
|---------|----------|-------------|-------------|
| **Quality/Purity Inputs & UX** | 🔴 ALTA | Alta | Impurity recipe + ore/raw + flux tag + ore-quality rich/poor presenti (copertura limitata); feedback fluidi base in channels/tank + tint purezza + sfx + alert ok, resta estendere ore-quality |
| **Progression Depth** | 🟡 MEDIA | Media | Specialization via sigilli craftabili ok; pagina progressione nel guidebook + sync progress; gating modifier (spec+flux raffinato) e gating materiali tool; modifier esclusivi weapon/tool/alloyist + lega alloyist; respec item presente |
| **More Materials** | 🟡 MEDIA | Bassa | Espandere da 69 a 90+ materiali (parity TiC) |
| **More Tools** | 🟡 MEDIA | Media | Fishing rod/staff con tool definitions; shield custom (non ShieldItem); mancano utility avanzate |

### 7.7 Architettura Implementata

```
com.devmod.foundry/
├── thermal/
│   └── ThermalManager.java          ✅ NEW
├── quality/
│   ├── MaterialQuality.java         ✅ NEW
│   ├── QualityCalculator.java       ✅ NEW
│   ├── FoundryFluidQuality.java     ✅ NEW
│   ├── FoundryItemQuality.java      ✅ NEW
│   └── MoltenMetal.java             ✅ NEW
├── risk/
│   ├── RiskLevel.java               ✅ NEW
│   ├── IncidentType.java            ✅ NEW
│   └── RiskManager.java             ✅ NEW
├── progression/
│   ├── FoundryTier.java             ✅ NEW
│   ├── FoundryPlayerProgress.java   ✅ NEW
│   └── FoundryProgressAttachment.java ✅ NEW
├── tool/
│   ├── PatternTier.java             ✅ NEW
│   ├── FoundryPatternItem.java      ✅ REFACTORED (durability+specialization)
│   ├── FoundryToolLeveling.java     ✅ NEW
│   └── FoundryToolSlots.java        ✅ NEW
├── block/entity/
│   └── FoundryControllerBlockEntity.java  ✅ REFACTORED (thermal+risk+purezza; quality applicata a output)
├── menu/
│   └── FoundryControllerMenu.java   ✅ REFACTORED (15 data slots + alloy preview)
└── client/screen/
    └── FoundryControllerScreen.java ✅ REFACTORED (new widgets)
```

### 7.8 Gameplay Impact

**Prima dell'implementazione**:
- Fusione deterministica 100%
- Pattern infiniti
- Nessun rischio, nessuna decisione
- GUI statica con solo progress/fuel

**Dopo l'implementazione**:
- Sistema termico con warmup struttura e stress
- Pattern con durabilita e specializzazione
- Sistema rischio con tradeoff efficienza/incidenti
- Qualita calcolata e applicata a molten/parti/tool
- GUI dinamica con 5 indicatori real-time
- Progressione player collegata via attachment (tier gating nei menu; unlock materiali enforced per tool assembly, part builder solo tier; gating modifier spec+flux raffinato)

---

## 8. REGISTRAZIONE MATERIALI (AUDIT)

### 8.1 Materiali (69)

Audit: 69 JSON in `data/devmod/foundry/materials`. Stat keys: `head/handle/binding/plate/mail/trim` + traits.
69 definiscono `tier`; 20 definiscono `melting` con `temperature/optimal_range/impurity_base`.
Nota: `melting.temperature` non e' usata per gating ricette; `optimal_range` e `impurity_base` alimentano purity/quality.

### 8.2 Assets (PARZIALE)

- Storage blocks: 12 block textures + models + blockstates (steel, bronze, cobalt, manyullyn, tin, lead,
  silver, nickel, electrum, invar, ardite, void_metal).
- Ingots: 12 custom ingot textures + models; iron/gold/copper sono vanilla.
- Molten fluids: 12/16 texture dedicate presenti e ora referenziate da `MoltenFluidType`; restano lava-tinted per iron/gold/copper/netherite.
- Smeltery block variants: duct/chute blockstates+models+loot tables + ricette per glass/window/tank/gauge/fuel.
- Tool parts: texture per 4 materiali (steel/bronze/cobalt/manyullyn) presenti, ma i modelli item usano texture generiche (no override).
- Model JSON aggiunti per `foundry_bow_limb`, `foundry_bowstring`, `foundry_crossbow_stock`, `foundry_shield_core`, `foundry_shield_plating` e relativi pattern (texture generiche).

### 8.3 Registrazione Java (PARZIALE)

- `FoundryFluids.java`: 16 molten fluids + bucket items + liquid blocks (iron, gold, copper, tin, bronze, steel, cobalt, manyullyn,
  lead, silver, nickel, electrum, invar, ardite, netherite, void_metal).
- `FoundryItems.java`: 12 ingots custom + 12 block items custom + 12 nuggets + cast + foundry_flux (standard/refined/pure) + sigilli specialization + reset.
- `FoundryTags.java` + data tag: `foundry_flux` item tag.
- `FoundryBlocks.java`: 12 storage blocks custom + foundry channel/faucet/etc + foundry_cracked_bricks.

### 8.4 Recipes (COMPLETO)

| Tipo | Count | Note |
|------|-------|------|
| Crafting (block<->ingot) | 24 | 12 materiali custom |
| Crafting (foundry_flux tiers) | 3 | flux (calcite+blaze -> 2), refined (2 flux + glowstone), pure (2 refined + echo_shard) |
| Crafting (specialization sigils + reset) | 4 | weaponsmith/toolsmith/alloyist/reset |
| Melting (ingots) | 16 | tutti i 16 fluidi (vanilla + custom) |
| Melting (nuggets) | 14 | tutti i nugget (vanilla + custom) |
| Melting (blocks) | 16 | tutti i blocchi storage (vanilla + custom) |
| Melting (raw ores) | 6 | tin, lead, silver, nickel, cobalt, ardite |
| Melting (ore blocks) | 6 | tin, lead, silver, nickel, cobalt, ardite |
| Casting Table | 16 | tutti i 16 fluidi -> ingots |
| Casting Basin | 16 | tutti i 16 fluidi -> blocks |
| Alloying | 6 | bronze, steel, electrum, invar, manyullyn, void_metal |
| Fuel | 1 | lava |

**Totale ricette Foundry (melting/casting/alloying/fuel): 97**  
**Totale recipe files in `data/devmod/recipe`: 249 (crafting top-level: 117)**

- Rimossi duplicati storici in `data/devmod/recipe/foundry` (stesso type/ingredient) per evitare ambiguità.
- Nota: ricette smelting/blasting raw ores duplicate (root + `smelting/`/`blasting/`).

### 8.5 Traduzioni (PARZIALE)

- UI foundry/quality/risk: presenti in `en_us.json` + `it_it.json`.
- Traits: ✅ COMPLETO - 64 traits tradotti (EN/IT: name + description complete).
- Materiali: copertura parziale rispetto ai 69 materiali.

---

## 9. PROSSIMI PASSI

| Feature | Priorita | Stato |
|---------|----------|-------|
| Material Registration | ALTA | ✅ COMPLETO (69 materiali con tier; 20 con melting data) |
| Traits System | ALTA | ✅ COMPLETO (64 trait IDs, 45 trait modifiers + 66 modifiers + traduzioni EN/IT) |
| Recipes (melting/casting/alloying) | ALTA | ✅ COMPLETO (96 ricette foundry + fuel) |
| Textures | MEDIA | PARZIALE (12/16 molten ora usate; 4 part sets senza override; bow/shield part+pattern models presenti con texture generiche) |
| More Tools | MEDIA | PARZIALE (ranged + shield + wrench aggiunti; bow/crossbow ok, shield custom non `ShieldItem`; fishing rod/staff con tool definitions) |
| Armor Variants | MEDIA | ✅ COMPLETO (traveler/plate/slime integrati via Tool Station + set bonus slime) |
| Guidebook | BASSA | PRESENTE (Foundry Guide) |
| JEI Integration | BASSA | PRESENTE (melting/alloying/casting/fuel) |

---

## 10. CHANGELOG

### 2026-01-19 - Defense/Slotless Modifier Slots + Materials Expansion

- **FoundryModifierSlot**: Aggiunti DEFENSE e SLOTLESS slot types con `consumesSlots()` helper
- **FoundryToolData**: Aggiunto `bonusDefense` field con persistenza NBT
- **FoundryToolDefinition**: Aggiunto `baseDefense` field per tool definitions
- **FoundryToolSlots**: Aggiornato SlotUsage record per tracciare defense slots
- **Armor Tool Definitions**: Aggiunti base_defense slots (helmet=1, chest=3, legs=2, boots=1)
- **Ore Quality Tags**:
  - Creato `foundry_ore_dense.json` per raw blocks (bonus yield)
  - Creato `foundry_ore_nether.json` per nether ores
  - Espanso `foundry_ore_rich.json` con vanilla gem/mineral ores
- **Common Tags**: Aggiunti tag `c:ores/*` e `c:raw_materials/*` per tin/lead/silver/nickel/cobalt/ardite
- **New Materials** (23 nuovi, totale 92):
  - Fantasy alloys: knightslime, pig_iron, queens_slime, soulsteel, signalum, lumium, enderium
  - Common metals: zinc, aluminum, platinum, constantan
  - Gems: ruby, sapphire, topaz, opal, peridot
  - Organics: nether_wart, shroomlight, honeycomb, moss, kelp, warped_fungus, crimson_fungus
- **New Modifiers** (12 nuovi, totale 78):
  - Defense modifiers: protection, blast_protection, fire_protection, projectile_protection, thorns, respiration, aqua_affinity, feather_falling, depth_strider, frost_walker
  - Slotless modifiers: cosmetic_dye, embellishment
- **Translations**: Aggiunte traduzioni EN/IT per materials e defense slots

### 2026-01-19 - Massive Trait Expansion

- **New Traits** (+59 nuovi, totale 107):
  - Combat traits: bouncy, sticky, momentum, sharp, swift, durable, efficient, reinforcing
  - Effect traits: necrotic, scorching, searing, blazing, frosty, venomous, withered
  - Utility traits: silky, binding, reaching, prospecting, telekinetic, growing
  - Status traits: bleeding, shulking, launching, conducting, explosive
  - Defense traits: fortified, temperate, weightless, cooling, absorbent
  - Combat enhancers: piercing, sweeping, smiting, bane, reaping, serrated, relentless
  - Special traits: lucky, unbreaking, splitting, crushing, crumbling, slippery
  - Survival traits: voracious, nourishing, undying, phantom, ethereal, overslime
  - Level-based: sharpening, experienced, established, maintained, tasty
  - Aquatic: aquadynamic, cultivated
- **Modifier Parity**: Raggiunta parità totale modifier count (137 = TiC)
- **Trait Coverage**: 107/137 trait IDs (78% TiC coverage)
- **Translations**: Aggiunte traduzioni EN/IT per tutti i nuovi traits

### 2026-01-22 - Tool Definitions + Armor Variants Integration

- **Fishing Rod/Staff**: Aggiunte definizioni tool JSON (3 parts) per `foundry_fishing_rod` e `foundry_staff`.
- **Armor Variants**: Definizioni tool JSON per traveler/plate/slime integrate in Tool Station.
- **Assets**: Aggiunti model JSON per parti/pattern bow/shield (texture generiche).

### 2026-01-19 - Wrench Tool Addition

- **Wrench Tool Kind**: Aggiunto WRENCH a FoundryToolKind (MAINHAND slot, utility tool)
- **Wrench Part Type**: Aggiunto WRENCH_HEAD a FoundryPartTypes (head stats, cost 2)
- **3D Model**: Creato modello JSON 3D stile Create mod con 8 elementi (manico, shaft, testa, ganasce)
- **Tool Definition**: Creata definizione JSON per foundry_wrench (wrench_head + tool_handle)
- **Item Registration**: Registrati pattern, parte e tool item per wrench
- **Custom Textures**: Create texture Python-generated per metal (brushed steel) e handle (wood grain)
- **Translations**: Aggiunte traduzioni EN/IT per wrench items
- **Build Fixes**: Corretti errori pre-esistenti:
  - FoundryTankRenderer setNormal signature (Matrix3f -> PoseStack.Pose)
  - FoundryChannelBlockEntity missing BlockEntity import

### 2026-01-19 - Maintenance Visuals Update

- **Cracked Bricks**: Aggiunto `foundry_cracked_bricks` (registrazione + blockstate/model + loot table).
- **Structure Damage**: `FoundryControllerBlockEntity` converte brick casuali in cracked e li ripara con bricks.
- **Structure Validation**: `FoundryStructureDetector` accetta anche cracked bricks per i muri.
- **UI/Creative**: Traduzioni EN/IT + item in creative tab.
- **Nota**: Texture cracked usa placeholder vanilla (`cracked_stone_bricks`) in attesa di asset dedicato.

### 2026-01-19 - Shield Addition

- **Shield Tool Kind**: Aggiunto SHIELD a FoundryToolKind (OFFHAND slot, shield actions)
- **Shield Part Types**: Aggiunti SHIELD_CORE (head stats), SHIELD_PLATING (plate stats)
- **Tool Definition**: Creata definizione JSON per foundry_shield (2 parti, armor + toughness + knockback resist)
- **Item Registration**: Registrati pattern, parti e tool item per scudo
- **Translations**: Aggiunte traduzioni EN/IT per shield items

### 2026-01-19 - Ranged Weapons Addition

- **Ranged Tool Kinds**: Aggiunti BOW, CROSSBOW, LONGBOW a FoundryToolKind
- **Ranged Part Types**: Aggiunti BOW_LIMB, BOWSTRING, CROSSBOW_STOCK a FoundryPartTypes
- **Tool Definitions**: Create definizioni JSON per foundry_bow, foundry_crossbow, foundry_longbow
- **Item Registration**: Registrati pattern, parti e tool items per armi a distanza
- **Translations**: Aggiunte traduzioni EN/IT per tutti i nuovi items
- **Nota Audit**: Bow/Longbow usano `FoundryBowItem` (BowItem vanilla), Crossbow usa `FoundryCrossbowItem` (CrossbowItem vanilla).
- **Bug Fixes**: Risolti errori pre-esistenti:
  - FoundryChuteBlockEntity registrazione mancante
  - FoundrySpecialization method names (id->getId, translationKey->getDisplayName)
  - FoundryModifierSlot.TRAIT mancante
  - FoundryGuideScreen Optional handling

### 2026-01-19 - Traits System Completion

- **Trait Translations**: 64 traits tradotti (EN/IT: name + description complete).
- **Design Tokens JEI**: Aggiunto DesignTokens.Foundry.Jei.TEXT per colori testo nelle categorie JEI
- **JEI Categories Fix**: Aggiornate 4 categorie JEI (Melting, Casting, Alloying, Fuel) per usare design tokens

### 2026-01-21 - Guidebook + JEI + Seared Variants

- **Guidebook**: Aggiunto item guida e schermata Foundry Guide (5 pagine base)
- **JEI Integration**: Categorie per melting/alloying/casting/fuel + catalyst blocks
- **Seared Variants**: Aggiunti glass/window/gauge/fuel tank/duct e validazione struttura

### 2026-01-19 - Recipe Expansion Update

- **Melting Recipes**: 58 ricette melting (ingots, nuggets, blocks, raw, ore per 16 fluidi)
- **Casting Table**: 16 ricette casting table (totale 16)
- **Casting Basin**: 16 ricette casting basin (totale 16)
- **Alloying**: 6 ricette alloying (bronze, steel, electrum, invar, manyullyn, void_metal)
- **Foundry Flux**: Ricette crafting per flux standard/refined/pure (calcite+blaze, +glowstone, +echo_shard)
- **Mining Tags**: Aggiunti tag mineable/pickaxe e needs_stone_tool/needs_iron_tool per ore blocks
- **Smelting/Blasting**: 12 smelting + 12 blasting per raw ores (duplicati root + cartelle `smelting/`/`blasting/`)
- **Loot Tables**: Aggiunte 23 loot tables mancanti:
  - Storage blocks (12): steel, bronze, cobalt, manyullyn, tin, lead, silver, nickel, electrum, invar, ardite, void_metal
  - Foundry blocks (11): foundry_bricks, foundry_controller, foundry_drain, foundry_tank, foundry_faucet, foundry_channel, foundry_casting_table, foundry_casting_basin, foundry_part_builder, foundry_tool_station, foundry_tool_anvil
- **Material Tiers**: Aggiunti tier a 22 materiali non-metallici:
  - Tier 0 (PRIMITIVE): wood, stone, bamboo, leather
  - Tier 1 (BASIC): slime, honey, redstone, quartz, lapis
  - Tier 2 (IRON_AGE): amethyst, prismarine, prismarine_crystal, glowstone, turtle
  - Tier 3 (ADVANCED): diamond, obsidian, emerald
  - Tier 4 (NETHER): ender, blaze
  - Tier 5 (COSMIC): nether_star, echo, chorus
- **Design Tokens**: Aggiunti DesignTokens.Foundry.Guide per colori del guidebook

---

*Report generato per DevMod Foundry Module Evolution*
*Versione: 4.1 - Tool Definitions Expansion*
*Data: 2026-01-22*
*Autore: Claude Agent*
