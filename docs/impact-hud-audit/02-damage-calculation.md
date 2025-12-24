# Impact HUD - Logiche di Calcolo Danno

## 1. Overview del Flusso di Calcolo

Il sistema calcola il danno in due momenti distinti:

1. **Pre-riduzione** (DamageHandler): Calcola danno teorico basato su arma + enchant + body part
2. **Post-riduzione** (ActualDamageTracker): Cattura danno reale dopo armor/effects

```
┌─────────────────────────────────────────────────────────────────┐
│                    DAMAGE CALCULATION FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  BASE WEAPON DAMAGE                                             │
│        │                                                         │
│        ▼                                                         │
│  ┌─────────────────┐                                            │
│  │ + baseDamageBonus │ ◄── Da WeaponStats                       │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ × bodyPartMult   │ ◄── HEAD/BODY/ARMS/LEGS multiplier        │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ × rangedSpeed    │ ◄── Solo per attacchi ranged              │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ + damageBonus %  │ ◄── Percentuale danno extra               │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ + armorPenBonus  │ ◄── Armor penetration                     │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ × damageVsType   │ ◄── vs Players/Undead/Arthropods          │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ × (1-armorReduc) │ ◄── Custom armor reduction (DevMod)       │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ × critDamage     │ ◄── Se crit ranged triggera               │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│       FINAL DAMAGE (event.setAmount)                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Formula Principale (DamageHandler)

### 2.1 Codice Sorgente
**File:** `DamageHandler.java:136-215`

```java
// 4. Calculate Final Damage
float originalDamage = event.getAmount();
if (rangedBaseOverride > 0) {
    originalDamage = rangedBaseOverride;
    event.setAmount(originalDamage);
}
float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;
if (rangedSpeedOverride > 0) {
    newDamage *= rangedSpeedOverride;
}

// Damage Bonus: applies additional damage bonus on single target
if (stats.damageBonus > 0) {
    newDamage += originalDamage * stats.damageBonus;
}

// Armor Penetration
float armorPenBonus = 0f;
float targetArmor = Math.max(0f, victim.getArmorValue() - stats.armorShred);
if (stats.armorPenetration > 0) {
    armorPenBonus = calculateArmorPenBonus(stats.armorPenetration, targetArmor, newDamage);
    newDamage += armorPenBonus;
}

// Custom Armor Reduction
float armorReduction = calculateCustomArmorReduction(playerVictim, event.getSource());
newDamage = newDamage * (1.0f - armorReduction);

// Damage type bonuses
newDamage *= (1f + stats.damageVsPlayers);  // o damageVsUndead, damageVsArthropods

// Ranged crit
if (isRanged && rangedCritChance > 0 && Math.random() < rangedCritChance) {
    newDamage *= rangedCritDamage;
}
```

### 2.2 Variabili Coinvolte

| Variabile | Sorgente | Descrizione |
|-----------|----------|-------------|
| `originalDamage` | `event.getAmount()` | Danno base dall'evento |
| `rangedBaseOverride` | `RangedWeaponModule` | Override per armi ranged |
| `stats.baseDamageBonus` | `WeaponConfigManager` | Bonus danno flat |
| `multiplier` | Body part | 0.5x - 2.0x tipicamente |
| `rangedSpeedOverride` | `RangedWeaponModule` | Moltiplicatore velocità proiettile |
| `stats.damageBonus` | `WeaponConfigManager` | Bonus % danno |
| `stats.armorPenetration` | `WeaponConfigManager` | % armor penetration |
| `stats.armorShred` | `WeaponConfigManager` | Riduzione armor flat |
| `armorReduction` | `ArmorConfigManager` | Riduzione danno custom |
| `stats.damageVsPlayers` | `WeaponConfigManager` | Bonus vs giocatori |
| `rangedCritDamage` | `RangedWeaponModule` | Moltiplicatore crit |

---

## 3. DamageBreakdown (Per HUD)

### 3.1 Formula
**File:** `damage/DamageBreakdown.java:50-54`

```java
// Final calculation: (base + enchants + pehkui) * bodyPartMult + armorPen
float enchantTotal = enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
float subtotal = baseWeaponDamage + enchantTotal + pehkuiSizeBonus;
this.finalDamage = (subtotal * bodyPartMultiplier) + armorPenetrationBonus;
```

### 3.2 Rappresentazione Matematica

```
finalDamage = (baseWeaponDamage + Σ(enchantBonuses) + pehkuiSizeBonus) × bodyPartMultiplier + armorPenBonus
```

### 3.3 Esempio Calcolo

```
Scenario:
- Spada diamante: 7.0 base damage
- Sharpness V: +3.0 bonus (1.0 + 4×0.5)
- Pehkui scale 1.5: +1.75 bonus (7.0 × 0.25 × 0.5)
- Headshot (1.5x multiplier)
- Armor pen: +2.0

