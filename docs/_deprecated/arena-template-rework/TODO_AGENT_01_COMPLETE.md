# Agent 01 – Registry & Resolver completion

> DEPRECATED: usare `docs/arena-template-rework/TODO_AGENT_01_COMPLETE.md` per lo stato completo.

- Implemented version handling (last-wins) and inheritance resolution on-load with caching in `ArenaTemplateRegistry`.
- Added telemetry hooks for instance settings clamp/coverage and structure validation wiring helper.
- Enforced policy/version compatibility, deterministic tie-break (score → version → id), weight clamp (0.1–10.0) with telemetry, and scoring breakdown double-based.
- Added override/session cleanup hooks and per-player locks with scheduled cleanup and contention telemetry in `PolicyResolver`/`OverrideManager`.
- Provided schema validation helper and tests; added policy weight tests.
