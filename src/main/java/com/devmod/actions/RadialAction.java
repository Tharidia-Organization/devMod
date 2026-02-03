package com.devmod.actions;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class RadialAction {
    /**
     * UI Feedback type shown when action completes or fails.
     */
    public enum UIFeedback {
        /** No UI feedback (silent) */
        NONE,
        /** Use server/chat feedback */
        CHAT
    }

    private final String id;
    private final String labelKey;
    private final String descriptionKey;
    private final ActionCategory category;
    @Nullable
    private final ActionType actionType;
    @Nullable
    private final String menuPath;
    @Nullable
    private final ItemLike iconItem;
    /**
     * Visibility predicate - determines if action is shown in menu.
     * Checked before rendering. If false, item is hidden (not grayed out).
     */
    @Nullable
    private final Predicate<ActionContext> visibilityPredicate;
    /**
     * Execution precondition - determines if action can be executed.
     * Checked when action is invoked. If false, action is blocked with error.
     */
    private final ActionPrecondition precondition;
    private final Consumer<ActionContext> handler;
    private final boolean requiresConfirm;
    private final boolean toggle;
    @Nullable
    private final Predicate<ActionContext> activePredicate;
    @Nullable
    private final String commandHint;
    /**
     * Explicit permission level required (for documentation/contract).
     * -1 means no specific permission required.
     */
    private final int permissionLevel;
    /**
     * UI feedback type to show on completion/failure.
     */
    private final UIFeedback uiFeedback;

    private RadialAction(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.labelKey = Objects.requireNonNull(builder.labelKey, "labelKey");
        this.descriptionKey = Objects.requireNonNull(builder.descriptionKey, "descriptionKey");
        this.category = Objects.requireNonNull(builder.category, "category");
        this.actionType = builder.actionType;
        this.menuPath = builder.menuPath;
        this.iconItem = builder.iconItem;
        this.visibilityPredicate = builder.visibilityPredicate;
        this.precondition = builder.precondition != null ? builder.precondition : ActionPreconditions.always();
        this.handler = Objects.requireNonNull(builder.handler, "handler");
        this.requiresConfirm = builder.requiresConfirm;
        this.toggle = builder.toggle;
        this.activePredicate = builder.activePredicate;
        this.commandHint = builder.commandHint;
        this.permissionLevel = builder.permissionLevel;
        this.uiFeedback = builder.uiFeedback != null ? builder.uiFeedback : UIFeedback.NONE;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public Component getLabel() {
        return Component.translatable(Objects.requireNonNull(labelKey, "labelKey"));
    }

    public Component getDescription() {
        return Component.translatable(Objects.requireNonNull(descriptionKey, "descriptionKey"));
    }

    public ActionCategory getCategory() {
        return category;
    }

    /**
     * Returns the type of this action, or null if not explicitly set.
     * Used for telemetry, gating patterns, and behavior categorization.
     */
    @Nullable
    public ActionType getActionType() {
        return actionType;
    }

    @Nullable
    public String getMenuPath() {
        return menuPath;
    }

    @Nullable
    public ItemStack getIconStack() {
        return iconItem != null ? new ItemStack(iconItem) : null;
    }

    public ActionPrecondition getPrecondition() {
        return precondition;
    }

    /**
     * Returns the visibility predicate, or null if always visible.
     * Used by UI to determine if action should be shown in menu.
     */
    @Nullable
    public Predicate<ActionContext> getVisibilityPredicate() {
        return visibilityPredicate;
    }

    /**
     * Check if this action should be visible in the given context.
     * Returns true if no visibility predicate is set (always visible).
     */
    public boolean isVisible(ActionContext context) {
        Predicate<ActionContext> pred = visibilityPredicate;
        return pred == null || pred.test(context);
    }

    /**
     * Returns the explicit permission level required, or -1 if none.
     */
    public int getPermissionLevel() {
        return permissionLevel;
    }

    /**
     * Returns the UI feedback type for this action.
     */
    public UIFeedback getUIFeedback() {
        return uiFeedback;
    }

    public boolean requiresConfirm() {
        return requiresConfirm;
    }

    public boolean isToggle() {
        return toggle;
    }

    public boolean isActive(ActionContext context) {
        Predicate<ActionContext> predicate = activePredicate;
        if (predicate == null) {
            return false;
        }
        return predicate.test(context);
    }

    @Nullable
    public String getCommandHint() {
        return commandHint;
    }

    void invoke(ActionContext context) {
        handler.accept(context);
    }

    public static final class Builder {
        private final String id;
        @Nullable
        private String labelKey;
        @Nullable
        private String descriptionKey;
        @Nullable
        private ActionCategory category;
        @Nullable
        private ActionType actionType;
        @Nullable
        private String menuPath;
        @Nullable
        private ItemLike iconItem;
        @Nullable
        private Predicate<ActionContext> visibilityPredicate;
        @Nullable
        private ActionPrecondition precondition;
        @Nullable
        private Consumer<ActionContext> handler;
        private boolean requiresConfirm;
        private boolean toggle;
        @Nullable
        private Predicate<ActionContext> activePredicate;
        @Nullable
        private String commandHint;
        private int permissionLevel = -1;
        @Nullable
        private UIFeedback uiFeedback;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder labelKey(String labelKey) {
            this.labelKey = labelKey;
            return this;
        }

        public Builder descriptionKey(String descriptionKey) {
            this.descriptionKey = descriptionKey;
            return this;
        }

        public Builder category(ActionCategory category) {
            this.category = category;
            return this;
        }

        public Builder actionType(@Nullable ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder menuPath(@Nullable String menuPath) {
            this.menuPath = menuPath;
            return this;
        }

        public Builder icon(@Nullable ItemLike iconItem) {
            this.iconItem = iconItem;
            return this;
        }

        public Builder precondition(@Nullable ActionPrecondition precondition) {
            this.precondition = precondition;
            return this;
        }

        /**
         * Set visibility predicate - controls whether action is shown in menu.
         * This is separate from precondition (which controls execution).
         * Use for permission-based hiding (Scenario 9).
         */
        public Builder visibilityPredicate(@Nullable Predicate<ActionContext> visibilityPredicate) {
            this.visibilityPredicate = visibilityPredicate;
            return this;
        }

        /**
         * Set explicit permission level for documentation/contract.
         */
        public Builder permissionLevel(int level) {
            this.permissionLevel = level;
            return this;
        }

        /**
         * Set UI feedback type shown on completion/failure.
         */
        public Builder uiFeedback(@Nullable UIFeedback uiFeedback) {
            this.uiFeedback = uiFeedback;
            return this;
        }

        public Builder handler(Consumer<ActionContext> handler) {
            this.handler = handler;
            return this;
        }

        public Builder requiresConfirm(boolean requiresConfirm) {
            this.requiresConfirm = requiresConfirm;
            return this;
        }

        public Builder toggle(Predicate<ActionContext> activePredicate) {
            this.toggle = true;
            this.activePredicate = activePredicate;
            return this;
        }

        public Builder commandHint(@Nullable String commandHint) {
            this.commandHint = commandHint;
            return this;
        }

        public RadialAction build() {
            return new RadialAction(this);
        }
    }
}