Calcolo:
subtotal = 7.0 + 3.0 + 1.75 = 11.75
finalDamage = 11.75 × 1.5 + 2.0 = 19.625

Formula String: "(7.0+3.0+1.8) × 1.50 + 2.0 = 19.6"
```

---

## 4. Calcolo Enchant Bonus

### 4.1 Sharpness
**File:** `damage/DamageBreakdown.java:72-76`

```java
if (enchName.contains("sharpness")) {
    float bonus = 1.0f + (level - 1) * 0.5f;
    enchantBonuses.add(new EnchantBonus("Sharpness " + toRoman(level), level, bonus));
}
```

**Formula:** `bonus = 1.0 + (level - 1) × 0.5`

| Level | Bonus |
|-------|-------|
| I | 1.0 |
| II | 1.5 |
| III | 2.0 |
| IV | 2.5 |
| V | 3.0 |

### 4.2 Smite (vs Undead)
**File:** `damage/DamageBreakdown.java:78-83`

```java
else if (enchName.contains("smite")) {
    if (target.isInvertedHealAndHarm()) { // Undead check
        float bonus = level * 2.5f;
        enchantBonuses.add(new EnchantBonus("Smite " + toRoman(level), level, bonus));
    }
}
```

**Formula:** `bonus = level × 2.5` (solo vs undead)

**Condizione:** `target.isInvertedHealAndHarm()` = true per:
- Zombie (tutte le varianti)
- Skeleton (tutte le varianti)
- Phantom
- Drowned
- Wither
- Zoglin

| Level | Bonus |
|-------|-------|
| I | 2.5 |
| II | 5.0 |
| III | 7.5 |
| IV | 10.0 |
| V | 12.5 |

### 4.3 Bane of Arthropods
**File:** `damage/DamageBreakdown.java:85-91`

```java
else if (enchName.contains("bane_of_arthropods")) {
    if (isArthropod(target)) {
        float bonus = level * 2.5f;
        enchantBonuses.add(new EnchantBonus("Bane " + toRoman(level), level, bonus));
    }
}

