# Armor Properties – Compliance Plan (parity with weapon doc)
Goal: port ArmorModule to the same quality bar as `15-weapon-properties.md`: component-first, typed payload, overwrite modifiers, datapack parity, runtime enforcement, QA/tests, docs.

## Snapshot (current state/gaps)
- No `armor_stats` data component or typed payload; legacy payload/config only.
- Equip sanitization/overwrite not present for armor/shields; modifiers may stack with external ones.
- Datapack export/import uses older schema (no advanced fields, no overwrite semantics).
- Testing Hub lacks armor-specific test cases; no automated GameTests for armor reduction/shield logic.
- UI coverage is not documented/aligned with weapon doc: no badge model, tab map, or tooltip/label parity; shield-specific flows not listed.

## Gap Critici (P0)
- Data component assente: manca `armor_stats` analogo al weapon doc; oggi esiste solo NBT legacy (`ArmorModStats`) e nessuna migrazione automatica NBT → component.
- Payload non tipizzato: l’attuale `ArmorStatsPayload` non segue il pattern v2 (record + `StreamCodec`), non c’è `ArmorStatsPayloadV2` né validazione server-side via `PacketSecurityService`.
- Overwrite modifiers: nessuna applicazione di attribute modifiers DevMod né logica “remove-all-then-add DevMod” per evitare stacking; manca enforcement runtime su equip/apply.
- Source badges: l’UI non mostra badge DEV/NBT/VANILLA né una legend di pannello per spiegare le fonti.

## Gap Significativi (P1)
- Tab structure/documentazione mancante: REDUCTION, STATS, SHIELD, DEBUG non hanno elenco controlli, grid spacing, tooltip, ranges/clamp, badge legend, né flussi shield-specific (reflect/cooldown/overwrite).
- Runtime enforcement limitato: manca validazione/sanitize su equip change, nessun warning/log quando esistono external modifiers (overwrite semantics non esplicitati), shield mechanics non integrate nel component system.
- Datapack export/import: schema legacy senza campi avanzati né overwrite semantics; manca parity con weapon datapack (campi shield, riduzioni, clamp info).

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
- [ ] Add source badges (DEV/NBT/VANILLA) to ArmorModule sliders/toggles (same pattern as Weapon/Ranged) with legend in the panel.
- [ ] Tab coverage/structure (match weapon doc quality, with consistent grid spacing):
  - REDUCTION: phys/fire/magic/explosion/projectile sliders (0–100%, % suffix, clamped, warning on clamp).
  - STATS: armor, toughness, knockback resistance (flat units, clamp + warning).
  - SHIELD: block strength %, reflect toggle, cooldown/recovery speed, projectile reflect flag; show badge + tooltip for overwrite behavior.
  - DURABILITY/COMPONENTS: unbreakable, repair cost, max damage (if exposed), “clear shield rules”/tool-like toggles if applicable.
  - DEBUG: raw component viewer + applied modifiers list (highlight DevMod vs external).
- [ ] UI labels/tooltips: mirror weapon doc style (units, ranges, overwrite semantics), consistent spacing and badges per control.

## P1 – Runtime enforcement
- [x] Equip change: sanitize/clamp armor stats, reapply modifiers, warn when external modifiers exist (but overwrite them) with log.
- [ ] Shield mechanics: ensure block strength/reflect/cooldown read from component/modifiers (no legacy path), integrate clear/overwrite rules.
- [ ] Optional: validate modifier ranges on equip/apply (log/remove out-of-range non-DevMod modifiers); emit user-facing warning when clamped.

## P1 – Datapack parity
- [x] Export/import advanced armor fields (reductions, armor/toughness/KB resist, shield props) with overwrite semantics (one modifier per attribute, DevMod namespace) and document version.

## P2 – Tests (QA + automated)
- [ ] TestingHub cases: component round-trip, shield block/reflect/cooldown, datapack export/import, clamp override, modifier overwrite (no stacking).
- [ ] (If runtime MC available) GameTests for armor reduction calc, component→modifier materialization, shield reflect/cooldown.

## P2 – Docs
- [ ] Add “Armor Properties” section mirroring doc15 structure: data components, payloads, runtime enforcement, tests, datapack notes.
- [ ] Mark legacy armor paths as deprecated once component/payload are live.
[{
	"resource": "/Users/erik/Desktop/DevMod/devMod/src/main/java/com/frenkvs/devmod/ui/editor/modules/ArmorModule.java",
	"owner": "_generated_diagnostic_collection_name_#4",
	"code": "16778128",
	"severity": 4,
	"message": "Null type safety: The expression of type 'ItemAttributeModifiers' needs unchecked conversion to conform to '@Nonnull ItemAttributeModifiers'",
	"source": "Java",
	"startLineNumber": 161,
	"startColumn": 45,
	"endLineNumber": 161,
	"endColumn": 108,
	"origin": "extHost1"
},{
	"resource": "/Users/erik/Desktop/DevMod/devMod/src/main/java/com/frenkvs/devmod/ui/editor/modules/ArmorModule.java",
	"owner": "_generated_diagnostic_collection_name_#4",
	"code": "1201",
	"severity": 2,
	"message": "Unlikely argument type for equals(): Holder<Attribute> seems to be unrelated to Attribute",
	"source": "Java",
	"startLineNumber": 193,
	"startColumn": 56,
	"endLineNumber": 193,
	"endColumn": 113,
	"origin": "extHost1"
},{
	"resource": "/Users/erik/Desktop/DevMod/devMod/src/main/java/com/frenkvs/devmod/ui/editor/modules/ArmorModule.java",
	"owner": "_generated_diagnostic_collection_name_#4",
	"code": "1201",
	"severity": 2,
	"message": "Unlikely argument type for equals(): Holder<Attribute> seems to be unrelated to Attribute",
	"source": "Java",
	"startLineNumber": 195,
	"startColumn": 63,
	"endLineNumber": 195,
	"endColumn": 130,
	"origin": "extHost1"
},{
	"resource": "/Users/erik/Desktop/DevMod/devMod/src/main/java/com/frenkvs/devmod/ui/editor/modules/ArmorModule.java",
	"owner": "_generated_diagnostic_collection_name_#4",
	"code": "1201",
	"severity": 2,
	"message": "Unlikely argument type for equals(): Holder<Attribute> seems to be unrelated to Attribute",
	"source": "Java",
	"startLineNumber": 197,
	"startColumn": 63,
	"endLineNumber": 197,
	"endColumn": 135,
	"origin": "extHost1"
}]