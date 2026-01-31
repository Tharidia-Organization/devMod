# Configurazione

> Ultimo aggiornamento: 2026-01-31

## Panoramica

DevMod usa config NeoForge (TOML) per impostazioni runtime e JSON per definizioni data-driven (arena, policy, preset, ecc.).

- **TOML runtime**: `run/config/` (scritti automaticamente da NeoForge).
- **JSON runtime**: `config/devmod/` (arena template/policy, kit, ecc.).
- **JSON packaged defaults**: `src/main/resources/config/devmod/`.

## TOML runtime

File attivi (NeoForge):

- `run/config/devmod-common.toml` (Config.java, common)
- `run/config/devmod-mechanics.toml` (GameMechanicsConfig.java, common)
- `run/config/devmod-portals.toml` (PortalConfig.java, common)
- `run/config/devmod-client.toml` (EditorClientConfig.java, client)

Note:
- **Config** e **GameMechanicsConfig** supportano reload runtime con sync ai client dove previsto.
- **PortalConfig** e **EditorClientConfig** sono runtime classici (reload richiede riavvio o config reload quando supportato).
- **WISClientConfig** esiste nel codice ma **non e' registrata** con `ModConfig` (nessun file generato di default).

## Reference chiavi (TOML)

## devmod-common.toml

Config principale (Config.java)

