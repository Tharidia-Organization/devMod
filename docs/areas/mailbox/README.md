# Sistema Mailbox MMO

Sistema di mailbox in-game in stile MMO con centro notizie e informazioni utente.

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
├── news/
│   ├── NewsManager.java             # Gestione notizie
│   ├── NewsArticle.java             # Record articolo
│   └── NewsCategory.java            # Enum categorie
├── persistence/
│   ├── MailboxRepository.java       # Interfaccia persistenza
│   └── DuckDbMailboxRepository.java # Implementazione DuckDB
├── network/
│   ├── MailboxNetworkHandler.java   # Handler network
│   └── payload/
│       ├── MailboxSyncPayload.java      # Sync completo inbox
│       ├── MailboxSendPayload.java      # Invio messaggio
│       ├── MailboxActionPayload.java    # Read/Delete/Claim
│       ├── MailboxNotifyPayload.java    # Notifica nuovo msg
│       ├── NewsSyncPayload.java         # Sync notizie
│       └── NewsReadPayload.java         # Segna news letta
├── client/ (TODO)
│   ├── ClientMailboxCache.java
│   ├── screen/
│   └── overlay/
└── api/ (TODO)
    └── REST API per admin panel
```

### Network Channels

| Channel ID | Nome | Direzione | Descrizione |
|------------|------|-----------|-------------|
| 100 | MAILBOX_SYNC | S→C | Sync completo inbox |
| 101 | MAILBOX_SEND | C→S | Invio messaggio |
| 102 | MAILBOX_READ | C→S | Azioni (read/delete/claim) |
| 105 | MAILBOX_NOTIFY | S→C | Notifica nuovo messaggio |
| 106 | NEWS_SYNC | S→C | Sync notizie |
| 107 | NEWS_READ | C→S | Segna news come letta |

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
| playerToPlayerEnabled | true | Messaggi tra giocatori |
| itemAttachmentsEnabled | true | Allegati item |
| currencyAttachmentsEnabled | true | Allegati valuta |

## TODO

- [ ] Client UI (MailboxScreen, ComposeScreen)
- [ ] Notification overlay (toast)
- [ ] NewsScreen
- [ ] Attachment system (ItemAttachment, CurrencyAttachment)
- [ ] REST API per admin panel
- [ ] Admin panel web (React)
- [ ] Sistema test tasks