private boolean isArthropod(LivingEntity entity) {
    String typeName = entity.getType().getDescriptionId().toLowerCase();
    return typeName.contains("spider") ||
           typeName.contains("silverfish") ||
           typeName.contains("endermite") ||
           typeName.contains("bee");
}
```

**Formula:** `bonus = level × 2.5` (solo vs arthropod)

**Entità Riconosciute:**
- Spider, Cave Spider
- Silverfish
- Endermite
- Bee

### 4.4 Fire Aspect
**File:** `damage/DamageBreakdown.java:93-96`

```java
else if (enchName.contains("fire_aspect")) {
    // Fire aspect non aggiunge danno diretto, ma lo notiamo
    enchantBonuses.add(new EnchantBonus("Fire Aspect " + toRoman(level), level, 0f));
}
```

**Nota:** Fire Aspect è tracciato ma con bonus 0 (il danno fuoco è applicato separatamente da Minecraft).

---

## 5. Pehkui Size Bonus

### 5.1 Calcolo
**File:** `damage/DamageBreakdown.java:40-48`

```java
Float scale = ModIntegrationManager.getPehkuiScale(attacker);  // ✅ FIXED (BUG-001)
if (scale != null && scale > 1.0f) {
    this.pehkuiScale = scale;
    this.pehkuiSizeBonus = baseDmg * 0.25f * (scale - 1.0f);
} else {
    this.pehkuiScale = 1.0f;
    this.pehkuiSizeBonus = 0f;
}
```

**Formula:** `bonus = baseDamage × 0.25 × (scale - 1.0)`

**Esempi:**

| Scale | Bonus (su base 10.0) |
|-------|----------------------|
| 1.0 | 0 |
| 1.2 | 0.5 |
| 1.5 | 1.25 |
| 2.0 | 2.5 |
| 3.0 | 5.0 |

### 5.2 Status: FIXED (BUG-001)

Il codice ora usa correttamente `attacker`:
```java
Float scale = ModIntegrationManager.getPehkuiScale(attacker);  // ✅ CORRETTO
```

**Comportamento Attuale:** Se TU sei un gigante, ottieni bonus danno (corretto).

---

## 6. Armor Penetration

### 6.1 Formule Disponibili
**File:** `DamageHandler.java:414-460`

#### SIMPLE (Default)
```java
float ignoredArmor = armorValue * armorPen;
return ignoredArmor * (float) multiplier;
```
**Formula:** `bonus = armorValue × armorPen × multiplier`

#### VANILLA_ACCURATE
```java
float effectiveArmor = Math.min(20f, Math.max(armorValue / 5f, armorValue - baseDamage / 2f));
float armorReduction = effectiveArmor / 25f;
float blockedDamage = baseDamage * armorReduction;
return blockedDamage * armorPen * (float) multiplier;
```
**Formula:** Usa la formula vanilla di Minecraft per calcolare il danno bloccato, poi applica la percentuale di penetrazione.

#### PERCENTAGE
```java
float effectiveArmor = Math.min(20f, armorValue);
float normalReduction = effectiveArmor / 25f;
float reducedReduction = normalReduction * (1f - armorPen);
float bonusDamage = baseDamage * (normalReduction - reducedReduction);
return bonusDamage * (float) multiplier;
```
**Formula:** Riduce l'efficacia dell'armatura di una percentuale.

#### FLAT_BONUS
```java
return armorPen * (float) flatBonus;
```
**Formula:** `bonus = armorPen × flatBonus` (true damage indipendente da armor)

### 6.2 Configurazione
**File:** `Config.java`

```java
ARMOR_PEN_FORMULA = BUILDER
    .comment("Formula for armor penetration calculation")
    .defineEnum("armorPenFormula", ArmorPenFormula.SIMPLE);

ARMOR_PEN_MULTIPLIER = BUILDER
    .defineInRange("armorPenMultiplier", 0.5, 0.0, 2.0);

ARMOR_PEN_FLAT_BONUS = BUILDER
    .defineInRange("armorPenFlatBonus", 2.0, 0.0, 10.0);
```

---

## 7. Body Part Multipliers

### 7.1 Valori Default (da Config)
**File:** `Config.java`

```java
HEAD_DAMAGE_MULTIPLIER = BUILDER
    .defineInRange("headDamageMultiplier", 1.5, 0.1, 10.0);

BODY_DAMAGE_MULTIPLIER = BUILDER
    .defineInRange("bodyDamageMultiplier", 1.0, 0.1, 10.0);

ARMS_DAMAGE_MULTIPLIER = BUILDER
    .defineInRange("armsDamageMultiplier", 0.8, 0.1, 10.0);

LEGS_DAMAGE_MULTIPLIER = BUILDER
    .defineInRange("legsDamageMultiplier", 0.7, 0.1, 10.0);
