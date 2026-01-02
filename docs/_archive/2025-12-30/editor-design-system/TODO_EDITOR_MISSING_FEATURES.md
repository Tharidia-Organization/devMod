# TODO - Allineamento Editor vs design docs (08–10)

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

## ✅ Recipe Editor Bug Fixes (2025-12-20)

### Problemi risolti
- [x] **Recipe deletion non funzionante**: Il mixin `RecipeManagerMixin` non filtrava le ricette marcate per rimozione in `REMOVED_RECIPES`. Aggiunto filtering in `getAllRecipesFor()` e `byKey()`.
- [x] **Ricette vanilla non sostituite**: Quando si crea una ricetta custom che modifica una vanilla (`isModified=true`, `originalId != null`), ora la ricetta originale viene automaticamente marcata per rimozione in `RecipeInjector.injectRecipeUnsafe()`.
- [x] **Aggiunto `hasRemovedRecipes()`**: Nuovo metodo in `RecipeInjector` per ottimizzare il check di filtering nel mixin.
- [x] **Toggle "Replace Vanilla Recipe"**: Aggiunto toggle UI nell'editor (tab Settings) che permette di specificare se la nuova ricetta deve sostituire quella vanilla esistente per lo stesso item. Trova automaticamente la ricetta vanilla da sostituire.
- [x] **Aggiunto `withOriginalId()`**: Nuovo metodo in `CraftingRecipeData` per impostare `originalId` e `isModified=true`.

### File modificati
- `src/main/java/com/devmod/mixin/RecipeManagerMixin.java` - Aggiunto filtering ricette rimosse
- `src/main/java/com/devmod/recipe/RecipeInjector.java` - Aggiunto auto-removal originalId e `hasRemovedRecipes()`
- `src/main/java/com/devmod/recipe/CraftingRecipeData.java` - Aggiunto `withOriginalId()`
- `src/main/java/com/devmod/client/ui/editor/modules/RecipeModule.java` - Aggiunto toggle UI, `findVanillaRecipeForItem()`, integrazione in `buildCurrentRecipe()`

## ✅ Radial Menu - Editor Entries Mancanti (2025-12-20)

### Problemi risolti
- [x] **Food Editor mancante nel radial menu**: Aggiunta voce "Food Editor" per modificare nutrition, saturation, tempo consumo, effetti
- [x] **Fuel Editor mancante nel radial menu**: Aggiunta voce "Fuel Editor" per modificare burn time e efficiency
- [x] **Usable Editor mancante nel radial menu**: Aggiunta voce "Usable Editor" per modificare throwables, cooldowns, use duration (corni, pozioni lanciabili, palle di neve)
- [x] **Helper methods per detection**: Aggiunti `isFoodItem()`, `isFuelItem()`, `isUsableItem()` per visibility condizionale

### File modificati
- `src/main/java/com/devmod/client/ui/radial/RadialMenuRegistry.java` - Aggiunte 3 nuove voci editor + 3 helper methods

---

## 🔥 P0 - Architettura unificata (08)
- [x] Portare **WeaponModule** e **ArmorModule** su layout a sezioni (`EditorSection`) eliminando coordinate manuali (`renderContent` custom, calcolo sliderWidth, ecc.). Obiettivo: i moduli devono restituire solo sezioni e usare il renderer di base (aggiunte sezioni Custom per DPS/EHP).
- [x] Consolidare la gestione tab debug: tab debug ora usa il flusso standard di `AbstractEditorModule`.
- [x] Tracciare lo stato variante nei reset/diff: i campi variante (mace/trident/shield) contribuiscono a dirty state e `resetToOriginal` (weapon variants snapshot).

## 🎯 P1 - Weapon types & radial (09–10)
- [x] **Weapon variants UI**: Mace tab include knockback/AOE fields; Trident retains throw/return/riptide; variant data persisted in payload NBT.
- [x] **Shield variant**: valori shield viaggiano in payload/config e ora agiscono in gameplay (block strength, reflect proiettili, cooldown recovery).
- [x] **Ranged module**: payload/server path clampa valori e verifica mismatch item; auto-variant BOW/CROSSBOW; ammo filter UI + runtime enforcement (warns shooter, skips scaling se ammo non allineata).
- [x] **Weapon detection logging**: usare `Config.EDITOR_WEAPON_LOG_DETECTION` per loggare i risultati e gestire reload di whitelist/blacklist da `ConfigPaths` (hook su server start).
- [x] **Low-confidence UX**: aggiunta chiusura via ESC/Enter e blocco input di moduli/scroll/typing mentre il dialog è aperto. (Resta da valutare overlay visivo più forte se serve.)
- [x] **Radial menu**: doc aggiornato per voce Shield dedicata e fallback auto-detect a GENERAL quando il tab richiesto non è valido.

## 📋 P2 - Copertura e docs
- [ ] Aggiornare `docs/subsystems/editor-design-system` con lo stato reale (varianti incomplete, shield WIP) e screenshot/flow aggiornati.
- [ ] Aggiungere test mirati: detection (whitelist/blacklist/tag/heuristica), serialization per varianti, regressione low-confidence dialog.
- [ ] UI shell: riallineare colonna sinistra (preview/info sopra la toolbar) e rendere sempre disponibile il pulsante Apply anche in modalità preview.
- [ ] Footer: allineare gli action button dietro al separatore e includere il pulsante Templates (ora presente in UI) per accedere all’overlay relativo.
