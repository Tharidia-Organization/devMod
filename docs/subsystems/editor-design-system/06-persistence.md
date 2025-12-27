# Persistence Architecture

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

> **Architettura confermata:** Storage primario B (CustomData + serverconfig), Export D (datapack)

## Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  PERSISTENZA DI LAVORO (veloce, iterativa)                      │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  SPECIFIC mode → NBT/Components sull'item stesso                │
│                  Persiste con l'item, funziona in multiplayer   │
│                                                                 │
│  GLOBAL mode   → Per-world serverconfig                         │
│                  Files: serverconfig/devmod/armor_configs.json  │
│                         serverconfig/devmod/weapon_configs.json │
│                  Applicato a tutti gli item di quel tipo        │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  EXPORT/RELEASE FORMAT (stabile, condivisibile)                 │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Datapack      → JSON in datapacks/devmod_balance/              │
│                  Formato ufficiale per distribuzione            │
│                  Versionabile in Git, condivisibile             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Perché NON Datapack come Storage Primario

| Problema | Descrizione |
|----------|-------------|
| **Lento da iterare** | Scrittura file + reload + cache clear |
| **Non copre per-item** | Datapack = regole globali, non instance-specific |
| **Conflitti priority** | Gestione precedenze tra datapack complessa |
| **Troppo formale** | Overkill per "sto testando un valore" |

## Storage Layers

```
Layer 1: SPECIFIC (Per-Item Instance)
─────────────────────────────────────
Dove:     CustomData (WeaponModStats/ArmorModStats) sull'ItemStack
Scope:    Solo quell'item specifico
Persiste: Finché l'item esiste
Sync:     Automatico con item (inventory sync)

Layer 2: GLOBAL (Per-World Rules)
─────────────────────────────────────
Dove:     serverconfig/devmod/armor_configs.json + weapon_configs.json (JSON separati)
Scope:    Tutti gli item di quel tipo in quel mondo
Persiste: Finché il mondo esiste
Sync:     Server → Client al login

Layer 3: EXPORT (Distribution Format)
─────────────────────────────────────
Dove:     datapacks/devmod_balance/data/devmod/...
Scope:    Qualsiasi mondo che carichi il datapack
Persiste: Sempre (file system)
Sync:     /reload o restart server
```

> **Stato implementazione corrente (DevMod):**
> - GLOBAL: file per-mondo in `serverconfig/devmod/armor_configs.json` e `serverconfig/devmod/weapon_configs.json` caricati all’avvio server (vedi `ArmorConfigManager` / `WeaponConfigManager`).
> - SPECIFIC: `CustomData` con chiave `ArmorModStats` / `WeaponModStats` sullo stack.
> - EXPORT: (parziale) l’UI export/import opera su file JSON interni `config/devmod/item_editor/exports/…`; in parallelo viene generato un datapack automatico `datapacks/devmod_balance_auto` con le override globali (armor/weapons). Import usa lo stesso pack se presente.
> - UI/DEBUG: il pannello debug mostra la sorgente stat attiva (Specific/Global/Vanilla) per l’item corrente.

## Priorità Applicazione

Quando un item viene valutato, le modifiche si applicano in questo ordine (ultima vince):

```
1. Vanilla defaults                    (base)
    ↓
2. Datapack rules (se presente)        (override globale)
    ↓
3. Per-world serverconfig              (override mondo)
    ↓
4. Per-item CustomData                 (override istanza) ← VINCE
```

## Implementazione SPECIFIC (Layer 1)

