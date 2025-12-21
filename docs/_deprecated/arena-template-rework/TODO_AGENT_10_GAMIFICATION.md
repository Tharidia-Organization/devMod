# TODO Agent 10 - Gamification & Balance (DD 51-56)

> DEPRECATED: task list archived; see `docs/arena-template-rework/TODO_AGENT_10_COMPLETE.md`.


## Parallel Agent Coordination
- **Agent ID**: 10
- **Role**: Perks, Badges, Rewards, Challenges, Leaderboard
- **Dependencies**: Agent 05 (Observability) for DuckDB, Agent 06 (Identity) for player tracking
- **Outputs consumed by**: Agent 11 (Telemetry)
- **Shared resources**: `LeaderboardService.java`, `ChallengeGenerator.java`

## Design Decisions Reference
- DD51: Perk Suggestions Bias - shuffle SUGGESTED, A/B test 10%, weekly winrate
- DD52: Badge Template Tracking - usage table source of truth, version-agnostic
- DD53: Reward Multipliers - weight*0.05+1.0, bounds [0.5, 2.0], anti-exploit
- DD54: Currency Source Enum - ~15 valori, sourceId separato
- DD55: Challenge Generation - 5 availability checks, fallback generica
- DD56: Leaderboard Batch - calcolo 03:00 daily, Redis cache, O(1) read

## Tasks

### Perk Suggestions
- [ ] Implementare `PerkSuggestionEngine` con shuffle SUGGESTED
- [ ] Implementare A/B test 10% per perk suggestions
- [ ] Implementare weekly winrate analysis query (DuckDB)

### Badge Tracking
- [ ] Creare tabella `badge_usage` per tracking version-agnostic
- [ ] Implementare `BadgeUsage` record e query
- [ ] Implementare migration script badge_awards → badge_usage

### Rewards
- [ ] Implementare `RewardMultiplier` record con bounds [0.5, 2.0]
- [ ] Implementare `RewardAntiExploit` (rate limit 20/hour, speed check)

### Currency
- [ ] Implementare `CurrencySource` enum (16 valori)
- [ ] Implementare `CurrencyGrant` record con sourceId validation

### Challenges
- [ ] Implementare `ChallengeGenerator` con 5 availability checks
- [ ] Implementare `AvailabilityResult` sealed interface
- [ ] Implementare fallback challenge per ogni ChallengeType

### Leaderboard
- [ ] Implementare `LeaderboardService` con scheduled calculation
- [ ] Configurare cron job 03:00 daily per leaderboard
- [ ] Implementare Redis cache per leaderboard (TTL 25h)
- [ ] Implementare `LeaderboardType` enum con query SQL
- [ ] Implementare player rank lookup O(1) via secondary index

### Files to Create/Modify
- `src/main/java/com/devmod/arena/gamification/PerkSuggestionEngine.java`
- `src/main/java/com/devmod/arena/gamification/BadgeUsage.java`
- `src/main/java/com/devmod/arena/rewards/RewardMultiplier.java`
- `src/main/java/com/devmod/arena/rewards/RewardAntiExploit.java`
- `src/main/java/com/devmod/arena/currency/CurrencySource.java`
- `src/main/java/com/devmod/arena/challenge/ChallengeGenerator.java`
- `src/main/java/com/devmod/arena/leaderboard/LeaderboardService.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test PerkSuggestionEngine shuffle (no position bias)
- [ ] Unit test PerkSuggestionEngine A/B test deterministic
- [ ] Unit test PerkSuggestionEngine A/B test ratio (10%)
- [ ] Unit test BadgeUsage count query (version-agnostic)
- [ ] Unit test BadgeUsage migration script
- [ ] Unit test RewardMultiplier fromWeight calculation
- [ ] Unit test RewardMultiplier bounds clamping [0.5, 2.0]
- [ ] Unit test RewardAntiExploit rate limit (21st reward blocked)
- [ ] Unit test RewardAntiExploit speed check (telemetry emitted)
- [ ] Unit test CurrencySource enum cardinality (16 values)
- [ ] Unit test CurrencyGrant sourceId validation (max 64 chars)
- [ ] Unit test ChallengeGenerator level check
- [ ] Unit test ChallengeGenerator prerequisite check
- [ ] Unit test ChallengeGenerator cooldown check
- [ ] Unit test ChallengeGenerator template availability check
- [ ] Unit test ChallengeGenerator time window check
- [ ] Unit test ChallengeGenerator fallback (no available → generic)
- [ ] Unit test AvailabilityResult sealed interface
- [ ] Unit test LeaderboardService scheduled calculation
- [ ] Unit test LeaderboardService Redis cache TTL (25h)
- [ ] Unit test LeaderboardService cache miss → empty list
- [ ] Unit test LeaderboardService pagination
- [ ] Unit test LeaderboardService player rank lookup O(1)
- [ ] Unit test LeaderboardType query validation (all 3 types)
- [ ] Integration test leaderboard end-to-end
- [ ] Integration test challenge generation pipeline

### Completion Signal
When done, create file: `TODO_AGENT_10_COMPLETE.md` with summary of changes.
