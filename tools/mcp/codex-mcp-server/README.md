# Codex MCP Server

Server MCP stdio che espone Codex CLI come tool `codex.run`, così puoi chiamarlo dagli agent MCP.

## Requisiti
- Node.js >= 18
- Codex CLI disponibile nel `PATH` (oppure variabile `CODEX_MCP_CLI` che punta al binario).

## Setup
```bash
cd tools/mcp/codex-mcp-server
npm install
```

## Avvio
```bash
# dalla cartella del server
npm start
# oppure
node ./src/index.js
```

Variabili utili:
- `CODEX_MCP_CLI`: binario Codex CLI (default `codex`).
- `CODEX_MCP_TIMEOUT_MS`: timeout predefinito dei comandi (ms).
- Compat: se il client non invia `protocolVersion`/`clientInfo` in `initialize`, il server li autocompila (usa `LATEST_PROTOCOL_VERSION` e clientInfo fittizio) per non fallire il handshake.

## Tool esposto: `codex.run`
Input (JSON Schema):
- `args` (string): argomenti per la CLI in formato shell.
- `argv` (array<string>): argomenti passati senza shell (usa questo per evitare problemi di quoting).
- `cwd` (string, opzionale): working directory.
- `env` (object, opzionale): env extra da aggiungere (solo valori stringa).
- `timeoutMs` (number, opzionale): override del timeout predefinito.
Devi specificare `args` **oppure** `argv`.

Output: blocchi `text` con stdout, stderr e riepilogo `exitCode`/timeout. `isError` è true se exit code != 0 o timeout.

## Registrazione in un client MCP (esempio)
Snippet di manifest/config per un client MCP che usa stdio:
```json
{
  "mcpServers": {
    "codex": {
      "command": "node",
      "args": ["tools/mcp/codex-mcp-server/src/index.js"],
      "transport": "stdio"
    }
  }
}
```

Se preferisci usare il bin, puoi anche referenziare `codex-mcp-server` dopo un `npm install` in questa cartella.

## Esempio multi-sessione (più istanze dedicate)
File pronto: `tools/mcp/codex-mcp-server/mcp-multi-example.json`
```json
{
  "mcpServers": {
    "codex-build": {
      "command": "node",
      "args": ["tools/mcp/codex-mcp-server/src/index.js"],
      "transport": "stdio",
      "cwd": ".",
      "env": { "CODEX_MCP_TIMEOUT_MS": "600000" }
    },
    "codex-tests": {
      "command": "node",
      "args": ["tools/mcp/codex-mcp-server/src/index.js"],
      "transport": "stdio",
      "cwd": ".",
      "env": { "CODEX_MCP_TIMEOUT_MS": "300000" }
    },
    "codex-docs": {
      "command": "node",
      "args": ["tools/mcp/codex-mcp-server/src/index.js"],
      "transport": "stdio",
      "cwd": "docs"
    }
  }
}
```
Importa quel JSON nel tuo client MCP (o copiane il contenuto nella sua configurazione) per avere tre tool separati: uno per build, uno per test, uno per documentazione. Ogni istanza espone il tool `codex.run` e ha il proprio cwd/env/timeout.
