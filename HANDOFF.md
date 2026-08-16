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

### Game server — NOT DONE, needs you

`sftp://lordbanana89@51.68.35.33:2224/`, panel `http://51.68.35.33:8080/instances/3f31be3d`.

I did not upload. The credentials are the panel login (plus a 2FA suffix when
enabled) and I do not handle passwords — that is yours to run:

```
sftp -P 2224 lordbanana89@51.68.35.33
cd mods
put "/Volumes/HD 1/Dev Mod/DevMod/devMod/build/libs/DevMod.jar" devmod-0.1.0.jar
```

Then restart the server from the panel. Before doing it, confirm the server is
on NeoForge **21.1.248 or newer** — this build hard-refuses anything older, and
the failure mode is a mod-loading error at boot, not a warning.

DevMod has both client and server code, so both sides need the same jar and they
must be the same build.

## Next steps, in order

1. Upload to the server and restart it (above).
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
