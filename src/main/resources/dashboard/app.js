/**
 * DevMod Analytics Dashboard v3.0
 * Advanced telemetry visualization with Chart.js
 */

const API_BASE = '';
let currentSection = 'overview';
let selectedPlayer = '';
let charts = {};
let autoRefreshInterval = null;
let lastQueryData = null;
let cachedTableData = {};

// Chart.js global configuration
Chart.defaults.color = '#8892a0';
Chart.defaults.borderColor = 'rgba(255, 255, 255, 0.08)';
Chart.defaults.font.family = "'Segoe UI', system-ui, sans-serif";

const CHART_COLORS = [
    '#4ade80', '#60a5fa', '#f472b6', '#facc15', '#a78bfa',
    '#fb923c', '#34d399', '#818cf8', '#f87171', '#22d3ee',
    '#e879f9', '#84cc16', '#14b8a6', '#f59e0b', '#6366f1'
];

// ==================== Initialization ====================

document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    checkHealth();
    loadPlayersList();
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

let selectedArenaTemplate = '';

function switchSection(section) {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.section === section);
    });

    document.querySelectorAll('.section').forEach(sec => {
        sec.classList.toggle('active', sec.id === `section-${section}`);
    });

    currentSection = section;

    switch (section) {
        case 'overview': loadOverview(); break;
        case 'combat': loadCombatSection(); break;
        case 'players': loadPlayersSection(); break;
        case 'endurance': loadEnduranceSection(); break;
        case 'dungeons': loadDungeonsSection(); break;
        case 'performance': loadPerformanceSection(); break;
        case 'spatial': loadSpatialSection(); break;
        case 'economy': loadEconomySection(); break;
        case 'arenas': loadArenaSection(); break;
    }
}

function onTimeRangeChange() {
    refreshAll();
}

function onPlayerFilterChange() {
    selectedPlayer = document.getElementById('player-filter')?.value || '';
    if (currentSection === 'players') {
        loadPlayerStats();
    }
    refreshAll();
}

function toggleAutoRefresh() {
    const checkbox = document.getElementById('auto-refresh');
    if (checkbox?.checked) {
        autoRefreshInterval = setInterval(refreshAll, 30000); // 30 seconds
    } else {
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
        }
    }
}

function refreshAll() {
    switch (currentSection) {
        case 'overview': loadOverview(); break;
        case 'combat': loadCombatSection(); break;
        case 'players': loadPlayersSection(); break;
        case 'endurance': loadEnduranceSection(); break;
        case 'dungeons': loadDungeonsSection(); break;
        case 'performance': loadPerformanceSection(); break;
        case 'spatial': loadSpatialSection(); break;
        case 'economy': loadEconomySection(); break;
        case 'arenas': loadArenaSection(); break;
    }
    updateRefreshTime();
}

// ==================== API Calls ====================

async function fetchApi(endpoint) {
    try {
        const range = document.getElementById('time-range')?.value || '24h';
        const separator = endpoint.includes('?') ? '&' : '?';
        const url = `${API_BASE}${endpoint}${separator}range=${range}`;
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    } catch (error) {
        console.error(`API Error (${endpoint}):`, error);
        return null;
    }
}

async function checkHealth() {
    const statusEl = document.getElementById('status');
    try {
        const health = await fetchApi('/api/health');
        if (health?.status === 'ok' && health?.duckdb_enabled) {
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

async function loadPlayersList() {
    const players = await fetchApi('/api/analytics/players-list');
    const select = document.getElementById('player-filter');
    if (players && select) {
        // Keep current selection
        const currentValue = select.value;

        // Clear and rebuild options
        select.innerHTML = '<option value="">All Players</option>';
        players.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.player;
            opt.textContent = `${p.player} (${formatNumber(p.activity)})`;
            select.appendChild(opt);
        });

        // Restore selection
        if (currentValue) select.value = currentValue;
    }
}

// ==================== Overview Section ====================

async function loadOverview() {
    // Load trends for comparison
    const trends = await fetchApi('/api/analytics/trends');
    const overview = await fetchApi('/api/analytics/overview');

    if (overview) {
        document.getElementById('stat-total-hits').textContent = formatNumber(overview.totalHits || 0);
        document.getElementById('stat-total-damage').textContent = formatNumber(Math.round(overview.totalDamage || 0));
        document.getElementById('stat-total-deaths').textContent = formatNumber(overview.totalDeaths || 0);
        document.getElementById('stat-mobs-killed').textContent = formatNumber(overview.mobsKilled || 0);
        document.getElementById('stat-accuracy').textContent = (overview.accuracy || 0) + '%';
        document.getElementById('stat-db-size').textContent = formatSize((overview.dbSizeKb || 0) * 1024);
    }

    // Update trend indicators
    if (trends?.deltas) {
        updateTrend('trend-hits', trends.deltas.hitsChange);
        updateTrend('trend-damage', trends.deltas.damageChange);
    }
    if (trends) {
        updateTrend('trend-deaths', trends.deathsChange);
        updateTrend('trend-kills', trends.killsChange);
    }

    // Load charts in parallel
    await Promise.all([
        loadDpsTimelineChart(),
        loadBodyPartsChart(),
        loadWeaponsChart(),
        loadMobKillsChart(),
        loadDamageTypesOverviewChart()
    ]);

    updateRefreshTime();
}

function updateTrend(elementId, change) {
    const el = document.getElementById(elementId);
    if (!el || change === undefined || change === null) return;

    const value = Math.round(change * 10) / 10;
    if (value > 0) {
        el.textContent = `+${value}%`;
        el.className = 'stat-trend up';
    } else if (value < 0) {
        el.textContent = `${value}%`;
        el.className = 'stat-trend down';
    } else {
        el.textContent = '0%';
        el.className = 'stat-trend neutral';
    }
}

async function loadDpsTimelineChart() {
    const playerParam = selectedPlayer ? `&player=${selectedPlayer}` : '';
    const data = await fetchApi(`/api/analytics/dps-timeline?${playerParam}`);
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-dps-timeline');
    destroyChart('dps-timeline');

    charts['dps-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [
                {
                    label: 'DPS',
                    data: data.map(d => d.dps),
                    borderColor: CHART_COLORS[0],
                    backgroundColor: CHART_COLORS[0] + '30',
                    fill: true,
                    tension: 0.4,
                    yAxisID: 'y'
                },
                {
                    label: 'Avg Hit',
                    data: data.map(d => d.avg_hit),
                    borderColor: CHART_COLORS[1],
                    borderDash: [5, 5],
                    tension: 0.4,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { position: 'top' },
                tooltip: {
                    callbacks: {
                        afterBody: (items) => {
                            const idx = items[0]?.dataIndex;
                            if (idx !== undefined && data[idx]) {
                                return `Total Damage: ${formatNumber(data[idx].total_damage)}\nHits: ${data[idx].hits}`;
                            }
                        }
                    }
                }
            },
            scales: {
                y: { type: 'linear', position: 'left', title: { display: true, text: 'DPS' } },
                y1: { type: 'linear', position: 'right', title: { display: true, text: 'Avg Hit' }, grid: { drawOnChartArea: false } }
            }
        }
    });
}

