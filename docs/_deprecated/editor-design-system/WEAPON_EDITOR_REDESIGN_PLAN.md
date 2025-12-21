# WeaponEditorScreen - Implementation Plan
> DEPRECATED: legacy plan for pre-unified WeaponEditorScreen; see `docs/editor-design-system/17-implementation-guide.md` and `docs/editor-design-system/15-weapon-properties.md`.
## Aligned with EDITOR_DESIGN_SYSTEM.md v1.0

---

> **IMPORTANTE:** Questo documento segue le specifiche definite in [EDITOR_DESIGN_SYSTEM.md](../../editor-design-system/EDITOR_DESIGN_SYSTEM.md).
> Tutte le dimensioni, colori, componenti e comportamenti devono essere conformi al Design System.

---

## STATO ATTUALE

### Analisi Codice
| Metrica | Valore |
|---------|--------|
| Linee di codice | ~3,100 |
| Tab implementate | 5 (STATS, ENCHANTS, DURABILITY, ATTRIBUTES, COMPONENTS) |
| Undo/Redo | Implementato (50 stati) |
| Presets | **Completo e funzionante** |
| History | **Completo e funzionante** |
| Templates | **Completo e funzionante** |
| Dirty State | **MANCANTE** |

### Problemi Identificati
1. **Dirty State mancante** - Nessun warning chiudendo con modifiche
2. **Solo Main Hand** - Non supporta Off Hand
3. **Dimensioni diverse** - Panel 450x400 invece di 550x420
4. **Tooltips non renderizzati** - Definiti ma non mostrati

### Punti di Forza (da preservare)
- Sistema Presets completo con ItemEditorDataManager
- History panel con timestamps
- Templates auto-suggestion
- Attribute picker dinamico da registry
- Enchantment filtering sofisticato (6 filtri, favorites)

---

## IMPLEMENTAZIONE PER FASI

### Fase 1: Foundation (CRITICA)
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.3, 2.4

#### 1.1 Aggiungere Dirty State System

```java
// Aggiungere campi (circa linea 80)
private boolean isDirty = false;
private final List<String> pendingChanges = new ArrayList<>();
private long lastSaveTimestamp = 0;

// Aggiungere metodi
private void markDirty(String changeDescription) {
    isDirty = true;
    if (!pendingChanges.contains(changeDescription)) {
        pendingChanges.add(changeDescription);
    }
}

private void clearDirty() {
    isDirty = false;
    pendingChanges.clear();
    lastSaveTimestamp = System.currentTimeMillis();
}

private boolean hasUnsavedChanges() {
    return isDirty && !pendingChanges.isEmpty();
}
```

#### 1.2 Aggiungere chiamate markDirty()

Punti di modifica da tracciare:
- `updateSliderValue()` - quando slider cambia
- `addEnchantment()` - quando enchantment aggiunto
- `removeEnchantment()` - quando enchantment rimosso
- `setEnchantmentLevel()` - quando livello cambia
- `addAttribute()` - quando attribute aggiunto
- `removeAttribute()` - quando attribute rimosso
- `setAttributeValue()` - quando valore attribute cambia
- Toggle clicks (unbreakable, etc.)

```java
// Esempio per slider (modificare metodo esistente)
private void updateSliderValue(StatSlider slider, float value) {
    saveUndoState();
    slider.value = value;
    markDirty("Changed " + slider.name);  // AGGIUNGERE
    addHistoryEntry("Changed " + slider.name + " to " + String.format("%.1f", value));
}
```

#### 1.3 Implementare ConfirmDialog

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.4

