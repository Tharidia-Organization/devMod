# Piano di sviluppo - Sistema Mailbox MMO

> Last updated: 2025-12-26
> Status: IN PROGRESS (roadmap operativo; aggiorna i checkbox dopo ogni merge)

## Come usare questo documento

- Legenda stato: [x] completato, [ ] da fare, [~] implementato ma da validare
- Ogni task completato deve avere: test eseguiti, note, data
- I task sono ordinati per priorita e dipendenze

## Obiettivo e scope

- Mailbox in-game: messaggi tra player e da sistema/admin con persistenza offline
- Centro news: annunci globali con categorie, schedulazione e scadenze
- Ruoli: Admin, Tester, Utente con UI e permessi separati
- Configurazione completa da Admin Panel
- Sicurezza: rate limit, permessi, audit, anti-abuso
- Scalabilita: operazioni batch, DB indicizzato, purge TTL

Fuori scope iniziale (post MVP):

- Notifiche push esterne (email/mobile)
- Localizzazione multilingua avanzata
- Ricerca full-text sui messaggi

## Stato attuale (baseline dal repo)

### Server/Core

- [x] `MailboxManager` con send/read/delete/claim, purge TTL, rate limit
- [x] `DuckDbMailboxRepository` con schema e indici base
- [x] `NewsManager` con publish/draft/schedule e read tracking
- [x] `TestTaskManager` persistente (DuckDB + audit)
- [x] `MailboxConfig` persistita su file (serverconfig)
- [~] `AdminAuditLog` solo in-memory (persistenza mancante)

### Network

- [x] Payload: `MailboxSyncPayload`, `MailboxSendPayload`, `MailboxActionPayload`
- [x] Payload: `MailboxNotifyPayload`, `NewsSyncPayload`, `NewsReadPayload`
- [x] Payload: `TaskSyncPayload`, `TaskActionPayload`
- [x] Handler: send/read/delete/claim/refresh, news read, task sync
- [~] Task sync inviato a tutti (gating tester mancante)

### Client

- [x] UI: `MailboxScreen`, `MailboxComposeScreen`, `NewsScreen`, `TesterTaskScreen`
- [x] Cache: `ClientMailboxCache`, `ClientNewsCache`, `ClientTaskCache`
- [x] Notifications: `NotificationService` + `UnifiedToastOverlay`

### Allegati

- [x] `MailAttachment` + `ItemAttachment` + `CurrencyAttachment`
- [x] NBT parsing e validazione base item/currency
- [~] Regole whitelist/blacklist in-memory (manca config admin)
- [x] Transaction log anti-exploit

### Admin/API

- [x] API CRUD: messages/news/tasks/users/config/stats/analytics
- [~] Auth JWT base (utenti in-memory, hash debole, default admin)
- [x] Avvio server API integrato al lifecycle via `MailboxConfig`
- [x] Admin panel web (Vite + dashboard, news, tasks, users, config)

## Gap rispetto ai requisiti

- Allegati P2P non authoritativi (nessun addebito inventario/valuta del mittente)
- Claim multi-allegato non atomico (retry puo duplicare parzialmente)
- Ruoli/permessi non persistenti (admin/tester/blocklist solo in RAM)
- Gating tester tasks assente (sync e azioni disponibili a tutti)
- Content filter non applicato al flusso di invio
- Auth API debole (default admin, hash base64) + CORS anyHost
- Subject length mismatch (config 256 vs DB/record 128)
- Mapping valuta incoerente (coins -> tokens) + policy valute non configurabile
- Maintenance mode non applicato (invii/API)
- Scheduling news/scadenze non esposte in admin UI/API
- Broadcast worker/queue non integrato (solo batch sync)
- Audit log admin non persistente

## Roadmap (macro-fasi)

### Fase 0 - Audit e allineamento

Obiettivo: validare baseline e definire criteri di successo.

- Verifica flussi base (send/read/delete/claim, news read, task update)
- Allineamento requisiti con owner (priorita, scope MVP)

Uscita: checklist base passata + scope MVP firmato

### Fase 1 - Core mailbox e persistenza

Obiettivo: stabilita e correttezza dati.

