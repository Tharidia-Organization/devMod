# Sistema Mailbox MMO

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Sistema di mailbox in-game in stile MMO con centro notizie e informazioni utente.

Per la roadmap e checklist dettagliata, vedi [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md).

## Panoramica

Il sistema mailbox permette:
- **Messaggistica in-game**: Invio/ricezione messaggi tra giocatori e dal sistema
- **Centro Notizie**: Pannello per annunci ufficiali, patch notes, eventi
- **Allegati**: Supporto per item e valuta come allegati
- **Tre Ruoli**: Admin, Tester, Utente con interfacce dedicate

## Architettura

### Package Structure

```
com.devmod.mailbox/
├── MailboxManager.java              # Singleton server-side
├── MailboxMessage.java              # Record per messaggi
├── MailboxConfig.java               # Configurazioni sistema
├── MessageType.java                 # Enum tipi messaggio
├── admin/
│   ├── MailboxCommands.java         # Comandi admin mailbox/news
│   └── MailboxCommandEvents.java    # Hook registrazione comandi
├── news/
│   ├── NewsManager.java             # Gestione notizie
│   ├── NewsArticle.java             # Record articolo
│   ├── NewsCategory.java            # Enum categorie
│   └── NewsPurgeJob.java            # Job purge news scadute
├── task/
│   ├── TestTaskManager.java         # Gestione task tester
│   ├── TestTask.java                # Record task
│   └── TaskAuditEntry.java          # Audit trail modifiche
├── attachment/
│   ├── MailAttachment.java          # Interfaccia allegati
│   ├── ItemAttachment.java          # Allegato item (+ whitelist/blacklist)
│   ├── CurrencyAttachment.java      # Allegato valuta (+ validazione)
│   ├── CompositeAttachment.java     # Allegato multiplo
│   └── AttachmentTransactionLog.java # Log transazioni anti-exploit
├── moderation/
│   ├── ContentFilter.java           # Filtro contenuti (parole vietate)
│   ├── AdminAuditLog.java           # Audit log azioni admin
│   └── MailboxPermissions.java      # Sistema permessi mailbox
├── analytics/
│   └── MailboxAnalyticsEngine.java  # Analytics avanzate e anomaly detection
├── broadcast/
│   └── BroadcastQueueWorker.java    # Worker asincrono per broadcast massivi
├── scheduler/
│   └── MessageScheduler.java        # Scheduler per invii programmati
├── digest/
│   └── DigestManager.java           # Aggregatore notifiche intelligente
├── persistence/
│   ├── MailboxRepository.java       # Interfaccia persistenza
│   ├── DuckDbMailboxRepository.java # Implementazione DuckDB (+ backup/restore)
│   └── DbPerformanceMonitor.java    # Monitor performance query DB
├── network/
│   ├── MailboxNetworkHandler.java   # Handler network
│   └── payload/
│       ├── MailboxSyncPayload.java      # Sync completo inbox
│       ├── MailboxSendPayload.java      # Invio messaggio
│       ├── MailboxActionPayload.java    # Read/Delete/Claim
│       ├── MailboxNotifyPayload.java    # Notifica nuovo msg
│       ├── MailboxStatusPayload.java    # Esito azioni (toast/status)
│       ├── NewsSyncPayload.java         # Sync notizie
│       ├── NewsReadPayload.java         # Segna news letta
│       ├── TaskSyncPayload.java         # Sync task tester
│       └── TaskActionPayload.java       # Azioni su task
├── client/
│   ├── ClientMailboxCache.java          # Cache messaggi client
│   ├── ClientNewsCache.java             # Cache news client
│   ├── ClientTaskCache.java             # Cache task client
│   ├── screen/
│   │   ├── MailboxScreen.java           # UI inbox
│   │   ├── MailboxComposeScreen.java    # UI composizione
│   │   ├── NewsScreen.java              # UI notizie
│   │   └── TesterTaskScreen.java        # UI task tester
│   └── overlay/
│       ├── MailNotificationOverlay.java # Toast nuovi msg
│       └── NewsAlertOverlay.java        # Toast news importanti
└── api/
    ├── MailboxApiServer.java            # Server REST Javalin
    ├── AuthMiddleware.java              # Auth JWT middleware
    └── controllers/
        ├── MessageController.java       # CRUD messaggi
        ├── NewsController.java          # CRUD news
        ├── TaskController.java          # CRUD task
        ├── UserController.java          # Gestione utenti
        └── ConfigController.java        # Config + stats
```

