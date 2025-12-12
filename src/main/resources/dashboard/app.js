/**
 * DevMod Telemetry Dashboard - JavaScript Application
 */

const API_BASE = '';  // Same origin

// ==================== State ====================

let currentSection = 'overview';

// ==================== Initialization ====================

document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    setupTabs();
    checkHealth();
    loadOverview();
});

// ==================== Navigation ====================

function setupNavigation() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const section = btn.dataset.section;
            switchSection(section);
        });
    });
}

function switchSection(section) {
    // Update nav buttons
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.section === section);
    });

    // Update sections
    document.querySelectorAll('.section').forEach(sec => {
        sec.classList.toggle('active', sec.id === `section-${section}`);
    });

    currentSection = section;

    // Load section data
    switch (section) {
        case 'overview':
            loadOverview();
            break;
        case 'combat':
            loadCombatWeapons();
            break;
        case 'endurance':
            loadEndurancePerks();
            break;
        case 'dungeons':
            loadDungeonRuns();
            break;
    }
}

// ==================== Tabs ====================

function setupTabs() {
    document.querySelectorAll('.tabs').forEach(tabContainer => {
        tabContainer.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const tabId = btn.dataset.tab;
                const section = tabContainer.parentElement;

                // Update tab buttons
                tabContainer.querySelectorAll('.tab-btn').forEach(b => {
                    b.classList.toggle('active', b === btn);
                });

                // Update tab content
                section.querySelectorAll('.tab-content').forEach(content => {
                    content.classList.toggle('active', content.id === tabId);
                });
            });
        });
    });
}

// ==================== API Calls ====================

async function fetchApi(endpoint, options = {}) {
    try {
        const response = await fetch(`${API_BASE}${endpoint}`, options);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error(`API Error (${endpoint}):`, error);
        throw error;
    }
}

async function checkHealth() {
    const statusEl = document.getElementById('status');
    try {
        const health = await fetchApi('/api/health');
        if (health.status === 'ok' && health.duckdb_enabled) {
            statusEl.textContent = 'Connected';
            statusEl.className = 'status connected';
        } else {
            statusEl.textContent = 'DuckDB Disabled';
            statusEl.className = 'status error';
        }
    } catch (error) {
        statusEl.textContent = 'Connection Error';
        statusEl.className = 'status error';
    }
}

// ==================== Overview ====================

async function loadOverview() {
    try {
        const summary = await fetchApi('/api/summary');
        renderSummaryCards(summary);
        renderTableCounts(summary.tables);
        updateRefreshTime();
    } catch (error) {
        document.getElementById('summary-cards').innerHTML =
            '<div class="error-message">Failed to load summary data</div>';
    }
}

function renderSummaryCards(summary) {
    const container = document.getElementById('summary-cards');
    const tables = summary.tables || {};
    const recent = summary.recent_activity || {};

    const totalRows = Object.values(tables).reduce((sum, v) => sum + (v > 0 ? v : 0), 0);
    const combatHits = tables.combat_hits || 0;
    const recentHits = recent.combat_hits_15min || 0;
    const dbSize = summary.db_size_kb || 0;

    container.innerHTML = `
        <div class="card">
            <div class="label">Total Rows</div>
            <div class="value">${formatNumber(totalRows)}</div>
        </div>
        <div class="card">
            <div class="label">Combat Hits</div>
            <div class="value">${formatNumber(combatHits)}</div>
        </div>
        <div class="card">
            <div class="label">Hits (15min)</div>
            <div class="value">${formatNumber(recentHits)}</div>
        </div>
        <div class="card">
            <div class="label">DB Size</div>
            <div class="value">${formatSize(dbSize * 1024)}</div>
        </div>
    `;
}

function renderTableCounts(tables) {
    const container = document.getElementById('table-counts');
    if (!tables || Object.keys(tables).length === 0) {
        container.innerHTML = '<div class="empty-state">No tables found</div>';
        return;
    }

    const rows = Object.entries(tables)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([name, count]) => `
            <tr>
                <td>${name}</td>
                <td>${count >= 0 ? formatNumber(count) : 'Error'}</td>
            </tr>
        `).join('');

    container.innerHTML = `
        <table>
            <thead><tr><th>Table</th><th>Row Count</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

// ==================== Combat ====================

async function loadCombatHits() {
    const limit = document.getElementById('combat-hits-limit').value;
    const container = document.getElementById('combat-hits-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/combat/hits?limit=${limit}`);
        renderTable(container, data, ['ts', 'attacker_name', 'target_name', 'damage', 'damage_type', 'room']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load combat hits</div>';
    }
}

