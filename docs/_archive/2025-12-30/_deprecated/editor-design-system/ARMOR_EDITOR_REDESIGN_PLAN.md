# ArmorEditorScreen - Implementation Plan
> DEPRECATED: legacy plan for pre-unified ArmorEditorScreen; see `docs/editor-design-system/17-implementation-guide.md` and `docs/editor-design-system/16-armor-properties.md`.
## Aligned with EDITOR_DESIGN_SYSTEM.md v1.0

---

> **IMPORTANTE:** Questo documento segue le specifiche definite in [EDITOR_DESIGN_SYSTEM.md](../../editor-design-system/EDITOR_DESIGN_SYSTEM.md).
> Tutte le dimensioni, colori, componenti e comportamenti devono essere conformi al Design System.

---

## STATO ATTUALE

### Analisi Codice
| Metrica | Valore |
|---------|--------|
| Linee di codice | ~2,100 |
| Tab implementate | 5 (PROTECTION, ATTRIBUTES, ENCHANTS, DURABILITY, EFFECTS) |
| Undo/Redo | Implementato (50 stati) |
| Presets | Solo skeleton |
| History | Solo skeleton |
| Dirty State | **RIMOSSO** (errore precedente) |

### Problemi Critici Identificati
1. **Dirty State mancante** - Le modifiche non vengono tracciate
2. **Presets non funzionanti** - Solo UI, nessuna logica
3. **History non funzionante** - Solo UI, nessuna logica
4. **Layout non conforme** - Dimensioni e posizioni diverse da WeaponEditor
5. **Slot system limitato** - Solo item equipaggiati

---

## IMPLEMENTAZIONE PER FASI

### Fase 1: Foundation (CRITICA)
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.3, 2.4

#### 1.1 Ripristinare Dirty State System

```java
// Aggiungere campi (circa linea 50)
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

Cercare tutti i punti dove si modificano valori:
- Slider `onRelease` o value change
- Toggle click
- Enchantment add/remove/level change
- Qualsiasi edit field

```java
// Esempio per slider
private void updateSliderValue(Slider slider, float value) {
    saveUndoState();
    slider.value = value;
    markDirty("Changed " + slider.name);  // AGGIUNGERE
}
```

#### 1.3 Implementare ConfirmDialog

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.4

```java
private ConfirmDialog activeDialog = null;

private static class ConfirmDialog {
    final String title;
    final String message;
    final Runnable onConfirm;
    final Runnable onCancel;

    // Implementazione come da Design System
}

private void showConfirmDialog(String title, String message,
                               Runnable onConfirm, Runnable onCancel) {
    activeDialog = new ConfirmDialog(title, message, onConfirm, onCancel);
}
```

#### 1.4 Override onClose()

```java
@Override
public void onClose() {
    if (hasUnsavedChanges()) {
        showConfirmDialog(
            "Unsaved Changes",
            "You have " + pendingChanges.size() + " unsaved changes. Discard?",
            this::confirmClose,
            () -> {} // cancel - do nothing
        );
    } else {
        confirmClose();
    }
}

