# Piano Infrastruttura - Gestione Completa Server Minecraft OVH

> **Last updated**: 2025-12-26
> **Status**: PLANNING (roadmap; non validato)

## Obiettivo e principi
Permettere a Claude di controllare totalmente il server Minecraft (filesystem, AMP, bot in-game) in modo sicuro, ripetibile e osservabile. Guidato da:
- **Sicurezza by design:** least privilege, segreti fuori repo, audit trail per AMP/SSH.
- **GitOps-lite:** configurazioni (AMP, server.properties, plugin) versionate e applicate con change review.
- **Affidabilita e DR:** backup 3-2-1 (locale + offsite), test di restore cadenzati, rollback rapidi.
- **Osservabilita:** metriche, log e alert minimi; SLO per uptime e TPS.
- **Automazione:** runbook eseguibili, lockfile per job cron, health-check post-azione.

---

## Metodologie e best practice adottate (ricerca e standard comuni)
- **Change Management/Release:** staging prima di prod (se possibile), finestre di manutenzione per upgrade AMP/MC, smoke test post deploy (join server, TPS, log error).
- **Backup 3-2-1:** backup giornaliero locale + settimanale offsite (S3/MinIO/OVH Object Storage), retention 7/30, checksum e restore mensile. Bloccare backup concorrenti con lockfile (`flock`).
- **Hardening AMP:** uso di HTTPS dietro reverse proxy (Caddy/Nginx con Let’s Encrypt), account dedicato con ruoli minimi, rate limit IP (fail2ban/modsecurity), timeout chiamate API.
- **SSH Security:** chiavi dedicate con passphrase, disabilitare root login, MFA/2FA se bastion, cambio porta opzionale, fail2ban.
- **Osservabilita:** esportare metriche AMP (status API) + eventuale exporter Prometheus, alert su TPS < 18 per 2m, disco <15%, backup fallito, login AMP falliti ripetuti.
- **CI/CD plugin/config:** pipeline che esegue lint/yaml check, pack plugin, copia su staging, smoke test (avvio + log puliti), poi prod.
- **Runbook Incident:** percorsi rapidi (es. stop/rollback plugin, restore ultimo backup, raccolta log e metriche).

---

## Architettura proposta
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CLAUDE CODE (CLI)                                    │
│                              │                                              │
│         ┌────────────────────┼────────────────────┐                         │
│         │                    │                    │                         │
│         ▼                    ▼                    ▼                         │
│  ┌─────────────┐    ┌─────────────────┐   ┌──────────────────┐             │
│  │ SSH/SFTP    │    │ AMP API         │   │ Minecraft        │             │
│  │ MCP Server  │    │ MCP Server      │   │ Remote MCP       │             │
│  │ (esistente) │    │ (da creare)     │   │ (esistente)      │             │
│  └──────┬──────┘    └────────┬────────┘   └────────┬─────────┘             │
│         │                    │                     │                        │
└─────────┼────────────────────┼─────────────────────┼────────────────────────┘
          │                    │                     │
          ▼                    ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SERVER OVH                                           │
│  ┌─────────────┐    ┌─────────────────┐   ┌──────────────────┐             │
│  │ Filesystem  │    │ AMP Panel       │   │ Minecraft Server │             │
│  │ /home/mc    │    │ :8080           │   │ :25565           │             │
│  │             │    │                 │   │                  │             │
│  │ - plugins/  │    │ - Start/Stop    │   │ - Bot in-game    │             │
│  │ - config/   │    │ - Console       │   │ - Costruzioni    │             │
│  │ - logs/     │    │ - Scheduler     │   │ - Chat           │             │
│  │ - backups/  │    │ - Updates       │   │ - Inventario     │             │
│  └─────────────┘    └─────────────────┘   └──────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Componenti MCP da installare

### 1) SSH/SFTP MCP Server (esistente)
**Package:** `sftp-ssh-mcp` o `@idletoaster/ssh-mcp-server`  
**Funzionalita:** gestione file, log, backup, comandi shell.  
**Configurazione (esempio):**
```bash
claude mcp add --transport stdio ssh -- npx -y sftp-ssh-mcp \
  --host=IP_SERVER --port=22 --user=USERNAME --password=PASSWORD
```
*Preferire chiave SSH dedicata con passphrase; aggiungere `--privateKey` e passphrase via env.*

### 2) AMP API MCP Server (da creare)
**Perche:** start/stop/restart, console, scheduler, backup, metriche, update via API AMP.  
**API da coprire:** `Core.LoginAsync`, `Core.GetStatusAsync`, `Core.SendConsoleMessageAsync`, `Core.Start/Stop/RestartAsync`, `Core.GetUpdatesAsync`, `Core.GetScheduledTasksAsync`, `Core.StartTaskAsync`.  
**Tool esposti:** `amp_login`, `amp_get_status` (CPU, RAM, TPS, players, uptime), `amp_send_command`, `amp_start/stop/restart_server`, `amp_get_console_output` (ultime N righe con filtro), `amp_get_updates`, `amp_scheduler_list/run`.  
**Hardening:** token in env, HTTPS se possibile, timeout e retry limitati, rate limit IP, audit dei comandi.