### Network Channels

| Channel ID | Nome | Direzione | Descrizione |
|------------|------|-----------|-------------|
| 100 | MAILBOX_SYNC | S→C | Sync completo inbox |
| 101 | MAILBOX_SEND | C→S | Invio messaggio |
| 102 | MAILBOX_READ | C→S | Azioni (read/delete/claim/refresh) |
| 105 | MAILBOX_NOTIFY | S→C | Notifica nuovo messaggio |
| 106 | NEWS_SYNC | S→C | Sync notizie |
| 107 | NEWS_READ | C→S | Segna news come letta |
| 108 | TASK_SYNC | S→C | Sync task tester |
| 109 | TASK_ACTION | C→S | Azioni su task |
| 110 | MAILBOX_STATUS | S→C | Esito azioni mailbox |

### Database Schema (DuckDB)

```sql
-- Messaggi
CREATE TABLE mailbox_messages (
    id VARCHAR PRIMARY KEY,
    sender_uuid VARCHAR,
    sender_name VARCHAR(64),
    recipient_uuid VARCHAR NOT NULL,
    subject VARCHAR(128) NOT NULL,
    body TEXT,
    message_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP,
    expires_at TIMESTAMP,
    has_attachment BOOLEAN DEFAULT FALSE,
    attachment_claiming BOOLEAN DEFAULT FALSE,
    attachment_claimed BOOLEAN DEFAULT FALSE,
    attachment_data TEXT,
    deleted BOOLEAN DEFAULT FALSE
);

-- Notizie
CREATE TABLE news_articles (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    author_name VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    priority INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT TRUE
);

-- Stato lettura news
CREATE TABLE news_read_status (
    player_uuid VARCHAR NOT NULL,
    news_id VARCHAR NOT NULL,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, news_id)
);

-- Task tester
CREATE TABLE test_tasks (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    assigned_to VARCHAR NOT NULL,
    assigned_by_name VARCHAR(64),
    priority INTEGER DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    due_at BIGINT,
    completed_at BIGINT,
    notes TEXT
);

-- Audit trail task
CREATE TABLE task_audit (
    id VARCHAR PRIMARY KEY,
    task_id VARCHAR NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_uuid VARCHAR,
    actor_name VARCHAR(64),
    old_value TEXT,
    new_value TEXT,
    timestamp BIGINT NOT NULL
);
CREATE INDEX idx_task_audit_task ON task_audit(task_id);
CREATE INDEX idx_task_audit_timestamp ON task_audit(timestamp);
```

## Utilizzo

### Invio messaggio di sistema

```java
MailboxManager.INSTANCE.sendSystemMessage(
    playerUuid,
    "Benvenuto!",
    "Grazie per aver effettuato l'accesso.",
    null, // attachmentData
    Duration.ofDays(7) // expires in 7 days
);
```

### Invio messaggio admin con allegato

```java
MailboxManager.INSTANCE.sendAdminMessage(
    "AdminName",
    playerUuid,
    "Ricompensa Evento",
    "Ecco la tua ricompensa per l'evento!",
    "{\"type\":\"item\",\"id\":\"minecraft:diamond\",\"count\":5}"
);
```

### Pubblicare una notizia

```java
NewsManager.INSTANCE.publishNews(
    "Patch 1.5.0",
    "Novita della nuova patch...",
    NewsCategory.PATCH,
    "DevTeam"
);
```

## Configurazione

Parametri configurabili tramite `MailboxConfig`:

| Parametro | Default | Descrizione |
|-----------|---------|-------------|
| maxMessagesPerPlayer | 100 | Max messaggi per inbox |
| maxSubjectLength | 128 | Lunghezza max oggetto |
| maxBodyLength | 2000 | Lunghezza max corpo |
| defaultMessageTtl | 30 giorni | Scadenza messaggi |
| maxMessagesPerMinute | 10 | Rate limit invio |
| maxMessagesPerDay | 0 | Limite giornaliero (0 = disattivo) |
| maxMessagesPerRecipientPerDay | 0 | Limite giornaliero per destinatario (0 = disattivo) |
| sendCooldownSeconds | 5 | Cooldown tra invii |
| broadcastBatchSize | 500 | Destinatari per batch broadcast |
| broadcastBatchDelayMs | 0 | Ritardo tra batch broadcast (ms) |
| playerToPlayerEnabled | true | Messaggi tra giocatori |
| itemAttachmentsEnabled | true | Allegati item |
| currencyAttachmentsEnabled | true | Allegati valuta |
| messageRetentionDays | 30 giorni | Retention per soft-delete prima del purge |
| hardDeleteOnUserDelete | false | Elimina subito (hard delete) al delete utente |

