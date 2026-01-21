# Slimeknights Package Census (DevMod)

## Scopo
- Cartella locale con subset Mantle/TConstruct per rendering e modelli (client side).
- Fornisce loader, util e dati per materiali/modifier/texture usati dai modelli Foundry.
- Diversi componenti sono stub/minimali: servono solo a far girare i modelli, non gameplay completo.

## Stato attuale
- `src/main/java/slimeknights` rimosso: i loader e utilita` equivalenti sono ora sotto `com.devmod.foundry`.
- Alias Mantle/TCon rimossi da `FoundryClientSetup` (solo loader `devmod:` registrati).
- Modelli/texture DevMod ripuntati a loader `devmod:foundry_*` e namespace `devmod:` (nessun riferimento `tconstruct:` nei JSON DevMod).
- Questo file resta come snapshot per la migrazione.

## Integrazione con Foundry (com.devmod)

### Loader e Listener
- `com.devmod.foundry.client.FoundryClientSetup` registra i geometry loader DevMod (`foundry_part`, `foundry_tool`, `foundry_tank`, `foundry_material`, `foundry_tank_model`, `foundry_connected`, `retextured`, `item_layer`, `colored_block`, `nbt_key`, `foundry_material_block`, `foundry_fluid_texture`, `foundry_fluid_container`, `foundry_gui`).
- `com.devmod.foundry.client.FoundryClientSetup` registra `DynamicTextureLoader` e `ModifierModelManager` come reload listener.
- `com.devmod.foundry.client.FoundryClientSetup` registra `FoundryMaterialRenderInfoLoader` per caricare render info materiali custom.
- `com.devmod.foundry.client.FoundryClientSetup` registra `ToolModel.COLOR_HANDLER` solo per gli item tool di `FoundryToolItems` (non per parti/pattern).

### Adapter Materiali
- `slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader` delega a `FoundryMaterialRenderInfoLoader`.

### Adapter Tool NBT
- `slimeknights.tconstruct.library.tools.nbt.ToolStack` costruisce view da `FoundryToolData` per modifier/embossment.
- `slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT` legge materiali da `FoundryToolData`.

### Implementazioni Foundry
- `com.devmod.foundry.tool.FoundryToolItem` implementa `IModifiable` per rendering tool.
- `com.devmod.foundry.tool.FoundryPartItem` implementa `IMaterialItem` e usa `MaterialVariantId`.
- `com.devmod.foundry.block.entity.FoundryTankBlockEntity` usa `ModelProperties` per dati fluido in ModelData.
- `com.devmod.foundry.block.entity.FoundryTankBlockEntity` scrive sia le key `FoundryTankModelLoader.*` sia `ModelProperties.*` nello stesso ModelData.

## Inventario per package

### slimeknights.mantle
- `slimeknights/mantle/Mantle.java`: bootstrap minimale (MOD_ID, logger, description id).
- `slimeknights/mantle/block/IMultipartConnectedBlock.java`: helper per connessioni su multipart + CTM; gestisce bool props e update.
- `slimeknights/mantle/data/listener/IEarlySafeManagerReloadListener.java`: reload listener semplificato per risorse.
- `slimeknights/mantle/data/listener/ResourceValidator.java`: cache di ResourceLocation per validazioni veloci.
- `slimeknights/mantle/client/render/FluidCuboid.java`: definisce cubo fluido + facce e parsing JSON.
- `slimeknights/mantle/util/JsonHelper.java`: util JSON + resource loading.
- `slimeknights/mantle/util/RetexturedHelper.java`: NBT per retexturing + ModelData per block.
- `slimeknights/mantle/util/LogicHelper.java`: util list getOrDefault.
- `slimeknights/mantle/util/ItemLayerPixels.java`: prevenzione z-fighting per item layer.
- `slimeknights/mantle/util/ReversedListBuilder.java`: builder per layering da cima a fondo.

### slimeknights.mantle.client.model
- `slimeknights/mantle/client/model/NBTKeyModel.java`: seleziona texture in base a NBT key.
- `slimeknights/mantle/client/model/RetexturedModel.java`: retexture dinamico da block (ModelData).
- `slimeknights/mantle/client/model/connected/ConnectedModel.java`: CTM runtime con cache per connessioni.
- `slimeknights/mantle/client/model/connected/ConnectedModelRegistry.java`: registra predicate e mappe CTM.
- `slimeknights/mantle/client/model/util/ModelTextureIteratable.java`: iterator sui texture map di un BlockModel.
- `slimeknights/mantle/client/model/util/ColoredBlockModel.java`: estende SimpleBlockModel con colore/luminosity per elemento.
- `slimeknights/mantle/client/model/util/SimpleBlockModel.java`: block model semplificato + bake utilities.
- `slimeknights/mantle/client/model/util/GeometryContextWrapper.java`: wrapper di IGeometryBakingContext.
- `slimeknights/mantle/client/model/util/ExtraTextureContext.java`: aggiunge texture override in bake context.
- `slimeknights/mantle/client/model/util/DynamicBakedWrapper.java`: helper per modelli dinamici con ModelData.
- `slimeknights/mantle/client/model/util/ModelHelper.java`: util per texture particle e parsing JSON vecchio.
- `slimeknights/mantle/client/model/util/MantleItemLayerModel.java`: clone ItemLayerModel con colore e luminosity.

### slimeknights.tconstruct
- `slimeknights/tconstruct/TConstruct.java`: bootstrap minimale (MOD_ID, logger, resource helpers).
- `slimeknights/tconstruct/common/config/Config.java`: config stub (log missing textures, tank render, ecc).
- `slimeknights/tconstruct/smeltery/item/TankItem.java`: legge fluid in item per override modello tank.

### slimeknights.tconstruct.library.client.model
- `slimeknights/tconstruct/library/client/model/DynamicTextureLoader.java`: valida texture dinamiche (modifiers) via ResourceValidator.
- `slimeknights/tconstruct/library/client/model/FluidContainerModel.java`: modello item per contenitori di fluido con tint.
- `slimeknights/tconstruct/library/client/model/UniqueGuiModel.java`: usa modello alternativo in GUI.
- `slimeknights/tconstruct/library/client/model/ModelProperties.java`: ModelData keys (fluid, materiali).
- `slimeknights/tconstruct/library/client/model/block/IncrementalFluidCuboid.java`: cubo fluido con step di riempimento.
- `slimeknights/tconstruct/library/client/model/block/TankModel.java`: modello tank con fluido interno + override item.
- `slimeknights/tconstruct/library/client/model/block/FluidTextureModel.java`: sostituisce texture fluide da ModelData.

### slimeknights.tconstruct.library.client.model.tools
- `.../ToolModel.java`: renderer tool completo (materiali + modifier overlay + ammo); caching e tint.
- `.../MaterialModel.java`: item layer per singolo materiale (usa MaterialRenderInfo).
- `.../MaterialBlockModel.java`: block-model retextured per tools/parts/anvil.
- `.../NestedOverrides.java`: delega overrides annidati evitando loop.

### slimeknights.tconstruct.library.client.materials
- `.../MaterialRenderInfo.java`: descrive texture/fallback/color per materiale.
- `.../MaterialRenderInfoLoader.java`: adapter verso Foundry render info (materiali).

### slimeknights.tconstruct.library.client.modifiers
- `.../ModifierModelManager.java`: carica `tinkering/modifiers.json`, registra modelli e bake per tool.
- `.../IBakedModifierModel.java`: interfaccia per quads/tint dinamici.
- `.../IUnbakedModifierModel.java`: factory per modelli modifier.
- `.../NormalModifierModel.java`: overlay statico standard.
- `.../DyedModifierModel.java`: colore da NBT int (persistent data).
- `.../PotionModifierModel.java`: colore da potion.
- `.../MaterialModifierModel.java`: overlay che usa texture/materiale.
- `.../FluidModifierModel.java`: overlay fluido (tint + emissive).
- `.../TankModifierModel.java`: variante tank full/partial.
- `.../TrimModifierModel.java`: overlay trims armor (usa registries trims).

### slimeknights.tconstruct.library.materials.definition
- `.../IMaterial.java`: interface minimale, UNKNOWN_ID.
- `.../MaterialVariantId.java`: id materiale + variant, parsing e suffix.

### slimeknights.tconstruct.library.modifiers
- `.../Modifier.java`: wrapper id.
- `.../ModifierId.java`: ResourceLocation id + parse.
- `.../ModifierEntry.java`: id + level.
- `.../modules/capacity/OverslimeModule.java`: stub per overslime.

### slimeknights.tconstruct.tools
- `tools/client/OverslimeModifierModel.java`: mostra overlay solo se overslime > 0.
- `tools/modules/SmashingModule.java`: stub helper fluid tank.
- `tools/modifiers/slotless/TrimModifier.java`: key NBT trim.

### slimeknights.tconstruct.library.tools
- `tools/item/IModifiable.java`: marker interface per tool modifiable (render).
- `tools/part/IMaterialItem.java`: interface per items con materiale; helper statico.
- `tools/capability/fluid/ToolTankHelper.java`: stub getFluid/getCapacity.
- `tools/nbt/IToolStackView.java`: view per tool data (item, modifiers, persistent).
- `tools/nbt/IModDataView.java`: view read-only NBT per modifiers.
- `tools/nbt/ModDataNBT.java`: legge persistent data da stack.
- `tools/nbt/ModifierNBT.java`: lista modifier.
- `tools/nbt/ToolStack.java`: costruisce tool view da FoundryToolData.
- `tools/nbt/MaterialIdNBT.java`: materiali da FoundryToolData.

### slimeknights.tconstruct.library.recipe.worktable
- `.../ModifierSetWorktableRecipe.java`: stub per set di modifier nascosti.

## Stub o placeholder importanti
- `OverslimeModule.getAmount()` ritorna sempre 0.
- `ToolTankHelper.getFluid()` e `getCapacity()` ritornano vuoto/0.
- `ModifierSetWorktableRecipe.getModifierSet()` ritorna sempre set vuoto.
- `Config` usa valori fissi (no config file).

## Risorse attese / dipendenze runtime
- `tinkering/modifiers.json` per mappa modelli modifier (se assente: nessun overlay).
- Texture material: suffix usati da MaterialRenderInfo (es. `_iron`, `_tconstruct_iron`).
- ModelData keys: `ModelProperties.FLUID_STACK`, `ModelProperties.TANK_CAPACITY`, `ModelProperties.MATERIAL`, `ModelProperties.MATERIALS`.

## Loader Custom Foundry (paralleli a slimeknights)

- `FoundryToolModelLoader` (`devmod:foundry_tool`): rendering tool multi-part, usa `FoundryMaterialRenderInfo` per texture.
- `FoundryPartModelLoader` (`devmod:foundry_part`): rendering singole parti, usa `FoundryPartItem` e `FoundryMaterialRenderInfo`.
- `FoundryTankModelLoader` (`devmod:foundry_tank`): rendering tank con fluido interno.
- `FoundryTankModel` (`devmod:foundry_tank_model`): tank dinamico con cubo fluido incrementale + GUI opzionale.
- `FoundryMaterialBlockModel` (`devmod:foundry_material_block`): block model per anvil/parti con retexturing materiali.
- `FoundryFluidTextureModel` (`devmod:foundry_fluid_texture`): sostituzione texture fluido da ModelData.
- `FoundryUniqueGuiModel` (`devmod:foundry_gui`): modello alternativo per GUI.
- Questi loader sostituiscono completamente Mantle/TCon (nessun alias attivo).

## Modelli DevMod migrati a loader DevMod (ex Mantle/TCon)

- Residui Mantle/TCon in `assets/devmod/models/block/tconstruct`: 0.
- `src/main/resources/assets/devmod/models/block/tconstruct/template/tank_knob.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/controller_fluid.json` (`devmod:foundry_fluid_texture`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/tinker_station.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/chute.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/tinkers_anvil.json` (`devmod:foundry_material_block`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/crafting_station.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/lantern_hanging.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/io_fluid.json` (`devmod:foundry_fluid_texture`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/lantern.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/io.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/half_tank.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/controller.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/template/tank.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/part_builder.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/scorched_anvil.json` (`devmod:foundry_material_block`)
- `src/main/resources/assets/devmod/models/block/tconstruct/table/modifier_worktable.json` (`devmod:retextured`)
- `src/main/resources/assets/devmod/models/block/tconstruct/smeltery/tank/casting_tank.json` (`devmod:foundry_tank_model`)
- `src/main/resources/assets/devmod/models/block/tconstruct/foundry/proxy_tank.json` (`devmod:foundry_gui`)
- `src/main/resources/assets/devmod/models/block/tconstruct/foundry/controller/alloyer.json` (`devmod:foundry_tank_model`)

## Stato overlay Foundry (modifiers)

- Overlay rigenerati per tutti i root `devmod:item/foundry/tool/**/modifiers`.
- Overlay rinominati con prefisso `devmod_` per allineare gli ID modifier DevMod.
- Copertura gruppi chiave: plate armor (boots/chestplate/helmet/leggings/shield + large_modifiers), heavy broken, light broken, staff broken, swasher livelli, melting_pan charged, minotaur_axe, flint_and_bronze, ammo, slime.
- Check automatico alpha zero: 0 PNG (ultimo run).
- Script: `scripts/generate_foundry_modifier_overlays.py` ora include root con suffisso `_modifiers`, posizionamento per overlap reale, fallback broken -> non-broken per template mancanti.

## Tabella overlay per gruppo (root -> PNG)

### Plate armor
| Root | PNG count |
| --- | ---: |
| `armor/plate/boots/modifiers` | 57 |
| `armor/plate/boots/modifiers/broken` | 57 |
| `armor/plate/chestplate/modifiers` | 57 |
| `armor/plate/chestplate/modifiers/broken` | 57 |
| `armor/plate/helmet/modifiers` | 57 |
| `armor/plate/helmet/modifiers/broken` | 57 |
| `armor/plate/leggings/modifiers` | 57 |
| `armor/plate/leggings/modifiers/broken` | 57 |
| `armor/plate/shield/modifiers` | 57 |
| `armor/plate/shield/modifiers/broken` | 57 |
| `armor/plate/shield/large_modifiers` | 57 |
| `armor/plate/shield/large_modifiers/broken` | 57 |

### Heavy tools broken
| Root | PNG count |
| --- | ---: |
| `broad_axe/modifiers/broken` | 57 |
| `broad_axe/large/modifiers/broken` | 57 |
| `cleaver/modifiers/broken` | 57 |
| `cleaver/large/modifiers/broken` | 57 |
| `excavator/modifiers/broken` | 57 |
| `excavator/large/modifiers/broken` | 57 |
| `scythe/modifiers/broken` | 57 |
| `scythe/large/modifiers/broken` | 57 |
| `sledge_hammer/modifiers/broken` | 57 |
| `sledge_hammer/large/modifiers/broken` | 57 |
| `vein_hammer/modifiers/broken` | 57 |
| `vein_hammer/large/modifiers/broken` | 57 |

### Light tools broken
| Root | PNG count |
| --- | ---: |
| `dagger/modifiers/broken` | 57 |
| `hand_axe/modifiers/broken` | 57 |
| `kama/modifiers/broken` | 57 |
| `mattock/modifiers/broken` | 57 |
| `pickaxe/modifiers/broken` | 57 |
| `pickadze/modifiers/broken` | 57 |
| `sword/modifiers/broken` | 57 |

### Staff broken
| Root | PNG count |
| --- | ---: |
| `staff/large_modifiers/broken` | 57 |
| `staff/large_modifiers/earth/broken` | 57 |
| `staff/large_modifiers/ender/broken` | 57 |
| `staff/large_modifiers/ichor/broken` | 57 |
| `staff/large_modifiers/sky/broken` | 57 |
| `staff/modifiers/broken` | 57 |
| `staff/modifiers/earth/broken` | 57 |
| `staff/modifiers/ender/broken` | 57 |
| `staff/modifiers/ichor/broken` | 57 |
| `staff/modifiers/sky/broken` | 57 |

### Swasher levels
| Root | PNG count |
| --- | ---: |
| `swasher/modifiers/1` | 57 |
| `swasher/modifiers/2` | 57 |

### Melting pan charged
| Root | PNG count |
| --- | ---: |
| `melting_pan/modifiers/charged` | 57 |

### Minotaur axe
| Root | PNG count |
| --- | ---: |
| `minotaur_axe/modifiers` | 57 |
| `minotaur_axe/modifiers/broken` | 57 |

### Flint and bronze
| Root | PNG count |
| --- | ---: |
| `flint_and_bronze/modifiers` | 57 |
| `flint_and_bronze/modifiers/broken` | 57 |

### Ammo
| Root | PNG count |
| --- | ---: |
| `ammo/arrow_modifiers` | 57 |
| `ammo/axe_modifiers` | 57 |
| `ammo/shuriken_modifiers` | 57 |

### Slime armor
| Root | PNG count |
| --- | ---: |
| `armor/slime/boot_modifiers` | 57 |
| `armor/slime/shell_modifiers` | 57 |
| `armor/slime/skull_modifiers` | 57 |
| `armor/slime/wings_modifiers` | 57 |

## Audit

- Ultimo aggiornamento: 2026-01-21
- File Java verificati: 67 (snapshot storico; package rimosso dal codebase)
- Integrazioni Foundry verificate: FoundryClientSetup, FoundryToolItem, FoundryPartItem, FoundryTankBlockEntity
- Risorse JSON tinkering: assenti (nessun overlay modifier attivo)
- Test eseguiti: FoundryToolModelSchemaTest, FoundryModifierRootCoverageTest (pass)
- Client run: avviato (nessun crash nei log, timeout CLI per processo in esecuzione)
