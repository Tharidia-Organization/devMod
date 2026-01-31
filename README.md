# DevMod

DevMod e' una mod NeoForge per Minecraft 1.21.1 pensata come toolkit di sviluppo: combat testing, arena template, telemetry, editor in-game e sistemi di supporto (NPC, clone, portali, trasporto, aree, zone, hub Nexus).

## Cosa offre (high level)

- **Arena & Endurance**: generazione di arene da template, policy di selezione, autosmoke, quest endurance a wave con perk, reward, challenge e WIS.
- **Combat & Collision**: body-part detection, OBB hitbox, breakdown danni, HUD impatti e strumenti di debug visivo.
- **Tooling in-game**: menu radiale, editor per weapon/armor/recipe, pannelli UI e overlay di profiling.
- **Sistemi world**: Nexus hub, Area Builder, Zone Marker/Editor, portali custom e sistema di trasporto unificato (Warp Core).
- **Contenuti e moduli**: Clone system (telepad, neurocell, reformer, macchine), NPC con dialoghi, proiettori olografici.
- **Telemetry & Admin**: pipeline NDJSON + DuckDB, dashboard locale, admin panel web per mailbox/news/task/ticket.
- **Compat**: integrazioni soft con mod esterne (Pehkui, Better Combat, GeckoLib, ecc.).

## Requisiti

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.x (repo usa 21.1.216)

## Build e run

```bash
# Build
./gradlew build

# Avvia client di sviluppo
./gradlew runClient

# Avvia server
./gradlew runServer

# GameTest
./gradlew runGameTestServer

# Admin panel (Vite)
./gradlew startAdminPanel
```

## Documentazione

La documentazione completa e aggiornata e' in `docs/README.md` (ultimo aggiornamento: 2026-01-31).

## Meta

- Mod ID: `devmod`
- Versione: `0.1.0`
- Licenza: vedi `LICENSE`