```java
// NBT keys allineati (no namespace, coerenti tra codebase e doc)
private static final String ARMOR_STATS_KEY = "ArmorModStats";   // Armor editor
private static final String WEAPON_STATS_KEY = "WeaponModStats"; // Weapon editor

// Salvataggio su item (Armor example; Weapon usa WEAPON_STATS_KEY)
private void saveToItemNBT(ItemStack stack, ArmorStats stats) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    CompoundTag devmodTag = new CompoundTag();

    devmodTag.putFloat("physicalReduction", stats.physicalReduction);
    devmodTag.putFloat("fireReduction", stats.fireReduction);
    devmodTag.putFloat("magicReduction", stats.magicReduction);
    devmodTag.putFloat("explosionReduction", stats.explosionReduction);
    devmodTag.putFloat("projectileReduction", stats.projectileReduction);
    devmodTag.putFloat("armorBonus", stats.armorBonus);
    devmodTag.putFloat("toughnessBonus", stats.toughnessBonus);
    devmodTag.putFloat("knockbackResistance", stats.knockbackResistance);
    devmodTag.putBoolean("thornsReflect", stats.thornsReflect);
    devmodTag.putFloat("thornsPercent", stats.thornsPercent);

    // Timestamp per debug
    devmodTag.putLong("modifiedAt", System.currentTimeMillis());

    tag.put(ARMOR_STATS_KEY, devmodTag);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
}

// Lettura da item
private ArmorStats loadFromItemNBT(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (!tag.contains(ARMOR_STATS_KEY)) {
        return null; // Nessuna modifica custom
    }

    CompoundTag devmodTag = tag.getCompound(ARMOR_STATS_KEY);
    ArmorStats stats = new ArmorStats();

    stats.physicalReduction = devmodTag.getFloat("physicalReduction");
    stats.fireReduction = devmodTag.getFloat("fireReduction");
    // ... altri campi

    return stats;
}

// Check se item ha modifiche custom
public boolean hasCustomStats(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    return tag.contains(ARMOR_STATS_KEY);
}

// Rimuovi modifiche custom (reset to vanilla/global)
public void clearCustomStats(ItemStack stack) {
    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (tag.contains(ARMOR_STATS_KEY)) {
        tag.remove(ARMOR_STATS_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
```

## Implementazione GLOBAL (Layer 2)

```java
// Files: serverconfig/devmod/armor_configs.json
//        serverconfig/devmod/weapon_configs.json
// Formato JSON con map di item_id -> stats

// ArmorConfigManager.java
public class ArmorConfigManager {
    private static final Map<Item, ArmorStats> globalArmorStats = new HashMap<>();
    private static Path dataDirectory = null;

    // Chiamato durante mod init
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        Files.createDirectories(dataDirectory);
        load();  // Carica armor_configs.json
    }

    // Priorità: Component → NBT → Global → Default
    public static ArmorStats getStats(ItemStack stack) {
        // 1. Check typed data component (new path)
        CompoundTag componentTag = stack.get(ArmorComponents.armorStatsComponent());
        if (componentTag != null && !componentTag.isEmpty()) {
            return ArmorStats.load(componentTag.copy());
        }

        // 2. Check CustomData NBT (legacy path)
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("ArmorModStats")) {
            return ArmorStats.load(customData.copyTag().getCompound("ArmorModStats"));
        }

        // 3. Check global overrides
        Item item = stack.getItem();
        if (globalArmorStats.containsKey(item)) {
            return globalArmorStats.get(item);
        }

        // 4. Return default
        return new ArmorStats();
    }

    public static void setGlobalStats(Item item, ArmorStats stats) {
        globalArmorStats.put(item, stats);
        save();  // Persiste su armor_configs.json
    }

    public static Map<Item, ArmorStats> getAllGlobalStats() {
        return Collections.unmodifiableMap(globalArmorStats);
    }
}

// WeaponConfigManager.java - stessa struttura per weapon_configs.json
```

## Implementazione EXPORT (Layer 3)

