# Impact HUD System - Audit Documentation

## Introduzione

Questa cartella contiene l'audit completo del sistema Impact HUD, una delle feature principali della mod DevMod per Minecraft 1.21.x/NeoForge.

L'Impact HUD fornisce feedback visivo in tempo reale durante il combattimento, mostrando:
- Breakdown dettagliato del danno inflitto
- Body part colpita con moltiplicatore
- Integrazione con mod esterne (Pehkui, Better Combat)
- Effetti visivi 3D nel mondo di gioco
- Tracking del danno reale vs calcolato

## Indice Documenti

| Documento | Descrizione |
|-----------|-------------|
| [00-executive-summary.md](00-executive-summary.md) | Riepilogo esecutivo con stato attuale e raccomandazioni |
| [01-architecture.md](01-architecture.md) | Architettura dettagliata del sistema |
| [02-damage-calculation.md](02-damage-calculation.md) | Logiche di calcolo del danno |
| [03-rendering-system.md](03-rendering-system.md) | Sistema di rendering 2D e 3D |
| [04-issues-and-bugs.md](04-issues-and-bugs.md) | Bug e problemi identificati |
| [05-upgrade-roadmap.md](05-upgrade-roadmap.md) | Piano di upgrade proposto |
| [06-code-snippets.md](06-code-snippets.md) | Frammenti di codice di riferimento |

## Quick Links

### File Sorgente Principali

```
src/main/java/com/frenkvs/devmod/
├── DamageHandler.java          # Entry point, gestione eventi danno
├── HitHelper.java              # Rilevamento body part
├── ActualDamageTracker.java    # Tracking danno reale
└── hud/
    ├── ImpactHudOverlay.java   # Rendering HUD 2D
    ├── ImpactData.java         # Container dati impatto
    ├── DamageBreakdown.java    # Calcolo breakdown danno
    ├── Impact3DRenderer.java   # Rendering pannelli 3D
    ├── Impact3DPanel.java      # Singolo pannello 3D
    ├── Impact3DPanelManager.java # Gestione pannelli
    └── ImpactVFX.java          # Effetti visivi 3D
```

### Bug Critici da Risolvere

| ID | Problema | File |
|----|----------|------|
| BUG-001 | Pehkui bonus calcolato su target | DamageBreakdown.java:41 |
| BUG-002 | True damage no-op | DamageHandler.java:204 |
| BUG-003 | Discrepanza danno calcolato/reale | ImpactHudOverlay.java |

### Statistiche Sistema

| Metrica | Valore |
|---------|--------|
| Files coinvolti | 10 |
| Lines of Code totali | ~3,600 |
| Complessità media | Alta |
| Test coverage | Da verificare |

## Come Usare Questo Audit

### Per Sviluppatori

1. **Inizia da** [00-executive-summary.md](00-executive-summary.md) per una panoramica
2. **Leggi** [04-issues-and-bugs.md](04-issues-and-bugs.md) per capire cosa va sistemato
3. **Segui** [05-upgrade-roadmap.md](05-upgrade-roadmap.md) per il piano di lavoro
4. **Consulta** [06-code-snippets.md](06-code-snippets.md) per riferimenti rapidi

### Per Code Review

1. **Verifica** le formule in [02-damage-calculation.md](02-damage-calculation.md)
2. **Controlla** i flussi in [01-architecture.md](01-architecture.md)
3. **Valida** le correzioni proposte in [04-issues-and-bugs.md](04-issues-and-bugs.md)

### Per Testing

I punti critici da testare sono:
1. Pehkui bonus (attacker vs target)
2. Enchant bonus vs entità specifiche
3. Observation lock timeout
4. Body part detection accuracy

## Data Audit

- **Data creazione:** 2025-12-17
- **Versione mod:** DevMod (branch Banastaff)
- **Versione Minecraft:** 1.21.x
- **Mod loader:** NeoForge

## Note

Questo audit è stato creato per facilitare il refactoring e l'upgrade del sistema Impact HUD. Le raccomandazioni sono prioritizzate in base a:

1. **Impatto utente** - Bug che affettano gameplay
2. **Complessità fix** - Effort richiesto
3. **Rischio regressione** - Possibilità di introdurre nuovi bug

Per domande o chiarimenti, consultare i documenti dettagliati o il codice sorgente.
