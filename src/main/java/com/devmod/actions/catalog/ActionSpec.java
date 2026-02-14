package com.devmod.actions.catalog;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;

/**
 * Canonical action specification - the single source of truth for an action's metadata,
 * policy, and binding information. Part of the Actions V2 declarative catalog.
 *
 * <p>Unlike {@link com.devmod.actions.RadialAction} (which mixes metadata with handler logic),
 * ActionSpec is purely declarative - it describes WHAT an action is, not HOW it executes.
 * Handler references are stored as string keys, resolved at runtime by the engine.
 *
 * <p>ActionSpec identity is based solely on {@link #id()}. Two specs with the same ID
 * are considered equal regardless of other fields, which allows safe registry replacement
 * during hot-reload.
 */
public record ActionSpec(
    String id,
    int schemaVersion,
    ActionChannel channel,
    Set<ActionOrigin> allowedOrigins,
    int permissionLevel,
    @Nullable String handlerRef,
    ActionCategory category,
    @Nullable ActionType actionType,
    UiMeta uiMeta,
    PolicyMeta policyMeta,
    TelemetryMeta telemetryMeta,
    BindingMeta bindingMeta
) {

    /** Pattern for valid action IDs: devmod.domain.name (at least 3 dot-separated segments). */
    private static final Pattern ID_PATTERN = Pattern.compile(
        "^devmod\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)+$");

    /** Current schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Compact constructor with validation.
     */
    public ActionSpec {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                "id must match pattern 'devmod.<domain>.<name>': " + id);
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, got: " + schemaVersion);
        }
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(allowedOrigins, "allowedOrigins must not be null");
        if (permissionLevel < 0 || permissionLevel > 4) {
            throw new IllegalArgumentException(
                "permissionLevel must be 0-4, got: " + permissionLevel);
        }
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(uiMeta, "uiMeta must not be null");
        Objects.requireNonNull(policyMeta, "policyMeta must not be null");
        Objects.requireNonNull(telemetryMeta, "telemetryMeta must not be null");
        Objects.requireNonNull(bindingMeta, "bindingMeta must not be null");

        // Defensive copy of origins
        allowedOrigins = allowedOrigins.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(allowedOrigins));

        // Origin compatibility: NETWORK origin requires explicit allowance
        if (channel == ActionChannel.CLIENT && allowedOrigins.contains(ActionOrigin.NETWORK)) {
            throw new IllegalArgumentException(
                "CLIENT-only actions cannot allow NETWORK origin: " + id);
        }
    }

    /**
     * Creates a builder for the given action ID.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns true if this action allows invocation from the given origin.
     */
    public boolean allowsOrigin(ActionOrigin origin) {
        return allowedOrigins.contains(origin);
    }

    /**
     * Returns true if this action is client-side only.
     */
    public boolean isClientOnly() {
        return channel == ActionChannel.CLIENT;
    }

    /**
     * Returns true if this action is server-side only.
     */
    public boolean isServerOnly() {
        return channel == ActionChannel.SERVER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionSpec other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ActionSpec{id='" + id + "', channel=" + channel
            + ", category=" + category
            + (actionType != null ? ", type=" + actionType : "")
            + ", perm=" + permissionLevel + "}";
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Execution channel for an action.
     */
    public enum ActionChannel {
        /** Runs only on the client (UI opens, HUD toggles, config changes). */
        CLIENT,
        /** Runs only on the server (commands, world modifications). */
        SERVER,
        /** Has both client and server components (RPC actions). */
        BOTH
    }

    /**
     * UI-related metadata for rendering in menus, tooltips, and search.
     *
     * @param labelKey     Translation key for the action's display name (required)
     * @param descKey      Translation key for the action's description (required)
     * @param iconItemId   Registry name of the icon item (e.g., "minecraft:compass"), or null
     * @param menuPath     Hierarchical menu path (e.g., "Root/Tools/Commands"), or null
     * @param isToggle     Whether this action toggles a boolean state
     */
    public record UiMeta(
        String labelKey,
        String descKey,
        @Nullable String iconItemId,
        @Nullable String menuPath,
        boolean isToggle
    ) {
        public UiMeta {
            Objects.requireNonNull(labelKey, "labelKey must not be null");
            Objects.requireNonNull(descKey, "descKey must not be null");
        }

        /** Creates a minimal UiMeta with just label and description keys. */
        public static UiMeta of(String labelKey, String descKey) {
            return new UiMeta(labelKey, descKey, null, null, false);
        }
    }

    /**
     * Policy-related metadata controlling authorization behavior.
     *
     * @param requiresConfirm  Whether the action needs user confirmation before execution
     * @param rateLimitPerSec  Max invocations per second per player (0 = no limit)
     * @param preconditionRef  Reference to a named precondition, or null for always-allow
     */
    public record PolicyMeta(
        boolean requiresConfirm,
        int rateLimitPerSec,
        @Nullable String preconditionRef
    ) {
        /** Default policy metadata: no confirmation, no rate limit, no precondition. */
        public static final PolicyMeta DEFAULT = new PolicyMeta(false, 0, null);

        public PolicyMeta {
            if (rateLimitPerSec < 0) {
                throw new IllegalArgumentException(
                    "rateLimitPerSec must be >= 0, got: " + rateLimitPerSec);
            }
        }
    }

    /**
     * Telemetry-related metadata for action tracking and analytics.
     *
     * @param tags      Arbitrary string tags for filtering/grouping in dashboards
     * @param trackDuration  Whether to measure and log execution duration
     */
    public record TelemetryMeta(
        List<String> tags,
        boolean trackDuration
    ) {
        /** Default telemetry metadata: no tags, track duration. */
        public static final TelemetryMeta DEFAULT = new TelemetryMeta(List.of(), true);

        public TelemetryMeta {
            Objects.requireNonNull(tags, "tags must not be null");
            tags = List.copyOf(tags);
        }
    }

    /**
     * Binding-related metadata for connecting actions to input sources.
     *
     * @param keybindId      Keybind field name in KeyInputHandler, or null
     * @param commandHint    Brigadier command string for command-type actions, or null
     * @param networkChannel Network channel ID for RPC actions, or null
     */
    public record BindingMeta(
        @Nullable String keybindId,
        @Nullable String commandHint,
        @Nullable String networkChannel
    ) {
        /** Default binding metadata: no bindings. */
        public static final BindingMeta NONE = new BindingMeta(null, null, null);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for ActionSpec. Required fields: id (via constructor), category, uiMeta.
     */
    public static final class Builder {
        private final String id;
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private ActionChannel channel = ActionChannel.CLIENT;
        private Set<ActionOrigin> allowedOrigins = EnumSet.of(
            ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
            ActionOrigin.UI, ActionOrigin.EVENT);
        private int permissionLevel = 0;
        @Nullable private String handlerRef;
        @Nullable private ActionCategory category;
        @Nullable private ActionType actionType;
        private UiMeta uiMeta = UiMeta.of("", "");
        private PolicyMeta policyMeta = PolicyMeta.DEFAULT;
        private TelemetryMeta telemetryMeta = TelemetryMeta.DEFAULT;
        private BindingMeta bindingMeta = BindingMeta.NONE;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder channel(ActionChannel channel) {
            this.channel = Objects.requireNonNull(channel, "channel");
            return this;
        }

        public Builder allowedOrigins(Set<ActionOrigin> origins) {
            this.allowedOrigins = Objects.requireNonNull(origins, "origins");
            return this;
        }

        /** Convenience: allow all trusted origins plus the specified ones. */
        public Builder allowedOrigins(ActionOrigin... origins) {
            this.allowedOrigins = origins.length == 0
                ? EnumSet.noneOf(ActionOrigin.class)
                : EnumSet.of(origins[0], origins);
            return this;
        }

        /** Convenience: allow all origins including untrusted. */
        public Builder allowAllOrigins() {
            this.allowedOrigins = EnumSet.allOf(ActionOrigin.class);
            return this;
        }

        /** Convenience: allow only trusted origins (default). */
        public Builder trustedOriginsOnly() {
            this.allowedOrigins = EnumSet.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT);
            return this;
        }

        public Builder permissionLevel(int level) {
            this.permissionLevel = level;
            return this;
        }

        public Builder handlerRef(@Nullable String handlerRef) {
            this.handlerRef = handlerRef;
            return this;
        }

        public Builder category(ActionCategory category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder actionType(@Nullable ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder uiMeta(UiMeta uiMeta) {
            this.uiMeta = Objects.requireNonNull(uiMeta, "uiMeta");
            return this;
        }

        /** Convenience: set UI metadata from individual fields. */
        public Builder ui(String labelKey, String descKey, @Nullable String iconItemId,
                          @Nullable String menuPath, boolean isToggle) {
            this.uiMeta = new UiMeta(labelKey, descKey, iconItemId, menuPath, isToggle);
            return this;
        }

        /** Convenience: set minimal UI metadata. */
        public Builder ui(String labelKey, String descKey) {
            this.uiMeta = UiMeta.of(labelKey, descKey);
            return this;
        }

        public Builder policyMeta(PolicyMeta policyMeta) {
            this.policyMeta = Objects.requireNonNull(policyMeta, "policyMeta");
            return this;
        }

        /** Convenience: set policy metadata from individual fields. */
        public Builder policy(boolean requiresConfirm, int rateLimitPerSec,
                              @Nullable String preconditionRef) {
            this.policyMeta = new PolicyMeta(requiresConfirm, rateLimitPerSec, preconditionRef);
            return this;
        }

        public Builder telemetryMeta(TelemetryMeta telemetryMeta) {
            this.telemetryMeta = Objects.requireNonNull(telemetryMeta, "telemetryMeta");
            return this;
        }

        /** Convenience: set telemetry tags. */
        public Builder telemetryTags(String... tags) {
            this.telemetryMeta = new TelemetryMeta(List.of(tags), true);
            return this;
        }

        public Builder bindingMeta(BindingMeta bindingMeta) {
            this.bindingMeta = Objects.requireNonNull(bindingMeta, "bindingMeta");
            return this;
        }

        /** Convenience: set binding metadata from individual fields. */
        public Builder binding(@Nullable String keybindId, @Nullable String commandHint,
                               @Nullable String networkChannel) {
            this.bindingMeta = new BindingMeta(keybindId, commandHint, networkChannel);
            return this;
        }

        /**
         * Builds the ActionSpec. Throws if required fields are missing.
         */
        public ActionSpec build() {
            Objects.requireNonNull(category, "category is required");
            return new ActionSpec(
                id, schemaVersion, channel, allowedOrigins, permissionLevel,
                handlerRef, category, actionType, uiMeta, policyMeta,
                telemetryMeta, bindingMeta
            );
        }
    }
}
