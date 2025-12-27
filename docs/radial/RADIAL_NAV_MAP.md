# DevMod Radial Menu Navigation Map

**Version:** 1.0
**Last Updated:** 2024-12-27
**Status:** Proposed Architecture

This document defines the optimal navigation structure for the radial menu.

---

## Design Principles

1. **Radial-First**: Every feature accessible from radial (keybinds are shortcuts)
2. **Max 3 Levels Deep**: Root > Category > Action (rarely: Sub-Action)
3. **6-8 Items Per Level**: Optimal for radial UX
4. **Contextual Visibility**: Hide irrelevant actions based on context
5. **Vanilla Icons Only**: ItemStack icons from Minecraft items/blocks

---

## MacroCategory Hub (Root Level)

The center hub shows 6 macro-categories arranged in a hexagon:

```
        ANALYZE (Blue)
           /    \
    TELEMETRY    COMBAT
     (Cyan)       (Red)
         |         |
      ARENA ---- PLAY
    (Emerald)   (Green)
           \    /
         TOOLS (Orange)
```

| MacroCategory | Icon (ItemStack) | Color | Description |
|---------------|------------------|-------|-------------|
| ANALYZE | `SPYGLASS` | Blue | Debug, spatial analysis, visualization |
| TELEMETRY | `COMPARATOR` | Cyan | Telemetry, dashboards, exports |
| COMBAT | `DIAMOND_SWORD` | Red | Combat tools, abilities, heatmaps |
| ARENA | `SPAWNER` | Emerald | Arena ops, templates, autosmoke |
| PLAY | `TOTEM_OF_UNDYING` | Green | Quests, endurance, party |
| TOOLS | `CRAFTING_TABLE` | Orange | Settings, editors, testing |

---

## Category Structure by MacroCategory

### ANALYZE (Max 8 categories)

```
ANALYZE
├── Debug Overlays [REDSTONE]
│   ├── Main Overlay         [REDSTONE_TORCH]
│   ├── Body Parts           [SKELETON_SKULL]
│   ├── Enable All           [GLOWSTONE]
│   └── Disable All          [COAL]
├── Heatmaps [MAP]
│   ├── Death                [WITHER_ROSE]
│   ├── Movement             [LEATHER_BOOTS]
│   ├── Camping              [CAMPFIRE]
│   ├── Stuck                [COBWEB]
│   ├── Aggro Drop           [ENDER_PEARL]
│   ├── Kiting               [FEATHER]
│   ├── Clear Current        [WATER_BUCKET]
│   └── Clear All            [LAVA_BUCKET]
├── Spatial [STRUCTURE_BLOCK]
│   ├── Room Bounds          [GLASS]
│   ├── Pathfinding          [COMPASS]
│   ├── Line of Sight        [ENDER_EYE]
│   ├── Vertical Levels      [LADDER]
│   ├── Safe Spots           [TORCH]
│   └── Spawnability         [SPAWNER]
├── Performance [CLOCK]
│   ├── FPS Tracker          [CLOCK]
│   ├── Profiler             [BOOK]
│   ├── Entity Density       [PIG_SPAWN_EGG]
│   └── Chunk Performance    [GRASS_BLOCK]
├── Attributes [GOLDEN_APPLE]
│   ├── Monitor              [SPYGLASS]
│   └── Aggro Range          [BOW]
├── Native Debug [COMMAND_BLOCK]
│   ├── Entity Pathing       [COMPASS]
│   ├── Entity Goals         [TARGET]
│   ├── Entity Brains        [CARVED_PUMPKIN]
│   ├── POI                  [BELL]
│   ├── Raids                [CROSSBOW]
│   ├── Bees                 [HONEYCOMB]
│   ├── Game Events          [NOTE_BLOCK]
│   └── Structures           [MOSSY_COBBLESTONE]
├── Economy [GOLD_INGOT]
│   ├── Toggle               [GOLD_INGOT]
│   ├── Cycle View           [GOLD_NUGGET]
│   └── Cycle Sort           [HOPPER]
└── Light [LANTERN]
    ├── Overlay              [LANTERN]
    ├── Dark Areas           [COAL_BLOCK]
    └── Spawnable            [TORCH]
```

### TELEMETRY (Max 6 categories)

