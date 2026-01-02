# Quickstart DevMod

> Ultimo aggiornamento: 2025-12-30

Guida rapida per iniziare a sviluppare con DevMod.

---

## Prerequisiti

| Requisito | Versione |
|-----------|----------|
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.215 |
| Node.js | 18+ (per Admin Panel) |
| IDE | IntelliJ IDEA consigliato |

---

## Setup Ambiente

### 1. Clona il Repository

```bash
git clone <repository-url>
cd devMod
```

### 2. Setup Gradle

```bash
# Su macOS/Linux
./gradlew setup

# Su Windows
gradlew.bat setup
```

### 3. Importa in IDE

**IntelliJ IDEA:**
1. File → Open → Seleziona la cartella `devMod`
2. Importa come progetto Gradle
3. Attendi il sync delle dipendenze

**VS Code:**
1. Apri la cartella `devMod`
2. Installa estensione "Extension Pack for Java"
3. Attendi l'indicizzazione

---

## Comandi Gradle

| Comando | Descrizione |
|---------|-------------|
| `./gradlew runClient` | Avvia client Minecraft |
| `./gradlew runServer` | Avvia server dedicato |
| `./gradlew build` | Compila la mod |
| `./gradlew test --no-build-cache` | Esegui test |
| `./gradlew genIntellijRuns` | Genera run configurations |

### Avvio Rapido

```bash
# Avvia il client per sviluppo
./gradlew runClient
```

Il client si avvierà con la mod caricata. I file di configurazione saranno in `run/config/devmod/`.

---

## Struttura Progetto

```
devMod/
├── src/main/java/com/devmod/    # Codice Java
├── src/main/resources/          # Risorse
│   ├── assets/devmod/           # Asset client
│   ├── data/devmod/             # Data pack
│   └── dashboard/               # Dashboard web
├── admin-panel/                 # Admin Panel React
├── docs/                        # Documentazione
├── run/                         # Directory runtime
└── build.gradle                 # Config build
```

---

## Primo Avvio

### 1. Avvia il Client

```bash
./gradlew runClient
```

### 2. Crea un Mondo

1. Crea nuovo mondo in modalità Creativa
2. Abilita cheats

### 3. Testa la Mod

```
/devtest                  # Mostra comandi disponibili
/devmod dashboard open    # Apre dashboard nel browser
```

### 4. Apri Menu Radiale

Premi **G** per aprire il menu radiale (keybind default).

---

## Dashboard Telemetry

Il dashboard si avvia automaticamente con il server.

### Accesso

```
http://127.0.0.1:8642/dashboard
```

### Via Comando

```
/devmod dashboard open    # Apre nel browser
/devmod dashboard status  # Mostra stato
```

---

## Admin Panel

### Setup

```bash
cd admin-panel
npm install
```

### Avvio

```bash
npm run dev
```

L'admin panel sarà disponibile su `http://localhost:5173`.

---

## Comandi Utili

### Debug e Test

```
/devtest                      # Lista comandi test
/devtest qa                   # Apre Testing Hub
/devtest hud <tipo>           # Attiva HUD debug
/devdebug                     # Toggle debug features
```

### Arena

```
/arena list                   # Lista template
/arena build <template>       # Costruisci arena
/arena autosmoke              # Esegui autosmoke test
```

### Telemetry

```
/devmod telemetry status      # Stato telemetry
/devmod telemetry reload      # Ricarica config
/devmod telemetry export      # Esporta dati
```

### Mailbox

```
/mailbox send <player> <msg>  # Invia messaggio
/news list                    # Lista news
/news create <title>          # Crea news
```

---

## Keybind

| Tasto | Azione |
|-------|--------|
| G | Menu radiale |
| M | Mailbox |
| T | Task tester |

Altri keybind sono disponibili ma non assegnati di default. Vai in Opzioni → Controlli → DevMod per configurarli.

---

## Debug

### Log

I log della mod sono visibili nella console e in:
```
run/logs/latest.log
```

### Filtri Log Utili

```bash
# Cerca log DevMod
grep "devmod" run/logs/latest.log

# Cerca errori
grep "ERROR" run/logs/latest.log | grep -i devmod
```

### DuckDB

Per ispezionare il database:

```bash
# Installa DuckDB CLI
brew install duckdb  # macOS

# Apri database
duckdb run/config/devmod/telemetry.duckdb

# Query esempio
SELECT COUNT(*) FROM combat_hits;
```

---

## Troubleshooting

### La mod non si carica

1. Verifica Java 21: `java -version`
2. Rigenera run: `./gradlew genIntellijRuns`
3. Pulisci cache: `./gradlew clean`

### Dashboard non si apre

1. Verifica stato: `/devmod dashboard status`
2. Controlla porta 8642 non in uso
3. Apri manualmente: `http://127.0.0.1:8642/dashboard`

### Admin Panel non si connette

1. Verifica che il server Minecraft sia avviato
2. Controlla i log per errori MailboxApiServer
3. Verifica CORS configuration

### DuckDB errori

1. Verifica permessi su `run/config/devmod/`
2. Controlla spazio disco
3. Rimuovi file corrotto e riavvia

---

## File Configurazione

### devmod-common.toml

Config generale della mod:
```
run/config/devmod-common.toml
```

### telemetry_settings.json

Config telemetry:
```
run/config/devmod/telemetry_settings.json
```

### DuckDB

Database telemetry:
```
run/config/devmod/telemetry.duckdb
```

---

## Test

### Unit Test

```bash
./gradlew test --no-build-cache
```

### GameTest

I GameTest sono in `src/test/java/com/devmod/gametest/`.

```bash
./gradlew runGameTestServer
```

---

## Build Produzione

```bash
# Build JAR
./gradlew build

# Il JAR sarà in
build/libs/devmod-<version>.jar
```

---

## Risorse

- [Architettura](ARCHITECTURE.md)
- [Database Schema](DATABASE.md)
- [Pannelli Esterni](PANELS.md)
- [Sistemi](SYSTEMS.md)
- [NeoForge Docs](https://docs.neoforged.net/)
