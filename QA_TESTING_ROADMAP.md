# DevMod QA Testing System - Roadmap Completa v3.0

## Filosofia: Testing come Gioco con Auto-Rilevamento

> **"Il miglior test è quello che si completa DA SOLO mentre giochi"**

I tester sono giocatori. Il sistema deve:
1. **Rilevare automaticamente** quando un test viene completato
2. **Notificare immediatamente** con feedback visivo/sonoro
3. **Premiare** con XP, achievement, titoli
4. **Non richiedere** apertura manuale della schermata QA

---

## ARCHITETTURA: Event-Driven Auto-Detection

### Il Problema del Sistema Attuale
Il `Supplier<Boolean> autoValidator` attuale è **polling-based**:
- Controlla ogni secondo se la condizione è vera
- Non può tracciare eventi passati (es. "hai ucciso 10 zombie?")
- Non può contare progressi incrementali

### La Soluzione: Event Tracker + Progress Counter

```
┌─────────────────────────────────────────────────────────────────┐
│                     GAME EVENTS                                 │
│  LivingDeathEvent, DamageEvent, ExplosionEvent, PotionEvent... │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   QAEventTracker                                │
│  - Intercetta TUTTI gli eventi rilevanti                        │
│  - Aggiorna contatori in TesterProgress                         │
│  - Verifica condizioni di completamento test                    │
│  - Triggera notifiche quando test completato                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   TesterProgress                                │
│  - zombiesKilled: 47                                            │
│  - headshotsTotal: 23                                           │
│  - explosionsTested: 8                                          │
│  - potionsUsed: {instant_damage: 3, poison: 5, ...}             │
│  - enchantmentsTested: {sharpness: true, smite: true, ...}      │
│  - bodyPartsHit: {HEAD: 45, BODY: 120, ARMS: 30, LEGS: 25}      │
│  - etc...                                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   TestCase (Enhanced)                           │
│  - progressChecker: Function<TesterProgress, Float>             │
│  - Returns 0.0 to 1.0 (progress percentage)                     │
│  - When reaches 1.0 → AUTO-COMPLETE + Notification              │
└─────────────────────────────────────────────────────────────────┘
```

---

## FASE 0: Strutture Dati Core

### 0.1 TesterProgress - Tracciamento Statistiche
**File:** `src/main/java/com/frenkvs/devmod/testing/TesterProgress.java`

```java
public class TesterProgress {
    // === COMBAT STATS ===
    private int totalMobsKilled = 0;
    private Map<EntityType<?>, Integer> killsByType = new HashMap<>();
    private int headshotsTotal = 0;
    private int headshotsConsecutive = 0;
    private int headshotsBestStreak = 0;
    private int criticalHits = 0;
    private int sweepAttacks = 0;
    private float totalDamageDealt = 0f;
    private float maxSingleHitDamage = 0f;

    // === BODY PARTS ===
    private Map<HitHelper.BodyPart, Integer> bodyPartsHit = new EnumMap<>(HitHelper.BodyPart.class);
    private boolean hitAllBodyPartsInOneFight = false;
    private Set<HitHelper.BodyPart> currentFightParts = EnumSet.noneOf(HitHelper.BodyPart.class);

    // === WEAPONS ===
    private Map<Item, Integer> killsPerWeaponType = new HashMap<>();
    private Set<Item> swordTypesUsed = new HashSet<>();
    private int maceSmashAttacks = 0;
    private float highestMaceSmashHeight = 0f;
    private int shieldBlocks = 0;
    private int arrowKills = 0;
    private int crossbowKills = 0;
    private int tridentKills = 0;
    private float longestArrowShot = 0f;

    // === EXPLOSIONS ===
    private int explosionsTotal = 0;
    private int tntExploded = 0;
    private int creepersExploded = 0;
    private int chargedCreepersExploded = 0;
    private int explosionsInTimeWindow = 0; // Per chain reaction
    private long lastExplosionTime = 0;
    private boolean testedBedExplosion = false;
    private boolean testedRespawnAnchorExplosion = false;
    private boolean testedEndCrystal = false;
    private boolean testedWitherSpawn = false;

    // === POTIONS ===
    private Set<MobEffect> potionEffectsExperienced = new HashSet<>();
    private int splashPotionsThrown = 0;
    private int lingeringPotionsCreated = 0;
    private int maxSimultaneousEffects = 0;
    private boolean testedStrengthDamageBonus = false;
    private boolean testedWeaknessDamageReduction = false;
    private boolean testedResistanceDamageReduction = false;
    private boolean survivedLavaWithFireResistance = false;

    // === ENCHANTMENTS ===
    private Set<Enchantment> enchantmentsTested = new HashSet<>();
    private boolean testedMaxLevelEnchant = false;
    private boolean testedSharpnessBonus = false;
    private boolean testedSmiteVsUndead = false;
    private boolean testedBaneVsArthropod = false;
    private boolean testedFireAspect = false;
    private boolean testedChannelingLightning = false;
    private boolean testedThornsReflect = false;

    // === ENVIRONMENTAL DAMAGE ===
    private float fallDamageTaken = 0f;
    private float maxFallHeight = 0f;
    private float fireDamageTaken = 0f;
    private float lavaDamageTaken = 0f;
    private float cactusDamageTaken = 0f;
    private float berryBushDamageTaken = 0f;
    private boolean takenDrowningDamage = false;
    private boolean takenSuffocationDamage = false;
    private boolean takenVoidDamage = false;
    private boolean struckByLightning = false;
    private boolean hitByAnvil = false;
    private float timeInLava = 0f; // seconds

    // === MOB-SPECIFIC ===
    private Set<EntityType<?>> mobTypesKilled = new HashSet<>();
    private int endermanEvasionsTriggered = 0;
    private boolean testedEndermanWaterDamage = false;
    private boolean testedBlazeFireImmunity = false;
    private boolean testedWitchPotionResistance = false;
    private boolean foughtWither = false;
    private boolean foughtDragon = false;
    private boolean foughtWarden = false;
    private boolean foughtElderGuardian = false;

    // === OVERLAY USAGE ===
    private Set<String> overlaysToggled = new HashSet<>();
    private long overlayUsageTime = 0; // ms

    // === SESSION STATS ===
    private long sessionStartTime = System.currentTimeMillis();
    private long totalPlayTime = 0;
    private int testsCompletedThisSession = 0;

    // === INCREMENT METHODS ===
    public void onMobKill(EntityType<?> type, Item weapon, HitHelper.BodyPart lastHitPart) {...}
    public void onDamageDealt(float amount, boolean isCrit, boolean isHeadshot, HitHelper.BodyPart part) {...}
    public void onExplosion(ExplosionType type) {...}
    public void onPotionEffect(MobEffect effect) {...}
    public void onEnvironmentalDamage(DamageType type, float amount) {...}
    public void onOverlayToggle(String overlayId) {...}

    // === SAVE/LOAD ===
    public JsonObject toJson() {...}
    public static TesterProgress fromJson(JsonObject json) {...}
}
```

