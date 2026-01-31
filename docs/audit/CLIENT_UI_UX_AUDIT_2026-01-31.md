# CLIENT UI UX AUDIT (2026-01-31)

Scope
- Client UI (screens, overlays, toasts/feedback, tooltips, localization)
- Source: src/main/java + assets/lang

Summary snapshot
- Screen classes: 68 total
- BaseDevModScreen: 10
- ErrorBoundaryScreen: 1
- ModScreen: 1
- AbstractContainerScreen: 5
- Plain Screen: 52
- Implication: majority of screens do not use the shared error-fallback/status UX path

Key findings

1) Toast / feedback consistency
- There are multiple toast/feedback systems in parallel:
  - UnifiedToastOverlay (notification pipeline)
  - ClientNotificationManager (legacy toast renderer)
  - ToastMessage (mini toasts inside testing/NPC screens)
  - Direct chat feedback (ClientUiBridgeImpl.showNotification)
- Impact: inconsistent visual language, stacking, timing, and user expectations.
- Examples:
  - UnifiedToastOverlay: src/main/java/com/devmod/client/notification/render/UnifiedToastOverlay.java
  - ClientNotificationManager: src/main/java/com/devmod/client/notification/ClientNotificationManager.java
  - ToastMessage usage: DialogEditorScreen, DialogGraphScreen, AbstractVoxelLabPage, VoxelLabUiTestScreen
  - Chat fallback: src/main/java/com/devmod/client/ClientUiBridgeImpl.java

2) Localization gaps (hardcoded strings in client UI)
- Multiple screens and overlays render hardcoded English strings (Component.literal / raw strings).
- Examples (not exhaustive):
  - EntityScannerScreen title: src/main/java/com/devmod/debug/client/EntityScannerScreen.java:41
  - WaveDirectiveScreen title: src/main/java/com/devmod/client/endurance/WaveDirectiveScreen.java:53
  - QuestCompletionScreen title: src/main/java/com/devmod/client/endurance/QuestCompletionScreen.java:63
  - ItemEditorScreen title: src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java:313
  - TicketCreateScreen field labels/hints: src/main/java/com/devmod/mailbox/client/screen/TicketCreateScreen.java:83-96
  - TelepadConfigScreen hints: src/main/java/com/devmod/clone/client/screen/TelepadConfigScreen.java:75
  - TransportOverlay state/hints: src/main/java/com/devmod/client/transport/TransportOverlay.java:233-263
  - UnifiedToastOverlay fallback text: src/main/java/com/devmod/client/notification/render/UnifiedToastOverlay.java:449-507
  - PartyScreen search hints: src/main/java/com/devmod/client/party/PartyScreen.java:317-345
  - DialogOptionEditorScreen param labels/hints: src/main/java/com/devmod/client/npc/DialogOptionEditorScreen.java:202-307
- Impact: mixed language UX, impossible to localize fully.

3) Tooltip behavior and consistency
- Tooltip delay mismatch: DesignTokens.TOOLTIP_DELAY_MS = 200ms, but UnifiedSettingsScreen uses 500ms.
  - src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java:79
  - src/main/java/com/devmod/client/ui/editor/core/DesignTokens.java:3791
- Multiple tooltip systems in parallel (radial tooltips, editor tooltips, custom tooltips) with different delays and layouts.
- Some tooltip content is literal (not localized) in editor components and overlays.

4) Screen fallback/error handling coverage
- BaseDevModScreen provides an error boundary + status banner, but only 10 screens use it.
- 52 screens still extend Screen directly, so they lack standard fallback UX.
- Risk: inconsistent failure states and fragmented error recovery UX.

Appendix: Screen class base distribution
- Plain Screen (non-BaseDevModScreen): 52
- BaseDevModScreen: 10
- ErrorBoundaryScreen: 1
- ModScreen: 1
- AbstractContainerScreen: 5

Quick wins suggested (ordered)
1) UX audit follow-up
- Convert highest-impact hardcoded strings (titles, input hints, overlay states) to translatable keys.
- Define a single toast/feedback pipeline and deprecate legacy toast paths.
- Align tooltip delay with DesignTokens and define a single tooltip timing source.

2) Network hardening + fallback
- Wrap any remaining playToServer payloads with PayloadValidation (TemplateNetworkHandler is missing).
- Add a standard safe screen open helper to show a fallback error screen when a payload triggers a screen open failure.

3) Performance pass
- Cache resolved toast text and truncated strings in UnifiedToastOverlay.
- Cache overlay strings (state/hint/destination) in TransportOverlay to reduce per-frame allocations.