- Persistenza config (file o DB) + reload
- Idempotenza claim allegati (lock/flag transazionale)
- Policy delete (soft/hard) configurabile
- Migrazioni DB e versioning schema

Uscita: messaggi e allegati robusti, config salvata

### Fase 2 - UX client e flussi utente

Obiettivo: UI completa e usabile.

- Compose multi-line e validazioni UI
- UI allegati user-friendly (selezione item/currency)
- Bulk actions (claim all, delete read)
- Error handling client coerente

Uscita: esperienza utente completa e coerente

### Fase 3 - News center completo

Obiettivo: news gestibili da admin e visibili al player.

- CRUD news via API admin
- Scheduling e scadenze validate
- Filtri categorie + stato letto

Uscita: ciclo news completo end-to-end

### Fase 4 - Tester workflow

Obiettivo: task QA persistenti e tracciati.

- Persistenza task (DB)
- API admin per assegnazione e tracking
- UI tester con stato e note

Uscita: task QA end-to-end

### Fase 5 - Admin API e sicurezza

Obiettivo: backend admin stabile e sicuro.

- Implementazione endpoint + paginazione
- Auth JWT con rotazione/secret da config
- Audit log per azioni critiche

Uscita: admin API pronta per UI

### Fase 6 - Admin Panel web

Obiettivo: UI admin utilizzabile.

- Dashboard, messaggi, news, tasks, config, users
- Preview + conferma per broadcast

Uscita: admin operabile senza accesso al DB

### Fase 7 - Hardening e scalabilita

Obiettivo: gestione carico e sicurezza.

- Broadcast batch/queue
- Metriche e alert base
- Filtri anti-spam e moderazione

Uscita: pronto per load test

## Workflow standard per nuove funzionalita

1) Aggiornare modello dati + schema DB
2) Aggiornare repository e manager
3) Aggiornare payload/handler network
4) Aggiornare cache client
5) Aggiornare UI + testi i18n
6) Aggiungere test unit/integration
7) Aggiornare docs e checklist

## TODO list dettagliata

### 1) Core mailbox (P0)

- [x] Invio messaggi player -> player con rate limit
- [x] Lettura e delete con ownership check
- [x] Claim allegati con validazione base
- [~] Idempotenza claim (lock DB + in-flight, non atomico multi-allegato)
- [x] Policy delete configurabile (soft/hard + retention)
- [~] Broadcast verso lista utenti offline (batch/delay base; queue worker non integrato)
- [~] Maintenance mode: blocco invii P2P e API non essenziali
- [ ] Gestione outbox (opzionale)

### 2) Persistenza e config (P0)

- [x] DuckDB schema + indici base
- [x] Migrazioni DB/versioning schema
- [x] Persistenza `MailboxConfig` su file/DB
- [x] Job purge news scadute (`NewsPurgeJob`)
- [x] Backup/restore DB mailbox (`DuckDbMailboxRepository.createBackup/restoreBackup`)
- [x] Migrazione subject a 256 + allineamento payload/config/schema

### 3) Allegati ed economia (P0)

- [x] ItemAttachment + CurrencyAttachment
- [x] NBT parsing e validazione item (`ItemAttachment.claim()`)
- [~] Regole item/currency (whitelist/blacklist) non configurabili da admin
- [~] Deduzione inventario/valuta del mittente per allegati P2P (authoritative transfer, no NBT)
- [~] Transaction safety con economia/inventario (claim ok, send parziale)
- [x] Log transazioni anti-exploit (`AttachmentTransactionLog`)
- [ ] Correzione mapping valute (coins/gems) + allowlist configurabile

### 4) Network e permessi (P0)

- [x] Payloads e handler base
- [x] Ack/errore strutturati client->server (UI feedback)
- [~] Permessi/ruoli (in-memory; persistenza mancante)
- [x] Rate limit extra (per giorno / per destinatario)
- [x] Gating tester tasks (sync e azioni solo tester)
- [x] Applica ContentFilter a subject/body P2P

### 5) Client UI/UX (P1)

- [x] MailboxScreen
- [x] Compose screen (input JSON + helper)
- [x] NewsScreen
- [x] TesterTaskScreen
- [x] Compose multi-line + validazioni UX (\\n)
- [x] Attachment helper fields (item/currency shortcuts)
- [x] Attachment picker UI (item picker overlay)
- [x] Bulk actions: claim all, delete read
- [~] Stato errore UI coerente con esito server (ottimistico vs conferma)