### 0.2 TestCase Enhanced - Progress-Based Completion
**File:** `TestCase.java` - Modifiche

```java
public class TestCase {
    // ... existing fields ...

    // NEW: Progress-based completion
    private final Function<TesterProgress, Float> progressChecker;
    private final int xpReward;
    private final String achievementTrigger; // Achievement to unlock when completed

    // For multi-step tests
    private final int targetCount; // e.g., "Kill 10 zombies" → targetCount = 10

    public TestCase(String id, String category, String name, String description,
                    String instructions, TestPriority priority, int xpReward,
                    Function<TesterProgress, Float> progressChecker) {
        this(id, category, name, description, instructions, priority, xpReward, progressChecker, null, 1);
    }

    public TestCase(String id, String category, String name, String description,
                    String instructions, TestPriority priority, int xpReward,
                    Function<TesterProgress, Float> progressChecker,
                    String achievementTrigger, int targetCount) {
        // ...
        this.progressChecker = progressChecker;
        this.xpReward = xpReward;
        this.achievementTrigger = achievementTrigger;
        this.targetCount = targetCount;
    }

    /**
     * Get current progress (0.0 to 1.0)
     */
    public float getProgress(TesterProgress progress) {
        if (progressChecker == null) return 0f;
        if (status == TestStatus.PASSED) return 1f;
        return Math.min(1f, progressChecker.apply(progress));
    }

    /**
     * Check if test should auto-complete based on progress
     */
    public boolean checkAutoComplete(TesterProgress progress) {
        if (status == TestStatus.PASSED) return false;
        float currentProgress = getProgress(progress);
        if (currentProgress >= 1f) {
            markPassed("Auto-completed");
            return true;
        }
        return false;
    }

    public int getXpReward() { return xpReward; }
    public String getAchievementTrigger() { return achievementTrigger; }
    public int getTargetCount() { return targetCount; }
}
```

---

## FASE 1: Event Tracker System

### 1.1 QAEventTracker - Il Cuore del Sistema
**File:** `src/main/java/com/frenkvs/devmod/testing/QAEventTracker.java`

