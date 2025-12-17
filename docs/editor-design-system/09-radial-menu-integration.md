# 2.13 Entry Point: Radial Menu Integration

> **Entry point confermato:** Radial Menu con voci separate per tipo.

## Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  RADIAL MENU                                                    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Voci SEPARATE per tipo, ma STESSO screen:                      │
│                                                                 │
│  [Edit Weapon]  ──→  ItemEditorScreen(item, WEAPON)             │
│  [Edit Armor]   ──→  ItemEditorScreen(item, ARMOR)              │
│  [Edit Shield]  ──→  ItemEditorScreen(item, ARMOR)              │
│  [Edit Item]    ──→  ItemEditorScreen(item, GENERAL)            │
│                                                                 │
│  Visibilità basata su tipo item in mano (class/tag/detection).  │
│  Auto-detect: se tab richiesto non è valido → fallback + warn.  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Enum StartTab

```java
public enum EditorStartTab {
    WEAPON,   // Apre WeaponModule (melee o ranged via detection)
    ARMOR,    // Apre ArmorModule (include shield)
    GENERAL;  // Apre GeneralModule (fallback)
}
```

> **Nota:** Non esiste un valore RANGED separato. Il modulo RangedModule viene
> selezionato automaticamente da `resolveModule()` quando rileva un arco/balestra.

## Constructor ItemEditorScreen

```java
public class ItemEditorScreen extends Screen {

    private final ItemStack item;
    private final ItemStack originalItem;
    private final EditorStartTab requestedTab;
    private EditorModule activeModule;

    /**
     * Costruttore con tab esplicito.
     * @param item L'item da editare
     * @param startTab Il modulo richiesto (WEAPON, ARMOR, GENERAL)
     */
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        super(Component.literal("Item Editor"));
        this.item = item.copy();
        this.originalItem = item.copy();
        this.requestedTab = startTab;
        // Il modulo viene inizializzato dopo in init()
    }

    @Override
    protected void init() {
        activeModule = resolveModule(item, requestedTab);
        activeModule.setItem(item);
        activeModule.init(layout);
        // ... resto dell'inizializzazione
    }
}
```

## resolveModule() - Implementazione Attuale

```java
private EditorModule resolveModule(ItemStack stack, EditorStartTab requested) {
    return switch (requested) {
        case WEAPON -> {
            var detection = WeaponTypeDetector.detectDetailed(stack);
            if (detection.type() == WeaponType.NOT_A_WEAPON) {
                LOGGER.warn("[ItemEditor] Requested WEAPON but item is not a weapon; fallback to GENERAL.");
                yield new GeneralModule();
            }
            // Auto-select tra melee e ranged
            if (WeaponTypeDetector.isRanged(detection.type())) {
                yield new RangedModule();
            }
            yield new WeaponModule();
        }
        case ARMOR -> new ArmorModule();
        case GENERAL -> {
            // Auto-detect: se item è armor o weapon, reindirizza
            if (ArmorConfigManager.isArmor(stack)) {
                LOGGER.warn("[ItemEditor] Requested GENERAL but item is armor; falling back to ARMOR module.");
                yield new ArmorModule();
            }
            var detection = WeaponTypeDetector.detectDetailed(stack);
            if (detection.type() != WeaponType.NOT_A_WEAPON) {
                LOGGER.warn("[ItemEditor] Requested GENERAL but item is weapon; auto-selecting module.");
                if (WeaponTypeDetector.isRanged(detection.type())) {
                    yield new RangedModule();
                }
                yield new WeaponModule();
            }
            yield new GeneralModule();
        }
    };
}
```

> **Nota importante:** I moduli (`WeaponModule`, `ArmorModule`, etc.) **non**
> ricevono l'item nel costruttore. L'item viene passato via `setItem()` dopo
> la creazione, per permettere la separazione tra creazione e inizializzazione.

## Type Detection

L'implementazione **non usa** una classe `ItemTypeHelper` separata. Invece:

### WeaponTypeDetector

Classe dedicata per la detection delle armi con confidenza e metodo di rilevamento:

