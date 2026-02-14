package com.devmod.actions.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.devmod.actions.ActionContext;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Evaluates a chain of policies in priority order, short-circuiting on the first deny.
 * An empty chain allows all actions by default.
 *
 * <p>Policies are sorted by {@link ActionPolicy#priority()} at construction time
 * (lower number = evaluated first). This is immutable after construction.
 *
 * <p>Usage:
 * <pre>{@code
 * PolicyChain chain = PolicyChain.builder()
 *     .add(OriginPolicy.INSTANCE)
 *     .add(PermissionPolicy.INSTANCE)
 *     .add(CommandSafetyPolicy.INSTANCE)
 *     .add(new RateLimitPolicy())
 *     .build();
 *
 * PolicyResult result = chain.evaluate(spec, context);
 * if (result.isDenied()) {
 *     // handle denial
 * }
 * }</pre>
 */
public final class PolicyChain {

    private final List<ActionPolicy> policies;

    private PolicyChain(List<ActionPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    /**
     * Evaluates all policies in priority order. Returns the first deny result,
     * or allow if all policies pass.
     */
    public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
        for (ActionPolicy policy : policies) {
            PolicyResult result = policy.evaluate(spec, context);
            if (result.isDenied()) {
                return result;
            }
        }
        return PolicyResult.allow();
    }

    /**
     * Returns the number of policies in this chain.
     */
    public int size() {
        return policies.size();
    }

    /**
     * Returns an unmodifiable view of the policies in evaluation order.
     */
    public List<ActionPolicy> policies() {
        return policies;
    }

    /**
     * Creates a new builder for constructing a PolicyChain.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty chain that allows everything.
     */
    public static PolicyChain empty() {
        return new PolicyChain(List.of());
    }

    /**
     * Builder for PolicyChain. Policies are sorted by priority at build time.
     */
    public static final class Builder {
        private final List<ActionPolicy> policies = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a policy to the chain. Order of add() calls does not matter;
         * policies are sorted by priority at build time.
         */
        public Builder add(ActionPolicy policy) {
            policies.add(Objects.requireNonNull(policy, "policy"));
            return this;
        }

        /**
         * Builds the PolicyChain with policies sorted by priority.
         */
        public PolicyChain build() {
            List<ActionPolicy> sorted = new ArrayList<>(policies);
            sorted.sort(Comparator.comparingInt(ActionPolicy::priority));
            return new PolicyChain(sorted);
        }
    }
}