```java
@EventBusSubscriber(modid = "devmod")
public class QAEventTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(QAEventTracker.class);

    // =========================================
    // COMBAT EVENTS
    // =========================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        LivingEntity victim = event.getEntity();
        EntityType<?> type = victim.getType();
        ItemStack weapon = player.getMainHandItem();

        // Get last hit body part from our damage tracking
        HitHelper.BodyPart lastPart = getLastHitPart(victim);

        // Update progress
        TesterProgress progress = TestingSession.INSTANCE.getProgress();
        progress.onMobKill(type, weapon.getItem(), lastPart);

        // Check for test completions
        checkTestCompletions(player, "kill");

        // Check achievements
        checkAchievements(player, "kill", type);

        LOGGER.debug("Mob killed: {} with {}, part={}", type, weapon.getItem(), lastPart);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        float damage = event.getNewDamage();
        LivingEntity victim = event.getEntity();

        // Get body part from ImpactData if available
        ImpactData impact = ImpactData.get();
        HitHelper.BodyPart part = impact != null ? impact.bodyPart() : HitHelper.BodyPart.BODY;
        boolean isHeadshot = part == HitHelper.BodyPart.HEAD;
        boolean isCrit = player.fallDistance > 0 && !player.onGround();

        TesterProgress progress = TestingSession.INSTANCE.getProgress();
        progress.onDamageDealt(damage, isCrit, isHeadshot, part);

        checkTestCompletions(player, "damage");
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageSource source = event.getSource();
        float damage = event.getNewDamage();

        TesterProgress progress = TestingSession.INSTANCE.getProgress();

        // Categorize damage type
        if (source.is(DamageTypes.FALL)) {
            progress.onEnvironmentalDamage(DamageType.FALL, damage);
        } else if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)) {
            progress.onEnvironmentalDamage(DamageType.FIRE, damage);
        } else if (source.is(DamageTypes.LAVA)) {
            progress.onEnvironmentalDamage(DamageType.LAVA, damage);
        } else if (source.is(DamageTypes.CACTUS)) {
            progress.onEnvironmentalDamage(DamageType.CACTUS, damage);
        } else if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            progress.onEnvironmentalDamage(DamageType.BERRY_BUSH, damage);
        } else if (source.is(DamageTypes.DROWN)) {
            progress.onEnvironmentalDamage(DamageType.DROWN, damage);
        } else if (source.is(DamageTypes.IN_WALL)) {
            progress.onEnvironmentalDamage(DamageType.SUFFOCATION, damage);
        } else if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            progress.onEnvironmentalDamage(DamageType.VOID, damage);
        } else if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            progress.onEnvironmentalDamage(DamageType.LIGHTNING, damage);
        } else if (source.is(DamageTypes.FALLING_ANVIL)) {
            progress.onEnvironmentalDamage(DamageType.ANVIL, damage);
        } else if (source.is(DamageTypes.EXPLOSION)) {
            progress.onEnvironmentalDamage(DamageType.EXPLOSION, damage);
        }

        checkTestCompletions(player, "hurt");
    }

    // =========================================
    // EXPLOSION EVENTS
    // =========================================

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        Entity source = explosion.getDirectSourceEntity();

        TesterProgress progress = TestingSession.INSTANCE.getProgress();

        if (source instanceof PrimedTnt) {
            progress.onExplosion(ExplosionType.TNT);
        } else if (source instanceof Creeper creeper) {
            if (creeper.isPowered()) {
                progress.onExplosion(ExplosionType.CHARGED_CREEPER);
            } else {
                progress.onExplosion(ExplosionType.CREEPER);
            }
        } else if (source instanceof Fireball) {
            progress.onExplosion(ExplosionType.GHAST_FIREBALL);
        } else if (source instanceof EndCrystal) {
            progress.onExplosion(ExplosionType.END_CRYSTAL);
        } else if (source instanceof WitherSkull) {
            progress.onExplosion(ExplosionType.WITHER);
        } else if (source instanceof FireworkRocketEntity) {
            progress.onExplosion(ExplosionType.FIREWORK);
        }

        // Check chain reaction (multiple explosions in short time)
        long now = System.currentTimeMillis();
        if (now - progress.getLastExplosionTime() < 2000) {
            progress.incrementExplosionsInWindow();
        } else {
            progress.resetExplosionsInWindow();
        }
        progress.setLastExplosionTime(now);

        // Find nearest player to check tests
        Player nearestPlayer = findNearestPlayer(event.getLevel(), explosion.center());
        if (nearestPlayer != null) {
            checkTestCompletions(nearestPlayer, "explosion");
        }
    }

    // =========================================
    // POTION EVENTS
    // =========================================

    @SubscribeEvent
    public static void onPotionAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player)) return;

        MobEffectInstance effect = event.getEffectInstance();
        TesterProgress progress = TestingSession.INSTANCE.getProgress();
        progress.onPotionEffect(effect.getEffect().value());

        // Count simultaneous effects
        int activeEffects = player.getActiveEffects().size();
        progress.updateMaxSimultaneousEffects(activeEffects);

        checkTestCompletions(player, "potion");
    }

    @SubscribeEvent
    public static void onPotionThrow(ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (!(potion.getOwner() instanceof Player)) return;

        TesterProgress progress = TestingSession.INSTANCE.getProgress();

        if (potion.getItem().is(Items.SPLASH_POTION)) {
            progress.incrementSplashPotionsThrown();
        } else if (potion.getItem().is(Items.LINGERING_POTION)) {
            progress.incrementLingeringPotionsCreated();
        }

        checkTestCompletions((Player) potion.getOwner(), "potion");
    }

    // =========================================
    // ENCHANTMENT DETECTION
    // =========================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamageForEnchantment(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getMainHandItem();
        LivingEntity target = event.getEntity();
        TesterProgress progress = TestingSession.INSTANCE.getProgress();

        // Check each enchantment on weapon
        for (var entry : weapon.getEnchantments().entrySet()) {
            Enchantment ench = entry.getKey().value();
            progress.markEnchantmentTested(ench);

            // Specific checks
            if (ench == Enchantments.SHARPNESS && entry.getValue() >= 5) {
                progress.setTestedMaxLevelEnchant(true);
            }
            if (ench == Enchantments.SMITE && target.getType().is(EntityTypeTags.UNDEAD)) {
                progress.setTestedSmiteVsUndead(true);
            }
            if (ench == Enchantments.BANE_OF_ARTHROPODS && target.getType().is(EntityTypeTags.ARTHROPOD)) {
                progress.setTestedBaneVsArthropod(true);
            }
            if (ench == Enchantments.FIRE_ASPECT) {
                progress.setTestedFireAspect(true);
            }
        }

        checkTestCompletions(player, "enchantment");
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof Player player) {
            TesterProgress progress = TestingSession.INSTANCE.getProgress();
            progress.setStruckByLightning(true);

            // Check if caused by Channeling
            if (event.getLightning().getCause() instanceof Player) {
                progress.setTestedChannelingLightning(true);
            }

            checkTestCompletions(player, "lightning");
        }
    }

    // =========================================
    // OVERLAY TOGGLE DETECTION
    // =========================================

    public static void onOverlayToggled(String overlayId, boolean enabled) {
        if (!enabled) return;

        TesterProgress progress = TestingSession.INSTANCE.getProgress();
        progress.onOverlayToggle(overlayId);

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            checkTestCompletions(player, "overlay");
        }
    }

    // =========================================
    // TEST COMPLETION CHECK
    // =========================================

    private static void checkTestCompletions(Player player, String triggerType) {
        TestingSession session = TestingSession.INSTANCE;
        if (!session.isSessionActive()) return;

        TesterProgress progress = session.getProgress();
        List<TestCase> newlyCompleted = new ArrayList<>();

        for (TestCase test : session.getAllTests()) {
            if (test.getStatus() == TestCase.TestStatus.PASSED) continue;
            if (test.getStatus() == TestCase.TestStatus.SKIPPED) continue;

            if (test.checkAutoComplete(progress)) {
                newlyCompleted.add(test);
            }
        }

        // Process completions
        for (TestCase test : newlyCompleted) {
            onTestCompleted(player, test);
        }
    }

    private static void onTestCompleted(Player player, TestCase test) {
        TestingSession session = TestingSession.INSTANCE;
        TesterProfile profile = session.getProfile();

        // Award XP
        int xp = test.getXpReward();
        int streakBonus = calculateStreakBonus(profile.getCurrentStreak());
        int totalXP = xp + (xp * streakBonus / 100);
        profile.addXP(totalXP, "Test: " + test.getName());

        // Update streak
        profile.incrementStreak();

        // Check achievement trigger
        if (test.getAchievementTrigger() != null) {
            QAAchievementManager.checkAchievement(test.getAchievementTrigger(), profile);
        }

        // Show notification
        QANotificationSystem.INSTANCE.showTestCompleted(test, totalXP);

        // Play sound
        QASoundManager.playTestComplete();

        // Save progress
        session.markDirty();

        LOGGER.info("Test auto-completed: {} (+{} XP)", test.getName(), totalXP);
    }

    private static int calculateStreakBonus(int streak) {
        if (streak >= 10) return 200;
        if (streak >= 5) return 100;
        if (streak >= 3) return 50;
        return 0;
    }
}
```