```java
public class WeaponTypeDetector {

    public record DetectionResult(
        WeaponType type,
        DetectionMethod method,
        float confidence,
        @Nullable String warning
    ) {
        public boolean isHighConfidence() {
            return confidence >= 0.8f;
        }
    }

    public enum WeaponType {
        SWORD, AXE, PICKAXE_COMBAT, MACE, TRIDENT,
        GENERIC_MELEE, BOW, CROSSBOW, GENERIC_RANGED,
        SHIELD, UNKNOWN, NOT_A_WEAPON
    }

    public enum DetectionMethod {
        CLASS_INSTANCEOF,      // SwordItem, AxeItem, etc.
        ITEM_TAG,              // ModTags.Items.MELEE_WEAPONS, etc.
        ATTRIBUTE_HEURISTIC,   // attack_damage attribute + name keywords
        CONFIG_WHITELIST,      // JSON whitelist file
        CONFIG_BLACKLIST,      // JSON blacklist file
        FALLBACK_GENERIC
    }

    public static DetectionResult detectDetailed(ItemStack stack) { ... }
    public static WeaponType detect(ItemStack stack) { ... }
    public static boolean isRanged(WeaponType type) { ... }
    public static boolean isMelee(WeaponType type) { ... }
    public static boolean isShield(WeaponType type) { ... }
}
```

### ArmorConfigManager.isArmor()

```java
// In ArmorConfigManager.java
public static boolean isArmor(ItemStack stack) {
    if (stack.isEmpty()) return false;
    var item = stack.getItem();
    return item instanceof ArmorItem || item instanceof ShieldItem;
}
```

> **Nota:** Gli shield sono considerati "armor" ai fini dell'editor, ma hanno
> una voce dedicata nel radial menu.

## Radial Menu Integration

### Struttura Category

Gli editor sono disponibili in **due location** nel radial menu:

1. **COMBAT > Editors** - Accesso rapido senza controllo visibilità
2. **TOOLS > Items** - Con visibilità dinamica basata sull'item in mano

### Helper Functions (in RadialMenuRegistry)

```java
private static ItemStack getHeldItem() {
    var mc = Minecraft.getInstance();
    var player = mc.player;
    if (player == null) return ItemStack.EMPTY;
    ItemStack held = player.getMainHandItem();
    return held.isEmpty() ? ItemStack.EMPTY : held.copy();
}

private static boolean isWeaponItem(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return false;
    var detection = WeaponTypeDetector.detectDetailed(stack);
    return WeaponTypeDetector.isMelee(detection.type())
        || WeaponTypeDetector.isRanged(detection.type());
}

private static boolean isArmorItem(ItemStack stack) {
    return stack != null && ArmorConfigManager.isArmor(stack);
}

private static boolean isShieldItem(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return false;
    var detection = WeaponTypeDetector.detectDetailed(stack);
    return detection.type() == WeaponTypeDetector.WeaponType.SHIELD;
}

private static boolean isGeneralItem(ItemStack stack) {
    if (stack == null || stack.isEmpty()) return false;
    var detection = WeaponTypeDetector.detectDetailed(stack);
    return !isWeaponItem(stack) && !isArmorItem(stack)
        && detection.type() != WeaponTypeDetector.WeaponType.SHIELD;
}

private static void openEditor(EditorStartTab tab) {
    var mc = Minecraft.getInstance();
    var player = mc.player;
    if (player == null) return;
    ItemStack held = player.getMainHandItem();
    if (held.isEmpty()) {
        player.displayClientMessage(
            Component.translatable("devmod.message.must_hold_item")
                .withStyle(s -> s.withColor(0xFFAA00)),
            true
        );
        return;
    }
    mc.setScreen(new ItemEditorScreen(held, tab));
}
```

### COMBAT > Editors (Accesso diretto)

```java
// Category 2: Editors in COMBAT macro
RadialCategory.builder("combateditors")
    .name("Editors")
    .color(0xFFFF6666)
    .icon("✏")
    .iconStack(stack(Items.ANVIL))
    .item(RadialMenuItem.action("Weapon Editor", "🗡",
        stack(Items.DIAMOND_SWORD),
        () -> openEditor(EditorStartTab.WEAPON),
        "Edit weapon stats and body part multipliers"))
    .item(RadialMenuItem.action("Armor Editor", "🛡",
        stack(Items.DIAMOND_CHESTPLATE),
        () -> openEditor(EditorStartTab.ARMOR),
        "Edit armor protection and attributes"))
    // + eventuale Mob Editor
    .build();
```