| Chiave | Tipo | Default | Range/Enum |
|---|---|---|---|
| telemetry.enabled | boolean | true |  |
| telemetry.logHits | boolean | true |  |
| telemetry.logDeaths | boolean | true |  |
| telemetry.logSpawns | boolean | true |  |
| telemetry.tickInterval | number | 20 | 1..100 |
| telemetry.validationEnabled | boolean | false |  |
| combat.bodyPartDetection | boolean | true |  |
| combat.obbHitboxEnabled | boolean | true |  |
| combat.obbDebugAxes | boolean | false |  |
| combat.headMultiplier | number | 1.5 | 0.1..10.0 |
| combat.bodyMultiplier | number | 1.0 | 0.1..10.0 |
| combat.armsMultiplier | number | 0.8 | 0.1..10.0 |
| combat.legsMultiplier | number | 0.7 | 0.1..10.0 |
| combat.armorPenFormula | enum ArmorPenFormula | SIMPLE | SIMPLE, VANILLA_ACCURATE, PERCENTAGE, FLAT_BONUS |
| combat.armorPenMultiplier | number | 0.5 | 0.0..5.0 |
| combat.armorPenFlatBonus | number | 2.0 | 0.0..20.0 |
| debug.overlayEnabled | boolean | false |  |
| debug.impactHudEnabled | boolean | false |  |
| debug.impactHudPosition | enum HudPosition | TOP_RIGHT | TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER_RIGHT, CENTER_LEFT |
| debug.impactHudOffsetX | number | 10 | 0..200 |
| debug.impactHudOffsetY | number | 10 | 0..200 |
| debug.impactHudHistoryEnabled | boolean | true |  |
| debug.impactHudDpsEnabled | boolean | true |  |
| debug.impactHudHistoryCount | number | 5 | 1..10 |
| debug.showBodyPartBoxes | boolean | false |  |
| debug.impactVfxDuration | number | 500 | 100..5000 |
| debug.impactVfxEnabled | boolean | true |  |
| debug.impactVfxVortexEnabled | boolean | true |  |
| debug.impactVfxSlashEnabled | boolean | true |  |
| debug.impactVfxLinesEnabled | boolean | true |  |
| debug.impactVfxGlyphsEnabled | boolean | true |  |
| debug.impactVfxGlyphsExclusive | boolean | false |  |
| debug.impactVfxIntensity | number | 1.0 | 0.1..2.0 |
| debug.impactVfxUseEffekseer | boolean | false |  |
| debug.impactVfxEffekseerPreset | number | 2 | 0..5 |
| debug.impactDisplayModeDefault | string | DETAILED |  |
| performance.cacheTtl | number | 100 | 50..1000 |
| performance.cacheMaxSize | number | 1000 | 100..10000 |
| performance.mobSearchRadius | number | 128 | 32..512 |
| effects.screenShakeEnabled | boolean | true |  |
| effects.screenShakeIntensity | number | 1.0 | 0.0..2.0 |
| effects.projectileTrailsEnabled | boolean | false |  |
| effects.projectileTrailsIntensity | number | 1.0 | 0.0..2.0 |
| badgePopup.enabled | boolean | true |  |
| badgePopup.durationMs | number | 5000 | 1000..15000 |
| badgePopup.slideInMs | number | 400 | 100..1000 |
| badgePopup.fadeOutMs | number | 600 | 100..2000 |
| badgePopup.yPosition | number | 12 | 0..200 |
| badgePopup.soundEnabled | boolean | true |  |
| badgePopup.soundVolume | number | 1.0 | 0.0..1.0 |
| badgePopup.particlesEnabled | boolean | true |  |
| badgePopup.glowEnabled | boolean | true |  |
| nexus.enabled | boolean | true |  |
| nexus.rebuildPolicy | enum NexusRebuildPolicy | ON_VERSION_MISMATCH | ALWAYS, ON_VERSION_MISMATCH, ONCE, NEVER |
| nexus.buildMode | enum NexusBuildMode | STAGGERED | SYNC, STAGGERED |
| nexus.buildStepInterval | number | 2 | 1..100 |
| nexus.tickMode | enum NexusTickMode | THROTTLED | ALWAYS, PLAYERS_ONLY, THROTTLED |
| nexus.idleTickInterval | number | 40 | 1..400 |
| nexus.keepLoaded | boolean | true |  |
| nexus.forceChunkRadius | number | 6 | 1..16 |
| nexus.tickDistance | number | 3 | 1..10 |
| nexus.worldBorderSize | number | 256 | 64..512 |
| nexus.spawnMode | enum NexusSpawnMode | ROUND_ROBIN | DEFAULT, ROUND_ROBIN, BY_TEAM |
| nexus.paletteProfile | enum NexusPaletteProfile | DEFAULT | DEFAULT, PERFORMANCE, CUSTOM |
| nexus.entryMessage | boolean | true |  |
| nexus.entrySound | boolean | true |  |
| nexus.zoneCues | boolean | true |  |
| nexus.zoneHints | boolean | true |  |
| nexus.zoneCueCooldown | number | 20 | 0..200 |
| nexus.dummyFeedback | boolean | true |  |
| nexus.dummyFeedbackCooldown | number | 4 | 0..40 |
| nexus.allowBuildAnywhere | boolean | true |  |
| nexus.restrictBuilding | boolean | true |  |
| nexus.hideMissingModPods | boolean | false |  |
| nexus.dynamicModPods | boolean | true |  |
| nexus.layoutOverlayTemplate | string |  |  |
| nexus.ambientEffects | boolean | true |  |
| nexus.ambientParticleInterval | number | 12 | 2..100 |
| nexus.avatarEnabled | boolean | true |  |
| nexus.avatarName | string | NEXA |  |
| nexus.avatarSkin | string | texture:e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2ZkZWUyMDE1MGM3YmY2NDdmYjA3ZmM4MjRiNzc5ZGRhNDA4YTEyYmE2NGQ2ZTA3NDhjNzA3ZjMyMjMxY2FhZSJ9fX0= |  |
| nexus.avatarGlow | boolean | true |  |
| nexus.avatarFloatOffset | number | 2 | 0..12 |
| nexus.avatarFloatEnabled | boolean | true |  |
| nexus.avatarFloatAmplitude | number | 0.3 | 0.0..2.0 |
| nexus.avatarFloatSpeed | number | 0.05 | 0.01..0.5 |
| nexus.avatarParticlesEnabled | boolean | true |  |
| nexus.avatarParticleInterval | number | 10 | 1..100 |
| nexus.avatarRotate | boolean | true |  |
| nexus.avatarRotateSpeed | number | 0.5 | 0.0..5.0 |
| nexus.portalsEnabled | boolean | false |  |
| nexus.portalParticleInterval | number | 5 | 1..60 |
| nexus.hologramsEnabled | boolean | true |  |
| nexus.hologramUpdateInterval | number | 600 | 20..6000 |
| nexus.hologramMaxPerDimension | number | 64 | 8..256 |
| nexus.performanceEnabled | boolean | true |  |
| nexus.lazyChunksEnabled | boolean | false |  |
| nexus.entityCullingEnabled | boolean | true |  |
| nexus.cullDistance | number | 64 | 16..256 |
| nexus.slots.hubSize | number | 384 | 128..4096 |
| nexus.slots.centerSize | number | 64 | 32..512 |
| nexus.slots.zoneSize | number | 96 | 48..512 |
| nexus.slots.corridorWidth | number | 16 | 4..64 |
| nexus.slots.floorY | number | 64 | 4..256 |
| nexus.slots.zoneHeight | number | 64 | 16..128 |
| nexus.slots.slotsConfigPath | string |  |  |
| nexus.slots.autoCreateZones | boolean | true |  |
| nexus.slots.autoCreatePortals | boolean | true |  |
| nexus.slots.clearOnUnlink | boolean | false |  |
| clonePulverizer.grinderDurability | number | 256 | 16..4096 |
| clonePulverizer.operationsPerDamage | number | 8 | 1..64 |
| adminPanel.permissionLevel | number | 2 | 0..4 |

