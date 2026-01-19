# Foundry vs TConstruct Models (Usage Comparison)

Scope: confronto asset models/blockstates per le parti Foundry della mod (DevMod) vs Tinkers' Construct (TConstruct) presenti in `tmp/tinkersconstruct/...`.

Legenda:
- DevMod = `src/main/resources/assets/devmod/...`
- TConstruct = `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/...` (o `src/generated/...` per i modelli generati)

---

## 1) Blocks - Functional (ordine del sistema)

### 1.1 foundry_controller
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_controller.json`
  - Models: `src/main/resources/assets/devmod/models/block/foundry_controller.json`, `src/main/resources/assets/devmod/models/block/foundry_controller_active.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/foundry_controller.json`
  - Models: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/controller/foundry_unformed.json`, `foundry_inactive.json`, `foundry_active.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/controller_fluid.json`
- Differenze uso/visive:
  - TConstruct ha stato `in_structure` + `active` (unformed/inactive/active); DevMod ha solo `active`.
  - TConstruct usa loader `tconstruct:fluid_texture` per finestra fluido sul controller; DevMod usa modello statico + renderer BE esterno.
  - TConstruct ha texture differenziate per front/back e bricks retextured; DevMod usa 3 texture principali (front/side/top) senza fluid window nel modello.

### 1.2 foundry_drain
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_drain.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_drain.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_drain.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/io/drain_active.json` (active), `drain_inactive.json` (inactive)
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/io_fluid.json`
- Differenze:
  - TConstruct ha `in_structure` + `facing` con modello active/inactive; DevMod non ha stati (sempre uguale).
  - TConstruct ha overlay fluido nella faccia drain; DevMod no (solo texture unica).

### 1.3 foundry_faucet
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_faucet.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_faucet.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_faucet.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/faucet.json`, `faucet_up.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/faucet.json`
- Differenze:
  - TConstruct supporta `facing=down` (faucet verticale); DevMod solo orizzontale.
  - TConstruct usa modello con geometria a rubinetto; DevMod e` un cube_all (nessuna silhouette).

### 1.4 foundry_casting_table
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_casting_table.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_casting_table.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_table.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/table.json`, `table_covered.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/table.json`
- Differenze:
  - TConstruct usa `facing` + `has_item` per copertura; DevMod non ha stati.
  - TConstruct ha modello "tavolo" con gambe e top aperto; DevMod e` un blocco pieno.

### 1.5 foundry_casting_basin
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_casting_basin.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_casting_basin.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_basin.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/basin.json`, `basin_covered.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/basin.json`
- Differenze:
  - TConstruct usa `facing` + `has_item`; DevMod no.
  - TConstruct ha bacino aperto con spessore e dettagli; DevMod e` un blocco pieno.

### 1.6 foundry_part_builder
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_part_builder.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_part_builder.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/part_builder.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/table/part_builder.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/table/table.json`
- Differenze:
  - TConstruct usa modello "tavolo" (gambe + top) con retexture Mantle; DevMod usa `orientable_with_bottom` (blocco pieno).

### 1.7 foundry_tool_station
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_tool_station.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_tool_station.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/tinker_station.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/table/tinker_station.json`
- Differenze:
  - TConstruct usa geometria tavolo; DevMod usa cubo pieno.
  - TConstruct ha texture top specifica (stazione), DevMod usa texture base tool.

### 1.8 foundry_tool_anvil
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_tool_anvil.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_tool_anvil.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/tinkers_anvil.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/table/tinkers_anvil.json`
- Differenze:
  - TConstruct usa geometria tavolo; DevMod usa cubo pieno.

### 1.9 foundry_channel
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_channel.json`
  - Models: `src/main/resources/assets/devmod/models/block/foundry_channel_center.json`, `foundry_channel_arm.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_channel.json`
  - Models: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/smeltery/channel/center.json`, `center_out.json`, `side_in.json`, `side_out.json`, `side_wall.json`
- Differenze:
  - TConstruct usa stato per lato `none/in/out` + center_out quando `down=true`; DevMod usa booleani per lati (arm).
  - TConstruct distingue canali in/out con pareti; DevMod non ha distinzione visiva.

### 1.10 foundry_duct
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_duct.json`
  - Models: `src/main/resources/assets/devmod/models/block/foundry_duct_center.json`, `foundry_duct_arm.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_duct.json`
  - Models: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/io/duct_active.json`, `duct_inactive.json`
- Differenze:
  - TConstruct ha `in_structure` + `facing` (singola direzione); DevMod e` pipe multi-connessione.
  - TConstruct ha stato visuale attivo/inattivo; DevMod no.

