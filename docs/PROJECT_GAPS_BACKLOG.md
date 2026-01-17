# Project Gaps Backlog

> Ultimo aggiornamento: 2026-01-15

Backlog delle lacune principali individuate dopo la documentazione completa. Le priorita sono orientative.

## P0 - Funzionalita incomplete visibili

Nessuna lacuna P0 aperta al momento.

## Completed (2026-01-15)

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

## P1 - Asset e contenuti incompleti

Nessuna lacuna P1 asset aperta al momento.

## P1 - Documentazione tecnica mancante

7) Config reference dettagliata
- Evidenza: docs elenca file ma non i campi.
- Target: creare reference per `Config`, `GameMechanicsConfig`, `PortalConfig`, `EditorClientConfig` e JSON runtime.
- File: `src/main/java/com/devmod/config/`, `src/main/resources/config/devmod/`, `docs/CONFIGURATION.md`
- Done quando: tabella completa di chiavi e default.

8) Network payload spec
- Evidenza: doc range canali senza payload/limiti/validation per canale.
- Target: tabella payload con limiti e direction.
- File: `src/main/java/com/devmod/network/ChannelId.java`, `src/main/java/com/devmod/network/PayloadValidation.java`, `docs/NETWORK.md`
- Done quando: mapping completo canale -> payload -> limiti.

## P2 - Testing e runbook

9) Test coverage report aggiornato
- Evidenza: report testing storici archiviati.
- Target: nuovo report con coverage attuale, suite, comandi.
- File: `src/test/java/`, `docs/`
- Done quando: report aggiornato e linkato da `docs/README.md`.

10) Runbook e release
- Evidenza: checklist/runbook storici archiviati.
- Target: ripristinare runbook operativo e checklist release aggiornati.
- File: `docs/`
- Done quando: procedure attuali documentate e verificate.

## P3 - Qualita e governance

11) Asset credits completi
- Evidenza: crediti parziali (solo icone radial).
- Target: inventario completo delle fonti asset.
- File: `docs/ASSETS_CREDITS.md`, `src/main/resources/assets/devmod/`
- Done quando: tutte le fonti asset tracciate.