## devmod-mechanics.toml

Game mechanics (GameMechanicsConfig.java)

| Chiave | Tipo | Default | Range/Enum |
|---|---|---|---|
| execution.hpThreshold | number | 0.15 | 0.01..0.50 |
| execution.durationTicks | number | 40 | 10..100 |
| execution.cooldownTicks | number | 60 | 0..200 |
| execution.styleReward | number | 200 | 0..1000 |
| execution.hpRegenPercent | number | 0.05 | 0.0..0.50 |
| execution.dropBoost | number | 0.30 | 0.0..2.0 |
| execution.interruptVulnerability | number | 2.0 | 1.0..5.0 |
| execution.range | number | 4.0 | 1.0..10.0 |
| combo.timeoutTicks | number | 60 | 20..200 |
| combo.basePoints | number | 10 | 1..100 |
| combo.multiplierIncrement | number | 0.1 | 0.01..0.5 |
| combo.maxMultiplier | number | 4.0 | 1.0..10.0 |
| combo.finisherThreshold | number | 10 | 3..50 |
| combo.juggleBonus | number | 25 | 0..200 |
| combo.headshotBonus | number | 15 | 0..200 |
| combo.executionBonus | number | 50 | 0..500 |
| styleRank.dThreshold | number | 0 | 0..100 |
| styleRank.cThreshold | number | 100 | 50..500 |
| styleRank.bThreshold | number | 300 | 100..1000 |
| styleRank.aThreshold | number | 600 | 200..2000 |
| styleRank.sThreshold | number | 1000 | 400..5000 |
| styleRank.ssThreshold | number | 1500 | 600..8000 |
| styleRank.sssThreshold | number | 2500 | 1000..15000 |
| styleRank.decayRate | number | 5.0 | 0.0..50.0 |
| styleRank.decayDelayTicks | number | 100 | 0..400 |
| momentum.decayRate | number | 0.05 | 0.0..0.5 |
| momentum.killBoost | number | 0.2 | 0.0..1.0 |
| momentum.hitBoost | number | 0.05 | 0.0..0.5 |
| momentum.overdriveThreshold | number | 0.9 | 0.5..1.0 |
| momentum.overdriveDurationTicks | number | 200 | 60..600 |
| flowState.staleThresholdTicks | number | 100 | 20..400 |
| flowState.freshBonusMultiplier | number | 1.5 | 1.0..3.0 |
| flowState.virtuosoThreshold | number | 0.8 | 0.5..1.0 |
| flowState.virtuosoDurationTicks | number | 100 | 20..300 |
| bargain.enabled | boolean | true |  |
| bargain.altarSpawnWave | number | 3 | 1..20 |
| bargain.altarIntervalWaves | number | 2 | 1..10 |
| bargain.maxCursesPerRun | number | 5 | 1..20 |
| bargain.choiceTimeoutTicks | number | 400 | 100..1200 |
| bargain.cursePowerMultiplier | number | 1.0 | 0.1..3.0 |
| bargain.boonPowerMultiplier | number | 1.0 | 0.1..3.0 |
| hazards.enabled | boolean | true |  |
| hazards.triggerWaves.floorCrumble | number | 3 | 1..50 |
| hazards.triggerWaves.bloodMoon | number | 5 | 1..50 |
| hazards.triggerWaves.arenaShrink | number | 7 | 1..50 |
| hazards.triggerWaves.lightningStorm | number | 9 | 1..50 |
| hazards.triggerWaves.voidRifts | number | 11 | 1..50 |
| hazards.durations.floorCrumble | number | 120 | 20..600 |
| hazards.durations.bloodMoon | number | 600 | 100..2400 |
| hazards.durations.arenaShrink | number | 400 | 100..1200 |
| hazards.durations.lightningStorm | number | 300 | 60..1200 |
| hazards.durations.voidRifts | number | 200 | 60..800 |
| hazards.damage.lightning | number | 6.0 | 1.0..20.0 |
| hazards.damage.voidRift | number | 2.0 | 0.5..10.0 |
| hazards.damage.bloodMoonMobBuff | number | 1.5 | 1.0..3.0 |
| hazards.damage.shrinkRate | number | 0.05 | 0.01..0.5 |
| perkSynergy.enabled | boolean | true |  |
| perkSynergy.hiddenPerksEnabled | boolean | true |  |
| perkSynergy.sacrificeEnabled | boolean | true |  |
| perkSynergy.sacrificeStyleCost | number | 500 | 0..5000 |
| perkSynergy.discoveryXpBase | number | 100 | 10..1000 |
| perkSynergy.synergyBonusMultiplier | number | 1.0 | 0.1..3.0 |
| waves.baseMobCount | number | 5 | 1..50 |
| waves.mobScaling | number | 1.2 | 1.0..3.0 |
| waves.intermissionTicks | number | 100 | 20..600 |
| waves.eliteChanceBase | number | 0.1 | 0.0..1.0 |
| waves.eliteChanceScaling | number | 0.02 | 0.0..0.1 |
| waves.bossInterval | number | 5 | 1..20 |
| rewards.xpMultiplier | number | 1.0 | 0.1..10.0 |
| rewards.styleMultiplier | number | 1.0 | 0.1..10.0 |
| rewards.dropRateBonus | number | 0.0 | 0.0..5.0 |
| rewards.bonusChestWaveInterval | number | 3 | 1..20 |
| seasonPass.maxTier | number | 100 | 10..500 |
| seasonPass.xpPerTier | number | 1000 | 100..10000 |
| seasonPass.durationDays | number | 90 | 7..365 |
| seasonPass.xpPerKill | number | 2 | 0..100 |
| seasonPass.xpPerWave | number | 50 | 0..500 |
| seasonPass.xpPerBoss | number | 200 | 0..1000 |
| seasonPass.xpDailyChallenge | number | 500 | 0..5000 |
| seasonPass.xpWeeklyChallenge | number | 2000 | 0..20000 |
| seasonPass.xpStyleS | number | 100 | 0..1000 |
| seasonPass.xpStyleSS | number | 200 | 0..2000 |
| seasonPass.xpStyleSSS | number | 500 | 0..5000 |
| seasonPass.xpFlawlessWave | number | 75 | 0..500 |
| seasonPass.xpHighCombo50 | number | 150 | 0..1000 |
| seasonPass.xpHighCombo100 | number | 300 | 0..2000 |
| seasonPass.xpBoostMultiplier | number | 1.5 | 1.0..5.0 |
| seasonPass.xpBoostDurationMinutes | number | 60 | 5..1440 |
| guild.maxSize | number | 50 | 5..200 |
| guild.maxLevel | number | 20 | 5..100 |
| guild.createCost | number | 5000 | 0..100000 |
| guild.xpPerLevelBase | number | 1000 | 100..10000 |
| guild.xpScaling | number | 1.5 | 1.0..3.0 |
| guild.bankTaxPercent | number | 10 | 0..50 |
| guild.objectiveKillsTarget | number | 10000 | 100..100000 |
| guild.objectiveWavesTarget | number | 500 | 10..5000 |
| guild.objectiveBossesTarget | number | 100 | 5..1000 |
| guild.objectiveTokensTarget | number | 50000 | 1000..1000000 |
| guild.objectiveChallengesTarget | number | 50 | 5..500 |
| guild.perkUnlockLevel1 | number | 5 | 1..20 |
| guild.perkUnlockLevel2 | number | 10 | 2..30 |
| guild.perkUnlockLevel3 | number | 15 | 3..40 |
| guild.bonusTokens5 | number | 0.05 | 0.0..0.5 |
| guild.bonusTokens10 | number | 0.10 | 0.0..0.5 |
| guild.bonusTokens15 | number | 0.15 | 0.0..0.5 |
| guild.bonusXp5 | number | 0.05 | 0.0..0.5 |
| guild.bonusXp10 | number | 0.10 | 0.0..0.5 |
| ascension.maxLevel | number | 10 | 1..50 |
| ascension.minPrestige | number | 100 | 10..1000 |
| ascension.costScaling | number | 50 | 0..500 |
| ascension.damageBonusPerLevel | number | 0.05 | 0.0..0.5 |
| ascension.defenseBonusPerLevel | number | 0.03 | 0.0..0.3 |
| ascension.tokenMultiplierPerLevel | number | 0.10 | 0.0..1.0 |
| ascension.lifestealBonusPerLevel | number | 0.01 | 0.0..0.1 |
| ascension.critBonusPerLevel | number | 0.02 | 0.0..0.2 |
| ascension.comboDecayReductionPerLevel | number | 0.05 | 0.0..0.5 |
| ascension.extraPerkSlotsPerLevel | number | 1 | 0..3 |
| ascension.startingHpBonusPerLevel | number | 0.05 | 0.0..0.5 |
| tension.baseWaveGain | number | 0.12 | 0.0..1.0 |
| tension.noHitBonus | number | 0.20 | 0.0..1.0 |
| tension.minThreshold | number | 0.70 | 0.1..1.0 |
| tension.maxThreshold | number | 1.0 | 0.5..2.0 |
| tension.eliteKillBonus | number | 0.05 | 0.0..0.5 |
| tension.comboBonusThreshold | number | 0.03 | 0.0..0.2 |
| tension.styleSBonus | number | 0.10 | 0.0..0.5 |
| tension.minWavesBeforeBoss | number | 3 | 1..20 |
| tension.maxWavesWithoutBoss | number | 8 | 3..30 |
| tension.decayAfterBoss | number | 0.50 | 0.0..1.0 |
| perkRarity.commonWeight | number | 60 | 0..100 |
| perkRarity.uncommonWeight | number | 25 | 0..100 |
| perkRarity.rareWeight | number | 10 | 0..100 |
| perkRarity.epicWeight | number | 4 | 0..100 |
| perkRarity.legendaryWeight | number | 1 | 0..100 |
| perkRarity.choicesPerSelection | number | 3 | 2..6 |
| perkRarity.rerollCostBase | number | 50 | 0..500 |
| perkRarity.rerollCostIncrement | number | 25 | 0..200 |
| perkRarity.damageBonusCommon | number | 0.05 | 0.0..0.5 |
| perkRarity.damageBonusUncommon | number | 0.10 | 0.0..0.5 |
| perkRarity.damageBonusRare | number | 0.15 | 0.0..0.5 |
| perkRarity.damageBonusEpic | number | 0.20 | 0.0..0.5 |
| perkRarity.damageBonusLegendary | number | 0.30 | 0.0..1.0 |
| challenges.dailyCount | number | 3 | 1..10 |
| challenges.weeklyCount | number | 2 | 1..5 |
| challenges.dailyResetHour | number | 0 | 0..23 |
| challenges.weeklyResetDay | number | 1 | 1..7 |
| challenges.killsTargetMin | number | 50 | 5..200 |
| challenges.killsTargetMax | number | 200 | 50..1000 |
| challenges.wavesTargetMin | number | 5 | 1..20 |
| challenges.wavesTargetMax | number | 15 | 5..50 |
| challenges.comboTargetMin | number | 25 | 5..50 |
| challenges.comboTargetMax | number | 100 | 50..500 |
| challenges.dailyTokenReward | number | 100 | 0..1000 |
| challenges.weeklyTokenReward | number | 500 | 0..5000 |
| challenges.dailyXpReward | number | 500 | 0..5000 |
| challenges.weeklyXpReward | number | 2000 | 0..20000 |

