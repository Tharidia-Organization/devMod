#!/usr/bin/env node
/**
 * MCP Server for AMP (CubeCoders) Minecraft Server Management
 *
 * Provides tools for:
 * - Server status, start, stop, restart
 * - File operations (upload mods, configs)
 * - Console commands
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import https from 'https';
import http from 'http';

// Configuration from environment
const AMP_HOST = process.env.AMP_HOST || '51.68.35.33';
const AMP_PORT = process.env.AMP_PORT || '8080';
const AMP_INSTANCE_ID = process.env.AMP_INSTANCE_ID || '4dacbb63-e7cc-4481-9cd1-13970cb57f8f';
const AMP_USERNAME = process.env.AMP_USERNAME || 'lordbanana89';
const AMP_PASSWORD = process.env.AMP_PASSWORD || 'Robby1989?';
const SSH_HOST = process.env.SSH_HOST || 'debian@51.68.35.33';
const MODS_PATH = process.env.MODS_PATH || '/home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/mods';
const CONFIG_PATH = process.env.CONFIG_PATH || '/home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/config';

let sessionId = null;

/**
 * Make HTTP request to AMP API
 */
async function ampRequest(endpoint, data = {}, useInstance = false) {
  const port = useInstance ? '8081' : AMP_PORT;
  const host = useInstance ? '127.0.0.1' : AMP_HOST;

  // For instance API, we need to go through SSH
  if (useInstance) {
    return sshAmpRequest(endpoint, data);
  }

  return new Promise((resolve, reject) => {
    const postData = JSON.stringify({ SESSIONID: sessionId, ...data });

    const options = {
      hostname: AMP_HOST,
      port: parseInt(AMP_PORT),
      path: `/API/${endpoint}`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(body));
        } catch (e) {
          resolve({ raw: body });
        }
      });
    });

    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

/**
 * Make request to instance API via SSH tunnel with auto-retry on session expiry
 */
async function sshAmpRequest(endpoint, data = {}, retried = false) {
  const { execSync } = await import('child_process');
  const postData = JSON.stringify({ SESSIONID: sessionId, ...data });

  const cmd = `ssh ${SSH_HOST} 'curl -s -X POST "http://127.0.0.1:8081/API/${endpoint}" -H "Content-Type: application/json" -H "Accept: application/json" -d '"'"'${postData}'"'"''`;

  try {
    const result = execSync(cmd, { encoding: 'utf8', timeout: 30000 });
    const parsed = JSON.parse(result);

    // Handle null response (AMP returns null for some successful operations)
    if (parsed === null) {
      return { _nullResponse: true, Status: true };
    }

    // Check for session expired and retry with fresh login
    if (!retried && isSessionExpired(parsed)) {
      sessionId = null;
      await loginInstance();
      return sshAmpRequest(endpoint, data, true);
    }

    return parsed;
  } catch (e) {
    return { error: e.message };
  }
}

/**
 * Check if result indicates session expired
 */
function isSessionExpired(result) {
  if (!result) return false;
  if (result.Title?.includes('Unauthorized') ||
      result.Message?.includes('permission') ||
      result.Message?.includes('Session')) {
    return true;
  }
  return false;
}

/**
 * Login to AMP API
 */
async function login() {
  const result = await ampRequest('Core/Login', {
    username: AMP_USERNAME,
    password: AMP_PASSWORD,
    token: '',
    rememberMe: false
  });

  if (result.sessionID) {
    sessionId = result.sessionID;
    return true;
  }
  return false;
}

/**
 * Login to instance API via SSH
 */
async function loginInstance() {
  const result = await sshAmpRequest('Core/Login', {
    username: AMP_USERNAME,
    password: AMP_PASSWORD,
    token: '',
    rememberMe: false
  });

  if (result.sessionID) {
    sessionId = result.sessionID;
    return true;
  }
  return false;
}

/**
 * Execute SSH command
 */
async function sshCommand(cmd) {
  const { execSync } = await import('child_process');
  try {
    const result = execSync(`ssh ${SSH_HOST} '${cmd}'`, {
      encoding: 'utf8',
      timeout: 60000,
      maxBuffer: 10 * 1024 * 1024
    });
    return { success: true, output: result };
  } catch (e) {
    return { success: false, error: e.message, output: e.stdout || '' };
  }
}

