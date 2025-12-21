# Armor Properties – Compliance Plan (parity with weapon doc)

> DEPRECATED: superseded by `docs/editor-design-system/16-armor-properties.md`.

Goal: port ArmorModule to the same quality bar as `15-weapon-properties.md`: component-first, typed payload, overwrite modifiers, datapack parity, runtime enforcement, QA/tests, docs.

## Implementation Status (Last Updated: 2025-12-17)

### ✅ COMPLETED - Data Model & Payload (P0)
All core P0 items verified implemented:
- `ArmorComponents.ARMOR_STATS` - registered data component with fallback for tests
- `ArmorStatsPayloadV2` - typed payload with StreamCodec (record pattern)
- `PacketSecurityService` - validates armor/shield/reduction values
- Component-first storage with attribute modifier overwrite in `ArmorConfigManager`
- Legacy NBT (`ArmorModStats`) → component auto-migration on `getStats()`

### ✅ COMPLETED - Runtime & Datapack (P1)
- Equip change sanitize/clamp implemented in `ArmorConfigManager.clampStats()`
- `DatapackIO.writeArmor()` exports all armor fields including shield properties
- `DatapackIO.parseArmor()` imports all fields with proper null handling

### ✅ COMPLETED - UI Parity (P0)
- `SourceBadge` component implemented for DEV/NBT/VAN/MOD indicators
- `EditorSlider` and `EditorToggle` support `.source()` and `.sourceBadge()` builders
- Badge renders inline after label in slider/toggle components
- Tab structure complete: REDUCTION, STATS, SPECIAL, SHIELD (variant), DEBUG

### ✅ COMPLETED - Shield Mechanics (P1)
- `DamageHandler.applyShieldBlock()` fully integrated with ArmorStats component
- Block strength scaling: `stats.shieldBlockStrength` (0-1) reduces damage proportionally
- Projectile reflection: `stats.shieldReflectProjectiles` reverses projectile velocity
- Recovery speed: `stats.shieldRecoverySpeed` scales cooldown (base 5 ticks)

### ✅ COMPLETED - Source Badge Auto-Detection
- ArmorModule tracks data origin: component, NBT, global config, or vanilla
- `determineSource()` method returns DEV/NBT/VANILLA badge type
- All sliders/toggles display inline source badge via `.source()` builder

### Remaining Gaps (Evolutionary)

**P2 - Tests & Docs:**
- [x] TestingHub armor cases implemented in `DevModArmorTestCases.java`
- [ ] GameTests for armor reduction not implemented (requires runtime MC)
- [x] Documentation written: `16-armor-properties.md`

---

## Original Gap Analysis (Historical Reference)

### Gap Critici (P0) - RISOLTI
- ~~Data component assente~~ → `ArmorComponents.ARMOR_STATS` implementato
- ~~Payload non tipizzato~~ → `ArmorStatsPayloadV2` implementato con StreamCodec
- ~~Overwrite modifiers~~ → implementato in `ArmorConfigManager.applyAttributeModifiers()`
- Source badges: l'UI non mostra badge DEV/NBT/VANILLA (ancora da implementare)

### Gap Significativi (P1) - PARZIALMENTE RISOLTI
- ~~Runtime enforcement limitato~~ → clamp/sanitize implementato
- ~~Datapack export/import legacy~~ → schema completo con tutti i campi
- Tab structure/documentazione: ancora incompleta

## P0 – Audit vs weapon doc (quality bar alignment)
- [ ] Cross-check every field in `15-weapon-properties.md` against armor needs: reductions, armor/toughness/KB resist, shield block strength/reflect/cooldown, durability/repair toggles, debug tab.
- [ ] Define authoritative ranges/units/tooltips (suffixes, % vs flat) matching weapon doc style; list clamp rules for each control.
- [ ] Document source-of-truth order (component > modifiers > legacy NBT) and overwrite semantics so UI/runtime/datapack are consistent.

## P0 – Data model & payload
- [x] Add `ArmorComponents.ARMOR_STATS` (persistent + network) mirroring weapon_stats.
- [x] Create `ArmorStatsPayloadV2` (StreamCodec) and server handler; keep legacy payload for compat but migrate into component on apply.
- [x] Clamp client/server via `PacketSecurityService` (reduction, toughness, block strength, reflect flags, cooldown).
- [x] Save/mirror to component + apply armor/shield modifiers by overwriting existing modifiers on the same attributes (remove-all-then-add DevMod).
- [x] Auto-migrate legacy NBT to component on load; persist reconstructed stats back into the component.

## P0 – UI parity
- [x] Add source badges (DEV/NBT/VANILLA) to ArmorModule sliders/toggles (same pattern as Weapon/Ranged) with legend in the panel.
  - Implemented `SourceBadge` component in `ui/editor/components/SourceBadge.java`
  - Added `sourceBadge()` and `source()` builder methods to `EditorSlider` and `EditorToggle`
  - Badge renders inline after label, showing DEV/NBT/VAN/MOD indicators
- [x] Tab coverage/structure (match weapon doc quality, with consistent grid spacing):
  - REDUCTION: phys/fire/magic/explosion/projectile sliders (0–80%, % suffix, clamped)
  - STATS: armor bonus, toughness bonus, knockback resistance (flat units, clamped)
  - SPECIAL: thorns toggle + thorns damage slider
  - SHIELD: block strength %, reflect toggle, recovery speed (for shield variant)
  - DEBUG: raw component viewer + applied modifiers list (via DebugInfoSection)
- [ ] UI labels/tooltips: mirror weapon doc style (units, ranges, overwrite semantics), consistent spacing and badges per control.

## P1 – Runtime enforcement
- [x] Equip change: sanitize/clamp armor stats, reapply modifiers, warn when external modifiers exist (but overwrite them) with log.
- [x] Shield mechanics: ensure block strength/reflect/cooldown read from component/modifiers (no legacy path), integrate clear/overwrite rules.
  - Implemented in `DamageHandler.applyShieldBlock()` - reads from `ArmorConfigManager.getStats(shield)`
- [ ] Optional: validate modifier ranges on equip/apply (log/remove out-of-range non-DevMod modifiers); emit user-facing warning when clamped.

## P1 – Datapack parity
- [x] Export/import advanced armor fields (reductions, armor/toughness/KB resist, shield props) with overwrite semantics (one modifier per attribute, DevMod namespace) and document version.

## P2 – Tests (QA + automated)
- [x] TestingHub cases: component round-trip, shield block/reflect/cooldown, datapack export/import, clamp override, modifier overwrite (no stacking).
  - Implemented in `DevModArmorTestCases.java` with 13 test cases
  - Integrated via `DevModCoreTestTemplate` in `DynamicTestGenerator`
- [ ] (If runtime MC available) GameTests for armor reduction calc, component→modifier materialization, shield reflect/cooldown.

## P2 – Docs
- [x] Add "Armor Properties" section mirroring doc15 structure: data components, payloads, runtime enforcement, tests, datapack notes.
  - Created `16-armor-properties.md` with full documentation
- [x] Mark legacy armor paths as deprecated once component/payload are live.
  - Legacy `ArmorModStats` NBT auto-migrates to component on load
