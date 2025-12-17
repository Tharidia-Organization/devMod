#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawn } from "node:child_process";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ErrorCode,
  LATEST_PROTOCOL_VERSION,
  ListToolsRequestSchema,
  McpError,
} from "@modelcontextprotocol/sdk/types.js";

const CODEX_BIN = process.env.CODEX_MCP_CLI || "codex";
const DEFAULT_TIMEOUT_MS =
  Number.parseInt(process.env.CODEX_MCP_TIMEOUT_MS ?? "", 10) || 120000;

const server = new Server(
  { name: "codex-cli-mcp", version: "0.1.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "codex.run",
      description: "Esegui comandi Codex CLI via MCP (stdio).",
      inputSchema: {
        type: "object",
        properties: {
          args: {
            type: "string",
            description: "Argomenti Codex CLI in forma di stringa (shell).",
          },
          argv: {
            type: "array",
            description: "Argomenti Codex CLI in forma di array (senza shell).",
            items: { type: "string" },
          },
          cwd: {
            type: "string",
            description: "Working directory del comando (default: cwd server).",
          },
          timeoutMs: {
            type: "number",
            description: `Timeout in ms (default: ${DEFAULT_TIMEOUT_MS}).`,
          },
          env: {
            type: "object",
            description: "Variabili d'ambiente addizionali (stringhe).",
            additionalProperties: { type: "string" },
          },
        },
        anyOf: [{ required: ["args"] }, { required: ["argv"] }],
        additionalProperties: false,
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  if (name !== "codex.run") {
    throw new McpError(ErrorCode.MethodNotFound, `Tool non supportato: ${name}`);
  }

  const { args: argString, argv, cwd, timeoutMs, env } = args ?? {};
  const spawnConfig = resolveInvocation(argString, argv);
  const workingDirectory = resolveCwd(cwd);
  const timeout = normalizeTimeout(timeoutMs);
  const mergedEnv = mergeEnv(env);

  const { stdout, stderr, code, timedOut, signal } = await runCommand({
    ...spawnConfig,
    cwd: workingDirectory,
    timeout,
    env: mergedEnv,
  });

  const content = buildContent(stdout, stderr, code, signal, timedOut, timeout);
  return {
    content,
    isError: timedOut || (typeof code === "number" && code !== 0),
  };
});

async function main() {
  const transport = new StdioServerTransport();

  // Compat: se il client non manda i campi obbligatori in initialize, applichiamo default.
  transport.onmessage = (message) => {
    if (message?.method !== "initialize") {
      return;
    }
    const params =
      message.params && typeof message.params === "object"
        ? message.params
        : {};
    if (!params.protocolVersion) {
      params.protocolVersion = LATEST_PROTOCOL_VERSION;
    }
    if (!params.clientInfo || typeof params.clientInfo !== "object") {
      params.clientInfo = { name: "unknown-client", version: "0.0.0" };
    } else {
      params.clientInfo.name =
        params.clientInfo.name || "unknown-client";
      params.clientInfo.version =
        params.clientInfo.version || "0.0.0";
    }
    message.params = params;
  };

  await server.connect(transport);
}

main().catch((err) => {
  // Log to stderr to avoid polluting the MCP transport channel.
  process.stderr.write(`codex-mcp fatal error: ${err?.message ?? err}\n`);
  process.exit(1);
});

function resolveInvocation(argString, argv) {
  const hasArgv = Array.isArray(argv) && argv.length > 0;
  const hasArgString = typeof argString === "string" && argString.trim().length;

  if (!hasArgv && !hasArgString) {
    throw new McpError(
      ErrorCode.InvalidParams,
      "Devi specificare `argv` (array) oppure `args` (stringa).",
    );
  }

  if (hasArgv) {
    if (!argv.every((entry) => typeof entry === "string")) {
      throw new McpError(
        ErrorCode.InvalidParams,
        "`argv` deve contenere solo stringhe.",
      );
    }

    return { command: CODEX_BIN, args: argv, shell: false };
  }

  return { command: `${CODEX_BIN} ${argString}`, args: [], shell: true };
}

function resolveCwd(cwd) {
  if (!cwd) {
    return process.cwd();
  }

  const resolved = path.isAbsolute(cwd)
    ? cwd
    : path.join(process.cwd(), cwd);
  if (!fs.existsSync(resolved) || !fs.statSync(resolved).isDirectory()) {
    throw new McpError(
      ErrorCode.InvalidParams,
      `cwd non valida o inesistente: ${resolved}`,
    );
  }

  return resolved;
}

function normalizeTimeout(value) {
  if (value === undefined || value === null) {
    return DEFAULT_TIMEOUT_MS;
  }

  const parsed = Number.parseInt(String(value), 10);
  if (Number.isNaN(parsed) || parsed <= 0) {
    throw new McpError(
      ErrorCode.InvalidParams,
      "`timeoutMs` deve essere un numero maggiore di zero.",
    );
  }

  return parsed;
}

function mergeEnv(env) {
  if (!env || typeof env !== "object") {
    return process.env;
  }

  const merged = { ...process.env };
  for (const [key, value] of Object.entries(env)) {
    if (typeof value === "string") {
      merged[key] = value;
    }
  }
  return merged;
}

function runCommand({ command, args, cwd, env, shell, timeout }) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env, shell });
    let stdout = "";
    let stderr = "";
    let timedOut = false;
    let timeoutId;

    if (timeout && timeout > 0) {
      timeoutId = setTimeout(() => {
        timedOut = true;
        child.kill("SIGKILL");
      }, timeout);
    }

    child.stdout?.on("data", (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr?.on("data", (chunk) => {
      stderr += chunk.toString();
    });
    child.on("error", (err) => {
      clearTimeout(timeoutId);
      reject(err);
    });
    child.on("close", (code, signal) => {
      clearTimeout(timeoutId);
      resolve({
        stdout,
        stderr,
        code: code ?? -1,
        signal: signal ?? null,
        timedOut,
      });
    });
  });
}

function buildContent(stdout, stderr, code, signal, timedOut, timeout) {
  const blocks = [];
  const trimmedOut = stdout.trim();
  const trimmedErr = stderr.trim();

  if (trimmedOut) {
    blocks.push({ type: "text", text: trimmedOut });
  }
  if (trimmedErr) {
    blocks.push({ type: "text", text: `stderr:\n${trimmedErr}` });
  }

  const summary = [];
  if (timedOut) {
    summary.push(`Timeout dopo ${timeout}ms (processo terminato)`);
  }
  summary.push(`exitCode: ${code}`);
  if (signal) {
    summary.push(`signal: ${signal}`);
  }
  blocks.push({ type: "text", text: summary.join("\n") });

  return blocks;
}