---

## FASE 2: Test Cases con Auto-Detection

### 2.1 Definizione Test con Progress Checker

**File:** `TestingSession.java` - Nuovo metodo `initializeTestCases()`

```java
private void initializeTestCases() {

    // ============================================
    // COMBATTIMENTO - MELEE
    // ============================================

    addTest(new TestCase(
        "combat_sword_basic",
        "Combattimento",
        "Spada Base",
        "Uccidi 3 zombie con una spada di legno",
        "Equipa una spada di legno e uccidi 3 zombie",
        TestPriority.MEDIUM,
        10, // XP
        progress -> {
            int woodenSwordKills = progress.getKillsWithItem(Items.WOODEN_SWORD);
            return woodenSwordKills / 3f;
        },
        null, // no achievement
        3 // target count for UI
    ));

    addTest(new TestCase(
        "combat_sword_types",
        "Combattimento",
        "Arsenale Spade",
        "Uccidi almeno 1 mob con ogni tipo di spada (legno → netherite)",
        "Usa tutte le spade: legno, pietra, ferro, oro, diamante, netherite",
        TestPriority.HIGH,
        25,
        progress -> {
            Set<Item> swords = Set.of(
                Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
            );
            int used = 0;
            for (Item sword : swords) {
                if (progress.getKillsWithItem(sword) > 0) used++;
            }
            return used / 6f;
        },
        "variety_killer",
        6
    ));

    addTest(new TestCase(
        "body_head_10",
        "Combattimento",
        "Cecchino",
        "Esegui 10 headshot su qualsiasi mob",
        "Mira in alto per colpire la testa dei mob",
        TestPriority.HIGH,
        25,
        progress -> progress.getHeadshotsTotal() / 10f,
        "headshot_master",
        10
    ));

    addTest(new TestCase(
        "body_all_parts",
        "Combattimento",
        "Anatomia Completa",
        "Colpisci HEAD, BODY, ARMS, LEGS in un singolo combattimento",
        "In un solo combattimento con un mob, colpisci tutte e 4 le parti del corpo",
        TestPriority.HIGH,
        30,
        progress -> progress.hasHitAllBodyPartsInOneFight() ? 1f : 0f,
        "body_expert",
        1
    ));

    addTest(new TestCase(
        "combat_crit_chain",
        "Combattimento",
        "Combo Critica",
        "Esegui 5 colpi critici consecutivi",
        "Salta e colpisci mentre cadi per fare critici",
        TestPriority.MEDIUM,
        20,
        progress -> progress.getCriticalHitsConsecutive() / 5f,
        null,
        5
    ));

    addTest(new TestCase(
        "mace_smash_5",
        "Combattimento",
        "Smash Bros",
        "Esegui 5 mace smash attacks cadendo",
        "Salta da almeno 3 blocchi con la mace equipaggiata",
        TestPriority.HIGH,
        30,
        progress -> progress.getMaceSmashAttacks() / 5f,
        null,
        5
    ));

    addTest(new TestCase(
        "mace_smash_max",
        "Combattimento",
        "Meteor Strike",
        "Esegui un mace smash da 20+ blocchi di altezza",
        "Costruisci una torre alta e salta con la mace",
        TestPriority.HIGH,
        40,
        progress -> progress.getHighestMaceSmashHeight() >= 20f ? 1f : progress.getHighestMaceSmashHeight() / 20f,
        "overkill",
        1
    ));

    addTest(new TestCase(
        "ranged_distance",
        "Combattimento",
        "Cecchino Estremo",
        "Colpisci un mob con freccia da 30+ blocchi",
        "Usa un arco potenziato per tiri a lunga distanza",
        TestPriority.MEDIUM,
        25,
        progress -> progress.getLongestArrowShot() >= 30f ? 1f : progress.getLongestArrowShot() / 30f,
        null,
        1
    ));

    // ============================================
    // ESPLOSIONI
    // ============================================

    addTest(new TestCase(
        "expl_tnt_single",
        "Esplosioni",
        "Prima Esplosione",
        "Fai esplodere 1 TNT",
        "Piazza un TNT, accendilo con acciarino o redstone",
        TestPriority.LOW,
        10,
        progress -> progress.getTntExploded() >= 1 ? 1f : 0f,
        "boom_beginner",
        1
    ));

    addTest(new TestCase(
        "expl_tnt_chain",
        "Esplosioni",
        "Reazione a Catena",
        "5 TNT che si innescano a vicenda in 10 secondi",
        "Piazza TNT vicini e innescane uno",
        TestPriority.MEDIUM,
        25,
        progress -> progress.getMaxExplosionsInWindow() >= 5 ? 1f : progress.getMaxExplosionsInWindow() / 5f,
        "chain_reaction",
        5
    ));

    addTest(new TestCase(
        "expl_creeper",
        "Esplosioni",
        "Creeper Test",
        "Fai esplodere 5 Creeper vicino a te",
        "Attira i Creeper e lasciali esplodere (con armatura!)",
        TestPriority.MEDIUM,
        20,
        progress -> progress.getCreepersExploded() / 5f,
        null,
        5
    ));

    addTest(new TestCase(
        "expl_charged_creeper",
        "Esplosioni",
        "Charged Creeper",
        "Fai esplodere un Charged Creeper",
        "Usa Channeling durante un temporale, o comando /summon",
        TestPriority.HIGH,
        35,
        progress -> progress.getChargedCreepersExploded() >= 1 ? 1f : 0f,
        null,
        1
    ));

    addTest(new TestCase(
        "expl_wither",
        "Esplosioni",
        "Wither Spawn",
        "Testa l'esplosione di spawn del Wither",
        "Costruisci la struttura del Wither con 4 soul sand + 3 teste",
        TestPriority.CRITICAL,
        40,
        progress -> progress.hasTestedWitherSpawn() ? 1f : 0f,
        "boss_tester",
        1
    ));

    // ============================================
    // POZIONI
    // ============================================

    addTest(new TestCase(
        "pot_damage_instant",
        "Pozioni",
        "Danno Istantaneo",
        "Subisci danno da Instant Damage",
        "Lancia una pozione di danno istantaneo a te stesso",
        TestPriority.MEDIUM,
        15,
        progress -> progress.hasExperiencedEffect(MobEffects.HARM) ? 1f : 0f,
        "alchemist_novice",
        1
    ));

    addTest(new TestCase(
        "pot_stacking",
        "Pozioni",
        "Effetti Multipli",
        "Avere 5+ effetti pozione attivi contemporaneamente",
        "Bevi/lancia multiple pozioni velocemente",
        TestPriority.HIGH,
        30,
        progress -> progress.getMaxSimultaneousEffects() >= 5 ? 1f : progress.getMaxSimultaneousEffects() / 5f,
        "effect_stacker",
        5
    ));

    addTest(new TestCase(
        "pot_fire_resistance",
        "Pozioni",
        "Ignifugo",
        "Sopravvivi 5 secondi in lava con Fire Resistance",
        "Bevi pozione di Fire Resistance e entra nella lava",
        TestPriority.HIGH,
        20,
        progress -> progress.hasSurvivedLavaWithFireResistance() ? 1f : 0f,
        null,
        1
    ));

    // ============================================
    // ENCHANTMENT
    // ============================================

    addTest(new TestCase(
        "ench_sharpness",
        "Enchantment",
        "Affilatura",
        "Testa Sharpness su un'arma",
        "Usa un'arma con Sharpness per attaccare",
        TestPriority.MEDIUM,
        20,
        progress -> progress.hasTestedEnchantment(Enchantments.SHARPNESS) ? 1f : 0f,
        null,
        1
    ));

    addTest(new TestCase(
        "ench_smite",
        "Enchantment",
        "Castigo vs Undead",
        "Usa Smite contro un mob non-morto",
        "Attacca zombie, scheletri, ecc. con arma Smite",
        TestPriority.MEDIUM,
        20,
        progress -> progress.hasTestedSmiteVsUndead() ? 1f : 0f,
        null,
        1
    ));

    addTest(new TestCase(
        "ench_channeling",
        "Enchantment",
        "Canalizzazione",
        "Evoca un fulmine con Channeling",
        "Usa tridente con Channeling durante un temporale",
        TestPriority.HIGH,
        30,
        progress -> progress.hasTestedChannelingLightning() ? 1f : 0f,
        "lightning_rod",
        1
    ));

    addTest(new TestCase(
        "ench_thorns",
        "Enchantment",
        "Spine",
        "Infliggi danno riflesso con Thorns",
        "Indossa armatura con Thorns e fatti colpire",
        TestPriority.MEDIUM,
        25,
        progress -> progress.hasTestedThornsReflect() ? 1f : 0f,
        null,
        1
    ));

    addTest(new TestCase(
        "ench_all",
        "Enchantment",
        "Arcimago",
        "Testa tutti gli enchantment offensivi",
        "Usa armi con Sharpness, Smite, Bane, Fire Aspect, Knockback, Sweeping",
        TestPriority.CRITICAL,
        50,
        progress -> {
            Set<Enchantment> required = Set.of(
                Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK, Enchantments.SWEEPING_EDGE
            );
            int tested = 0;
            for (Enchantment e : required) {
                if (progress.hasTestedEnchantment(e)) tested++;
            }
            return tested / (float) required.size();
        },
        "enchanter_pro",
        6
    ));

    // ============================================
    // DANNO AMBIENTALE
    // ============================================

    addTest(new TestCase(
        "env_fall_10",
        "Ambiente",
        "Caduta Media",
        "Cadi da 10+ blocchi",
        "Salta da una torre di 10 blocchi",
        TestPriority.LOW,
        15,
        progress -> progress.getMaxFallHeight() >= 10f ? 1f : progress.getMaxFallHeight() / 10f,
        "fall_tester",
        1
    ));

    addTest(new TestCase(
        "env_cactus",
        "Ambiente",
        "Cactus Hugger",
        "Subisci 20 danni totali da cactus",
        "Abbraccia ripetutamente un cactus",
        TestPriority.LOW,
        20,
        progress -> progress.getCactusDamageTaken() / 20f,
        "cactus_hugger",
        20
    ));

    addTest(new TestCase(
        "env_void",
        "Ambiente",
        "Esploratore del Vuoto",
        "Subisci danno dal void",
        "Vai nell'End e cadi nel vuoto (con Totem!)",
        TestPriority.HIGH,
        25,
        progress -> progress.hasTakenVoidDamage() ? 1f : 0f,
        "void_explorer",
        1
    ));

    addTest(new TestCase(
        "env_lightning",
        "Ambiente",
        "Parafulmine",
        "Vieni colpito da un fulmine",
        "Aspetta un temporale o usa Channeling",
        TestPriority.HIGH,
        35,
        progress -> progress.wasStruckByLightning() ? 1f : 0f,
        "lightning_rod",
        1
    ));

    // ============================================
    // MOB SPECIFICI
    // ============================================

    addTest(new TestCase(
        "mob_enderman_evade",
        "Mob",
        "Evasione Enderman",
        "Fai evadere un Enderman 3 volte",
        "Attacca un Enderman e guardalo negli occhi",
        TestPriority.MEDIUM,
        30,
        progress -> progress.getEndermanEvasionsTriggered() / 3f,
        null,
        3
    ));

    addTest(new TestCase(
        "mob_wither_boss",
        "Mob",
        "Boss Wither",
        "Combatti e infliggi danno al Wither",
        "Evoca e combatti il Wither",
        TestPriority.CRITICAL,
        60,
        progress -> progress.hasFoughtWither() ? 1f : 0f,
        "boss_tester",
        1
    ));

    addTest(new TestCase(
        "mob_dragon",
        "Mob",
        "Ender Dragon",
        "Combatti l'Ender Dragon",
        "Vai nell'End e affronta il drago",
        TestPriority.CRITICAL,
        75,
        progress -> progress.hasFoughtDragon() ? 1f : 0f,
        "boss_tester",
        1
    ));

    addTest(new TestCase(
        "mob_variety",
        "Mob",
        "Collezionista di Taglie",
        "Uccidi 10 tipi diversi di mob",
        "Uccidi varietà: zombie, skeleton, spider, creeper, ecc.",
        TestPriority.HIGH,
        40,
        progress -> progress.getMobTypesKilled().size() / 10f,
        "variety_killer",
        10
    ));

    // ============================================
    // OVERLAY (questi usano il vecchio sistema Supplier per semplicità)
    // ============================================

    addTest(new TestCase(
        "ovl_debug_g",
        "Overlay",
        "Debug Overlay",
        "Attiva il Debug Overlay premendo G",
        "Premi G per vedere i marker 3D",
        TestPriority.MEDIUM,
        10,
        progress -> progress.hasToggledOverlay("debug") ? 1f : 0f,
        null,
        1
    ));

    addTest(new TestCase(
        "ovl_light_l",
        "Overlay",
        "Light Level",
        "Attiva il Light Level Overlay premendo L",
        "Premi L per vedere i livelli di luce sui blocchi",
        TestPriority.MEDIUM,
        10,
        progress -> progress.hasToggledOverlay("light") ? 1f : 0f,
        null,
        1
    ));

    // ... altri overlay ...

    // ============================================
    // CACHE & PERFORMANCE
    // ============================================

    addTest(new TestCase(
        "cache_clear",
        "Cache",
        "Cache Clear",
        "Verifica che la cache si svuoti al logout",
        "Disconnetti e riconnetti, la cache deve essere vuota",
        TestPriority.HIGH,
        15,
        null, // Manual test
        null,
        1
    ));

    // Organize by category
    for (TestCase test : testCases) {
        categorizedTests.computeIfAbsent(test.getCategory(), k -> new ArrayList<>()).add(test);
    }
}
```

