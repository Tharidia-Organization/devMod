# Configurazione

> Ultimo aggiornamento: 2026-01-15

## Config TOML runtime

Path tipici in `run/config/`:

- `run/config/devmod-common.toml` - config principale (telemetry, combat, debug HUD, performance, Nexus).
- `run/config/devmod-mechanics.toml` - meccaniche di gioco syncate ai client.
- `run/config/devmod-portals.toml` - configurazione portali.
- `run/config/devmod-client.toml` - preferenze client/editor.

## Config JSON runtime (arena, kits)

Template arena:

- `config/devmod/arena_templates/default_flat_64.json`
- `config/devmod/arena_templates/boss_ring_80.json`
- `config/devmod/arena_templates/smoke_flat_48.json`

Policy arena:

- `config/devmod/arena_policies/default_flat_64.policy.json`
- `config/devmod/arena_policies/default_flat_64_melee.policy.json`
- `config/devmod/arena_policies/default_flat_64_ranged.policy.json`
- `config/devmod/arena_policies/boss_ring_80_casual.policy.json`
- `config/devmod/arena_policies/boss_ring_80_ranked.policy.json`
- `config/devmod/arena_policies/smoke_flat_48.policy.json`

Kits:

- `config/devmod/kits/` - directory (attualmente vuota).

## Config JSON di default (packaged)

- `src/main/resources/config/devmod/` - override e default (es. mob requirements).

## Note di reload

- `Config` e `GameMechanicsConfig` supportano reload runtime con sync ai client.
- Il reload arena aggiorna registry e snapshot (Arena + Endurance + Nexus).
