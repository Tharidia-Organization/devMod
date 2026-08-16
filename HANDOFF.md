# DevMod — handoff, 2026-08-16

Read this first in a fresh session. It is the state of play, what is verified,
what is not, and what to do next.

## Where the code is

- Canonical repo: `/Volumes/HD 1/Dev Mod/DevMod/devMod` (NeoForge, MC 1.21.1, Java 21)
- Branch `main`, in sync with `origin/main` (`github.com/Tharidia-Organization/devMod`)
- HEAD at handoff: `236434cc`
- `Banastaff` was merged into `main` (fast-forward) and is now redundant
- `/Volumes/HD 1/Dev Mod/DevMod copia/devMod` is a dead December-2025 snapshot,
  deletable
- The old unrelated `origin/main` (another author, 105 files, mostly gradle
  cache) is preserved on the remote branch `archive/pre-2026-main`

## Verified green at handoff

| Check | Result |
|---|---|
| `./gradlew build` | 9080 unit tests, 0 failures |
| `./gradlew runGameTestServer` | 51/51 in-game tests |
| Runtime log | 0 ERROR |

**Always capture gradle's own exit code.** `./gradlew … | tail` returns the exit
code of `tail`, and a backgrounded command's completion notification reports the
last command in the chain. Redirect to a file and `echo "EXIT=$?"` immediately
after gradle, then read that. This produced two false "green" readings before it
was noticed.

## Dependencies

- NeoForge `21.1.248` (was 21.1.219)
- GeckoLib `4.9.2` (was 4.8.3)

Both are the latest for 1.21.1. **Consequence to be aware of:** `neo_version`
feeds the generated `neoforge.mods.toml` dependency range, which is now
`[21.1.248,)`. This build refuses to load on any earlier NeoForge 21.1.x.

GeckoLib 4.9 declares `GeoModel.getModelResource(T)` / `getTextureResource(T)`
abstract *and* deprecated at once — the two-arg overloads delegate to them, so
an implementation is still required and the warning is unavoidable. It is
suppressed at the four call sites with that reason recorded.

## What was done

Five audit passes: SpotBugs at MAX effort plus parallel per-subsystem review
agents. ~150 findings, essentially all closed. Full record with reasoning,
including hypotheses **rejected** after checking, is in
`AUDIT_FINDINGS_2026-08.md` — read its "Still open" and "Rejected after
checking" sections before re-investigating anything.

Highest-impact fixes, all of which change observable behaviour:

- Body-part OBBs sat one offset above the entity, so every melee hit missed and
  fell back to a pitch heuristic. Head and leg multipliers now actually fire.
- The Nexus and every arena instance were ticked twice per server tick, so
  entities, wave timers and cooldowns ran at double speed there.
- With aggregation on (the default) no player hit reached `combat_hits`, the
  table six analytics endpoints read.
- Player recovery snapshots lived in the game-wide config dir keyed only by
  player UUID, so loading a second world could overwrite an inventory.
- `projectileSpeed` was used as an absolute velocity in three places while being
  documented, edited and damage-scaled as a multiplier: every bow fired at about
  a third of vanilla speed, and therefore a third of vanilla damage.
- Multiplayer exploits: two out-of-memory vectors from a few-byte packet, a
  teleport-anywhere, item duplication, and condition-gated rewards obtainable by
  a modified client.

## The one real gap: nothing has been play-tested

Automated coverage is unit tests plus 51 GameTests. Neither tells you how the
game *feels*. The combat work in particular needs hands-on checking:

- Melee body-part detection and the head/leg damage multipliers (they were
  effectively never firing before, so this is the biggest change)
- Head priority was deliberately dropped — a low swing whose ray continues up
  through the target no longer counts as a headshot
- Bow and crossbow velocity and damage back at vanilla values
- Execution finishers now take their full configured duration (they ran at
  double speed), with no leftover damage resistance afterwards
- Transport cores are creator-or-op only; party-teleport sessions cancellable
  only by members; the 3s arrival cooldown fires for the first time in any
  shipped build

## Deployment

### Prism client — DONE

Instance `Age of Fight - Banco 51.68.35.33` is MC 1.21.1 / NeoForge 21.1.248 /
GeckoLib 4.9.2, matching this build exactly. `build/libs/DevMod.jar` was copied
in as `devmod-0.1.0.jar`; the instance now has 12 mods. Not launched or tested.

Its other mods worth knowing about, because DevMod has soft compat for several:
CombatEvolution, EpicFight + EpicFightAwaken, age_of_fight (+ mobs, wool,
scenario), curios, invincible, ldlib2.

