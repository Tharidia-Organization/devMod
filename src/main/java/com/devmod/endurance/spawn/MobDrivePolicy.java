package com.devmod.endurance.spawn;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Decides how much of a spawned mob DevMod is allowed to drive.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The endurance system was written against vanilla mobs, where a mob is an inert bag of
 * attributes plus a goal selector: writing {@code MAX_HEALTH} and adding a melee goal is the whole
 * contract. A heavily modded server breaks that assumption in a way that produces no error at all.
 *
 * <p>The case that cost us a play-test: Age of Fight's Ashen Court entities
 * ({@code com.ageoffight.mobs.entity.AshenCourtCombatant} and its subclasses) have an
 * <b>empty</b> {@code registerGoals()} and are moved instead by that mod's own server runtime,
 * which every tick reconstructs a hundred-point stat allocation from the <b>base values</b> of
 * eight canonical attributes and refuses the whole squad's tactical snapshot when the sum is not
 * exactly a hundred. A single {@code setBaseValue(MAX_HEALTH, ...)} from us therefore did not
 * "buff the mob": it made the mob permanently unreadable to the only code that moves it. No
 * exception, no warning of ours, {@code combatAuthorised()} still true, the entity still enrolled
 * -- and standing perfectly still forever. The mod logs it as
 * {@code AOF_SNAPSHOT_MEMBER_EXCLUDED ... reason=POINTS_UNREADABLE}.
 *
 * <p>Using an {@code AttributeModifier} instead -- the usual advice, and what our own audit
 * proposed -- is strictly worse there: the same mod treats any modifier on those eight attributes,
 * found when the entity is read back from disk, as tampering, quarantines the allocation and
 * discards the entity. The fix is not a better way to write the attributes. It is to not write
 * them.
 *
 * <p>So the policy is per-mob and has two independent switches, because the two capabilities fail
 * independently:
 *
 * <ul>
 *   <li>{@link #scaleAttributes()} -- may we write attribute base values? False when the owning
 *       mod derives its own behaviour from them.</li>
 *   <li>{@link #installMovementGoals()} -- may we add goals that own navigation? False when the
 *       owning mod drives navigation itself; two writers on one {@code PathNavigation} means
 *       whichever ran last wins each tick, which looks like stuttering rather than a bug.</li>
 *   <li>{@link #requiresOwnFinalizeSpawn()} -- must {@code finalizeSpawn} be called on a path that
 *       historically did not call it? True only for these mobs, because for a vanilla mob that call
 *       is where the game randomises the spawn and a boss is meant to be deterministic.</li>
 * </ul>
 *
 * <p>The targeting goal is installed either way. It only calls {@code setTarget}, and for a
 * self-driven mob that is exactly the handshake its runtime needs: Age of Fight only considers a
 * player hostile once some member already targets it or has been hurt by it, so a freshly spawned
 * arena mob would otherwise never acquire the player it was spawned to fight.
 */
public final class MobDrivePolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobDrivePolicy.class);

    /** Used when FML has not been initialised, so this can never be the thing that throws. */
    private static final Path FALLBACK_CONFIG_PATH = Path.of("config/devmod/guarded_mobs.json");

    /**
     * Where the optional extra guards live.
     *
     * <p>Resolved through {@link com.devmod.util.ConfigPaths} rather than as a bare relative path: a
     * relative path happens to work for a dedicated server and for a launcher-started client, and
     * fails silently anywhere the working directory differs -- {@code isRegularFile} returns false,
     * the built-in guards are used and nothing is logged.
     *
     * <p>But {@code ConfigPaths} goes through {@code FMLPaths.CONFIGDIR.get()}, which throws when
     * FML has not been initialised, and this is reached from the wave spawn loop with no try around
     * it. A unit test caught it immediately. Falling back to the relative path keeps the better
     * resolution where it works and keeps a spawn from dying where it does not.
     *
     * @return the config file's path; never throws
     */
    private static Path configPath() {
        try {
            return com.devmod.util.ConfigPaths.getConfigDir().resolve("guarded_mobs.json");
        } catch (RuntimeException | LinkageError noFmlRuntime) {
            return FALLBACK_CONFIG_PATH;
        }
    }

    /**
     * Types whose own mod owns their statistics and their navigation.
     *
     * <p>Matched against the whole class hierarchy and every implemented interface, so naming the
     * shared supertype covers present and future subclasses -- which is the point: the next Ashen
     * Court archetype must be guarded on the day it is added, not on the day someone notices it is
     * frozen.
     */
    private static final Set<String> DEFAULT_GUARDED_TYPES = Set.of(
        // Age of Fight - Mobs. Base class of every Ashen Court entity (BoneboundVanguard and the
        // archetypes) and the interface its squad runtime resolves members through.
        "com.ageoffight.mobs.entity.AshenCourtCombatant",
        "com.ageoffight.mobs.entity.SquadCombatant"
    );

    /** Namespaces of entity-type registry ids to guard wholesale. Empty by default. */
    private static final Set<String> DEFAULT_GUARDED_NAMESPACES = Set.of();

    private static final MobDrivePolicy OPEN =
        new MobDrivePolicy(true, true, "open");

    private static final Map<EntityType<?>, MobDrivePolicy> CACHE = new ConcurrentHashMap<>();

    /** Refusals already logged, keyed "&lt;entity type&gt;|&lt;what was skipped&gt;". */
    private static final Set<String> LOGGED_REFUSALS = ConcurrentHashMap.newKeySet();

    @Nullable
    private static Set<String> guardedTypes;

    @Nullable
    private static Set<String> guardedNamespaces;

    private final boolean scaleAttributes;
    private final boolean installMovementGoals;
    private final String reason;

    private MobDrivePolicy(boolean scaleAttributes, boolean installMovementGoals, String reason) {
        this.scaleAttributes = scaleAttributes;
        this.installMovementGoals = installMovementGoals;
        this.reason = reason;
    }

    /** True when DevMod may write attribute base values on this mob. */
    public boolean scaleAttributes() {
        return this.scaleAttributes;
    }

    /** True when DevMod may install goals that take ownership of navigation. */
    public boolean installMovementGoals() {
        return this.installMovementGoals;
    }

    /** Human-readable justification, for the spawn log. */
    public String reason() {
        return this.reason;
    }

    /** True when the owning mod, not DevMod, is responsible for moving this mob. */
    public boolean selfDriven() {
        return !this.installMovementGoals;
    }

    /**
     * True when this mob's {@code finalizeSpawn} carries initialisation it cannot live without.
     *
     * <p>Only for spawn paths that did not call {@code finalizeSpawn} at all, i.e. the boss and
     * minion paths. Calling it unconditionally there looked like a strict improvement and is not:
     * for a vanilla mob, {@code Mob.finalizeSpawn} is where the game randomises the spawn.
     * {@code Zombie.handleAttributes} can attach a permanent MULTIPLY_TOTAL modifier to MAX_HEALTH
     * -- so a boss whose health we then set with {@code setHealth(scaled)} spawns at a fraction of
     * its own bar, while the phase thresholds are computed from the inflated maximum -- and passing
     * a null SpawnGroupData makes it roll baby zombies and chicken jockeys. A skeleton gets a bow
     * and {@code reassessWeaponGoal} installs a ranged goal that competes with the melee goal we
     * add four lines later. None of that is wanted on a boss, which is supposed to be the
     * deterministic part of a wave.
     *
     * <p>So on those paths it runs only for a mob that needs it, and "needs it" is the same list as
     * everything else here: a mod that owns the entity is also the mod that initialises it there.
     * A modded boss outside the list keeps the old behaviour; add it to the guard list, which is
     * the extension point, rather than widening this to every non-vanilla mob.
     *
     * @return true when finalizeSpawn must be called on a path that otherwise would not
     */
    public boolean requiresOwnFinalizeSpawn() {
        return this.selfDriven();
    }

    /**
     * Resolve the policy for a mob.
     *
     * <p>Cached per {@link EntityType}: the answer depends only on the class hierarchy and the
     * registry id, both fixed per type, and this runs on every single spawn.
     *
     * @param mob the mob about to be configured
     * @return the policy, never null
     */
    public static MobDrivePolicy resolve(Mob mob) {
        if (mob == null) {
            return OPEN;
        }
        return CACHE.computeIfAbsent(mob.getType(), type -> compute(mob.getClass(), type));
    }

    /**
     * Ask whether DevMod may write attribute base values on this mob, logging any refusal.
     *
     * <p>The one call every stat-writing path should go through, so that a new one cannot be added
     * without the guard and so that a refusal is always visible in the log rather than being a
     * silent difference in behaviour between two mob types.
     *
     * @param mob the mob about to be modified
     * @param what what was going to be applied, for the log line
     * @return true when scaling is permitted
     */
    public static boolean allowScaling(@Nullable Mob mob, String what) {
        if (mob == null) {
            return false;
        }
        MobDrivePolicy policy = resolve(mob);
        if (policy.scaleAttributes()) {
            return true;
        }
        // Once per (type, reason), not once per call. The refusal is constant per type by
        // construction, and the call sites are not: a thirty-mob wave produced four lines each, and
        // the boss ability paths -- charge, smoke bomb, enrage, every summoned minion -- add one per
        // activation for the whole fight. Several hundred INFO lines per wave to say the same
        // unchanging thing buries the lines that do change.
        if (LOGGED_REFUSALS.add(EntityType.getKey(mob.getType()) + "|" + what)) {
            LOGGER.info("[MobDrivePolicy] Skipping {} on {}: {}. Its own mod derives behaviour from "
                    + "these attribute base values and stops moving the entity when they change. "
                    + "Logged once per mob type and reason.",
                what, mob.getType().toString(), policy.reason());
        }
        return false;
    }

    private static MobDrivePolicy compute(Class<?> mobClass, EntityType<?> type) {
        String namespace = EntityType.getKey(type).getNamespace();
        if (guardedNamespaces().contains(namespace)) {
            LOGGER.info("[MobDrivePolicy] {} is self-driven: namespace '{}' is guarded",
                EntityType.getKey(type), namespace);
            return new MobDrivePolicy(false, false, "guarded-namespace:" + namespace);
        }
        String match = firstGuardedSupertype(mobClass);
        if (match != null) {
            LOGGER.info("[MobDrivePolicy] {} is self-driven: it is a {}. DevMod will set its target "
                    + "but will not scale its attributes or drive its navigation.",
                EntityType.getKey(type), match);
            return new MobDrivePolicy(false, false, "guarded-type:" + match);
        }
        return OPEN;
    }

    /**
     * Walk the class hierarchy and every interface, breadth-first, looking for a guarded name.
     *
     * <p>Interfaces are included deliberately: a mod's marker for "my runtime owns this entity" is
     * as likely to be an interface as a base class, and Age of Fight's is both.
     */
    @Nullable
    static String firstGuardedSupertype(Class<?> mobClass) {
        Set<String> guarded = guardedTypes();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(mobClass);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            if (current == null || !seen.add(current)) {
                continue;
            }
            if (guarded.contains(current.getName())) {
                return current.getName();
            }
            // Null-checked because ArrayDeque rejects nulls outright, and getSuperclass() returns
            // null for Object and for every interface -- i.e. at the end of every walk that does
            // not find a match, which is the common case.
            Class<?> parent = current.getSuperclass();
            if (parent != null) {
                queue.add(parent);
            }
            for (Class<?> implemented : current.getInterfaces()) {
                queue.add(implemented);
            }
        }
        return null;
    }

    private static Set<String> guardedTypes() {
        loadConfigOnce();
        Set<String> resolved = guardedTypes;
        return resolved != null ? resolved : DEFAULT_GUARDED_TYPES;
    }

    private static Set<String> guardedNamespaces() {
        loadConfigOnce();
        Set<String> resolved = guardedNamespaces;
        return resolved != null ? resolved : DEFAULT_GUARDED_NAMESPACES;
    }

    private static synchronized void loadConfigOnce() {
        if (guardedTypes != null) {
            return;
        }
        Set<String> types = new LinkedHashSet<>(DEFAULT_GUARDED_TYPES);
        Set<String> namespaces = new LinkedHashSet<>(DEFAULT_GUARDED_NAMESPACES);
        // The defaults stay in force whatever the file says. A guard that a config can switch off
        // is a guard that a copied config from another server switches off silently, and the
        // failure it prevents is invisible -- a mob that stands still, with no error anywhere.
        // Extra entries are additive precisely because the next mod to need one should not need a
        // DevMod release.
        try {
            Path path = configPath();
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    GuardedMobsFile parsed = new Gson().fromJson(reader, GuardedMobsFile.class);
                    if (parsed != null) {
                        addAllNonBlank(types, parsed.guardedTypes);
                        addAllLowerCase(namespaces, parsed.guardedNamespaces);
                        LOGGER.info("[MobDrivePolicy] Loaded {}: {} extra types, "
                                + "{} extra namespaces",
                            path,
                            types.size() - DEFAULT_GUARDED_TYPES.size(),
                            namespaces.size() - DEFAULT_GUARDED_NAMESPACES.size());
                    }
                }
            }
        } catch (java.io.IOException | JsonParseException malformed) {
            // JsonParseException, not JsonSyntaxException: Gson also throws JsonIOException, which
            // does NOT extend IOException. It would have escaped this method, and from here the
            // call stack is resolve() -> allowScaling() -> applyMobModifiers(), which is not inside
            // a try -- a truncated config file would have aborted the spawn loop mid-wave.
            LOGGER.warn("[MobDrivePolicy] Could not read {}, using built-in guards only",
                configPath(), malformed);
        }
        guardedNamespaces = Set.copyOf(namespaces);
        // guardedTypes being non-null is the "already loaded" flag, so it is assigned last. Note
        // that what makes the plain reads in guardedTypes()/guardedNamespaces() safe is the monitor
        // -- both call this synchronized method before reading, so the writer's release
        // happens-before their acquire. Write ordering alone would not be enough, and anyone adding
        // a lock-free fast path here would need volatile.
        guardedTypes = Set.copyOf(types);
    }

    private static void addAllNonBlank(Set<String> target, @Nullable List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim());
            }
        }
    }

    private static void addAllLowerCase(Set<String> target, @Nullable List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    /** Drop cached decisions. For tests and for a config reload. */
    public static void invalidate() {
        // Config first, cache second. The other order let a resolve() that had already read the old
        // config write its stale decision into the freshly cleared cache, where it would sit for
        // the life of the process because compute() only ever runs on a miss -- so an added guard
        // would never take effect for that type, silently. This order cannot fully close the window
        // either, but it narrows it from the whole of compute() to a single map insertion.
        synchronized (MobDrivePolicy.class) {
            guardedTypes = null;
            guardedNamespaces = null;
        }
        CACHE.clear();
        LOGGED_REFUSALS.clear();
    }

    /** Shape of {@code config/devmod/guarded_mobs.json}. */
    private static final class GuardedMobsFile {
        @Nullable
        List<String> guardedTypes;
        @Nullable
        List<String> guardedNamespaces;
    }
}
