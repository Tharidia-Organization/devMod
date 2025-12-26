# Impact HUD - Problemi e Bug Identificati

> **Last Updated**: 2024-12-23
> **Status**: AGGIORNATO - Bug risolti marcati come FIXED

## Classificazione Severità

| Livello | Descrizione |
|---------|-------------|
| CRITICO | Funzionalità rotta, comportamento errato |
| ALTO | Bug visibile, impatto significativo UX |
| MEDIO | Problema minore, workaround possibile |
| BASSO | Ottimizzazione, code quality |

---

## 1. Bug Risolti (FIXED)

### BUG-001: Pehkui Bonus Calcolato su Target invece di Attacker - **FIXED**

**File:** `damage/DamageBreakdown.java:56`

**Status:** **RISOLTO** - Il codice ora usa correttamente `attacker`:
```java
Float scale = ModIntegrationManager.getPehkuiScale(attacker);  // FIXED
```

---

### BUG-004: Enchant Mostrati Anche Quando Bonus = 0 - **FIXED**

**File:** `damage/DamageBreakdown.java:97-112`

**Status:** **RISOLTO** - Ora filtra per target type valido prima di aggiungere enchant bonus.

---

### BUG-005: Observation Lock Può Bloccare HUD Indefinitamente - **FIXED**

**File:** `hud/ImpactData.java:41-42`

**Status:** **RISOLTO** - Aggiunto timeout massimo:
```java
private static final long MAX_OBSERVATION_TIME_MS = 30000;  // 30 seconds max
```

---

### BUG-010: Formula String Ricalcolata Ogni Frame - **FIXED**

**File:** `damage/DamageBreakdown.java:29-30`

**Status:** **RISOLTO** - Formula string ora cached nel constructor.

---

### OPT-001: Duplicazione Codice Rendering 2D/3D - **ADDRESSED**

**Status:** **INDIRIZZATO** - Creato `ImpactHudContentBuilder.java` per centralizzare la generazione contenuti.

---

## 2. Bug Ancora Aperti

### BUG-002: True Damage Percent è un No-Op

**File:** `DamageHandler.java:204-208`

**Severità:** CRITICO

**Codice Attuale:**
```java
if (stats.trueDamagePercent > 0) {
    float truePortion = newDamage * stats.trueDamagePercent;
    float normalPortion = newDamage * (1f - stats.trueDamagePercent);
    newDamage = truePortion + normalPortion;  // = newDamage (no change)
}
```

**Analisi Matematica:**
```
truePortion = D × P
normalPortion = D × (1 - P)
newDamage = D × P + D × (1 - P) = D × 1 = D
```

**Impatto:**
- La statistica `trueDamagePercent` non ha alcun effetto
- Utenti che configurano questa stat non vedono risultati

---

### BUG-003: Discrepanza Danno Calcolato vs Reale Confonde

**File:** `hud/ImpactHudOverlay.java`

**Severità:** CRITICO

**Problema:**
L'HUD mostra due valori che possono differire drasticamente:
- "Calculated Dmg: 25.0" (teorico)
- "ACTUAL DAMAGE: 8.0" (reale)

**Causa:**
`DamageBreakdown` non considera: Armor reduction, Protection enchantments, Resistance effects, Absorption, Shield blocking.

---

### BUG-006: Hit Detection Imprecisa per Entità Alte

**File:** `HitHelper.java:392-438`

**Severità:** ALTO

**Problema:**
Per entità molto alte (Enderman, Iron Golem), la zona HEAD è solo 15% dell'altezza.

---

### BUG-007: Stringhe Hardcoded Non Traducibili

**File:** `hud/ImpactHudOverlay.java` e `hud/Impact3DRenderer.java`

**Severità:** MEDIO

**Problema:**
Molte stringhe non usano i18n.

---

### BUG-008: Posizione HUD Fissa

**File:** `hud/ImpactHudOverlay.java:94-96`

**Severità:** MEDIO

**Problema:**
Nessuna opzione per spostare l'HUD.

---

### BUG-009: Panel 3D Può Occludere Vista

**File:** `hud/Impact3DRenderer.java:30-31`

**Severità:** MEDIO

**Problema:**
L'offset fisso può posizionare il pannello dentro muri o fuori dalla vista.

---

## 3. Tabella Riepilogativa

| ID | Severità | Status | Descrizione |
|----|----------|--------|-------------|
| BUG-001 | CRITICO | **FIXED** | Pehkui bonus su target |
| BUG-002 | CRITICO | OPEN | True damage no-op |
| BUG-003 | CRITICO | OPEN | Discrepanza danno |
| BUG-004 | ALTO | **FIXED** | Enchant mostrati erroneamente |
| BUG-005 | ALTO | **FIXED** | Observation lock infinito |
| BUG-006 | ALTO | OPEN | Head zone troppo piccola |
| BUG-007 | MEDIO | OPEN | Stringhe hardcoded |
| BUG-008 | MEDIO | OPEN | Posizione fissa |
| BUG-009 | MEDIO | OPEN | Offset fisso |
| BUG-010 | MEDIO | **FIXED** | Formula non cached |
| OPT-001 | BASSO | **ADDRESSED** | Duplicazione codice |

---

## 4. Priorità di Intervento Rimanente

### Fase 1: Fix Critici Rimanenti
1. BUG-002: True damage implementation
2. BUG-003: Breakdown riduzione danno

### Fase 2: UX Improvements
3. BUG-006: Head zone detection
4. BUG-007: Internazionalizzazione
5. BUG-008: Posizione configurabile

### Fase 3: Polish
6. BUG-009: Dynamic panel offset