```java
private ConfirmDialog activeDialog = null;

private static class ConfirmDialog {
    final String title;
    final String message;
    final String confirmText;
    final String cancelText;
    final int confirmColor;
    final Runnable onConfirm;
    final Runnable onCancel;

    private static final int WIDTH = 350;
    private static final int HEIGHT = 150;

    ConfirmDialog(String title, String message, Runnable onConfirm, Runnable onCancel) {
        this(title, message, "Discard", "Cancel", UIConstants.Accent.RED, onConfirm, onCancel);
    }

    ConfirmDialog(String title, String message, String confirmText, String cancelText,
                  int confirmColor, Runnable onConfirm, Runnable onCancel) {
        this.title = title;
        this.message = message;
        this.confirmText = confirmText;
        this.cancelText = cancelText;
        this.confirmColor = confirmColor;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        // Dark overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);

        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;

        // Panel
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, x, y, WIDTH, HEIGHT, UIConstants.Border.DEFAULT);

        // Title
        graphics.drawString(font, title, x + 20, y + 15, UIConstants.Text.TITLE, false);

        // Message
        graphics.drawString(font, message, x + 20, y + 50, UIConstants.Text.PRIMARY, false);

        // Buttons
        int btnY = y + HEIGHT - 40;
        int btnWidth = 100;

        // Confirm button
        renderDialogButton(graphics, font, x + 50, btnY, btnWidth, 24, confirmText, confirmColor);

        // Cancel button
        renderDialogButton(graphics, font, x + WIDTH - 150, btnY, btnWidth, 24, cancelText, UIConstants.Background.HOVER);
    }

    boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;
        int btnY = y + HEIGHT - 40;
        int btnWidth = 100;

        // Confirm button
        if (AxiomRenderer.isMouseOver((int)mouseX, (int)mouseY, x + 50, btnY, btnWidth, 24)) {
            onConfirm.run();
            return true;
        }

        // Cancel button
        if (AxiomRenderer.isMouseOver((int)mouseX, (int)mouseY, x + WIDTH - 150, btnY, btnWidth, 24)) {
            onCancel.run();
            return true;
        }

        return false;
    }
}

private void showConfirmDialog(String title, String message,
                               Runnable onConfirm, Runnable onCancel) {
    activeDialog = new ConfirmDialog(title, message, onConfirm, onCancel);
}

private void closeDialog() {
    activeDialog = null;
}
```

#### 1.4 Modificare onClose()

```java
@Override
public void onClose() {
    if (hasUnsavedChanges()) {
        showConfirmDialog(
            "Unsaved Changes",
            "You have " + pendingChanges.size() + " unsaved changes. Discard?",
            this::confirmClose,
            this::closeDialog
        );
        return; // Non chiudere ancora
    }
    confirmClose();
}

private void confirmClose() {
    // Restore blur
    if (minecraft != null && minecraft.options != null) {
        minecraft.options.menuBackgroundBlurriness().set(originalBlurValue);
    }
    super.onClose();
}
```

#### 1.5 Aggiungere Dirty Indicator UI

```java
// Nel metodo render(), nel left panel
private void renderDirtyIndicator(GuiGraphics graphics, int x, int y) {
    if (isDirty) {
        String text = "● " + pendingChanges.size() + " unsaved";
        graphics.drawString(font, text, x, y, UIConstants.Accent.ORANGE, false);
    } else if (lastSaveTimestamp > 0) {
        long ago = (System.currentTimeMillis() - lastSaveTimestamp) / 1000;
        String text = "✓ Saved " + formatTimeAgo(ago);
        graphics.drawString(font, text, x, y, UIConstants.Accent.GREEN, false);
    }
}

private String formatTimeAgo(long seconds) {
    if (seconds < 60) return seconds + "s ago";
    long minutes = seconds / 60;
    if (minutes < 60) return minutes + "m ago";
    return (minutes / 60) + "h ago";
}
```

#### 1.6 Modificare applyChanges()

```java
private void applyChanges() {
    // ... existing validation and payload building ...

    PacketDistributor.sendToServer(payload);

    // AGGIUNGERE:
    clearDirty();
    UIConstants.Sound.success();
    showStatus("Changes saved!", UIConstants.Accent.GREEN);
}
```

**Linee stimate Fase 1:** 200-250

---

### Fase 2: Layout Alignment
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 1.1, 1.2, 1.3

#### 2.1 Aggiornare Costanti Dimensioni

```java
// Cambiare da:
private static final int PANEL_WIDTH = 450;
private static final int PANEL_HEIGHT = 400;

// A:
private static final int PANEL_WIDTH = 550;   // +100px
private static final int PANEL_HEIGHT = 420;  // +20px
private static final int PREVIEW_SIZE = 100;
private static final int LEFT_COLUMN_WIDTH = 140;
private static final int CONTENT_WIDTH = 390;
private static final int HEADER_HEIGHT = 28;
private static final int FOOTER_HEIGHT = 60;
```

#### 2.2 Aggiungere Hand Slot Selector

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.1 (WeaponEditor: 2-Slot Tabs)

