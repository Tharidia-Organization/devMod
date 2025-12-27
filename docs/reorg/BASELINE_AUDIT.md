# Baseline Audit - DevMod Re-Architecture

> Last updated: 2025-12-26
> Status: HISTORICAL (reorg baseline snapshot)

> Audit date: 2024-12-24
> Build status: PASS (./gradlew build)
> Total Java classes: 862
> Total packages: 154

---

## 1. Package Roots

| Root Package | Classes | Status |
|--------------|---------|--------|
| `com.devmod` | 674 | DA ELIMINARE |
| `com.devmod.arena` | 188 | DA MANTENERE |

**Target**: Tutto sotto `com.devmod.*`

---

## 2. Root Package Bloat

**Location**: `com.devmod/` (52 classi nel root - MAX CONSENTITO: 3)

| File | Target Package |
|------|----------------|
| DevMod.java | KEEP IN ROOT |
| DevModClient.java | KEEP IN ROOT |
| ModConfig.java | KEEP IN ROOT (o config/) |
| Config.java | config/ |
| EditorClientConfig.java | config/ |
| ArmorComponents.java | components/ |
| WeaponComponents.java | components/ |
| RangedComponents.java | components/ |
| FoodComponents.java | components/ |
| FuelComponents.java | components/ |
| UsableComponents.java | components/ |
| WeaponStats.java | stats/ |
| ArmorStats.java | stats/ |
| FoodStats.java | stats/ |
| FuelStats.java | stats/ |
| UsableStats.java | stats/ |
| ClientModEvents.java | events/ |
| CommonModEvents.java | events/ |
| GlobalMobEvents.java | events/ |
| CombatEvents.java | combat/events/ |
| ArrowEvents.java | combat/events/ |
| InteractionEvents.java | events/ |
| FoodEvents.java | events/ |
| FuelEvents.java | events/ |
| UsableEvents.java | events/ |
| DamageHandler.java | combat/core/ |
| HitHelper.java | combat/core/ |
| HitContext.java | combat/core/ |
| ActualDamageTracker.java | combat/tracking/ |
| ModAttributes.java | attributes/ |
| NetworkHandler.java | transport/ |
| ArmorConfigManager.java | config/ |
| WeaponConfigManager.java | config/ |
| MobConfigManager.java | config/ |
| FoodConfigManager.java | config/ |
| FuelConfigManager.java | config/ |
| UsableConfigManager.java | config/ |
| ArmorMigrationHelper.java | migration/ |
| ItemEditorDataManager.java | ui/editor/ |
| MobConfigScreen.java | ui/screens/ |
| MobConfigScreenRenderer.java | ui/screens/ |
| MobConfigScreenState.java | ui/screens/ |
| MobEquipmentScreen.java | ui/screens/ |
| MobPresetManager.java | mobs/ |
| TelemetryDashboardScreen.java | telemetry/ui/ |
| WorldRenderEvents.java | rendering/ |
| RangedHooks.java | combat/ |
| EquipMobPayload.java | transport/payloads/ |
| ModifyItemPayload.java | transport/payloads/ |
| UpdateArmorPayload.java | transport/payloads/ |
| UpdateMobStatsPayload.java | transport/payloads/ |
| UpdateWeaponPayload.java | transport/payloads/ |

---

## 3. Duplicate Class Names (12 conflicts)

| Class Name | Location 1 | Location 2 | Resolution |
|------------|------------|------------|------------|
| SpawnSlotValidator | arena/registry/ | arena/spawn/ | RENAME: TemplateSpawnValidator, RuntimeSpawnValidator |
| DashboardValidationJob | arena/monitoring/ | arena/validation/ | MERGE: keep monitoring/ |
| UIConstants | ui/ | ui/editor/core/ | MERGE: single location |
| DebugOverlay | ui/ | ui/editor/debug/ | MERGE: keep editor/debug/ |
| ConfirmDialog | ui/ | ui/editor/systems/ | MERGE: keep editor/systems/ |
| HelpOverlay | ui/ | ui/editor/systems/ | MERGE: keep editor/systems/ |
| RadialAction | ui/radial/ | actions/ | EVALUATE: different purposes? |
| ButtonRow | ui/testing/panel/ | ui/editor/components/ | MERGE: single location |
| TelemetryPage | ui/unified/pages/ | ui/testing/pages/ | MERGE: single location |
| Bounds | ? | ? | IDENTIFY locations |
| DebugOverlaysPage | ? | ? | IDENTIFY locations |
| QuickTestWizard | ? | ? | IDENTIFY locations |

---

## 4. Deprecated forRemoval=true (10 methods)

### ArenaServiceV2.java (4 methods)
```
Line 103: @Deprecated(since = "2.0", forRemoval = true)
Line 120: @Deprecated(since = "2.0", forRemoval = true)
Line 138: @Deprecated(since = "2.0", forRemoval = true)
Line 148: @Deprecated(since = "2.0", forRemoval = true)
```

