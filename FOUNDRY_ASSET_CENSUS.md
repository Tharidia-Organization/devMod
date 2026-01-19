# Foundry Asset Census

## Scope
- Blocks: 40
- Items (all): 194
- Fluids: 16 molten variants (source + flowing + bucket)
- Item models: all foundry items have a model json present
- Blockstates: all foundry blocks have blockstate json present
- Block models: duct uses multipart models (foundry_duct_center/foundry_duct_arm); no single foundry_duct model

## Blocks - Functional
- foundry_controller: multiblock smeltery controller; GUI; repair with foundry_bricks when idle; tracks thermal/risk/purity/alloy.
- foundry_drain: bucket in/out IO to controller (fill/drain 1000mb).
- foundry_faucet: auto pours molten to casting table/basin or channel below; ticks server side.
- foundry_casting_table: accepts ingot/nugget casts; outputs items; manual insert/extract.
- foundry_casting_basin: like table but for basin recipes; manual insert/extract.
- foundry_part_builder: GUI; consumes pattern + material; outputs part with quality/purity.
- foundry_tool_station: GUI; assembles tools from parts; consumes inputs on take.
- foundry_tool_anvil: GUI; applies modifiers, embossment, and repairs; uses slot rules + specialization gating in menu.
- foundry_channel: fluid routing; valves toggle with shift+use; filter set/clear with bucket.
- foundry_duct: channel variant; auto-pull from controller; shares channel behaviors.
- foundry_chute: item input to controller; shift+use sets/clears filter; manual insert/extract.
- foundry_tank: visual tank linked to controller; shows primary molten.
- foundry_gauge: visual gauge; same block entity as tank.
- foundry_fuel_tank: visual fuel tank; same block entity as tank.

## Blocks - Structural / Seared
- foundry_bricks: smeltery wall block (structure).
- foundry_cracked_bricks: damage state placeholder; accepted by structure validator.
- foundry_glass: window block; no occlusion; uses vanilla glass texture.
- foundry_window: window block; no occlusion; uses vanilla white stained glass texture.

## Blocks - Storage (materials)
- steel_block, bronze_block, cobalt_block, manyullyn_block, tin_block, lead_block, silver_block, nickel_block, electrum_block, invar_block, ardite_block, void_metal_block
  - Static storage blocks; used by casting/melting recipes; no special interactions.

## Blocks - Ores
- tin_ore, deepslate_tin_ore, lead_ore, deepslate_lead_ore, silver_ore, deepslate_silver_ore, nickel_ore, deepslate_nickel_ore, cobalt_ore, ardite_ore
  - Standard ore blocks; no special interactions (mineable tags set via data).

## Fluids (molten)
- molten_iron, molten_gold, molten_copper, molten_tin, molten_bronze, molten_steel, molten_cobalt, molten_manyullyn, molten_lead, molten_silver, molten_nickel, molten_electrum, molten_invar, molten_ardite, molten_netherite, molten_void_metal
- Custom textures used for: molten_tin, molten_bronze, molten_steel, molten_cobalt, molten_manyullyn, molten_lead, molten_silver, molten_nickel, molten_electrum, molten_invar, molten_ardite, molten_void_metal.
- Lava-tinted (no custom textures): molten_iron, molten_gold, molten_copper, molten_netherite.

## Items - Block Items (place blocks)
- foundry_controller, foundry_drain, foundry_faucet, foundry_casting_table, foundry_casting_basin, foundry_part_builder, foundry_tool_station, foundry_tool_anvil, foundry_channel, foundry_duct, foundry_chute, foundry_tank, foundry_gauge, foundry_fuel_tank, foundry_bricks, foundry_cracked_bricks, foundry_glass, foundry_window, steel_block, bronze_block, cobalt_block, manyullyn_block, tin_block, lead_block, silver_block, nickel_block, electrum_block, invar_block, ardite_block, void_metal_block, tin_ore, deepslate_tin_ore, lead_ore, deepslate_lead_ore, silver_ore, deepslate_silver_ore, nickel_ore, deepslate_nickel_ore, cobalt_ore, ardite_ore