## devmod-portals.toml

Portal settings (PortalConfig.java)

| Chiave | Tipo | Default | Range/Enum |
|---|---|---|---|
| range.defaultRange | number | DEFAULT_RANGE_VALUE | 1..100000 |
| range.rangeWithEnhancer | number | RANGE_WITH_ENHANCER_VALUE | 1..100000 |
| range.rangeWithStrongEnhancer | number | RANGE_WITH_STRONG_ENHANCER_VALUE | 1..1000000 |
| behavior.unlimitedRange | boolean | false |  |
| behavior.alwaysInterdimensional | boolean | false |  |
| behavior.alwaysHaste | enum HasteMode | FALSE | TRUE, FALSE, CREATIVE_ONLY |
| behavior.privatePortals | boolean | false |  |
| redstone.mode | enum RedstoneMode | IGNORE | OFF, ON, IGNORE |

## devmod-client.toml

Client editor (EditorClientConfig.java)

| Chiave | Tipo | Default | Range/Enum |
|---|---|---|---|
| editor.soundsEnabled | boolean | true |  |
| editor.defaultMode | enum EditorDefaultMode | PREVIEW | PREVIEW, APPLY |
| editor.uiScale | enum EditorUiScale | AUTO | AUTO, SCALE_1_0, SCALE_1_25, SCALE_1_5, SCALE_2_0 |
| editor.weaponDetectionHeuristic | boolean | true |  |
| editor.weaponDetectionMinConfidence | number | 0.8 | 0.0..1.0 |
| editor.weaponDetectionLog | boolean | false |  |
| editor.treatPickaxeAsWeapon | boolean | false |  |
| editor.gridValidation | boolean | false |  |

## wis-client (unregistered)

WIS client (WISClientConfig.java)

| Chiave | Tipo | Default | Range/Enum |
|---|---|---|---|
| wis.hud.position | enum HudPosition | TOP_RIGHT | TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT |
| wis.hud.opacity | number | 0.2 | 0.0..1.0 |
| wis.hud.extendedStats | boolean | false |  |
| wis.briefing.mobListLimit | number | 5 | 1..20 |
| wis.briefing.autoShowDebrief | boolean | true |  |
| wis.system.enabled | boolean | true |  |

## JSON runtime

- Arena templates: `config/devmod/arena_templates/`
- Arena policies: `config/devmod/arena_policies/`
- Kits: `config/devmod/kits/` (se presente)

Inventario completo: vedere `docs/IMPLEMENTATION_STATE.md` (sezione Config inventory).

## JSON packaged defaults

- `src/main/resources/config/devmod/` (override e default distribuiti nella mod)

Inventario completo: vedere `docs/IMPLEMENTATION_STATE.md`.