### 1.11 foundry_chute
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_chute.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_chute.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_chute.json`
  - Models: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/io/chute_active.json`, `chute_inactive.json`
- Differenze:
  - TConstruct ha `in_structure` + `facing` con active/inactive; DevMod no.

### 1.12 foundry_tank
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_tank.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_tank.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_ingot_tank.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/tank/ingot_tank.json`
  - Template: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/template/tank_knob.json`
- Differenze:
  - TConstruct usa loader `tconstruct:tank` per rendering fluido interno; DevMod usa cube_all + renderer BE.
  - TConstruct ha knob e overlay; DevMod no.

### 1.13 foundry_gauge
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_gauge.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_gauge.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_ingot_gauge.json` (per ingot) e `scorched_fuel_gauge.json` (per fuel)
  - Models: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/tank/ingot_gauge.json`, `fuel_gauge.json`
- Differenze:
  - TConstruct separa gauge per ingot/fuel; DevMod ha un solo gauge.
  - TConstruct usa loader tank + top gauge; DevMod texture unica.

### 1.14 foundry_fuel_tank
- DevMod:
  - Blockstate: `src/main/resources/assets/devmod/blockstates/foundry_fuel_tank.json`
  - Model: `src/main/resources/assets/devmod/models/block/foundry_fuel_tank.json`
- TConstruct:
  - Blockstate: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/blockstates/scorched_fuel_tank.json`
  - Model: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/tank/fuel_tank.json`
