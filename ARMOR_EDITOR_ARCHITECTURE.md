# ArmorEditor System - Architecture Design Document

## Overview

The ArmorEditor system provides a dedicated interface for customizing armor properties, separate from the WeaponEditor. While weapons affect **attacker-side** damage dealing (body part multipliers, armor penetration), armor affects **victim-side** damage reduction.

This document outlines the complete architecture for implementing the ArmorEditor feature.

---

## 1. Fundamental Differences: Weapons vs Armor

### Weapons (Attacker-Side)
- Body part multipliers (head, body, arms, legs)
- Armor penetration percentage
- Base damage bonus
- Applied during `LivingIncomingDamageEvent` to **increase** final damage

### Armor (Victim-Side)
- Damage reduction percentages by type (physical, fire, magic, explosion, projectile)
- Armor value modifier (adds to vanilla armor points)
- Toughness modifier (adds to vanilla armor toughness)
- Knockback resistance modifier
- Applied during `LivingIncomingDamageEvent` to **reduce** incoming damage

---

## 2. Data Structures

### 2.1 ArmorStats.java
```java
package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

/**
 * Armor statistics for damage reduction.
 * Applied to victim during damage calculations.
 */
public class ArmorStats {
    // Damage type reductions (0.0 = no reduction, 1.0 = 100% reduction)
    public float physicalReduction = 0.0f;    // Melee, projectile base
    public float fireReduction = 0.0f;        // Fire, lava
    public float magicReduction = 0.0f;       // Magic, wither, dragon breath
    public float explosionReduction = 0.0f;   // Explosions, fireworks
    public float projectileReduction = 0.0f;  // Arrows, tridents (stacks with physical)

    // Vanilla stat modifiers (additive)
    public float armorBonus = 0.0f;           // Added to armor value
    public float toughnessBonus = 0.0f;       // Added to armor toughness
    public float knockbackResistance = 0.0f;  // 0.0 - 1.0 (added to existing)

    // Special effects
    public boolean thornsReflect = false;     // Reflect % damage back
    public float thornsPercent = 0.0f;        // How much to reflect (0.0 - 0.5)

    // Save to NBT
    public void save(CompoundTag tag) {
        tag.putFloat("PhysicalRed", physicalReduction);
        tag.putFloat("FireRed", fireReduction);
        tag.putFloat("MagicRed", magicReduction);
        tag.putFloat("ExplosionRed", explosionReduction);
        tag.putFloat("ProjectileRed", projectileReduction);
        tag.putFloat("ArmorBonus", armorBonus);
        tag.putFloat("ToughnessBonus", toughnessBonus);
        tag.putFloat("KnockbackRes", knockbackResistance);
        tag.putBoolean("Thorns", thornsReflect);
        tag.putFloat("ThornsPercent", thornsPercent);
    }

    // Load from NBT
    public static ArmorStats load(CompoundTag tag) {
        ArmorStats stats = new ArmorStats();
        if (tag.contains("PhysicalRed")) stats.physicalReduction = tag.getFloat("PhysicalRed");
        if (tag.contains("FireRed")) stats.fireReduction = tag.getFloat("FireRed");
        if (tag.contains("MagicRed")) stats.magicReduction = tag.getFloat("MagicRed");
        if (tag.contains("ExplosionRed")) stats.explosionReduction = tag.getFloat("ExplosionRed");
        if (tag.contains("ProjectileRed")) stats.projectileReduction = tag.getFloat("ProjectileRed");
        if (tag.contains("ArmorBonus")) stats.armorBonus = tag.getFloat("ArmorBonus");
        if (tag.contains("ToughnessBonus")) stats.toughnessBonus = tag.getFloat("ToughnessBonus");
        if (tag.contains("KnockbackRes")) stats.knockbackResistance = tag.getFloat("KnockbackRes");
        if (tag.contains("Thorns")) stats.thornsReflect = tag.getBoolean("Thorns");
        if (tag.contains("ThornsPercent")) stats.thornsPercent = tag.getFloat("ThornsPercent");
        return stats;
    }
}
```

### 2.2 ArmorSlot Enum
```java
public enum ArmorSlot {
    HEAD("Helmet", EquipmentSlot.HEAD),
    CHEST("Chestplate", EquipmentSlot.CHEST),
    LEGS("Leggings", EquipmentSlot.LEGS),
    FEET("Boots", EquipmentSlot.FEET);

    private final String displayName;
    private final EquipmentSlot slot;

    // ... constructor and getters
}
```

---

## 3. ArmorConfigManager

Similar to `WeaponConfigManager`, manages global and per-item armor configurations.

### 3.1 Storage Pattern
```
config/devmod/
  armor_configs.json        <- Global armor configs (per Item type)
  backups/
    armor_configs_*.backup  <- Timestamped backups
```

