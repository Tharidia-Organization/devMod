# QA Test Plan – Weapon Properties

> Last updated: 2025-12-26
> Status: ARCHIVED (manual checklist; replace with automated tests)

Nota: la validazione e' coperta da test automatici (es. `src/test/java/com/devmod/WeaponConfigTest.java`
e `src/test/java/com/devmod/gametest/WeaponStatsComponentTest.java`). Questo checklist resta solo come
riferimento storico.

Manual/QA scenarios to validate the weapon properties implementation (component + modifiers + datapack).

## Setup
- Modpack with DevMod enabled.
- Ensure `weapon_stats_v2` payload path is active (latest build).
- Have editor access and a few test items (e.g., diamond sword/pickaxe/bow).

## Scenarios
1. **Component round-trip**
   - Edit a melee weapon: set attack damage, crit chance/damage, vs-undead, armor shred.
   - Apply changes, unequip/reequip. Confirm values persist and tooltips reflect modifiers (no stacking duplicates).
2. **Clear Tool Rules**
   - On a pickaxe, toggle “Clear Tool Rules” on, apply.
   - Verify the item has no `tool` component (in NBT viewer) and mining speed uses default behavior.
   - Toggle off, add a rule (tag + speed), apply, verify component present and speed applies.
3. **Damage type bonuses**
   - Set vs-undead to +100%, true damage to 20%, armor shred to 10.
   - Attack an undead mob: confirm higher damage than baseline; armor shred/logs show effect; true damage bypasses part of armor.
   - Attack a player: confirm vs-undead not applied.
4. **Datapack export/import**
   - Export overrides via editor (datapack).
   - Delete local overrides, import datapack, confirm values and clear-tool rules are restored without stacking modifiers.
5. **Equip sanitization**
   - Add conflicting modifiers manually to a weapon (via commands).
   - Equip weapon: DevMod should overwrite with its own modifiers and clamp values; logs should show sanitization.
6. **Pufferfish/other mods coexistence**
   - With another mod adding modifiers to the same attribute, apply DevMod edits.
   - Confirm DevMod overwrote modifiers once (no duplicates) and values match editor.
7. **Regression: payload clamp**
   - Attempt to send out-of-range values from editor (e.g., armor shred >66, true damage >100%).
   - Confirm server clamps to allowed max and applies correctly.

## Acceptance criteria
- No duplicate modifiers per attribute after apply/equip.
- Clear-tool toggle removes tool component reliably.
- Datapack export/import preserves all advanced fields (sweep, armor_shred, vs-*, true_damage, clear_tool_rules).
- Out-of-range edits are clamped server-side.
