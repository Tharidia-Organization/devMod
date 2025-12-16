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
│  Auto-detect: se tab richiesto non è valido → GENERAL + warning.│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Enum StartTab

```java
public enum EditorStartTab {
    WEAPON,   // Apre WeaponModule
    ARMOR,    // Apre ArmorModule
    GENERAL;  // Apre GeneralModule (fallback)
}
```

## Constructor ItemEditorScreen

```java
public class ItemEditorScreen extends Screen {

    private final ItemStack item;
    private final EditorStartTab requestedTab;
    private EditorModule activeModule;

    /**
     * Costruttore con tab esplicito.
     * @param item L'item da editare
     * @param startTab Il modulo richiesto (WEAPON, ARMOR, GENERAL)
     */
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        super(Component.literal("Item Editor"));
        this.item = item;
        this.requestedTab = startTab;
        this.activeModule = resolveModule(item, startTab);
    }

    /**
     * Risolve il modulo da usare.
     * Se startTab non è applicabile all'item, fallback a GENERAL + warning.
     */
    private EditorModule resolveModule(ItemStack item, EditorStartTab requested) {
        return switch (requested) {
            case WEAPON -> {
                if (isWeapon(item)) {
                    yield new WeaponModule(item);
                } else {
                    LOGGER.warn("Requested WEAPON tab but item {} is not a weapon. Falling back to GENERAL.",
                        item.getItem().getDescriptionId());
                    yield new GeneralModule(item);
                }
            }
            case ARMOR -> {
                if (isArmor(item)) {
                    yield new ArmorModule(item);
                } else {
                    LOGGER.warn("Requested ARMOR tab but item {} is not armor. Falling back to GENERAL.",
                        item.getItem().getDescriptionId());
                    yield new GeneralModule(item);
                }
            }
            case GENERAL -> new GeneralModule(item);
        };
    }
}
```

## Helper Methods per Type Detection

```java
/**
 * Utility per determinare il tipo di item.
 * Usato sia dal Radial Menu che dall'Editor.
 */
public final class ItemTypeHelper {

    private ItemTypeHelper() {}

    /**
     * Verifica se l'item è un'arma editabile.
     */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem
            || item instanceof AxeItem
            || item instanceof TridentItem
            || item instanceof MaceItem
            // Aggiungi altri tipi se necessario
            || hasWeaponAttributes(stack);
    }

    /**
     * Verifica se l'item è un'armatura editabile.
     */
    public static boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ArmorItem;
    }

    /**
     * Verifica se l'item è editabile in generale.
     * True per qualsiasi item non-vuoto.
     */
    public static boolean isEditable(ItemStack stack) {
        return !stack.isEmpty();
    }

    /**
     * Check per armi custom che non estendono SwordItem.
     */
    private static boolean hasWeaponAttributes(ItemStack stack) {
        // Check se ha attack_damage attribute
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
            .keySet()
            .stream()
            .anyMatch(attr -> attr.equals(Attributes.ATTACK_DAMAGE));
    }
}
```

## Radial Menu Integration

```java
// Nel RadialMenuRegistry o dove registri le voci

// Voce "Edit Weapon" - visibile solo se isWeapon()
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_weapon",
    Component.translatable("devmod.radial.edit_weapon"),
    WEAPON_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isWeapon(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.WEAPON)
            );
        }
    },
    // Condizione visibilità
    (player) -> ItemTypeHelper.isWeapon(player.getMainHandItem())
));

// Voce "Edit Armor" - visibile solo se isArmor()
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_armor",
    Component.translatable("devmod.radial.edit_armor"),
    ARMOR_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isArmor(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.ARMOR)
            );
        }
    },
    // Condizione visibilità
    (player) -> ItemTypeHelper.isArmor(player.getMainHandItem())
));

// Voce "Edit Item" (General) - visibile se item editabile ma non weapon/armor
RadialMenuRegistry.register(new RadialMenuItem(
    "edit_item",
    Component.translatable("devmod.radial.edit_item"),
    GENERAL_ICON,
    (player) -> {
        ItemStack held = player.getMainHandItem();
        if (ItemTypeHelper.isEditable(held)) {
            Minecraft.getInstance().setScreen(
                new ItemEditorScreen(held, EditorStartTab.GENERAL)
            );
        }
    },
    // Condizione visibilità: editabile ma NON weapon e NON armor
    (player) -> {
        ItemStack held = player.getMainHandItem();
        return ItemTypeHelper.isEditable(held)
            && !ItemTypeHelper.isWeapon(held)
            && !ItemTypeHelper.isArmor(held);
    }
));
```

### Stato implementazione attuale
- Weapon/Armor/General: voci presenti nel radial, visibilità condizionata sui helper (`WeaponTypeDetector` + `ArmorConfigManager`).
- Fallback in `ItemEditorScreen`: se viene richiesto GENERAL su armor/weapon/ranged, log di warning e switch forzato.
- Shield/mace/trident: ancora senza voce dedicata nel radial.

## Comportamento Fallback

| Scenario | Azione | Log |
|----------|--------|-----|
| WEAPON richiesto + item è weapon | Apre WeaponModule | - |
| WEAPON richiesto + item NON è weapon | Apre GeneralModule | `WARN: Falling back to GENERAL` |
| ARMOR richiesto + item è armor | Apre ArmorModule | - |
| ARMOR richiesto + item NON è armor | Apre GeneralModule | `WARN: Falling back to GENERAL` |
| GENERAL richiesto | Apre GeneralModule | - |

## Translation Keys

```json
{
    "devmod.radial.edit_weapon": "Edit Weapon",
    "devmod.radial.edit_armor": "Edit Armor",
    "devmod.radial.edit_item": "Edit Item"
}
```

## Debug: Nessuna Voce Visibile

Se l'utente apre il radial menu con un item che non mostra nessuna voce editor:

```java
// Nel GeneralModule o come fallback globale
// Opzionale: mostrare comunque "Edit Item" per QUALSIASI item
// Decidi se vuoi questo comportamento
```

**Decisione attuale:** "Edit Item" visibile solo se NON weapon e NON armor.
Se vuoi che sia sempre visibile come fallback, cambia la condizione.
