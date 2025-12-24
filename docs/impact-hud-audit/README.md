# Impact HUD System - Audit Documentation

> **Last Updated**: 2024-12-23
> **Status**: PARTIALLY OUTDATED - See notes below

## Introduzione

Questa cartella contiene l'audit del sistema Impact HUD, una delle feature principali della mod DevMod per Minecraft 1.21.x/NeoForge.

L'Impact HUD fornisce feedback visivo in tempo reale durante il combattimento, mostrando:
- Breakdown dettagliato del danno inflitto
- Body part colpita con moltiplicatore
- Integrazione con mod esterne (Pehkui, Better Combat)
- Effetti visivi 3D nel mondo di gioco
- Tracking del danno reale vs calcolato

## Indice Documenti

| Documento | Descrizione |
|-----------|-------------|
| [00-executive-summary.md](00-executive-summary.md) | Riepilogo esecutivo con stato attuale |
| [01-architecture.md](01-architecture.md) | Architettura dettagliata del sistema |
| [02-damage-calculation.md](02-damage-calculation.md) | Logiche di calcolo del danno |
| [03-rendering-system.md](03-rendering-system.md) | Sistema di rendering 2D e 3D |
| [04-issues-and-bugs.md](04-issues-and-bugs.md) | Bug e problemi identificati |
| [05-upgrade-roadmap.md](05-upgrade-roadmap.md) | Piano di upgrade proposto |
| [06-code-snippets.md](06-code-snippets.md) | Frammenti di codice di riferimento |

## File Sorgente Principali (AGGIORNATO 2024-12-23)

```
src/main/java/com/frenkvs/devmod/
├── DamageHandler.java              # Entry point, gestione eventi danno
├── HitHelper.java                  # Rilevamento body part
├── damage/
│   ├── DamageBreakdown.java        # Calcolo breakdown danno (MOVED from hud/)
│   └── DamageCalculator.java       # NEW: Calcolo centralizzato danno
└── hud/
    ├── ImpactHudOverlay.java       # Rendering HUD 2D
    ├── ImpactHudService.java       # NEW: Facade per Impact HUD
    ├── ImpactHudContentBuilder.java # NEW: Builder contenuti condiviso
    ├── ImpactData.java             # Container dati impatto
    ├── ImpactHistory.java          # NEW: Storico impatti
    ├── ImpactDpsTracker.java       # NEW: Tracking DPS
    ├── ImpactHudPresets.java       # NEW: Preset configurazioni
    ├── Impact3DRenderer.java       # Rendering pannelli 3D
    ├── Impact3DPanel.java          # Singolo pannello 3D
    ├── Impact3DPanelManager.java   # Gestione pannelli
    └── ImpactVFX.java              # Effetti visivi 3D
```

## Bug Status (AGGIORNATO 2024-12-23)

| ID | Problema | Status | Note |
|----|----------|--------|------|
| BUG-001 | Pehkui bonus calcolato su target | **FIXED** | DamageBreakdown.java:56 usa `attacker` |
| BUG-002 | True damage no-op | OPEN | Da verificare |
| BUG-003 | Discrepanza danno calcolato/reale | OPEN | Da verificare |
| BUG-004 | Enchant mostrati quando non applicabili | **FIXED** | Filtro su target type |
| BUG-005 | Observation lock timeout mancante | **FIXED** | MAX_OBSERVATION_TIME_MS = 30000 |
| BUG-010 | Formula string non cached | **FIXED** | Cache in constructor |

## Statistiche Sistema

| Metrica | Valore |
|---------|--------|
| Files coinvolti | 15+ |
| Complessità media | Alta |
| Test coverage | Da verificare |

## Data Audit

- **Data creazione originale:** 2025-12-17
- **Ultimo aggiornamento:** 2024-12-23
- **Versione mod:** DevMod (branch Banastaff)
- **Versione Minecraft:** 1.21.x
- **Mod loader:** NeoForge

## Note Importanti

Questo audit è stato parzialmente aggiornato il 2024-12-23. Alcuni documenti interni potrebbero ancora contenere riferimenti obsoleti. I path dei file e lo status dei bug in questo README sono aggiornati.
