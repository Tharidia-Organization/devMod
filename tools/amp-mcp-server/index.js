#!/usr/bin/env node
/**
 * AMP MCP Server - Control CubeCoders AMP via Model Context Protocol
 *
 * Environment variables:
 *   AMP_URL - Base URL of AMP panel (e.g., http://51.68.35.33:8080)
 *   AMP_INSTANCE_ID - Instance ID to control
 *   AMP_USERNAME - AMP username
 *   AMP_PASSWORD - AMP password
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema
} from '@modelcontextprotocol/sdk/types.js';
import { AMPClient } from './amp-client.js';
import { execSync } from 'child_process';

// Configuration from environment
const config = {
  url: process.env.AMP_URL || 'http://51.68.35.33:8080',
  instanceId: process.env.AMP_INSTANCE_ID || '4dacbb63-e7cc-4481-9cd1-13970cb57f8f',
  username: process.env.AMP_USERNAME || 'lordbanana89',
  password: process.env.AMP_PASSWORD || '',
  sshHost: process.env.SSH_HOST || 'debian@51.68.35.33',
  modsPath: process.env.MODS_PATH || '/home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/mods',
  configPath: process.env.CONFIG_PATH || '/home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/config',
  logsPath: process.env.LOGS_PATH || '/home/amp/.ampdata/instances/DevModTestPlace01/Minecraft/logs/latest.log'
};

/**
 * Execute SSH command on remote server
 */
function sshCommand(cmd) {
  try {
    const result = execSync(`ssh ${config.sshHost} '${cmd}'`, {
      encoding: 'utf8',
      timeout: 60000,
      maxBuffer: 10 * 1024 * 1024
    });
    return { success: true, output: result };
  } catch (e) {
    return { success: false, error: e.message, output: e.stdout || '' };
  }
}

// Validate configuration
if (!config.password) {
  console.error('AMP_PASSWORD environment variable is required');
  process.exit(1);
}

// Create AMP client
const amp = new AMPClient(config.url, config.instanceId, config.username, config.password);

// Create MCP server
const server = new Server(
  { name: 'amp-mcp-server', version: '1.0.0' },
  { capabilities: { tools: {} } }
);

// Define available tools
const tools = [
  {
    name: 'amp_get_status',
    description: 'Get current status of the Minecraft server (CPU, RAM, TPS, players, uptime)',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'amp_start_server',
    description: 'Start the Minecraft server',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'amp_stop_server',
    description: 'Stop the Minecraft server gracefully',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'amp_restart_server',
    description: 'Restart the Minecraft server',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'amp_kill_server',
    description: 'Force kill the Minecraft server (use only if stop fails)',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'amp_send_command',
    description: 'Send a command to the Minecraft server console',
    inputSchema: {
      type: 'object',
      properties: {
        command: {
          type: 'string',
          description: 'The command to send (without leading /)'
        }
      },
      required: ['command']
    }
  },
  {
    name: 'amp_get_console',
    description: 'Get recent console output from the Minecraft server',
    inputSchema: {
      type: 'object',
      properties: {
        lines: {
          type: 'number',
          description: 'Number of lines to retrieve (default: 50, max: 200)'
        }
      },
      required: []
    }
  },
  {
    name: 'amp_list_instances',
    description: 'List all AMP instances on this server',
    inputSchema: {
      type: 'object',
      properties: {},
      required: []
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
];

// Handle tool listing
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return { tools };
});