### 3) Minecraft Remote MCP (esistente)
**Package:** `mcp-minecraft-remote`  
**Funzionalita:** bot giocatore (movimento, build, mining, combat, chat, trading).  
**Requisito:** `online-mode=false` oppure account premium.

### 4) Plugin MCP Tools (opzionale)
**Package:** `mcsrv-mcp-server` (Bukkit/Spigot)  
**Uso:** log realtime, permessi granulari, bridge DiscordSRV. Installare solo se servono eventi server-side aggiuntivi.

---

## Processi operativi chiave (strutturati)
- **Deploy plugin/config (GitOps-lite):**
  1) Commit/PR su repo config (server.properties, plugin config, AMP scheduler).  
  2) Backup automatico pre-deploy.  
  3) Deploy su staging (se disponibile) → smoke test (avvio, log puliti, join).  
  4) Deploy prod in finestra concordata; rollback rapido a backup precedente.
- **Patch management:** calendario mensile per AMP/Java/Paper; checklist: backup, stop pulito, update, verifica log, riavvio, monitoraggio TPS.  
- **Backup & DR:** cron con lockfile; compressione + checksum; replica offsite settimanale; restore test mensile documentato.  
- **Access management:** account dedicati per AMP e SSH; rotazione credenziali; 2FA su AMP se disponibile; log accessi centralizzati.  
- **Incident response:** playbook minimo (stop plugin difettoso, restore ultimo backup, raccolta log AMP/Minecraft, verifica CPU/TPS, comunicazione agli utenti).

---

## Requisiti server e rete
- OS: Ubuntu/Debian con `systemd`, `apt`.  
- Pacchetti: Java per versione MC, `zip`, `screen`/`tmux` se usati, `ufw`/`iptables`, `fail2ban`.  
- Porte: 22 (SSH), 8080 (AMP), 25565 (MC), opzionale porta plugin; restringere ai soli IP di management.  
- TLS: preferire HTTPS per AMP con reverse proxy (Caddy/Nginx + Let’s Encrypt).  
- Storage: ≥ 3× dimensione mondo per consentire backup multipli; monitor spazio.  
- Time sync: `systemd-timesyncd` attivo per scheduler affidabili.

---

## Matrice capacita
| Funzionalita           | SSH/SFTP | AMP API | MC Remote | MC Plugin |
|------------------------|----------|---------|-----------|-----------|
| Modificare config      | ✅       | ❌      | ❌        | ❌        |
| Upload plugin          | ✅       | ❌      | ❌        | ❌        |
| Log file / console     | ✅ file  | ✅ live | ❌        | ✅        |
| Backup mondo           | ✅       | ✅      | ❌        | ❌        |
| Start/Stop/Restart     | ✅       | ✅      | ❌        | ❌        |
| CPU/RAM/TPS            | ❌       | ✅      | ❌        | ⚠️*      |
| Scheduler/Task         | ❌       | ✅      | ❌        | ❌        |
| Comandi admin (/ban…)  | ✅**     | ✅      | ❌        | ✅        |
| Bot: costruzione/chat  | ❌       | ❌      | ✅        | ❌        |
| Chat bridging          | ❌       | ✅      | ✅        | ✅        |
*Dipende dal plugin; **via RCON/screen o comando shell.

---

## Piano di implementazione
### Fase 0: Preparazione
1) Raccogliere dati (IP/porte, utenti, online-mode, preferenze TLS/backup).  
2) Creare utenti dedicati (SSH `mc`, AMP per Claude) e chiavi.  
3) Abilitare firewall di base e fail2ban.

### Fase 1: Setup base (ALTA)
1) Configurare SSH MCP e testare permessi su `/home/mc/server`.  
2) Mappare struttura, versione Java/MC, service di avvio.  
3) Creare cartella backup, job manuale e verifica checksum.

### Fase 2: MCP AMP (ALTA)
1) Scaffold `amp-mcp-server` (package.json, index.js, tools).  
2) Implementare login, status, start/stop/restart, console, updates, scheduler list/run.  
3) Timeout/error handling, audit minimo.  
4) Test end-to-end da Claude; validare HTTPS se configurato.

### Fase 3: Bot in-game (MEDIA)
1) Configurare `mcp-minecraft-remote`; definire account/whitelist.  
2) Test join, chat, movimento; limitare permessi.  
3) Documentare comandi frequenti (build, pathing, combat).

### Fase 4: Plugin opzionale (BASSA)
1) Installare `mcsrv-mcp-server` se servono eventi granulari.  
2) Aprire porta dedicata e token; aggiornare firewall.  
3) Validare log live e comandi con permessi limitati.

---

