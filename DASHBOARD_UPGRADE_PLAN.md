# DevMod Telemetry Dashboard Upgrade Plan

## Current State Analysis

The current dashboard provides:
- Basic summary statistics (row counts, DB size)
- Raw data table views for each telemetry category
- Custom SQL query execution

**Limitations identified:**
- No visual charts or graphs
- No aggregations or trend analysis
- No time-series visualization
- No filtering by player/room/time range
- No comparative analytics
- Data shown as raw tables only

---

## Upgrade Goals

Transform the dashboard from a basic data viewer into a **comprehensive analytics platform** for game design insights.

---

## Phase 1: Analytics API Endpoints (Backend)

### 1.1 Combat Analytics APIs

| Endpoint | Description | SQL Aggregation |
|----------|-------------|-----------------|
| `/api/analytics/combat/dps-by-weapon` | DPS ranking by weapon | AVG damage, hit rate, total damage per weapon |
| `/api/analytics/combat/damage-distribution` | Body part damage distribution | GROUP BY body_part, SUM damage |
| `/api/analytics/combat/ttk-by-mob` | Time-to-kill by mob type | AVG ttk_spawn_ms per target_type |
| `/api/analytics/combat/hits-timeline` | Hits over time (hourly buckets) | DATE_TRUNC('hour', ts), COUNT(*) |
| `/api/analytics/combat/accuracy` | Hit/miss ratio over time | hits vs misses percentage |
| `/api/analytics/combat/damage-sources` | Damage by source type | GROUP BY damage_type |

### 1.2 Endurance Analytics APIs

| Endpoint | Description |
|----------|-------------|
| `/api/analytics/endurance/session-outcomes` | Win/loss/abandon rates |
| `/api/analytics/endurance/wave-difficulty` | Average deaths/duration per wave number |
| `/api/analytics/endurance/perk-winrate` | Perk correlation with session outcome |
| `/api/analytics/endurance/progression-curve` | Wave completion rate by session |
| `/api/analytics/endurance/boss-stats` | Boss kill times, damage taken |

### 1.3 Dungeon Analytics APIs

| Endpoint | Description |
|----------|-------------|
| `/api/analytics/dungeons/completion-rate` | Success rate by dungeon_id |
| `/api/analytics/dungeons/avg-duration` | Average completion time |
| `/api/analytics/dungeons/death-hotspots` | Rooms with most deaths |
| `/api/analytics/dungeons/player-progression` | Runs over time per player |

### 1.4 Spatial Analytics APIs

| Endpoint | Description |
|----------|-------------|
| `/api/analytics/spatial/room-flow` | Transition matrix (room A → room B counts) |
| `/api/analytics/spatial/heatmap-aggregate` | Aggregated position data for visualization |
| `/api/analytics/spatial/death-locations` | Death coordinates with counts |
| `/api/analytics/spatial/time-in-room` | Average time spent per room |

### 1.5 Performance Analytics APIs

| Endpoint | Description |
|----------|-------------|
| `/api/analytics/performance/tps-timeline` | TPS over time |
| `/api/analytics/performance/memory-usage` | Memory trends |
| `/api/analytics/performance/entity-counts` | Entity count vs TPS correlation |

---

## Phase 2: Charting Library Integration (Frontend)

### 2.1 Library Selection: Chart.js

**Why Chart.js:**
- No build step required (CDN or bundled)
- MIT license
- Small footprint (~60KB)
- Supports: line, bar, pie, doughnut, radar, scatter
- Good for time-series data

### 2.2 Chart Components to Add

| Chart Type | Use Case | Data Source |
|------------|----------|-------------|
| **Line Chart** | DPS timeline, TPS over time, hits/hour | Time-series APIs |
| **Bar Chart** | Weapon rankings, perk popularity, TTK by mob | Aggregation APIs |
| **Pie/Doughnut** | Damage type distribution, body part hits | Distribution APIs |
| **Scatter Plot** | Heatmap visualization (2D positions) | Spatial APIs |
| **Radar Chart** | Player combat profile comparison | Multi-metric aggregation |
| **Stacked Bar** | Session outcomes over time | Endurance APIs |

---

## Phase 3: Enhanced UI Components

### 3.1 Global Filters Panel

```
┌─────────────────────────────────────────────────────┐
│ Time Range: [Last Hour ▼] [Custom: From ___ To ___] │
│ Player: [All Players ▼]  Room: [All Rooms ▼]        │
│ [Apply Filters]                                      │
└─────────────────────────────────────────────────────┘
```

### 3.2 Overview Dashboard Redesign

