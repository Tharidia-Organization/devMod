# Responsiveness Checklist (UI/HUD)

Questa checklist serve per verificare che tutte le UI e HUD restino leggibili,
senza overflow o sovrapposizioni, al variare di:
- dimensione finestra
- fullscreen on/off
- GUI scale (Minecraft)
- aspect ratio (4:3, 16:9, 21:9)

## Matrice minima consigliata
- 854x480 (GUI scale: Auto, 1, 2)
- 1280x720 (GUI scale: Auto, 2, 3)
- 1920x1080 (GUI scale: Auto, 2, 3, 4)
- 2560x1080 (21:9, GUI scale: Auto, 2)
- 640x480 (4:3, GUI scale: 1, 2)
- Fullscreen toggle in almeno 2 risoluzioni

## Criteri di accettazione
- Nessun testo oltre i bordi dello schermo
- Nessuna sovrapposizione di testo o icone
- Elementi principali dentro la safe-area (margini minimi)
- Troncamenti coerenti con ellissi dove serve
- Pannelli non tagliati (width/height clamp)
- Overlay/HUD non coprono elementi critici (hotbar, crosshair)

## Schermate da verificare (almeno)
- RadialMenuScreen
- UIResponsivenessTestScreen
- TestingHub
- QATestingScreen
- BadgeTestScreen
- WelcomeScreen
- SeasonPassScreen
- TelemetryDashboardScreen
- EditorHubScreen
- QuestEditorScreen

## HUD/Overlay da verificare (almeno)
- ImpactHudOverlay
- EnduranceQuestOverlay (con/ senza dettagli)
- PartyHudOverlay
- QuestHudOverlay
- AttributeHudOverlay
- CombatFlowHudOverlay
- Stamina/Nutrition/Contract HUD
- TelemetryStatusOverlay
- InstanceLoadingOverlay
- OnboardingOverlay
- TransportOverlay
- ActiveTestHudOverlay

## Note operative
- Usa il test screen: UI Responsiveness Test
- Se un pannello non entra, riduci dettagli o verifica clamp
- Ogni regressione: annotare screen, risoluzione, GUI scale