```java
private enum HandSlot { MAIN, OFF }
private HandSlot activeHand = HandSlot.MAIN;
private ItemStack mainHandItem = ItemStack.EMPTY;
private ItemStack offHandItem = ItemStack.EMPTY;

private void initializeItems() {
    if (player != null) {
        mainHandItem = player.getMainHandItem().copy();
        offHandItem = player.getOffhandItem().copy();
        // Default to main hand, but switch to off if main is empty
        if (mainHandItem.isEmpty() && !offHandItem.isEmpty()) {
            activeHand = HandSlot.OFF;
        }
        stack = (activeHand == HandSlot.MAIN) ? mainHandItem : offHandItem;
    }
}

private void renderHandSelector(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
    graphics.drawString(font, "WEAPON SLOT", x, y, UIConstants.Text.SECONDARY, false);

    int slotY = y + 12;
    int slotWidth = 60;
    int slotHeight = 30;
    int gap = 5;

    // Main Hand
    boolean mainActive = (activeHand == HandSlot.MAIN);
    boolean mainHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, slotY, slotWidth, slotHeight);

    int mainBg = mainActive ? UIConstants.Background.ACTIVE : UIConstants.Background.INPUT;
    int mainBorder = mainActive ? UIConstants.Border.ACCENT : (mainHovered ? UIConstants.Border.LIGHT : UIConstants.Border.MUTED);

    graphics.fill(x, slotY, x + slotWidth, slotY + slotHeight, mainBg);
    AxiomRenderer.drawBorder(graphics, x, slotY, slotWidth, slotHeight, mainBorder);
    graphics.drawString(font, "Main", x + 5, slotY + 5, UIConstants.Text.PRIMARY, false);
    if (!mainHandItem.isEmpty()) {
        graphics.renderItem(mainHandItem, x + 22, slotY + 12);
    }

    // Off Hand
    int offX = x + slotWidth + gap;
    boolean offActive = (activeHand == HandSlot.OFF);
    boolean offHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, offX, slotY, slotWidth, slotHeight);

    int offBg = offActive ? UIConstants.Background.ACTIVE : UIConstants.Background.INPUT;
    int offBorder = offActive ? UIConstants.Border.ACCENT : (offHovered ? UIConstants.Border.LIGHT : UIConstants.Border.MUTED);

    graphics.fill(offX, slotY, offX + slotWidth, slotY + slotHeight, offBg);
    AxiomRenderer.drawBorder(graphics, offX, slotY, slotWidth, slotHeight, offBorder);
    graphics.drawString(font, "Off", offX + 5, slotY + 5, UIConstants.Text.PRIMARY, false);
    if (!offHandItem.isEmpty()) {
        graphics.renderItem(offHandItem, offX + 22, slotY + 12);
    }
}

private boolean handleHandSelectorClick(double mouseX, double mouseY) {
    // Calculate positions based on render method
    int x = panelX + 10;
    int y = panelY + PREVIEW_SIZE + 20 + 12;  // Below preview
    int slotWidth = 60;
    int slotHeight = 30;
    int gap = 5;

    // Main hand click
    if (AxiomRenderer.isMouseOver((int)mouseX, (int)mouseY, x, y, slotWidth, slotHeight)) {
        if (activeHand != HandSlot.MAIN) {
            switchHand(HandSlot.MAIN);
        }
        return true;
    }

    // Off hand click
    int offX = x + slotWidth + gap;
    if (AxiomRenderer.isMouseOver((int)mouseX, (int)mouseY, offX, y, slotWidth, slotHeight)) {
        if (activeHand != HandSlot.OFF) {
            switchHand(HandSlot.OFF);
        }
        return true;
    }

    return false;
}

private void switchHand(HandSlot newHand) {
    if (hasUnsavedChanges()) {
        showConfirmDialog(
            "Switch Hand",
            "Discard changes to " + (activeHand == HandSlot.MAIN ? "main" : "off") + " hand?",
            () -> {
                closeDialog();
                performHandSwitch(newHand);
            },
            this::closeDialog
        );
    } else {
        performHandSwitch(newHand);
    }
}

private void performHandSwitch(HandSlot newHand) {
    activeHand = newHand;
    stack = (newHand == HandSlot.MAIN) ? mainHandItem : offHandItem;
    loadWeaponStats();
    loadEnchantments();
    loadAttributes();
    clearDirty();
    clearUndoHistory();
    UIConstants.Sound.click();
}
```