async function loadCombatDeaths() {
    const limit = document.getElementById('combat-deaths-limit').value;
    const container = document.getElementById('combat-deaths-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/combat/deaths?limit=${limit}`);
        renderTable(container, data, ['ts', 'target_name', 'target_type', 'cause', 'room', 'ttk_spawn_ms']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load combat deaths</div>';
    }
}

async function loadCombatWeapons() {
    const container = document.getElementById('combat-weapons-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi('/api/combat/weapons');
        renderTable(container, data, ['weapon', 'hits', 'total_damage', 'avg_damage', 'misses']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load weapon stats</div>';
    }
}

async function loadCombatFights() {
    const limit = document.getElementById('combat-fights-limit').value;
    const container = document.getElementById('combat-fights-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/combat/fights?limit=${limit}`);
        renderTable(container, data, ['start_ts', 'room', 'duration_ms', 'hits', 'mob_kills', 'player_deaths']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load fights</div>';
    }
}

// ==================== Endurance ====================

async function loadEnduranceSessions() {
    const limit = document.getElementById('endurance-sessions-limit').value;
    const container = document.getElementById('endurance-sessions-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/endurance/sessions?limit=${limit}`);
        renderTable(container, data, ['start_ts', 'player_name', 'quest_name', 'outcome', 'waves_completed', 'total_kills']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load sessions</div>';
    }
}

async function loadEnduranceWaves() {
    const limit = document.getElementById('endurance-waves-limit').value;
    const container = document.getElementById('endurance-waves-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/endurance/waves?limit=${limit}`);
        renderTable(container, data, ['ts', 'wave_number', 'event_type', 'mob_count', 'mobs_killed', 'duration_ms']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load waves</div>';
    }
}

async function loadEndurancePerks() {
    const container = document.getElementById('endurance-perks-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi('/api/endurance/perks');
        renderTable(container, data, ['perk_name', 'category', 'tier', 'picks', 'avg_stacks']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load perk stats</div>';
    }
}

// ==================== Dungeons ====================

async function loadDungeonRuns() {
    const limit = document.getElementById('dungeons-limit').value;
    const container = document.getElementById('dungeons-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/dungeons/runs?limit=${limit}`);
        renderTable(container, data, ['start_ts', 'player_name', 'dungeon_id', 'outcome', 'duration_ms', 'kills', 'deaths', 'rooms_visited']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load dungeon runs</div>';
    }
}

// ==================== Spatial ====================

async function loadHeatmaps() {
    const type = document.getElementById('spatial-heatmap-type').value;
    const limit = document.getElementById('spatial-heatmaps-limit').value;
    const container = document.getElementById('spatial-heatmaps-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        let url = `/api/spatial/heatmaps?limit=${limit}`;
        if (type) url += `&type=${type}`;
        const data = await fetchApi(url);
        renderTable(container, data, ['ts', 'heatmap_type', 'room', 'x', 'y', 'z', 'count']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load heatmaps</div>';
    }
}

async function loadTransitions() {
    const limit = document.getElementById('spatial-transitions-limit').value;
    const container = document.getElementById('spatial-transitions-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/spatial/transitions?limit=${limit}`);
        renderTable(container, data, ['ts', 'player_name', 'room']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load transitions</div>';
    }
}

// ==================== Economy ====================

async function loadEconomyDrops() {
    const limit = document.getElementById('economy-drops-limit').value;
    const container = document.getElementById('economy-drops-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/economy/drops?limit=${limit}`);
        renderTable(container, data, ['ts', 'mob_type', 'room', 'item_id', 'item_count', 'x', 'y', 'z']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load mob drops</div>';
    }
}

async function loadEconomyKills() {
    const limit = document.getElementById('economy-kills-limit').value;
    const container = document.getElementById('economy-kills-data');
    container.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchApi(`/api/economy/kills?limit=${limit}`);
        renderTable(container, data, ['ts', 'mob_type', 'total_kills', 'had_loot']);
    } catch (error) {
        container.innerHTML = '<div class="error-message">Failed to load mob kills</div>';
    }
}

// ==================== SQL Query ====================

async function executeQuery() {
    const sql = document.getElementById('sql-query').value;
    const container = document.getElementById('query-result');

    if (!sql.trim()) {
        container.innerHTML = '<div class="error-message">Please enter a SQL query</div>';
        return;
    }

    container.innerHTML = '<div class="loading">Executing...</div>';

    try {
        const data = await fetchApi('/api/query', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sql })
        });

        if (data.error) {
            container.innerHTML = `<div class="error-message">${data.error}</div>`;
        } else {
            renderTable(container, data);
        }
    } catch (error) {
        container.innerHTML = `<div class="error-message">Query failed: ${error.message}</div>`;
    }
}

// ==================== Table Rendering ====================

function renderTable(container, data, columns = null) {
    if (!data || data.length === 0) {
        container.innerHTML = '<div class="empty-state">No data found</div>';
        return;
    }

    // Auto-detect columns if not specified
    if (!columns) {
        columns = Object.keys(data[0]);
    }

    const headers = columns.map(col => `<th>${formatColumnName(col)}</th>`).join('');
    const rows = data.map(row => {
        const cells = columns.map(col => {
            const value = row[col];
            return `<td>${formatValue(value, col)}</td>`;
        }).join('');
        return `<tr>${cells}</tr>`;
    }).join('');

    container.innerHTML = `
        <table>
            <thead><tr>${headers}</tr></thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

// ==================== Formatting ====================

function formatNumber(num) {
    if (num === null || num === undefined) return '-';
    return num.toLocaleString();
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatColumnName(name) {
    return name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

function formatValue(value, column) {
    if (value === null || value === undefined) return '-';

    // Timestamp formatting
    if (column.includes('ts') || column.includes('time')) {
        if (typeof value === 'string' || typeof value === 'number') {
            try {
                const date = new Date(value);
                if (!isNaN(date.getTime())) {
                    return date.toLocaleString();
                }
            } catch (e) {
                // Fall through
            }
        }
    }

    // JSON formatting
    if (typeof value === 'object') {
        return `<span class="json">${JSON.stringify(value)}</span>`;
    }

    // Number formatting
    if (typeof value === 'number') {
        if (Number.isInteger(value)) {
            return formatNumber(value);
        }
        return value.toFixed(2);
    }

    // Boolean formatting
    if (typeof value === 'boolean') {
        return value ? 'Yes' : 'No';
    }

    return String(value);
}

function updateRefreshTime() {
    const el = document.getElementById('refresh-time');
    el.textContent = 'Last updated: ' + new Date().toLocaleTimeString();
}