// Create MCP server
const server = new Server(
  {
    name: 'amp-minecraft',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// Define available tools
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: 'amp_status',
        description: 'Get Minecraft server status (running state, TPS, players, CPU, memory)',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      },
      {
        name: 'amp_start',
        description: 'Start the Minecraft server',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      },
      {
        name: 'amp_stop',
        description: 'Stop the Minecraft server gracefully',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      },
      {
        name: 'amp_restart',
        description: 'Restart the Minecraft server',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      },
      {
        name: 'amp_console',
        description: 'Send a command to the Minecraft server console',
        inputSchema: {
          type: 'object',
          properties: {
            command: {
              type: 'string',
              description: 'The command to send (without /)'
            }
          },
          required: ['command']
        }
      },
      {
        name: 'amp_logs',
        description: 'Get recent server logs',
        inputSchema: {
          type: 'object',
          properties: {
            lines: {
              type: 'number',
              description: 'Number of lines to retrieve (default: 50)'
            },
            filter: {
              type: 'string',
              description: 'Optional grep filter pattern'
            }
          },
          required: []
        }
      },
      {
        name: 'amp_list_mods',
        description: 'List all mods in the server mods folder',
        inputSchema: {
          type: 'object',
          properties: {
            filter: {
              type: 'string',
              description: 'Optional filter pattern'
            }
          },
          required: []
        }
      },
      {
        name: 'amp_mod_enable',
        description: 'Enable a disabled mod (.disabled -> .jar)',
        inputSchema: {
          type: 'object',
          properties: {
            modName: {
              type: 'string',
              description: 'Name of the mod file (without .disabled extension)'
            }
          },
          required: ['modName']
        }
      },
      {
        name: 'amp_mod_disable',
        description: 'Disable a mod (.jar -> .disabled)',
        inputSchema: {
          type: 'object',
          properties: {
            modName: {
              type: 'string',
              description: 'Name of the mod file (with .jar extension)'
            }
          },
          required: ['modName']
        }
      },
      {
        name: 'amp_read_config',
        description: 'Read a config file from the server',
        inputSchema: {
          type: 'object',
          properties: {
            path: {
              type: 'string',
              description: 'Path relative to config folder (e.g., "coldsweat/world.toml")'
            }
          },
          required: ['path']
        }
      },
      {
        name: 'amp_upload_file',
        description: 'Upload a file to the server via SCP',
        inputSchema: {
          type: 'object',
          properties: {
            localPath: {
              type: 'string',
              description: 'Local file path'
            },
            remotePath: {
              type: 'string',
              description: 'Remote destination path'
            }
          },
          required: ['localPath', 'remotePath']
        }
      },
      {
        name: 'amp_ssh',
        description: 'Execute a custom SSH command on the server',
        inputSchema: {
          type: 'object',
          properties: {
            command: {
              type: 'string',
              description: 'The command to execute'
            }
          },
          required: ['command']
        }
      }
    ]
  };
});