```java
// DatapackIO.java - Export/Import utilities
// Struttura: datapacks/<pack>/data/devmod/item_modifiers/{armor|weapons}/<item>.json

public final class DatapackIO {

    /**
     * Export current global overrides into a datapack.
     * @param packName directory under datapacks/
     * @return number of files exported
     */
    public static int exportOverrides(String packName) {
        Path base = ConfigPaths.getGameDir().resolve("datapacks").resolve(packName);

        // pack.mcmeta
        writePackMeta(base);

        // Armor overrides
        Path armorDir = base.resolve("data/devmod/item_modifiers/armor");
        for (Map.Entry<Item, ArmorStats> entry : ArmorConfigManager.getAllGlobalStats().entrySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            Path out = armorDir.resolve(id.toString().replace(":", "_") + ".json");
            writeArmor(out, id, entry.getValue());
        }

        // Weapon overrides
        Path weaponDir = base.resolve("data/devmod/item_modifiers/weapons");
        for (Map.Entry<Item, WeaponStats> entry : WeaponConfigManager.getAllGlobalStats().entrySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            Path out = weaponDir.resolve(id.toString().replace(":", "_") + ".json");
            writeWeapon(out, id, entry.getValue());
        }
    }

    // Formato JSON per armor modifier (include shield stats)
    private static void writeArmor(Path path, ResourceLocation id, ArmorStats stats) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:armor_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("physical_reduction", stats.physicalReduction);
        values.addProperty("fire_reduction", stats.fireReduction);
        values.addProperty("magic_reduction", stats.magicReduction);
        values.addProperty("explosion_reduction", stats.explosionReduction);
        values.addProperty("projectile_reduction", stats.projectileReduction);
        values.addProperty("armor_bonus", stats.armorBonus);
        values.addProperty("toughness_bonus", stats.toughnessBonus);
        values.addProperty("knockback_resistance", stats.knockbackResistance);
        values.addProperty("thorns_reflect", stats.thornsReflect);
        values.addProperty("thorns_percent", stats.thornsPercent);
        // Shield-specific stats
        values.addProperty("shield_reflect_projectiles", stats.shieldReflectProjectiles);
        values.addProperty("shield_block_strength", stats.shieldBlockStrength);
        values.addProperty("shield_recovery_speed", stats.shieldRecoverySpeed);

        json.add("values", values);

        // TODO: Aggiungere metadata per tracciabilità
        // JsonObject meta = new JsonObject();
        // meta.addProperty("exported_at", LocalDateTime.now().toString());
        // meta.addProperty("devmod_version", DevMod.VERSION);
        // json.add("_meta", meta);

        Files.writeString(path, GSON.toJson(json));
    }

    // Formato JSON per weapon modifier (extended stats)
    private static void writeWeapon(Path path, ResourceLocation id, WeaponStats stats) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:weapon_stats");
        json.addProperty("target", id.toString());

        JsonObject values = new JsonObject();
        values.addProperty("attack_damage", stats.attackDamage);
        values.addProperty("attack_speed", stats.attackSpeed);
        values.addProperty("attack_reach", stats.attackReach);
        values.addProperty("attack_knockback", stats.attackKnockback);
        values.addProperty("armor_penetration", stats.armorPenetration);
        values.addProperty("base_damage_bonus", stats.baseDamageBonus);
        values.addProperty("crit_chance", stats.critChance);
        values.addProperty("crit_damage", stats.critDamage);
        values.addProperty("damage_bonus", stats.damageBonus);
        values.addProperty("armor_shred", stats.armorShred);
        // Extended damage types
        values.addProperty("damage_vs_undead", stats.damageVsUndead);
        values.addProperty("damage_vs_arthropods", stats.damageVsArthropods);
        values.addProperty("damage_vs_players", stats.damageVsPlayers);
        values.addProperty("true_damage_percent", stats.trueDamagePercent);
        values.addProperty("fire_damage_bonus", stats.fireDamageBonus);
        values.addProperty("magic_damage_bonus", stats.magicDamageBonus);
        values.addProperty("lifesteal", stats.lifesteal);
        values.addProperty("clear_tool_rules", stats.clearToolRules);

        json.add("values", values);
        Files.writeString(path, GSON.toJson(json));
    }

    /**
     * Import overrides from an existing datapack folder.
     */
    public static int importOverrides(String packName) {
        Path base = ConfigPaths.getGameDir().resolve("datapacks").resolve(packName);
        int imported = 0;

        // Import armor from data/devmod/item_modifiers/armor/*.json
        Path armorDir = base.resolve("data/devmod/item_modifiers/armor");
        if (Files.exists(armorDir)) {
            for (Path file : Files.list(armorDir).toList()) {
                JsonObject json = readJson(file);
                String target = json.get("target").getAsString();
                ArmorStats stats = parseArmor(json.getAsJsonObject("values"));
                Item item = ItemLookup.getItem(ResourceLocation.tryParse(target));
                if (item != null) {
                    ArmorConfigManager.setGlobalStats(item, stats);
                    imported++;
                }
            }
        }

        // Import weapons from data/devmod/item_modifiers/weapons/*.json
        Path weaponDir = base.resolve("data/devmod/item_modifiers/weapons");
        if (Files.exists(weaponDir)) {
            // ... similar pattern
        }

        return imported;
    }
}
```

