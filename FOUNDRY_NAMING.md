# Foundry Asset Naming

## Goal
Use DevMod-owned, descriptive resource locations for Foundry tool rendering.

## Base path
- devmod:item/foundry/

## Tool part textures
- devmod:item/foundry/tool/<tool>/<part>
- devmod:item/foundry/tool/<tool>/large/<part>
- devmod:item/foundry/tool/parts/<part>
- devmod:item/foundry/tool/armor/<set>/<piece>/<part>

## Modifier overlays
- devmod:item/foundry/tool/<tool>/modifiers/
- devmod:item/foundry/tool/<tool>/modifiers/<state>/
- devmod:item/foundry/tool/<tool>/large/modifiers/
- devmod:item/foundry/tool/<tool>/large/modifiers/<state>/
- devmod:item/foundry/tool/armor/<set>/<piece>/modifiers/
- devmod:item/foundry/tool/ammo/<type>_modifiers/

## Material overrides (optional)
- devmod:item/foundry/<material>_<part>

## Legacy mapping
- tconstruct:item/tool/<...> -> devmod:item/foundry/tool/<...>