---

## FASE 3: UI con Progress Bar per Ogni Test

### 3.1 QATestingScreen - Visualizzazione Progresso

Nella UI, ogni test mostra una **progress bar** invece di solo lo stato:

```java
private void renderTestCard(GuiGraphics graphics, int x, int y, int width, TestCase test, boolean hovered, boolean selected) {
    // ... existing code ...

    // NUOVO: Progress bar per test non completati
    if (test.getStatus() != TestCase.TestStatus.PASSED) {
        TesterProgress progress = TestingSession.INSTANCE.getProgress();
        float testProgress = test.getProgress(progress);

        if (testProgress > 0) {
            // Draw progress bar
            int barX = x + 12;
            int barY = y + height - 12;
            int barWidth = width - 24;
            int barHeight = 4;

            // Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

            // Fill
            int fillWidth = (int)(barWidth * testProgress);
            int fillColor = testProgress >= 1f ? 0xFF55FF55 : 0xFFFFAA00;
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

            // Percentage text
            String percentText = String.format("%.0f%%", testProgress * 100);
            graphics.drawString(font, percentText, barX + barWidth + 5, barY - 2, UIConstants.Text.MUTED, false);

            // Target count if applicable
            if (test.getTargetCount() > 1) {
                int current = (int)(test.getTargetCount() * testProgress);
                String countText = current + "/" + test.getTargetCount();
                graphics.drawString(font, countText, x + width - 50, y + 5, UIConstants.Text.SECONDARY, false);
            }
        }
    }
}
```

