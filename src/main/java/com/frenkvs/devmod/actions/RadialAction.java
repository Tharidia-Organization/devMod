package com.frenkvs.devmod.actions;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RadialAction {
    private final String id;
    private final String labelKey;
    private final String descriptionKey;
    private final ActionCategory category;
    @Nullable
    private final String menuPath;
    @Nullable
    private final ItemLike iconItem;
    private final ActionPrecondition precondition;
    private final Consumer<ActionContext> handler;
    private final boolean requiresConfirm;
    private final boolean toggle;
    @Nullable
    private final Predicate<ActionContext> activePredicate;
    @Nullable
    private final String commandHint;

    private RadialAction(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.labelKey = Objects.requireNonNull(builder.labelKey, "labelKey");
        this.descriptionKey = Objects.requireNonNull(builder.descriptionKey, "descriptionKey");
        this.category = Objects.requireNonNull(builder.category, "category");
        this.menuPath = builder.menuPath;
        this.iconItem = builder.iconItem;
        this.precondition = builder.precondition != null ? builder.precondition : ActionPreconditions.always();
        this.handler = Objects.requireNonNull(builder.handler, "handler");
        this.requiresConfirm = builder.requiresConfirm;
        this.toggle = builder.toggle;
        this.activePredicate = builder.activePredicate;
        this.commandHint = builder.commandHint;
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
        private String labelKey;
        private String descriptionKey;
        private ActionCategory category;
        @Nullable
        private String menuPath;
        @Nullable
        private ItemLike iconItem;
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