Configurazione persistita in `serverconfig/devmod/mailbox_config.json` (backup `.bak`).

Per l'admin API, `apiSecretKey` viene generato automaticamente se mancante e salvato su file.

## Admin API

L'API REST admin e disponibile su `http://localhost:8090/api/` (porta configurabile).

### Autenticazione

Tutte le richieste richiedono header `Authorization: Bearer <token>`.
Il token si ottiene tramite login:

```bash
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<secret_from_config>"}'
```

### Endpoint Principali

| Endpoint | Metodo | Descrizione |
|----------|--------|-------------|
| `/api/messages` | GET | Lista messaggi (paginata) |
| `/api/messages/{id}` | GET | Dettaglio messaggio |
| `/api/messages/broadcast` | POST | Invio broadcast |
| `/api/messages/{id}` | DELETE | Elimina messaggio |
| `/api/news` | GET/POST | Lista/crea news |
| `/api/news/{id}` | GET/PUT/DELETE | CRUD singola news |
| `/api/tasks` | GET/POST | Lista/crea task |
| `/api/tasks/{id}` | GET/PUT/DELETE | CRUD singolo task |
| `/api/tasks/{id}/audit` | GET | Storico modifiche task |
| `/api/tasks/audit/recent` | GET | Audit recenti |
| `/api/users` | GET | Lista utenti noti |
| `/api/users/{uuid}/inbox` | GET | Inbox di un utente |
| `/api/config` | GET/PUT | Configurazione sistema |
| `/api/stats` | GET | Statistiche sistema |

---

## Runbook Operazioni Admin

### Broadcast Massivo

Per inviare un messaggio a tutti gli utenti:

```bash
curl -X POST http://localhost:8090/api/messages/broadcast \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Manutenzione Programmata",
    "body": "Il server sara offline domani dalle 10:00 alle 12:00.",
    "messageType": "SYSTEM"
  }'
```

Con allegato:
```bash
curl -X POST http://localhost:8090/api/messages/broadcast \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Ricompensa Evento",
    "body": "Grazie per aver partecipato!",
    "messageType": "REWARD",
    "attachmentData": "{\"type\":\"currency\",\"currency\":\"tokens\",\"amount\":100}"
  }'
```

Il broadcast viene eseguito in batch (default 500 utenti per batch) per evitare sovraccarichi.

### Moderazione Inbox

Visualizzare inbox di un utente specifico:
```bash
curl -X GET "http://localhost:8090/api/users/<uuid>/inbox" \
  -H "Authorization: Bearer $TOKEN"
```

Eliminare forzatamente un messaggio:
```bash
curl -X DELETE "http://localhost:8090/api/messages/<message-id>?force=true" \
  -H "Authorization: Bearer $TOKEN"
```

### Gestione News

Creare una news schedulata:
```bash
curl -X POST http://localhost:8090/api/news \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Nuovo Evento",
    "content": "Dettagli evento...",
    "category": "event",
    "publishedAt": "2025-01-15T10:00:00Z",
    "priority": 10
  }'
```

### Gestione Task Tester

Creare un task:
```bash
curl -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Testare nuova feature mailbox",
    "description": "Verificare invio/ricezione messaggi",
    "assignedTo": "<tester-uuid>",
    "priority": 3,
    "dueAtMillis": 1736899200000
  }'
```

Visualizzare storico modifiche:
```bash
curl -X GET "http://localhost:8090/api/tasks/<task-id>/audit" \
  -H "Authorization: Bearer $TOKEN"
```

### Backup Database

Il database DuckDB si trova in `serverconfig/devmod/mailbox.duckdb`.

Backup manuale:
```bash
cp serverconfig/devmod/mailbox.duckdb serverconfig/devmod/mailbox_backup_$(date +%Y%m%d).duckdb
```

