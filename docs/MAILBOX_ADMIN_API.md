# Mailbox Admin API

> Ultimo aggiornamento: 2026-01-15

Documento di riferimento per l'API HTTP del pannello admin Mailbox (gestione messaggi, news, task, ticket, analytics).

## Base URL e avvio

- Base URL default: `http://127.0.0.1:8765`
- Health check (no auth): `GET /health`
- Prefix API: `/api`
- Porta e CORS configurabili in `MailboxConfig` (`apiPort`, `apiAllowedOrigins`).
- Server attivo solo se `apiEnabled=true`; al primo avvio puo scaricare dipendenze Javalin.

## Autenticazione e sicurezza

- Login: `POST /api/auth/login`
  - Body JSON: `{ "username": "...", "password": "..." }`
  - Risposta: `{ "success": true|false, "token": "jwt", "expiresAt": <epoch_ms>, "error": "..." }`
  - Token valido 24h.
- Tutti gli endpoint `/api/*` richiedono header:
  - `Authorization: Bearer <token>`
- Solo utenti con ruolo `ADMIN` possono accedere.
- Store admin utenti: `config/devmod/mailbox_admin_users.json`.
  - Se mancante, viene creato un bootstrap admin (credenziali loggate nei log server).
- Rate limit:
  - Login: max 5 tentativi per ip+username in 10 min, blocco 10 min.
  - API: rate limit per IP per categoria endpoint, risposta `429`.

## Convenzioni

- Tutte le risposte sono JSON (tranne `204 No Content`).
- Timestamp in millisecondi epoch (UTC) con suffisso `Millis`.
- UUID come stringa.
- Error payload possibili:
  - `{ "error": "Bad Request", "message": "..." }`
  - `{ "status": "error", "message": "..." }`
- Liste standard:
  - `{ "items": [ ... ], "count": <int> }` (count = numero elementi restituiti).

## Attachment data (messaggi)

Campo `attachmentData` e' una stringa JSON serializzata. Supporta singolo attachment o array.

Tipi supportati:
- Item:
  - `{ "type": "item", "item": "minecraft:diamond", "count": 3, "nbt": "{...}" }`
- Currency:
  - `{ "type": "currency", "currency": "tokens", "amount": 250 }`
- Composite:
  - `{ "type": "composite", "items": [ {item...}, {currency...} ] }`
- Array (equivalente a composite flat):
  - `[ {item...}, {currency...} ]`

Nota: il valore e' una stringa, quindi le virgolette vanno escapate nel JSON esterno.

## Endpoint

### Health

- `GET /health`
  - Risposta: `{ "healthy": true, "status": "OK" }`

### Auth

- `POST /api/auth/login`
  - Body: `{ "username": "admin", "password": "..." }`
  - Risposta: `{ "success": true, "token": "...", "expiresAt": 1700000000000, "error": null }`

### Messaggi

- `GET /api/messages`
  - Query: `limit` (default 50, max 200), `offset` (default 0), `recipientUuid` (opzionale)
  - Nota: se `recipientUuid` e' presente, l'offset e' applicato in memoria.
  - Risposta: list di `MessageDto`.

- `GET /api/messages/{id}`
  - Risposta: `MessageDto`.

- `POST /api/messages`
  - Body:
    - `recipientUuid` (UUID, required)
    - `senderName` (default `Admin`)
    - `subject` (required)
    - `body` (opzionale)
    - `messageType` (default `ADMIN`) valori: `PLAYER`, `SYSTEM`, `ADMIN`, `REWARD`
    - `attachmentData` (stringa JSON opzionale)
    - `expiresAtMillis` (opzionale)
  - Risposta: `{ "id": "uuid", "message": "Message sent" }`
  - Nota: per `SYSTEM` `expiresAtMillis` viene convertito in TTL (solo se futuro).

- `POST /api/messages/broadcast`
  - Body:
    - `subject` (required)
    - `senderName`, `body`, `messageType`, `attachmentData`, `expiresAtMillis` (opzionali)
  - Risposta: `{ "recipientCount": n, "message": "...", "queued": true|false, "jobId": "uuid"|null }`
  - Se la broadcast queue e' attiva e supera la soglia, la risposta risulta `queued=true`.

- `DELETE /api/messages/{id}`
  - Risposta: `204 No Content` o errore.

`MessageDto`:
- `id`, `senderUuid`, `senderName`, `recipientUuid`, `subject`, `body`, `messageType`
- `createdAtMillis`, `readAtMillis`, `expiresAtMillis`
- `hasAttachment`, `attachmentClaimed`, `attachmentData`

### Broadcast Jobs

