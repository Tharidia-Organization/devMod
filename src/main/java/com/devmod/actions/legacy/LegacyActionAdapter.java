package com.devmod.actions.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.RadialAction;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.BindingMeta;
import com.devmod.actions.catalog.ActionSpec.PolicyMeta;
import com.devmod.actions.catalog.ActionSpec.TelemetryMeta;
import com.devmod.actions.catalog.ActionSpec.UiMeta;

/**
 * Adapts V1 {@link RadialAction} instances to V2 {@link ActionSpec} records.
 * This allows the V2 engine to process actions that have not yet been migrated
 * to native V2 registration.
 *
 * <p>The adapter preserves all metadata from the RadialAction and infers
 * V2-specific fields where possible (e.g., channel from actionType).
 *
 * <p>CLIENT_SAFE_ACTIONS membership from V1's {@link ActionRegistry} is preserved:
 * <ul>
 *   <li>For SERVER/BOTH channel actions: NETWORK origin is added to allowedOrigins</li>
 *   <li>For CLIENT channel actions: a "client-safe" telemetry tag is added (ActionSpec
 *       forbids NETWORK origin on CLIENT-channel actions by design)</li>
 * </ul>
 */
public final class LegacyActionAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyActionAdapter.class);

    /** Telemetry tag applied to actions that are in CLIENT_SAFE_ACTIONS. */
    public static final String TAG_CLIENT_SAFE = "client-safe";

    /** Telemetry tag applied to all legacy-adapted actions. */
    public static final String TAG_LEGACY_ADAPTED = "legacy-adapted";

    private LegacyActionAdapter() {}

    /**
     * Converts a single RadialAction to an ActionSpec.
     *
     * @param action the V1 RadialAction to convert
     * @return an equivalent ActionSpec
     */
    public static ActionSpec toSpec(RadialAction action) {
        Objects.requireNonNull(action, "action");

        ActionChannel channel = inferChannel(action.getActionType());
        boolean isClientSafe = ActionRegistry.isClientSafeAction(action.getId());
        Set<ActionOrigin> origins = inferOrigins(action, channel, isClientSafe);
        int permLevel = Math.max(0, action.getPermissionLevel());
        if (permLevel > 4) {
            permLevel = 4;
        }

        String iconId = null;
        var iconStack = action.getIconStack();
        if (iconStack != null && !iconStack.isEmpty()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(iconStack.getItem());
            iconId = key.toString();
        }

        UiMeta uiMeta = new UiMeta(
            action.getLabelKey(),
            action.getDescriptionKey(),
            iconId,
            action.getMenuPath(),
            action.isToggle()
        );

        PolicyMeta policyMeta = new PolicyMeta(
            action.requiresConfirm(),
            0,
            null
        );

        // Tag legacy-adapted actions; also tag client-safe for policy layer reference
        List<String> tags = new ArrayList<>();
        tags.add(TAG_LEGACY_ADAPTED);
        if (isClientSafe) {
            tags.add(TAG_CLIENT_SAFE);
        }
        TelemetryMeta telemetryMeta = new TelemetryMeta(tags, true);

        BindingMeta bindingMeta = new BindingMeta(
            null,
            action.getCommandHint(),
            null
        );

        return new ActionSpec(
            action.getId(),
            ActionSpec.CURRENT_SCHEMA_VERSION,
            channel,
            origins,
            permLevel,
            "legacy:" + action.getId(),
            action.getCategory(),
            action.getActionType(),
            uiMeta,
            policyMeta,
            telemetryMeta,
            bindingMeta
        );
    }

    /**
     * Converts all currently registered V1 actions to ActionSpecs.
     * Results are sorted by action ID for deterministic ordering.
     *
     * @return an unmodifiable list of ActionSpec conversions, sorted by ID
     */
    public static List<ActionSpec> convertAll() {
        return convertAll(ActionRegistry.listActions());
    }

    /**
     * Converts a collection of V1 actions to ActionSpecs.
     * Results are sorted by action ID for deterministic ordering.
     * Actions that fail conversion are logged and skipped.
     *
     * @param actions the V1 actions to convert
     * @return an unmodifiable list of ActionSpec conversions, sorted by ID
     */
    public static List<ActionSpec> convertAll(Collection<RadialAction> actions) {
        Objects.requireNonNull(actions, "actions");

        List<ActionSpec> specs = new ArrayList<>();
        int skipped = 0;

        for (RadialAction action : actions) {
            try {
                specs.add(toSpec(action));
            } catch (Exception e) {
                skipped++;
                LOGGER.warn("[LegacyAdapter] Failed to convert action '{}': {}",
                    action.getId(), e.getMessage());
            }
        }

        specs.sort(Comparator.comparing(ActionSpec::id));

        if (skipped > 0) {
            LOGGER.warn("[LegacyAdapter] Skipped {} actions during batch conversion", skipped);
        }
        LOGGER.info("[LegacyAdapter] Converted {} V1 actions to V2 specs", specs.size());

        return List.copyOf(specs);
    }

    /**
     * Infers the action channel from the action type.
     */
    static ActionChannel inferChannel(@Nullable ActionType actionType) {
        if (actionType == null) {
            return ActionChannel.CLIENT;
        }
        return switch (actionType) {
            case NAVIGATE_SCREEN, TOGGLE_SETTING, OPEN_EXTERNAL, SUBCATEGORY -> ActionChannel.CLIENT;
            case RUN_SERVER_COMMAND -> ActionChannel.SERVER;
            case SEND_SERVER_RPC -> ActionChannel.BOTH;
            case TRIGGER_EVENT -> ActionChannel.CLIENT;
        };
    }

    /**
     * Infers allowed origins based on channel and CLIENT_SAFE_ACTIONS membership.
     *
     * <p>All legacy actions allow trusted origins (RADIAL, KEYBIND, COMMAND, UI, EVENT).
     * For actions in CLIENT_SAFE_ACTIONS with a non-CLIENT channel (SERVER or BOTH),
     * NETWORK origin is also allowed. CLIENT-channel actions cannot have NETWORK origin
     * per ActionSpec's design constraint.
     *
     * @param action       the V1 action
     * @param channel      the inferred channel
     * @param isClientSafe whether this action is in CLIENT_SAFE_ACTIONS
     * @return the set of allowed origins
     */
    static Set<ActionOrigin> inferOrigins(RadialAction action, ActionChannel channel,
                                           boolean isClientSafe) {
        EnumSet<ActionOrigin> origins = EnumSet.of(
            ActionOrigin.RADIAL,
            ActionOrigin.KEYBIND,
            ActionOrigin.COMMAND,
            ActionOrigin.UI,
            ActionOrigin.EVENT
        );

        // For non-CLIENT channel actions that are client-safe, allow NETWORK origin.
        // CLIENT-channel actions cannot have NETWORK per ActionSpec invariant.
        if (isClientSafe && channel != ActionChannel.CLIENT) {
            origins.add(ActionOrigin.NETWORK);
        }

        return origins;
    }
}