```
TELEMETRY
├── Dashboard [LECTERN]
│   ├── Open Client UI       [BOOK]
│   ├── Start Server         [LEVER]
│   ├── Stop Server          [BARRIER]
│   └── Status               [OBSERVER]
├── Export [CHEST]
│   ├── All                  [ENDER_CHEST]
│   ├── CSV                  [PAPER]
│   ├── JSON                 [WRITABLE_BOOK]
│   ├── PNG                  [PAINTING]
│   ├── Heatmaps             [MAP]
│   └── Damage Stats         [DIAMOND_SWORD]
├── Dump [DROPPER]
│   ├── Weapons              [IRON_SWORD]
│   ├── Rooms                [STRUCTURE_BLOCK]
│   ├── Fights               [SHIELD]
│   └── Minions              [ZOMBIE_HEAD]
├── Scan [SPYGLASS]
│   ├── Light All            [GLOWSTONE]
│   └── Light Room           [SEA_LANTERN]
├── Analysis [ENCHANTED_BOOK]
│   ├── Desire Lines         [LEAD]
│   ├── Backtracking         [RECOVERY_COMPASS]
│   └── Dungeons             [MOSSY_COBBLESTONE]
└── Config [COMPARATOR]
    ├── Enable/Disable       [LEVER]
    ├── Hits                  [DIAMOND_AXE]
    ├── Deaths                [SKELETON_SKULL]
    ├── Spawns                [SPAWNER]
    └── Reload                [REPEATER]
```

### COMBAT (Max 6 categories)

```
COMBAT
├── Impact HUD [DAMAGED_ANVIL]
│   ├── Toggle               [ANVIL]
│   ├── 3D Toggle            [ITEM_FRAME]
│   ├── History              [BOOK]
│   ├── DPS                  [DIAMOND_SWORD]
│   ├── Position             [ARMOR_STAND]
│   ├── Offset               [PISTON]
│   ├── Export Preset        [CHEST]
│   ├── Import Preset        [HOPPER]
│   ├── Reset Defaults       [BARRIER]
│   └── Dismiss              [RED_WOOL]
├── VFX [FIREWORK_ROCKET]
│   ├── Toggle               [FIREWORK_ROCKET]
│   ├── Vortex               [WIND_CHARGE]
│   ├── Slash                [IRON_SWORD]
│   ├── Lines                [STICK]
│   ├── Intensity: Low       [COAL]
│   ├── Intensity: Med       [IRON_INGOT]
│   ├── Intensity: High      [GOLD_INGOT]
│   ├── Intensity: Max       [DIAMOND]
│   └── Reset Defaults       [BARRIER]
├── Abilities [ENDER_PEARL]
│   ├── Dash                 [FEATHER]
│   └── Dodge                [LEATHER_BOOTS]
├── Tracking [TARGET]
│   ├── Boss Phase           [DRAGON_EGG]
│   ├── Skill Efficacy       [ENCHANTED_BOOK]
│   └── Heatmaps             [MAP]
├── Effects [BLAZE_POWDER]
│   ├── Screen Shake         [SLIME_BALL]
│   ├── Projectile Trails    [ARROW]
│   └── Test Shake           [TNT]
└── Config [COMPARATOR]
    └── Body Part Detection  [SKELETON_SKULL]
```

### ARENA (Max 6 categories)

```
ARENA
├── Templates [STRUCTURE_BLOCK]
│   ├── List                 [PAPER]
│   ├── Info                 [BOOK]
│   ├── Reload               [REPEATER]
│   ├── Validate             [OBSERVER]
│   └── Metrics              [CLOCK]
├── Build [BRICKS]
│   ├── Create               [SCAFFOLDING]
│   └── Status               [OBSERVER]
├── Force Override [COMMAND_BLOCK]
│   ├── Force Template       [PISTON]
│   ├── Clear                [BARRIER]
│   └── Status               [OBSERVER]
├── Autosmoke [CAMPFIRE]
│   ├── Run                  [FLINT_AND_STEEL]
│   ├── Status               [OBSERVER]
│   └── Schedule             [CLOCK]
├── HUD [ITEM_FRAME]
│   ├── Toggle               [GLOW_ITEM_FRAME]
│   ├── On                   [GREEN_WOOL]
│   ├── Off                  [RED_WOOL]
│   └── Status               [OBSERVER]
└── Quick Test [LIGHTNING_ROD]
    └── Open Wizard          [NETHER_STAR]
```

### PLAY (Max 6 categories)