async function loadBodyPartsChart() {
    const data = await fetchApi('/api/analytics/damage-by-bodypart');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-body-parts');
    destroyChart('body-parts');

    charts['body-parts'] = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: data.map(d => d.body_part),
            datasets: [{
                data: data.map(d => d.total_damage),
                backgroundColor: CHART_COLORS
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'right' },
                tooltip: {
                    callbacks: {
                        label: (ctx) => {
                            const d = data[ctx.dataIndex];
                            return `${d.body_part}: ${formatNumber(d.total_damage)} dmg (${d.hits} hits, avg ${d.avg_damage})`;
                        }
                    }
                }
            }
        }
    });
}

async function loadWeaponsChart() {
    const data = await fetchApi('/api/analytics/weapon-stats');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-weapons');
    destroyChart('weapons');

    charts['weapons'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.slice(0, 10).map(d => cleanName(d.weapon)),
            datasets: [{
                label: 'Total Damage',
                data: data.slice(0, 10).map(d => d.total_damage),
                backgroundColor: CHART_COLORS[1],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        afterLabel: (ctx) => {
                            const d = data[ctx.dataIndex];
                            return `Hits: ${d.hits} | Avg: ${d.avg_damage} | Acc: ${d.accuracy}%`;
                        }
                    }
                }
            }
        }
    });
}

async function loadMobKillsChart() {
    const data = await fetchApi('/api/analytics/mob-kills');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-mob-kills');
    destroyChart('mob-kills');

    charts['mob-kills'] = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: data.slice(0, 8).map(d => cleanName(d.mob_type)),
            datasets: [{
                data: data.slice(0, 8).map(d => d.kills),
                backgroundColor: CHART_COLORS
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'right' },
                tooltip: {
                    callbacks: {
                        label: (ctx) => {
                            const d = data[ctx.dataIndex];
                            return `${cleanName(d.mob_type)}: ${d.kills} kills (${d.loot_rate}% loot)`;
                        }
                    }
                }
            }
        }
    });
}

async function loadDamageTypesOverviewChart() {
    const data = await fetchApi('/api/analytics/damage-by-type');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-damage-types-overview');
    destroyChart('damage-types-overview');

    charts['damage-types-overview'] = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: data.map(d => d.damage_type),
            datasets: [{
                data: data.map(d => d.total_damage),
                backgroundColor: CHART_COLORS.slice(5)
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'right' } }
        }
    });
}

// ==================== Combat Section ====================

async function loadCombatSection() {
    await Promise.all([
        loadDamageComparisonChart(),
        loadWeaponPerformanceChart(),
        loadDamageTypesChart(),
        loadTTKChart(),
        loadAccuracyTimelineChart(),
        loadFightAnalysis()
    ]);
}

async function loadDamageComparisonChart() {
    // Load both damage dealt and damage taken timelines
    const playerParam = selectedPlayer ? `&player=${selectedPlayer}` : '';
    const [dealtData, takenData] = await Promise.all([
        fetchApi(`/api/analytics/dps-timeline?${playerParam}`),
        fetchApi(`/api/analytics/damage-taken?${playerParam}`)
    ]);

    if (!dealtData || dealtData.length === 0) return;

    const ctx = document.getElementById('chart-damage-comparison');
    destroyChart('damage-comparison');

    const takenTimeline = takenData?.timeline || [];
    const takenMap = {};
    takenTimeline.forEach(t => { takenMap[t.time_bucket] = t.damage_taken; });

    charts['damage-comparison'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: dealtData.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [
                {
                    label: 'Damage Dealt',
                    data: dealtData.map(d => d.total_damage),
                    borderColor: CHART_COLORS[0],
                    backgroundColor: CHART_COLORS[0] + '20',
                    fill: true,
                    tension: 0.3
                },
                {
                    label: 'Damage Taken',
                    data: dealtData.map(d => takenMap[d.time_bucket] || 0),
                    borderColor: CHART_COLORS[8],
                    backgroundColor: CHART_COLORS[8] + '20',
                    fill: true,
                    tension: 0.3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { position: 'top' } }
        }
    });
}

