# DevMod 2.0 - Game Design Evolution

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

**Author:** Senior Game Designer
**Date:** 2024-12-24
**Completion Status:** ✅ COMPLETE - All Systems Implemented

---

## Implementation Roadmap

### P0 - Resonance Chain System ✅ COMPLETE

- [x] Create `ResonanceChainSystem.java` - core tracking logic
- [x] Create `ResonanceEvent.java` - event data structure
- [x] Add resonance detection in `EnduranceEventCombat.java`
- [x] Create `ResonanceChainPayload.java` - network sync
- [x] Integrate with `ComboSystem.java` for style bonuses
- [x] Add resonance multiplier to reward calculation
- [x] Integrate with Tide system for threat reduction

### P1 - Blood Contracts System ✅ COMPLETE

- [x] Create `BloodContract.java` - contract definition
- [x] Create `BloodContractRegistry.java` - available contracts (8 contracts)
- [x] Create `ActiveContractManager.java` - runtime tracking
- [x] Add contract effects to wave mechanics
- [x] Create `ContractSyncPayload.java` - network sync
- [x] Add contract HUD integration
- [x] Integrate with reward multipliers

### P1 - Signature Weapons System ✅ COMPLETE

- [x] Create `SoulImprint.java` - weapon history data
- [x] Create `WeaponTraitRegistry.java` - 12 unlockable traits
- [x] Create `SoulImprintManager.java` - tracking per weapon
- [x] Record weapon stats in `EnduranceEventCombat.java`
- [x] Add trait effects to `DamageCalculator.java`
- [x] Create `SignatureWeaponTooltip.java` - lore display
- [x] Persist weapon data via DataComponents API
- [x] Integrate SSS/no-hit wave tracking

### P2 - Nemesis Evolution System ✅ COMPLETE

- [x] Create `NemesisProfile.java` - player behavior tracking
- [x] Create `NemesisAdaptation.java` - 11 adaptation definitions
- [x] Create `NemesisEvolutionManager.java` - per-player boss memory
- [x] Apply adaptations via attribute modifiers
- [x] Add scar level tracking for bosses
- [x] Persist nemesis data per player (NBT serialization)
- [x] Integrate boss defeat recording

### P2 - The Tide (Global Threat) ✅ COMPLETE

- [x] Create `TideManager.java` - global threat tracker
- [x] Create `TideLevel.java` - 5 threat level definitions
- [x] Create Tide Boss event system
- [x] Integrate with quest start/end events
- [x] Integrate with player death events
- [x] Integrate with boss kill events
- [x] Integrate with resonance chain triggers
- [x] Add tide effects via mob stat multipliers

---

## System 1: Resonance Chain (P0)

### Overview
When multiple players hit the same enemy within a tight time window, trigger a "Resonance Chain" that multiplies damage and grants massive style bonuses.

### Technical Design

```
ResonanceChainSystem
├── recentHits: Map<EntityID, List<HitRecord>>
├── RESONANCE_WINDOW_MS: 500 (2-player), 300 (3-player), 200 (4-player)
├── checkForResonance(entityId, attackerId, timestamp)
├── triggerResonance(entityId, participants, tier)
└── cleanup() - remove stale hit records

HitRecord
├── attackerId: UUID
├── timestamp: long
├── damage: float
└── weaponType: String

ResonanceTier
├── DUO (2 players): 1.5x damage, +200 style each
├── TRINITY (3 players): 2.5x damage, +500 style, AoE shockwave
├── APOCALYPSE (4 players): 5.0x damage, +1000 style, instant SSS
```

### Integration Points
1. `DamageHandler.onEntityDamage()` → record hit, check resonance
2. `ComboSystem.recordAction()` → add RESONANCE action type
3. `RewardCalculator` → apply resonance bonus multiplier
4. `EnduranceTelemetryService` → log resonance events

### Visual/Audio Feedback
- Screen flash (gold for DUO, purple for TRINITY, red for APOCALYPSE)
- Unique sound effect per tier
- Floating text announcement
- Particle burst on target

---

## System 2: Blood Contracts (P1)

### Overview
Optional high-risk challenges players can sign before each wave for massive reward multipliers.

### Contract Examples

| Contract | Effect | Reward |
|----------|--------|--------|
| Aggression | +50% damage dealt, +100% damage taken | +100% tokens |
| Hubris | Drop below S rank = instant death | +200% tokens, guaranteed Legendary perk |
| Haste | Complete wave in 60s or lose all tokens | +150% tokens, Blood Gem |
| Fool | Random perk removed | Next 2 perks Epic+ |
| Sacrifice | Random party member takes 2x damage | Party +100% tokens |
| Glass | One-hit death | +300% tokens |
| Pacifist | Cannot use primary weapon | +150% tokens |
| Vampire | No natural regen, lifesteal only | +100% tokens |

### Technical Design