```
PLAY
├── Endurance [TOTEM_OF_UNDYING]
│   ├── Start Quest          [NETHER_STAR]
│   ├── Continue             [EXPERIENCE_BOTTLE]
│   ├── Exit                 [OAK_DOOR]
│   ├── Settings             [COMPARATOR]
│   ├── Status               [BOOK]
│   ├── Shop                 [EMERALD]
│   ├── HUD Toggle           [ITEM_FRAME]
│   └── Details Toggle       [SPYGLASS]
├── Quest [FILLED_MAP]
│   ├── Editor               [WRITABLE_BOOK]
│   ├── HUD Toggle           [ITEM_FRAME]
│   └── Complete Task        [EMERALD]
├── Party [PLAYER_HEAD]
│   └── Open                 [PLAYER_HEAD]
├── Communication [BOOK_AND_QUILL]
│   ├── Mailbox              [CHEST]
│   └── Tester Tasks         [PAPER]
├── Perks [ENCHANTED_BOOK]
│   └── Selection            [ENCHANTED_BOOK]
└── Config [COMPARATOR]
    └── Badge Popups         [EMERALD]
```

### TOOLS (Max 8 categories)

```
TOOLS
├── Settings [COMPARATOR]
│   └── Open                 [COMPARATOR]
├── Editors [CRAFTING_TABLE]
│   ├── Item (Auto)          [DIAMOND_PICKAXE]
│   ├── Weapon               [DIAMOND_SWORD]
│   ├── Armor                [DIAMOND_CHESTPLATE]
│   ├── Shield               [SHIELD]
│   ├── General              [STICK]
│   ├── Recipe               [CRAFTING_TABLE]
│   ├── Food                 [GOLDEN_APPLE]
│   ├── Fuel                 [COAL]
│   ├── Usable               [POTION]
│   └── Stamina              [EXPERIENCE_BOTTLE]
├── Mob Tools [ZOMBIE_HEAD]
│   ├── Config               [ZOMBIE_HEAD]
│   └── Equipment            [IRON_SWORD]
├── Testing [HOPPER]
│   ├── Testing Hub          [COMMAND_BLOCK]
│   ├── QA Testing           [PAPER]
│   ├── Quick Test           [LIGHTNING_ROD]
│   ├── Badge Tests          [EMERALD]
│   ├── VoxelLab             [GLASS]
│   ├── VoxelLab UI          [TINTED_GLASS]
│   └── Tester Tasks         [PAPER]
├── QA Session [WRITTEN_BOOK]
│   ├── Start Session        [BOOK_AND_QUILL]
│   ├── Resume               [EXPERIENCE_BOTTLE]
│   ├── Save Report          [WRITABLE_BOOK]
│   ├── Copy Report          [PAPER]
│   ├── Pass                 [GREEN_WOOL]
│   ├── Fail                 [RED_WOOL]
│   ├── Skip                 [YELLOW_WOOL]
│   └── Auto                 [OBSERVER]
├── Game Design [ENCHANTED_BOOK]
│   ├── Reload               [REPEATER]
│   ├── Save                 [CHEST]
│   ├── Reset                [BARRIER]
│   ├── Resonance            [AMETHYST_SHARD]
│   ├── Contracts            [BOOK_AND_QUILL]
│   ├── Signatures           [GOLDEN_SWORD]
│   ├── Nemesis              [WITHER_SKELETON_SKULL]
│   ├── Tide                 [TRIDENT]
│   └── Presets              [BUNDLE]
├── Shortcuts [COMMAND_BLOCK]
│   ├── Creative Mode        [DIAMOND_BLOCK]
│   ├── Survival Mode        [GRASS_BLOCK]
│   ├── Heal                 [GOLDEN_APPLE]
│   ├── Time: Day            [SUNFLOWER]
│   ├── Time: Night          [CLOCK]
│   └── Weather: Clear       [WHITE_WOOL]
├── Room Bounds [STRUCTURE_BLOCK]
│   └── Editor               [STRUCTURE_BLOCK]
├── Help [BOOK]
│   ├── Quick Help           [BOOK]
│   ├── Welcome              [WRITTEN_BOOK]
│   ├── Onboarding           [KNOWLEDGE_BOOK]
│   └── Keybinds             [TRIPWIRE_HOOK]
└── Admin [COMMAND_BLOCK_MINECART]
    ├── Mailbox Admin        [CHEST_MINECART]
    └── News Admin           [PAPER]
```

---

## Contextual Visibility Rules

Actions should be hidden (not grayed) when not applicable:

### Context: In Endurance Quest

**Show:**
- PLAY > Endurance > Continue, Exit, Shop, HUD
- COMBAT > All