private void confirmClose() {
    if (minecraft != null && minecraft.options != null) {
        minecraft.options.menuBackgroundBlurriness().set(originalBlurValue);
    }
    super.onClose();
}
```

#### 1.5 Aggiungere Dirty Indicator UI

```java
// Nel metodo render(), dopo il rendering del left panel
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
    return minutes + "m ago";
}
```

**Linee stimate Fase 1:** 200-250

---

### Fase 2: Layout Alignment
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 1.1, 1.2, 1.3

#### 2.1 Aggiornare Costanti Dimensioni

```java
// Cambiare da valori attuali a:
private static final int PANEL_WIDTH = 550;   // Era 550 (OK)
private static final int PANEL_HEIGHT = 420;  // Era 420 (OK)
private static final int PREVIEW_SIZE = 100;  // Era 100 (OK)
private static final int LEFT_COLUMN_WIDTH = 140;
private static final int CONTENT_WIDTH = 390;
private static final int HEADER_HEIGHT = 28;
private static final int FOOTER_HEIGHT = 60;
```

#### 2.2 Ridisegnare Slot Selector

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 2.1 (ArmorEditor: 4-Slot Grid)

Attuale: Bottoni verticali o tab
Target: 4 slot in linea orizzontale sotto preview

```java
private void renderSlotSelector(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
    // Header
    graphics.drawString(font, "ARMOR SLOTS", x, y, UIConstants.Text.SECONDARY, false);

    int slotY = y + 12;
    int slotSize = 30;
    int gap = 5;

    String[] labels = {"H", "C", "L", "F"};
    int[] colors = {
        UIConstants.BodyPart.HEAD,
        UIConstants.BodyPart.BODY,
        UIConstants.BodyPart.LEGS,
        UIConstants.BodyPart.LEGS
    };

    for (int i = 0; i < 4; i++) {
        int slotX = x + i * (slotSize + gap);
        boolean isActive = (currentSlot == i);
        boolean isHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, slotX, slotY, slotSize, slotSize);

        // Background
        int bgColor = isActive ? UIConstants.Background.ACTIVE : UIConstants.Background.INPUT;
        graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, bgColor);

        // Border
        int borderColor = isActive ? UIConstants.Border.ACCENT : UIConstants.Border.MUTED;
        if (isHovered && !isActive) borderColor = colors[i];
        AxiomRenderer.drawBorder(graphics, slotX, slotY, slotSize, slotSize, borderColor);

        // Label
        int textX = slotX + (slotSize - font.width(labels[i])) / 2;
        int textY = slotY + (slotSize - 8) / 2;
        graphics.drawString(font, labels[i], textX, textY, UIConstants.Text.PRIMARY, false);

        // Item icon se presente
        ItemStack slotItem = getArmorInSlot(i);
        if (!slotItem.isEmpty()) {
            graphics.renderItem(slotItem, slotX + 7, slotY + 7);
        }
    }
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
        graphics.drawString(font, "No item", x + 5, y + 25, UIConstants.Text.MUTED, false);
        return;
    }

    // Item icon (32x32)
    graphics.renderItem(stack, x + 5, y + 20);

    // Item name
    String name = stack.getHoverName().getString();
    if (name.length() > 15) name = name.substring(0, 12) + "...";
    graphics.drawString(font, name, x + 45, y + 25, UIConstants.Text.PRIMARY, false);

    // Stats
    int statY = y + 45;
    graphics.drawString(font, "Defense: " + getDefenseValue(), x + 5, statY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Toughness: " + getToughnessValue(), x + 5, statY + 12, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Durability: " + getDurabilityPercent() + "%", x + 5, statY + 24, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, "Enchants: " + getEnchantCount(), x + 5, statY + 36, UIConstants.Text.SECONDARY, false);
}
```

#### 2.4 Riorganizzare Footer Buttons

**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 4.1, 4.2

```java
private void renderFooter(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
    int footerY = panelY + PANEL_HEIGHT - FOOTER_HEIGHT;

    // Row 1: Undo, Redo | History, Export, Import, Presets
    int x = panelX + 10;
    int y1 = footerY + 5;

    // Undo (Yellow)
    renderFooterButton(graphics, x, y1, 50, 22, "Undo", UIConstants.Accent.GOLD, ...);
    x += 55;

    // Redo (Green)
    renderFooterButton(graphics, x, y1, 50, 22, "Redo", UIConstants.Accent.GREEN, ...);
    x += 55;

    // Separator
    graphics.fill(x, y1, x + 1, y1 + 50, UIConstants.Border.SEPARATOR);
    x += 10;

    // History (Cyan)
    renderFooterButton(graphics, x, y1, 60, 22, "History", UIConstants.Accent.CYAN, ...);
    x += 65;

    // Export (Orange)
    renderFooterButton(graphics, x, y1, 55, 22, "Export", UIConstants.Accent.ORANGE, ...);
    x += 60;

    // Import (Gold)
    renderFooterButton(graphics, x, y1, 55, 22, "Import", UIConstants.Accent.GOLD, ...);
    x += 60;

    // Presets (Purple)
    renderFooterButton(graphics, x, y1, 60, 22, "Presets", UIConstants.Accent.PURPLE, ...);

    // Row 2: Reset, Cancel
    int y2 = y1 + 27;
    x = panelX + 195;

    // Reset (Red)
    renderFooterButton(graphics, x, y2, 60, 22, "Reset", UIConstants.Accent.RED, ...);
    x += 65;

    // Cancel (Gray)
    renderFooterButton(graphics, x, y2, 60, 22, "Cancel", UIConstants.Background.HOVER, ...);

    // Apply button (big, right side)
    int applyX = panelX + PANEL_WIDTH - 130;
    int applyY = footerY + 5;
    renderApplyButton(graphics, applyX, applyY, 120, 50, mouseX, mouseY);
}
```

**Linee stimate Fase 2:** 200-250

---

### Fase 3: Feature Parity
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 5

#### 3.1 Implementare Presets

Copiare pattern da WeaponEditorScreen e adattare:

```java
// Usare ItemEditorDataManager
private void savePreset(String name) {
    ArmorStats stats = buildCurrentStats();
    // Serializzare e salvare
    ItemEditorDataManager.saveArmorPreset(name, currentSlot, stats);
    UIConstants.Sound.save();
    showStatus("Preset saved!", UIConstants.Accent.GREEN);
}

