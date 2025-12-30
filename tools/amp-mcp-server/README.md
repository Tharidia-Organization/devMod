# AMP MCP Server

MCP Server per controllare CubeCoders AMP da Claude Code.

## Installazione

```bash
cd tools/amp-mcp-server
npm install
```

## Configurazione

Il server richiede le seguenti variabili d'ambiente:

| Variabile | Descrizione | Default |
|-----------|-------------|---------|
| `AMP_URL` | URL del pannello AMP | `http://51.68.35.33:8080` |
| `AMP_INSTANCE_ID` | ID dell'istanza da controllare | `4dacbb63-e7cc-4481-9cd1-13970cb57f8f` |
| `AMP_USERNAME` | Username AMP | `lordbanana89` |
| `AMP_PASSWORD` | Password AMP | **Richiesta** |

## Aggiungere a Claude Code

```bash
claude mcp add amp-panel -e AMP_PASSWORD="YOUR_PASSWORD" -- node /path/to/amp-mcp-server/index.js
```

## Tools Disponibili

| Tool | Descrizione |
|------|-------------|
| `amp_get_status` | Stato server (CPU, RAM, TPS, players) |
| `amp_start_server` | Avvia il server |
| `amp_stop_server` | Ferma il server |
| `amp_restart_server` | Riavvia il server |
| `amp_kill_server` | Forza chiusura (emergenza) |
| `amp_send_command` | Invia comando alla console |
| `amp_get_console` | Legge output console |
| `amp_list_instances` | Lista tutte le istanze AMP |

## Esempi d'uso in Claude

```
"Riavvia il server Minecraft"
"Qual è lo stato del server?"
"Invia il comando 'say Hello everyone!'"
"Mostrami le ultime 20 righe della console"
```

## API Reference

Il server usa le API REST di AMP:
- `POST /API/Core/Login` - Autenticazione
- `POST /API/ADSModule/Servers/{id}/API/Core/GetStatus` - Stato
- `POST /API/ADSModule/Servers/{id}/API/Core/Start|Stop|Restart` - Controllo
- `POST /API/ADSModule/Servers/{id}/API/Core/SendConsoleMessage` - Console