async function loadWeaponPerformanceChart() {
    const data = await fetchApi('/api/analytics/weapon-stats');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-weapon-performance');
    destroyChart('weapon-performance');

    charts['weapon-performance'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.slice(0, 8).map(d => cleanName(d.weapon)),
            datasets: [
                {
                    label: 'Avg Damage',
                    data: data.slice(0, 8).map(d => d.avg_damage),
                    backgroundColor: CHART_COLORS[0],
                    borderRadius: 4
                },
                {
                    label: 'Accuracy %',
                    data: data.slice(0, 8).map(d => d.accuracy),
                    backgroundColor: CHART_COLORS[1],
                    borderRadius: 4
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: { y: { beginAtZero: true } }
        }
    });
}

async function loadDamageTypesChart() {
    const data = await fetchApi('/api/analytics/damage-by-type');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-damage-types');
    destroyChart('damage-types');

    charts['damage-types'] = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: data.map(d => d.damage_type),
            datasets: [{
                data: data.map(d => d.total_damage),
                backgroundColor: CHART_COLORS
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'right' } }
        }
    });
}

async function loadTTKChart() {
    const data = await fetchApi('/api/analytics/ttk');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-ttk');
    destroyChart('ttk');

    charts['ttk'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.slice(0, 10).map(d => cleanName(d.mob_type)),
            datasets: [{
                label: 'Avg TTK (seconds)',
                data: data.slice(0, 10).map(d => d.avg_ttk_seconds),
                backgroundColor: CHART_COLORS[3],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        afterLabel: (ctx) => {
                            const d = data[ctx.dataIndex];
                            return `Min: ${d.min_ttk_seconds}s | Max: ${d.max_ttk_seconds}s | Deaths: ${d.deaths}`;
                        }
                    }
                }
            }
        }
    });
}

async function loadAccuracyTimelineChart() {
    const data = await fetchApi('/api/analytics/accuracy-timeline');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-accuracy-timeline');
    destroyChart('accuracy-timeline');

    charts['accuracy-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [{
                label: 'Accuracy %',
                data: data.map(d => d.accuracy),
                borderColor: CHART_COLORS[4],
                backgroundColor: CHART_COLORS[4] + '20',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { min: 0, max: 100 } }
        }
    });
}

async function loadFightAnalysis() {
    const data = await fetchApi('/api/analytics/fight-analysis');
    if (!data) return;

    const statsContainer = document.getElementById('fight-stats');
    const tableContainer = document.getElementById('fights-data');

    if (data.stats) {
        const s = data.stats;
        statsContainer.innerHTML = `
            <div class="fight-stat"><div class="label">Total Fights</div><div class="value">${formatNumber(s.total_fights || 0)}</div></div>
            <div class="fight-stat"><div class="label">Avg Duration</div><div class="value">${s.avg_duration_sec || 0}s</div></div>
            <div class="fight-stat"><div class="label">Dmg Dealt</div><div class="value">${formatNumber(s.total_damage_dealt || 0)}</div></div>
            <div class="fight-stat"><div class="label">Dmg Received</div><div class="value">${formatNumber(s.total_damage_received || 0)}</div></div>
            <div class="fight-stat"><div class="label">Victories</div><div class="value">${s.victories || 0}</div></div>
            <div class="fight-stat"><div class="label">Defeats</div><div class="value">${s.defeats || 0}</div></div>
        `;
    }

    if (data.fights) {
        renderTable(tableContainer, data.fights, ['start_ts', 'duration_ms', 'total_damage_dealt', 'total_damage_received', 'outcome']);
        cachedTableData['fights'] = data.fights;
    }
}

async function loadCombatHits() {
    const limit = document.getElementById('combat-hits-limit')?.value || 50;
    const container = document.getElementById('combat-hits-data');
    container.innerHTML = '<div class="loading">Loading</div>';

    const data = await fetchApi(`/api/combat/hits?limit=${limit}`);
    if (data) {
        renderTable(container, data, ['ts', 'attacker_name', 'target_name', 'damage', 'body_part', 'damage_type']);
        cachedTableData['combat-hits'] = data;
    } else {
        container.innerHTML = '<div class="error-message">Failed to load data</div>';
    }
}

// ==================== Players Section ====================

async function loadPlayersSection() {
    await loadPlayerComparisonChart();
    await loadPlayerLeaderboard();
    if (selectedPlayer) {
        await loadPlayerStats();
    }
}

async function loadPlayerComparisonChart() {
    const data = await fetchApi('/api/analytics/player-comparison');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-player-comparison');
    destroyChart('player-comparison');

    charts['player-comparison'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.player),
            datasets: [
                {
                    label: 'Total Damage',
                    data: data.map(d => d.total_damage),
                    backgroundColor: CHART_COLORS[0],
                    borderRadius: 4
                },
                {
                    label: 'Session DPS',
                    data: data.map(d => d.session_dps * 100), // Scale for visibility
                    backgroundColor: CHART_COLORS[1],
                    borderRadius: 4
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                tooltip: {
                    callbacks: {
                        afterLabel: (ctx) => {
                            const d = data[ctx.dataIndex];
                            return `Hits: ${d.total_hits} | Avg: ${d.avg_damage} | Acc: ${d.accuracy}%`;
                        }
                    }
                }
            }
        }
    });
}

async function loadPlayerLeaderboard() {
    const data = await fetchApi('/api/analytics/player-comparison');
    const container = document.getElementById('player-leaderboard');
    if (data) {
        renderTable(container, data, ['player', 'total_damage', 'total_hits', 'avg_damage', 'accuracy', 'session_dps']);
    }
}