---

## FASE 4: Notifiche Real-Time

### 4.1 Toast Notification quando Test Completato

```java
public class QANotificationSystem {
    public static final QANotificationSystem INSTANCE = new QANotificationSystem();

    private final Deque<Notification> queue = new ArrayDeque<>();
    private Notification current = null;
    private long displayStartTime = 0;
    private static final long DISPLAY_DURATION = 4000; // 4 seconds

    public void showTestCompleted(TestCase test, int xpEarned) {
        String title = "Test Completato!";
        String subtitle = test.getName();
        String xpText = "+" + xpEarned + " XP";

        queue.add(new Notification(
            NotificationType.TEST_PASSED,
            title,
            subtitle,
            xpText,
            test.getCategory()
        ));
    }

    public void showAchievementUnlocked(QAAchievement achievement) {
        queue.add(new Notification(
            NotificationType.ACHIEVEMENT,
            "Achievement Sbloccato!",
            achievement.getName(),
            achievement.getDescription(),
            null
        ));
    }

    public void showLevelUp(int newLevel, String title) {
        queue.add(new Notification(
            NotificationType.LEVEL_UP,
            "LEVEL UP!",
            "Livello " + newLevel,
            title,
            null
        ));
    }

    public void showStreak(int streak) {
        if (streak == 3 || streak == 5 || streak == 10 || streak == 25) {
            queue.add(new Notification(
                NotificationType.STREAK,
                "COMBO x" + streak + "!",
                "Bonus XP +" + (streak >= 10 ? "200" : streak >= 5 ? "100" : "50") + "%",
                null,
                null
            ));
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            displayStartTime = System.currentTimeMillis();
            QASoundManager.playNotificationSound(current.type);
        }

        if (current == null) return;

        long elapsed = System.currentTimeMillis() - displayStartTime;
        if (elapsed > DISPLAY_DURATION) {
            current = null;
            return;
        }

        // Animation
        float progress = elapsed / (float) DISPLAY_DURATION;
        float slideIn = Math.min(1f, progress * 5f); // Fast slide in
        float fadeOut = progress > 0.75f ? 1f - (progress - 0.75f) * 4f : 1f;

        int toastWidth = 250;
        int toastHeight = 60;
        int x = screenWidth - (int)(toastWidth * slideIn) - 10;
        int y = 50;

        // Background with type color
        int bgColor = switch (current.type) {
            case TEST_PASSED -> 0xCC22AA22;
            case ACHIEVEMENT -> 0xCCFFAA00;
            case LEVEL_UP -> 0xCC8800FF;
            case STREAK -> 0xCC0088FF;
            default -> 0xCC333333;
        };
        bgColor = applyAlpha(bgColor, fadeOut);

        graphics.fill(x, y, x + toastWidth, y + toastHeight, bgColor);
        graphics.renderOutline(x, y, toastWidth, toastHeight, 0xFFFFFFFF);

        // Icon
        // ... draw icon based on type ...

        // Text
        int textX = x + 50;
        graphics.drawString(Minecraft.getInstance().font, current.title, textX, y + 8, applyAlpha(0xFFFFFFFF, fadeOut), false);
        graphics.drawString(Minecraft.getInstance().font, current.subtitle, textX, y + 22, applyAlpha(0xFFCCCCCC, fadeOut), false);
        if (current.detail != null) {
            graphics.drawString(Minecraft.getInstance().font, current.detail, textX, y + 36, applyAlpha(0xFF88FF88, fadeOut), false);
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int)((color >> 24 & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    record Notification(NotificationType type, String title, String subtitle, String detail, String category) {}
    enum NotificationType { TEST_PASSED, TEST_FAILED, ACHIEVEMENT, LEVEL_UP, STREAK, CATEGORY_COMPLETE }
}
```