## Items - System
- foundry_guide: opens Foundry guide screen.
- foundry_specialization_weaponsmith/toolsmith/alloyist: sets specialization once; consumes item.
- foundry_specialization_reset: clears specialization and tool specializations; consumes item.
- foundry_flux/refined/pure: purity bonus + cap; used by controller; refined also unlocks modifiers in tool anvil.

## Items - Casts
- foundry_ingot_cast, foundry_nugget_cast: inserted/removed in casting table/basin.

## Items - Tool Patterns (durability + mastery)
- foundry_pattern_tool_head, foundry_pattern_tool_handle, foundry_pattern_tool_binding, foundry_pattern_pickaxe_head, foundry_pattern_axe_head, foundry_pattern_shovel_head, foundry_pattern_sword_blade, foundry_pattern_hoe_head, foundry_pattern_hammer_head, foundry_pattern_excavator_head, foundry_pattern_scythe_head, foundry_pattern_dagger_blade, foundry_pattern_spear_head, foundry_pattern_cleaver_blade, foundry_pattern_longsword_blade, foundry_pattern_battleaxe_head, foundry_pattern_mattock_head, foundry_pattern_kama_head, foundry_pattern_armor_plate, foundry_pattern_armor_mail, foundry_pattern_armor_trim, foundry_pattern_bow_limb, foundry_pattern_bowstring, foundry_pattern_crossbow_stock, foundry_pattern_shield_core, foundry_pattern_shield_plating, foundry_pattern_wrench_head, foundry_pattern_tool_guard, foundry_pattern_large_head, foundry_pattern_tough_handle
  - Used in Part Builder; durability, mastery, specialization after 100 uses; default tier STANDARD.

## Items - Tool Parts (materialized parts)
- foundry_tool_head, foundry_tool_handle, foundry_tool_binding, foundry_pickaxe_head, foundry_axe_head, foundry_shovel_head, foundry_sword_blade, foundry_hoe_head, foundry_hammer_head, foundry_excavator_head, foundry_scythe_head, foundry_dagger_blade, foundry_spear_head, foundry_cleaver_blade, foundry_longsword_blade, foundry_battleaxe_head, foundry_mattock_head, foundry_kama_head, foundry_armor_plate, foundry_armor_mail, foundry_armor_trim, foundry_bow_limb, foundry_bowstring, foundry_crossbow_stock, foundry_shield_core, foundry_shield_plating, foundry_wrench_head, foundry_tool_guard, foundry_large_head, foundry_tough_handle
  - Store material + quality + purity; used in Tool Station.

## Items - Tools
- foundry_pickaxe, foundry_axe, foundry_shovel, foundry_sword, foundry_hoe, foundry_hammer, foundry_excavator, foundry_scythe, foundry_dagger, foundry_spear, foundry_cleaver, foundry_longsword, foundry_battleaxe, foundry_mattock, foundry_kama, foundry_bow, foundry_crossbow, foundry_longbow, foundry_shield, foundry_wrench, foundry_fishing_rod, foundry_staff
  - Dynamic stats via Foundry tool data; XP/level from mining/combat events; modifiers/embossment supported.
  - Bow/Longbow use BowItem mechanics; Crossbow uses CrossbowItem; Shield uses custom blocking; Fishing Rod uses FishingRodItem; Staff has bow-style use animation, no extra effect yet.

## Items - Armor
- foundry_helmet, foundry_chestplate, foundry_leggings, foundry_boots, traveler_helmet, traveler_chestplate, traveler_leggings, traveler_boots, plate_helmet, plate_chestplate, plate_leggings, plate_boots, slime_helmet, slime_chestplate, slime_leggings, slime_boots
  - Dynamic stats via Foundry tool data; slime set bonus implemented (bounce + fall damage cancel).

