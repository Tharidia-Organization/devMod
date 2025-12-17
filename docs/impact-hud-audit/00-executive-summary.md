# Impact HUD System - Executive Summary

## Overview

L'Impact HUD è un sistema di feedback visivo in tempo reale per il combattimento che mostra:
- Breakdown dettagliato del danno inflitto
- Body part colpita con moltiplicatore
- Integrazione con mod esterne (Pehkui, Better Combat)
- Effetti visivi 3D nel mondo di gioco

## Stato Attuale

| Aspetto | Stato | Note |
|---------|-------|------|
| Funzionalità Core | 🟡 Parziale | Funziona ma con errori di calcolo |
| UX/Usabilità | 🟡 Parziale | Posizione fissa, alcune stringhe hardcoded |
| Performance | 🟢 Buono | Cache implementata, distance culling |
| Manutenibilità | 🟡 Parziale | Codice duplicato tra 2D/3D |
| Internazionalizzazione | 🔴 Incompleto | Molte stringhe non tradotte |

## Problemi Critici Identificati

1. **Pehkui bonus calcolato sulla target invece che sull'attacker** - Il bonus dimensione è invertito
2. **Discrepanza danno calcolato vs reale** - L'HUD può mostrare valori fuorvianti
3. **Enchant mostrati anche quando non applicabili** - Confonde l'utente

## Architettura

```
┌─────────────────────────────────────────────────────────────────┐
│                        IMPACT HUD SYSTEM                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │ HUD 2D      │    │ HUD 3D      │    │ VFX 3D      │         │
│  │ (Overlay)   │    │ (World)     │    │ (Effects)   │         │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘         │
│         │                  │                  │                 │
│         └──────────────────┼──────────────────┘                 │
│                            │                                    │
│                    ┌───────┴───────┐                           │
│                    │  ImpactData   │ ◄── Container condiviso   │
│                    └───────┬───────┘                           │
│                            │                                    │
│         ┌──────────────────┼──────────────────┐                │
│         │                  │                  │                 │
│  ┌──────┴──────┐   ┌───────┴───────┐  ┌──────┴──────┐         │
│  │DamageHandler│   │DamageBreakdown│  │ActualDamage │         │
│  │ (Entry)     │   │ (Calc)        │  │Tracker(Real)│         │
│  └─────────────┘   └───────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

## File Coinvolti

| File | LOC | Complessità | Priorità Refactor |
|------|-----|-------------|-------------------|
| ImpactHudOverlay.java | 373 | Media | Alta |
| ImpactData.java | 412 | Alta | Media |
| DamageBreakdown.java | 163 | Media | Alta |
| Impact3DRenderer.java | 408 | Alta | Media |
| Impact3DPanel.java | 188 | Bassa | Bassa |
| Impact3DPanelManager.java | 193 | Media | Bassa |
| ImpactVFX.java | 570 | Alta | Bassa |
| DamageHandler.java | 713 | Molto Alta | Alta |
| HitHelper.java | 534 | Alta | Media |
| ActualDamageTracker.java | 58 | Bassa | Media |

**Totale:** ~3,612 LOC dedicati al sistema Impact HUD

## Raccomandazioni

### Fase 1: Fix Critici (Priorità Immediata)
- Correggere calcolo Pehkui bonus
- Unificare visualizzazione danno
- Nascondere enchant non applicabili

### Fase 2: Miglioramenti UX
- Aggiungere configurazione posizione HUD
- Completare internazionalizzazione
- Migliorare logica observation

### Fase 3: Refactoring Tecnico
- Estrarre logica rendering comune
- Implementare cache per stringhe formula
- Separare VFX da pannelli 3D

## Documenti Correlati

- [01-architecture.md](01-architecture.md) - Architettura dettagliata
- [02-damage-calculation.md](02-damage-calculation.md) - Logiche di calcolo
- [03-rendering-system.md](03-rendering-system.md) - Sistema di rendering
- [04-issues-and-bugs.md](04-issues-and-bugs.md) - Problemi identificati
- [05-upgrade-roadmap.md](05-upgrade-roadmap.md) - Piano di upgrade
