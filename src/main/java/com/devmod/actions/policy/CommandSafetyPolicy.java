package com.devmod.actions.policy;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionType;
import com.devmod.actions.CommandSanitizer;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Validates command safety for command-type actions. Wraps the existing
 * {@link CommandSanitizer} to integrate it into the policy chain.
 *
 * <p>Only applies to actions with {@code actionType == RUN_SERVER_COMMAND}
 * that have a command hint in their binding metadata. Other action types
 * are allowed unconditionally by this policy.
 */
public final class CommandSafetyPolicy implements ActionPolicy {

    public static final CommandSafetyPolicy INSTANCE = new CommandSafetyPolicy();

    private CommandSafetyPolicy() {}

    @Override
    public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
        // Only check command-type actions
        if (spec.actionType() != ActionType.RUN_SERVER_COMMAND) {
            return PolicyResult.allow();
        }

        String commandHint = spec.bindingMeta().commandHint();
        if (commandHint == null || commandHint.isEmpty()) {
            return PolicyResult.allow();
        }

        if (!CommandSanitizer.isCommandSafe(commandHint)) {
            return PolicyResult.deny(
                "Command failed safety check: "
                    + CommandSanitizer.sanitizeForLog(commandHint),
                policyName()
            );
        }

        return PolicyResult.allow();
    }

    @Override
    public String policyName() {
        return "CommandSafetyPolicy";
    }

    @Override
    public int priority() {
        return 200;
    }
}