private void loadPreset(String name) {
    if (hasUnsavedChanges()) {
        showConfirmDialog("Load Preset",
            "This will overwrite current changes. Continue?",
            () -> doLoadPreset(name),
            () -> {}
        );
    } else {
        doLoadPreset(name);
    }
}

private void doLoadPreset(String name) {
    ArmorStats stats = ItemEditorDataManager.loadArmorPreset(name, currentSlot);
    if (stats != null) {
        applyStatsToUI(stats);
        clearDirty();
        UIConstants.Sound.click();
    }
}
```

#### 3.2 Implementare History Panel

```java
private final List<HistoryEntry> history = new ArrayList<>();
private boolean showingHistory = false;

private record HistoryEntry(long timestamp, String description) {}

private void addHistoryEntry(String description) {
    history.add(0, new HistoryEntry(System.currentTimeMillis(), description));
    if (history.size() > 50) {
        history.remove(history.size() - 1);
    }
}

private void renderHistoryPanel(GuiGraphics graphics, int mouseX, int mouseY) {
    if (!showingHistory) return;

    int w = 250, h = 200;
    int x = (width - w) / 2;
    int y = (height - h) / 2;

    // Background
    graphics.fill(x, y, x + w, y + h, UIConstants.Background.PANEL_SOLID);
    AxiomRenderer.drawBorder(graphics, x, y, w, h, UIConstants.Border.DEFAULT);

    // Title
    graphics.drawString(font, "EDIT HISTORY", x + 10, y + 10, UIConstants.Text.TITLE, false);

    // Entries
    int entryY = y + 30;
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    for (int i = 0; i < Math.min(8, history.size()); i++) {
        HistoryEntry entry = history.get(i);
        String time = sdf.format(new Date(entry.timestamp));
        graphics.drawString(font, time + "  " + entry.description, x + 10, entryY, UIConstants.Text.SECONDARY, false);
        entryY += 18;
    }

    // Footer
    graphics.drawString(font, "Showing " + Math.min(8, history.size()) + "/" + history.size(),
        x + 10, y + h - 25, UIConstants.Text.MUTED, false);

    // Clear button
    renderSmallButton(graphics, x + w - 70, y + h - 30, 60, "Clear All", ...);
}
```

#### 3.3 Implementare Templates

```java
private static final Map<String, ArmorStats> TEMPLATES = Map.of(
    "Tank", new ArmorStats(0.5f, 0.3f, 0.3f, 0.4f, 0.3f, 5, 3, 0.5f, true, 0.2f),
    "Mage", new ArmorStats(0.2f, 0.5f, 0.6f, 0.3f, 0.2f, 2, 1, 0f, false, 0f),
    "Archer", new ArmorStats(0.3f, 0.2f, 0.2f, 0.2f, 0.5f, 3, 1, 0.2f, false, 0f),
    "Balanced", new ArmorStats(0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 3, 2, 0.2f, false, 0f)
);