```

### 7.2 Override per Arma
**File:** `WeaponStats.java`

Ogni arma può avere multipliers custom:
```java
public float headMult = 1.5f;
public float bodyMult = 1.0f;
public float armsMult = 0.8f;
public float legsMult = 0.7f;
```

### 7.3 Applicazione
**File:** `DamageHandler.java:125-134`

```java
switch (part) {
    case HEAD -> { multiplier = stats.headMult; partKey = "devmod.bodypart.head"; color = 0xFF5555; }
    case BODY -> { multiplier = stats.bodyMult; partKey = "devmod.bodypart.body"; color = 0x55FF55; }
    case ARMS -> { multiplier = stats.armsMult; partKey = "devmod.bodypart.arms"; color = 0xFFAA00; }
    case LEGS -> { multiplier = stats.legsMult; partKey = "devmod.bodypart.legs"; color = 0x55FFFF; }
}
```

---

## 8. Damage Type Bonuses

### 8.1 vs Players
**File:** `DamageHandler.java:184-185`

```java
if (victim instanceof Player) {
    newDamage *= (1f + stats.damageVsPlayers);
}
```

### 8.2 vs Undead/Arthropods
**File:** `DamageHandler.java:186-197`

```java
else if (victim instanceof Mob) {
    if (victim.getType().is(EntityTypeTags.UNDEAD)) {
        newDamage *= (1f + stats.damageVsUndead);
    } else if (victim.getType().is(EntityTypeTags.ARTHROPOD)) {
        newDamage *= (1f + stats.damageVsArthropods);
    }
}
```

### 8.3 Fire/Magic Damage
**File:** `DamageHandler.java:198-208`

```java
if (stats.fireDamageBonus > 0) {
    newDamage *= (1f + stats.fireDamageBonus / 100f);
}
if (stats.magicDamageBonus > 0) {
    newDamage *= (1f + stats.magicDamageBonus / 100f);
}
if (stats.trueDamagePercent > 0) {
    float truePortion = newDamage * stats.trueDamagePercent;
    float normalPortion = newDamage * (1f - stats.trueDamagePercent);
    newDamage = truePortion + normalPortion;  // ⚠️ Questo è un no-op
}
```

**Nota Bug:** Il calcolo `trueDamagePercent` è un no-op perché `truePortion + normalPortion = newDamage`.

---

## 9. Custom Armor Reduction (DevMod)

### 9.1 Calcolo
**File:** `DamageHandler.java:302-328`

```java
private static float calculateCustomArmorReduction(Player player, DamageSource source) {
    float totalReduction = 0f;

    // Determine damage type flags
    boolean isFire = source.is(DamageTypeTags.IS_FIRE);
    boolean isExplosion = source.is(DamageTypeTags.IS_EXPLOSION);
    boolean isProjectile = source.is(DamageTypeTags.IS_PROJECTILE);
    boolean isMagic = source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    boolean isPhysical = !isFire && !isExplosion && !isMagic;

    // Sum reductions from all armor slots
    for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

        ItemStack armor = player.getItemBySlot(slot);
        ArmorStats stats = ArmorConfigManager.getStats(armor);

        totalReduction += stats.getReductionFor(isPhysical, isFire, isMagic, isExplosion, isProjectile);
    }

    // Cap at 80%
    return Math.min(totalReduction, 0.8f);
}
```

### 9.2 Cap
La riduzione massima è 80% per prevenire invincibilità.

---

## 10. Actual Damage Tracking

### 10.1 Cattura Post-Riduzione
**File:** `ActualDamageTracker.java:29-57`

```java
@SubscribeEvent(priority = EventPriority.LOWEST)
public static void onDamagePost(LivingDamageEvent.Post event) {
    float actualDamage = event.getNewDamage();  // Danno REALE

    float healthAfter = entity.getHealth();
    float healthBefore = healthAfter + actualDamage;

    ImpactData impact = ImpactData.get();
    if (impact != null && impact.getTarget().getId() == entityId) {
        impact.setActualDamage(healthBefore, healthAfter, actualDamage);
    }
}
```

### 10.2 Differenza Calcolato vs Reale
**File:** `ImpactData.java:397-404`

```java
public float getDamageReduction() {
    if (!hasActualDamage()) return 0;
    return breakdown.finalDamage - actualDamageDealt;
}
```

**Interpretazione:**
- `reduction > 0`: Armor/effects hanno ridotto il danno
- `reduction < 0`: Danno amplificato (weakness, vulnerability)
- `reduction ≈ 0`: Calcolo era accurato

---

## 11. Discrepanze Note

### 11.1 Cosa NON considera DamageBreakdown

| Fattore | Considerato | Note |
|---------|-------------|------|
| Base weapon damage | ✅ | |
| Enchant bonuses | ✅ | Solo Sharpness/Smite/Bane |
| Pehkui scale | ✅ | Ma su target sbagliato |
| Body part mult | ✅ | |
| Armor penetration | ✅ | |
| Target armor | ❌ | Non riduce il danno calcolato |
| Target toughness | ❌ | |
| Protection enchants | ❌ | |
| Resistance potion | ❌ | |
| Absorption hearts | ❌ | |
| Shield blocking | ❌ | |
| Damage immunity | ❌ | |

### 11.2 Conseguenze

L'HUD può mostrare "Calculated: 15.0" ma "Actual: 5.0" se il target ha:
- Armatura completa di diamante
- Protection IV su tutti i pezzi
- Pozione Resistance II

Questo è **by design** per mostrare quanto danno l'arma POTREBBE fare in condizioni ideali, ma può confondere l'utente.
