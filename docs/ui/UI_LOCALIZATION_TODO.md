# UI Localization TODO

Last updated: 2025-12-25  
Scope: Remaining UI string literals in client UI screens/components.

## Completed
- [x] `src/main/java/com/devmod/client/endurance/EnduranceQuestScreen.java`
- [x] `src/main/java/com/devmod/client/endurance/EnduranceSettingsScreen.java`
- [x] `src/main/java/com/devmod/client/endurance/KitSelectionScreen.java`
- [x] `src/main/java/com/devmod/client/endurance/EnduranceShopScreen.java`

## Endurance Screens (pending)
- [ ] `src/main/java/com/devmod/client/endurance/WaveDirectiveScreen.java` - title, header, fallback name, reward line
- [ ] `src/main/java/com/devmod/client/endurance/WaveCheckpointScreen.java` - header, stats labels, style rank, progress, keybind hint
- [ ] `src/main/java/com/devmod/client/endurance/PerkSelectionScreen.java` - button labels, required/suggested tags, stacks, badges, quick-compare strings
- [ ] `src/main/java/com/devmod/client/endurance/QuestCompletionScreen.java` - all section headers, rewards/bonuses/stats labels, keybind hint
- [ ] `src/main/java/com/devmod/client/endurance/QuestExitConfirmScreen.java` - confirm dialog body lines
- [ ] `src/main/java/com/devmod/client/endurance/QuestDeathScreen.java` - bullet/prefix lines and composed strings (minor cleanup)

## Party UI (pending)
- [ ] `src/main/java/com/devmod/client/party/InvitePopupScreen.java` - invite line and quest type descriptions
- [ ] `src/main/java/com/devmod/client/party/PartyScreen.java` - input hints, button labels, tooltips, status text
- [ ] `src/main/java/com/devmod/client/party/PartyScreenRenderer.java` - headers, empty-state copy, stats labels

## Testing / QA / Dev Tools (pending)
- [ ] `src/main/java/com/devmod/client/testing/BadgeTestScreen.java` - title, buttons, queue status, hints
- [ ] `src/main/java/com/devmod/client/testing/QATestingScreen.java` - button labels, default names, status messages
- [ ] `src/main/java/com/devmod/client/ui/hub/TestingHub.java` - panel titles, hints, notifications, action labels
- [ ] `src/main/java/com/devmod/client/ui/testing/VoxelLabScreen.java` - title/subtitle/tab labels
- [ ] `src/main/java/com/devmod/client/ui/testing/VoxelLabUiTestScreen.java` - showcase labels, section headers
- [ ] `src/main/java/com/devmod/client/ui/wizard/QuickTestWizard.java` - presets, steps, labels, hints, overlay names
- [ ] `src/main/java/com/devmod/client/arena/ui/ArenaTestWizard.java` - title, search hints, button labels

## Core UI (pending)
- [ ] `src/main/java/com/devmod/client/ui/WelcomeScreen.java` - feature list, keybind list, headings, hints
- [ ] `src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java` - hardcoded item labels (e.g., "Mob Editor", "Edit: ...")
- [ ] `src/main/java/com/devmod/client/ui/radial/RadialActionDetailScreen.java` - "Id: ..." label
- [ ] `src/main/java/com/devmod/client/ui/OpenExternalConfirmScreen.java` - default title string

## Editors / Tools (pending)
- [ ] `src/main/java/com/devmod/client/ui/screens/MobConfigScreenRenderer.java` - tab labels, preset names, buttons, dialog copy
- [ ] `src/main/java/com/devmod/client/ui/screens/MobConfigScreenState.java` - preset names/descriptions
- [ ] `src/main/java/com/devmod/client/ui/screens/MobEquipmentScreen.java` - section headers, slot labels, button labels, errors
- [ ] `src/main/java/com/devmod/client/ui/screens/TelemetryDashboardScreen.java` - tab names, section headers, overlay labels, hints, confirm copy
- [ ] `src/main/java/com/devmod/client/ui/RoomBoundsEditorScreen.java` - labels, dialog copy, status/error text
- [ ] `src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java` - button labels, dialog copy, search hints, placeholders
- [ ] `src/main/java/com/devmod/client/ui/editor/StaminaSystemEditor.java` - title + slider labels
- [ ] `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` - status messages, dialog copy, notifications (large pass)

## Shared Model Labels Used In UI (pending)
- [ ] `src/main/java/com/devmod/endurance/RewardSystem.java` - `Currency.displayName`, `ShopCategory.displayName`, `LootTier.displayName`
- [ ] `src/main/java/com/devmod/endurance/QuestType.java` - `displayName` and `description` used by party invite UI
- [ ] Other enums with `displayName` shown in UI (e.g., style ranks, tiers) - confirm usage and add I18n keys

## Notes
- Update translation keys in `src/main/resources/assets/devmod/lang/en_us.json`.
- Prefer `I18n.translate(...)` or `I18n.ui(...)` in UI code; avoid inline string concatenation when it affects grammar.
- For composed strings with symbols/bullets, consider whole-line keys to allow reordering in other languages.