```
┌──────────────────────────────────────────────────────────────┐
│  📊 DEVMOD ANALYTICS                            [Connected]  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐            │
│  │ Combat  │ │Endurance│ │ Dungeons│ │  TPS    │            │
│  │ 1,234   │ │   45    │ │   12    │ │  19.8   │            │
│  │ hits/hr │ │sessions │ │  runs   │ │ avg     │            │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘            │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              COMBAT HITS OVER TIME (24h)              │  │
│  │  ▲                                                    │  │
│  │  │    ╭──╮                        ╭───╮               │  │
│  │  │   ╱    ╲     ╭─────╮          ╱     ╲              │  │
│  │  │──╱      ╲───╱       ╲────────╱       ╲────         │  │
│  │  └──────────────────────────────────────────────────▶ │  │
│  │    00:00   06:00   12:00   18:00   24:00              │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────┐ ┌────────────────────────────┐  │
│  │   WEAPON RANKINGS      │ │   DAMAGE BY BODY PART      │  │
│  │   ████████████ Sword   │ │      [PIE CHART]           │  │
│  │   ████████     Bow     │ │   HEAD: 35%                │  │
│  │   █████        Axe     │ │   BODY: 45%                │  │
│  │   ███          Fist    │ │   LEGS: 20%                │  │
│  └────────────────────────┘ └────────────────────────────┘  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.3 Combat Analytics Page

- **DPS Leaderboard**: Bar chart of weapon damage rankings
- **Accuracy Timeline**: Line chart showing hit% over time
- **TTK Analysis**: Box plot or bar chart of time-to-kill by mob type
- **Damage Breakdown**: Pie chart of damage sources

### 3.4 Endurance Analytics Page

- **Session Outcomes**: Stacked bar (wins/losses/abandons per day)
- **Wave Difficulty Curve**: Line chart showing avg deaths per wave
- **Perk Meta**: Bar chart of most-picked perks with win-rate overlay
- **Boss Performance**: Table with sortable columns (kill time, damage taken)

### 3.5 Spatial Analytics Page

- **Room Flow Sankey**: Visualization of player movement patterns
- **Death Heatmap**: 2D scatter plot of death positions
- **Time in Room**: Horizontal bar chart

---

## Phase 4: Export & Reporting

### 4.1 Export Features

| Format | Description |
|--------|-------------|
| **CSV** | Export current view to CSV |
| **JSON** | Raw data export |
| **PNG** | Export chart as image |

### 4.2 Report Generation

- Generate summary PDF report with key metrics
- Email scheduling (future consideration)

---

## Implementation Priority

### Sprint 1: Core Analytics (High Value)
1. Add Chart.js to dashboard
2. Implement combat analytics endpoints
3. Build hits timeline chart
4. Build weapon rankings bar chart
5. Add time range filter

### Sprint 2: Endurance & Dungeon Analytics
1. Session outcomes chart
2. Wave difficulty analysis
3. Dungeon completion rates
4. Perk meta visualization

### Sprint 3: Spatial & Performance
1. Room flow visualization
2. TPS timeline chart
3. Entity count correlation
4. Heatmap scatter plot

### Sprint 4: Polish & Export
1. CSV/JSON export
2. Chart image export
3. Auto-refresh option
4. Mobile-responsive layout

---

## File Changes Summary

### Backend (Java)
- `TelemetryDashboardServer.java`: Add ~15 new analytics endpoints
- Create `AnalyticsQueries.java`: SQL aggregation query builder

### Frontend (HTML/CSS/JS)
- `index.html`: Add chart containers, filter panel
- `style.css`: Chart styling, responsive grid
- `app.js`: Chart.js integration, analytics data fetching
- Add `chart.min.js` (CDN or bundled)

---

## Example Analytics SQL Queries

### Hits Per Hour (24h)
```sql
SELECT
    DATE_TRUNC('hour', ts) as hour,
    COUNT(*) as hits
FROM combat_hits
WHERE ts >= NOW() - INTERVAL '24 hours'
GROUP BY DATE_TRUNC('hour', ts)
ORDER BY hour
```

### Weapon DPS Ranking
```sql
SELECT
    JSON_EXTRACT_STRING(attacker_state, '$.weapon') as weapon,
    COUNT(*) as hits,
    SUM(damage) as total_damage,
    AVG(damage) as avg_damage,
    SUM(damage) / GREATEST(1,
        EXTRACT(EPOCH FROM MAX(ts) - MIN(ts)) / 60) as dpm
FROM combat_hits
WHERE ts >= NOW() - INTERVAL '1 hour'
    AND attacker_id IS NOT NULL
GROUP BY weapon
HAVING hits > 5
ORDER BY total_damage DESC
LIMIT 10
```

### Session Outcomes
```sql
SELECT
    DATE_TRUNC('day', start_ts) as day,
    outcome,
    COUNT(*) as count
FROM endurance_sessions
WHERE start_ts >= NOW() - INTERVAL '7 days'
GROUP BY DATE_TRUNC('day', start_ts), outcome
ORDER BY day, outcome
```

### Wave Difficulty
```sql
SELECT
    wave_number,
    AVG(duration_ms) as avg_duration,
    AVG(mobs_spawned) as avg_mobs,
    COUNT(*) as attempts,
    SUM(CASE WHEN event_type = 'wave_failed' THEN 1 ELSE 0 END) as failures
FROM endurance_waves
GROUP BY wave_number
ORDER BY wave_number
```

### Room Transition Matrix
```sql
SELECT
    from_room,
    room as to_room,
    COUNT(*) as transitions
FROM (
    SELECT
        room,
        LAG(room) OVER (PARTITION BY player_id ORDER BY ts) as from_room
    FROM spatial_room_transitions
    WHERE ts >= NOW() - INTERVAL '24 hours'
) sub
WHERE from_room IS NOT NULL AND from_room != room
GROUP BY from_room, to_room
ORDER BY transitions DESC
LIMIT 50
```

---

## Estimated Effort

| Phase | Complexity | Time Estimate |
|-------|------------|---------------|
| Phase 1 (Backend APIs) | Medium | Core work |
| Phase 2 (Charts) | Medium | Core work |
| Phase 3 (UI) | High | Enhancement |
| Phase 4 (Export) | Low | Polish |

---

## Next Steps

To implement this plan:
1. Confirm scope and priorities
2. Start with Sprint 1: Core Analytics
3. Iterate based on feedback

Type **"proceed"** to begin implementation of Sprint 1.
