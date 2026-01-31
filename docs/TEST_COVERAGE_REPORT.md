# Test Coverage Report

> Ultimo aggiornamento: 2026-01-31
> Stato: CURRENT (report generato)

## Sintesi

Report JaCoCo generato con successo.

Comandi eseguiti:
- `./gradlew test jacocoTestReport`
- `./gradlew coverageSummary`
- `./gradlew jacocoTestCoverageVerification`

Esito:
- Test: OK
- JaCoCo report: OK
- Coverage verification: OK

## Configurazione coverage attuale

- Tool: JaCoCo `0.8.11`.
- Task: `jacocoTestReport` + `jacocoTestCoverageVerification` in `jacoco-coverage-rules.gradle`.
- Task aggiuntivo: `coverageSummary` (da `jacoco-coverage-rules.gradle`).
- Esclusioni principali dai report: `**/client/**`, `**/network/**`, `**/integration/**`, `**/mixin/**`, `**/generated/**`.
- Regole di coverage (DD41, attive su `check`):
  - Core arena (cleanup/template/registry/validation/builder): >= 80% line
  - MC-dependent (monitor/world/entity): >= 60% line
  - Network/UI (arena.ui/arena.hud/arena.dashboard): >= 50% line

Nota: `coverageSummary` e' disponibile dopo l'applicazione di `jacoco-coverage-rules.gradle`.

## Suite di test

- Framework: JUnit 5 (`useJUnitPlatform`).
- Tag esclusi nei run standard: `load`.
- Parallelismo: `maxParallelForks = 1`.

## Coverage Summary (2026-01-31)

| Counter | Coverage | Covered/Total |
| --- | --- | --- |
| INSTRUCTION | 12.99% | 74804/576077 |
| BRANCH | 7.50% | 3808/50768 |
| LINE | 13.20% | 15707/118960 |
| COMPLEXITY | 8.15% | 3887/47708 |
| METHOD | 13.27% | 2888/21768 |
| CLASS | 23.40% | 524/2239 |

## Come rigenerare la coverage

1. Eseguire `./gradlew test jacocoTestReport`.
2. (Opzionale) Summary rapido: `./gradlew coverageSummary`.
3. Report HTML: `build/reports/jacoco/test/html/index.html`.
4. Report XML: `build/reports/jacoco/test/jacocoTestReport.xml`.
5. Verifica soglie: `./gradlew jacocoTestCoverageVerification`.