### EnduranceQuestManager.java (3 methods)
```
Line 846: @Deprecated(forRemoval = true)
Line 1047: @Deprecated(forRemoval = true)
Line 1771: @Deprecated(forRemoval = true)
```

### InstanceArenaManager.java (2 methods)
```
Line 66: @Deprecated(forRemoval = true)
Line 85: @Deprecated(forRemoval = true)
```

**Action**: Remove deprecated methods and update all callers.

---

## 5. Package Distribution

### com.devmod/ Subpackages

| Package | Classes | Notes |
|---------|---------|-------|
| ui | 221 | Largest, needs split |
| endurance | 69 | Quest/wave system |
| telemetry | 62 | DuckDB, metrics |
| hud | 29 | Overlays |
| actions | 16 | Radial actions |
| abilities | 7 | Player abilities |
| ammo | ? | Ammo system |
| arena | ? | Legacy (use com.devmod.arena) |
| attributes | ? | Custom attributes |
| bridge | ? | Cross-mod integration |
| client | ? | Client-only code |
| collision | ? | Hit detection |
| combat | ? | Combat system |
| damage | ? | Damage calculation |
| debug | ? | Debug tools |
| effects | ? | Effects system |
| gametest | ? | Game tests |
| instance | ? | Instance management |
| integration | ? | External integrations |
| mixin | ? | Mixins |
| network | ? | Networking |
| panels | ? | UI panels |
| party | ? | Party system |
| quest | ? | Quest system |
| recipe | ? | Recipe editor |
| rendering | ? | Rendering |
| tags | ? | Tag system |
| testing | ? | Testing utilities |
| util | ? | Utilities |

### com.devmod.arena/ Subpackages (55 subdirs)

| Package | Classes | Notes |
|---------|---------|-------|
| registry | 30 | Template registry |
| builder | 10 | Arena building |
| policy | 9 | Policy resolution |
| command | 3 | Commands |
| + 51 more | 146 | Various subsystems |

---

## 6. Single-Class Packages (arena)

Arena has many single-class packages that should be consolidated:

- admin/
- dashboard/
- dryrun/
- gamification/
- gate/
- leaderboard/
- naming/
- obsolescence/
- rewards/
- rollout/
- template/
- ... (many more)

**Action**: Consolidate into logical groupings.

---

## 7. Migration Checklist

### Phase 1: Namespace (P0)
- [ ] Rename com.devmod → com.devmod
- [ ] Update all 674 imports
- [ ] Update mods.toml entrypoints
- [ ] Update devmod.mixins.json
- [ ] Update any reflection/registry strings
- [ ] Verify build passes

### Phase 2: Dedup (P0)
- [ ] Resolve 12 duplicate class names
- [ ] Remove 10 deprecated forRemoval methods
- [ ] Update all callers

### Phase 3: Root Cleanup (P1)
- [ ] Move 49 classes from root to appropriate packages
- [ ] Keep only DevMod, DevModClient, ModConfig in root
- [ ] Verify build passes

### Phase 4: Modularization (P1-P2)
- [ ] Create com.devmod.combat/ module
- [ ] Create com.devmod.config/ module
- [ ] Create com.devmod.events/ module
- [ ] Create com.devmod.components/ module
- [ ] Create com.devmod.stats/ module
- [ ] Create com.devmod.transport/ module
- [ ] Consolidate arena single-class packages

### Phase 5: Renames (P2)
- [ ] hud → overlay
- [ ] network → transport
- [ ] instance → runtime

### Phase 6: Docs (P2)
- [ ] Archive arena-template-rework/ agent files
- [ ] Consolidate into architecture/ docs
- [ ] Update all internal links

### Phase 7: CI (P3)
- [ ] Add namespace check
- [ ] Add root package check
- [ ] Add deprecated forRemoval check
- [ ] Add duplicate class warning

---

## 8. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Build break during namespace change | HIGH | Incremental commits, test after each file batch |
| Mixin breakage | HIGH | Verify mixin references immediately after rename |
| Registry name changes | MEDIUM | Check for hardcoded strings in resources |
| Test failures | LOW | Tests are well-isolated in com.devmod.arena |

---

## 9. Verification Commands

```bash
# No frenkvs references
grep -r "com.frenkvs" src/ --include="*.java"

# Root package check (max 3 files)
find src/main/java/com/devmod -maxdepth 1 -name "*.java" | wc -l

# No deprecated forRemoval
grep -r "forRemoval.*true" src/ --include="*.java"

# Duplicate class names
find src/ -name "*.java" -exec basename {} \; | sort | uniq -d

# Build verification
./gradlew clean build
```

---

*Baseline captured: 2024-12-24 00:28*
*Build: PASS*
*Ready for Phase 1: Namespace Unification*
