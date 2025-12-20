# Arena Template – Gap List (Fase 0 focus)

Status aggiornato dopo l'ultimo pass.

1) **[DONE] Policy L2 schema e validazione**
   - Aggiunti `arena_policy.schema.json` e `PolicySchemaValidator`, wiring in `ArenaPolicyRegistry`.

2) **[DONE] Template JSON schema enforcement profonda**
   - Allineato schema docs/resources e rimosso pattern non previsto (random) nel floor.

3) **[DONE] Environment / dimension tags**
   - Validazione in `TemplateValidator` per tag dimensione, fog e particles.

4) **[DONE] Structure NBT config/limits**
   - Fallback con limiti da config e telemetria `fallback_used` + checksum_mismatch.

5) **[DONE] MobSpawnStrategy typo/coverage**
   - Mapping operativo con telemetria in `ArenaQuestIntegration` e validazione strategia in `TemplateValidator`.

6) **[DONE] Metrics residuals completezza**
   - Supporto residuals per placers non-MC via `ResidualProvider`.

7) **[DONE] Error isolation & reload leak prevention**
   - Cleanup/health check/close registry, applyConfig snapshot su reload.

8) **[DONE] Inheritance merge map**
   - Merge map centralizzata in `TemplateMergeRules` con enforcement nel registry.

9) **[DONE] Instance-only gate copertura**
   - Gate esteso a quest flow con telemetria standard.

10) **[DONE] Feature flag snapshot/hot-reload**
   - `FeatureFlagManager` con listener + wiring `applyConfig` su reload.
