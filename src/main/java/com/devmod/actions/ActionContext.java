package com.devmod.actions;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.mojang.brigadier.context.CommandContext;

public final class ActionContext {
    @Nullable
    private final Player player;
    @Nullable
    private final ServerPlayer serverPlayer;
    @Nullable
    private final CommandSourceStack source;
    @Nullable
    private final Consumer<String> commandInvoker;
    @Nullable
    private final CommandContext<CommandSourceStack> commandContext;
    @Nullable
    private final Consumer<String> commandPrompt;
    @Nullable
    private final Object payload;
    private final ActionOrigin origin;
    private final boolean clientSide;
    private final boolean confirmed;
    private final boolean screenOpen;
    private final boolean shiftDown;
    private final boolean ctrlDown;
    private final boolean altDown;

    private ActionContext(Builder builder) {
        this.player = builder.player;
        this.serverPlayer = builder.serverPlayer;
        this.source = builder.source;
        this.commandInvoker = builder.commandInvoker;
        this.commandContext = builder.commandContext;
        this.commandPrompt = builder.commandPrompt;
        this.payload = builder.payload;
        this.origin = Objects.requireNonNull(builder.origin, "origin");
        this.clientSide = builder.clientSide;
        this.confirmed = builder.confirmed;
        this.screenOpen = builder.screenOpen;
        this.shiftDown = builder.shiftDown;
        this.ctrlDown = builder.ctrlDown;
        this.altDown = builder.altDown;
    }

    public static Builder builder(ActionOrigin origin) {
        return new Builder(origin);
    }

    public static ActionContext fromCommand(CommandSourceStack source) {
        return fromCommand(source, ActionOrigin.COMMAND);
    }