// Handle tool execution
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    let result;

    switch (name) {
      case 'amp_get_status': {
        const status = await amp.getStatus();
        result = `Server Status: ${status.stateName}
Uptime: ${status.uptime}
CPU: ${status.cpu}/${status.cpuMax}%
Memory: ${status.memory}/${status.memoryMax} MB
Players: ${status.players}/${status.playersMax}
TPS: ${status.tps}/20`;
        break;
      }

      case 'amp_start_server': {
        await amp.start();
        result = 'Server start command sent. Use amp_get_status to monitor startup.';
        break;
      }

      case 'amp_stop_server': {
        await amp.stop();
        result = 'Server stop command sent. Use amp_get_status to monitor shutdown.';
        break;
      }

      case 'amp_restart_server': {
        await amp.restart();
        result = 'Server restart command sent. Use amp_get_status to monitor restart.';
        break;
      }

      case 'amp_kill_server': {
        await amp.kill();
        result = 'Server kill command sent. The server process has been forcefully terminated.';
        break;
      }

      case 'amp_send_command': {
        const { command } = args;
        if (!command) {
          throw new Error('Command is required');
        }
        await amp.sendConsoleMessage(command);
        result = `Command sent: ${command}`;
        break;
      }

      case 'amp_get_console': {
        const lines = Math.min(args?.lines || 50, 200);
        const entries = await amp.getConsoleOutput(lines);
        result = entries.map(e => e.contents).join('\n') || 'No console output available';
        break;
      }

      case 'amp_list_instances': {
        const instances = await amp.getInstances();
        result = instances.map(i =>
          `${i.friendlyName} (${i.id})\n  Module: ${i.moduleDisplay || i.module}\n  Port: ${i.port}\n  Running: ${i.running}`
        ).join('\n\n');
        break;
      }

      case 'amp_logs': {
        const lines = args?.lines || 50;
        // Use double quotes and escape for shell safety
        const safeFilter = args?.filter ? args.filter.replace(/"/g, '\\"').replace(/\$/g, '\\$') : '';
        const filterCmd = safeFilter ? ` | grep -iE "${safeFilter}"` : '';
        const cmd = `sudo tail -${lines} ${config.logsPath}${filterCmd}`;
        const res = sshCommand(cmd);
        result = res.success ? (res.output || '(no matching lines)') : `Error: ${res.error}`;
        break;
      }

      case 'amp_list_mods': {
        const filter = args?.filter ? `| grep -i '${args.filter}'` : '';
        const cmd = `sudo ls -la ${config.modsPath} ${filter}`;
        const res = sshCommand(cmd);
        result = res.success ? res.output : `Error: ${res.error}`;
        break;
      }

      case 'amp_mod_enable': {
        const { modName } = args;
        if (!modName) throw new Error('modName is required');
        const cmd = `sudo mv '${config.modsPath}/${modName}.disabled' '${config.modsPath}/${modName}'`;
        const res = sshCommand(cmd);
        result = res.success ? `Enabled: ${modName}` : `Error: ${res.error}`;
        break;
      }

      case 'amp_mod_disable': {
        const { modName } = args;
        if (!modName) throw new Error('modName is required');
        const cmd = `sudo mv '${config.modsPath}/${modName}' '${config.modsPath}/${modName}.disabled'`;
        const res = sshCommand(cmd);
        result = res.success ? `Disabled: ${modName}` : `Error: ${res.error}`;
        break;
      }

      case 'amp_read_config': {
        const { path } = args;
        if (!path) throw new Error('path is required');
        const cmd = `sudo cat '${config.configPath}/${path}'`;
        const res = sshCommand(cmd);
        result = res.success ? res.output : `Error: ${res.error}`;
        break;
      }

      case 'amp_upload_file': {
        const { localPath, remotePath } = args;
        if (!localPath || !remotePath) throw new Error('localPath and remotePath are required');
        try {
          execSync(`scp '${localPath}' '${config.sshHost}:${remotePath}'`, {
            encoding: 'utf8',
            timeout: 120000
          });
          result = `Uploaded: ${localPath} -> ${remotePath}`;
        } catch (e) {
          result = `Upload failed: ${e.message}`;
        }
        break;
      }

      case 'amp_ssh': {
        const { command } = args;
        if (!command) throw new Error('command is required');
        const res = sshCommand(command);
        result = res.success ? res.output : `Error: ${res.error}\nOutput: ${res.output}`;
        break;
      }

      default:
        throw new Error(`Unknown tool: ${name}`);
    }

    return {
      content: [{ type: 'text', text: result }]
    };
  } catch (error) {
    return {
      content: [{ type: 'text', text: `Error: ${error.message}` }],
      isError: true
    };
  }
});

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('AMP MCP Server running on stdio');
}

main().catch(console.error);