### TOOLS > Items (Visibilità dinamica)

```java
// Category 5: Item Editors in TOOLS macro
ItemStack held = getHeldItem();

RadialMenuItem weaponEditor = RadialMenuItem.screen("Weapon Editor", "🗡",
    stack(Items.DIAMOND_SWORD),
    () -> new ItemEditorScreen(held, EditorStartTab.WEAPON),
    "Edit weapon stats and body part multipliers");
weaponEditor.setVisible(isWeaponItem(held));

RadialMenuItem armorEditor = RadialMenuItem.screen("Armor Editor", "🛡",
    stack(Items.DIAMOND_CHESTPLATE),
    () -> new ItemEditorScreen(held, EditorStartTab.ARMOR),
    "Edit armor protection and attributes");
armorEditor.setVisible(isArmorItem(held));

RadialMenuItem shieldEditor = RadialMenuItem.screen("Shield Editor", "盾",
    stack(Items.SHIELD),
    () -> new ItemEditorScreen(held, EditorStartTab.ARMOR),
    "Edit shield block/reflect")
    .setCustomColor(0xFFDDDDDD);
shieldEditor.setVisible(isShieldItem(held));

RadialMenuItem generalEditor = RadialMenuItem.screen("Item Editor", "⚙",
    stack(Items.BOOK),
    () -> new ItemEditorScreen(held, EditorStartTab.GENERAL),
    "Edit generic item data");
generalEditor.setVisible(isGeneralItem(held));

RadialCategory.builder("itemeditors")
    .name("Items")
    .color(0xFFFFEECC)
    .icon("🗡")
    .iconStack(stack(Items.DIAMOND_SWORD))
    .item(weaponEditor)
    .item(armorEditor)
    .item(shieldEditor)
    .item(generalEditor)
    .build();
```

## Comportamento Fallback

| Scenario | Azione | Log |
|----------|--------|-----|
| WEAPON + item è melee | WeaponModule | - |
| WEAPON + item è ranged | RangedModule | - |
| WEAPON + item NON è weapon | GeneralModule | `WARN: Requested WEAPON but item is not a weapon` |
| ARMOR + qualsiasi armor/shield | ArmorModule | - |
| GENERAL + item è armor | ArmorModule | `WARN: Requested GENERAL but item is armor` |
| GENERAL + item è weapon | WeaponModule/RangedModule | `WARN: Requested GENERAL but item is weapon` |
| GENERAL + altro | GeneralModule | - |

## Translation Keys

```json
{
    "devmod.radial.edit_weapon": "Edit Weapon",
    "devmod.radial.edit_armor": "Edit Armor",
    "devmod.radial.edit_shield": "Edit Shield",
    "devmod.radial.edit_item": "Edit Item",
    "devmod.message.must_hold_item": "You must be holding an item!"
}
```

## File Correlati

| File | Responsabilità |
|------|----------------|
| `EditorStartTab.java` | Enum per tab iniziale |
| `ItemEditorScreen.java` | Screen principale, `resolveModule()` |
| `WeaponTypeDetector.java` | Detection armi con confidenza |
| `ArmorConfigManager.java` | `isArmor()` per armature/shield |
| `RadialMenuRegistry.java` | Definizione voci radial menu |
| `RadialMenuItem.java` | Model per singola voce |

## Note Implementazione

1. **Shield come ARMOR:** Gli shield usano `EditorStartTab.ARMOR` ma hanno
   voce dedicata nel radial per chiarezza UX.

2. **Dual Location:** Gli editor sono accessibili sia da COMBAT (sempre visibili)
   che da TOOLS (visibilità dinamica). Questo permette accesso rapido a chi
   sa cosa vuole modificare, e discovery guidata per chi esplora.

3. **Item copy:** L'`ItemEditorScreen` lavora su una copia dell'item (`item.copy()`)
   per evitare modifiche accidentali prima dell'Apply esplicito.

4. **Confidenza detection:** `WeaponTypeDetector` fornisce una confidence score.
   Item con bassa confidenza possono mostrare warning o prompt di conferma.