### Game server — BLOCKED on a governance decision, not on access

Panel `http://51.68.35.33:8080/instances/3f31be3d` = AMP instance `Modbana01`
(`InstanceID=3f31be3d-0c5a-4423-b8f7-6f63839927e8`, friendly name "Mod bana").

**Do not follow the old `sftp` + `put` recipe.** It was written from the panel's
connection details without looking at the box, and on this instance it does not
work. Verified on 2026-08-16:

- The server is reachable by SSH key as `debian` (`~/.ssh/my_custom_key`) with
  passwordless sudo. No panel password is needed for anything below.
- It runs NeoForge **21.1.248**, so the `[21.1.248,)` floor is satisfied — the
  version worry in this document was unfounded. GeckoLib on the box is 4.9.2,
  matching the build.
- `Minecraft/mods/` is **not writable and not hand-managed**. It is mode 555 with
  444 jars, is the materialisation of `mods.d/gen-900040`, and its jars are
  hardlinks into a content-addressed store at `/srv/mcbench/cache/sha256/`.
  Copying a jar in would be reverted at the next generation and would break the
  drift check (`mcbench-state --strict`: 31 sha, 32 dup, 33 other) on two counts.
- Deploys go through a CI pipeline: the `mcbench-ci` key on user `mcdeploy` has
  the forced command `/usr/local/sbin/mcbench-entry`, verbs
  `ping | state | orient | rollback gen-NNNNNN | deploy <run-id>` with a flat tar
  of jars on stdin. `mcbench-deploy` updates a lockfile, rematerialises the whole
  set from cache, stops and starts the container, runs a smoke gate and rolls
  back on failure. That key is not on this Mac.

**The blocker is not access, it is governance.** `/srv/mcbench/manifest/bench-manifest.json`
declares seven first-party mods — `combat_evolution`, `epicfight_awaken`,
`age_of_fight`, `age_of_fight_mobs`, `neo_age_of_fight_scenario`,
`age_of_fight_wool`, `epicfightx` — and `devmod` is not among them. Modbana01 is
the Age of Fight product line's test bench, not DevMod's. That manifest
describes itself as a governed substrate changed via reviewed PR in git, and its
`excluded_mods` entries (EMI, EpicFight Nightfall) show that admissions and
removals are argued decisions.

Adding DevMod would technically pass — its only mandatory deps are `neoforge`
and `minecraft`, both loader-provided, so the dependency closure the manifest is
built on does not grow — but it belongs in a PR against that manifest, not in a
box-side deploy.

The governance repo is `lordbanana89/age-of-fight-bench` (`manifest/`, `bin/`,
`.github/workflows/`), and the ecosystem workspace is `~/Desktop/Age of Fight`,
whose `BANCO-DI-TEST.md` is the full plan. Registering `devmod` needs both
`manifest/bench-manifest.json` *and* `manifest/platform.lock.properties` —
`verify-platform.sh` fails closed (exit 3) on a mod that is not in the project
registry. A drafted PR body is in the session scratchpad.

`scripts/clean_mods.sh` targets an instance `TharidiaDevModTest01` that no
longer exists. It was **not** a DevMod bench: §6.1 of `BANCO-DI-TEST.md`
records it as a 19 MB leftover holding only `mods_disabled/` with 13
third-party RPG mods, none from this ecosystem, left behind when the instance
was deleted from AMP.

DevMod has both client and server code, so both sides need the same jar and they
must be the same build.

## Next steps, in order

1. Decide where DevMod is meant to run. Either open the PR that adds `devmod`
   to `bench-manifest.json` (the entry and its rationale are drafted), or pick a
   non-governed AMP instance, or restore a dedicated DevMod bench. Only then
   deploy.
2. Launch the Prism instance, confirm DevMod loads alongside the other 11 mods,
   and watch the log for compat errors — EpicFight and GeckoLib are the ones
   most likely to surface something.
3. Play-test the combat list above. Report symptoms concretely ("headshots feel
   like they never register on X") rather than as suspicions; the audit document
   records what changed and why, so a concrete symptom maps back to a specific
   change quickly.
4. Delete `Banastaff` and `DevMod copia` once you are satisfied nothing is
   missing.

## Known-open, non-blocking

- `BLOCK_UPDATES` deliberately excludes shape updates (`neighborShapeChanged`,
  the fences-reconnecting path), so the overlay is not every block-state
  notification.
- Actions V2 is correct but still has no production call site. Its bugs were
  fixed so it *can* be enabled; enabling it is a separate decision, and shadow
  mode must not be turned on before its double-execution fix is verified in
  practice.