## Testing e validazione
- **Fase 1:** backup manuale con checksum; modifica file dummy e restore.  
- **Fase 2:** `amp_get_status` (CPU/RAM/TPS), `amp_start/stop`, `amp_send_command` e verifica in log; simulare timeout.  
- **Fase 3:** bot join, chat di prova, path verso coordinate note; verifica TPS impatto.  
- **Fase 4:** ricezione eventi log e comando con permesso limitato.  
- **Restore test mensile:** estrazione backup in `/home/mc/restore-test`, avvio isolato, verifica integrita.

---

## Telemetria/monitoraggio
- Metriche minime: CPU, RAM, TPS, player count, spazio disco, esito backup, tempi API AMP.  
- Alert: TPS < 18 per 2m, disco <15%, backup fallito, login AMP falliti ripetuti, start/stop non riusciti.  
- Integrazione: pull `amp_get_status`; opzionale exporter Prometheus o push verso grafana/loki.  
- Log: ruotare `latest.log` e `/var/log/amp/`; centralizzare se disponibile.

---

## Backup e restore (dettaglio)
- Percorso: `/home/mc/backups`.  
- Comando base:
```bash
flock /tmp/mc-backup.lock -c "zip -r /home/mc/backups/world-$(date +%F).zip /home/mc/server/world"
```
- Checksum (`sha256sum`) e log in `/home/mc/backups/backup.log`.  
- Offsite settimanale su storage esterno (S3/OVH Object Storage/MinIO).  
- Restore: scompattare in path separato, validare, poi sostituire mondo con downtime concordato.

---

## Sicurezza (riepilogo operativo)
- Segreti solo in env/secret store; mai in repo.  
- Utente `mc` senza sudo; account AMP dedicato con ruolo minimo.  
- SSH con chiavi, root login disabilitato, fail2ban; AMP con HTTPS e rate limit.  
- Audit: log accessi SSH, comandi AMP MCP, esito scheduler.  
- Rotazione credenziali periodica; revoca accesso bot se compromesso.

---

## Configurazione Claude (esempio finale)
```json
{
  "mcpServers": {
    "ssh-ovh": {
      "command": "npx",
      "args": ["-y", "sftp-ssh-mcp", "--host", "IP_SERVER", "--port", "22", "--user", "USERNAME"],
      "env": { "SSH_PASSWORD": "***" }
    },
    "amp-panel": {
      "command": "node",
      "args": ["/path/to/amp-mcp-server/index.js"],
      "env": {
        "AMP_URL": "https://IP:8080/",
        "AMP_USER": "***",
        "AMP_PASS": "***"
      }
    },
    "minecraft-bot": {
      "command": "npx",
      "args": ["-y", "mcp-minecraft-remote@latest"],
      "env": {
        "MC_HOST": "IP_SERVER",
        "MC_PORT": "25565",
        "MC_USERNAME": "ClaudeBot"
      }
    }
  }
}
```

---

## Esempi di comandi eseguibili
- **Gestione server:** restart, stato CPU/RAM/TPS, ban/kick via `amp_send_command`.  
- **Gestione file:** edit `server.properties`, upload plugin, grep errori log.  
- **Bot in-game:** goto coordinate, build muro 10x5, chat di benvenuto.

---

## Dati richiesti per partire
- IP/porta SSH, utente e chiave/password; path installazione Minecraft e utente di esecuzione.  
- URL/porta AMP, utente/password dedicati; preferenza TLS.  
- `online-mode` e account premium per bot se true.  
- Frequenza e retention backup (default 7/30) e storage offsite preferito.

---

## Rischi e mitigazioni
- **Credenziali esposte:** env + rotazione; niente commit; account dedicati.  
- **Downtime AMP MCP:** fallback via SSH/RCON; timeout e retry limitati; watchdog sul servizio MCP.  
- **Backup corrotti:** checksum, log esito, restore test programmato.  
- **Permessi bot eccessivi:** ruolo limitato, niente OP, comandi admin solo via AMP.  
- **Aggiornamenti falliti:** backup pre-update, rollback plan, staging se possibile.

---

## Prossimi passi
1) Confermare dati di accesso, preferenze TLS e storage offsite.  
2) Eseguire Fase 0-1: hardening base, SSH MCP, primo backup con checksum.  
3) Scaffold e test AMP MCP con tools base.  
4) Attivare bot in-game e (se serve) plugin opzionale con firewall/tokens.

---

## Note tecniche AMP MCP (scaffold)
```
amp-mcp-server/
├── package.json
├── index.js          # entry point MCP
├── amp-client.js     # wrapper @cubecoders/ampapi
├── tools/
│   ├── server.js     # start, stop, restart, status
│   ├── console.js    # send command, get output
│   ├── metrics.js    # CPU, RAM, TPS, players
│   └── scheduler.js  # task management
└── README.md
```
Stima sviluppo: ~2-4 ore per versione base funzionante.

---

*Piano creato da Claude - Ultimo aggiornamento: oggi*