## UI Export/Import

```
┌─────────────────────────────────────────────────────────────────┐
│  EXPORT / IMPORT                                          [X]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  EXPORT TO DATAPACK                                             │
│  ─────────────────────────────────────────────────────────────  │
│  Pack name: [devmod_balance_v1___]                              │
│                                                                 │
│  Include:                                                       │
│  [✓] Armor overrides (12 items)                                 │
│  [✓] Weapon overrides (8 items)                                 │
│  [ ] Per-item custom stats (requires /give)                     │
│                                                                 │
│                           [Export]                              │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  IMPORT FROM DATAPACK                                           │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Available:                                                     │
│  > devmod_balance_v1 (20 items, 2024-01-15)                     │
│    devmod_test_pack (5 items, 2024-01-10)                       │
│                                                                 │
│  [Import Selected]  [Refresh List]                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Workflow Tipico

```
SVILUPPO (rapido, iterativo)
────────────────────────────
1. Apri editor su item
2. Modifica valori (PREVIEW mode per testare)
3. Quando soddisfatto → APPLY mode → Apply
4. Modifiche salvate in:
   - NBT item (se SPECIFIC)
   - serverconfig (se GLOBAL)
5. Testa immediatamente in-game

RELEASE (stabile, condivisibile)
────────────────────────────
1. Bilanciamento completato e testato
2. Footer → Export button
3. Scegli nome datapack
4. Export genera:
   datapacks/devmod_balance_v1/
     pack.mcmeta
     data/devmod/item_modifiers/...
5. Datapack pronto per:
   - Condividere con team
   - Commit in Git
   - Distribuire a server
```

## Indicatore Sorgente Stats

Nel Debug Panel, mostra da dove vengono i valori correnti:

```
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO - STAT SOURCES                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  attack_damage:   7.0 → 12.0                                    │
│                   ↑      ↑                                      │
│                   │      └── [NBT] Custom per-item override     │
│                   └───────── [VANILLA] Base value               │
│                                                                 │
│  armor:           8.0 → 10.0                                    │
│                   ↑      ↑                                      │
│                   │      └── [CONFIG] Per-world serverconfig    │
│                   └───────── [DATAPACK] devmod_balance_v1       │
│                                                                 │
│  Source Priority: NBT > CONFIG > DATAPACK > VANILLA             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Changelog

| Data | Modifica |
|------|----------|
| 2025-12-17 | Aggiornato Layer 2 GLOBAL: usa JSON separati invece di TOML unificato |
| 2025-12-17 | Aggiornato path: `serverconfig/devmod/armor_configs.json` + `weapon_configs.json` |
| 2025-12-17 | Aggiornato GLOBAL impl con ArmorConfigManager/WeaponConfigManager reali |
| 2025-12-17 | Aggiornato EXPORT impl con DatapackIO.java (include shield/extended weapon stats) |
| 2025-12-17 | Aggiunti campi armor: shield_reflect_projectiles, shield_block_strength, shield_recovery_speed |
| 2025-12-17 | Aggiunti campi weapon: damage_vs_*, true_damage_percent, fire/magic_damage_bonus, lifesteal |

---

## Funzionalità Implementate

### ✅ Export Metadata (Implementato 2025-12-17)
Blocco `_meta` aggiunto ai JSON esportati per tracciabilità:

```json
{
  "type": "devmod:armor_stats",
  "target": "minecraft:diamond_chestplate",
  "values": { ... },
  "_meta": {
    "exported_at": "2025-12-17T14:30:00",
    "devmod_version": "1.0.0"
  }
}
```

**Implementazione:** `DatapackIO.java` - metodi `createExportMetadata()`, `writeArmor()`, `writeWeapon()`

### ✅ syncToAllClients() (Implementato 2025-12-17)
Sync automatico delle modifiche GLOBAL a tutti i client connessi.

**Implementazione:**
- `ArmorConfigManager.syncToAllClients()` - broadcast a tutti i player connessi
- `WeaponConfigManager.syncToAllClients()` - broadcast a tutti i player connessi
- `GlobalConfigSyncPayload.java` - payload server→client per sync config
- `NetworkHandler.java` - handler per ricevere e applicare config su client
