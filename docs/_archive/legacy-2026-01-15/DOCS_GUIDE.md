# Documentation Guide

> Last updated: 2025-12-26
> Status: CURRENT
> Purpose: keep docs consistent, easy to find, and easy to keep current.

---

## Where to Put Docs
- `docs/README.md`: documentation home
- `docs/MOC.md`: curated index
- `docs/areas/`: area dossiers for core systems
- `docs/subsystems/`: subsystem deep dives and specs
- `docs/cross_cutting/`: conventions and shared concerns
- `docs/testing/`: test guides and harness docs
- `docs/telemetry/`: telemetry and DuckDB docs
- `docs/project/`: planning, next steps, status
- `docs/_deprecated/`: archived or superseded docs

If a document does not fit, add it under a new directory and update `docs/DOCS_INVENTORY.md`.

---

## Status Tags
Use one of these in the header of each doc:
- CURRENT
- NEEDS_VERIFICATION
- PLANNING
- HISTORICAL
- ARCHIVE
- MIXED

Example header:

```markdown
> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION
```

---

## Linking
- Use `wiki links` for internal docs to match the existing style.
- Use standard markdown links for external references.

---

## Content Rules
- Avoid hardcoded counts or version tables unless they can be generated.
- Prefer short summaries with links to detailed docs.
- When a doc becomes outdated, change the status to NEEDS_VERIFICATION or ARCHIVE and add a short note.

---

## Updating Process
1. Update the relevant doc and set `Last updated`.
2. If you move or add docs, update `docs/DOCS_INVENTORY.md` and `docs/README.md`.
3. Keep `docs/DOCUMENTATION_STATUS.md` aligned with directory-level status.
