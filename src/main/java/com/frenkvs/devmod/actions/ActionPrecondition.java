package com.frenkvs.devmod.actions;

import net.minecraft.network.chat.Component;

@FunctionalInterface
public interface ActionPrecondition {
    boolean test(ActionContext context);

    default Component failureMessage(ActionContext context) {
        return Component.translatable("devmod.action.unavailable");
    }

    default ActionPrecondition and(ActionPrecondition other) {
        return new ActionPrecondition() {
            @Override
            public boolean test(ActionContext context) {
                return ActionPrecondition.this.test(context) && other.test(context);
            }

            @Override
            public Component failureMessage(ActionContext context) {
                if (!ActionPrecondition.this.test(context)) {
                    return ActionPrecondition.this.failureMessage(context);
                }
                return other.failureMessage(context);
            }
        };
    }
}
