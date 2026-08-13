package com.devmod.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@code preconditionRef} strings carried by
 * {@link com.devmod.actions.catalog.ActionSpec.PolicyMeta} to real
 * {@link ActionPrecondition} instances.
 *
 * <p>Most refs are fixed names looked up in a map. Two families are parameterised by
 * permission level ({@code requiresPermission3}, {@code requiresPermissionOrClient_3})
 * because the registrars build them by concatenating the spec's permission level; those
 * are parsed rather than enumerated, so adding a new level cannot silently fail to
 * resolve.
 *
 * <p>Used both by the engine, to evaluate a gate, and by the catalog validator, to
 * prove at startup that every declared ref can be evaluated.
 */
public final class ActionPreconditionRegistry {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(ActionPreconditionRegistry.class);

    /** Longest first: "requiresPermission" is a prefix of "requiresPermissionOrClient_". */
    private static final String PERMISSION_OR_CLIENT_PREFIX = "requiresPermissionOrClient_";
    private static final String PERMISSION_PREFIX = "requiresPermission";

    /**
     * Refs whose full implementation reads client-only state. Named here rather than
     * taken from the client class so a dedicated server can resolve them without
     * loading it.
     */
    private static final List<String> CLIENT_STATE_REFS = List.of(
        "screenPrecondition",
        "uiScreenPrecondition",
        "qaSessionActivePrecondition",
        "qaSessionExistsPrecondition",
        "qaActiveTestPrecondition",
        "qaAutoTestPrecondition",
        "developerModePrecondition",
        "testerPrecondition",
        "partyInvitePrecondition",
        "perkSelectionPrecondition",
        "questCompletionPrecondition",
        "questDeathPrecondition",
        "onboardingActivePrecondition",
        "activeQuestPrecondition",
        "clientOnly_activeTask",
        "clientOnly_activeQuest",
        "clientOnly_respawnOrCheckpoint");

    private final Map<String, ActionPrecondition> named;

    private ActionPreconditionRegistry(Map<String, ActionPrecondition> named) {
        this.named = Map.copyOf(Objects.requireNonNull(named, "named"));
    }

    /**
     * Creates a registry over an explicit set of named preconditions.
     * Parameterised refs are still resolved.
     */
    public static ActionPreconditionRegistry of(Map<String, ActionPrecondition> named) {
        return new ActionPreconditionRegistry(named);
    }

    /**
     * Creates an empty registry. Only parameterised refs resolve.
     */
    public static ActionPreconditionRegistry empty() {
        return new ActionPreconditionRegistry(Map.of());
    }

    /**
     * Creates the registry matching what the domain registrars actually declare.
     *
     * <p>Refs backed by client-only state always resolve, on both distributions.
     * They must: {@code GameplayDomainRegistrar} is server-safe and loads everywhere,
     * so its specs put those refs in the catalog even on a dedicated server, where
     * leaving them unresolvable would fail catalog validation at boot. On the server
     * they degrade to plain {@code clientOnly()}, which already refuses every one of
     * them - each client implementation is {@code clientOnly().and(...)}, so the
     * decision is unchanged, only the failure message is less specific.
     *
     * <p>The full client implementations are loaded only on a real client
     * distribution. {@code FMLEnvironment.dist} is null under test, and there the
     * degraded forms are used deliberately: touching {@code ClientUIActions} or
     * {@code ClientGameplayActions} would initialize Minecraft client classes that
     * cannot start outside a running game. Refs still resolve, so catalog validation
     * is exercised in tests without that dependency.
     */
    public static ActionPreconditionRegistry createDefault() {
        Map<String, ActionPrecondition> named = new HashMap<>();

        named.put("always", ActionPreconditions.always());
        named.put("clientOnly", ActionPreconditions.clientOnly());
        named.put("serverOnly", ActionPreconditions.serverOnly());
        named.put("requiresPlayer", ActionPreconditions.requiresPlayer());
        named.put("requiresServerPlayer", ActionPreconditions.requiresServerPlayer());
        named.put("screenClosed", ActionPreconditions.screenClosed());

        for (String ref : CLIENT_STATE_REFS) {
            named.put(ref, ActionPreconditions.clientOnly());
        }

        if (FMLEnvironment.dist != null && FMLEnvironment.dist.isClient()) {
            named.putAll(com.devmod.actions.client.ClientActionPreconditions.all());
        }

        return new ActionPreconditionRegistry(named);
    }

    /**
     * Resolves a ref, or returns null if it names nothing this registry knows.
     *
     * @param ref the precondition ref from an ActionSpec; may be null or empty
     */
    @Nullable
    public ActionPrecondition resolve(@Nullable String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }

        ActionPrecondition direct = named.get(ref);
        if (direct != null) {
            return direct;
        }

        if (ref.startsWith(PERMISSION_OR_CLIENT_PREFIX)) {
            Integer level = parseLevel(ref.substring(PERMISSION_OR_CLIENT_PREFIX.length()));
            return level == null ? null : ActionPreconditions.requiresPermissionOrClient(level);
        }

        if (ref.startsWith(PERMISSION_PREFIX)) {
            Integer level = parseLevel(ref.substring(PERMISSION_PREFIX.length()));
            return level == null ? null : ActionPreconditions.requiresPermission(level);
        }

        return null;
    }

    /**
     * Returns true if this registry can evaluate the given ref. A null or empty ref
     * means "no gate", which is always satisfiable.
     */
    public boolean canResolve(@Nullable String ref) {
        return ref == null || ref.isEmpty() || resolve(ref) != null;
    }

    /**
     * Parses a permission level suffix, accepting only the 0-4 range that
     * {@code ActionSpec} enforces for permission levels.
     */
    @Nullable
    private static Integer parseLevel(String suffix) {
        // Also rejects a leading '_' left over from a mistyped ref such as
        // "requiresPermission_2", which must not silently resolve.
        if (suffix.length() != 1) {
            return null;
        }
        char c = suffix.charAt(0);
        if (c < '0' || c > '4') {
            LOGGER.warn("Precondition ref has out-of-range permission level: {}", suffix);
            return null;
        }
        return c - '0';
    }
}
