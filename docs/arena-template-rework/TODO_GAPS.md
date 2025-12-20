# Arena Template – Gap List (Fase 0 focus)

TODO list dei gap rimasti dopo l’ultimo pass. Ogni voce è azionabile e referenziata al codice attuale.

1) **Policy L2 schema e validazione**
   - Mancano schema/validator per perkBindings, mutatorBindings, rewardModifiers, routing weight clamp e tie-break dettagli.
   - Da aggiungere: schema JSON (docs/…), validazione in `ArenaPolicyRegistry`/`PolicyResolver`.

2) **Template JSON schema enforcement profonda**
   - `SchemaValidator` usa solo allowed/required fields; non applica type/range del file `docs/arena-template-rework/arena_template.schema.json`.
   - Servono: validator JSON schema draft 2020-12 o mappa di controlli per range (lighting 0–15, size bounds, palette, environment).

3) **Environment / dimension tags**
   - Nessuna validazione per indoor/outdoor/nether/end o fog/particles extra; spec presente in TODO ma non nel codice (`TemplateValidator`).

4) **Structure NBT config/limits**
   - Manifest caricato ma i limiti config (size/block/entity/namespaces) non sono telemeterizzati; manca evento `arena.structure.checksum_mismatch` dal loader diretto.
   - Verificare path whitelist/size clamp anche quando manifest non trovato (oggi solo warning).

5) **Hazard clamp persistence e parametri**
   - `safePut` ignora mappe immutabili: clamp potrebbe non persistere in template risolto. Valutare clone mutabile o applicare clamp su DTO prima della validazione finale.
   - Coverage/interval clamp ok ma servono telemetry dettagliate per motivo (type_limit, radius_clamp, coverage_clamp).

6) **MobSpawnStrategy typo/coverage**
   - Validator avvisa solo su tag mancanti; nessun controllo su strategia non riconosciuta (enum copre, ma validazione tag ring/corners potrebbe fallire con pos null). Considerare fallback/errore esplicito.

7) **Metrics residuals completezza**
   - Residuals calcolati solo con `MinecraftBlockPlacer`; altri placers impostano 0. ExpectedBlocks non è emesso in telemetria. Entities residual usa filtro generico (esclude solo player).
   - Aggiungere expectedBlocks in `arena.build.end` e conteggio residual anche con altri placers o indicare “unknown”.

8) **Error isolation & reload leak prevention**
   - Registry reload non invalida cache/listeners/locks; watcher teardown non implementato (spec “no leak su reload”).
   - Aggiungere clear cache, prune locks, chiusura watcher e test reload stress.

9) **Inheritance merge map**
   - Merge strategy per campi non centralizzata (SHALLOW_MERGE/OVERRIDE); diamond detection presente ma controllare uso effettivo. Documentare/implementare mapping campo→strategia.

10) **Instance-only gate copertura**
    - Gate applicato a ArenaBuilder/Async/ArenaManager/Commands; verificare altre call-site (quest managers, debug tools) e aggiungere log telemetry standard `[INSTANCE_GATE]`.

11) **Policy versioning/breakingChange enforcement**
    - `ArenaPolicyRegistry.validatePolicy` controlla min/maxTemplateVersion ma non breakingChange rules; manca fallback/log `arena.policy.version_mismatch`.

12) **Feature flag snapshot/hot-reload**
    - Config snapshot usato, ma manca un manager di reload che aggiorni FeatureFlagRegistry e propaghi change (with telemetry). Also: hazard/policy loader non agganciato a reload.
