# WeaponEditorScreen Feature Audit
> DEPRECATED: legacy audit of `WeaponEditorScreen`; use `docs/editor-design-system/README.md` and `docs/editor-design-system/15-weapon-properties.md`.

## Overview
**File**: `/src/main/java/com/frenkvs/devmod/WeaponEditorScreen.java`
**Size**: ~2400 lines
**Purpose**: Complete Item Editor with multiple tabs for weapon/item customization

---

## TABS (5 total)

### 1. STATS Tab
- **Body Part Multipliers**: Head, Body, Legs (colored sliders)
- **Armor Penetration**: 0-100% slider
- **Bonus Damage**: -10 to +50 slider
- **Visual**: Colored indicator per stat, pulse animation on change
- **Input**: Slider + EditBox for precise values

### 2. ENCHANTS Tab
- **List current enchantments** with search/filter
- **Add button** opens enchantment picker
- **Per-enchantment controls**:
  - Level +/- buttons
  - Remove (X) button
  - Level display
- **Scroll support** with visual scrollbar
- **Search box** for filtering

### 3. DURABILITY Tab
- **Current Durability** (EditBox)
- **Max Durability** (display only)
- **Repair Cost** (EditBox)
- **Unbreakable toggle** (clickable row)
- **Quick Actions**:
  - Full Repair button
  - Set to 1 button (break)

### 4. ATTRIBUTES Tab
- **List all attributes** (vanilla + modded)
- **Add button** opens attribute picker
- **Per-attribute controls**:
  - Value input field
  - Operation selector (+, x, x%)
  - Remove button
- **Auto-loads common attributes**: Attack Damage, Attack Speed, Attack Knockback, Armor, Armor Toughness, Movement Speed

### 5. COMPONENTS Tab (Read-Only)
- Lists all DataComponents on the item
- Informational only, no editing

---

## UI FEATURES

### Preview Panel (Top-Left)
- **3D rotating item preview** (auto-rotation)
- **Item info**: Name, Registry ID
- **Quick stats**: Enchantment count, Durability %

### Mode Toggle (Top-Right)
- **GLOBAL** (orange): Edits all items of same type
- **SPECIFIC** (green): Edits only this item instance

### Status Messages
- Feedback messages with color coding
- Auto-fade after 3 seconds

---

## ADVANCED FEATURES

### Undo/Redo System
- **Undo button** (up to 50 states)
- **Redo button**
- State snapshots include: stats, enchantments, attributes, durability, unbreakable

### History Panel
- Shows timestamped edit history
- Scrollable list
- Clear history button
- Uses `ItemEditorDataManager` for persistence

### Presets System
- **Save preset**: Name input + save button
- **Load preset**: Click to apply
- **Delete preset**: X button per preset
- Scrollable list
- Persistent storage via `ItemEditorDataManager`

### Templates
- **Suggested template** based on item type
- **All templates** list with scroll
- Auto-suggests best match for current item

### Export/Import
- **Export button**: Copies config to clipboard
- **Import button**: Imports config from clipboard
- (Partially implemented - reserved for future)

### Enchantment Picker Overlay
- **Filter tabs**: All, Favorites (★), Compatible, Weapon, Armor, Tool
- **Search box**
- **Count display** (X found)
- **Scrollable list** with namespace color coding
- **Favorite star** indicator
- **Tooltips**: ID, Max Level, Description
- **Right-click to favorite**
- Click to add

### Attribute Picker Overlay
- **Search box**
- **Count display** (X found)
- **Scrollable list**
- **Namespace color coding** (modded = cyan)
- **Full ID display**
- Click to add

---

## BOTTOM BUTTONS

### Top Row (Small buttons)
1. **Undo** (↩)
2. **Redo** (↪)
3. **History**
4. **Export**
5. **Import**
6. **Template**
7. **Presets**

### Main Row (Large buttons)
1. **Reset** - Revert to original
2. **Cancel** - Close without saving
3. **Apply** - Save changes

---

## DATA MANAGEMENT

### ItemEditorDataManager Integration
- **Favorites**: Store favorite enchantments
- **History**: Edit history with timestamps
- **Presets**: Named stat configurations
- **Templates**: Item-type specific defaults

---

## COMPARISON WITH ArmorEditorScreen

| Feature | WeaponEditor | ArmorEditor | Status |
|---------|--------------|-------------|--------|
| **TABS** | 5 (Stats, Enchants, Durability, Attributes, Components) | 5 (Protection, Attributes, Enchants, Durability, Effects) | ✅ Same count |
| **3D Preview** | Item (auto-rotate) | Player (mouse-responsive) | ✅ Different but OK |
| **Mode Toggle** | Global/Specific | Global/Specific | ✅ Present |
| **Sliders** | Body part mults | Protection reductions | ✅ Present |
| **Enchantments** | Full picker with filters | Basic add/remove | ⚠️ Missing: Filters, Favorites, Tooltips |
| **Attributes** | Full picker | Simple list | ⚠️ Missing: Picker overlay |
| **Durability** | Full controls | Full controls | ✅ Present |
| **Undo/Redo** | Yes | No | ❌ MISSING |
| **History Panel** | Yes | No | ❌ MISSING |
| **Presets** | Yes | No | ❌ MISSING |
| **Templates** | Yes | No | ❌ MISSING |
| **Export/Import** | Yes | No | ❌ MISSING |
| **Dirty State** | No | Yes | ✅ ArmorEditor has it |
| **Search in Enchants** | Yes | Yes | ✅ Present |
| **Scroll Support** | Yes | Yes | ✅ Present |

---

## MISSING IN ArmorEditorScreen

### High Priority
1. **Undo/Redo System** - Critical for usability
2. **History Panel** - Track changes
3. **Presets System** - Save/load configurations
4. **Enchantment Picker Filters** - Filter by All/Favorites/Compatible/etc.
5. **Enchantment Tooltips** - Show max level, description
6. **Favorite Enchantments** - Star system

### Medium Priority
7. **Templates** - Suggest configs based on armor type
8. **Export/Import** - Clipboard support
9. **Attribute Picker Overlay** - Add modded attributes

### Low Priority (ArmorEditor has alternatives)
10. **Components Tab** - Read-only NBT viewer (could be useful)

---

## RECOMMENDATIONS

To achieve feature parity with WeaponEditorScreen, ArmorEditorScreen needs:

1. **Add EditorState class** for undo/redo snapshots
2. **Add undoStack/redoStack** deques
3. **Add saveUndoState()** method called before changes
4. **Add undo()/redo()** methods
5. **Add renderHistoryPanel()**
6. **Add renderPresetMenu()**
7. **Add renderTemplateMenu()**
8. **Enhance enchantment picker** with filters and favorites
9. **Add attribute picker overlay**
10. **Add export/import via clipboard**

Estimated effort: ~400-600 lines of additional code
