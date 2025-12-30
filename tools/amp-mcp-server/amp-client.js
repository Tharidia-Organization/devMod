/**
 * AMP API Client - Wrapper for CubeCoders AMP REST API
 */

export class AMPClient {
  constructor(baseUrl, instanceId, username, password) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.instanceId = instanceId;
    this.username = username;
    this.password = password;
    this.adsSessionId = null;
    this.instanceSessionId = null;
  }

  async request(endpoint, data = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(data)
    });

    const text = await response.text();
    if (!text) return null;

    try {
      return JSON.parse(text);
    } catch {
      throw new Error(`Invalid JSON response: ${text}`);
    }
  }

  async login() {
    // Login to ADS first
    const adsLogin = await this.request('/API/Core/Login', {
      username: this.username,
      password: this.password,
      token: '',
      rememberMe: false
    });

    if (!adsLogin?.success) {
      throw new Error(`ADS Login failed: ${adsLogin?.resultReason || 'Unknown error'}`);
    }
    this.adsSessionId = adsLogin.sessionID;

    // Login to instance via proxy
    const instanceLogin = await this.request(
      `/API/ADSModule/Servers/${this.instanceId}/API/Core/Login`,
      {
        SESSIONID: this.adsSessionId,
        username: this.username,
        password: this.password,
        token: '',
        rememberMe: false
      }
    );

    if (!instanceLogin?.success) {
      throw new Error(`Instance Login failed: ${instanceLogin?.resultReason || 'Unknown error'}`);
    }
    this.instanceSessionId = instanceLogin.sessionID;

    return { adsSessionId: this.adsSessionId, instanceSessionId: this.instanceSessionId };
  }

  async ensureLoggedIn() {
    if (!this.instanceSessionId) {
      await this.login();
    }
  }

  async instanceRequest(method, params = {}, retried = false) {
    await this.ensureLoggedIn();
    const result = await this.request(
      `/API/ADSModule/Servers/${this.instanceId}/API/${method}`,
      { SESSIONID: this.instanceSessionId, ...params }
    );

    // Check for session expired error and retry with fresh login
    if (!retried && this.isSessionExpired(result)) {
      this.instanceSessionId = null;
      this.adsSessionId = null;
      await this.login();
      return this.instanceRequest(method, params, true);
    }

    return result;
  }

  isSessionExpired(result) {
    if (!result) return false;
    // Check for unauthorized/session errors
    if (result.Title?.includes('Unauthorized') ||
        result.Message?.includes('permission') ||
        result.Message?.includes('Session')) {
      return true;
    }
    return false;
  }

  // Server Control Methods
  async getStatus() {
    const result = await this.instanceRequest('Core/GetStatus');
    if (!result) throw new Error('Failed to get status');
    return {
      state: result.State,
      stateName: this.getStateName(result.State),
      uptime: result.Uptime,
      cpu: result.Metrics?.['CPU Usage']?.RawValue ?? 0,
      cpuMax: result.Metrics?.['CPU Usage']?.MaxValue ?? 100,
      memory: result.Metrics?.['Memory Usage']?.RawValue ?? 0,
      memoryMax: result.Metrics?.['Memory Usage']?.MaxValue ?? 0,
      players: result.Metrics?.['Active Users']?.RawValue ?? 0,
      playersMax: result.Metrics?.['Active Users']?.MaxValue ?? 0,
      tps: result.Metrics?.['TPS']?.RawValue ?? 0
    };
  }

  getStateName(state) {
    const states = {
      0: 'Stopped',
      5: 'PreStart',
      7: 'Configuring',
      10: 'Starting',
      20: 'Running',
      30: 'Stopping',
      40: 'Restarting',
      45: 'Updating',
      50: 'AwaitingUserInput',
      100: 'Failed',
      200: 'Suspended',
      250: 'Maintainence',
      999: 'Indeterminate'
    };
    return states[state] || 'Unknown';
  }

  async start() {
    return this.instanceRequest('Core/Start');
  }

  async stop() {
    return this.instanceRequest('Core/Stop');
  }

  async restart() {
    return this.instanceRequest('Core/Restart');
  }

  async kill() {
    return this.instanceRequest('Core/Kill');
  }

  // Console Methods
  async sendConsoleMessage(message) {
    return this.instanceRequest('Core/SendConsoleMessage', { message });
  }

  async getConsoleOutput(maxLines = 50) {
    const result = await this.instanceRequest('Core/GetUpdates');
    if (!result?.ConsoleEntries) return [];

    return result.ConsoleEntries
      .slice(-maxLines)
      .map(entry => ({
        timestamp: entry.Timestamp,
        source: entry.Source,
        type: entry.Type,
        contents: entry.Contents
      }));
  }

  // Instance Info
  async getInstances() {
    await this.ensureLoggedIn();
    const result = await this.request('/API/ADSModule/GetInstances', {
      SESSIONID: this.adsSessionId
    });

    const instances = [];
    for (const target of result || []) {
      for (const inst of target.AvailableInstances || []) {
        instances.push({
          id: inst.InstanceID,
          name: inst.InstanceName,
          friendlyName: inst.FriendlyName,
          module: inst.Module,
          moduleDisplay: inst.ModuleDisplayName,
          port: inst.Port,
          running: inst.Running,
          appState: inst.AppState
        });
      }
    }
    return instances;
  }
}