- `GET /api/broadcast/jobs/active`
- `GET /api/broadcast/jobs/recent` (query `limit`, default 50, max 200)
- `GET /api/broadcast/jobs/{id}`

`BroadcastJobDto`:
- `jobId`, `subject`, `totalRecipients`, `sentCount`, `failedCount`, `state`
- `startedAtMillis`, `completedAtMillis`, `errorMessage`

### News

- `GET /api/news`
  - Query: `activeOnly` (default true), `category` (opzionale)
- `GET /api/news/{id}`
- `POST /api/news`
  - Body:
    - `title` (required), `content` (required)
    - `category` (default `ANNOUNCEMENT`) valori: `PATCH`, `EVENT`, `ANNOUNCEMENT`, `MAINTENANCE`, `DEV_BLOG`, `COMMUNITY`
    - `authorName` (opzionale)
    - `publishNow` (bool)
    - `publishAtMillis` (epoch ms)
    - `expiresAtMillis` (epoch ms)
    - `priority` (int, default 0)
    - `active` (bool, default true)
- `PUT /api/news/{id}`
  - Body: stessi campi di `POST`, tutti opzionali
- `DELETE /api/news/{id}`

`NewsArticleDto`:
- `id`, `title`, `content`, `category`, `authorName`
- `publishedAtMillis`, `expiresAtMillis`, `priority`, `active`, `expired`

### Utenti

- `GET /api/users`
  - Elenco utenti noti + ruoli (admin/tester/blocked).
- `GET /api/users/{uuid}`
  - Dettaglio con conteggi inbox.
- `GET /api/users/{uuid}/inbox`
  - Query: `limit` (default 50, max 200), `includeRead` (default true)
- `PUT /api/users/{uuid}/access`
  - Body: `{ "admin": true|false, "tester": true|false, "blockedSender": true|false, "blockedReceiver": true|false }`
  - Risposta: `UserDetailsDto` aggiornato.

`UserDto`:
- `uuid`, `name` (attualmente null), `isAdmin`, `isTester`, `blockedSender`, `blockedReceiver`

`UserDetailsDto`:
- `uuid`, `name` (null), `totalMessages`, `unreadMessages`, `unclaimedAttachments`
- `isAdmin`, `isTester`, `blockedSender`, `blockedReceiver`

### Task tester

- `GET /api/tasks`
  - Query: `assignedTo` (UUID), `status` (pending|in_progress|completed)
  - Se `assignedTo` non e' presente: `limit` (default 100), `offset` (default 0)
- `GET /api/tasks/{id}`
- `GET /api/tasks/{id}/audit`
- `GET /api/tasks/audit/recent` (query `limit`, default 50, max 200)
- `POST /api/tasks`
  - Body: `title` (required), `assignedTo` (UUID, required)
  - Opzionali: `description`, `assignedByName`, `priority` (int), `dueAtMillis`
- `PUT /api/tasks/{id}`
  - Body opzionale: `title`, `description`, `priority`, `status`, `dueAtMillis`, `notes`
- `DELETE /api/tasks/{id}`

`TaskDto`:
- `id`, `title`, `description`, `assignedTo`, `assignedByName`, `priority`, `status`
- `createdAtMillis`, `dueAtMillis`, `completedAtMillis`, `notes`, `overdue`

`AuditEntryDto`:
- `id`, `taskId`, `action`, `actorUuid`, `actorName`, `oldValue`, `newValue`, `timestampMillis`

### Config e Stats

- `GET /api/config`
  - Risposta `ConfigDto` con tutti i campi runtime.
- `PUT /api/config`
  - Body: aggiornamento parziale (stessi campi di `ConfigDto`, tutti opzionali)
  - Risposta: `ConfigDto` aggiornato.

Campi principali `ConfigDto` (nomi esatti API):
- Abilitazioni: `enabled`, `playerToPlayerEnabled`, `broadcastQueueEnabled`, `deliveryDispatchEnabled`,
  `deliveryImmediateDispatchEnabled`, `deliveryRecallEnabled`, `itemAttachmentsEnabled`,
  `currencyAttachmentsEnabled`, `itemAttachmentWhitelistEnabled`, `maintenanceMode`, `useOpLevelForRoles`,
  `contentFilterEnabled`, `hardDeleteOnUserDelete`
- Limiti messaggi: `maxMessagesPerPlayer`, `maxSubjectLength`, `maxBodyLength`,
  `defaultMessageTtlHours`, `maxMessagesPerMinute`, `maxMessagesPerDay`,
  `maxMessagesPerRecipientPerDay`, `sendCooldownSeconds`, `maxAttachmentsPerMessage`,
  `messageRetentionDays`, `minLevelToSend`