### 6) News center (P1)

- [x] NewsManager + read tracking
- [x] Admin CRUD news via API
- [~] Scheduling + scadenze esposte in admin UI/API (publishAt, expiresAt)
- [x] Notifica news importanti (overlay)
- [~] Filtri categorie + stato letto (UI)

### 7) Tester tasks (P1)

- [x] UI task tester + cache
- [x] Persistenza task in DB
- [x] API admin per creare/assegnare task
- [x] Audit e storico modifiche task
- [ ] Ruolo tester persistito + gating UI/azioni

### 8) Admin API (P0)

- [x] Start/stop server API su lifecycle con `MailboxConfig`
- [~] Auth con secret persistente da config e role check admin (hash debole, user in-memory)
- [ ] Hardening auth (rimozione default admin, hash forte, rate limit login)
- [x] Endpoint messages (list/get/create/delete/broadcast)
- [x] Endpoint news (list/get/create/update/delete)
- [x] Endpoint users (list/get/inbox)
- [x] Endpoint tasks (list/get/create/update/delete)
- [x] Endpoint config (get/update)
- [x] Endpoint stats (counts, unread, backlog)
- [ ] Fix paginazione offset/limit su `/api/messages`
- [ ] CORS allowlist + controllo origin

### 9) Admin panel web (P1)

- [x] Login + token handling
- [x] Dashboard con metriche
- [x] Messaggi: ricerca, broadcast
- [~] News: editor + scheduling + preview (scheduling mancante)
- [x] Tasks: assegnazione e review
- [x] Config: form + validazioni
- [x] Users: lista e inbox

### 10) Sicurezza e moderazione (P1)

- [~] Content filter base (presente ma non applicato)
- [~] Audit log azioni admin (solo in-memory)
- [x] Moderazione: view inbox e delete forzato
- [x] Limitazioni spam (cooldown/limit per destinatario)
- [~] Protezioni anti-dup per allegati (lock DB, manca atomicita)

### 11) Scalabilita e performance (P2)

- [x] Batch sender per broadcast massivi
- [~] Queue/worker per invii globali (`BroadcastQueueWorker`) non integrato
- [ ] Indici aggiuntivi per analytics/top sender/receiver (se serve)
- [x] Cache news globali (`NewsManager` con TTL configurabile)
- [x] Monitor performance DB (`DbPerformanceMonitor` con suggerimenti indici)

### 12) Testing e QA (P0/P1)

- [x] Unit test modelli/payload base
- [x] Integration test repository DuckDB
- [x] E2E test: send/read/delete/claim
- [x] E2E test: news read/unread
- [x] E2E test: task assign/complete
- [x] Load test broadcast (simulato) (`BroadcastLoadTest`)

### 13) Documentazione e operativita (P1)

- [x] Aggiorna `docs/areas/mailbox/README.md` con link al piano
- [x] Runbook operazioni admin (broadcast, moderazione)
- [x] Runbook backup/restore DB mailbox
- [x] Changelog versioni schema DB

## Definition of Done per ruolo

### Utente (player)

- Inbox con stato letto/non letto e conteggio
- Lettura messaggi, delete, claim allegati
- Composizione messaggio con allegati validati
- Notifica realtime o refresh manuale

### Tester

- Vede task assegnati con stato e note
- Aggiorna stato e commenti
- Accesso a mailbox/news come utente

### Admin

- Invio messaggi globali e mirati
- CRUD news con scheduling
- Gestione utenti/ruoli e blocchi
- Configurazione parametri via panel
- Audit log per azioni critiche

## Test plan (minimo)

- Send player->player con e senza allegati
- Claim allegato singolo e multiplo
- Delete con allegato non claimato (blocco)
- News read/unread + filtro categoria
- Task tester: assign -> in progress -> complete
- Broadcast a batch con offline user

## Release checklist

- DB schema migrato e backup ok
- Config persistente e validata
- Admin API protetta e funzionante
- UI client e admin complete
- Test suite minima passata
- Documentazione aggiornata
