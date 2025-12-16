# EditorButton Adoption Audit (Completed)

Inventario puntuale e verificato dei punti in cui i pulsanti non usano la classe standard `EditorButton` e richiedono migrazione. Ogni voce indica il tipo di implementazione custom e la strategia di sostituzione.

Questo audit è stato completato e verificato tramite analisi statica del codice.

## Componente di Riferimento

Il componente standard da adottare è:
`com.frenkvs.devmod.ui.editor.components.EditorButton`

## Pattern di Implementazione da Sostituire

Sono stati identificati tre pattern principali di implementazione non standard:

1.  **Vanilla `Button`**: Uso diretto di `net.minecraft.client.gui.components.Button`. La migrazione è solitamente diretta, sostituendo l'istanza e adattando il costruttore e il gestore `onPress`.
2.  **`AxiomRenderer.drawButton`**: Un pattern diffuso che usa `AxiomRenderer.drawButton` per il rendering e `AxiomRenderer.isMouseOver` (o un calcolo manuale dei limiti) nel metodo `mouseClicked` per la logica di click. Richiede la creazione di un `EditorButton` e la sua integrazione nel ciclo di rendering ed eventi del pannello.
3.  **Componenti Custom Complessi**: Alcuni componenti UI (es. `FooterComponent`) hanno una gestione interna del layout, rendering (con `GuiGraphics.fill`) e hit-testing (`Rect.contains`). La migrazione qui è più complessa e richiede di refattorizzare il layout per ospitare istanze di `EditorButton` mantenendo il comportamento originale (es. scrolling orizzontale).

## Come Sostituire
- Istanziare `EditorButton` con `id` e `label`. Configurare `style`, `tooltip`, `enabled`, e `playSound` secondo necessità.
- Il path completo del componente è `com.frenkvs.devmod.ui.editor.components.EditorButton`.
- In `render` del contenitore: chiama `button.render(graphics, mouseX, mouseY, partialTick)`. I bounds sono gestiti internamente.
- In `mouseClicked`/`mouseReleased` del contenitore: propagare l'evento al `button` e consumare l'evento se il metodo restituisce `true`.
- Replicare funzionalità custom (es. hotkey, bordi speciali) usando le API di `EditorButton` o, se necessario, un componente layout che lo contenga.

## Checklist per file (Verificata)

### Vanilla `net.minecraft.client.gui.components.Button`
- `src/main/java/com/frenkvs/devmod/ui/RoomBoundsEditorScreen.java`: `setPointAButton`, `setPointBButton`, `saveButton`, `cancelButton`, `deleteLastButton`.
  - **Note**: Implementazione diretta di `Button`.
- `src/main/java/com/frenkvs/devmod/ui/ModScreen.java`: Pulsanti in `addStandardButtons` / `addApplyCancelButtons`.
  - **Note**: Implementazione diretta di `Button`.
- `src/main/java/com/frenkvs/devmod/ui/WelcomeScreen.java`: `tutorialButton`, `skipButton`.
  - **Note**: Implementazione diretta di `Button`.
- `src/main/java/com/frenkvs/devmod/party/PartyScreen.java`, `src/main/java/com/frenkvs/devmod/party/InvitePopupScreen.java`
  - **Note**: Implementazione diretta di `Button`.
- `src/main/java/com/frenkvs/devmod/quest/QuestEditorScreen.java`, `src/main/java/com/frenkvs/devmod/testing/BadgeTestScreen.java`, `src/main/java/com/frenkvs/devmod/testing/QATestingScreen.java`
  - **Note**: Implementazione diretta di `Button`.
- `src/main/java/com/frenkvs/devmod/endurance/QuestExitConfirmScreen.java`, `.../QuestCompletionScreen.java`, `.../QuestDeathScreen.java`, `.../WaveCheckpointScreen.java`, `.../PerkSelectionScreen.java`
  - **Note**: Implementazione diretta di `Button`.

### Pulsanti disegnati con `AxiomRenderer.drawButton` o `fill`
- `src/main/java/com/frenkvs/devmod/MobEquipmentScreen.java`, `src/main/java/com/frenkvs/devmod/TelemetryDashboardScreen.java`
  - **Note**: Utilizza `AxiomRenderer.drawButton`.
- `src/main/java/com/frenkvs/devmod/ui/hub/TestingHub.java`, `TestDetailPanel.java`, `ProgressFooter.java`
  - **Note**: Utilizza `AxiomRenderer.drawButton`.
- `src/main/java/com/frenkvs/devmod/ui/hub/QuickToolsPanel.java`
  - **Note**: Pattern custom con `fill` manuale e `isInBounds` in `mouseClicked`.
- `src/main/java/com/frenkvs/devmod/ui/wizard/QuickTestWizard.java`
    - **Note**: Pattern custom con `fill` manuale e gestione click separata.
- `src/main/java/com/frenkvs/devmod/ui/unified/UnifiedSettingsScreen.java` e tutte le pagine in `.../ui/unified/pages/`
  - **Note**: Utilizza `AxiomRenderer.drawButton` in modo estensivo.

### Componenti Editor (Custom)
- `src/main/java/com/frenkvs/devmod/ui/editor/components/FooterComponent.java`: History, Export, Import, Presets, etc.
  - **Note**: Componente custom complesso con `ResponsiveLayout.Rect`, `fill` e `Rect.contains`.
- `src/main/java/com/frenkvs/devmod/ui/editor/components/HeaderComponent.java`: Pulsante Close e tab.
  - **Note**: Componente custom complesso.
- `src/main/java/com/frenkvs/devmod/ui/editor/components/ModeBadge.java`: Toggle scope/mode.
  - **Note**: Componente custom. Valutare se wrappare in `EditorButton` o lasciare custom data la forma non standard.
- `src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java`: Pulsante "Copy".
  - **Note**: Hit-test manuale.
- `src/main/java/com/frenkvs/devmod/ui/editor/ItemEditorScreen.java`: Pulsanti multipli per rename, delete, etc.
  - **Note**: Disegnati con `fill`/`border`.
- `src/main/java/com/frenkvs/devmod/ui/editor/systems/TemplateOverlay.java`, `ConfirmDialog.java`
  - **Note**: Metodo `renderButton` custom.

### Endurance screens (renderButton custom)
- `src/main/java/com/frenkvs/devmod/endurance/EnduranceQuestScreen.java`, `EnduranceShopScreen.java`
  - **Note**: Helper `renderButton` custom per quasi tutti gli elementi cliccabili.

### Helper e Casi Particolari
- `src/main/java/com/frenkvs/devmod/ui/AxiomRenderer.java`: `drawButton`.
  - **Note**: Questo metodo è il principale "colpevole" di pulsanti non-standard. Il suo uso va deprecato e sostituito con `EditorButton`.
- **Esclusioni**: Pulsanti circolari/menu radiale (`src/main/java/com/frenkvs/devmod/ui/radial/*`) e altre icone non rettangolari restano custom.

## Suggerimento di Rollout (Confermato)
1.  **Basso Rischio**: Sostituire le istanze di `net.minecraft.client.gui.components.Button`.
2.  **Copertura Massima**: Migrare tutti gli usi di `AxiomRenderer.drawButton` partendo da `UnifiedSettings` e `Hub`.
3.  **Dialoghi**: Uniformare `ConfirmDialog` e `TemplateOverlay`.
4.  **Complessità Alta**: Affrontare i componenti custom come `FooterComponent`, `HeaderComponent`, e `ItemEditorScreen`.
5.  **Casi Speciali**: Valutare `ModeBadge` e simili; se la forma non standard è un problema, si può lasciare custom o creare un wrapper.