**Hide:**
- PLAY > Endurance > Start
- ARENA > Build, Create
- TOOLS > Editors (prevent accidental changes)

### Context: In Arena Instance

**Show:**
- ARENA > All
- ANALYZE > All
- COMBAT > All

**Hide:**
- PLAY > Endurance (except viewing)

### Context: Developer Mode Off

**Hide:**
- ANALYZE > Native Debug (all)
- TOOLS > Admin (all)
- Some advanced telemetry features

### Context: Not Tester

**Hide:**
- TOOLS > Testing > Tester Tasks
- TOOLS > QA Session (all)

### Context: In Combat

**Disable (gray out, don't execute):**
- Screen-opening actions (prevent combat interruption)
- Destructive actions

**Allow:**
- Toggle overlays
- Abilities
- Quick toggles

---

## Search System

The radial menu supports type-to-search:

1. Press any alphanumeric key to enter search mode
2. Search box appears at top center
3. Results shown in a list overlay
4. Navigate with arrow keys or mouse
5. Enter to execute, Escape to cancel

**Search targets:**
- Action label (localized)
- Action ID
- Description (localized)
- Command hint (if any)

---

## Favorites System

- Inner ring shows up to 6 favorites
- Long-press action to add/remove from favorites
- Favorites persist across sessions
- Contextual favorites (different per context)

---

## Interaction Model

### Click (Single)
- Execute action immediately
- For screens: open screen
- For toggles: toggle state

### Hold (Long Press, 500ms)
- Show action details panel
- Options:
  - Add/remove favorite
  - View/edit keybind
  - Copy action ID
  - View command hint

### Right-Click
- Quick context menu
- Same as long-press options

### Scroll Wheel
- In category: scroll through items if > 8
- In search: scroll results

### Escape
- Go back one level
- At root: close menu

### Backspace (in navigation)
- Go back one level

---

## Visual Feedback

### Action States

| State | Visual | Behavior |
|-------|--------|----------|
| Available | Full color icon | Execute on click |
| Toggled ON | Glowing border + checkmark | Toggle off on click |
| Toggled OFF | Normal | Toggle on on click |
| Disabled (precondition) | Grayed out | Show reason on hover |
| Hidden (visibility) | Not rendered | - |
| Requires Confirm | Red border | Show confirm dialog |

### Animation

| Event | Animation |
|-------|-----------|
| Open menu | Fade in + scale from 0 |
| Select category | Smooth transition |
| Hover item | Scale up 1.1x + glow |
| Execute action | Flash + shrink |
| Error | Shake + red flash |

---

## Sound Feedback

| Event | Sound |
|-------|-------|
| Open menu | `UI_BUTTON_CLICK` (soft) |
| Navigate category | `UI_BUTTON_CLICK` |
| Hover item | None (visual only) |
| Execute action | `ENTITY_EXPERIENCE_ORB_PICKUP` |
| Toggle ON | `BLOCK_NOTE_BLOCK_PLING` (high) |
| Toggle OFF | `BLOCK_NOTE_BLOCK_BASS` |
| Error/blocked | `BLOCK_NOTE_BLOCK_DIDGERIDOO` |
| Confirm required | `BLOCK_NOTE_BLOCK_BELL` |

---

## Implementation Priority

### Phase 1: Core Structure
1. Update MacroCategory enum with new icons
2. Implement ActionKeybindRegistry
3. Update RadialMenuScreen category building

### Phase 2: Keybind Integration
1. Migrate RenderEvents handlers to ActionRegistry
2. Add keybind hints to action details
3. Implement keybind remapping from radial

### Phase 3: Contextual System
1. Implement visibility predicates for all actions
2. Add context detection (quest, arena, combat, etc.)
3. Test contextual hiding

### Phase 4: Polish
1. Favorites system persistence
2. Search improvements
3. Sound/animation refinements

---

## Acceptance Criteria

### Functional
- [ ] All 298 ActionIds accessible from radial
- [ ] All 37 keybinds show in action details
- [ ] Search finds all actions
- [ ] Favorites persist across sessions
- [ ] Contextual hiding works correctly

### UX
- [ ] Max 3 clicks to any action
- [ ] Icons clearly recognizable
- [ ] Tooltips explain every action
- [ ] Sound feedback for all interactions
- [ ] Smooth 60fps animations

### Technical
- [ ] No orphan features (CI check)
- [ ] Telemetry logs all invocations
- [ ] No duplicate action registrations
- [ ] All commands route through ActionRegistry