#### 2.3 Aggiungere Item Info Panel

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.5

```java
private void renderItemInfoPanel(GuiGraphics graphics, int x, int y) {
    int width = 130;
    int height = 100;

    // Panel background
    graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL);
    AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.MUTED);

    // Header
    graphics.drawString(font, "ITEM INFO", x + 5, y + 5, UIConstants.Text.SECONDARY, false);

    if (stack.isEmpty()) {
        graphics.drawString(font, "No weapon", x + 5, y + 25, UIConstants.Text.MUTED, false);
        return;
    }

    // Item icon
    graphics.renderItem(stack, x + 5, y + 20);

    // Item name
    String name = stack.getHoverName().getString();
    if (name.length() > 15) name = name.substring(0, 12) + "...";
    graphics.drawString(font, name, x + 45, y + 25, UIConstants.Text.PRIMARY, false);

    // Stats
    int statY = y + 45;
    float damage = getAttackDamage();
    float speed = getAttackSpeed();
    int durability = getDurabilityPercent();
    int enchants = getEnchantmentCount();

    graphics.drawString(font, "Damage: " + String.format("%.1f", damage), x + 5, statY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Speed: " + String.format("%.1f", speed), x + 5, statY + 12, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Durability: " + durability + "%", x + 5, statY + 24, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Enchants: " + enchants, x + 5, statY + 36, UIConstants.Text.SECONDARY, false);
}
```

#### 2.4 Verificare Footer Buttons

WeaponEditor ha già i footer buttons, verificare che siano conformi al Design System:
- Posizioni corrette
- Colori corretti
- Ordine corretto

**Linee stimate Fase 2:** 250-300

---

### Fase 3: Polish & Tooltips
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 6, 7

#### 3.1 Attivare Tooltip Rendering

I tooltip sono già definiti negli slider, ma non vengono renderizzati:

```java
// Aggiungere nel metodo render(), ALLA FINE (sopra tutto il resto)
private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
    // Non mostrare tooltip se c'è un dialog attivo
    if (activeDialog != null) return;

    // Mode badge tooltip
    if (isHoveringModeBadge(mouseX, mouseY)) {
        String tip = editGlobal
            ? "GLOBAL: Changes affect ALL weapons of this type"
            : "SPECIFIC: Changes affect only THIS weapon";
        renderTooltipBox(graphics, tip, mouseX, mouseY);
        return;
    }

    // Slider tooltips
    for (StatSlider slider : statSliders) {
        if (isHoveringSlider(slider, mouseX, mouseY) && slider.tooltip != null && !slider.tooltip.isEmpty()) {
            renderTooltipBox(graphics, slider.tooltip, mouseX, mouseY);
            return;
        }
    }

    // Button tooltips
    // ... add more as needed
}

private void renderTooltipBox(GuiGraphics graphics, String text, int mouseX, int mouseY) {
    int padding = 6;
    int textWidth = font.width(text);
    int width = textWidth + padding * 2;
    int height = 16;

    int x = mouseX + 12;
    int y = mouseY - 20;

    // Keep on screen
    if (x + width > this.width) x = this.width - width - 5;
    if (y < 5) y = mouseY + 20;

    // Background
    graphics.fill(x, y, x + width, y + height, UIConstants.Background.TOOLTIP);
    AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.LIGHT);

    // Text
    graphics.drawString(font, text, x + padding, y + 4, UIConstants.Text.PRIMARY, false);
}

private boolean isHoveringSlider(StatSlider slider, int mouseX, int mouseY) {
    // Calculate slider bounds based on current layout
    // Return true if mouse is over this slider
    return false; // Implement based on actual layout
}

private boolean isHoveringModeBadge(int mouseX, int mouseY) {
    int badgeX = panelX + PANEL_WIDTH - 110;
    int badgeY = panelY + 4;
    return AxiomRenderer.isMouseOver(mouseX, mouseY, badgeX, badgeY, 100, 20);
}
```

#### 3.2 Sound Feedback Completo