async function loadPlayerStats() {
    if (!selectedPlayer) {
        document.getElementById('player-stats-container').innerHTML =
            '<div class="player-select-prompt"><p>Select a player from the dropdown above to view detailed stats</p></div>';
        document.getElementById('player-charts').style.display = 'none';
        return;
    }

    const data = await fetchApi(`/api/analytics/player-stats?player=${selectedPlayer}`);
    if (!data) return;

    const container = document.getElementById('player-stats-container');
    const combat = data.combat || {};
    const taken = data.damageTaken || {};
    const deaths = data.deaths || {};
    const kills = data.kills || {};

    container.innerHTML = `
        <h3 style="margin-bottom: 1rem; color: var(--accent);">${selectedPlayer} Stats</h3>
        <div class="player-stats-grid">
            <div class="player-stat-group">
                <h4>Combat</h4>
                <div class="player-stat-item"><span class="label">Total Damage</span><span class="value">${formatNumber(combat.total_damage_dealt || 0)}</span></div>
                <div class="player-stat-item"><span class="label">Hits Landed</span><span class="value">${formatNumber(combat.hits_landed || 0)}</span></div>
                <div class="player-stat-item"><span class="label">Avg Damage</span><span class="value">${combat.avg_damage || 0}</span></div>
                <div class="player-stat-item"><span class="label">Max Hit</span><span class="value">${combat.max_hit || 0}</span></div>
                <div class="player-stat-item"><span class="label">Accuracy</span><span class="value">${combat.accuracy || 0}%</span></div>
            </div>
            <div class="player-stat-group">
                <h4>Defense</h4>
                <div class="player-stat-item"><span class="label">Damage Taken</span><span class="value">${formatNumber(taken.total_damage_taken || 0)}</span></div>
                <div class="player-stat-item"><span class="label">Times Hit</span><span class="value">${formatNumber(taken.times_hit || 0)}</span></div>
                <div class="player-stat-item"><span class="label">Avg Dmg Taken</span><span class="value">${taken.avg_damage_taken || 0}</span></div>
                <div class="player-stat-item"><span class="label">Deaths</span><span class="value">${deaths.total_deaths || 0}</span></div>
            </div>
            <div class="player-stat-group">
                <h4>Kills</h4>
                <div class="player-stat-item"><span class="label">Mobs Killed</span><span class="value">${formatNumber(kills.total_kills || 0)}</span></div>
                <div class="player-stat-item"><span class="label">Unique Targets</span><span class="value">${combat.unique_targets || 0}</span></div>
            </div>
        </div>
    `;

    // Show player charts
    document.getElementById('player-charts').style.display = 'grid';

    // Load player-specific charts
    if (data.dpsTimeline && data.dpsTimeline.length > 0) {
        loadPlayerDpsChart(data.dpsTimeline);
    }
    if (data.weapons && data.weapons.length > 0) {
        loadPlayerWeaponsChart(data.weapons);
    }
}

