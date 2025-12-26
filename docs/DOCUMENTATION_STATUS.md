# Documentation Status

> Last updated: 2025-12-26
> Status scope: navigation and link/path validation. Code alignment requires per-doc review.

---

## Status Legend
- CURRENT: actively maintained and expected to match the repo as of the last review.
- NEEDS_VERIFICATION: structure is present, but content has not been re-audited against code.
- PLANNING: future or speculative work; not implemented yet.
- HISTORICAL: dated snapshot; not expected to match current behavior.
- ARCHIVE: superseded or retired; read-only.
- MIXED: directory contains multiple statuses; check individual docs.

---

## Verification Summary
- Link/path validation report: `docs/DOCS_VERIFICATION_REPORT.md` (2025-12-26).
- Scope: 184 files checked; excludes archived docs, planning docs under `docs/subsystems/recipe-editor-spec/`, and `docs/testing/L*_REPORT.md` stubs.
- This validation checks links and repo-root paths only; it does not guarantee behavioral alignment with code.

---

## Canonical Entrypoints (CURRENT)
- `docs/README.md`
- `docs/MOC.md`
- `docs/DOCS_GUIDE.md`
- `docs/DOCS_INVENTORY.md`
- `docs/DOCS_VERIFICATION_REPORT.md`
- `docs/DOCUMENTATION_STATUS.md`
- `docs/PROJECT_TOPOLOGY.md`
- `docs/ENTRYPOINTS.md`
- `docs/ARCHITECTURE.md`
- `docs/FEATURES.md`
- `docs/GLOSSARY.md`
- `docs/TRACEABILITY_MATRIX.md`
- `docs/AUDIT_REPORT.md`

---

## Directory Status Map

| Path | Status | Notes |
|------|--------|-------|
| `docs/areas/` | NEEDS_VERIFICATION | Area dossiers need content review against code. |
| `docs/cross_cutting/` | NEEDS_VERIFICATION | Conventions need re-check. |
| `docs/subsystems/arena-template-rework/` | NEEDS_VERIFICATION | Links updated; content not fully code-audited. |
| `docs/subsystems/editor-design-system/` | NEEDS_VERIFICATION | Links updated; content not fully code-audited. |
| `docs/subsystems/impact-hud-audit/` | NEEDS_VERIFICATION | Links updated; content not fully code-audited. |
| `docs/subsystems/prismatic-shield-integration/` | NEEDS_VERIFICATION | Links updated; content not fully code-audited. |
| `docs/subsystems/recipe-editor-spec/` | PLANNING | Future spec; not implemented. |
| `docs/design/` | NEEDS_VERIFICATION | Design docs. |
| `docs/gamedesign/` | NEEDS_VERIFICATION | Game design docs. |
| `docs/ui/` | NEEDS_VERIFICATION | UI docs. |
| `docs/telemetry/` | NEEDS_VERIFICATION | Telemetry docs. |
| `docs/testing/` | MIXED | Guides are current; L*_REPORT stubs point to archived snapshots. |
| `docs/compat/` | NEEDS_VERIFICATION | Compatibility docs. |
| `docs/network/` | NEEDS_VERIFICATION | Network docs. |
| `docs/infrastructure/` | NEEDS_VERIFICATION | Infrastructure plans and notes. |
| `docs/runbook/` | NEEDS_VERIFICATION | Operational runbooks. |
| `docs/project/` | NEEDS_VERIFICATION | Project planning and drafts. |
| `docs/adr/` | NEEDS_VERIFICATION | ADRs need content review. |
| `docs/tools/` | NEEDS_VERIFICATION | Tooling docs. |
| `docs/_deprecated/` | ARCHIVE | Archived or superseded content. |
| `docs/_deprecated/testing-reports/` | HISTORICAL | Test report snapshots. |
| `docs/arena-template-rework/` | ARCHIVE | Legacy stubs; use `docs/subsystems/arena-template-rework/`. |
| `docs/editor-design-system/` | ARCHIVE | Legacy stubs; use `docs/subsystems/editor-design-system/`. |
| `docs/impact-hud-audit/` | ARCHIVE | Legacy stubs; use `docs/subsystems/impact-hud-audit/`. |
| `docs/prismatic-shield-integration/` | ARCHIVE | Legacy stubs; use `docs/subsystems/prismatic-shield-integration/`. |
| `docs/recipe-editor-spec/` | ARCHIVE | Legacy stubs; use `docs/subsystems/recipe-editor-spec/`. |
| `docs/audit/` | NEEDS_VERIFICATION | Audit/remediation notes; content needs code re-check. |
| `docs/quality/` | MIXED | Baseline/changelog snapshots plus active quality tracking; check per-doc status. |
| `docs/remediation/` | MIXED | Runbook + reports; `VERIFY.md` is current, reports are historical. |
| `docs/reorg/` | HISTORICAL | Re-architecture snapshots; not expected to match current behavior. |

---

## Alignment Notes
- This report does not guarantee that every doc matches current behavior.
- For code-level alignment, verify the relevant area or subsystem docs against the current implementation and update their status to CURRENT.