- Broadcast: `broadcastBatchSize`, `broadcastBatchDelayMs`, `broadcastQueueThreshold`
- Delivery: `deliveryDispatchIntervalSeconds`, `deliveryDispatchBatchSize`, `deliveryMaxAttempts`,
  `deliveryRetryDelaySeconds`, `deliveryRetryMaxDelaySeconds`, `deliveryRetryBackoffMultiplier`,
  `deliveryRetryJitterRatio`
- Attachment rules: `itemAttachmentWhitelist`, `itemAttachmentBlacklist`,
  `currencyAttachmentAllowed`, `currencyAttachmentMaxAmounts`
- Content filter: `contentFilterAction`, `contentFilterWords`, `contentFilterPatterns`

- `GET /api/stats`
  - Risposta `StatsDto`: tot messaggi, unread, users, news, task, memoria, timestamp.

`StatsDto`:
- `totalMessages`, `totalUnreadMessages`, `totalUsers`, `activeNewsArticles`
- `totalTasks`, `pendingTasks`, `inProgressTasks`, `completedTasks`
- `apiServerRunning`, `freeMemoryBytes`, `totalMemoryBytes`, `timestampMillis`

### Security metrics

- `GET /api/security/metrics`
- `POST /api/security/metrics/reset`

`SecurityMetricsDto`:
- Payload: `payloadsProcessed`, `sizeRejections`, `playerRateLimitRejections`,
  `ipRateLimitRejections`, `totalPayloadRejections`, `rejectionRate`
- IP limiter: `ipLimiterTotalRequests`, `ipLimiterRateLimited`, `ipLimiterBlocked`,
  `ipLimiterTrackedIps`, `ipLimiterBlockedIps`
- Packet validator: `packetValidatorRejections`, `packetValidatorPlayerRateLimits`,
  `packetValidatorIpRateLimits`, `timestampMillis`

### Analytics

- `GET /api/analytics/dashboard`
- `GET /api/analytics/hourly`
- `GET /api/analytics/type-breakdown`
- `GET /api/analytics/top-senders` (query `limit`, default 10, max 100)
- `GET /api/analytics/top-receivers` (query `limit`, default 10, max 100)
- `GET /api/analytics/anomalies` (query `limit`, default 20, max 100)
- `GET /api/analytics/system-status`
- `GET /api/analytics/db-recommendations`

`DashboardDto`:
- `totalMessages`, `totalRead`, `totalDeleted`, `readRate`
- `attachmentsSent`, `attachmentsClaimed`, `claimRate`
- `activePlayers`, `avgMessagesPerHour`, `avgMessagesPerDay`

`HourlyVolumeDto`: `hour`, `count`

`TypeBreakdownDto`: `type`, `count`

`PlayerRankingDto`: `playerUuid`, `count`

`AnomalyAlertDto`: `type`, `playerUuid`, `currentRate`, `normalRate`, `detectedAtMillis`

`SystemStatusDto` include:
- `broadcastQueue`: `queueSize`, `activeJobs`, `totalCompleted`, `totalFailed`
- `scheduler`: `pendingMessages`, `activeRecurringTemplates`, `totalDelivered`, `totalFailed`
- `digest`: `pendingNotifications`, `playersWithPending`, `digestsSent`, `immediateDeliveries`
- `delivery`: `totalAttempts`, `totalDelivered`, `totalFailed`, `totalRetried`,
  `totalRecalls`, `totalExpired`, `inFlight`, `running`
- `dbPerformance`: `totalQueries`, `avgQueryTimeMs`, `slowQueryCount`, `recentSlowQueries`

`IndexRecommendationDto`: `queryName`, `slowCount`, `suggestion`, `priority`

### Audit log admin

- `GET /api/audit`
  - Query: `limit` (default 100, max 200), `action` (opzionale), `actor` (nome o UUID)

Valori `action`:
- `BROADCAST`, `MESSAGE_SEND`, `MESSAGE_FLAG`, `MESSAGE_DELETE`, `MESSAGE_FORCE_DELETE`
- `NEWS_CREATE`, `NEWS_UPDATE`, `NEWS_DELETE`
- `TASK_CREATE`, `TASK_ASSIGN`, `TASK_UPDATE`, `TASK_DELETE`
- `CONFIG_CHANGE`, `USER_BLOCK`, `USER_UNBLOCK`, `USER_ACCESS_UPDATE`
- `LOGIN`, `LOGOUT`

`AuditDto`:
- `id`, `action`, `actorUuid`, `actorName`, `targetType`, `targetId`, `details`, `timestamp`

### Search

