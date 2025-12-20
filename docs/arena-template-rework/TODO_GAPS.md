# Arena Template – Gap List (Fase 0 focus)

TODO list dei gap rimasti dopo l’ultimo pass. Ogni voce è azionabile e referenziata al codice attuale.

1) **Policy L2 schema e validazione**
   - Schema JSON per perkBindings/mutatorBindings/rewardModifiers mancante; tie-break/routing details non documentati. Validazione in `PolicyResolver` ancora basata su manual parse.

2) **Template JSON schema enforcement profonda (parziale)**
   - Controlli base estesi (size/walls/ceiling/instance/compat/palette fog/particles) ma manca allineamento completo con `arena_template.schema.json` (palette materiali obbligatori, environment dimension tags, validator JSON schema draft).

3) **Environment / dimension tags**
   - Nessuna validazione per indoor/outdoor/nether/end o fog/particles estese; spec presente in TODO ma non nel codice (`TemplateValidator`).

4) **Structure NBT config/limits (parziale)**
   - Telemetria checksum/loaded/rejected presente, ma non si applicano/telemetrizzano tutti i limiti da config quando manca manifest; path whitelist/clamp fallback da rivedere.

5) **MobSpawnStrategy typo/coverage**
   - Validazione ring/corners presente; manca fallback/errore se pos null e mapping operativo nei builder.

6) **Metrics residuals completezza (parziale)**
   - Filtro entità esteso (player/marker/area_effect_cloud esclusi) e expected_blocks emesso; residuals restano unknown (-1) per placers non-MC (residuals_unknown=true).

7) **Error isolation & reload leak prevention (parziale)**
   - Pruning di locks/listenerHandles aggiunto; mancano teardown watcher/cache esterne e stress test reload.

8) **Inheritance merge map**
   - Strategia campo→merge non centralizzata/documentata; verificare enforcement e diamond detection per tutti i campi.

9) **Instance-only gate copertura**
    - Gate applicato a builder/commands/ArenaManager; da verificare altri call-site (quest manager, debug tools) e allineare telemetria standard `[INSTANCE_GATE]`.

10) **Feature flag snapshot/hot-reload (parziale)**
    - `FeatureFlagManager.applyConfig` esiste ed è richiamato nel bootstrap; manca wiring nei flussi di config reload/policy/hazard loader per propagare cambi runtime con telemetry.