- Differenze:
  - TConstruct usa loader tank + textures dedicate; DevMod e` cube con texture tank.

---

## 2) Blocks - Structural / Seared

### 2.1 foundry_bricks
- DevMod: `src/main/resources/assets/devmod/models/block/foundry_bricks.json`
- TConstruct: `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/scorched/bricks.json`
- Differenze: modello equivalente (cube_all). TConstruct ha varianti slab/stairs/wall; DevMod no.

### 2.2 foundry_cracked_bricks
- DevMod: `src/main/resources/assets/devmod/models/block/foundry_cracked_bricks.json`
- TConstruct: non esiste versione "scorched_cracked"; piu simile a `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/smeltery/seared/cracked_bricks.json` (seared)
- Differenze: variante foundry non presente in TConstruct (solo seared).

### 2.3 foundry_glass / foundry_window
- DevMod:
  - `src/main/resources/assets/devmod/models/block/foundry_glass.json`
  - `src/main/resources/assets/devmod/models/block/foundry_window.json`
- TConstruct:
  - `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/block/foundry/tinted_glass.json`
  - Loader: `mantle:connected` per connected textures
- Differenze:
  - TConstruct usa connected textures + render_type translucent; DevMod usa cube_all con texture vanilla (no connessione).

---

## 3) Item Models - Parti, Pattern, Tool, Bucket

### 3.1 Tool parts (parti)
- DevMod (esempio): `src/main/resources/assets/devmod/models/item/foundry_pickaxe_head.json`
- TConstruct (esempio): `tmp/tinkersconstruct/src/generated/resources/assets/tconstruct/models/item/pick_head.json`
- Differenze:
  - TConstruct usa loader `tconstruct:material` con texture per parte e offset (material-based); DevMod usa item/generated statico.
  - TConstruct supporta variazione materiale via loader; DevMod no (solo texture fissa).

### 3.2 Pattern items
- DevMod: modelli per ogni pattern (es. `src/main/resources/assets/devmod/models/item/foundry_pattern_pickaxe_head.json`)
- TConstruct: pattern unico `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/item/pattern.json`
- Differenze:
  - TConstruct usa un item pattern unico con scelta tipo/slot; DevMod ha pattern separati con texture condivisa.

### 3.3 Tool items
- DevMod (esempio): `src/main/resources/assets/devmod/models/item/foundry_pickaxe.json`
- TConstruct (esempio): `tmp/tinkersconstruct/src/main/resources/assets/tconstruct/models/item/pickaxe.json`
- Differenze:
  - TConstruct usa loader `tconstruct:tool` con parti (head/handle/binding), overlay modifiers, override broken/charging.
  - DevMod usa texture statica (handheld), nessun overlay dinamico.

### 3.4 Fluid buckets
- DevMod (esempio): `src/main/resources/assets/devmod/models/item/molten_iron_bucket.json`
- TConstruct (esempio): `tmp/tinkersconstruct/src/generated/resources/assets/tconstruct/models/item/molten_iron_bucket.json`
- Differenze:
  - TConstruct usa loader `tconstruct:fluid_container` + `forge:item/bucket_drip` con fluid id; DevMod usa `neoforge:item/bucket` standard.

---

## 4) Impatti principali sull'usage (riassunto)

1. **Stati multiblocco**: ✅ DevMod ora espone `in_structure` e `active` nei modelli (controller/drain/duct/chute) con feedback visivo.
2. **Fluid rendering**: TConstruct usa loader per fluidi; DevMod usa modelli statici + renderer BE (approccio valido alternativo).
3. **IO orientati**: ✅ DevMod drain/chute hanno `facing` + stati attivi; duct mantiene sistema pipe multi-connessione con `in_structure`.
4. **Casting blocks**: ✅ DevMod ha tavolo/basin con geometria aperta (gambe + top) e `has_item` cover.
5. **Tools/parts**: ✅ DevMod ora usa `IGeometryLoader` custom (`FoundryPartModelLoader`, `FoundryToolModelLoader`) per rendering dinamico basato su materiale.
6. **Pattern system**: TConstruct usa pattern unico, DevMod pattern per-part (scelta di design).
7. **Faucet verticale**: ✅ DevMod supporta `facing=down/up` per faucet verticali.
8. **Tool tables**: ✅ DevMod ha geometria tavolo (gambe + top) per tool station, part builder, tool anvil.
9. **Tank/Gauge/Fuel**: ✅ DevMod ha modelli con knob, gauge indicator, fuel cap per distinzione visiva.

---

## 5) Gap diretto TConstruct vs DevMod (modelli da allineare)

### Completati ✅

- **Controller**: ✅ Aggiunto stato `in_structure` + modello `foundry_controller_unformed.json`. Blockstate aggiornato con 16 varianti (active x facing x in_structure).
- **Drain**: ✅ Aggiunto `facing` + `in_structure` con modelli `foundry_drain_active.json` e `foundry_drain_inactive.json`.
- **Duct**: ✅ Aggiunto `in_structure` con modello `foundry_duct_center_active.json`. Mantiene sistema pipe multi-connessione.
- **Chute**: ✅ Aggiunto `facing` + `in_structure` con modelli `foundry_chute_active.json` e `foundry_chute_inactive.json`.
- **Faucet**: ✅ Supporto `facing=down/up` con modello dedicato `foundry_faucet_down.json`. Geometria faucet migliorata con spout + base.
- **Casting table/basin**: ✅ Già presenti modelli open-top con gambe e `has_item` cover.
- **Tool tables**: ✅ Modelli `foundry_tool_station.json`, `foundry_part_builder.json`, `foundry_tool_anvil.json` aggiornati con geometria tavolo (gambe + top).
- **Tank**: ✅ Modello `foundry_tank.json` aggiornato con knob frontale e texture top dedicata.
- **Gauge**: ✅ Modello `foundry_gauge.json` aggiornato con indicatore gauge superiore e knob.
- **Fuel Tank**: ✅ Modello `foundry_fuel_tank.json` aggiornato con fuel cap superiore e knob su entrambi i lati.

### Rinviati / Non implementati

- **Channel tri-state**: ⏸️ Rinviato - richiede refactoring significativo del sistema valvole nel block entity. Il sistema attuale con boolean per lati funziona bene come sistema pipe.
- **Fluid loader dinamico**: ⏸️ Non implementato - DevMod usa renderer BE per fluidi invece di loader JSON custom.

### Completati recentemente ✅

- **Tool/parts loader dinamico**: ✅ Implementato sistema `IGeometryLoader` custom:
  - `FoundryPartModelLoader` per parti singole (head, handle, binding)
  - `FoundryToolModelLoader` per tool completi multi-parte
  - `FoundryMaterialRenderInfo` + `FoundryMaterialRenderInfoLoader` per gestione texture/colore materiali
  - Supporto fallback automatico da colore materiale in `FoundryMaterialDefinition`
  - Caching modelli per performance
  - Modelli JSON aggiornati con loader custom (`"loader": "devmod:foundry_part"`, `"loader": "devmod:foundry_tool"`)