### 3.2 Lookup Priority
1. **NBT Data** (per-item instance): Check `stack.get(DataComponents.CUSTOM_DATA)` for `"ArmorModStats"`
2. **Global Config** (per-item type): Check `globalArmorStats.get(stack.getItem())`
3. **Default Values**: Return `new ArmorStats()` with all zeros

### 3.3 Key Methods
```java
public class ArmorConfigManager {
    private static final Map<Item, ArmorStats> globalArmorStats = new HashMap<>();

    // Get stats for armor piece (NBT -> Global -> Default)
    public static ArmorStats getStats(ItemStack stack);

    // Set global stats for item type
    public static void setGlobalStats(Item item, ArmorStats stats);

    // Set specific stats on item instance (NBT)
    public static void setSpecificStats(ItemStack stack, ArmorStats stats);

    // Check if item is armor
    public static boolean isArmor(ItemStack stack);

    // Persistence
    public static void load();
    public static void save();
}
```

---

## 4. Damage Integration

### 4.1 Modification to DamageHandler

Add armor stats processing **after** the current weapon stats processing:

```java
@SubscribeEvent(priority = EventPriority.HIGH)
public static void onDamage(LivingIncomingDamageEvent event) {
    // ... existing weapon stats code ...

    // === NEW: Apply Armor Stats (Victim-Side) ===
    if (victim instanceof Player player) {
        float reduction = calculateArmorReduction(player, event.getSource());
        if (reduction > 0) {
            float reducedDamage = newDamage * (1.0f - reduction);
            event.setAmount(reducedDamage);

            // Log for telemetry
            HitContext.storeArmorReduction(victim, reduction);
        }
    }
}

private static float calculateArmorReduction(Player player, DamageSource source) {
    float totalReduction = 0f;

    // Sum reductions from all equipped armor
    for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;

        ItemStack armor = player.getItemBySlot(slot);
        if (armor.isEmpty()) continue;

        ArmorStats stats = ArmorConfigManager.getStats(armor);
        totalReduction += getReductionForDamageType(stats, source);
    }

    // Cap at 80% reduction to prevent invincibility
    return Math.min(totalReduction, 0.8f);
}

private static float getReductionForDamageType(ArmorStats stats, DamageSource source) {
    if (source.is(DamageTypeTags.IS_FIRE)) return stats.fireReduction;
    if (source.is(DamageTypeTags.IS_EXPLOSION)) return stats.explosionReduction;
    if (source.is(DamageTypeTags.IS_PROJECTILE)) return stats.projectileReduction + stats.physicalReduction;
    if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return stats.magicReduction;
    return stats.physicalReduction; // Default to physical
}
```

---

## 5. ArmorEditorScreen

### 5.1 Tab Structure
| Tab | Purpose | UI Elements |
|-----|---------|-------------|
| **PROTECTION** | Damage type reductions | 5 sliders (Physical, Fire, Magic, Explosion, Projectile) |
| **ATTRIBUTES** | Vanilla stat modifiers | Armor bonus, Toughness, Knockback Res sliders |
| **ENCHANTS** | Enchantment management | List + add/remove (reuse from WeaponEditor) |
| **DURABILITY** | Durability/Unbreakable | Fields (reuse from WeaponEditor) |
| **EFFECTS** | Special effects | Thorns toggle + percentage slider |

### 5.2 Visual Design
- Match WeaponEditorScreen aesthetic (Axiom-style dark panels)
- Preview: 3D rotating armor piece (from player slot)
- Color coding: Blue theme (vs red/orange for weapons)
- Same undo/redo, presets, and history systems

### 5.3 Armor Slot Selector
Unlike weapons (single main hand), armor has 4 slots. Add a slot selector bar:
```
[ HEAD ] [ CHEST ] [ LEGS ] [ FEET ]
```
Clicking a slot:
1. Highlights that tab
2. Loads that armor piece into editor
3. Shows "Empty Slot" if no armor equipped

---

## 6. Network Packets

### 6.1 UpdateArmorPayload.java
```java
public record UpdateArmorPayload(
    boolean isGlobal,
    int slot,              // 0=HEAD, 1=CHEST, 2=LEGS, 3=FEET
    float physicalRed,
    float fireRed,
    float magicRed,
    float explosionRed,
    float projectileRed,
    float armorBonus,
    float toughnessBonus,
    float knockbackRes,
    boolean thorns,
    float thornsPercent,
    String itemName        // For global config key
) implements CustomPacketPayload {
    public static final Type<UpdateArmorPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "update_armor")
    );
    // ... StreamCodec implementation
}
```

### 6.2 Server Handler
```java
public static void handleUpdateArmor(UpdateArmorPayload payload, ServerPlayer player) {
    ArmorStats stats = new ArmorStats();
    stats.physicalReduction = payload.physicalRed();
    stats.fireReduction = payload.fireRed();
    // ... set all fields

    if (payload.isGlobal()) {
        // Apply to item type globally
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(payload.itemName()));
        ArmorConfigManager.setGlobalStats(item, stats);
    } else {
        // Apply to specific item in slot
        EquipmentSlot slot = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, payload.slot());
        ItemStack armor = player.getItemBySlot(slot);
        if (!armor.isEmpty()) {
            ArmorConfigManager.setSpecificStats(armor, stats);
        }
    }
}
```