private void applyTemplate(String templateName) {
    ArmorStats template = TEMPLATES.get(templateName);
    if (template != null) {
        saveUndoState();
        applyStatsToUI(template);
        markDirty("Applied template: " + templateName);
        addHistoryEntry("Applied template: " + templateName);
    }
}
```

**Linee stimate Fase 3:** 350-400

---

### Fase 4: Polish
**Riferimento:** EDITOR_DESIGN_SYSTEM.md - Sezione 6, 7

#### 4.1 Tooltips

```java
private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
    // Mode badge tooltip
    if (isHoveringModeBadge(mouseX, mouseY)) {
        String tip = editGlobal
            ? "GLOBAL: Changes affect ALL armor of this type"
            : "SPECIFIC: Changes affect only THIS armor piece";
        renderTooltipBox(graphics, tip, mouseX, mouseY);
        return;
    }

    // Slider tooltips
    for (Slider slider : currentTabSliders) {
        if (isHoveringSlider(slider, mouseX, mouseY) && slider.tooltip != null) {
            renderTooltipBox(graphics, slider.tooltip, mouseX, mouseY);
            return;
        }
    }
}
```

#### 4.2 Sound Feedback

Aggiungere chiamate `UIConstants.Sound.*` in:
- `applyChanges()` → `UIConstants.Sound.success()`
- Button clicks → `UIConstants.Sound.click()`
- Toggle → `UIConstants.Sound.toggleOn()` / `toggleOff()`
- Error → `UIConstants.Sound.error()`
- Save preset → `UIConstants.Sound.save()`
- Delete → `UIConstants.Sound.delete()`

#### 4.3 Keyboard Shortcuts

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (activeDialog != null) {
        // Dialog handles keys
        return activeDialog.keyPressed(keyCode);
    }

    if (Screen.hasControlDown()) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_Z -> { undo(); return true; }
            case GLFW.GLFW_KEY_Y -> { redo(); return true; }
            case GLFW.GLFW_KEY_S -> { applyChanges(); return true; }
        }
    }

    if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
        switchTab(keyCode - GLFW.GLFW_KEY_1);
        return true;
    }

    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
        onClose();
        return true;
    }

    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

**Linee stimate Fase 4:** 100-150

---

## RIEPILOGO EFFORT

| Fase | Descrizione | Linee | Priorità |
|------|-------------|-------|----------|
| 1 | Foundation (Dirty State, Dialogs) | 200-250 | CRITICA |
| 2 | Layout Alignment | 200-250 | ALTA |
| 3 | Feature Parity (Presets, History) | 350-400 | ALTA |
| 4 | Polish (Tooltips, Sound, Keys) | 100-150 | MEDIA |
| **TOTALE** | | **850-1050** | |

---

## CHECKLIST IMPLEMENTAZIONE

### Fase 1
- [ ] Aggiungere campi isDirty, pendingChanges, lastSaveTimestamp
- [ ] Implementare markDirty(), clearDirty(), hasUnsavedChanges()
- [ ] Aggiungere markDirty() in tutti i punti di modifica
- [ ] Implementare classe ConfirmDialog
- [ ] Override onClose() con check dirty
- [ ] Implementare renderDirtyIndicator()
- [ ] Testare: modifica → dirty indicator → close → dialog

### Fase 2
- [ ] Verificare/aggiornare costanti dimensioni
- [ ] Ridisegnare slot selector come 4-grid orizzontale
- [ ] Implementare renderItemInfoPanel()
- [ ] Riorganizzare footer buttons secondo spec
- [ ] Verificare allineamento visivo con WeaponEditor

### Fase 3
- [ ] Implementare savePreset() con ItemEditorDataManager
- [ ] Implementare loadPreset() con confirmation
- [ ] Implementare deletePreset() con confirmation
- [ ] Implementare UI preset popup/list
- [ ] Implementare history panel
- [ ] Collegare addHistoryEntry() a tutti i cambiamenti
- [ ] Implementare templates system
- [ ] Implementare Export/Import (JSON)

### Fase 4
- [ ] Aggiungere tooltip a mode badge
- [ ] Aggiungere tooltip a tutti gli sliders
- [ ] Aggiungere tooltip ai bottoni
- [ ] Aggiungere UIConstants.Sound.* calls
- [ ] Implementare keyboard shortcuts
- [ ] Code cleanup e commenti

---

*Questo documento deve essere usato insieme a EDITOR_DESIGN_SYSTEM.md*
