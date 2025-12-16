# TODO - Allineamento Editor vs design docs (08–10)

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
- [ ] Aggiornare `docs/editor-design-system` con lo stato reale (varianti incomplete, shield WIP) e screenshot/flow aggiornati.
- [ ] Aggiungere test mirati: detection (whitelist/blacklist/tag/heuristica), serialization per varianti, regressione low-confidence dialog.
- [ ] UI shell: riallineare colonna sinistra (preview/info sopra la toolbar) e rendere sempre disponibile il pulsante Apply anche in modalità preview.
- [ ] Footer: allineare gli action button dietro al separatore e includere il pulsante Templates (ora presente in UI) per accedere all’overlay relativo.