---

## FASE 5: Integrazione con KeyInputHandler

Per tracciare quando gli overlay vengono attivati:

```java
// In KeyInputHandler.java, dopo ogni toggle:

if (TOGGLE_DEBUG_OVERLAY_KEY.consumeClick()) {
    DebugRenderer.INSTANCE.toggle();
    // NEW: Track for QA
    QAEventTracker.onOverlayToggled("debug", DebugRenderer.INSTANCE.isEnabled());
    // ... rest of code
}

if (TOGGLE_LIGHT_OVERLAY_KEY.consumeClick()) {
    LightLevelOverlay.INSTANCE.toggle();
    // NEW: Track for QA
    QAEventTracker.onOverlayToggled("light", LightLevelOverlay.INSTANCE.isEnabled());
    // ... rest of code
}

// ... for each overlay ...
```

---

## RIEPILOGO: Come Funziona l'Auto-Detection

### Flusso Completo:

1. **Giocatore uccide uno zombie**
   ↓
2. **`LivingDeathEvent` viene triggerato**
   ↓
3. **`QAEventTracker.onLivingDeath()` intercetta l'evento**
   ↓
4. **`TesterProgress.onMobKill()` aggiorna i contatori**
   ```
   zombiesKilled: 46 → 47
   killsByType[ZOMBIE]: 46 → 47
   ```
   ↓