---

## 7. Item Type Filtering

### 7.1 EditorType Enhancement
Update `EditorType.java`:
```java
public enum EditorType {
    WEAPON("Weapon Editor", "M"),
    ARMOR("Armor Editor", "N"),       // NEW
    MOB_CONFIG("Mob Config", "Click mob");
}
```

### 7.2 Key Binding
Add new keybind for Armor Editor in `KeyInputHandler`:
```java
public static final KeyMapping KEY_ARMOR_EDITOR = new KeyMapping(
    "key.devmod.armor_editor",
    InputConstants.Type.KEYSYM,
    GLFW.GLFW_KEY_N,
    "key.categories.devmod"
);

// In tick handler:
if (KEY_ARMOR_EDITOR.consumeClick()) {
    Player player = mc.player;
    // Check if player has any armor equipped
    boolean hasArmor = false;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                hasArmor = true;
                break;
            }
        }
    }
    if (hasArmor) {
        mc.setScreen(new ArmorEditorScreen());
    } else {
        player.displayClientMessage(Component.literal("No armor equipped!"), true);
    }
}
```

### 7.3 Automatic Detection (Optional Enhancement)
WeaponEditorScreen could auto-redirect to ArmorEditorScreen:
```java
// In WeaponEditorScreen constructor:
if (stack.getItem() instanceof ArmorItem) {
    // Close and open ArmorEditorScreen instead
    mc.setScreen(new ArmorEditorScreen(stack));
    return;
}
```

---

## 8. Implementation Order

### Phase 1: Core Data Layer
1. [x] Design ArmorStats data structure
2. [ ] Create `ArmorStats.java`
3. [ ] Create `ArmorConfigManager.java`
4. [ ] Add persistence (JSON save/load)

### Phase 2: Integration
5. [ ] Create `UpdateArmorPayload.java`
6. [ ] Register packet in `NetworkHandler`
7. [ ] Add server-side handler
8. [ ] Modify `DamageHandler` for armor reduction

### Phase 3: UI
9. [ ] Create `ArmorEditorScreen.java`
10. [ ] Implement PROTECTION tab (damage reductions)
11. [ ] Implement ATTRIBUTES tab (armor/toughness/knockback)
12. [ ] Port ENCHANTS and DURABILITY tabs from WeaponEditor
13. [ ] Implement EFFECTS tab (thorns)
14. [ ] Add armor slot selector

### Phase 4: Polish
15. [ ] Add keybind (N key)
16. [ ] Update `EditorType` enum
17. [ ] Add i18n translations
18. [ ] Add presets system
19. [ ] Add telemetry hooks

---

## 9. Files to Create/Modify

### New Files
| File | Description |
|------|-------------|
| `ArmorStats.java` | Data structure for armor stats |
| `ArmorConfigManager.java` | Config management (save/load) |
| `ArmorEditorScreen.java` | Main UI screen |
| `UpdateArmorPayload.java` | Network packet |

### Modified Files
| File | Changes |
|------|---------|
| `DamageHandler.java` | Add armor reduction calculation |
| `NetworkHandler.java` | Register UpdateArmorPayload |
| `KeyInputHandler.java` | Add N key for armor editor |
| `EditorType.java` | Add ARMOR entry |
| `en_us.json` | Add translations |
| `it_it.json` | Add Italian translations |

---

## 10. UX Considerations

### 10.1 Visual Feedback
- Show damage reduction preview: "With this setup, you'll take 30% less fire damage"
- Color-code damage types (fire=orange, magic=purple, etc.)
- Show equipped armor set bonus (if full set configured)

### 10.2 Presets
- "Tank Build" - High physical reduction, knockback resistance
- "Fire Walker" - Max fire resistance
- "Glass Cannon" - No armor bonuses (for players who prioritize offense)

### 10.3 Warnings
- Warn if total reduction exceeds 80% cap
- Warn if thorns + high reduction (balance concern)
- Warn if editing global config vs specific item

---

## 11. Technical Notes

### 11.1 Thread Safety
- `ArmorConfigManager` uses same pattern as `WeaponConfigManager`
- Static `HashMap` for global configs (server-side only)
- NBT data is item-specific (thread-safe per-stack)

### 11.2 Compatibility
- Works with modded armor items (anything extending `ArmorItem`)
- Works with custom damage types (extensible `getReductionForDamageType`)
- NBT storage compatible with vanilla item stacking rules

### 11.3 Performance
- Armor stats lookup is O(1) for global, O(1) NBT check for specific
- Damage calculation adds ~4 NBT reads per hit (one per armor slot)
- Cached stats possible if performance becomes an issue

---

*Document Version: 1.0*
*Created for DevMod NeoForge 1.21*
