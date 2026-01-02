# DevMod - Mod Compatibility Inventory

> Last updated: 2025-12-30
> Status: CURRENT (verified against code)
> Source: modules registered in `ModIntegrationManager` and `ClientCompatRegistrar`.

## Summary

- CompatModule integrations (common): 33
- CompatModule integrations (client-only): 3
- Legacy/direct integrations: 4
- Total integration points: 40

## CompatModule Integrations (Common)

### P1 - UI/Input

| Mod ID | Module class |
|---|---|
| `cloth_config` | `com.devmod.compat.mods.clothconfig.ClothConfigCompat` |
| `journeymap` | `com.devmod.compat.mods.journeymap.JourneyMapCompat` |
| `emi` | `com.devmod.compat.mods.emi.EmiCompat` |

### P2 - Dimension/World

| Mod ID | Module class |
|---|---|
| `terrablender` | `com.devmod.compat.mods.terrablender.TerraBlenderCompat` |
| `c2me` | `com.devmod.compat.mods.c2me.C2MECompat` |

### P3 - Combat/Attributes

| Mod ID | Module class |
|---|---|
| `geckolib` | `com.devmod.compat.mods.geckolib.GeckoLibModuleCompat` |
| `azurelib` | `com.devmod.compat.mods.azurelib.AzureLibCompat` |
| `epicfight` | `com.devmod.compat.mods.epicfight.EpicFightCompat` |
| `curios` | `com.devmod.compat.mods.curios.CuriosCompat` |
| `accessories` | `com.devmod.compat.mods.accessories.AccessoriesCompat` |
| `irons_spellbooks` | `com.devmod.compat.mods.ironsspellbooks.IronsSpellbooksCompat` |
| `spell_engine` | `com.devmod.compat.mods.spellengine.SpellEngineCompat` |
| `spell_power` | `com.devmod.compat.mods.spellpower.SpellPowerCompat` |
| `ranged_weapon_api` | `com.devmod.compat.mods.rangedweaponapi.RangedWeaponApiCompat` |
| `apothic_attributes` | `com.devmod.compat.mods.apothicattributes.ApothicAttributesCompat` |
| `elixirum` | `com.devmod.compat.mods.elixirum.ElixirumCompat` |
| `mowziesmobs` | `com.devmod.compat.mods.mowziesmobs.MowziesMobsCompat` |
| `smartbrainlib` | `com.devmod.compat.mods.smartbrainlib.SmartBrainLibCompat` |
| `relics` | `com.devmod.compat.mods.relics.RelicsCompat` |
| `puffish_skills` | `com.devmod.compat.mods.puffishskills.PuffishSkillsCompat` |
| `archers` | `com.devmod.compat.mods.rpgseries.RpgSeriesCompat` (covers `paladins`, `wizards`, `runes`, `rogues`) |
| `shield_api` | `com.devmod.compat.mods.shieldapi.ShieldApiCompat` |
| `playeranimator` | `com.devmod.compat.mods.playeranimator.PlayerAnimatorCompat` |
| `emotecraft` | `com.devmod.compat.mods.emotecraft.EmotecraftCompat` |

### P4 - Telemetry/Performance

| Mod ID | Module class |
|---|---|
| `spark` | `com.devmod.compat.mods.spark.SparkCompat` |
| `iris` | `com.devmod.compat.mods.iris.IrisCompat` |
| `modernfix` | `com.devmod.compat.mods.modernfix.ModernFixCompat` |
| `ferritecore` | `com.devmod.compat.mods.ferritecore.FerriteCoreCompat` |
| `lithium` | `com.devmod.compat.mods.lithium.LithiumCompat` |
| `sodium` | `com.devmod.compat.mods.sodium.SodiumCompat` |
| `entityculling` | `com.devmod.compat.mods.entityculling.EntityCullingCompat` |

### P5 - QoL/Testing

| Mod ID | Module class |
|---|---|
| `easy_npc` | `com.devmod.compat.mods.easynpc.EasyNpcCompat` |
| `dummmmmmy` | `com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat` |

## CompatModule Integrations (Client-only)

| Mod ID | Module class |
|---|---|
| `controlling` | `com.devmod.client.compat.mods.controlling.ControllingCompat` |
| `yet_another_config_lib_v3` | `com.devmod.client.compat.mods.yacl.YaclCompat` |
| `fancymenu` | `com.devmod.client.compat.mods.fancymenu.FancyMenuCompat` |

## Legacy/Direct Integrations (non-CompatModule)

| Mod ID | Integration class |
|---|---|
| `pehkui` | `com.devmod.integration.PehkuiIntegration` |
| `distanthorizons` | `com.devmod.integration.DistantHorizonsIntegration` |
| `littletiles` | `com.devmod.integration.LittleTilesIntegration` |
| `puffish_attributes` | `com.devmod.integration.PufferfishIntegration` |

## Notes

- If a mod is not listed here, there is no dedicated integration in code.
- Mod detection uses `Compat.isLoaded` and reflection where needed to avoid hard dependencies.