5. **`checkTestCompletions()` verifica tutti i test**
   ```java
   // Test "combat_zombie_10" ha progressChecker:
   progress -> progress.getKillsByType(ZOMBIE) / 10f
   // Ritorna 47/10 = 4.7 → clamped a 1.0 → TEST COMPLETATO!
   ```
   ↓
6. **`onTestCompleted()` viene chiamato**
   - Award XP (10 + streak bonus)
   - Increment streak
   - Check achievement triggers
   - Save progress
   ↓
7. **`QANotificationSystem.showTestCompleted()`**
   - Toast appare in alto a destra
   - Suono di completamento
   - Particelle (opzionale)
   ↓
8. **Il giocatore continua a giocare senza interruzioni!**

### Tipi di Auto-Detection:

| Tipo | Meccanismo | Esempio |
|------|------------|---------|
| **Counter** | `progress.getX() / target` | "Uccidi 10 zombie" |
| **Boolean** | `progress.hasX() ? 1f : 0f` | "Entra nel void" |
| **Threshold** | `value >= min ? 1f : value/min` | "Cadi da 20 blocchi" |
| **Set** | `set.size() / required` | "Usa 6 tipi di spada" |
| **Combo** | `consecutive >= target` | "5 headshot consecutivi" |
| **Time-window** | `countInWindow >= target` | "5 esplosioni in 10 sec" |

---

## CHECKLIST IMPLEMENTAZIONE ✅ COMPLETATA

### File Creati:
- [x] `TesterProgress.java` - Facade per statistiche (delega a 11 tracker specializzati)
- [x] `TesterProfile.java` - XP, livello, streak, achievements, badges
- [x] `QAEventTracker.java` - Hook su tutti gli eventi NeoForge
- [x] `QANotificationSystem.java` - Toast notifications con animazioni
- [x] `QATestingScreen.java` - UI completa per QA testing
- [x] `ActiveTestHudOverlay.java` - HUD overlay per test attivo

### Stats Subpackage (`testing/stats/`):
- [x] `KillStatistics.java` - Kill tracking con body parts, armi, streak
- [x] `DamageStatistics.java` - Danno inflitto e ricevuto
- [x] `EnvironmentalDamageStats.java` - Danni ambientali (fall, fire, lava, etc.)
- [x] `ExplosionStatistics.java` - Tracking esplosioni (TNT, Creeper)
- [x] `PotionStatistics.java` - Uso pozioni ed effetti
- [x] `EnchantmentStatistics.java` - Tracking enchantments
- [x] `ModInteractionTracker.java` - Tracking interazioni con mod
- [x] `CombatEventStatistics.java` - Eventi combattimento (evasioni, shield)
- [x] `OverlayUsageTracker.java` - Toggle overlay e screen opens
- [x] `SessionStatistics.java` - Statistiche sessione
- [x] `AchievementTracker.java` - Tracking achievement interni

### File Modificati:
- [x] `TestCase.java` - Aggiunto progressChecker, xpReward, getProgress()
- [x] `TestingSession.java` - Integrato TesterProgress, TesterProfile
- [x] `KeyInputHandler.java` - Tracciamento overlay (via QAEventTracker)

### Eventi Hookati:
- [x] `LivingDeathEvent` - Kill tracking
- [x] `LivingDamageEvent.Post` - Damage/headshot tracking
- [x] `ExplosionEvent.Detonate` - Explosion tracking
- [x] `MobEffectEvent.Added` - Potion tracking
- [x] `PlayerTickEvent.Post` - Periodic checks e auto-completion

### Funzionalità Extra Implementate:
- [x] Achievement system con 40+ achievements
- [x] Badge system (Bronze, Silver, Gold, Diamond, Specialist)
- [x] Daily streak tracking con bonus XP
- [x] Level system (1-7 con titoli)
- [x] Persistenza JSON per progress e profile
- [x] Auto-completion test basata su progress
- [x] Sound effects per notifiche
- [x] Dynamic test generation per mods installati

---

## STATO COMPLETAMENTO

| Fase | Descrizione | Stato |
|------|-------------|-------|
| 0 | Strutture dati (TesterProgress, etc.) | ✅ Completato |
| 1 | QAEventTracker completo | ✅ Completato |
| 2 | Test cases con progressChecker | ✅ Completato |
| 3 | UI con progress bar | ✅ Completato |
| 4 | Notifiche real-time | ✅ Completato |
| 5 | Integrazione e testing | ✅ Completato |
| **TOTALE** | | **100%** |

---

*Documento aggiornato: 2025-12-07*
*Versione: 3.1 - IMPLEMENTAZIONE COMPLETATA*
*Autore: Claude Code Assistant*