    public static ActionContext fromCommand(CommandContext<CommandSourceStack> context) {
        Builder builder = new Builder(ActionOrigin.COMMAND);
        CommandSourceStack source = Objects.requireNonNull(context, "context").getSource();
        builder.source = Objects.requireNonNull(source, "source");
        builder.commandContext = context;
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer sp) {
            builder.serverPlayer = sp;
            builder.player = sp;
        } else if (entity instanceof Player p) {
            builder.player = p;
        }
        builder.clientSide = false;
        return builder.build();
    }

    public static ActionContext fromCommand(CommandSourceStack source, ActionOrigin origin) {
        Builder builder = new Builder(origin);
        builder.source = Objects.requireNonNull(source, "source");
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer sp) {
            builder.serverPlayer = sp;
            builder.player = sp;
        } else if (entity instanceof Player p) {
            builder.player = p;
        }
        builder.clientSide = false;
        return builder.build();
    }

    public ActionContext withConfirmed(boolean confirmed) {
        Builder builder = new Builder(origin);
        builder.player = player;
        builder.serverPlayer = serverPlayer;
        builder.source = source;
        builder.commandInvoker = commandInvoker;
        builder.commandContext = commandContext;
        builder.commandPrompt = commandPrompt;
        builder.payload = payload;
        builder.clientSide = clientSide;
        builder.confirmed = confirmed;
        builder.screenOpen = screenOpen;
        builder.shiftDown = shiftDown;
        builder.ctrlDown = ctrlDown;
        builder.altDown = altDown;
        return builder.build();
    }

    public ActionContext withPayload(@Nullable Object payload) {
        Builder builder = new Builder(origin);
        builder.player = player;
        builder.serverPlayer = serverPlayer;
        builder.source = source;
        builder.commandInvoker = commandInvoker;
        builder.commandContext = commandContext;
        builder.commandPrompt = commandPrompt;
        builder.payload = payload;
        builder.clientSide = clientSide;
        builder.confirmed = confirmed;
        builder.screenOpen = screenOpen;
        builder.shiftDown = shiftDown;
        builder.ctrlDown = ctrlDown;
        builder.altDown = altDown;
        return builder.build();
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    @Nullable
    public ServerPlayer getServerPlayer() {
        return serverPlayer;
    }

    @Nullable
    public CommandSourceStack getSource() {
        return source;
    }

    @Nullable
    public CommandContext<CommandSourceStack> getCommandContext() {
        return commandContext;
    }

    @Nullable
    public Object getPayload() {
        return payload;
    }

    @Nullable
    public <T> T getPayload(Class<T> type) {
        if (payload != null && type.isInstance(payload)) {
            return type.cast(payload);
        }
        return null;
    }

    public ActionOrigin getOrigin() {
        return origin;
    }

    public boolean isClientSide() {
        return clientSide;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isScreenOpen() {
        return screenOpen;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    public boolean isCtrlDown() {
        return ctrlDown;
    }

    public boolean isAltDown() {
        return altDown;
    }

    @Nullable
    public Level getLevel() {
        if (player != null) {
            return player.level();
        }
        if (source != null) {
            return source.getLevel();
        }
        return null;
    }

    public void sendSuccess(Component message, boolean actionBar) {
        Component safeMessage = java.util.Objects.requireNonNull(message, "message");
        if (source != null) {
            source.sendSuccess(() -> safeMessage, false);
            return;
        }
        if (player != null) {
            player.displayClientMessage(safeMessage, actionBar);
        }
    }

    public void sendFailure(Component message) {
        Component safeMessage = java.util.Objects.requireNonNull(message, "message");
        if (source != null) {
            source.sendFailure(safeMessage);
            return;
        }
        if (player != null) {
            player.displayClientMessage(safeMessage, true);
        }
    }

    public boolean executeCommand(String command) {
        String trimmed = normalizeCommand(command);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (commandInvoker != null) {
            commandInvoker.accept(trimmed);
            return true;
        }
        CommandSourceStack commandSource = source;
        if (commandSource != null && commandSource.getServer() != null) {
            commandSource.getServer().getCommands().performPrefixedCommand(commandSource, trimmed);
            return true;
        }
        return false;
    }

    public boolean openCommandPrompt(String command) {
        String trimmed = normalizeCommand(command);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (commandPrompt != null) {
            commandPrompt.accept(trimmed);
            return true;
        }
        return false;
    }

    private static String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    public static final class Builder {
        private final ActionOrigin origin;
        @Nullable
        private Player player;
        @Nullable
        private ServerPlayer serverPlayer;
        @Nullable
        private CommandSourceStack source;
        @Nullable
        private Consumer<String> commandInvoker;
        @Nullable
        private CommandContext<CommandSourceStack> commandContext;
        @Nullable
        private Consumer<String> commandPrompt;
        @Nullable
        private Object payload;
        private boolean clientSide;
        private boolean confirmed;
        private boolean screenOpen;
        private boolean shiftDown;
        private boolean ctrlDown;
        private boolean altDown;

        private Builder(ActionOrigin origin) {
            this.origin = Objects.requireNonNull(origin, "origin");
        }

        public Builder player(@Nullable Player player) {
            this.player = player;
            return this;
        }

        public Builder serverPlayer(@Nullable ServerPlayer serverPlayer) {
            this.serverPlayer = serverPlayer;
            return this;
        }

        public Builder source(@Nullable CommandSourceStack source) {
            this.source = source;
            return this;
        }

        public Builder commandInvoker(@Nullable Consumer<String> commandInvoker) {
            this.commandInvoker = commandInvoker;
            return this;
        }

        public Builder commandContext(@Nullable CommandContext<CommandSourceStack> commandContext) {
            this.commandContext = commandContext;
            return this;
        }

        public Builder commandPrompt(@Nullable Consumer<String> commandPrompt) {
            this.commandPrompt = commandPrompt;
            return this;
        }

        public Builder payload(@Nullable Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder clientSide(boolean clientSide) {
            this.clientSide = clientSide;
            return this;
        }

        public Builder confirmed(boolean confirmed) {
            this.confirmed = confirmed;
            return this;
        }

        public Builder screenOpen(boolean screenOpen) {
            this.screenOpen = screenOpen;
            return this;
        }

        public Builder shiftDown(boolean shiftDown) {
            this.shiftDown = shiftDown;
            return this;
        }

        public Builder ctrlDown(boolean ctrlDown) {
            this.ctrlDown = ctrlDown;
            return this;
        }

        public Builder altDown(boolean altDown) {
            this.altDown = altDown;
            return this;
        }

        public ActionContext build() {
            return new ActionContext(this);
        }
    }
}
