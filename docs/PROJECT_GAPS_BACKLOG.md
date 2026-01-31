# Project Gaps Backlog

> Ultimo aggiornamento: 2026-01-31

Backlog delle lacune principali individuate dopo la documentazione completa. Le priorita sono orientative.

## P0 - Funzionalita incomplete visibili

Nessuna lacuna P0 aperta al momento.

## Completed (2026-01-31)

1) Zone Editor completato
- Stato: offset spawn/portal coerenti con hub, UI aggiornata, stringhe placeholder rimosse.
- File: `src/main/java/com/devmod/client/zone/ZoneEditorScreen.java`, `src/main/resources/assets/devmod/lang/en_us.json`

2) Dialog Editor NPC completato
- Stato: registry dialoghi custom + persistenza JSON, comando `/npc dialog edit`, salvataggio server attivo.
- File: `src/main/java/com/devmod/npc/dialog/DialogRegistry.java`, `src/main/java/com/devmod/npc/network/NpcNetworkHandler.java`, `src/main/java/com/devmod/npc/command/NpcDialogCommand.java`

3) GeckoLib assets per macchine clone completati
- Stato: audit asset per tutte le macchine registrate (geo/anim/texture/blockstate).
- File: `src/main/java/com/devmod/clone/CloneBlocks.java`, `src/main/resources/assets/devmod/geo/block/`, `src/main/resources/assets/devmod/animations/block/`, `src/main/resources/assets/devmod/textures/block/clone/`

4) Placeholder models risolti
- Stato: modelli block/item per le macchine clone presenti senza placeholder espliciti.
- File: `src/main/resources/assets/devmod/models/block/`, `src/main/resources/assets/devmod/models/item/`

5) API dashboard telemetry documentata
- Stato: spec API completa con endpoint, parametri e auth token.
- File: `docs/TELEMETRY_DASHBOARD_API.md`, `docs/OPERATIONS.md`

6) API mailbox/admin panel documentata
- Stato: spec API completa con auth, endpoint, payload e note operative.
- File: `docs/MAILBOX_ADMIN_API.md`, `docs/OPERATIONS.md`

7) Config reference dettagliata
- Stato: tabella completa chiavi/default/enum per TOML runtime.
- File: `docs/CONFIGURATION.md`, `src/main/java/com/devmod/config/`, `src/main/java/com/devmod/portal/PortalConfig.java`

8) Network payload spec
- Stato: tabella completa ChannelId -> payload -> direction.
- File: `docs/NETWORK.md`, `src/main/java/com/devmod/network/ChannelId.java`

9) Test coverage report aggiornato
- Stato: JaCoCo report + coverageSummary generati, verifica soglie OK.
- File: `docs/TEST_COVERAGE_REPORT.md`, `build/reports/jacoco/test/html/index.html`

10) Runbook e release
- Stato: ripristinati runbook operativi e checklist release.
- File: `docs/runbook/arena-alerts.md`, `docs/runbook/VERIFY.md`, `docs/runbook/RELEASE_CHECKLIST.md`

11) Asset credits completi
- Stato: fonti esterne registrate e paths tracciati.
- File: `docs/ASSETS_CREDITS.md`

## P1 - Asset e contenuti incompleti

Nessuna lacuna P1 asset aperta al momento.

## P2 - Testing e runbook

Nessuna lacuna P2 aperta al momento.

## P3 - Qualita e governance

Nessuna lacuna P3 aperta al momento.