Restore:
```bash
# 1. Fermare il server
# 2. Sostituire il file
cp serverconfig/devmod/mailbox_backup_YYYYMMDD.duckdb serverconfig/devmod/mailbox.duckdb
# 3. Riavviare il server
```

### Configurazione Runtime

Modificare configurazione:
```bash
curl -X PUT http://localhost:8090/api/config \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "maxMessagesPerPlayer": 200,
    "maxMessagesPerDay": 50,
    "playerToPlayerEnabled": false
  }'
```

Le modifiche sono persistite automaticamente in `serverconfig/devmod/mailbox_config.json`.

---

## Schema Versioning

| Versione | Modifiche |
|----------|-----------|
| 1 | Schema iniziale (messages, news, news_read_status, test_tasks) |
| 2 | Aggiunta colonne soft-delete e claim lock |
| 3 | Aggiunta tabella task_audit |

Le migrazioni sono gestite automaticamente da `DuckDbMailboxRepository.initialize()`.

---

## Sistemi Avanzati

### Analytics Engine

Il motore di analytics (`MailboxAnalyticsEngine`) fornisce metriche in tempo reale:

```java
// Ottenere metriche dashboard
var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
System.out.println("Messaggi totali: " + metrics.totalMessages());
System.out.println("Tasso lettura: " + metrics.readRate() + "%");
System.out.println("Ora di picco: " + metrics.peakHour());

// Volume orario ultimi 24h
List<HourlyVolume> hourly = MailboxAnalyticsEngine.INSTANCE.getHourlyVolume();

// Top sender/receiver
List<PlayerRanking> topSenders = MailboxAnalyticsEngine.INSTANCE.getTopSenders(10);

// Alert anomalie (spam detection)
List<AnomalyAlert> alerts = MailboxAnalyticsEngine.INSTANCE.getAnomalyAlerts(20);
```

Funzionalita:
- Volume messaggi per ora/giorno/mese
- Breakdown per tipo messaggio
- Tasso claim allegati
- Rilevamento anomalie (burst sending, rapid fire)
- Ranking player per attivita

### Message Scheduler

Lo scheduler (`MessageScheduler`) permette invii programmati:

```java
// Messaggio programmato per domani
UUID schedId = MessageScheduler.INSTANCE.scheduleMessage(
    playerUuid,
    "Promemoria",
    "Non dimenticare l'evento!",
    Instant.now().plus(Duration.ofDays(1)),
    "System",
    MessageType.SYSTEM,
    null
);

// Broadcast programmato
UUID batchId = MessageScheduler.INSTANCE.scheduleBroadcast(
    recipientList,
    "Manutenzione",
    "Server offline tra 1 ora",
    Instant.now().plus(Duration.ofHours(1)),
    "Admin",
    MessageType.ADMIN
);

// Template ricorrente (es. daily digest)
UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
    recipientList,
    "Daily Update",
    "Ecco le novita di oggi...",
    "System",
    MessageType.SYSTEM,
    RecurrencePattern.DAILY,
    Instant.now(),
    null // no end date
);

// Cancellare/pausare
MessageScheduler.INSTANCE.cancelMessage(schedId);
MessageScheduler.INSTANCE.pauseRecurringTemplate(templateId);
```

### Digest Manager

Il digest manager (`DigestManager`) aggrega notifiche per evitare spam:

```java
// Accodare una notifica (sara aggregata o inviata subito in base a priorita)
DigestManager.INSTANCE.queueNotification(
    playerUuid,
    NotificationType.NEW_MESSAGE,
    "Nuovo messaggio da Mario",
    "Riguardo: Evento weekend",
    5, // priority (1-10)
    Map.of("messageId", msgId.toString())
);

// Impostare preferenze player
DigestManager.INSTANCE.setPreferences(playerUuid, new DigestPreferences(
    true,                           // digest enabled
    Duration.ofHours(4),           // interval
    5,                              // batch threshold
    8,                              // immediate priority threshold
    LocalTime.of(22, 0),           // quiet hours start
    LocalTime.of(8, 0),            // quiet hours end
    ZoneId.of("Europe/Rome"),
    null
));

// Forzare invio digest
DigestManager.INSTANCE.flushDigest(playerUuid);
```

Funzionalita:
- Aggregazione intelligente per tipo
- Quiet hours (niente notifiche di notte)
- Soglia priorita per invio immediato
- Preferenze per-player
