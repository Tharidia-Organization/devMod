# Persistence Architecture

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
│                  File: world/serverconfig/devmod-items.toml     │
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
Dove:     world/serverconfig/devmod/devmod-items.toml (configurazione per mondo)
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
// File: world/serverconfig/devmod-items.toml
// Formato:
// [armor."minecraft:diamond_chestplate"]
// physicalReduction = 0.8
// fireReduction = 0.5
// ...

public class DevModItemConfig {
    private static final Map<String, ArmorStats> ARMOR_OVERRIDES = new HashMap<>();
    private static final Map<String, WeaponStats> WEAPON_OVERRIDES = new HashMap<>();

    // Chiamato al caricamento del mondo
    public static void loadWorldConfig(Path worldPath) {
        Path configPath = worldPath.resolve("serverconfig/devmod-items.toml");
        if (Files.exists(configPath)) {
            // Parse TOML e popola mappe
            parseConfigFile(configPath);
        }
    }

    // Chiamato quando un editor applica modifiche GLOBAL
    public static void saveGlobalOverride(String itemId, ArmorStats stats) {
        ARMOR_OVERRIDES.put(itemId, stats);
        writeConfigFile();

        // Sync a tutti i client
        syncToAllClients();
    }

    // Ottieni stats per un item type
    public static ArmorStats getGlobalOverride(String itemId) {
        return ARMOR_OVERRIDES.get(itemId);
    }
}
```

## Implementazione EXPORT (Layer 3)

```java
// Export a Datapack
public class DatapackExporter {

    public static void exportToDatapack(String packName) {
        Path packPath = getDatapacksPath().resolve(packName);

        // Struttura:
        // datapacks/devmod_balance/
        //   pack.mcmeta
        //   data/
        //     devmod/
        //       item_modifiers/
        //         armor/
        //           diamond_chestplate.json
        //         weapons/
        //           diamond_sword.json

        createPackMeta(packPath, packName);

        // Export armor overrides
        for (var entry : DevModItemConfig.getArmorOverrides().entrySet()) {
            String itemId = entry.getKey();
            ArmorStats stats = entry.getValue();

            Path jsonPath = packPath.resolve(
                "data/devmod/item_modifiers/armor/" +
                itemId.replace(":", "_") + ".json"
            );

            writeArmorJson(jsonPath, itemId, stats);
        }

        // Export weapon overrides
        for (var entry : DevModItemConfig.getWeaponOverrides().entrySet()) {
            // ... similar
        }

        LOGGER.info("Exported {} items to datapack: {}",
            getExportedCount(), packName);
    }

    // Formato JSON per armor modifier
    private static void writeArmorJson(Path path, String itemId, ArmorStats stats) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "devmod:armor_stats");
        json.addProperty("target", itemId);

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

        json.add("values", values);

        // Metadata
        JsonObject meta = new JsonObject();
        meta.addProperty("exported_at", LocalDateTime.now().toString());
        meta.addProperty("devmod_version", DevMod.VERSION);
        json.add("_meta", meta);

        Files.writeString(path, GSON.toJson(json));
    }
}

// Import da Datapack
public class DatapackImporter {

    public static int importFromDatapack(String packName) {
        Path packPath = getDatapacksPath().resolve(packName);

        if (!Files.exists(packPath)) {
            LOGGER.warn("Datapack not found: {}", packName);
            return 0;
        }

        int imported = 0;

        // Import armor
        Path armorPath = packPath.resolve("data/devmod/item_modifiers/armor");
        if (Files.exists(armorPath)) {
            imported += importArmorModifiers(armorPath);
        }

        // Import weapons
        Path weaponPath = packPath.resolve("data/devmod/item_modifiers/weapons");
        if (Files.exists(weaponPath)) {
            imported += importWeaponModifiers(weaponPath);
        }

        LOGGER.info("Imported {} items from datapack: {}", imported, packName);
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