```
BloodContract
├── id: ResourceLocation
├── name: Component
├── description: Component
├── effect: ContractEffect (functional interface)
├── rewardMultiplier: float
├── isPartyContract: boolean
└── isCompatibleWith(other): boolean

ActiveContractManager
├── activeContracts: Map<UUID, List<BloodContract>>
├── signContract(player, contract)
├── checkContractViolation(player, event)
├── applyContractEffects(player)
└── clearContracts(player) - on wave end
```

---

## System 3: Signature Weapons (P1)

### Overview
Weapons accumulate "Soul Imprint" based on player actions, unlocking unique traits and evolving names.

### Trait Unlock Thresholds

| Stat | Threshold | Trait |
|------|-----------|-------|
| Headshots | 100 | Executioner (+15% headshot damage) |
| Boss Kills | 50 | Tyrant Slayer (+30% vs bosses) |
| SSS Waves | 10 | Stylish (+20% style gain) |
| Total Kills | 500 | Bloodthirsty (+0.5% lifesteal) |
| Perfect Resonances | 10 | Harmonic (+50% resonance damage) |
| Critical Hits | 200 | Precision (+10% crit chance) |
| Combo 50+ | 25 | Relentless (-20% combo decay) |
| No-Hit Waves | 10 | Guardian (+5% damage reduction) |

### Name Evolution
```
Stage 1 (0 traits):     "Diamond Sword"
Stage 2 (1 trait):      "[Player]'s Diamond Sword"
Stage 3 (3 traits):     "[Player]'s [Primary Trait] Blade"
Stage 4 (5 traits):     "[Unique Name], the [Title]"

Example progression:
"Diamond Sword" → "Erik's Diamond Sword" → "Erik's Executioner Blade" → "Voidrender, the SSS Blade"
```

### Technical Design

```
SoulImprint (stored in item capability)
├── ownerId: UUID
├── ownerName: String
├── stats: Map<ImprintStat, Integer>
├── unlockedTraits: Set<WeaponTrait>
├── evolutionStage: int
├── customName: Component (nullable)
└── recordAction(stat, amount)

WeaponTrait
├── id: ResourceLocation
├── displayName: Component
├── requiredStat: ImprintStat
├── threshold: int
├── effect: AttributeModifier or DamageModifier
└── apply(ItemStack, LivingEntity)
```

---

## System 4: Nemesis Evolution (P2)

### Overview
Bosses remember how players defeated them and adapt their abilities and behavior.

### Tracked Behaviors
- Preferred attack range (melee ratio vs ranged ratio)
- Dodge direction patterns (left/right/back frequency)
- Most used weapon types
- Average time-to-kill per phase
- Damage source distribution

### Adaptation Examples

| Player Behavior | Boss Adaptation |
|-----------------|-----------------|
| 70%+ ranged attacks | Projectile Deflection shield (30% chance) |
| Always dodges left | Leads attacks left, gains Sweeping Blade |
| Fast phase kills (<30s) | Starts with phase 1 pre-activated |
| Uses same weapon type | Gains resistance to that damage type |
| High headshot ratio | Wears protective helmet (reduces head hitbox) |

### Visual Scarring
Each defeat leaves a visible scar on the boss model:
- 1 defeat: Small scar
- 3 defeats: Multiple scars, slightly different color
- 5+ defeats: Battle-worn appearance, glowing eyes

---

## System 5: The Tide (P2)

### Overview
A server-wide threat level that rises with player failures and falls with successes, creating community events.

### Tide Accumulation
```
Player death:           +1 Tide
Failed quest (<wave 5): +5 Tide
Quest completed:        -3 Tide
SSS wave clear:         -10 Tide
Boss killed:            -5 Tide
Resonance triggered:    -1 Tide
```

### Tide Levels

| Level | Threshold | Effect |
|-------|-----------|--------|
| CALM | 0-100 | Normal gameplay |
| RISING | 100-300 | All mobs +10% stats |
| HIGH | 300-500 | Random curse mutators appear |
| STORM | 500-800 | Boss every 3 waves |
| APOCALYPSE | 800-1000 | Tide Boss event triggers |

### Tide Boss Event
When Tide reaches 1000:
1. All active quests receive "The Harbinger" invasion
2. Shared health pool across all instances
3. Collective damage from all parties
4. Massive rewards on defeat
5. Tide resets to 0

---

## Implementation Order

1. **Week 1**: Resonance Chain (P0)
   - Core system + damage integration
   - Network sync + HUD
   - Sound/visual feedback

2. **Week 2**: Blood Contracts (P1)
   - Contract definitions
   - Selection UI
   - Effect application

3. **Week 3**: Signature Weapons (P1)
   - Soul Imprint capability
   - Trait unlocks
   - Name evolution

4. **Week 4**: Polish & Integration
   - Telemetry for all systems
   - Balance tuning
   - Bug fixes

5. **Future**: Nemesis & Tide (P2)
   - More complex systems
   - Require stable base

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Resonance triggers per party quest | 10+ |
| Contracts signed per wave | 0.5 average |
| Weapons with 3+ traits | 30% of active players |
| Player retention (return next day) | +20% |
| Average session length | +15% |
| Streaming mentions | Trackable engagement |