function loadPlayerDpsChart(data) {
    const ctx = document.getElementById('chart-player-dps');
    destroyChart('player-dps');

    charts['player-dps'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [{
                label: 'DPS',
                data: data.map(d => d.dps),
                borderColor: CHART_COLORS[0],
                backgroundColor: CHART_COLORS[0] + '20',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

function loadPlayerWeaponsChart(data) {
    const ctx = document.getElementById('chart-player-weapons');
    destroyChart('player-weapons');

    charts['player-weapons'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => cleanName(d.weapon)),
            datasets: [{
                label: 'Damage',
                data: data.map(d => d.damage),
                backgroundColor: CHART_COLORS[1],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        }
    });
}

// ==================== Endurance Section ====================

async function loadEnduranceSection() {
    const stats = await fetchApi('/api/analytics/endurance-stats');
    if (stats) {
        document.getElementById('stat-endurance-sessions').textContent = formatNumber(stats.total_sessions || 0);
        document.getElementById('stat-endurance-winrate').textContent = (stats.winRate || 0) + '%';
        document.getElementById('stat-endurance-waves').textContent = stats.avg_waves || '-';
        document.getElementById('stat-endurance-best').textContent = stats.best_wave || '-';

        loadEnduranceOutcomesChart(stats.outcomes);
        loadPerkPopularityChart(stats.perks);
    }

    loadWaveDifficultyChart();
}

function loadEnduranceOutcomesChart(outcomes) {
    if (!outcomes || outcomes.length === 0) return;

    const ctx = document.getElementById('chart-endurance-outcomes');
    destroyChart('endurance-outcomes');

    const outcomeMap = {};
    outcomes.forEach(o => {
        outcomeMap[o.outcome] = (outcomeMap[o.outcome] || 0) + o.count;
    });

    charts['endurance-outcomes'] = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: Object.keys(outcomeMap),
            datasets: [{
                data: Object.values(outcomeMap),
                backgroundColor: [CHART_COLORS[0], CHART_COLORS[8], CHART_COLORS[3]]
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom' } }
        }
    });
}

async function loadWaveDifficultyChart() {
    const data = await fetchApi('/api/endurance/waves?limit=500');
    if (!data || data.length === 0) return;

    const waveMap = {};
    data.forEach(w => {
        const wn = w.wave_number;
        if (!waveMap[wn]) waveMap[wn] = { count: 0, duration: 0 };
        waveMap[wn].count++;
        waveMap[wn].duration += w.duration_ms || 0;
    });

    const waveNumbers = Object.keys(waveMap).map(Number).sort((a, b) => a - b);

    const ctx = document.getElementById('chart-wave-difficulty');
    destroyChart('wave-difficulty');

    charts['wave-difficulty'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: waveNumbers.map(w => `Wave ${w}`),
            datasets: [{
                label: 'Avg Duration (s)',
                data: waveNumbers.map(w => (waveMap[w].duration / waveMap[w].count / 1000).toFixed(1)),
                borderColor: CHART_COLORS[5],
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

function loadPerkPopularityChart(perks) {
    if (!perks || perks.length === 0) return;

    const ctx = document.getElementById('chart-perk-popularity');
    destroyChart('perk-popularity');

    charts['perk-popularity'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: perks.map(p => p.perk_name),
            datasets: [{
                label: 'Picks',
                data: perks.map(p => p.picks),
                backgroundColor: CHART_COLORS[6],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        }
    });
}

// ==================== Dungeons Section ====================

async function loadDungeonsSection() {
    const stats = await fetchApi('/api/analytics/dungeon-stats');
    if (stats) {
        document.getElementById('stat-dungeon-runs').textContent = formatNumber(stats.total_runs || 0);
        document.getElementById('stat-dungeon-completion').textContent = (stats.completionRate || 0) + '%';
        document.getElementById('stat-dungeon-duration').textContent = (stats.avg_duration_min || 0) + ' min';
        document.getElementById('stat-dungeon-deaths').textContent = stats.avg_deaths || '-';

        loadDungeonRunsChart(stats.byDungeon);
        loadDungeonCompletionChart(stats.byDungeon);
    }
}

function loadDungeonRunsChart(byDungeon) {
    if (!byDungeon || byDungeon.length === 0) return;

    const ctx = document.getElementById('chart-dungeon-runs');
    destroyChart('dungeon-runs');

    charts['dungeon-runs'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: byDungeon.map(d => d.dungeon_id || 'Unknown'),
            datasets: [{
                label: 'Runs',
                data: byDungeon.map(d => d.runs),
                backgroundColor: CHART_COLORS[1],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

function loadDungeonCompletionChart(byDungeon) {
    if (!byDungeon || byDungeon.length === 0) return;

    const ctx = document.getElementById('chart-dungeon-completion');
    destroyChart('dungeon-completion');

    charts['dungeon-completion'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: byDungeon.map(d => d.dungeon_id || 'Unknown'),
            datasets: [{
                label: 'Completion Rate %',
                data: byDungeon.map(d => d.completion_rate),
                backgroundColor: CHART_COLORS[0],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { min: 0, max: 100 } }
        }
    });
}

async function loadDungeonRuns() {
    const limit = document.getElementById('dungeons-limit')?.value || 50;
    const container = document.getElementById('dungeons-data');
    container.innerHTML = '<div class="loading">Loading</div>';

    const data = await fetchApi(`/api/dungeons/runs?limit=${limit}`);
    if (data) {
        renderTable(container, data, ['start_ts', 'player_name', 'dungeon_id', 'outcome', 'duration_ms', 'kills', 'deaths']);
        cachedTableData['dungeons'] = data;
    } else {
        container.innerHTML = '<div class="error-message">Failed to load data</div>';
    }
}

// ==================== Performance Section ====================

async function loadPerformanceSection() {
    const data = await fetchApi('/api/analytics/performance');
    if (!data) return;

    if (data.summary) {
        const s = data.summary;
        document.getElementById('stat-avg-tps').textContent = s.avg_tps || '-';
        document.getElementById('stat-min-tps').textContent = s.min_tps || '-';
        document.getElementById('stat-avg-memory').textContent = (s.avg_memory_mb || 0) + ' MB';
        document.getElementById('stat-avg-entities').textContent = formatNumber(s.avg_entities || 0);
    }

    loadTpsChart(data.tpsTimeline);
    loadMemoryChart(data.memoryTimeline);
    loadEntityChart(data.entityTimeline);
}

function loadTpsChart(data) {
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-tps-timeline');
    destroyChart('tps-timeline');

    charts['tps-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [
                {
                    label: 'Avg TPS',
                    data: data.map(d => d.avg_tps),
                    borderColor: CHART_COLORS[0],
                    backgroundColor: CHART_COLORS[0] + '20',
                    fill: true,
                    tension: 0.3
                },
                {
                    label: 'Min TPS',
                    data: data.map(d => d.min_tps),
                    borderColor: CHART_COLORS[8],
                    borderDash: [5, 5],
                    tension: 0.3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'top' } },
            scales: { y: { min: 0, max: 20, title: { display: true, text: 'TPS' } } }
        }
    });
}

function loadMemoryChart(data) {
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-memory-timeline');
    destroyChart('memory-timeline');

    charts['memory-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [{
                label: 'Memory (MB)',
                data: data.map(d => d.avg_memory_mb),
                borderColor: CHART_COLORS[4],
                backgroundColor: CHART_COLORS[4] + '20',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

function loadEntityChart(data) {
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-entity-timeline');
    destroyChart('entity-timeline');

    charts['entity-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => formatTimeLabel(d.time_bucket)),
            datasets: [{
                label: 'Entities',
                data: data.map(d => d.avg_entities),
                borderColor: CHART_COLORS[5],
                backgroundColor: CHART_COLORS[5] + '20',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

// ==================== Spatial Section ====================

async function loadSpatialSection() {
    const stats = await fetchApi('/api/analytics/room-stats');
    if (stats) {
        loadRoomVisitsChart(stats.visits);
        loadDeathsByRoomChart(stats.deaths);
    }
}

function loadRoomVisitsChart(visits) {
    if (!visits || visits.length === 0) return;

    const ctx = document.getElementById('chart-room-visits');
    destroyChart('room-visits');

    charts['room-visits'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: visits.map(v => v.room || 'Unknown'),
            datasets: [{
                label: 'Visits',
                data: visits.map(v => v.visits),
                backgroundColor: CHART_COLORS[7],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        }
    });
}

function loadDeathsByRoomChart(deaths) {
    if (!deaths || deaths.length === 0) return;

    const ctx = document.getElementById('chart-deaths-by-room');
    destroyChart('deaths-by-room');

    charts['deaths-by-room'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: deaths.map(d => d.room || 'Unknown'),
            datasets: [{
                label: 'Deaths',
                data: deaths.map(d => d.deaths),
                backgroundColor: CHART_COLORS[8],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        }
    });
}

async function loadHeatmaps() {
    const type = document.getElementById('spatial-heatmap-type')?.value || '';
    const limit = document.getElementById('spatial-heatmaps-limit')?.value || 500;
    const container = document.getElementById('spatial-heatmaps-data');
    container.innerHTML = '<div class="loading">Loading</div>';

    let url = `/api/spatial/heatmaps?limit=${limit}`;
    if (type) url += `&type=${type}`;

    const data = await fetchApi(url);
    if (data) {
        renderTable(container, data, ['ts', 'heatmap_type', 'room', 'x', 'y', 'z', 'count']);
        cachedTableData['heatmaps'] = data;
    } else {
        container.innerHTML = '<div class="error-message">Failed to load data</div>';
    }
}

// ==================== Economy Section ====================

async function loadEconomySection() {
    await Promise.all([
        loadEconomyKillsChart(),
        loadLootRateChart(),
        loadDropsTimelineChart()
    ]);
}

async function loadEconomyKillsChart() {
    const data = await fetchApi('/api/analytics/mob-kills');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-economy-kills');
    destroyChart('economy-kills');

    charts['economy-kills'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.slice(0, 10).map(d => cleanName(d.mob_type)),
            datasets: [{
                label: 'Kills',
                data: data.slice(0, 10).map(d => d.kills),
                backgroundColor: CHART_COLORS[0],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        }
    });
}

async function loadLootRateChart() {
    const data = await fetchApi('/api/analytics/loot-rates');
    if (!data || data.length === 0) return;

    const ctx = document.getElementById('chart-loot-rate');
    destroyChart('loot-rate');

    charts['loot-rate'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.slice(0, 10).map(d => cleanName(d.mob_type)),
            datasets: [{
                label: 'Loot Rate %',
                data: data.slice(0, 10).map(d => d.loot_rate),
                backgroundColor: CHART_COLORS[3],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: { legend: { display: false } },
            scales: { x: { min: 0, max: 100 } }
        }
    });
}

async function loadDropsTimelineChart() {
    const data = await fetchApi('/api/economy/drops?limit=500');
    if (!data || data.length === 0) return;

    const hourMap = {};
    data.forEach(d => {
        const hour = new Date(d.ts).toISOString().slice(0, 13);
        hourMap[hour] = (hourMap[hour] || 0) + (d.item_count || 1);
    });

    const hours = Object.keys(hourMap).sort();

    const ctx = document.getElementById('chart-drops-timeline');
    destroyChart('drops-timeline');

    charts['drops-timeline'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: hours.map(h => formatTimeLabel(h)),
            datasets: [{
                label: 'Items Dropped',
                data: hours.map(h => hourMap[h]),
                borderColor: CHART_COLORS[5],
                backgroundColor: CHART_COLORS[5] + '20',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } }
        }
    });
}

async function loadEconomyDrops() {
    const limit = document.getElementById('economy-drops-limit')?.value || 100;
    const container = document.getElementById('economy-drops-data');
    container.innerHTML = '<div class="loading">Loading</div>';

    const data = await fetchApi(`/api/economy/drops?limit=${limit}`);
    if (data) {
        renderTable(container, data, ['ts', 'mob_type', 'item_id', 'item_count', 'room']);
        cachedTableData['drops'] = data;
    } else {
        container.innerHTML = '<div class="error-message">Failed to load data</div>';
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

    container.innerHTML = '<div class="loading">Executing</div>';

    try {
        const response = await fetch('/api/query', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sql })
        });
        const data = await response.json();

        if (data.error) {
            container.innerHTML = `<div class="error-message">${data.error}</div>`;
        } else {
            renderTable(container, data);
            lastQueryData = data;
        }
    } catch (error) {
        container.innerHTML = `<div class="error-message">Query failed: ${error.message}</div>`;
    }
}

function exportQueryResults() {
    if (lastQueryData && lastQueryData.length > 0) {
        downloadCSV(lastQueryData, 'query_results.csv');
    }
}

// ==================== Data Export ====================

/**
 * Gap 1: Export chart as PNG using Chart.js toBase64Image
 */
function exportChartPng(chartKey) {
    const chart = charts[chartKey];
    if (!chart) {
        showToast('Chart not available. Please load the data first.', 'error');
        return;
    }

    try {
        // Get base64 image from Chart.js
        const base64Image = chart.toBase64Image('image/png', 1.0);

        // Create download link
        const link = document.createElement('a');
        const timestamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
        link.download = `chart_${chartKey}_${timestamp}.png`;
        link.href = base64Image;
        link.click();

        showToast(`Chart exported as PNG`, 'success');
    } catch (error) {
        console.error('PNG export failed:', error);
        showToast('Failed to export chart as PNG', 'error');
    }
}

/**
 * Show a toast notification
 */
function showToast(message, type = 'info') {
    // Remove existing toast
    const existing = document.querySelector('.toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    // Animate in
    setTimeout(() => toast.classList.add('show'), 10);

    // Remove after 3 seconds
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

function exportData(dataType) {
    const data = cachedTableData[dataType];
    if (data && data.length > 0) {
        downloadCSV(data, `${dataType}_export.csv`);
    } else {
        showToast('No data to export. Please load the data first.', 'error');
    }
}

function downloadCSV(data, filename) {
    if (!data || data.length === 0) return;

    const headers = Object.keys(data[0]);
    const csvContent = [
        headers.join(','),
        ...data.map(row =>
            headers.map(h => {
                let val = row[h];
                if (val === null || val === undefined) return '';
                if (typeof val === 'object') val = JSON.stringify(val);
                val = String(val).replace(/"/g, '""');
                return `"${val}"`;
            }).join(',')
        )
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
}

// ==================== Utilities ====================

function destroyChart(name) {
    if (charts[name]) {
        charts[name].destroy();
        delete charts[name];
    }
}

/**
 * Gap 6: Shows a "no data available" state on a chart canvas
 */
function showNoDataState(canvas, message = 'No data available') {
    if (!canvas) return;

    const container = canvas.closest('.chart-container');
    if (container) {
        // Add no-data overlay
        let overlay = container.querySelector('.no-data-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.className = 'no-data-overlay';
            container.appendChild(overlay);
        }
        overlay.innerHTML = `<div class="no-data-icon">📊</div><div class="no-data-message">${message}</div>`;
        overlay.style.display = 'flex';
    }
}

/**
 * Gap 6: Hides the no-data overlay when data becomes available
 */
function hideNoDataState(canvas) {
    if (!canvas) return;

    const container = canvas.closest('.chart-container');
    if (container) {
        const overlay = container.querySelector('.no-data-overlay');
        if (overlay) {
            overlay.style.display = 'none';
        }
    }
}

function renderTable(container, data, columns = null) {
    if (!data || data.length === 0) {
        container.innerHTML = '<div class="empty-state">No data found</div>';
        return;
    }

    if (!columns) columns = Object.keys(data[0]);

    const headers = columns.map(col => `<th>${formatColumnName(col)}</th>`).join('');
    const rows = data.map(row => {
        const cells = columns.map(col => `<td>${formatValue(row[col], col)}</td>`).join('');
        return `<tr>${cells}</tr>`;
    }).join('');

    container.innerHTML = `<table><thead><tr>${headers}</tr></thead><tbody>${rows}</tbody></table>`;
}

function formatNumber(num) {
    if (num === null || num === undefined) return '-';
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
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

    const isTimestampColumn = /(?:^ts$|_ts$|^start_ts$|^end_ts$|timestamp|_time$|time_bucket|_kill$)/.test(column);
    if (isTimestampColumn && (typeof value === 'string' || typeof value === 'number')) {
        try {
            const date = new Date(value);
            if (!isNaN(date.getTime()) && date.getFullYear() > 1980) {
                return date.toLocaleString();
            }
        } catch (e) {}
    }

    if (typeof value === 'object') return JSON.stringify(value);
    if (typeof value === 'number') {
        if (Number.isInteger(value)) return formatNumber(value);
        return value.toFixed(2);
    }
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';

    return String(value);
}

function formatTimeLabel(ts) {
    if (!ts) return '';
    const date = new Date(ts);
    if (isNaN(date.getTime())) return ts;
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function cleanName(name) {
    if (!name) return 'Unknown';
    return name
        .replace('minecraft.', '')
        .replace('minecraft:', '')
        .replace('entity.', '')
        .replace(/_/g, ' ')
        .split('.').pop();
}

function updateRefreshTime() {
    const el = document.getElementById('refresh-time');
    if (el) el.textContent = 'Last updated: ' + new Date().toLocaleTimeString();
}

// ==================== Arenas Section (DD36) ====================

function onArenaTemplateChange() {
    selectedArenaTemplate = document.getElementById('arena-template-filter')?.value || '';
    if (selectedArenaTemplate) {
        loadArenaMetrics();
    }
}

async function loadArenaSection() {
    // Load template list first
    await loadArenaTemplateList();

    if (selectedArenaTemplate) {
        await loadArenaMetrics();
    }
}

async function loadArenaTemplateList() {
    const data = await fetchApi('/api/analytics/arena/templates');
    const select = document.getElementById('arena-template-filter');

    if (data && select) {
        const currentValue = select.value;
        select.innerHTML = '<option value="">Select Template...</option>';

        (data.templates || data || []).forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.id || t;
            opt.textContent = t.id || t;
            select.appendChild(opt);
        });

        if (currentValue) select.value = currentValue;
    }
}

async function loadArenaMetrics() {
    if (!selectedArenaTemplate) return;

    await Promise.all([
        loadArenaBuildMetrics(),
        loadArenaFailureRateByTemplate(), // Gap 3
        loadArenaPerformance(),
        loadArenaSpawnHeatmap(),
        loadArenaDeathHeatmap(),
        loadArenaWaveCorrelation()
    ]);
}

async function loadArenaBuildMetrics() {
    const data = await fetchApi(`/api/analytics/arena/build-metrics?templateId=${selectedArenaTemplate}`);
    if (!data) return;

    // Update stat cards
    document.getElementById('stat-arena-builds').textContent = formatNumber(data.totalBuilds || 0);
    document.getElementById('stat-arena-success').textContent =
        data.totalBuilds > 0 ? ((100 - (data.failureRate || 0)).toFixed(1) + '%') : '-';
    document.getElementById('stat-arena-avgtime').textContent =
        data.avgBuildTimeMs ? (data.avgBuildTimeMs.toFixed(0) + 'ms') : '-';
    document.getElementById('stat-arena-p95').textContent =
        data.p95BuildTimeMs ? (data.p95BuildTimeMs.toFixed(0) + 'ms') : '-';

    // Build chart
    const ctx = document.getElementById('chart-arena-builds');
    destroyChart('arena-builds');

    charts['arena-builds'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: ['Successful', 'Failed'],
            datasets: [{
                label: 'Builds',
                data: [data.successfulBuilds || 0, data.failedBuilds || 0],
                backgroundColor: [CHART_COLORS[0], CHART_COLORS[8]],
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                title: { display: true, text: `Template: ${selectedArenaTemplate}` }
            }
        }
    });
}

async function loadArenaPerformance() {
    const data = await fetchApi(`/api/analytics/arena/performance?templateId=${selectedArenaTemplate}`);
    if (!data || !data.samples || data.samples.length === 0) return;

    const ctx = document.getElementById('chart-arena-performance');
    destroyChart('arena-performance');

    charts['arena-performance'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.samples.map(s => formatTimeLabel(s.timestamp)),
            datasets: [
                {
                    label: 'MSPT',
                    data: data.samples.map(s => s.mspt),
                    borderColor: CHART_COLORS[4],
                    backgroundColor: CHART_COLORS[4] + '20',
                    fill: true,
                    tension: 0.3,
                    yAxisID: 'y'
                },
                {
                    label: 'TPS',
                    data: data.samples.map(s => s.tps),
                    borderColor: CHART_COLORS[0],
                    borderDash: [5, 5],
                    tension: 0.3,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { position: 'top' } },
            scales: {
                y: { type: 'linear', position: 'left', title: { display: true, text: 'MSPT' } },
                y1: { type: 'linear', position: 'right', title: { display: true, text: 'TPS' }, min: 0, max: 20, grid: { drawOnChartArea: false } }
            }
        }
    });
}

/**
 * Gap 3: Loads failure rate comparison across all templates
 */
async function loadArenaFailureRateByTemplate() {
    const data = await fetchApi('/api/analytics/arena/templates-failure-rate');
    if (!data || !data.templates || data.templates.length === 0) {
        // Show "no data" state
        const ctx = document.getElementById('chart-arena-failure-rate');
        destroyChart('arena-failure-rate');
        showNoDataState(ctx, 'No template failure data available');
        return;
    }

    const ctx = document.getElementById('chart-arena-failure-rate');
    destroyChart('arena-failure-rate');

    // Sort by failure rate descending to show problematic templates first
    const sorted = [...data.templates].sort((a, b) => b.failureRate - a.failureRate);

    // Color code: green for low failure, yellow for medium, red for high
    const colors = sorted.map(t => {
        if (t.failureRate <= 5) return CHART_COLORS[0];   // Green - healthy
        if (t.failureRate <= 20) return CHART_COLORS[3];  // Yellow - warning
        return CHART_COLORS[8];                            // Red - critical
    });

    charts['arena-failure-rate'] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: sorted.map(t => t.templateId),
            datasets: [{
                label: 'Failure Rate %',
                data: sorted.map(t => t.failureRate),
                backgroundColor: colors,
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        afterLabel: (ctx) => {
                            const t = sorted[ctx.dataIndex];
                            return `Builds: ${t.totalBuilds} | Failed: ${t.failedBuilds}`;
                        }
                    }
                }
            },
            scales: {
                x: { min: 0, max: 100, title: { display: true, text: 'Failure Rate %' } }
            }
        }
    });
}

async function loadArenaSpawnHeatmap() {
    const data = await fetchApi(`/api/analytics/arena/spawn-heatmap?templateId=${selectedArenaTemplate}`);
    renderHeatmapChart('chart-arena-spawn-heatmap', 'arena-spawn-heatmap', data, 'Spawn');
}

async function loadArenaDeathHeatmap() {
    const data = await fetchApi(`/api/analytics/arena/death-heatmap?templateId=${selectedArenaTemplate}`);
    renderHeatmapChart('chart-arena-death-heatmap', 'arena-death-heatmap', data, 'Death');
}

function renderHeatmapChart(canvasId, chartKey, data, label) {
    if (!data || !data.grid) return;

    const ctx = document.getElementById(canvasId);
    destroyChart(chartKey);

    // Convert 2D grid to flat data for visualization
    const gridData = [];
    const labels = [];
    for (let x = 0; x < data.gridSizeX; x++) {
        let rowSum = 0;
        for (let z = 0; z < data.gridSizeZ; z++) {
            rowSum += data.grid[x]?.[z] || 0;
        }
        gridData.push(rowSum);
        labels.push(`X${x}`);
    }

    charts[chartKey] = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: `${label} Events`,
                data: gridData,
                backgroundColor: label === 'Spawn' ? CHART_COLORS[0] : CHART_COLORS[8],
                borderRadius: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        afterLabel: () => `Total: ${data.totalEvents}`
                    }
                }
            },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

async function loadArenaWaveCorrelation() {
    const data = await fetchApi(`/api/analytics/arena/wave-correlation?templateId=${selectedArenaTemplate}`);
    if (!data || !data.waves || data.waves.length === 0) return;

    // Wave completion chart
    const ctx = document.getElementById('chart-arena-waves');
    destroyChart('arena-waves');

    charts['arena-waves'] = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.waves.map(w => `Wave ${w.waveNumber}`),
            datasets: [
                {
                    label: 'Completion Rate %',
                    data: data.waves.map(w => w.completionRate),
                    borderColor: CHART_COLORS[0],
                    backgroundColor: CHART_COLORS[0] + '20',
                    fill: true,
                    tension: 0.3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        afterLabel: (ctx) => {
                            const w = data.waves[ctx.dataIndex];
                            return `Attempts: ${w.attempts} | Completions: ${w.completions} | Avg Duration: ${w.avgDurationSeconds.toFixed(1)}s`;
                        }
                    }
                }
            },
            scales: {
                y: { min: 0, max: 100, title: { display: true, text: 'Completion %' } }
            }
        }
    });

    // Wave data table
    const container = document.getElementById('arena-wave-data');
    renderTable(container, data.waves, ['waveNumber', 'attempts', 'completions', 'completionRate', 'avgDurationSeconds']);
    cachedTableData['arena-waves'] = data.waves;
}
