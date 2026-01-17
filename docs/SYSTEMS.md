# Sistemi DevMod

> Ultimo aggiornamento: 2026-01-15

Questo documento descrive i principali sistemi a runtime e i moduli che compongono la mod.

## Arena Template System

- **Registry**: template caricati da config, validazione e snapshot (`src/main/java/com/devmod/arena/registry/`).
- **Policy**: selezione template e scoring (`src/main/java/com/devmod/arena/policy/`).
- **Builder**: build sync/async con controlli di budget e integrita (`src/main/java/com/devmod/arena/builder/`, `src/main/java/com/devmod/arena/validation/`).
- **Autosmoke**: test automatici per template (`src/main/java/com/devmod/arena/autosmoke/`).
- **Telemetry**: metriche e audit di build (`src/main/java/com/devmod/arena/telemetry/`).
- **Comandi**: `/arena ...` con permessi e audit (`src/main/java/com/devmod/arena/command/`, `src/main/java/com/devmod/arena/security/`).

## Endurance Quest

- **Sessioni**: gestione quest e party session (`src/main/java/com/devmod/endurance/`).
- **Wave loop**: spawn, progressione e checkpoint (`src/main/java/com/devmod/endurance/wave/`).
- **Perk/Reward/Shop**: progressione roguelike (`src/main/java/com/devmod/endurance/perk/`, reward, shop).
- **Challenges/Contracts**: contenuti opzionali e sync client.
- **Telemetry**: eventi endurance, record personali, statistiche.

## Combat + Collision

- **Body-part detection**: mapping head/body/arms/legs (`src/main/java/com/devmod/collision/bodypart/`).
- **OBB hitbox**: collision avanzata e debug (`src/main/java/com/devmod/collision/obb/`).
- **Damage pipeline**: breakdown e integrazione mod (`src/main/java/com/devmod/combat/`, `src/main/java/com/devmod/damage/`).
- **Impact HUD**: overlay con breakdown danni e history (`src/main/java/com/devmod/client/overlay/`).

## Clone System

- **Blocchi core**: telepad, imprinter, neurocell, neurolink, reformer, centrifuge.
- **Macchine Clone**: pulverizer e macchine Oritech-style (blockstate + GeckoLib).
- **Entity**: `player_clone` e logica companion.
- **UI/Network**: schermate e payload dedicati.
- **Workflow modelli**: GeckoLib (vedi `docs/CLONE_MODEL_WORKFLOW.md`).

## Portal & Transport

- **Custom Portal**: portal block + rune blocks + preview (`src/main/java/com/devmod/portal/`).
- **Transport unificato**: Warp Core, moduli, network/linked/party, countdown (`src/main/java/com/devmod/transport/`).

## Area Builder & Zone System

- **Area Builder**: editor in-world, preview e build task (`src/main/java/com/devmod/area/`).
- **Nexus Editor Central**: hub di gestione aree.
- **Zone Marker**: marker item, editor e sync (`src/main/java/com/devmod/zone/`).

## NPC & Dialog

- **NPC Item**: neurocell NPC per spawn/config.
- **Dialog**: node/option/action/condition, editor UI, registry custom (`config/devmod/dialogs/`).
- **Payload**: open/save dialog e config.

## Hologram

- **Projector**: block entity con scansione terreno.
- **Rendering**: VBO e meshing per preview 3D.

## Nexus Hub

- **Dimensione Nexus**: manager runtime e config (tick, rebuild, palette).
- **Decor blocks**: palette futuristica con slab.

## Mailbox + Notification

- **Mailbox**: messaggi, news, task tester, ticket.
- **API**: admin panel web (`src/main/java/com/devmod/mailbox/api/`).
- **Notifiche**: centro notifiche e preferenze.

## Telemetry

- **Pipeline**: eventi -> NDJSON + DuckDB.
- **Dashboard**: server locale con UI.
- **Export**: CSV/JSON per heatmap e report.

## UI, Tools e Debug

- **Menu radiale**: accesso rapido a strumenti e comandi.
- **Editor**: weapon/armor/recipe, settings unificati.
- **Debug**: overlay, entity scanner, pathing e profiling.
- **Testing**: hub QA + GameTest.

## Compat e Integration

- Moduli compat per GeckoLib, Pehkui, Better Combat e vari mod client/server (`src/main/java/com/devmod/compat/`, `src/main/java/com/devmod/integration/`).