## Items - Materials
- Ingots: steel_ingot, bronze_ingot, cobalt_ingot, manyullyn_ingot, tin_ingot, lead_ingot, silver_ingot, nickel_ingot, electrum_ingot, invar_ingot, ardite_ingot, void_metal_ingot
- Nuggets: steel_nugget, bronze_nugget, cobalt_nugget, manyullyn_nugget, tin_nugget, lead_nugget, silver_nugget, nickel_nugget, electrum_nugget, invar_nugget, ardite_nugget, void_metal_nugget
- Raw: raw_tin, raw_lead, raw_silver, raw_nickel, raw_cobalt, raw_ardite

## Items - Molten Buckets
- molten_iron_bucket, molten_gold_bucket, molten_copper_bucket, molten_tin_bucket, molten_bronze_bucket, molten_steel_bucket, molten_cobalt_bucket, molten_manyullyn_bucket, molten_lead_bucket, molten_silver_bucket, molten_nickel_bucket, molten_electrum_bucket, molten_invar_bucket, molten_ardite_bucket, molten_netherite_bucket, molten_void_metal_bucket
  - Bucket items use neoforge bucket model; tint comes from fluid type.

## Item models using vanilla minecraft textures (no dedicated texture yet)
- foundry_boots, foundry_chestplate, foundry_fishing_rod, foundry_flux, foundry_flux_pure, foundry_flux_refined, foundry_guide, foundry_helmet, foundry_leggings, foundry_specialization_alloyist, foundry_specialization_reset, foundry_specialization_toolsmith, foundry_specialization_weaponsmith, foundry_staff, plate_boots, plate_chestplate, plate_helmet, plate_leggings, slime_boots, slime_chestplate, slime_helmet, slime_leggings, traveler_boots, traveler_chestplate, traveler_helmet, traveler_leggings

## Block models using vanilla minecraft textures (placeholders)
- foundry_cracked_bricks, foundry_glass, foundry_window

## Shared/generic item textures (candidates for dedicated shapes)
- Tool parts share textures:
  - devmod:item/foundry_tool_head: foundry_tool_head, foundry_pickaxe_head, foundry_axe_head, foundry_shovel_head, foundry_sword_blade, foundry_hoe_head, foundry_hammer_head, foundry_excavator_head, foundry_scythe_head, foundry_dagger_blade, foundry_spear_head, foundry_cleaver_blade, foundry_longsword_blade, foundry_battleaxe_head, foundry_mattock_head, foundry_kama_head, foundry_armor_plate, foundry_bow_limb, foundry_shield_core, foundry_large_head
  - devmod:item/foundry_tool_handle: foundry_tool_handle, foundry_armor_mail, foundry_crossbow_stock, foundry_tough_handle
  - devmod:item/foundry_tool_binding: foundry_tool_binding, foundry_armor_trim, foundry_bowstring, foundry_tool_guard
- Pattern items share textures:
  - devmod:item/foundry_pattern_tool_head: foundry_pattern_tool_head, foundry_pattern_pickaxe_head, foundry_pattern_axe_head, foundry_pattern_shovel_head, foundry_pattern_sword_blade, foundry_pattern_hoe_head, foundry_pattern_hammer_head, foundry_pattern_excavator_head, foundry_pattern_scythe_head, foundry_pattern_dagger_blade, foundry_pattern_spear_head, foundry_pattern_cleaver_blade, foundry_pattern_longsword_blade, foundry_pattern_battleaxe_head, foundry_pattern_mattock_head, foundry_pattern_kama_head, foundry_pattern_armor_plate, foundry_pattern_bow_limb, foundry_pattern_shield_core, foundry_pattern_large_head
  - devmod:item/foundry_pattern_tool_handle: foundry_pattern_tool_handle, foundry_pattern_armor_mail, foundry_pattern_crossbow_stock, foundry_pattern_tough_handle
  - devmod:item/foundry_pattern_tool_binding: foundry_pattern_tool_binding, foundry_pattern_armor_trim, foundry_pattern_bowstring, foundry_pattern_tool_guard