```java
// Verificare e aggiungere dove mancante:

private void applyChanges() {
    // ... existing code ...
    UIConstants.Sound.success();  // Già presente? Verificare
}

private void undo() {
    // ... existing code ...
    UIConstants.Sound.click();
}

private void redo() {
    // ... existing code ...
    UIConstants.Sound.click();
}

private void toggleUnbreakable() {
    boolean newValue = !isUnbreakable;
    if (newValue) {
        UIConstants.Sound.toggleOn();
    } else {
        UIConstants.Sound.toggleOff();
    }
    // ... rest of logic
}

private void savePreset(String name) {
    // ... existing code ...
    UIConstants.Sound.save();
}

private void deletePreset(String name) {
    // ... existing code ...
    UIConstants.Sound.delete();
}

private void reset() {
    UIConstants.Sound.warning();
    // ... show confirm dialog
}
```

#### 3.3 Keyboard Shortcuts

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    // Dialog ha priorità
    if (activeDialog != null) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            activeDialog.onCancel.run();
            closeDialog();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            activeDialog.onConfirm.run();
            closeDialog();
            return true;
        }
        return true; // Block other keys when dialog open
    }

    // Ctrl shortcuts
    if (Screen.hasControlDown()) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_Z -> {
                undo();
                return true;
            }
            case GLFW.GLFW_KEY_Y -> {
                redo();
                return true;
            }
            case GLFW.GLFW_KEY_S -> {
                applyChanges();
                return true;
            }
        }
    }

    // Tab switching con numeri 1-5
    if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
        int tabIndex = keyCode - GLFW.GLFW_KEY_1;
        if (tabIndex < Tab.values().length) {
            currentTab = Tab.values()[tabIndex];
            UIConstants.Sound.click();
            return true;
        }
    }

    // ESC per chiudere
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
        onClose();
        return true;
    }

    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

**Linee stimate Fase 3:** 150-200

---

## RIEPILOGO EFFORT

| Fase | Descrizione | Linee | Priorità |
|------|-------------|-------|----------|
| 1 | Foundation (Dirty State, Dialogs) | 200-250 | CRITICA |
| 2 | Layout + Off-Hand | 250-300 | ALTA |
| 3 | Polish (Tooltips, Sound, Keys) | 150-200 | MEDIA |
| **TOTALE** | | **600-750** | |

---

## CHECKLIST IMPLEMENTAZIONE

### Fase 1
- [ ] Aggiungere campi isDirty, pendingChanges, lastSaveTimestamp
- [ ] Implementare markDirty(), clearDirty(), hasUnsavedChanges()
- [ ] Cercare tutti i punti di modifica e aggiungere markDirty()
- [ ] Implementare classe ConfirmDialog
- [ ] Modificare onClose() con check dirty
- [ ] Modificare applyChanges() per chiamare clearDirty()
- [ ] Implementare renderDirtyIndicator()
- [ ] Testare: modifica → dirty → close → dialog → discard

### Fase 2
- [ ] Cambiare PANEL_WIDTH a 550, PANEL_HEIGHT a 420
- [ ] Aggiungere campi activeHand, mainHandItem, offHandItem
- [ ] Implementare initializeItems() per caricare entrambe le mani
- [ ] Implementare renderHandSelector()
- [ ] Implementare handleHandSelectorClick()
- [ ] Implementare switchHand() con confirmation
- [ ] Implementare renderItemInfoPanel()
- [ ] Verificare footer buttons alignment
- [ ] Testare switch tra main e off hand

### Fase 3
- [ ] Implementare renderTooltips()
- [ ] Implementare isHoveringSlider(), isHoveringModeBadge()
- [ ] Verificare tutti i UIConstants.Sound.* calls
- [ ] Implementare keyboard shortcuts
- [ ] Testare Ctrl+Z, Ctrl+Y, Ctrl+S, 1-5, ESC
- [ ] Code cleanup

---

## DIFFERENZE DA ARMOREDITOR

| Aspetto | ArmorEditor | WeaponEditor |
|---------|-------------|--------------|
| Slot selector | 4-grid (H/C/L/F) | 2-tabs (Main/Off) |
| Presets | Da implementare | Già funzionante |
| History | Da implementare | Già funzionante |
| Templates | Da implementare | Già funzionante |
| Dirty State | Da implementare | Da implementare |
| Preview rotation | Mouse drag | Auto-rotate (+ mouse drag da aggiungere) |

---

*Questo documento deve essere usato insieme a EDITOR_DESIGN_SYSTEM.md*
