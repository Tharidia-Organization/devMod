# Orphanage Integrations

**Date**: 2025-12-27

## ConfigurableTestTemplate

- **Before**: `ConfigurableTestTemplate` non registrato in `DynamicTestGenerator`; nessun test da JSON.
- **After**: `DynamicTestGenerator.registerConfigTemplates()` carica config da `ModTestConfig` e registra i template.
- **Call-site**: `src/main/java/com/devmod/testing/DynamicTestGenerator.java`
- **How to test**:
  1. Creare `config/devmod/test_templates/<modid>.json`
  2. Avviare generazione test e verificare logging: `Registered config template for <modid>`

## PathSanitizer

- **Before**: `PathSanitizer` non usato; exporter scrivevano path direttamente.
- **After**: validazione path in `CsvExporter`, `JsonReportExporter`, `HeatmapExporter` (PNG consentiti).
- **Call-sites**:
  - `src/main/java/com/devmod/telemetry/export/CsvExporter.java`
  - `src/main/java/com/devmod/telemetry/export/JsonReportExporter.java`
  - `src/main/java/com/devmod/telemetry/export/HeatmapExporter.java`
- **How to test**:
  1. Eseguire export (CSV/JSON/Heatmap)
  2. Verificare log di export e path validati