- `POST /api/search`
  - Body:
    - `playerUuid` (required)
    - `query` (opzionale)
    - `messageType` (`SYSTEM|ADMIN|PLAYER|REWARD`)
    - `readStatus` (`READ|UNREAD|ALL`)
    - `fromDate`, `toDate` (ISO-8601)
    - `senderName`
    - `sortBy` (`DATE_DESC|DATE_ASC|SENDER_ASC|SENDER_DESC|RELEVANCE`)
    - `page` (default 0)
    - `pageSize` (default 20)
- `GET /api/search/quick`
  - Query: `playerUuid` (required), `q` (required), `page`, `pageSize`
- `GET /api/search/suggestions`
  - Query: `playerUuid` (required), `prefix` (min 2 chars)
- `GET /api/search/stats`
- `POST /api/search/rebuild-index`

`SearchResultDto`:
- `hits`, `page`, `pageSize`, `totalHits`, `totalPages`, `hasMore`, `query`

`SearchHitDto`:
- `messageId`, `senderName`, `subject`, `body`, `messageType`, `sentAtMillis`,
  `readAtMillis`, `isRead`, `relevance`, `highlight`

`SearchStatsDto`:
- `totalIndexedMessages`, `uniquePlayers`, `indexReady`

### Tickets

- `GET /api/tickets`
  - Query: `status`, `category`, `priority`, `assignedTo`, `unassigned`, `page`, `pageSize`
- `GET /api/tickets/stats`
- `GET /api/tickets/{id}`
- `POST /api/tickets`
  - Body:
    - `reporterUuid` (required)
    - `reporterName` (opzionale)
    - `category` (required)
    - `subject` (required)
    - `description` (opzionale)
    - `reportedUuid`, `reportedName` (opzionali, per `ABUSE`)
- `PUT /api/tickets/{id}`
  - Body: `status`, `priority`, `resolutionNotes`
- `PUT /api/tickets/{id}/assign`
  - Body: `assigneeUuid` (required), `assigneeName` (required)
- `POST /api/tickets/{id}/comments`
  - Body: `content` (required), `authorName`, `authorUuid`, `isInternal`

Enum ticket:
- `status`: `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- `category`: `BUG`, `ABUSE`, `SUGGESTION`, `QUESTION`, `OTHER`
- `priority`: `LOW`, `NORMAL`, `HIGH`, `URGENT`

`TicketDto`:
- Identita: `id`, `reporterUuid`, `reporterName`, `reportedUuid`, `reportedName`
- Categoria/priorita: `category`, `categoryDisplay`, `priority`, `priorityDisplay`, `priorityColor`
- Stato: `status`, `statusDisplay`, `validTransitions`
- Contenuto: `subject`, `description`, `resolutionNotes`
- Assegnazione: `assignedTo`, `assignedToName`
- Timing: `createdAtMillis`, `updatedAtMillis`, `resolvedAtMillis`, `ageMillis`
- SLA: `responseSlaBreach`, `resolutionSlaBreach`, `timeUntilResponseSlaMillis`, `timeUntilResolutionSlaMillis`

`CommentDto`:
- `id`, `ticketId`, `authorUuid`, `authorName`, `content`, `isInternal`, `createdAtMillis`

`TicketListResponse`:
- `tickets`, `page`, `pageSize`, `totalItems`, `totalPages`

`TicketStatsResponse`:
- `total`, `open`, `assigned`, `inProgress`, `resolved`, `closed`, `avgResolutionTimeMs`

### Ticket auto-transition

- `GET /api/tickets/auto-transition/config`
- `PUT /api/tickets/auto-transition/config`
  - Body: `autoCloseEnabled`, `autoCloseDays`, `slaEscalationEnabled`, `autoAssignEnabled`
- `GET /api/tickets/auto-transition/metrics`

`AutoTransitionConfigDto`:
- `running`, `autoCloseEnabled`, `autoCloseDays`, `slaEscalationEnabled`, `autoAssignEnabled`

`AutoTransitionMetricsDto`:
- `autoClosedCount`, `escalatedCount`, `autoAssignedCount`, `totalRunCount`,
  `lastRunTimeMillis`, `running`

## Esempi rapidi

```bash
curl http://127.0.0.1:8765/health
```

```bash
curl -X POST http://127.0.0.1:8765/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"..."}'
```

```bash
TOKEN=... \
curl http://127.0.0.1:8765/api/messages?limit=10 \
  -H "Authorization: Bearer $TOKEN"
```

```bash
curl -X POST http://127.0.0.1:8765/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"recipientUuid":"00000000-0000-0000-0000-000000000000","subject":"Test","attachmentData":"{\"type\":\"currency\",\"currency\":\"tokens\",\"amount\":250}"}'
```
