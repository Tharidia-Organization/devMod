# Traceability Matrix

> **Audit Date**: 2024-12-23

---

## Feature → Entrypoint → Components → Telemetry → UI

### Arena System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Create Arena | `/arena create <id>` | ArenaCommands, TemplateArenaBuilder, PolicyResolver | `arena.build.complete` | In-memory | - |
| Template Reload | `/arena template reload` | TemplateRegistryBootstrap, TemplateLoader | `arena.template.hot_reload` | YAML/JSON files | - |
| Autosmoke | `/arena autosmoke run` | AutosmokeScheduler, AutosmokeRunner | `arena.autosmoke.complete` | Reports | Cron: 3 AM |
| Force Template | `/arena force <id>` | ArenaCommands, PolicyResolver | - | Transient | Expires after N mins |
| Arena HUD | `/arena hud toggle` | ArenaHudRenderer | - | Config | - |

### Endurance Quest System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Start Quest | `/startquest <mob>` | EnduranceQuestManager, WaveManager | `endurance.session.*` | DuckDB | Session tracking |
| Wave Spawn | Automatic | WaveManager, WaveDirector | `endurance.wave.*` | DuckDB | Per-wave data |
| Combo System | Combat events | ComboSystem | `endurance.combo.*` | DuckDB | D→SSS ranks |
| Perk Selection | Wave complete | PerkSystem, PerkSelectionScreen | `endurance.perk.*` | DuckDB | Per-player |
| Shop Purchase | Shop UI | RewardSystem, EnduranceShopScreen | `endurance.reward.*` | DuckDB | Wallet sync |
| Quest Complete | Last wave | EnduranceQuestManager | `endurance.session.complete` | JSON stats | Best records |

### Instance System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Create Instance | Quest start | InstanceManager, DynamicDimensionManager | - | Registry | Void dimension |
| Player Teleport | Countdown | InstanceManager | - | Snapshot NBT | State preserved |
| Recovery | Login/death | RecoverySystem | - | NBT files | Auto-restore |
| Cleanup | Quest end | InstanceManager | - | Deleted | Files removed |

### Telemetry System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Combat Hits | LivingHurtEvent | TelemetryEvents, DuckDBBatchWriter | `combat.hit` | DuckDB | Batched |
| Deaths | LivingDeathEvent | TelemetryEvents | `combat.death` | DuckDB | TTK tracked |
| Heatmaps | Tick sampling | HeatmapService | `spatial.heatmap` | DuckDB | 60s flush |
| Dashboard | HTTP request | TelemetryDashboardServer | - | Query API | Port 8642 |
| Export | `/devmod telemetry export` | CsvExporter, JsonExporter | - | Files | On-demand |

### Radial Menu / UX

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Open Menu | `G` key | RadialMenuScreenV3 | `radial.opened` | - | Main entry |
| Execute Action | Click/key | ActionRegistry | `radial.action.*` | - | Per-action |
| Search | `/` or `F` | RadialSearchHandler | `radial.search` | - | Fuzzy TBD |
| Favorites | Shift+Click | RadialMenuScreenV3 | - | NOT IMPLEMENTED | Gap |
| Settings | `K` key | UnifiedSettingsScreen | - | Config | - |

### Combat System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Melee Hit | Attack event | DamageHandler, HitHelper | `combat.hit` | DuckDB | Body parts |
| Ranged Hit | Projectile impact | RangedHooks, ArrowEvents | `combat.hit` | DuckDB | Distance |
| Body Parts | OBB collision | CollisionSystem | - | - | Multipliers |
| Damage Numbers | Combat | ImpactHudOverlay | - | - | Client-only |

### Party System

| Feature | Trigger | Components | Telemetry | Persistence | Notes |
|---------|---------|------------|-----------|-------------|-------|
| Create Party | UI action | PartyManager | `endurance.party.*` | DuckDB | Leader track |
| Invite Player | UI action | PartyNetworkHandler | - | Network | Sync |
| Party Quest | Quest start | EnduranceQuestManager | - | Session | Shared |

---

## Network Payload Mapping

| Payload | Direction | Handler | UI Effect |
|---------|-----------|---------|-----------|
| UpdateWeaponPayload | C→S | MobItemNetworkHandler | Editor apply |
| WeaponStatsPayload | S→C | MobItemNetworkHandler | Stats sync |
| StartQuestPayload | C→S | EnduranceNetworkHandler | Quest start |
| QuestSyncPayload | S→C | EnduranceNetworkHandler | HUD update |
| ShopPurchasePayload | C→S | EnduranceNetworkHandler | Shop buy |
| ShopSyncPayload | S→C | EnduranceNetworkHandler | Wallet update |
| PartySyncPayload | S→C | PartyNetworkHandler | Party UI |
| TelemetryBatchPayload | C→S | TelemetryPacketHandler | Client events |

---

## Keybind → Feature Mapping

| Key | Feature | Screen/Overlay | Toggleable |
|-----|---------|----------------|------------|
| `G` | Radial Menu | RadialMenuScreenV3 | No |
| `K` | Settings | UnifiedSettingsScreen | No |
| `M` | Weapon Editor | ItemEditorScreen | No |
| `J` | Dashboard | TelemetryDashboardScreen | No |
| `O` | Debug Overlay | DebugRenderer | Yes |
| `L` | Light Overlay | LightLevelOverlay | Yes |
| `H` | Heatmap | HeatmapVisualizer | Yes |
| `F10` | Endurance Quest | EnduranceQuestScreen | No |
| `\` | Quest HUD | EnduranceQuestOverlay | Yes |

---

## DuckDB Table → Feature Mapping

| Table | Feature | Events/Row | Retention |
|-------|---------|------------|-----------|
| `combat_hits` | Combat | ~100/fight | 30 days |
| `combat_deaths` | Combat | 1/death | 30 days |
| `combat_fights` | Combat | 1/fight | 30 days |
| `endurance_sessions` | Quest | 1/quest | Permanent |
| `endurance_waves` | Quest | 1/wave | 30 days |
| `endurance_combos` | Quest | 5-20/quest | 30 days |
| `endurance_perks` | Quest | 3-10/quest | 30 days |
| `spatial_heatmaps` | Analytics | ~1000/hour | 7 days |
| `player_snapshots` | Analytics | 1/trigger | 7 days |

---

## Gap Analysis

| Feature | Trigger | Telemetry | Gap |
|---------|---------|-----------|-----|
| Ability Usage | Keybind | NOT TRACKED | Missing hooks |
| Favorites | Shift+Click | - | Not persisted |
| Usage Stats | Any action | NOT TRACKED | Not implemented |
| Enchantment Effects | Combat | PARTIAL | Hooks exist, not called |
| Movement Mechanics | Tick | 2s sampling only | No detail |

---

## Cross-References

- [[MOC]] - Master index
- [[ENTRYPOINTS]] - Entry point details
- [[areas/telemetry/README]] - Telemetry details
- [[AUDIT_REPORT]] - Gap analysis

---

*Generated from codebase analysis - 2024-12-23*