// Handle tool calls
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    switch (name) {
      case 'amp_status': {
        await loginInstance();
        const status = await sshAmpRequest('Core/GetStatus');
        return {
          content: [{
            type: 'text',
            text: JSON.stringify({
              state: status.State,
              uptime: status.Uptime,
              tps: status.TPS,
              players: `${status.Metrics?.['Active Users']?.RawValue || 0}/${status.Metrics?.['Active Users']?.MaxValue || 0}`,
              cpu: `${status.Metrics?.['CPU Usage']?.RawValue || 0}%`,
              memory: `${status.Metrics?.['Memory Usage']?.RawValue || 0} MB`
            }, null, 2)
          }]
        };
      }

      case 'amp_start': {
        await loginInstance();
        const result = await sshAmpRequest('Core/Start');
        return {
          content: [{
            type: 'text',
            text: result.Status ? 'Server starting...' : `Failed: ${result.Reason || 'Unknown error'}`
          }]
        };
      }

      case 'amp_stop': {
        await loginInstance();
        const result = await sshAmpRequest('Core/Stop');
        return {
          content: [{
            type: 'text',
            text: result.Status ? 'Server stopping...' : `Failed: ${result.Reason || 'Unknown error'}`
          }]
        };
      }

      case 'amp_restart': {
        await loginInstance();
        const result = await sshAmpRequest('Core/Restart');
        return {
          content: [{
            type: 'text',
            text: result.Status ? 'Server restarting...' : `Failed: ${result.Reason || 'Unknown error'}`
          }]
        };
      }

      case 'amp_console': {
        await loginInstance();
        const result = await sshAmpRequest('Core/SendConsoleMessage', { message: args.command });
        // Handle various response formats
        if (result?.error) {
          return { content: [{ type: 'text', text: `Error: ${result.error}` }] };
        }
        // AMP returns empty object {} on success for console commands
        const success = result && (result.Status === true || result.Status === undefined || Object.keys(result).length === 0);
        return {
          content: [{
            type: 'text',
            text: success ? `Command sent: ${args.command}` : `Failed: ${result?.Reason || result?.Message || JSON.stringify(result)}`
          }]
        };
      }

      case 'amp_logs': {
        const lines = args.lines || 50;
        // Escape filter for safe shell usage - replace single quotes
        const safeFilter = args.filter ? args.filter.replace(/'/g, "'\"'\"'") : '';
        const filterCmd = safeFilter ? ` | grep -iE '${safeFilter}'` : '';
        const cmd = `sudo tail -${lines} /home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/logs/latest.log${filterCmd}`;
        const result = await sshCommand(cmd);
        return {
          content: [{
            type: 'text',
            text: result.success ? (result.output || '(no matching lines)') : `Error: ${result.error}`
          }]
        };
      }

      case 'amp_list_mods': {
        const filter = args.filter ? `| grep -i '${args.filter}'` : '';
        const cmd = `sudo ls -la ${MODS_PATH} ${filter}`;
        const result = await sshCommand(cmd);
        return {
          content: [{
            type: 'text',
            text: result.success ? result.output : `Error: ${result.error}`
          }]
        };
      }

      case 'amp_mod_enable': {
        const cmd = `sudo mv '${MODS_PATH}/${args.modName}.disabled' '${MODS_PATH}/${args.modName}'`;
        const result = await sshCommand(cmd);
        return {
          content: [{
            type: 'text',
            text: result.success ? `Enabled: ${args.modName}` : `Error: ${result.error}`
          }]
        };
      }

      case 'amp_mod_disable': {
        const cmd = `sudo mv '${MODS_PATH}/${args.modName}' '${MODS_PATH}/${args.modName}.disabled'`;
        const result = await sshCommand(cmd);
        return {
          content: [{
            type: 'text',
            text: result.success ? `Disabled: ${args.modName}` : `Error: ${result.error}`
          }]
        };
      }

      case 'amp_read_config': {
        const cmd = `sudo cat '${CONFIG_PATH}/${args.path}'`;
        const result = await sshCommand(cmd);
        return {
          content: [{
            type: 'text',
            text: result.success ? result.output : `Error: ${result.error}`
          }]
        };
      }

      case 'amp_upload_file': {
        const { execSync } = await import('child_process');
        try {
          execSync(`scp '${args.localPath}' '${SSH_HOST}:${args.remotePath}'`, {
            encoding: 'utf8',
            timeout: 120000
          });
          return {
            content: [{
              type: 'text',
              text: `Uploaded: ${args.localPath} -> ${args.remotePath}`
            }]
          };
        } catch (e) {
          return {
            content: [{
              type: 'text',
              text: `Upload failed: ${e.message}`
            }]
          };
        }
      }

      case 'amp_ssh': {
        const result = await sshCommand(args.command);
        return {
          content: [{
            type: 'text',
            text: result.success ? result.output : `Error: ${result.error}\nOutput: ${result.output}`
          }]
        };
      }

      default:
        return {
          content: [{
            type: 'text',
            text: `Unknown tool: ${name}`
          }],
          isError: true
        };
    }
  } catch (error) {
    return {
      content: [{
        type: 'text',
        text: `Error: ${error.message}`
      }],
      isError: true
    };
  }
});

// Start the server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('AMP MCP Server running on stdio');
}

main().catch(console.error);
