DevMod Foundry Porting Agent

Goal
- Port Tinkers' Construct smeltery/foundry capabilities into DevMod (NeoForge 1.21.1) with DevMod visual style (clone pulverizer palette).
- Deliver a complete, functional foundry system: multiblock detection, melting, alloying, casting, fluids, recipes, blocks, and UI where required.

Scope (per user request)
- Full feature parity for Smeltery/Foundry mechanics (controller, tanks, drains, faucets, casting table/basin, alloying, fuel handling, IO).
- Data-driven recipes and materials (JSON) to allow fast expansion.
- Multi-block structure validation (hollow cuboid, open top, size bounds configurable).
- DevMod-specific assets (blockstates, models, textures) matching clone_pulverizer style.
- Tool system port (Part Builder, Tool Station/Anvil, materials, traits/modifiers).

Design Notes
- Module namespace: com.devmod.foundry
- Resources:
  - assets/devmod/blockstates, models, textures
  - data/devmod/recipes/foundry (melting, alloying, casting)
  - data/devmod/tags (structure blocks, casts, compatible fluids)
- Config: add size limits and capacity in Config (foundry.*).

Implementation Phases
1) Module scaffolding
   - FoundryModule, FoundryBlocks, FoundryItems, FoundryBlockEntities, FoundryRecipeTypes, FoundryCreativeTab.
2) Multiblock core
   - FoundryStructure detection (hollow cuboid, open top).
   - Controller BE manages structure state and links to component BEs.
3) Fluids + fuel
   - Molten fluids (iron, gold, copper, tin, bronze).
   - Fluid tanks and bucket items.
   - Fuel handling (lava or data-driven fuels).
4) Melting + alloying
   - Melting recipes (item -> fluid).
   - Alloying recipes (fluid inputs -> fluid output).
5) Casting
   - Faucet transfer logic, casting table/basin BEs.
   - Cast items and cast tags.
6) Tool system
   - Part builder, tool station/anvil, materials, modifiers, tool definitions, and basic trait handling.

License & Attribution
- Tinkers' Construct is MIT (see tmp/tinkersconstruct/LICENSE).
- If code is copied/adapted, include MIT notice in a third_party file.

Testing
- Run: `./gradlew runClient` and verify forming structure, melting, alloying, and casting.
- Validate recipes load with `data` run: `./gradlew runData`.
